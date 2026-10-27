package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.FavoriteGroup
import io.github.speyll.ovela.model.FavoriteGroupAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteGroupSupportTest {
    @Test
    fun builtInGroupsUseStableDefaultAppearance() {
        assertEquals(
            FavoriteGroupAppearance("All Favorites", "heart", "purple"),
            defaultFavoriteGroupAppearance("all"),
        )
        assertEquals(
            FavoriteGroupAppearance("Live TV", "tv", "blue"),
            defaultFavoriteGroupAppearance("live"),
        )
    }

    @Test
    fun builtInAppearanceOverrideWins() {
        val override = FavoriteGroupAppearance("Channels", "star", "lime")
        assertEquals(
            override,
            favoriteGroupAppearance("live", mapOf("live" to override)),
        )
    }

    @Test
    fun firstVisibleGroupSkipsHiddenGroups() {
        assertEquals(
            "movie",
            firstVisibleFavoriteGroupId(
                order = listOf("all", "live", "movie", "series"),
                hidden = setOf("all", "live"),
            ),
        )
    }

    @Test
    fun allGroupsCanBeHidden() {
        assertEquals(
            "",
            firstVisibleFavoriteGroupId(
                order = BuiltInFavoriteGroupIds,
                hidden = BuiltInFavoriteGroupIds.toSet(),
            ),
        )
    }

    @Test
    fun validGroupIdsIncludeCustomGroups() {
        val custom = FavoriteGroup(id = "group-kids", name = "Kids")
        assertTrue("group-kids" in validFavoriteGroupIds(listOf(custom)))
    }
}
