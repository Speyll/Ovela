package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryVisibilitySupportTest {
    private fun category(id: String, name: String) = Category(id = id, name = name)

    @Test
    fun toggleAddsAndRemovesWithoutTouchingOtherCategories() {
        val current = setOf("news", "sport")
        assertEquals(setOf("news", "sport", "movies"), toggleHiddenCategory(current, "movies"))
        assertEquals(setOf("sport"), toggleHiddenCategory(current, "news"))
    }

    @Test
    fun explicitSetHonorsRequestedVisibilityAndIsIdempotent() {
        val current = setOf("news")
        assertEquals(setOf("news", "sport"), setHiddenCategory(current, "sport", hidden = true))
        assertEquals(setOf("news", "sport"), setHiddenCategory(setOf("news", "sport"), "sport", hidden = true))
        assertEquals(emptySet<String>(), setHiddenCategory(current, "news", hidden = false))
        assertEquals(current, setHiddenCategory(current, "sport", hidden = false))
    }

    @Test
    fun bulkSetOnlyAffectsTheRequestedIds() {
        val current = setOf("news", "sport", "kids")
        assertEquals(
            setOf("news"),
            setHiddenCategories(current, listOf("sport", "kids"), hidden = false),
        )
        assertEquals(
            setOf("news", "sport", "kids", "movies", "docs"),
            setHiddenCategories(current, listOf("movies", "docs"), hidden = true),
        )
    }

    @Test
    fun changedCountCountsBothDirections() {
        assertEquals(0, changedCategoryCount(setOf("a"), setOf("a")))
        assertEquals(2, changedCategoryCount(setOf("a", "b"), setOf("b", "c")))
    }

    @Test
    fun hiddenOnlyFilterExcludesVisibleRows() {
        assertFalse(categoryVisibilityMatches("news", "News", hidden = false, hiddenOnly = true, query = ""))
        assertTrue(categoryVisibilityMatches("news", "News", hidden = true, hiddenOnly = true, query = ""))
    }

    @Test
    fun queryMatchesNameOrIdCaseInsensitively() {
        assertTrue(categoryVisibilityMatches("sport-4k", "Sport", hidden = false, hiddenOnly = false, query = "sport"))
        assertTrue(categoryVisibilityMatches("sport-4k", "Athletics", hidden = false, hiddenOnly = false, query = "4K"))
        assertFalse(categoryVisibilityMatches("news", "News", hidden = false, hiddenOnly = false, query = "movies"))
    }

    @Test
    fun prefixesAreReadGenericallyAndNotAssumedToBeLanguages() {
        assertEquals("AR", categoryPrefix("AR| News"))
        assertEquals("FR", categoryPrefix("  FR | Sport"))
        assertEquals("4K", categoryPrefix("4K| UHD"))
        assertEquals("de", categoryPrefix("de|Dokumentation"))
        assertEquals("News", categoryPrefix("News | World"))
    }

    @Test
    fun malformedOrMissingPrefixesAreUnrecognized() {
        assertNull(categoryPrefix("No prefix at all"))
        assertNull(categoryPrefix("|Leading delimiter"))
        assertNull(categoryPrefix("Trailing|"))
        assertNull(categoryPrefix("A prefix that is far too long|Name"))
        assertNull(categoryPrefix("?!|Symbols"))
        assertEquals(UNRECOGNIZED_CATEGORY_GROUP, categoryGroupKey("No prefix at all"))
    }

    @Test
    fun prefixKeysAreCaseInsensitiveAndLabelPreservesProviderSpelling() {
        assertEquals("AR", categoryGroupKey("ar| News"))
        assertEquals("AR", categoryGroupKey("Ar| Sport"))
        val groups = categoryGroups(
            listOf(
                category("1", "ar| News"),
                category("2", "AR| Sport"),
                category("3", "FR| Cinema"),
            ),
        )
        assertEquals(listOf("AR", "FR"), groups.map { it.key })
        assertEquals("ar", groups.first().label)
        assertEquals(listOf("1", "2"), groups.first().ids)
    }

    @Test
    fun groupsPutUnrecognizedCategoriesInTheirOwnLastGroup() {
        val groups = categoryGroups(
            listOf(
                category("a", "No prefix"),
                category("b", "EN| Movies"),
                category("c", "4K| UHD"),
                category("d", "Another plain name"),
            ),
        )
        assertEquals(listOf("4K", "EN", UNRECOGNIZED_CATEGORY_GROUP), groups.map { it.key })
        assertEquals("Other", groups.last().label)
        assertEquals(listOf("a", "d"), groups.last().ids)
    }

    @Test
    fun groupHideAndShowComposeWithMixedExistingState() {
        val group = categoryGroups(
            listOf(category("ar-1", "AR| News"), category("ar-2", "AR| Sport")),
        ).first()
        val persisted = setOf("outside", "ar-1")
        val hidden = setHiddenCategories(persisted, group.ids, hidden = true)
        assertEquals(setOf("outside", "ar-1", "ar-2"), hidden)

        val shown = setHiddenCategories(hidden, group.ids, hidden = false)
        assertEquals(setOf("outside"), shown)
    }

    @Test
    fun groupActionsCanBeScopedToFilteredResultsOnly() {
        val all = listOf(
            category("ar-1", "AR| News"),
            category("ar-2", "AR| Sport"),
            category("fr-1", "FR| Cinema"),
        )
        val filtered = all.filter { categoryVisibilityMatches(it.id, it.name, hidden = false, hiddenOnly = false, query = "sport") }
        val persisted = setOf("fr-1")
        val staged = setHiddenCategories(persisted, filtered.map { it.id }, hidden = true)
        assertEquals(setOf("fr-1", "ar-2"), staged)
        assertEquals(setOf("fr-1", "ar-2"), staged) // categories outside the view are untouched
    }

    @Test
    fun groupFilterCombinesWithSearchAndHiddenOnly() {
        val visible = categoryVisibilityMatches("ar-1", "AR| News", hidden = false, hiddenOnly = false, query = "news", groupKey = "AR")
        assertTrue(visible)
        assertFalse(categoryVisibilityMatches("ar-2", "AR| Sport", hidden = false, hiddenOnly = false, query = "news", groupKey = "AR"))
        assertFalse(categoryVisibilityMatches("ar-1", "AR| News", hidden = false, hiddenOnly = true, query = "", groupKey = "AR"))
        assertFalse(categoryVisibilityMatches("ar-1", "AR| News", hidden = false, hiddenOnly = false, query = "", groupKey = "FR"))
        assertTrue(categoryVisibilityMatches("plain", "Plain", hidden = false, hiddenOnly = false, query = "", groupKey = UNRECOGNIZED_CATEGORY_GROUP))
    }

    @Test
    fun changeSummaryReportsHideAndShowSeparately() {
        val summary = categoryChangeSummary(
            persisted = setOf("a", "b", "c"),
            staged = setOf("b", "d", "e"),
        )
        assertEquals(2, summary.hideCount)
        assertEquals(2, summary.showCount)
        assertEquals(4, summary.total)
    }

    @Test
    fun applyPlanKeepsThePreviousSetForUndoAndIsEmptyWhenNothingChanged() {
        assertNull(categoryApplyPlan(setOf("a"), setOf("a")))
        val plan = categoryApplyPlan(persisted = setOf("a"), staged = setOf("a", "b"))!!
        assertEquals(setOf("a", "b"), plan.hidden)
        assertEquals(setOf("a"), plan.undo)
        assertEquals(setOf("a"), categoryApplyPlan(setOf("a", "b"), setOf("a"))!!.hidden)
    }
}
