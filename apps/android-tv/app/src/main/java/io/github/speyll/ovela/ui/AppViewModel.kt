package io.github.speyll.ovela.ui

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.speyll.ovela.data.CredentialsStore
import io.github.speyll.ovela.data.LocalStateStore
import io.github.speyll.ovela.data.M3uClient
import io.github.speyll.ovela.data.SourceClient
import io.github.speyll.ovela.data.MediaCacheStore
import io.github.speyll.ovela.data.ProviderUrl
import io.github.speyll.ovela.data.XtreamClient
import io.github.speyll.ovela.data.itemKey
import io.github.speyll.ovela.model.AppSettings
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.EpgProgram
import io.github.speyll.ovela.model.FavoriteGroup
import io.github.speyll.ovela.model.FavoriteGroupAppearance
import io.github.speyll.ovela.model.PlaybackProgress
import io.github.speyll.ovela.model.PlayerRequest
import io.github.speyll.ovela.model.ProviderSummary
import io.github.speyll.ovela.model.hiddenCategoryIds
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "OvelaViewModel"

data class FocusRequest(
    val scope: String,
    val itemKey: String? = null,
    val firstItem: Boolean = false,
)

sealed interface AppScreen {
    data object Login : AppScreen
    data object Home : AppScreen
    data class Catalog(val type: ContentType) : AppScreen
    data class Detail(
        val item: CatalogItem,
        val origin: AppScreen,
        val returnFocus: FocusRequest? = null,
    ) : AppScreen
    data object Favorites : AppScreen
    data class FavoriteGroupBrowser(val groupId: String) : AppScreen
    data object FavoriteGroupsManager : AppScreen
    data class FavoriteGroupEditor(val groupId: String?) : AppScreen
    data class Player(
        val request: PlayerRequest,
        val origin: AppScreen,
        val returnFocus: FocusRequest? = null,
    ) : AppScreen
    data object Settings : AppScreen
    data object CategoryVisibility : AppScreen
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val initializing: Boolean = true,
    val loading: Boolean = false,
    val catalogsLoading: Boolean = false,
    val error: String? = null,
    val credentials: Credentials? = null,
    val provider: ProviderSummary? = null,
    val categories: Map<ContentType, List<Category>> = emptyMap(),
    val selectedCategories: Map<ContentType, Category> = emptyMap(),
    val items: List<CatalogItem> = emptyList(),
    val loadedItems: Map<ContentType, List<CatalogItem>> = emptyMap(),
    val searchQuery: String = "",
    val favorites: List<CatalogItem> = emptyList(),
    val favoriteGroups: List<FavoriteGroup> = emptyList(),
    val favoriteGroupOrder: List<String> = listOf("all", "live", "movie", "series"),
    val favoriteItemOrders: Map<String, List<String>> = emptyMap(),
    val favoriteGroupAppearances: Map<String, FavoriteGroupAppearance> = emptyMap(),
    val hiddenFavoriteGroupIds: Set<String> = emptySet(),
    val selectedFavoriteGroupId: String = "all",
    val recent: List<CatalogItem> = emptyList(),
    val progress: Map<String, PlaybackProgress> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val detailEpisodes: List<CatalogItem> = emptyList(),
    val detailSelectedSeason: Int? = null,
    val richItems: Map<String, CatalogItem> = emptyMap(),
    val epg: Map<String, List<EpgProgram>> = emptyMap(),
    val epgLoading: Set<String> = emptySet(),
    val focusRequest: FocusRequest? = null,
    // Settings owns this instead of the screen so Back from anywhere (remote,
    // phone header) can cancel the provider edit rather than leaving Settings.
    val editingProvider: Boolean = false,
)

internal fun isCatalogItemHidden(item: CatalogItem, settings: AppSettings): Boolean {
    val type = if (item.type == ContentType.EPISODE) ContentType.SERIES else item.type
    return item.categoryId?.let { it in settings.hiddenCategoryIds(type) } == true
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CredentialsStore(application)
    }
    private val localStateStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalStateStore(application)
    }
    private val mediaCacheStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MediaCacheStore(application) }
    private var client: SourceClient? = null
    private val movieDetailJobs = mutableMapOf<String, Job>()
    private val categoryCache = LinkedHashMap<String, List<CatalogItem>>(16, 0.75f, true)
    private var catalogRequestId = 0L
    private var connectionGeneration = 0L
    private val epgUpdatedAt = mutableMapOf<String, Long>()
    private val persistenceMutex = Mutex()
    private val lastFocusByArea = mutableMapOf<String, FocusRequest>()
    private val _state = MutableStateFlow(AppUiState())
