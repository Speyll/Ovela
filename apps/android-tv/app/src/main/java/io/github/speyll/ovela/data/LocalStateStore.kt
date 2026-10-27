package io.github.speyll.ovela.data

import android.content.Context
import io.github.speyll.ovela.model.AppSettings
import io.github.speyll.ovela.model.AppTheme
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.FavoriteGroup
import io.github.speyll.ovela.model.FavoriteGroupAppearance
import io.github.speyll.ovela.model.PlaybackProgress
import io.github.speyll.ovela.model.StreamFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, device-only application state. Provider credentials remain in
 * [CredentialsStore]; this store mirrors the webOS state model for settings,
 * favorites, favorite groups, recent items, and playback progress.
 */
class LocalStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var preferences = legacyPreferences

    fun selectProvider(credentials: io.github.speyll.ovela.model.Credentials) {
        val scoped = appContext.getSharedPreferences("$PREFERENCES_NAME.${providerScope(credentials)}", Context.MODE_PRIVATE)
        if (scoped.all.isEmpty() && preferences === legacyPreferences) {
            val edit = scoped.edit()
            preferences.all.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
                }
            }
            edit.apply()
        }
        preferences = scoped
    }

    fun load(): LocalAppState = runCatching {
        val groups = parseGroups(preferences.getString(KEY_GROUPS, null)).take(24)
        val validGroupIds = DEFAULT_GROUP_ORDER + groups.map { it.id }
        LocalAppState(
            settings = AppSettings(
                theme = AppTheme.fromId(preferences.getString(KEY_THEME, null)),
                streamFormat = StreamFormat.fromId(preferences.getString(KEY_STREAM_FORMAT, null)),
                maxCachedCategories = preferences.getInt(KEY_CACHE_SIZE, DEFAULT_CACHE_SIZE).coerceIn(2, 5),
                favoriteAutoSort = preferences.getBoolean(KEY_FAVORITE_AUTO_SORT, true),
                hiddenCatalogCategories = parseHiddenCatalogCategories(
                    preferences.getString(KEY_HIDDEN_CATALOG_CATEGORIES, null),
                ),
            ),
            favorites = parseItems(preferences.getString(KEY_FAVORITES, null)).distinctBy(::itemKey).take(250),
            favoriteGroups = groups,
            favoriteGroupOrder = normalizeOrder(
                parseStringArray(preferences.getString(KEY_GROUP_ORDER, null)),
                validGroupIds,
            ),
            favoriteItemOrders = parseOrderMap(preferences.getString(KEY_ITEM_ORDERS, null)),
            favoriteGroupAppearances = parseAppearanceMap(preferences.getString(KEY_GROUP_APPEARANCES, null)),
            hiddenFavoriteGroupIds = parseStringArray(preferences.getString(KEY_HIDDEN_GROUPS, null))
                .filter { it in validGroupIds }
                .toSet(),
            recent = parseItems(preferences.getString(KEY_RECENT, null)).distinctBy(::itemKey).take(40),
            progress = parseProgress(preferences.getString(KEY_PROGRESS, null)),
        )
    }.getOrDefault(LocalAppState())

    fun saveSettings(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_THEME, settings.theme.id)
            .putString(KEY_STREAM_FORMAT, settings.streamFormat.id)
            .putInt(KEY_CACHE_SIZE, settings.maxCachedCategories.coerceIn(2, 5))
            .putBoolean(KEY_FAVORITE_AUTO_SORT, settings.favoriteAutoSort)
            .putString(KEY_HIDDEN_CATALOG_CATEGORIES, hiddenCatalogCategoriesToJson(settings.hiddenCatalogCategories).toString())
            .apply()
    }

    private fun parseHiddenCatalogCategories(value: String?): Map<ContentType, Set<String>> = runCatching {
        val root = JSONObject(value ?: return emptyMap())
        ContentType.entries
            .filter { it != ContentType.EPISODE }
            .mapNotNull { type ->
                val ids = parseStringArray(root.optJSONArray(type.name)?.toString())
                    .filter { it.isNotBlank() && !it.startsWith("__") }
                    .toSet()
                type.takeIf { ids.isNotEmpty() }?.let { it to ids }
            }
            .toMap()
    }.getOrDefault(emptyMap())

    private fun hiddenCatalogCategoriesToJson(categories: Map<ContentType, Set<String>>): JSONObject {
        val root = JSONObject()
        categories.forEach { (type, ids) ->
            if (type != ContentType.EPISODE && ids.isNotEmpty()) {
                root.put(type.name, JSONArray(ids.filter { it.isNotBlank() && !it.startsWith("__") }.sorted()))
            }
        }
        return root
    }

    fun saveFavorites(items: List<CatalogItem>) {
        preferences.edit().putString(KEY_FAVORITES, itemsToJson(items.take(250)).toString()).apply()
    }

    fun saveFavoriteGroups(groups: List<FavoriteGroup>) {
        val array = JSONArray()
        groups.take(24).forEach { group -> array.put(groupToJson(group)) }
        preferences.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    fun saveFavoriteGroupOrder(order: List<String>) {
        val array = JSONArray()
        order.distinct().take(28).forEach { array.put(it) }
        preferences.edit().putString(KEY_GROUP_ORDER, array.toString()).apply()
    }

    fun saveFavoriteItemOrders(orders: Map<String, List<String>>) {
        val root = JSONObject()
        orders.entries.take(28).forEach { (groupId, keys) ->
            val array = JSONArray()
            keys.distinct().filter { it.matches(FAVORITE_KEY_PATTERN) }.take(250).forEach { array.put(it) }
            root.put(groupId, array)
        }
        preferences.edit().putString(KEY_ITEM_ORDERS, root.toString()).apply()
    }

    fun saveFavoriteGroupAppearances(appearances: Map<String, FavoriteGroupAppearance>) {
        val root = JSONObject()
        appearances.entries
            .filter { (id, _) -> id in BUILT_IN_GROUP_IDS }
            .forEach { (id, appearance) ->
                root.put(
                    id,
                    JSONObject()
                        .put("name", appearance.name.trim().take(36))
                        .put("icon", appearance.icon)
                        .put("color", appearance.color),
                )
            }
        preferences.edit().putString(KEY_GROUP_APPEARANCES, root.toString()).apply()
    }

    fun saveHiddenFavoriteGroupIds(groupIds: Set<String>) {
        val array = JSONArray()
        groupIds.distinct().take(28).forEach { array.put(it) }
        preferences.edit().putString(KEY_HIDDEN_GROUPS, array.toString()).apply()
    }

    fun saveRecent(items: List<CatalogItem>) {
        preferences.edit().putString(KEY_RECENT, itemsToJson(items.take(40)).toString()).apply()
    }

    fun saveProgress(progress: Map<String, PlaybackProgress>) {
        val root = JSONObject()
        progress.forEach { (key, value) ->
            root.put(
                key,
                JSONObject()
                    .put("positionMs", value.positionMs)
                    .put("durationMs", value.durationMs)
                    .put("updatedAt", value.updatedAt),
            )
        }
        preferences.edit().putString(KEY_PROGRESS, root.toString()).apply()
    }

    fun clearHistory() {
        preferences.edit().remove(KEY_RECENT).remove(KEY_PROGRESS).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun parseItems(raw: String?): List<CatalogItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)?.toCatalogItem() ?: continue
                add(item)
            }
        }
    }

    private fun parseGroups(raw: String?): List<FavoriteGroup> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optString("id").trim()
                if (id.isBlank()) continue
                val keysArray = value.optJSONArray("itemKeys") ?: JSONArray()
                val keys = buildSet {
                    for (keyIndex in 0 until keysArray.length()) {
                        val key = keysArray.optString(keyIndex)
                        if (key.matches(FAVORITE_KEY_PATTERN)) add(key)
                    }
                }
                add(
                    FavoriteGroup(
                        id = id,
                        name = value.optString("name", "Untitled group").trim().take(36).ifBlank { "Untitled group" },
                        icon = value.optString("icon", "folder").takeIf { it in ALLOWED_ICONS } ?: "folder",
                        color = value.optString("color", "purple").takeIf { it in ALLOWED_COLORS } ?: "purple",
                        itemKeys = keys.take(250).toSet(),
                        createdAt = value.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }.distinctBy { it.id }
    }

    private fun parseStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.distinct()
    }

    private fun parseOrderMap(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = JSONObject(raw)
        return buildMap {
            val groupIds = root.keys()
            while (groupIds.hasNext()) {
                val groupId = groupIds.next().trim()
                if (groupId.isBlank()) continue
                val array = root.optJSONArray(groupId) ?: continue
                val keys = buildList {
                    for (index in 0 until array.length()) {
                        val key = array.optString(index)
                        if (key.matches(FAVORITE_KEY_PATTERN)) add(key)
                    }
                }.distinct().take(250)
                if (keys.isNotEmpty()) put(groupId, keys)
            }
        }
    }

    private fun parseAppearanceMap(raw: String?): Map<String, FavoriteGroupAppearance> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = JSONObject(raw)
        return buildMap {
            BUILT_IN_GROUP_IDS.forEach { id ->
                val value = root.optJSONObject(id) ?: return@forEach
                val fallback = defaultAppearance(id)
                put(
                    id,
                    FavoriteGroupAppearance(
                        name = value.optString("name", fallback.name).trim().take(36).ifBlank { fallback.name },
                        icon = value.optString("icon", fallback.icon).takeIf { it in ALLOWED_ICONS } ?: fallback.icon,
                        color = value.optString("color", fallback.color).takeIf { it in ALLOWED_COLORS } ?: fallback.color,
                    ),
                )
            }
        }
    }

    private fun normalizeOrder(saved: List<String>, valid: List<String>): List<String> {
        val validSet = valid.toSet()
        return (saved.filter { it in validSet } + valid.filterNot { it in saved }).distinct()
    }

    private fun parseProgress(raw: String?): Map<String, PlaybackProgress> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = JSONObject(raw)
        return buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = root.optJSONObject(key) ?: continue
                val position = value.optLong("positionMs", -1L)
                if (position < 0L) continue
                put(
                    key,
                    PlaybackProgress(
                        positionMs = position,
                        durationMs = value.optLong("durationMs", 0L).coerceAtLeast(0L),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun itemsToJson(items: List<CatalogItem>): JSONArray = JSONArray().also { array ->
        items.forEach { item -> array.put(itemToJson(item)) }
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

    private fun groupToJson(group: FavoriteGroup): JSONObject {
        val keys = JSONArray()
        group.itemKeys.take(250).forEach { key -> keys.put(key) }
        return JSONObject()
            .put("id", group.id)
            .put("name", group.name.take(36))
            .put("icon", group.icon)
            .put("color", group.color)
            .put("itemKeys", keys)
            .put("createdAt", group.createdAt)
            .put("updatedAt", group.updatedAt)
    }

    private fun JSONObject.toCatalogItem(): CatalogItem? {
        val id = optString("id").trim()
        val name = optString("name").trim()
        val type = runCatching { ContentType.valueOf(optString("type")) }.getOrNull()
        if (id.isBlank() || name.isBlank() || type == null) return null
        return CatalogItem(
            id = id,
            type = type,
            name = name,
            categoryId = nullableString("categoryId"),
            imageUrl = nullableString("imageUrl"),
            coverUrl = nullableString("coverUrl"),
            backdropUrl = nullableString("backdropUrl"),
            containerExtension = nullableString("containerExtension"),
            rating = nullableDouble("rating"),
            plot = nullableString("plot"),
            season = nullableInt("season"),
            episode = nullableInt("episode"),
            channelNumber = nullableString("channelNumber"),
            genre = nullableString("genre"),
            releaseDate = nullableString("releaseDate"),
            runtimeMinutes = nullableInt("runtimeMinutes"),
            director = nullableString("director"),
            cast = nullableString("cast"),
            country = nullableString("country"),
            youtubeTrailer = nullableString("youtubeTrailer"),
            tmdbId = nullableString("tmdbId"),
            resolution = nullableString("resolution"),
            audioLayout = nullableString("audioLayout"),
            parentSeriesId = nullableString("parentSeriesId"),
            parentSeriesName = nullableString("parentSeriesName"),
            parentSeriesImageUrl = nullableString("parentSeriesImageUrl"),
            parentSeriesBackdropUrl = nullableString("parentSeriesBackdropUrl"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = apply {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private companion object {
        // Historical store name, kept verbatim through the Ovela rename so that
        // existing favourites, progress, and settings survive the update.
        const val PREFERENCES_NAME = "xtreamlytv.state.v1"
        const val KEY_THEME = "settings.theme"
        const val KEY_STREAM_FORMAT = "settings.streamFormat"
        const val KEY_CACHE_SIZE = "settings.maxCachedCategories"
        const val KEY_FAVORITE_AUTO_SORT = "settings.favoriteAutoSort"
        const val KEY_HIDDEN_CATALOG_CATEGORIES = "settings.hiddenCatalogCategories"
        const val KEY_FAVORITES = "favorites"
        const val KEY_GROUPS = "favoriteGroups"
        const val KEY_GROUP_ORDER = "favoriteGroupOrder"
        const val KEY_ITEM_ORDERS = "favoriteItemOrders"
        const val KEY_GROUP_APPEARANCES = "favoriteGroupAppearances"
        const val KEY_HIDDEN_GROUPS = "hiddenFavoriteGroupIds"
        const val KEY_RECENT = "recent"
        const val KEY_PROGRESS = "progress"
        const val DEFAULT_CACHE_SIZE = 3
        val FAVORITE_KEY_PATTERN = Regex("^(LIVE|MOVIE|SERIES|EPISODE):.+$")
        val ALLOWED_ICONS = setOf("heart", "tv", "popcorn", "play", "smile", "trophy", "folder", "star")
        val ALLOWED_COLORS = setOf("purple", "blue", "teal", "orange", "rose", "lime", "slate")
        val DEFAULT_GROUP_ORDER = listOf("all", "live", "movie", "series")
        val BUILT_IN_GROUP_IDS = DEFAULT_GROUP_ORDER.toSet()

        fun defaultAppearance(id: String): FavoriteGroupAppearance = when (id) {
            "live" -> FavoriteGroupAppearance("Live TV", "tv", "blue")
            "movie" -> FavoriteGroupAppearance("Movies", "popcorn", "teal")
            "series" -> FavoriteGroupAppearance("Series", "play", "orange")
            else -> FavoriteGroupAppearance("All Favorites", "heart", "purple")
        }
    }
}

data class LocalAppState(
    val settings: AppSettings = AppSettings(),
    val favorites: List<CatalogItem> = emptyList(),
    val favoriteGroups: List<FavoriteGroup> = emptyList(),
    val favoriteGroupOrder: List<String> = listOf("all", "live", "movie", "series"),
    val favoriteItemOrders: Map<String, List<String>> = emptyMap(),
    val favoriteGroupAppearances: Map<String, FavoriteGroupAppearance> = emptyMap(),
    val hiddenFavoriteGroupIds: Set<String> = emptySet(),
    val recent: List<CatalogItem> = emptyList(),
    val progress: Map<String, PlaybackProgress> = emptyMap(),
)

fun itemKey(item: CatalogItem): String = "${item.type}:${item.id}"
