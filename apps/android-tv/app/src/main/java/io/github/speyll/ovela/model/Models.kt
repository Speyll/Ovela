package io.github.speyll.ovela.model

import androidx.compose.runtime.Immutable

/*
 * The data classes below are immutable value holders: every property is a
 * `val`, and collections are only ever replaced through `copy`, never mutated
 * in place. Annotating them lets the Compose compiler skip recomposition of
 * screens whose data did not change, which the compiler cannot infer for
 * classes that expose List/Set/Map. `CatalogItem` is already inferred stable
 * and is left alone; these four are the ones the stability report flagged.
 */

enum class ContentType { LIVE, MOVIE, SERIES, EPISODE }

enum class SourceType(val id: String, val label: String) {
    XTREAM("xtream", "Xtream"),
    M3U("m3u", "M3U playlist");

    companion object {
        fun fromId(value: String?): SourceType = entries.firstOrNull { it.id == value } ?: XTREAM
    }
}

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

@Immutable
data class AppSettings(
    val theme: AppTheme = AppTheme.BLUE,
    val streamFormat: StreamFormat = StreamFormat.AUTO,
    val maxCachedCategories: Int = 3,
    val favoriteAutoSort: Boolean = true,
    val hiddenCatalogCategories: Map<ContentType, Set<String>> = emptyMap(),
)

fun AppSettings.hiddenCategoryIds(type: ContentType): Set<String> =
    hiddenCatalogCategories[type].orEmpty()

@Immutable
data class Credentials(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val sourceType: SourceType = SourceType.XTREAM,
    val playlistUrl: String = "",
    val alternateServers: List<String> = emptyList(),
) {
    val isM3u: Boolean get() = sourceType == SourceType.M3U

    companion object {
        fun xtream(server: String, username: String, password: String): Credentials =
            Credentials(server = server, username = username, password = password)

        fun m3u(playlistUrl: String, alternateServers: List<String> = emptyList()): Credentials =
            Credentials(
                sourceType = SourceType.M3U,
                playlistUrl = playlistUrl,
                alternateServers = alternateServers,
            )
    }
}

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
    /** Direct stream URL for M3U entries. Xtream items continue to build URLs from provider data. */
    val streamUrl: String? = null,
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

@Immutable
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

@Immutable
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
