package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.Category
import java.text.Normalizer
import java.util.Locale

/**
 * Provider category names are free-form. This classifier deliberately matches
 * complete aliases only: `EN|` is English, while `Entertainment` is not. The
 * alias table lives in packages/category-classification.json and is generated
 * into both the Android and web clients.
 */

internal const val ALL_CATEGORY_FACETS = "__all__"
internal const val UNCLASSIFIED_CATEGORY_FACET = "__none__"

/**
 * Two-letter language/region codes are valid on a provider prefix (`NO|`) but
 * are ordinary words in bare category names (`No prefix`). Only trust them
 * when a `|` delimits the prefix.
 */
private val CATEGORY_AMBIGUOUS_SHORT_ALIASES = setOf(
    "no", "it", "in", "id", "hi", "he", "us", "is", "me", "my",
    "am", "an", "as", "at", "be", "by", "do", "go", "if", "of",
    "on", "or", "up", "we",
)

internal data class CategoryAliasRule(
    val key: String,
    val label: String,
    val aliases: List<String>,
    val rank: Int = 0,
)

internal enum class CategoryFacetDimension { LANGUAGE, QUALITY }

internal data class CategoryFacetOption(
    val key: String,
    val label: String,
    val count: Int,
    val rank: Int = 0,
)

internal data class CategoryFacetValue(val key: String, val label: String, val rank: Int = 0)

internal data class CategoryClassification(
    val language: CategoryFacetValue?,
    val quality: CategoryFacetValue?,
    val region: CategoryFacetValue?,
    val provider: CategoryFacetValue?,
    val formats: List<CategoryFacetValue>,
    val ambiguousLanguage: Boolean,
    val groupKey: String,
    val groupLabel: String,
    val markers: List<String>,
) {
    val summary: String
        get() = buildList {
            language?.let { add(it.label) }
            quality?.let { add(it.label) }
            formats.forEach { format -> if (none { it == format.label }) add(format.label) }
            provider?.let { add(it.label) }
            region?.let { add(it.label) }
            if (ambiguousLanguage) add("Multiple languages")
        }.ifEmpty { listOf("Unclassified") }.joinToString(" · ")
}

private data class CompiledRule(
    val dimension: CategoryFacetDimension,
    val rule: CategoryAliasRule,
    val alias: String,
    val aliasTokens: List<String>,
    val order: Int,
) {
    val matchKey: String get() = "${dimension.name}:${rule.key}"
}

private data class RuleMatch(
    val compiled: CompiledRule,
    val alias: String,
    val tokenCount: Int,
)

internal fun classifyCategory(name: String): CategoryClassification =
    CategoryClassifier.classify(name)

/** Test/diagnostic hook proving the classifier's derived string cache is bounded. */
internal fun categoryClassificationCacheSize(): Int = CategoryClassifier.cacheSize()

internal fun categoryGroupKey(name: String): String = classifyCategory(name).groupKey

internal fun categoryMatchesGroup(name: String, groupKey: String): Boolean =
    groupKey == ALL_CATEGORY_GROUPS || categoryGroupKey(name) == groupKey

internal fun categoryFacets(
    categories: List<Category>,
    dimension: CategoryFacetDimension,
): List<CategoryFacetOption> {
    val counts = LinkedHashMap<String, Int>()
    val labels = LinkedHashMap<String, String>()
    var unclassified = 0
    categories.forEach { category ->
        val value = facetValue(classifyCategory(category.name), dimension)
        if (value == null) {
            unclassified += 1
        } else {
            counts[value.key] = (counts[value.key] ?: 0) + 1
            labels[value.key] = value.label
        }
    }
    val rules = when (dimension) {
        CategoryFacetDimension.LANGUAGE -> categoryLanguageRules
        CategoryFacetDimension.QUALITY -> categoryQualityRules
    }
    val options = rules
        .filter { (counts[it.key] ?: 0) > 0 }
        .map {
            CategoryFacetOption(
                key = it.key,
                label = labels[it.key] ?: it.label,
                count = counts[it.key] ?: 0,
                rank = it.rank,
            )
        }
        .sortedWith(
            if (dimension == CategoryFacetDimension.QUALITY) {
                compareByDescending<CategoryFacetOption> { it.rank }.thenBy { it.label }
            } else {
                compareBy { it.label.lowercase(Locale.ROOT) }
            },
        )
        .toMutableList()
    if (unclassified > 0) {
        options += CategoryFacetOption(
            key = UNCLASSIFIED_CATEGORY_FACET,
            label = "Unclassified",
            count = unclassified,
        )
    }
    return options
}

