package com.xtreamlytv.androidtv.data

import android.util.Base64
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.EpgProgram
import com.xtreamlytv.androidtv.model.ProviderSummary
import com.xtreamlytv.androidtv.model.StreamFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class XtreamClient(
    private val credentials: Credentials,
    private val http: OkHttpClient = defaultHttpClient(),
) : SourceClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val server = ProviderUrl.normalize(credentials.server)

    override suspend fun authenticate(): ProviderSummary = withContext(Dispatchers.IO) {
        val root = requestJson(null)
        val info = root.obj("user_info") ?: error("Provider returned an invalid authentication response.")
        if (info.string("auth") != "1") error(info.string("message").ifBlank { "Login was rejected by the provider." })
        ProviderSummary(
            username = info.string("username").ifBlank { credentials.username },
            status = info.string("status").ifBlank { "Active" },
            expiration = info.string("exp_date").nullIfBlank(),
        )
    }

    override suspend fun categories(type: ContentType): List<Category> = withContext(Dispatchers.IO) {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
            ContentType.EPISODE -> error("Episodes do not have top-level categories")
        }
        normalizeCollection(requestJson(action), preferredCategoryKeys(type)).mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("category_id").ifBlank { obj.string("id") }
            if (id.isBlank()) null else Category(id, obj.string("category_name").ifBlank { "Category $id" })
        }
    }

    override suspend fun items(type: ContentType, categoryId: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
            ContentType.EPISODE -> error("Use seriesDetail")
        }
        val response = runCatching { requestJson(action, mapOf("category_id" to categoryId)) }
            .recoverCatching {
                if (type == ContentType.SERIES) requestJson("get_series_streams", mapOf("category_id" to categoryId))
                else throw it
            }.getOrThrow()
        normalizeCollection(response, preferredItemKeys(type)).mapNotNull { parseItem(it, type) }
    }

    override suspend fun movieInfo(movie: CatalogItem): CatalogItem = withContext(Dispatchers.IO) {
        val root = requestJson("get_vod_info", mapOf("vod_id" to movie.id))
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val data = root.obj("movie_data") ?: JsonObject(emptyMap())
        mergeRichItem(movie, data, info, ContentType.MOVIE)
    }

    override suspend fun seriesDetail(series: CatalogItem): Pair<CatalogItem, List<CatalogItem>> = withContext(Dispatchers.IO) {
        val root = requestJson("get_series_info", mapOf("series_id" to series.id))
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val richSeries = mergeRichItem(series, JsonObject(emptyMap()), info, ContentType.SERIES)
        val episodes = root.obj("episodes")?.entries
            ?.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            ?.flatMap { (seasonKey, value) ->
                val season = seasonKey.toIntOrNull()
                normalizeCollection(value).mapNotNull { element ->
                    val item = parseItem(element, ContentType.EPISODE) ?: return@mapNotNull null
                    item.copy(
                        season = item.season ?: season,
                        imageUrl = item.imageUrl ?: richSeries.coverUrl ?: richSeries.imageUrl,
                        parentSeriesId = series.id,
                        parentSeriesName = richSeries.name,
                        parentSeriesImageUrl = richSeries.coverUrl ?: richSeries.imageUrl,
                        parentSeriesBackdropUrl = richSeries.backdropUrl ?: series.backdropUrl,
                    )
                }
            }.orEmpty()
        richSeries to episodes
    }

    override suspend fun shortEpg(streamId: String, limit: Int): List<EpgProgram> = withContext(Dispatchers.IO) {
        val root = requestJson("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString()))
        val listing = (root["epg_listings"] as? JsonArray)
            ?: (root["data"] as? JsonArray)
            ?: return@withContext emptyList()
        listing.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val start = obj.string("start_timestamp").toLongOrNull()?.times(1000)
                ?: obj.string("start").toLongOrNull()?.times(1000)
                ?: return@mapNotNull null
            val end = obj.string("stop_timestamp").toLongOrNull()?.times(1000)
                ?: obj.string("end_timestamp").toLongOrNull()?.times(1000)
                ?: obj.string("stop").toLongOrNull()?.times(1000)
                ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            EpgProgram(
                streamId = streamId,
                title = decodeMaybeBase64(obj.string("title")).ifBlank { "Programming" },
                description = decodeMaybeBase64(obj.string("description")).nullIfBlank(),
                startEpochMs = start,
                endEpochMs = end,
            )
        }
    }

    override fun streamCandidates(item: CatalogItem, preferredFormat: StreamFormat): List<String> =
        StreamUrlBuilder.candidates(credentials, item, preferredFormat)

    private fun mergeRichItem(base: CatalogItem, data: JsonObject, info: JsonObject, type: ContentType): CatalogItem {
        val cover = firstNonBlank(
            info.string("cover"), info.string("cover_big"), info.string("movie_image"),
            data.string("stream_icon"), base.coverUrl, base.imageUrl,
        )
        val backdrop = firstBackdrop(info) ?: firstBackdrop(data) ?: base.backdropUrl
        val runtimeMinutes = info.primitive("runtime")?.intOrNull
            ?: info.primitive("episode_run_time")?.intOrNull
            ?: info.string("duration_secs").toLongOrNull()?.div(60)?.toInt()
            ?: base.runtimeMinutes
        val video = info.obj("video")
        val audio = info.obj("audio")
        val resolution = video?.let {
            val height = it.primitive("height")?.intOrNull ?: 0
            when {
                height >= 2160 -> "4K"
                height >= 1440 -> "1440p"
                height >= 1080 -> "1080p"
                height >= 720 -> "720p"
                height > 0 -> "${height}p"
                else -> null
            }
        } ?: base.resolution
        val audioLayout = audio?.string("channel_layout").nullIfBlank()
            ?: audio?.primitive("channels")?.intOrNull?.let { if (it == 6) "5.1" else if (it == 2) "Stereo" else "${it}ch" }
            ?: base.audioLayout
        val rating = info.primitive("rating")?.doubleOrNull
            ?: info.primitive("rating_5based")?.doubleOrNull?.let { if (it <= 5.0) it * 2.0 else it }
            ?: base.rating
        return base.copy(
            name = firstNonBlank(info.string("name"), info.string("o_name"), base.name) ?: base.name,
            imageUrl = if (type == ContentType.MOVIE) firstNonBlank(data.string("stream_icon"), base.imageUrl, cover) else firstNonBlank(base.imageUrl, cover),
            coverUrl = cover,
            backdropUrl = backdrop,
            containerExtension = firstNonBlank(data.string("container_extension"), base.containerExtension),
            rating = rating,
            plot = firstNonBlank(info.string("plot"), info.string("description"), base.plot),
            genre = firstNonBlank(info.string("genre"), base.genre),
            releaseDate = firstNonBlank(info.string("releasedate"), info.string("releaseDate"), info.string("release_date"), base.releaseDate),
            runtimeMinutes = runtimeMinutes,
            director = firstNonBlank(info.string("director"), base.director),
            cast = firstNonBlank(info.string("cast"), info.string("actors"), base.cast),
            country = firstNonBlank(info.string("country"), base.country),
            youtubeTrailer = firstNonBlank(info.string("youtube_trailer"), base.youtubeTrailer),
            tmdbId = firstNonBlank(info.string("tmdb_id"), info.string("tmdb"), base.tmdbId),
            resolution = resolution,
            audioLayout = audioLayout,
        )
    }

    private fun requestJson(action: String?, extras: Map<String, String> = emptyMap()): JsonObject {
        val base = "$server/player_api.php".toHttpUrlOrNull() ?: error("Provider URL is invalid.")
        val builder = base.newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
        if (!action.isNullOrBlank()) builder.addQueryParameter("action", action)
        extras.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder().url(builder.build()).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Provider returned HTTP ${response.code}.")
            val body = response.body?.string().orEmpty()
            val element = runCatching { json.parseToJsonElement(body) }.getOrElse { error("Provider returned invalid JSON.") }
            return element as? JsonObject ?: JsonObject(mapOf("data" to element))
        }
    }

    private fun parseItem(element: JsonElement, type: ContentType): CatalogItem? {
        val obj = element as? JsonObject ?: return null
        val id = when (type) {
            ContentType.SERIES -> obj.string("series_id").ifBlank { obj.string("id") }
            ContentType.EPISODE -> obj.string("id").ifBlank { obj.string("episode_id").ifBlank { obj.string("stream_id") } }
            else -> obj.string("stream_id").ifBlank { obj.string("id") }
        }
        if (id.isBlank()) return null
        val name = obj.string("name").ifBlank { obj.string("title").ifBlank { "Untitled" } }
        val info = obj.obj("info")
        val cover = when (type) {
            ContentType.LIVE -> obj.string("stream_icon")
            ContentType.MOVIE -> firstNonBlank(obj.string("stream_icon"), obj.string("cover"), obj.string("cover_big"), obj.string("movie_image"))
            ContentType.SERIES -> firstNonBlank(obj.string("cover"), obj.string("cover_big"), obj.string("stream_icon"))
            ContentType.EPISODE -> firstNonBlank(info?.string("movie_image"), obj.string("cover"))
        }.nullIfBlank()
        val rating = obj.primitive("rating")?.doubleOrNull
            ?: obj.primitive("rating_5based")?.doubleOrNull?.let { if (it <= 5.0) it * 2.0 else it }
        val runtime = obj.primitive("runtime")?.intOrNull ?: obj.primitive("episode_run_time")?.intOrNull
        return CatalogItem(
            id = id,
            type = type,
            name = name,
            categoryId = obj.string("category_id").nullIfBlank(),
            imageUrl = cover,
            coverUrl = if (type == ContentType.LIVE) null else cover,
            backdropUrl = firstBackdrop(obj) ?: info?.let(::firstBackdrop),
            containerExtension = obj.string("container_extension").nullIfBlank(),
            rating = rating,
            plot = obj.string("plot").ifBlank { obj.string("description") }.ifBlank { info?.string("plot").orEmpty() }.nullIfBlank(),
            season = obj.primitive("season")?.intOrNull,
            episode = obj.primitive("episode_num")?.intOrNull,
            channelNumber = obj.string("num").ifBlank { obj.string("channel_number") }.nullIfBlank(),
            genre = obj.string("genre").ifBlank { info?.string("genre").orEmpty() }.nullIfBlank(),
            releaseDate = obj.string("releaseDate").ifBlank { obj.string("release_date") }.ifBlank { info?.string("releasedate").orEmpty() }.nullIfBlank(),
            runtimeMinutes = runtime,
            director = obj.string("director").nullIfBlank(),
            cast = obj.string("cast").ifBlank { obj.string("actors") }.nullIfBlank(),
            country = obj.string("country").nullIfBlank(),
            youtubeTrailer = obj.string("youtube_trailer").nullIfBlank(),
            tmdbId = obj.string("tmdb").ifBlank { obj.string("tmdb_id") }.nullIfBlank(),
        )
    }

    private fun firstBackdrop(obj: JsonObject): String? {
        val value = obj["backdrop_path"]
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.firstOrNull { validArtwork(it) }
            is JsonPrimitive -> value.contentOrNull?.takeIf(::validArtwork)
            else -> firstNonBlank(obj.string("backdrop"), obj.string("fanart"), obj.string("fan_art"))?.takeIf(::validArtwork)
        }
    }

    private fun validArtwork(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        if (normalized.contains("noposter.jpg", ignoreCase = true)) return false
        return normalized.startsWith("http://", true) || normalized.startsWith("https://", true)
    }

    private fun normalizeCollection(element: JsonElement, preferredKeys: List<String> = emptyList()): List<JsonElement> {
        if (element is JsonArray) return element
        if (element !is JsonObject) return emptyList()
        val keys = preferredKeys + listOf("data", "results", "items", "streams", "channels", "movies", "series", "categories")
        keys.forEach { key -> (element[key] as? JsonArray)?.let { return it } }
        return element.values.filterIsInstance<JsonObject>()
    }

    private fun preferredCategoryKeys(type: ContentType) = when (type) {
        ContentType.LIVE -> listOf("live_categories")
        ContentType.MOVIE -> listOf("vod_categories")
        ContentType.SERIES -> listOf("series_categories")
        ContentType.EPISODE -> emptyList()
    }

    private fun preferredItemKeys(type: ContentType) = when (type) {
        ContentType.LIVE -> listOf("live_streams")
        ContentType.MOVIE -> listOf("vod_streams")
        ContentType.SERIES -> listOf("series")
        ContentType.EPISODE -> listOf("episodes")
    }

    companion object {
        private fun defaultHttpClient() = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
private fun JsonObject.string(key: String): String = primitive(key)?.contentOrNull.orEmpty()
private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

private fun decodeMaybeBase64(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val bytes = Base64.decode(trimmed, Base64.DEFAULT)
        val decoded = bytes.toString(Charsets.UTF_8)
        decoded.takeIf { text -> text.any { ch -> ch.isLetterOrDigit() } && !text.contains('\u0000') } ?: trimmed
    }.getOrDefault(trimmed)
}
