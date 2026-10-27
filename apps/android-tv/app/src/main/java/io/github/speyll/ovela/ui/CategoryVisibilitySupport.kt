package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType

/**
 * Pure selection helpers for the category visibility screen.
 *
 * The persisted set is never mutated directly by the UI. The screen works on a
 * staged copy, and these helpers make the bulk operations deterministic and
 * unit-testable without Android or Compose.
 */

internal const val ALL_CATEGORY_GROUPS = "__all__"
internal const val UNRECOGNIZED_CATEGORY_GROUP = "__other__"

internal data class CategoryPrefixGroup(
    val key: String,
    val label: String,
    val categories: List<Category>,
) {
    val ids: List<String> get() = categories.map { it.id }
}

/**
 * A provider prefix is whatever the provider put before a `|` delimiter. It is
 * preserved as its own group label; the classifier separately interprets only
 * known aliases. Implausibly long or punctuation-only prefixes are ignored so a
 * normal title such as `News | World` is not silently regrouped.
 */
internal fun categoryPrefix(name: String): String? {
    val trimmed = name.trim()
    val separator = trimmed.indexOf('|')
    if (separator <= 0 || separator >= trimmed.length - 1) return null
    if (trimmed.substring(separator + 1).isBlank()) return null
    val prefix = trimmed.substring(0, separator).trim()
    if (prefix.isEmpty() || prefix.length > 16) return null
    if (prefix.any { it.isISOControl() }) return null
    if (prefix.none { it.isLetterOrDigit() }) return null
    return prefix
}

/**
 * Groups use the provider prefix when present, otherwise a recognized leading
 * language/quality/region/provider marker (for example `ENGLISH MOVIES`). The
 * original provider spelling is kept as the label and categories stay in the
 * order supplied by the caller.
 */
internal fun categoryGroups(categories: List<Category>): List<CategoryPrefixGroup> {
    val labels = LinkedHashMap<String, String>()
    val members = LinkedHashMap<String, MutableList<Category>>()
    val other = mutableListOf<Category>()
    categories.forEach { category ->
        val classification = classifyCategory(category.name)
        if (classification.groupKey == UNRECOGNIZED_CATEGORY_GROUP) {
            other += category
        } else {
            if (!labels.containsKey(classification.groupKey)) {
                labels[classification.groupKey] = classification.groupLabel
            }
            members.getOrPut(classification.groupKey) { mutableListOf() } += category
        }
    }
    val groups = members.map { (key, items) ->
        CategoryPrefixGroup(key = key, label = labels[key] ?: key, categories = items)
    }.sortedBy { it.label.lowercase() }
    return if (other.isEmpty()) {
        groups
    } else {
        groups + CategoryPrefixGroup(
            key = UNRECOGNIZED_CATEGORY_GROUP,
            label = "Other",
            categories = other,
        )
    }
}

internal fun toggleHiddenCategory(current: Set<String>, categoryId: String): Set<String> =
    if (categoryId in current) current - categoryId else current + categoryId

/**
 * Applies an explicit visibility request instead of toggling. Callers that
 * already know the desired state stay idempotent even if the request is
 * replayed or the persisted set changed between the tap and the write.
 */
internal fun setHiddenCategory(current: Set<String>, categoryId: String, hidden: Boolean): Set<String> =
    if (hidden) current + categoryId else current - categoryId

internal fun setHiddenCategories(
    current: Set<String>,
    categoryIds: Collection<String>,
    hidden: Boolean,
): Set<String> =
    if (hidden) current + categoryIds else current - categoryIds.toSet()

internal fun changedCategoryCount(persisted: Set<String>, staged: Set<String>): Int =
    (persisted - staged).size + (staged - persisted).size

internal data class CategoryChangeSummary(
    val hideCount: Int,
    val showCount: Int,
) {
    val total: Int get() = hideCount + showCount
}

internal fun categoryChangeSummary(persisted: Set<String>, staged: Set<String>): CategoryChangeSummary =
    CategoryChangeSummary(
        hideCount = (staged - persisted).size,
        showCount = (persisted - staged).size,
    )

/**
 * A non-empty change becomes one atomic write. The previous persisted set is
 * the undo snapshot, so Apply and Undo never have to reconstruct intent.
 */
internal data class CategoryApplyPlan(
    val hidden: Set<String>,
    val undo: Set<String>,
)

internal fun categoryApplyPlan(persisted: Set<String>, staged: Set<String>): CategoryApplyPlan? =
    if (persisted == staged) null else CategoryApplyPlan(hidden = staged, undo = persisted)

internal fun categoryVisibilityMatches(
    categoryId: String,
    name: String,
    hidden: Boolean,
    hiddenOnly: Boolean,
    query: String,
    groupKey: String = ALL_CATEGORY_GROUPS,
    languageKey: String = ALL_CATEGORY_FACETS,
    qualityKey: String = ALL_CATEGORY_FACETS,
): Boolean {
    if (!categoryMatchesGroup(name, groupKey)) return false
    if (!categoryMatchesFacet(name, CategoryFacetDimension.LANGUAGE, languageKey)) return false
    if (!categoryMatchesFacet(name, CategoryFacetDimension.QUALITY, qualityKey)) return false
    if (hiddenOnly && !hidden) return false
    val normalized = query.trim()
    return normalized.isBlank() || name.contains(normalized, ignoreCase = true) || categoryId.contains(normalized, ignoreCase = true)
}

/**
 * Cache keys are `TYPE:categoryId`. When a category is hidden its loaded
 * contents are no longer reachable from browse, so keeping the list only holds
 * artwork URLs and item objects alive. Favorites, history and progress live in
 * separate state and are intentionally untouched.
 */
internal fun categoryCacheKeysToEvict(
    keys: Collection<String>,
    type: ContentType,
    hiddenIds: Set<String>,
): Set<String> {
    if (hiddenIds.isEmpty()) return emptySet()
    val prefix = "${type.name}:"
    return keys.filterTo(mutableSetOf()) { key ->
        key.startsWith(prefix) && key.substringAfter(':') in hiddenIds
    }
}

internal fun List<CatalogItem>.withoutHiddenCategories(
    type: ContentType,
    hiddenIds: Set<String>,
): List<CatalogItem> =
    if (hiddenIds.isEmpty()) this else filterNot { it.type == type && it.categoryId in hiddenIds }