internal fun categoryMatchesFacet(
    name: String,
    dimension: CategoryFacetDimension,
    key: String,
): Boolean {
    if (key == ALL_CATEGORY_FACETS) return true
    val value = facetValue(classifyCategory(name), dimension)
    return if (key == UNCLASSIFIED_CATEGORY_FACET) {
        value == null
    } else {
        value?.key == key
    }
}

private fun facetValue(
    classification: CategoryClassification,
    dimension: CategoryFacetDimension,
): CategoryFacetValue? = when (dimension) {
    CategoryFacetDimension.LANGUAGE -> classification.language
    CategoryFacetDimension.QUALITY -> classification.quality
}

private object CategoryClassifier {
    private val cache = object : LinkedHashMap<String, CategoryClassification>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CategoryClassification>?): Boolean =
            size > 512
    }

    private val languages = compile(CategoryFacetDimension.LANGUAGE, categoryLanguageRules)
    private val qualities = compile(CategoryFacetDimension.QUALITY, categoryQualityRules)
    private val formats = compile(CategoryFacetDimension.LANGUAGE, categoryFormatRules)
    private val regions = compile(CategoryFacetDimension.LANGUAGE, categoryRegionRules)
    private val providers = compile(CategoryFacetDimension.LANGUAGE, categoryProviderRules)
    private val leadingRules = languages + qualities + formats + regions + providers

    fun classify(name: String): CategoryClassification {
        val key = name
        synchronized(cache) { cache[key]?.let { return it } }
        val value = build(name)
        synchronized(cache) { cache[key] = value }
        return value
    }

    fun cacheSize(): Int = synchronized(cache) { cache.size }

    private fun build(name: String): CategoryClassification {
        val prefix = categoryPrefix(name)
        val allTokens = tokens(name)
        val prefixTokens = prefix?.let(::tokens)
        val prefixOnly = prefixTokens != null
        val languageMatches = collectMatches(prefixTokens ?: allTokens, languages, atStart = false, allowShort = prefixOnly)
        val language = languageMatches.singleOrNull()?.compiled?.rule
        val ambiguousLanguage = languageMatches.size > 1
        val qualityMatches = collectMatches(allTokens, qualities, atStart = false, allowShort = prefixOnly)
            .sortedByDescending { it.compiled.rule.rank }
        val quality = qualityMatches.firstOrNull()?.compiled?.rule
        val formatMatches = collectMatches(allTokens, formats, atStart = false, allowShort = prefixOnly)
        val region = collectMatches(prefixTokens ?: allTokens, regions, atStart = false, allowShort = prefixOnly)
            .firstOrNull()?.compiled?.rule
        val provider = collectMatches(prefixTokens ?: allTokens, providers, atStart = false, allowShort = prefixOnly)
            .firstOrNull()?.compiled?.rule
        val leading = if (prefix == null) {
            collectMatches(allTokens, leadingRules, atStart = true, allowShort = false).firstOrNull()
        } else {
            null
        }
        val groupLabel = prefix ?: leading?.let { rawLeadingLabel(name, it.tokenCount) }.orEmpty()
        val groupKey = groupLabel.uppercase(Locale.ROOT).ifBlank { UNRECOGNIZED_CATEGORY_GROUP }

        val markers = mutableListOf<String>()
        fun marker(source: String, match: RuleMatch?) {
            if (match != null) markers += "$source ${match.alias} → ${match.compiled.rule.label}"
        }
        if (prefixTokens != null) {
            marker("prefix", languageMatches.firstOrNull())
            marker("prefix", collectMatches(prefixTokens, qualities, atStart = false, allowShort = true).firstOrNull())
            marker("prefix", collectMatches(prefixTokens, regions, atStart = false, allowShort = true).firstOrNull())
            marker("prefix", collectMatches(prefixTokens, providers, atStart = false, allowShort = true).firstOrNull())
        } else {
            marker("name", languageMatches.firstOrNull())
            marker("name", qualityMatches.firstOrNull())
            marker("name", collectMatches(allTokens, providers, atStart = false, allowShort = false).firstOrNull())
            marker("name", collectMatches(allTokens, regions, atStart = false, allowShort = false).firstOrNull())
        }
        formatMatches.forEach { marker("name", it) }
        if (ambiguousLanguage) {
            markers += "ambiguous languages: ${languageMatches.joinToString(", ") { it.compiled.rule.label }}"
        }

        return CategoryClassification(
            language = language?.let { CategoryFacetValue(it.key, it.label) },
            quality = quality?.let { CategoryFacetValue(it.key, it.label, it.rank) },
            region = region?.let { CategoryFacetValue(it.key, it.label) },
            provider = provider?.let { CategoryFacetValue(it.key, it.label) },
            formats = formatMatches.map { CategoryFacetValue(it.compiled.rule.key, it.compiled.rule.label) },
            ambiguousLanguage = ambiguousLanguage,
            groupKey = groupKey,
            groupLabel = groupLabel.ifBlank { "Other" },
            markers = markers,
        )
    }

    private fun compile(
        dimension: CategoryFacetDimension,
        rules: List<CategoryAliasRule>,
    ): List<CompiledRule> = rules.flatMapIndexed { ruleIndex, rule ->
        rule.aliases.mapNotNull { alias ->
            val aliasTokens = tokens(alias)
            if (aliasTokens.isEmpty()) {
                null
            } else {
                CompiledRule(
                    dimension = dimension,
                    rule = rule,
                    alias = alias,
                    aliasTokens = aliasTokens,
                    order = ruleIndex,
                )
            }
        }
    }

    private fun collectMatches(
        haystack: List<String>,
        rules: List<CompiledRule>,
        atStart: Boolean,
        allowShort: Boolean,
    ): List<RuleMatch> {
        val found = LinkedHashMap<String, RuleMatch>()
        rules.forEach { compiled ->
            if (!allowShort && compiled.aliasTokens.size == 1) {
                val alias = compiled.aliasTokens.first()
                if (alias.length <= 2 && alias in CATEGORY_AMBIGUOUS_SHORT_ALIASES) return@forEach
            }
            val hit = if (atStart) {
                matchesAt(haystack, 0, compiled.aliasTokens)
            } else {
                (0..haystack.size - compiled.aliasTokens.size).any { start ->
                    matchesAt(haystack, start, compiled.aliasTokens)
                }
            }
            if (!hit) return@forEach
            val previous = found[compiled.matchKey]
            if (previous == null ||
                compiled.aliasTokens.size > previous.tokenCount ||
                (compiled.aliasTokens.size == previous.tokenCount && compiled.order < previous.compiled.order)
            ) {
                found[compiled.matchKey] = RuleMatch(compiled, compiled.alias, compiled.aliasTokens.size)
            }
        }
        return found.values.sortedWith(
            compareByDescending<RuleMatch> { it.tokenCount }
                .thenBy { it.compiled.order },
        )
    }

    private fun matchesAt(haystack: List<String>, start: Int, needle: List<String>): Boolean {
        if (needle.isEmpty() || start < 0 || start + needle.size > haystack.size) return false
        return needle.indices.all { haystack[start + it] == needle[it] }
    }

    private fun rawLeadingLabel(name: String, tokenCount: Int): String {
        val words = name.trim().split(WHITESPACE).take(tokenCount)
        return words.joinToString(" ").trim { !it.isLetterOrDigit() }
    }
}

internal fun normalizeCategoryText(value: String): String {
    val decomposed = runCatching { Normalizer.normalize(value, Normalizer.Form.NFD) }.getOrDefault(value)
    return decomposed
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
}

private fun tokens(value: String): List<String> {
    val normalized = normalizeCategoryText(value)
    return if (normalized.isEmpty()) emptyList() else normalized.split(' ')
}

private val WHITESPACE = Regex("\\s+")
private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
