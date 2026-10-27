package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassificationTest {
    private fun category(id: String, name: String) = Category(id = id, name = name)

    @Test
    fun prefixesAndFullWordAliasesClassifyAllThreeSections() {
        val sections = mapOf(
            ContentType.LIVE to listOf(
                category("l1", "AR| News"),
                category("l2", "FRANCE | Cinéma"),
                category("l3", "ESPAÑA 4K | Deportes"),
            ),
            ContentType.MOVIE to listOf(
                category("m1", "ENGLISH MOVIES"),
                category("m2", "FRENCH 4K MOVIES"),
                category("m3", "SPANISH FHD"),
            ),
            ContentType.SERIES to listOf(
                category("s1", "DEUTSCH| Serien"),
                category("s2", "TURKISH DRAMA"),
                category("s3", "Documentary SD"),
            ),
        )
        assertEquals(CategoryFacetValue("ar", "Arabic"), classifyCategory("AR| News").language)
        assertEquals("FRANCE", classifyCategory("FRANCE | Cinéma").groupKey)
        assertEquals(CategoryFacetValue("fr", "French"), classifyCategory("FRANCE | Cinéma").language)
        assertEquals("Spain", classifyCategory("ESPAÑA 4K | Deportes").region?.label)
        assertEquals(CategoryFacetValue("en", "English"), classifyCategory("ENGLISH MOVIES").language)
        assertEquals("ENGLISH", classifyCategory("ENGLISH MOVIES").groupKey)
        assertEquals(CategoryFacetValue("4k", "4K", 40), classifyCategory("FRENCH 4K MOVIES").quality)
        assertEquals("de", classifyCategory("DEUTSCH| Serien").language?.key)
        assertEquals("tr", classifyCategory("TURKISH DRAMA").language?.key)
        assertEquals("sd", classifyCategory("Documentary SD").quality?.key)

        sections.forEach { (type, categories) ->
            val languages = categoryFacets(categories, CategoryFacetDimension.LANGUAGE)
            val qualities = categoryFacets(categories, CategoryFacetDimension.QUALITY)
            assertTrue("$type should expose languages", languages.any { it.key != UNCLASSIFIED_CATEGORY_FACET })
            assertTrue("$type should expose quality", qualities.any { it.key != UNCLASSIFIED_CATEGORY_FACET })
        }
    }

    @Test
    fun qualityAndFormatMarkersAreIndependentOfLanguage() {
        val classification = classifyCategory("FRENCH 4K HEVC MOVIES")
        assertEquals("fr", classification.language?.key)
        assertEquals("4k", classification.quality?.key)
        assertEquals(listOf("HEVC"), classification.formats.map { it.label })
        assertTrue(classification.summary.contains("French"))
        assertTrue(classification.summary.contains("4K"))
    }

    @Test
    fun unknownWordsAndSubstringsAreNotFalsePositives() {
        val plain = classifyCategory("Entertainment")
        assertNull(plain.language)
        assertNull(plain.quality)
        assertEquals(UNRECOGNIZED_CATEGORY_GROUP, plain.groupKey)
        assertEquals("Unclassified", plain.summary)

        val news = classifyCategory("Plain News")
        assertNull(news.language)
        assertEquals(UNRECOGNIZED_CATEGORY_GROUP, news.groupKey)

        val hd = classifyCategory("News HD")
        assertEquals("hd", hd.quality?.key)
        assertNull(hd.language)
    }

    @Test
    fun ambiguousLanguageMarkersStayUnclassified() {
        val ambiguous = classifyCategory("AR EN| Mixed")
        assertNull(ambiguous.language)
        assertTrue(ambiguous.ambiguousLanguage)
        assertTrue(ambiguous.summary.contains("Multiple languages"))
        assertTrue(ambiguous.markers.any { it.contains("ambiguous languages") })
        assertTrue(categoryMatchesFacet("AR EN| Mixed", CategoryFacetDimension.LANGUAGE, UNCLASSIFIED_CATEGORY_FACET))
        assertFalse(categoryMatchesFacet("AR EN| Mixed", CategoryFacetDimension.LANGUAGE, "ar"))
    }

    @Test
    fun accentsAndCaseAreNormalizedWithoutLosingOriginalName() {
        val classification = classifyCategory("  espaÑa | DEPORTES  ")
        assertEquals("es", classification.language?.key)
        assertEquals("Spain", classification.region?.label)
        assertEquals("espaÑa", classification.groupLabel)
        assertEquals("espaÑa | DEPORTES", "  espaÑa | DEPORTES  ".trim())
    }

    @Test
    fun providerMetadataIsReviewableWithoutBecomingALanguage() {
        val netflix = classifyCategory("NETFLIX| Originals")
        assertEquals("netflix", netflix.provider?.key)
        assertNull(netflix.language)
        assertTrue(netflix.markers.any { it.contains("Netflix") })
    }

    @Test
    fun facetsCountAndOrderLanguagesAndQualities() {
        val categories = listOf(
            category("1", "English News"),
            category("2", "FRENCH Cinema"),
            category("3", "4K| UHD"),
            category("4", "Plain"),
        )
        val languages = categoryFacets(categories, CategoryFacetDimension.LANGUAGE)
        assertEquals(listOf("English", "French", "Unclassified"), languages.map { it.label })
        assertEquals(listOf(1, 1, 2), languages.map { it.count })
        val qualities = categoryFacets(categories, CategoryFacetDimension.QUALITY)
        assertEquals(listOf("4K", "Unclassified"), qualities.map { it.label })
        assertEquals(listOf(1, 3), qualities.map { it.count })
    }

    @Test
    fun visibilityMatchesComposeLanguageQualitySearchAndHiddenOnly() {
        assertTrue(
            categoryVisibilityMatches(
                "fr-1", "FRENCH 4K Cinema", hidden = false, hiddenOnly = false,
                query = "cinema", languageKey = "fr", qualityKey = "4k",
            ),
        )
        assertFalse(
            categoryVisibilityMatches(
                "fr-1", "FRENCH 4K Cinema", hidden = false, hiddenOnly = false,
                query = "cinema", languageKey = "en", qualityKey = "4k",
            ),
        )
        assertFalse(
            categoryVisibilityMatches(
                "fr-1", "FRENCH 4K Cinema", hidden = true, hiddenOnly = false,
                query = "", languageKey = "fr", qualityKey = "hd",
            ),
        )
        assertFalse(
            categoryVisibilityMatches(
                "fr-1", "FRENCH 4K Cinema", hidden = false, hiddenOnly = true,
                query = "", languageKey = "fr", qualityKey = "4k",
            ),
        )
    }

    @Test
    fun classificationCacheIsBoundedForLargeCategoryLists() {
        repeat(2_000) { index ->
            val unknown = classifyCategory("Unclassified provider group $index")
            assertNull(unknown.language)
            assertEquals(UNRECOGNIZED_CATEGORY_GROUP, unknown.groupKey)
        }
        assertTrue(categoryClassificationCacheSize() <= 512)
        assertTrue(categoryClassificationCacheSize() > 0)
    }

    @Test
    fun hiddenCategoryCacheKeysAndItemsAreScopedByType() {
        val keys = setOf("LIVE:1", "LIVE:2", "MOVIE:1", "SERIES:9")
        assertEquals(
            setOf("LIVE:1", "LIVE:2"),
            categoryCacheKeysToEvict(keys, ContentType.LIVE, setOf("1", "2")),
        )
        assertEquals(emptySet<String>(), categoryCacheKeysToEvict(keys, ContentType.LIVE, emptySet()))

        val items = listOf(
            CatalogItem(id = "live-1", type = ContentType.LIVE, name = "News", categoryId = "1"),
            CatalogItem(id = "live-2", type = ContentType.LIVE, name = "Sport", categoryId = "2"),
            CatalogItem(id = "movie-1", type = ContentType.MOVIE, name = "Film", categoryId = "1"),
        )
        val visible = items.withoutHiddenCategories(ContentType.LIVE, setOf("1"))
        assertEquals(listOf("live-2", "movie-1"), visible.map { it.id })
        assertNotNull(visible.firstOrNull { it.id == "movie-1" })
    }
}
