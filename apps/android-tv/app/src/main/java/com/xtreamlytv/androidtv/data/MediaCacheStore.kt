package com.xtreamlytv.androidtv.data

import android.content.Context
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small persistent metadata cache mirroring the v0.7 webOS cache strategy.
 * Images remain in Coil/HTTP disk cache; this store only retains lightweight
 * metadata and compact episode records.
 */
class MediaCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SeriesPayload(val series: CatalogItem, val episodes: List<CatalogItem>)

    fun getMovie(id: String): CatalogItem? = getItem("movie:$id", MOVIE_TTL_MS)

    fun putMovie(item: CatalogItem) {
        putEntry("movie:${item.id}", itemToJson(item), MAX_MOVIES, "movie:")
    }

    fun getSeries(id: String): SeriesPayload? {
        val entry = getEntry("series:$id", SERIES_TTL_MS) ?: return null
        val series = entry.optJSONObject("item")?.toCatalogItem() ?: return null
        val episodesArray = entry.optJSONArray("episodes") ?: JSONArray()
        val episodes = buildList {
            for (index in 0 until episodesArray.length()) {
                episodesArray.optJSONObject(index)?.toCatalogItem()?.let(::add)
            }
        }
        touch("series:$id", entry)
        return SeriesPayload(series, episodes)
    }

    fun putSeries(series: CatalogItem, episodes: List<CatalogItem>) {
        val payload = JSONObject()
            .put("item", itemToJson(series))
            .put("episodes", JSONArray().also { array -> episodes.take(MAX_EPISODES_PER_SERIES).forEach { array.put(itemToJson(it)) } })
        putEntry("series:${series.id}", payload, MAX_SERIES, "series:")
    }

    fun clear() = prefs.edit().clear().apply()

    private fun getItem(key: String, ttl: Long): CatalogItem? {
        val entry = getEntry(key, ttl) ?: return null
        val item = entry.optJSONObject("item")?.toCatalogItem()
        if (item != null) touch(key, entry)
        return item
    }

    private fun getEntry(key: String, ttl: Long): JSONObject? {
        val raw = prefs.getString(key, null) ?: return null
        val entry = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val updated = entry.optLong("updatedAt", 0L)
        if (updated <= 0L || System.currentTimeMillis() - updated > ttl) {
            prefs.edit().remove(key).apply()
            return null
        }
        return entry
    }

    private fun touch(key: String, entry: JSONObject) {
        entry.put("lastAccess", System.currentTimeMillis())
        prefs.edit().putString(key, entry.toString()).apply()
    }

    private fun putEntry(key: String, payload: JSONObject, maxEntries: Int, prefix: String) {
        val now = System.currentTimeMillis()
        val entry = JSONObject()
            .put("updatedAt", now)
            .put("lastAccess", now)
        if (payload.has("episodes")) {
            entry.put("item", payload.optJSONObject("item"))
            entry.put("episodes", payload.optJSONArray("episodes"))
        } else {
            entry.put("item", payload)
        }
        prefs.edit().putString(key, entry.toString()).apply()
        trim(prefix, maxEntries)
        trimBytes()
    }

    private fun trim(prefix: String, maxEntries: Int) {
        val entries = prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapNotNull { (key, value) ->
                val text = value as? String ?: return@mapNotNull null
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
                key to obj.optLong("lastAccess", obj.optLong("updatedAt", 0L))
            }
            .sortedByDescending { it.second }
        if (entries.size <= maxEntries) return
        prefs.edit().apply {
            entries.drop(maxEntries).forEach { remove(it.first) }
        }.apply()
    }

    private fun trimBytes() {
        var values = prefs.all.mapNotNull { (key, value) ->
            val text = value as? String ?: return@mapNotNull null
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
            Triple(key, text.length, obj.optLong("lastAccess", obj.optLong("updatedAt", 0L)))
        }.sortedBy { it.third }.toMutableList()
        var total = values.sumOf { it.second }
        if (total <= MAX_TOTAL_CHARS) return
        val edit = prefs.edit()
        while (total > MAX_TOTAL_CHARS && values.isNotEmpty()) {
            val oldest = values.removeAt(0)
            total -= oldest.second
            edit.remove(oldest.first)
        }
        edit.apply()
    }

    private fun itemToJson(item: CatalogItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("type", item.type.name)
        .put("name", item.name)
        .putNullable("categoryId", item.categoryId)
        .putNullable("imageUrl", item.imageUrl)
        .putNullable("coverUrl", item.coverUrl)
        .putNullable("backdropUrl", item.backdropUrl)
        .putNullable("containerExtension", item.containerExtension)
        .putNullable("rating", item.rating)
        .putNullable("plot", item.plot)
        .putNullable("season", item.season)
        .putNullable("episode", item.episode)
        .putNullable("channelNumber", item.channelNumber)
        .putNullable("genre", item.genre)
        .putNullable("releaseDate", item.releaseDate)
        .putNullable("runtimeMinutes", item.runtimeMinutes)
        .putNullable("director", item.director)
        .putNullable("cast", item.cast)
        .putNullable("country", item.country)
        .putNullable("youtubeTrailer", item.youtubeTrailer)
        .putNullable("tmdbId", item.tmdbId)
        .putNullable("resolution", item.resolution)
        .putNullable("audioLayout", item.audioLayout)
        .putNullable("parentSeriesId", item.parentSeriesId)
        .putNullable("parentSeriesName", item.parentSeriesName)
        .putNullable("parentSeriesImageUrl", item.parentSeriesImageUrl)
        .putNullable("parentSeriesBackdropUrl", item.parentSeriesBackdropUrl)

    private fun JSONObject.toCatalogItem(): CatalogItem? {
        val id = optString("id").trim()
        val name = optString("name").trim()
        val type = runCatching { ContentType.valueOf(optString("type")) }.getOrNull()
        if (id.isBlank() || name.isBlank() || type == null) return null
        return CatalogItem(
            id = id,
            type = type,
            name = name,
            categoryId = stringOrNull("categoryId"),
            imageUrl = stringOrNull("imageUrl"),
            coverUrl = stringOrNull("coverUrl"),
            backdropUrl = stringOrNull("backdropUrl"),
            containerExtension = stringOrNull("containerExtension"),
            rating = doubleOrNull("rating"),
            plot = stringOrNull("plot"),
            season = intOrNull("season"),
            episode = intOrNull("episode"),
            channelNumber = stringOrNull("channelNumber"),
            genre = stringOrNull("genre"),
            releaseDate = stringOrNull("releaseDate"),
            runtimeMinutes = intOrNull("runtimeMinutes"),
            director = stringOrNull("director"),
            cast = stringOrNull("cast"),
            country = stringOrNull("country"),
            youtubeTrailer = stringOrNull("youtubeTrailer"),
            tmdbId = stringOrNull("tmdbId"),
            resolution = stringOrNull("resolution"),
            audioLayout = stringOrNull("audioLayout"),
            parentSeriesId = stringOrNull("parentSeriesId"),
            parentSeriesName = stringOrNull("parentSeriesName"),
            parentSeriesImageUrl = stringOrNull("parentSeriesImageUrl"),
            parentSeriesBackdropUrl = stringOrNull("parentSeriesBackdropUrl"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = apply {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private companion object {
        const val PREFS_NAME = "xtreamlytv.media-cache.v1"
        const val MAX_MOVIES = 60
        const val MAX_SERIES = 8
        const val MAX_EPISODES_PER_SERIES = 320
        const val MAX_TOTAL_CHARS = 1_800_000
        const val MOVIE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val SERIES_TTL_MS = 3L * 24 * 60 * 60 * 1000
    }
}
