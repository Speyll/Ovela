package com.xtreamlytv.androidtv.model

enum class ContentType { LIVE, MOVIE, SERIES, EPISODE }

enum class AppTheme(val id: String, val label: String) {
    BLUE("blue", "Blue"),
    TEAL("teal", "Teal"),
    GRAPHITE("graphite", "Graphite"),
    PURPLE("purple", "Purple"),
    PINK("pink", "Pink");

    companion object {
        fun fromId(value: String?): AppTheme = entries.firstOrNull { it.id == value } ?: BLUE
    }
}

enum class StreamFormat(val id: String, val label: String) {
    AUTO("auto", "Automatic fallback"),
    HLS("m3u8", "HLS first"),
    MPEG_TS("ts", "MPEG-TS first");

    companion object {
        fun fromId(value: String?): StreamFormat = entries.firstOrNull { it.id == value } ?: AUTO
    }
}

data class AppSettings(
    val theme: AppTheme = AppTheme.BLUE,
    val streamFormat: StreamFormat = StreamFormat.AUTO,
    val maxCachedCategories: Int = 3,
    val favoriteAutoSort: Boolean = true,
)

data class Credentials(
    val server: String,
    val username: String,
    val password: String,
)

data class Category(
    val id: String,
    val name: String,
)

data class CatalogItem(
    val id: String,
    val type: ContentType,
    val name: String,
    val categoryId: String? = null,
    /** Primary card/channel artwork. */
    val imageUrl: String? = null,
    /** Poster/cover artwork when distinct from imageUrl. */
    val coverUrl: String? = null,
    /** 16:9 hero/fanart artwork. */
    val backdropUrl: String? = null,
    val containerExtension: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val channelNumber: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val youtubeTrailer: String? = null,
    val tmdbId: String? = null,
    val resolution: String? = null,
    val audioLayout: String? = null,
    val parentSeriesId: String? = null,
    val parentSeriesName: String? = null,
    val parentSeriesImageUrl: String? = null,
    val parentSeriesBackdropUrl: String? = null,
)

data class EpgProgram(
    val streamId: String,
    val title: String,
    val description: String? = null,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

data class FavoriteGroup(
    val id: String,
    val name: String,
    val icon: String = "folder",
    val color: String = "purple",
    val itemKeys: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class FavoriteGroupAppearance(
    val name: String,
    val icon: String,
    val color: String,
)

data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class PlayerRequest(
    val item: CatalogItem,
    val queue: List<CatalogItem>,
    val urlCandidates: List<String>,
    val startPositionMs: Long = 0L,
)

data class ProviderSummary(
    val username: String,
    val status: String,
    val expiration: String?,
)
