package io.github.speyll.ovela.ui

import io.github.speyll.ovela.model.FavoriteGroupAppearance

val BuiltInFavoriteGroupIds: List<String> = listOf("all", "live", "movie", "series")

fun defaultFavoriteGroupAppearance(id: String): FavoriteGroupAppearance = when (id) {
    "live" -> FavoriteGroupAppearance("Live TV", "tv", "blue")
    "movie" -> FavoriteGroupAppearance("Movies", "popcorn", "teal")
    "series" -> FavoriteGroupAppearance("Series", "play", "orange")
    else -> FavoriteGroupAppearance("All Favorites", "heart", "purple")
}

fun favoriteGroupAppearance(
    id: String,
    overrides: Map<String, FavoriteGroupAppearance>,
): FavoriteGroupAppearance = overrides[id] ?: defaultFavoriteGroupAppearance(id)