val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val credentialsResult = withContext(Dispatchers.IO) { runCatching { credentialsStore.load() } }
            val savedCredentials = credentialsResult.getOrNull()
            if (savedCredentials != null) {
                localStateStore.selectProvider(savedCredentials)
                mediaCacheStore.selectProvider(savedCredentials)
            }
            val localResult = withContext(Dispatchers.IO) { runCatching { localStateStore.load() } }
            val local = localResult.getOrNull()
            _state.update { current ->
                current.copy(
                    settings = local?.settings ?: AppSettings(),
                    favorites = local?.favorites.orEmpty(),
                    favoriteGroups = local?.favoriteGroups.orEmpty(),
                    favoriteGroupOrder = local?.favoriteGroupOrder ?: listOf("all", "live", "movie", "series"),
                    favoriteItemOrders = local?.favoriteItemOrders.orEmpty(),
                    favoriteGroupAppearances = local?.favoriteGroupAppearances.orEmpty(),
                    hiddenFavoriteGroupIds = local?.hiddenFavoriteGroupIds.orEmpty(),
                    selectedFavoriteGroupId = firstVisibleFavoriteGroupId(
                        order = local?.favoriteGroupOrder ?: BuiltInFavoriteGroupIds,
                        hidden = local?.hiddenFavoriteGroupIds.orEmpty(),
                    ),
                    recent = local?.recent.orEmpty(),
                    progress = local?.progress.orEmpty(),
                )
            }
            if (savedCredentials == null) {
                _state.update {
                    it.copy(
                        initializing = false,
                        error = credentialsResult.exceptionOrNull()?.let {
                            "Saved provider details could not be read. Enter them again."
                        },
                    )
                }
            } else {
                startConnection(
                    credentials = savedCredentials,
                    persist = false,
                    startup = true,
                    successScreen = AppScreen.Home,
                    failureScreen = AppScreen.Login,
                )
            }
        }
    }

    fun connect(credentials: Credentials) {
        startConnection(
            credentials = credentials,
            persist = true,
            startup = false,
            successScreen = AppScreen.Home,
            failureScreen = AppScreen.Login,
        )
    }

    fun updateProvider(credentials: Credentials) {
        startConnection(
            credentials = credentials,
            persist = true,
            startup = false,
            successScreen = AppScreen.Settings,
            failureScreen = AppScreen.Settings,
        )
    }

    private fun startConnection(
        credentials: Credentials,
        persist: Boolean,
        startup: Boolean,
        successScreen: AppScreen,
        failureScreen: AppScreen,
    ) {
        val normalizedCredentials = runCatching {
            if (credentials.isM3u) {
                val playlistUrl = credentials.playlistUrl.trim()
                require(
                    playlistUrl.startsWith("http://", true) ||
                        playlistUrl.startsWith("https://", true) ||
                        playlistUrl.startsWith("file://", true),
                ) { "Enter a complete M3U playlist URL." }
                credentials.copy(
                    server = "",
                    username = "",
                    password = "",
                    playlistUrl = playlistUrl,
                    alternateServers = credentials.alternateServers
                        .map { it.trim().trimEnd('/') }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(4),
                )
            } else {
                credentials.copy(
                    server = ProviderUrl.normalize(credentials.server),
                    username = credentials.username.trim(),
                ).also {
                    require(it.username.isNotBlank()) { "Enter your provider username." }
                    require(it.password.isNotBlank()) { "Enter your provider password." }
                }
            }
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    initializing = false,
                    loading = false,
                    screen = failureScreen,
                    credentials = credentials,
                    error = error.userMessage(),
                )
            }
            return
        }
        val generation = ++connectionGeneration

        _state.update {
            it.copy(
                initializing = startup,
                loading = true,
                catalogsLoading = false,
                error = null,
                credentials = normalizedCredentials,
            )
        }

        viewModelScope.launch {
            try {
                val candidate: SourceClient = if (normalizedCredentials.isM3u) {
                    M3uClient(normalizedCredentials)
                } else {
                    XtreamClient(normalizedCredentials)
                }
                val profile = withTimeout(CONNECTION_TIMEOUT_MS) { candidate.authenticate() }
                if (generation != connectionGeneration) return@launch

                client = candidate
                categoryCache.clear()
                catalogRequestId += 1
                movieDetailJobs.values.forEach { it.cancel() }
                movieDetailJobs.clear()
                epgUpdatedAt.clear()
                withContext(Dispatchers.IO) {
                    localStateStore.selectProvider(normalizedCredentials)
                    mediaCacheStore.selectProvider(normalizedCredentials)
                }
                val providerState = withContext(Dispatchers.IO) { localStateStore.load() }
                if (persist) {
                    withContext(Dispatchers.IO) { credentialsStore.save(normalizedCredentials) }
                }
                _state.update {
                    it.copy(
                        screen = successScreen,
                        initializing = false,
                        loading = false,
                        catalogsLoading = true,
                        error = null,
                        provider = profile,
                        categories = emptyMap(),
                        selectedCategories = emptyMap(),
                        items = emptyList(),
                        loadedItems = emptyMap(),
                        richItems = emptyMap(),
                        epg = emptyMap(),
                        epgLoading = emptySet(),
                        favorites = providerState.favorites,
                        favoriteGroups = providerState.favoriteGroups,
                        favoriteGroupOrder = providerState.favoriteGroupOrder,
                        favoriteItemOrders = providerState.favoriteItemOrders,
                        favoriteGroupAppearances = providerState.favoriteGroupAppearances,
                        hiddenFavoriteGroupIds = providerState.hiddenFavoriteGroupIds,
                        selectedFavoriteGroupId = firstVisibleFavoriteGroupId(
                            providerState.favoriteGroupOrder,
                            providerState.hiddenFavoriteGroupIds,
                        ),
                        recent = providerState.recent,
                        progress = providerState.progress,
                        settings = providerState.settings,
                        searchQuery = "",
                        editingProvider = false,
                    )
                }
                loadCategories(candidate)
            } catch (error: CancellationException) {
                if (generation != connectionGeneration) return@launch
                if (error !is TimeoutCancellationException) throw error
                connectionFailed(error, failureScreen, startup)
            } catch (error: Throwable) {
                if (generation != connectionGeneration) return@launch
                connectionFailed(error, failureScreen, startup)
            }
        }
    }

    private fun loadCategories(candidate: SourceClient) {
        viewModelScope.launch {
            val categories = coroutineScope {
                ContentType.entries
                    .filter { it != ContentType.EPISODE }
                    .associateWith { type ->
                        async {
                            runCatching { candidate.categories(type) }
                                .getOrDefault(emptyList())
                        }
                    }
                    .mapValues { (_, deferred) -> deferred.await() }
            }
            if (client === candidate) {
                _state.update { it.copy(categories = categories, catalogsLoading = false) }
                val currentScreen = _state.value.screen as? AppScreen.Catalog
                if (currentScreen != null && _state.value.selectedCategories[currentScreen.type] == null) {
                    categories[currentScreen.type]
                        ?.firstOrNull { it.id !in _state.value.settings.hiddenCategoryIds(currentScreen.type) }
                        ?.let { firstCategory ->
                        selectCategory(currentScreen.type, firstCategory)
                    }
                }
            }
        }
    }

    private fun connectionFailed(error: Throwable, failureScreen: AppScreen, startup: Boolean) {
        if (startup || failureScreen == AppScreen.Login) client = null
        _state.update {
            it.copy(
                screen = failureScreen,
                initializing = false,
                loading = false,
                catalogsLoading = false,
                error = error.userMessage(),
                editingProvider = false,
            )
        }
    }

    fun rememberFocusedItem(area: String, scope: String, key: String) {
        lastFocusByArea[area] = FocusRequest(scope = scope, itemKey = key)
    }

    fun consumeFocusRequest(scope: String) {
        _state.update { current ->
            if (current.focusRequest?.scope == scope) current.copy(focusRequest = null) else current
        }
    }

    fun openHome() = _state.update {
        it.copy(
            screen = AppScreen.Home,
            items = emptyList(),
            searchQuery = "",
            error = null,
            focusRequest = lastFocusByArea[AREA_HOME],
            editingProvider = false,
        )
    }

    fun openFavorites() = _state.update { current ->
        val selected = current.selectedFavoriteGroupId.takeIf { id ->
            id !in current.hiddenFavoriteGroupIds && id in validFavoriteGroupIds(current.favoriteGroups)
        } ?: firstVisibleFavoriteGroupId(current.favoriteGroupOrder, current.hiddenFavoriteGroupIds)
        val scope = selected.takeIf { it.isNotBlank() }?.let(::favoriteFocusScope)
        val remembered = lastFocusByArea[AREA_FAVORITES]?.takeIf { it.scope == scope }
        current.copy(
            screen = if (selected == "all" || selected.isBlank()) AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(selected),
            selectedFavoriteGroupId = selected,
            items = emptyList(),
            searchQuery = "",
            error = null,
            focusRequest = remembered ?: scope?.let { FocusRequest(it, firstItem = true) },
        )
    }

    fun openSettings() = _state.update {
        it.copy(
            screen = AppScreen.Settings,
            items = emptyList(),
            searchQuery = "",
            error = null,
            focusRequest = null,
            editingProvider = false,
        )
    }

    fun beginProviderEdit() = _state.update {
        if (it.screen == AppScreen.Settings) it.copy(editingProvider = true) else it
    }

    fun cancelProviderEdit() = _state.update { it.copy(editingProvider = false) }

    fun openCategoryVisibility() = _state.update {
        it.copy(screen = AppScreen.CategoryVisibility, items = emptyList(), searchQuery = "", error = null, focusRequest = null)
    }

    fun openCatalog(type: ContentType) {
        val hidden = _state.value.settings.hiddenCategoryIds(type)
        val categories = _state.value.categories[type].orEmpty().filter { it.id !in hidden }
        val selected = _state.value.selectedCategories[type]?.takeIf { it.id !in hidden } ?: categories.firstOrNull()
        // Top-menu entry always starts at the category list. Remembered poster
        // focus is intentionally ignored here; selecting a category from the list
        // will create the first-item focus request after the category is activated.
        _state.update {
            it.copy(
                screen = AppScreen.Catalog(type),
                items = emptyList(),
                searchQuery = "",
                error = null,
                focusRequest = null,
            )
        }
        selected?.let { category ->
            if (category.id.startsWith("__")) {
                selectVirtualCategory(type, category, preserveFocusRequest = true)
            } else {
                selectCategory(type, category, preserveRememberedFocus = true)
            }
        }
    }

    fun selectCategory(
        type: ContentType,
        category: Category,
        preserveRememberedFocus: Boolean = false,
        deferFirstItemFocus: Boolean = false,
    ) {
        val api = client ?: return
        val key = cacheKey(type, category.id)
        val cached = categoryCache[key]
        val requestId = ++catalogRequestId
        val retainCurrentItems = cached == null &&
            (_state.value.screen as? AppScreen.Catalog)?.type == type &&
            _state.value.items.isNotEmpty()
        _state.update {
            it.copy(
                loading = cached == null,
                selectedCategories = it.selectedCategories + (type to category),
                items = cached ?: if (retainCurrentItems) it.items else emptyList(),
                searchQuery = "",
                error = null,
                focusRequest = when {
                    deferFirstItemFocus && cached == null -> null
                    deferFirstItemFocus && !cached.isNullOrEmpty() ->
                        FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                    preserveRememberedFocus -> it.focusRequest
                    else -> FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                },
            )
        }
        if (cached != null) {
            updateLoadedItems(type, cached)
            return
        }
        viewModelScope.launch {
            runCatching { api.items(type, category.id) }
                .onSuccess { items ->
                    if (client !== api) return@onSuccess
                    cacheCategory(type, category.id, items)
                    if (requestId == catalogRequestId && _state.value.screen == AppScreen.Catalog(type)) {
                        _state.update {
                            it.copy(
                                loading = false,
                                items = items,
                                focusRequest = if (deferFirstItemFocus && items.isNotEmpty()) {
                                    FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                                } else {
                                    it.focusRequest
                                },
                            )
                        }
                    }
                    updateLoadedItems(type, items)
                }
                .onFailure { error ->
                    if (client !== api) return@onFailure
                    if (requestId == catalogRequestId) {
                        _state.update { it.copy(loading = false, error = error.userMessage()) }
                    }
                }
        }
    }

    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }

    fun activate(item: CatalogItem) {
        when (item.type) {
            ContentType.LIVE, ContentType.MOVIE, ContentType.EPISODE -> play(item)
            ContentType.SERIES -> openDetail(item)
        }
    }

    fun openDetail(item: CatalogItem) {
        val origin = _state.value.screen
        val returnFocus = focusForScreen(origin)
        _state.update {
            it.copy(
                screen = AppScreen.Detail(item, origin, returnFocus),
                detailEpisodes = emptyList(),
                detailSelectedSeason = null,
                loading = item.type == ContentType.SERIES,
                error = null,
                focusRequest = null,
            )
        }
        when (item.type) {
            ContentType.SERIES -> loadSeriesEpisodes(item)
            ContentType.MOVIE -> requestMovieDetails(item, delayMs = 0L)
            else -> Unit
        }
    }

    private fun loadSeriesEpisodes(series: CatalogItem) {
        val api = client ?: return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { mediaCacheStore.getSeries(series.id) }
            if (client !== api) return@launch
            if (cached != null) {
                applySeriesDetail(series.id, cached.series, cached.episodes)
                return@launch
            }
            runCatching { api.seriesDetail(series) }
                .onSuccess { (richSeries, episodes) ->
                    if (client !== api) return@onSuccess
                    withContext(Dispatchers.IO) { mediaCacheStore.putSeries(richSeries, episodes) }
                    applySeriesDetail(series.id, richSeries, episodes)
                }
                .onFailure { error ->
                    if (client !== api) return@onFailure
                    _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
        }
    }

    private fun applySeriesDetail(seriesId: String, richSeries: CatalogItem, episodes: List<CatalogItem>) {
        val current = _state.value.screen as? AppScreen.Detail ?: return
        if (current.item.id != seriesId) return
        val firstSeason = episodes.mapNotNull { it.season }.distinct().sorted().firstOrNull()
        _state.update { state ->
            state.copy(
                screen = current.copy(item = richSeries),
                loading = false,
                detailEpisodes = episodes,
                detailSelectedSeason = firstSeason,
                richItems = state.richItems + (itemKey(richSeries) to richSeries),
            )
        }
    }

    fun requestMovieDetails(item: CatalogItem, delayMs: Long = 160L) {
        if (item.type != ContentType.MOVIE) return
        val key = itemKey(item)
        if (_state.value.richItems.containsKey(key) || movieDetailJobs[key]?.isActive == true) return

        movieDetailJobs[key] = viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { mediaCacheStore.getMovie(item.id) }
                if (cached != null) {
                    _state.update { it.copy(richItems = it.richItems + (key to cached)) }
                    return@launch
                }

                if (delayMs > 0L) delay(delayMs)
                val api = client ?: return@launch
                runCatching { api.movieInfo(item) }.onSuccess { rich ->
                    if (client !== api) return@onSuccess
                    withContext(Dispatchers.IO) { mediaCacheStore.putMovie(rich) }
                    _state.update { state -> state.copy(richItems = state.richItems + (key to rich)) }
                }
            } finally {
                movieDetailJobs.remove(key)
            }
        }
    }

    fun warmMovieDetails(items: List<CatalogItem>) {
        items
            .asSequence()
            .filter { it.type == ContentType.MOVIE }
            .distinctBy(::itemKey)
            .take(5)
            .forEachIndexed { index, item ->
                requestMovieDetails(item, delayMs = 220L + index * 90L)
            }
    }

    fun richItem(item: CatalogItem): CatalogItem = _state.value.richItems[itemKey(item)] ?: item

    fun ensureEpg(channels: List<CatalogItem>) {
        val api = client ?: return
        val now = System.currentTimeMillis()
        val state = _state.value
        val pending = channels.asSequence()
            .filter { it.type == ContentType.LIVE }
            .distinctBy { it.id }
            .take(8)
            .filter { channel ->
                val ttl = if (state.epg[channel.id].isNullOrEmpty()) EPG_RETRY_MS else EPG_TTL_MS
                val fresh = now - (epgUpdatedAt[channel.id] ?: 0L) < ttl
                !fresh && channel.id !in state.epgLoading
            }
            .toList()
        if (pending.isEmpty()) return
        // One emission for the whole batch. Marking each channel individually
        // used to emit the state up to eight times before the first programme
        // arrived, and every emission recomposed the live list.
        _state.update { it.copy(epgLoading = it.epgLoading + pending.map { channel -> channel.id }) }
        pending.forEach { channel ->
            val id = channel.id
            viewModelScope.launch {
                val programs = runCatching { api.shortEpg(id, 12) }.getOrDefault(emptyList())
                if (client !== api) return@launch
                epgUpdatedAt[id] = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        epg = current.epg + (id to programs),
                        epgLoading = current.epgLoading - id,
                    )
                }
            }
        }
    }

    fun virtualCategoryItems(type: ContentType, categoryId: String): List<CatalogItem> {
        val current = _state.value
        val base = current.favorites.filter { it.type == type && !isCatalogItemHidden(it, current.settings) }
        val filtered = when {
            categoryId == "__favorites__" -> base
            categoryId.startsWith("__group__:") -> {
                val groupId = categoryId.removePrefix("__group__:")
                val keys = current.favoriteGroups.firstOrNull { it.id == groupId }?.itemKeys.orEmpty()
                base.filter { itemKey(it) in keys }
            }
            else -> emptyList()
        }
        return if (current.settings.favoriteAutoSort) filtered.sortedBy { it.name.lowercase() } else filtered
    }

    fun selectVirtualCategory(
        type: ContentType,
        category: Category,
        preserveFocusRequest: Boolean = false,
        deferFirstItemFocus: Boolean = false,
    ) {
        val items = virtualCategoryItems(type, category.id)
        _state.update {
            it.copy(
                selectedCategories = it.selectedCategories + (type to category),
                items = items,
                searchQuery = "",
                loading = false,
                error = null,
                focusRequest = when {
                    deferFirstItemFocus && items.isNotEmpty() ->
                        FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                    deferFirstItemFocus -> null
                    preserveFocusRequest -> it.focusRequest
                    else -> FocusRequest(catalogFocusScope(type, category.id), firstItem = true)
                },
            )
        }
        updateLoadedItems(type, items)
    }

    fun selectDetailSeason(seriesId: String, season: Int) {
        _state.update {
            it.copy(
                detailSelectedSeason = season,
                focusRequest = FocusRequest(detailFocusScope(seriesId, season), firstItem = true),
            )
        }
    }

    fun play(item: CatalogItem, queue: List<CatalogItem> = currentQueueFor(item)) {
        val api = client ?: return
        val playableQueue = queue.filter { it.type == item.type && it.type != ContentType.SERIES }
        val origin = _state.value.screen
        val returnFocus = focusForScreen(origin)
        val progress = _state.value.progress[itemKey(item)]
        val candidates = api.streamCandidates(item, _state.value.settings.streamFormat)
        if (candidates.isEmpty()) {
            _state.update { it.copy(error = "This item does not include a playable stream.") }
            return
        }
        val request = PlayerRequest(
            item = item,
            queue = playableQueue.ifEmpty { listOf(item) },
            urlCandidates = candidates,
            startPositionMs = progress?.positionMs ?: 0L,
        )
        addRecent(item)
        _state.update { it.copy(screen = AppScreen.Player(request, origin, returnFocus), error = null, focusRequest = null) }
    }

    fun playAdjacent(delta: Int) {
        val screen = _state.value.screen as? AppScreen.Player ?: return
        val queue = screen.request.queue
        val currentIndex = queue.indexOfFirst { itemKey(it) == itemKey(screen.request.item) }
        if (currentIndex < 0 || queue.size < 2) return
        val next = queue[(currentIndex + delta + queue.size) % queue.size]
        val api = client ?: return
        val progress = _state.value.progress[itemKey(next)]
        val candidates = api.streamCandidates(next, _state.value.settings.streamFormat)
        if (candidates.isEmpty()) {
            _state.update { it.copy(error = "This item does not include a playable stream.") }
            return
        }
        Log.i(TAG, "Channel switch: ${screen.request.item.name} -> ${next.name} (delta=$delta)")
        addRecent(next)
        _state.update {
            it.copy(
                screen = AppScreen.Player(
                    request = PlayerRequest(
                        item = next,
                        queue = queue,
                        urlCandidates = candidates,
                        startPositionMs = progress?.positionMs ?: 0L,
                    ),
                    origin = screen.origin,
                    returnFocus = screen.returnFocus,
                ),
            )
        }
    }

    fun toggleFavorite(item: CatalogItem) {
        val key = itemKey(item)
        val current = _state.value
        val exists = current.favorites.any { itemKey(it) == key }
        val nextFavorites = if (exists) {
            current.favorites.filterNot { itemKey(it) == key }
        } else {
            (listOf(item) + current.favorites.filterNot { itemKey(it) == key }).take(250)
        }
        val nextGroups = if (exists) {
            current.favoriteGroups.map { group ->
                if (key in group.itemKeys) group.copy(itemKeys = group.itemKeys - key, updatedAt = System.currentTimeMillis())
                else group
            }
        } else current.favoriteGroups
        val nextOrders = if (exists) {
            current.favoriteItemOrders.mapValues { (_, order) -> order.filterNot { it == key } }
        } else current.favoriteItemOrders
        _state.update {
            it.copy(
                favorites = nextFavorites,
                favoriteGroups = nextGroups,
                favoriteItemOrders = nextOrders,
            )
        }
        persistFavorites(nextFavorites, nextGroups, nextOrders)
        val screen = _state.value.screen as? AppScreen.Catalog
        val selected = screen?.let { _state.value.selectedCategories[it.type] }
        if (screen != null && selected != null && selected.id.startsWith("__")) {
            val refreshed = virtualCategoryItems(screen.type, selected.id)
            _state.update { it.copy(items = refreshed) }
        }
    }

    fun isFavorite(item: CatalogItem): Boolean =
        _state.value.favorites.any { itemKey(it) == itemKey(item) }

    fun selectFavoriteGroup(groupId: String) {
        val current = _state.value
        if (groupId in current.hiddenFavoriteGroupIds || groupId !in validFavoriteGroupIds(current.favoriteGroups)) return
        _state.update {
            it.copy(
                screen = if (groupId == "all") AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(groupId),
                selectedFavoriteGroupId = groupId,
                searchQuery = "",
                error = null,
                focusRequest = FocusRequest(favoriteFocusScope(groupId), firstItem = true),
            )
        }
    }

    fun openFavoriteGroup(groupId: String) = selectFavoriteGroup(groupId)

    fun openFavoriteGroupsManager() = _state.update {
        it.copy(screen = AppScreen.FavoriteGroupsManager, error = null, focusRequest = null)
    }

    fun openFavoriteEditor(groupId: String? = null) = _state.update {
        it.copy(screen = AppScreen.FavoriteGroupEditor(groupId), error = null, focusRequest = null)
    }

    fun saveBuiltInFavoriteGroupAppearance(
        groupId: String,
        name: String,
        icon: String,
        color: String,
    ) {
        if (groupId !in BuiltInFavoriteGroupIds) return
        val fallback = defaultFavoriteGroupAppearance(groupId)
        val appearance = FavoriteGroupAppearance(
            name = name.trim().take(36).ifBlank { fallback.name },
            icon = icon,
            color = color,
        )
        val appearances = _state.value.favoriteGroupAppearances + (groupId to appearance)
        _state.update {
            it.copy(
                screen = AppScreen.FavoriteGroupsManager,
                favoriteGroupAppearances = appearances,
                error = null,
            )
        }
        persist { localStateStore.saveFavoriteGroupAppearances(appearances) }
    }

    fun toggleFavoriteGroupHidden(groupId: String) {
        val current = _state.value
        if (groupId !in validFavoriteGroupIds(current.favoriteGroups)) return
        val hidden = current.hiddenFavoriteGroupIds.toMutableSet().apply {
            if (!add(groupId)) remove(groupId)
        }.toSet()
        val selected = current.selectedFavoriteGroupId.takeIf { it !in hidden }
            ?: firstVisibleFavoriteGroupId(current.favoriteGroupOrder, hidden)
        _state.update {
            it.copy(
                hiddenFavoriteGroupIds = hidden,
                selectedFavoriteGroupId = selected,
                error = null,
            )
        }
        persist { localStateStore.saveHiddenFavoriteGroupIds(hidden) }
    }

    fun saveFavoriteGroup(
        groupId: String?,
        name: String,
        icon: String,
        color: String,
        itemKeys: Set<String>,
    ) {
        if (groupId in BuiltInFavoriteGroupIds) {
            saveBuiltInFavoriteGroupAppearance(groupId!!, name, icon, color)
            return
        }
        val trimmedName = name.trim().take(36)
        if (trimmedName.isBlank()) {
            _state.update { it.copy(error = "Enter a group name.") }
            return
        }
        val now = System.currentTimeMillis()
        val existing = _state.value.favoriteGroups.firstOrNull { it.id == groupId }
        val saved = FavoriteGroup(
            id = existing?.id ?: "group-${now.toString(36)}",
            name = trimmedName,
            icon = icon,
            color = color,
            itemKeys = itemKeys.intersect(_state.value.favorites.map(::itemKey).toSet()).take(250).toSet(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val groups = if (existing == null) {
            (_state.value.favoriteGroups + saved).take(24)
        } else {
            _state.value.favoriteGroups.map { if (it.id == saved.id) saved else it }
        }
        val groupOrder = normalizeGroupOrder(
            _state.value.favoriteGroupOrder + saved.id,
            groups,
        )
        _state.update {
            it.copy(
                screen = AppScreen.FavoriteGroupBrowser(saved.id),
                favoriteGroups = groups,
                favoriteGroupOrder = groupOrder,
                selectedFavoriteGroupId = saved.id,
                error = null,
                focusRequest = FocusRequest(favoriteFocusScope(saved.id), firstItem = true),
            )
        }
        persistGroups(groups, groupOrder, _state.value.favoriteItemOrders)
    }

    fun deleteFavoriteGroup(groupId: String) {
        val groups = _state.value.favoriteGroups.filterNot { it.id == groupId }
        val groupOrder = normalizeGroupOrder(
            _state.value.favoriteGroupOrder.filterNot { it == groupId },
            groups,
        )
        val itemOrders = _state.value.favoriteItemOrders - groupId
        val hidden = _state.value.hiddenFavoriteGroupIds - groupId
        val selected = firstVisibleFavoriteGroupId(groupOrder, hidden)
        _state.update {
            it.copy(
                screen = if (selected == "all" || selected.isBlank()) AppScreen.Favorites else AppScreen.FavoriteGroupBrowser(selected),
                favoriteGroups = groups,
                favoriteGroupOrder = groupOrder,
                favoriteItemOrders = itemOrders,
                hiddenFavoriteGroupIds = hidden,
                selectedFavoriteGroupId = selected,
                error = null,
                focusRequest = selected.takeIf { it.isNotBlank() }?.let { FocusRequest(favoriteFocusScope(it), firstItem = true) },
            )
        }
        persistGroups(groups, groupOrder, itemOrders)
        persist { localStateStore.saveHiddenFavoriteGroupIds(hidden) }
    }

    fun saveFavoriteGroupOrder(order: List<String>) {
        val normalized = normalizeGroupOrder(order, _state.value.favoriteGroups)
        _state.update { it.copy(favoriteGroupOrder = normalized, error = null) }
        persist { localStateStore.saveFavoriteGroupOrder(normalized) }
    }

    fun saveFavoriteItemOrder(groupId: String, order: List<String>) {
        if (groupId == "all" || _state.value.settings.favoriteAutoSort) return
        val validFavoriteKeys = _state.value.favorites.map(::itemKey).toSet()
        val normalized = order.filter { it in validFavoriteKeys }.distinct().take(250)
        val orders = _state.value.favoriteItemOrders + (groupId to normalized)
        _state.update { it.copy(favoriteItemOrders = orders, error = null) }
        persist { localStateStore.saveFavoriteItemOrders(orders) }
    }

    fun updateSettings(settings: AppSettings) {
        val normalized = settings.copy(maxCachedCategories = settings.maxCachedCategories.coerceIn(2, 5))
        _state.update { it.copy(settings = normalized, error = null) }
        trimCategoryCache(normalized.maxCachedCategories)
        persist { localStateStore.saveSettings(normalized) }
    }

    fun setProviderCategoryHidden(type: ContentType, categoryId: String, hidden: Boolean) {
        if (type == ContentType.EPISODE || categoryId.isBlank() || categoryId.startsWith("__")) return
        val current = _state.value.settings.hiddenCategoryIds(type)
        val next = setHiddenCategory(current, categoryId, hidden)
        if (next == current) return
        setProviderCategoriesHidden(type, next)
    }

    /**
     * Atomically applies a staged visibility selection. One state update and
     * one settings write keep a large bulk change from flickering through the
     * UI or racing the catalog loader.
     */
    fun setProviderCategoriesHidden(type: ContentType, hiddenIds: Set<String>) {
        if (type == ContentType.EPISODE) return
        val current = _state.value
        val providerIds = current.categories[type].orEmpty().map { it.id }.toSet()
        // Keep stored ids when a provider has not delivered its category list
        // yet; once it has, drop ids that no longer exist.
        val normalized = if (providerIds.isEmpty()) hiddenIds else hiddenIds.intersect(providerIds)
        val categories = current.settings.hiddenCatalogCategories.toMutableMap().apply {
            if (normalized.isEmpty()) remove(type) else put(type, normalized)
        }
        val settings = current.settings.copy(hiddenCatalogCategories = categories)
        val selected = current.selectedCategories[type]
        val clearSelection = selected != null && !selected.id.startsWith("__") && selected.id in normalized
        // A hidden category can no longer be reached from browse. Drop its
        // cached list and derived item references; favorites, recent items,
        // progress and the active player are kept in separate state.
        categoryCacheKeysToEvict(categoryCache.keys, type, normalized).forEach(categoryCache::remove)
        _state.update { state ->
            val scopedItems = state.items.withoutHiddenCategories(type, normalized)
            val loaded = state.loadedItems[type]
            state.copy(
                settings = settings,
                selectedCategories = if (clearSelection) state.selectedCategories - type else state.selectedCategories,
                items = if ((state.screen as? AppScreen.Catalog)?.type == type) scopedItems else state.items,
                loadedItems = if (loaded == null) {
                    state.loadedItems
                } else {
                    state.loadedItems + (type to loaded.withoutHiddenCategories(type, normalized))
                },
                richItems = state.richItems.filterValues { item ->
                    item.type != type || item.categoryId !in normalized
                },
                error = null,
            )
        }
        persist { localStateStore.saveSettings(settings) }
    }

    fun clearCatalogCache() {
        catalogRequestId += 1
        categoryCache.clear()
        movieDetailJobs.values.forEach { it.cancel() }
        movieDetailJobs.clear()
        mediaCacheStore.clear()
        epgUpdatedAt.clear()
        _state.update { it.copy(items = emptyList(), loadedItems = emptyMap(), richItems = emptyMap(), epg = emptyMap(), error = null) }
    }

    fun clearHistory() {
        _state.update { it.copy(recent = emptyList(), progress = emptyMap(), error = null) }
        persist { localStateStore.clearHistory() }
    }

    fun savePlaybackProgress(item: CatalogItem, positionMs: Long, durationMs: Long) {
        if (item.type == ContentType.LIVE || positionMs < 0L) return
        val key = itemKey(item)
        val next = _state.value.progress.toMutableMap()
        if (durationMs > 0L && positionMs >= durationMs - 30_000L) {
            next.remove(key)
        } else {
            next[key] = PlaybackProgress(positionMs, durationMs.coerceAtLeast(0L))
        }
        _state.update { it.copy(progress = next) }
        persist { localStateStore.saveProgress(next) }
    }

    fun clearPlaybackProgress(item: CatalogItem) {
        val next = _state.value.progress - itemKey(item)
        _state.update { it.copy(progress = next) }
        persist { localStateStore.saveProgress(next) }
    }

    fun disconnect() {
        connectionGeneration += 1
        catalogRequestId += 1
        client = null
        categoryCache.clear()
        movieDetailJobs.values.forEach { it.cancel() }
        movieDetailJobs.clear()
        lastFocusByArea.clear()
        epgUpdatedAt.clear()
        _state.update {
            it.copy(
                screen = AppScreen.Login,
                initializing = false,
                loading = false,
                catalogsLoading = false,
                error = null,
                credentials = null,
                provider = null,
                categories = emptyMap(),
                selectedCategories = emptyMap(),
                items = emptyList(),
                loadedItems = emptyMap(),
                richItems = emptyMap(),
                epg = emptyMap(),
                epgLoading = emptySet(),
                focusRequest = null,
                editingProvider = false,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { credentialsStore.clear() }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun back() {
        when (val screen = _state.value.screen) {
            AppScreen.Login, AppScreen.Home -> Unit
            is AppScreen.Player -> _state.update {
                it.copy(screen = screen.origin, error = null, focusRequest = screen.returnFocus)
            }
            is AppScreen.Detail -> _state.update {
                it.copy(
                    screen = screen.origin,
                    detailEpisodes = emptyList(),
                    detailSelectedSeason = null,
                    error = null,
                    focusRequest = screen.returnFocus,
                )
            }
            AppScreen.FavoriteGroupsManager -> openSettings()
            is AppScreen.FavoriteGroupEditor -> openFavoriteGroupsManager()
            AppScreen.CategoryVisibility -> openSettings()
            AppScreen.Settings ->
                if (_state.value.editingProvider) cancelProviderEdit() else openHome()
            is AppScreen.Catalog, AppScreen.Favorites, is AppScreen.FavoriteGroupBrowser -> openHome()
        }
    }

    private fun focusForScreen(screen: AppScreen): FocusRequest? = when (screen) {
        AppScreen.Home -> lastFocusByArea[AREA_HOME]
        is AppScreen.Catalog -> lastFocusByArea[catalogArea(screen.type)]
        AppScreen.Favorites, is AppScreen.FavoriteGroupBrowser -> lastFocusByArea[AREA_FAVORITES]
        is AppScreen.Detail -> lastFocusByArea[detailArea(screen.item.id)]
        else -> null
    }

    private fun currentQueueFor(item: CatalogItem): List<CatalogItem> = when {
        item.type == ContentType.EPISODE && _state.value.detailEpisodes.isNotEmpty() -> _state.value.detailEpisodes
        _state.value.items.isNotEmpty() -> _state.value.items
        else -> listOf(item)
    }

    private fun addRecent(item: CatalogItem) {
        val next = (listOf(item) + _state.value.recent.filterNot { itemKey(it) == itemKey(item) }).take(40)
        _state.update { it.copy(recent = next) }
        persist { localStateStore.saveRecent(next) }
    }

    private fun persistFavorites(
        favorites: List<CatalogItem>,
        groups: List<FavoriteGroup>,
        itemOrders: Map<String, List<String>>,
    ) {
        persist {
            localStateStore.saveFavorites(favorites)
            localStateStore.saveFavoriteGroups(groups)
            localStateStore.saveFavoriteItemOrders(itemOrders)
        }
    }

    private fun persistGroups(
        groups: List<FavoriteGroup>,
        groupOrder: List<String>,
        itemOrders: Map<String, List<String>>,
    ) {
        persist {
            localStateStore.saveFavoriteGroups(groups)
            localStateStore.saveFavoriteGroupOrder(groupOrder)
            localStateStore.saveFavoriteItemOrders(itemOrders)
        }
    }

    private fun normalizeGroupOrder(order: List<String>, groups: List<FavoriteGroup>): List<String> {
        val valid = BuiltInFavoriteGroupIds + groups.map { it.id }
        val validSet = valid.toSet()
        return (order.filter { it in validSet } + valid.filterNot { it in order }).distinct()
    }

    private fun updateLoadedItems(type: ContentType, items: List<CatalogItem>) {
        val freshById = items.associateBy { it.id }
        val before = _state.value
        // Only create new lists if artwork URLs actually changed
        val freshFavorites = before.favorites.mapNotNull { item ->
            val fresh = freshById[item.id]
            if (fresh == null || item.type != type) return@mapNotNull null
            val newImageUrl = fresh.imageUrl ?: item.imageUrl
            val newCoverUrl = fresh.coverUrl ?: item.coverUrl
            val newBackdropUrl = fresh.backdropUrl ?: item.backdropUrl
            if (newImageUrl == item.imageUrl && newCoverUrl == item.coverUrl && newBackdropUrl == item.backdropUrl) {
                null
            } else {
                item.copy(
                    imageUrl = newImageUrl,
                    coverUrl = newCoverUrl,
                    backdropUrl = newBackdropUrl,
                )
            }
        }
        val freshRecent = before.recent.mapNotNull { item ->
            val fresh = freshById[item.id]
            if (fresh == null || item.type != type) return@mapNotNull null
            val newImageUrl = fresh.imageUrl ?: item.imageUrl
            val newCoverUrl = fresh.coverUrl ?: item.coverUrl
            val newBackdropUrl = fresh.backdropUrl ?: item.backdropUrl
            if (newImageUrl == item.imageUrl && newCoverUrl == item.coverUrl && newBackdropUrl == item.backdropUrl) {
                null
            } else {
                item.copy(
                    imageUrl = newImageUrl,
                    coverUrl = newCoverUrl,
                    backdropUrl = newBackdropUrl,
                )
            }
        }
        val favorites = if (freshFavorites.isNotEmpty()) before.favorites.map { item ->
            freshFavorites.firstOrNull { it.id == item.id } ?: item
        } else before.favorites
        val recent = if (freshRecent.isNotEmpty()) before.recent.map { item ->
            freshRecent.firstOrNull { it.id == item.id } ?: item
        } else before.recent
        val favoritesChanged = favorites !== before.favorites
        val recentChanged = recent !== before.recent
        _state.update { state ->
            state.copy(
                loadedItems = state.loadedItems + (type to items.take(60)),
                favorites = if (favoritesChanged) favorites else state.favorites,
                recent = if (recentChanged) recent else state.recent,
            )
        }
        if (favoritesChanged || recentChanged) {
            persist {
                localStateStore.saveFavorites(favorites)
                localStateStore.saveRecent(recent)
            }
        }
    }

    private fun cacheCategory(type: ContentType, categoryId: String, items: List<CatalogItem>) {
        categoryCache[cacheKey(type, categoryId)] = items
        trimCategoryCache(_state.value.settings.maxCachedCategories)
    }

    private fun persist(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock { block() }
        }
    }

    private fun trimCategoryCache(maxPerType: Int) {
        // One pass per type: the previous loop rescanned the whole key set for
        // every eviction. Insertion order is preserved, so the oldest entries
        // are still the ones dropped.
        ContentType.entries.filter { it != ContentType.EPISODE }.forEach { type ->
            val prefix = "${type.name}:"
            val keys = categoryCache.keys.filter { it.startsWith(prefix) }
            val excess = keys.size - maxPerType
            if (excess > 0) keys.take(excess).forEach { categoryCache.remove(it) }
        }
    }

    private fun cacheKey(type: ContentType, categoryId: String) = "${type.name}:$categoryId"

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 30_000L
        const val EPG_TTL_MS = 5 * 60_000L
        const val EPG_RETRY_MS = 30_000L
    }
}

internal const val AREA_HOME = "home"
internal const val AREA_FAVORITES = "favorites"

internal fun catalogArea(type: ContentType): String = "catalog:${type.name}"
internal fun catalogFocusScope(type: ContentType, categoryId: String): String = "catalog:${type.name}:$categoryId"
internal fun homeFocusScope(type: ContentType): String = "home:${type.name}"
internal fun favoriteFocusScope(groupId: String): String = "favorites:$groupId"
internal fun detailArea(seriesId: String): String = "detail:$seriesId"
internal fun detailFocusScope(seriesId: String, season: Int?): String = "detail:$seriesId:${season ?: 0}"

internal fun validFavoriteGroupIds(groups: List<FavoriteGroup>): Set<String> =
    (BuiltInFavoriteGroupIds + groups.map { it.id }).toSet()

internal fun firstVisibleFavoriteGroupId(order: List<String>, hidden: Set<String>): String =
    (order + BuiltInFavoriteGroupIds).distinct().firstOrNull { it !in hidden }.orEmpty()

private fun Throwable.userMessage(): String = when (this) {
    is TimeoutCancellationException, is SocketTimeoutException ->
        "The provider did not respond in time. Check the address and try again."
    is UnknownHostException ->
        "The provider host could not be found. Check the address and network connection."
    is ConnectException ->
        "The provider refused the connection. Check the address and port."
    is SSLHandshakeException ->
        "The provider's HTTPS certificate could not be verified."
    is IllegalArgumentException -> message ?: "The provider details are invalid."
    else -> message?.takeIf { it.isNotBlank() } ?: "Unable to connect to the provider."
}
