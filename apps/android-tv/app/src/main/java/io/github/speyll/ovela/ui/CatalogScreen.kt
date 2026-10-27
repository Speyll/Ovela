package io.github.speyll.ovela.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.speyll.ovela.data.itemKey
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.PlaybackProgress
import io.github.speyll.ovela.model.hiddenCategoryIds
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CatalogScreen(
    type: ContentType,
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    navReturnFocus: FocusRequester,
    topContentPadding: Dp = 0.dp,
) {
    val touchUi = LocalTouchUi.current
    val colors = palette()
    val categories = remember(type, state.categories, state.favorites, state.favoriteGroups, state.settings) {
        catalogCategories(type, state)
    }
    val selected = state.selectedCategories[type]?.takeIf { category -> categories.any { it.id == category.id } }
        ?: categories.firstOrNull()
    val selectedId = selected?.id.orEmpty()
    val query = state.searchQuery.trim()
    // Large providers can return tens of thousands of titles. Debounce the
    // filter pass so typing stays responsive; clearing applies immediately so
    // a category switch never shows stale, query-filtered rows.
    var appliedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            appliedQuery = query
        } else {
            delay(140L)
            appliedQuery = query
        }
    }
    // Filtering a large provider category belongs off the composition thread:
    // this used to run on the main thread inside a `remember` block.
    val items by produceState(initialValue = state.items, key1 = state.items, key2 = appliedQuery) {
        value = if (appliedQuery.isBlank()) {
            state.items
        } else {
            withContext(Dispatchers.Default) {
                state.items.filter { it.name.contains(appliedQuery, ignoreCase = true) }
            }
        }
    }
    val scope = catalogFocusScope(type, selectedId)
    val focusRequest = state.focusRequest?.takeIf { it.scope == scope }
    val categoryFocusers = remember(type) { mutableMapOf<String, FocusRequester>() }
    val firstItemFocus = remember(type, selectedId) { FocusRequester() }
    val itemFocusers = remember(type, selectedId) { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val categoryRowState = rememberLazyListState()

    LaunchedEffect(focusRequest, items.size, selectedId) {
        if (focusRequest == null || items.isEmpty()) return@LaunchedEffect
        val target = when {
            focusRequest.firstItem -> 0
            focusRequest.itemKey != null -> items.indexOfFirst { itemKey(it) == focusRequest.itemKey }.coerceAtLeast(0)
            else -> 0
        }.coerceIn(items.indices)
        repeat(5) { attempt ->
            if (type == ContentType.LIVE) listState.scrollToItem(target) else gridState.scrollToItem(target)
            delay(if (attempt == 0) 28L else 20L)
            val key = itemKey(items[target])
            val requester = itemFocusers[key]
            if (requester != null && runCatching { requester.requestFocus() }.getOrDefault(false)) {
                viewModel.consumeFocusRequest(scope)
                return@LaunchedEffect
            }
        }
    }

    if (touchUi) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // The poster grid follows the actual width, so a landscape phone shows a
        // dense wall of posters instead of three oversized ones.
        val posterColumns = touchPosterColumns(maxWidth.value - 28f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = topContentPadding + 4.dp),
        ) {
            if (categories.isNotEmpty()) {
                // Keep the active category chip visible when the screen is
                // re-entered (for example after Back from a detail page), so the
                // rail always explains where the visible content came from.
                LaunchedEffect(selectedId, categories.size) {
                    val index = categories.indexOfFirst { it.id == selectedId }
                    if (index >= 0) categoryRowState.animateScrollToItem(index)
                }
                LazyRow(
                    state = categoryRowState,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(vertical = 3.dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        val active = category.id == selectedId
                        val requester = if (active) contentEntryFocus else categoryFocusers.getOrPut(category.id) { FocusRequester() }
                        CategoryChip(
                            label = category.name,
                            selected = active,
                            onClick = { selectCategory(viewModel, type, category) },
                            focusRequester = requester,
                            // Provider category names are long; a cap keeps several
                            // chips on screen instead of one chip per screen.
                            modifier = Modifier
                                .widthIn(max = 168.dp)
                                .then(if (active) Modifier.focusProperties { down = firstItemFocus } else Modifier),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
            CatalogContentHeader(type, selected?.name ?: type.title(), items.size, state.loading, query)
            Spacer(Modifier.height(8.dp))
            if (type == ContentType.LIVE) {
                LiveChannelList(
                    channels = items,
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    itemFocusers = itemFocusers,
                    firstItemFocus = firstItemFocus,
                    scope = scope,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MediaGrid(
                    type = type,
                    items = items,
                    state = state,
                    viewModel = viewModel,
                    gridState = gridState,
                    itemFocusers = itemFocusers,
                    firstItemFocus = firstItemFocus,
                    scope = scope,
                    columns = posterColumns,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        }
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 26.dp, end = 32.dp, top = topContentPadding + 8.dp),
        ) {
            Column(Modifier.width(236.dp).fillMaxSize()) {
                Text(
                    "Categories",
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                if (categories.isEmpty()) {
                    Text("No categories", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                } else {
                    // Returning from a detail or player screen must reveal the
                    // selected category rather than resetting the rail to the
                    // first provider category.
                    LaunchedEffect(selectedId, categories.size) {
                        val index = categories.indexOfFirst { it.id == selectedId }
                        if (index >= 0) categoryListState.scrollToItem(index)
                    }
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(categories, key = { it.id }) { category ->
                            val active = category.id == selectedId
                            val first = category.id == categories.first().id
                            val requester = if (active) contentEntryFocus else categoryFocusers.getOrPut(category.id) { FocusRequester() }
                            CategoryRailRow(
                                label = category.name,
                                selected = active,
                                onClick = { selectCategory(viewModel, type, category) },
                                focusRequester = requester,
                                modifier = Modifier.focusProperties {
                                    right = firstItemFocus
                                    if (first) {
                                        up = navReturnFocus
                                        left = navReturnFocus
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).fillMaxSize()) {
                CatalogContentHeader(type, selected?.name ?: type.title(), items.size, state.loading, query)
                Spacer(Modifier.height(10.dp))
                if (type == ContentType.LIVE) {
                    LiveChannelList(
                        channels = items,
                        state = state,
                        viewModel = viewModel,
                        listState = listState,
                        itemFocusers = itemFocusers,
                        firstItemFocus = firstItemFocus,
                        scope = scope,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MediaGrid(
                        type = type,
                        items = items,
                        state = state,
                        viewModel = viewModel,
                        gridState = gridState,
                        itemFocusers = itemFocusers,
                        firstItemFocus = firstItemFocus,
                        scope = scope,
                        columns = 7,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun selectCategory(viewModel: AppViewModel, type: ContentType, category: Category) {
    if (category.id.startsWith("__")) {
        viewModel.selectVirtualCategory(type, category)
    } else {
        viewModel.selectCategory(type, category)
    }
}

private fun catalogCategories(type: ContentType, state: AppUiState): List<Category> {
    val hidden = state.settings.hiddenCategoryIds(type)
    val favoriteKeys = state.favorites
        .filter { it.type == type && !isCatalogItemHidden(it, state.settings) }
        .map(::itemKey)
        .toSet()
    return buildList {
        if (favoriteKeys.isNotEmpty()) add(Category("__favorites__", "Favourites"))
        state.favoriteGroups
            .filter { group -> group.itemKeys.any { it in favoriteKeys } }
            .forEach { group -> add(Category("__group__:${group.id}", group.name)) }
        addAll(state.categories[type].orEmpty().filter { it.id !in hidden })
    }
}

@Composable
private fun CatalogContentHeader(
    type: ContentType,
    title: String,
    count: Int,
    loading: Boolean,
    query: String,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                if (query.isNotBlank()) "Search results" else title,
                color = colors.text,
                fontSize = if (touchUi) 22.sp else 24.sp,
                lineHeight = if (touchUi) 25.sp else 27.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    query.isNotBlank() -> "\"$query\"  ·  " +
                        if (count == 1) "1 result" else "$count results"
                    type == ContentType.LIVE -> if (count == 1) "1 channel" else "$count channels"
                    else -> if (count == 1) "1 title" else "$count titles"
                },
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun MediaGrid(
    type: ContentType,
    items: List<CatalogItem>,
    state: AppUiState,
    viewModel: AppViewModel,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    itemFocusers: MutableMap<String, FocusRequester>,
    firstItemFocus: FocusRequester,
    scope: String,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    if (items.isEmpty()) {
        CatalogEmptyState(state.loading)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(if (touchUi) 9.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (touchUi) 12.dp else 18.dp),
    ) {
        gridItems(items, key = { itemKey(it) }) { item ->
            val key = itemKey(item)
            val first = key == itemKey(items.first())
            val requester = if (first) firstItemFocus else itemFocusers.getOrPut(key) { FocusRequester() }
            itemFocusers[key] = requester
            val progress = state.progress[key]
            Column {
                Box(Modifier.fillMaxWidth()) {
                    PosterArtCard(
                        item = item,
                        favorite = viewModel.isFavorite(item),
                        onClick = { viewModel.openDetail(item) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = requester,
                        onFocused = {
                            viewModel.rememberFocusedItem(catalogArea(type), scope, key)
                            if (item.type == ContentType.MOVIE) viewModel.requestMovieDetails(item, delayMs = 320L)
                        },
                    )
                    if (progress != null && progress.durationMs > 0L) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(colors.background.copy(alpha = .70f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .background(colors.accent),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    item.name,
                    color = colors.text,
                    fontSize = if (touchUi) 12.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        progress != null && progress.positionMs > 0L -> "Resume ${formatDuration(progress.positionMs)}"
                        !item.releaseDate.isNullOrBlank() -> item.releaseDate.take(4)
                        !item.genre.isNullOrBlank() -> item.genre.substringBefore(',').trim()
                        item.type == ContentType.SERIES -> "Series"
                        else -> "Movie"
                    },
                    color = if (progress != null) colors.accent else colors.muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveChannelList(
    channels: List<CatalogItem>,
    state: AppUiState,
    viewModel: AppViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemFocusers: MutableMap<String, FocusRequester>,
    firstItemFocus: FocusRequester,
    scope: String,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) {
        CatalogEmptyState(state.loading)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp, top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(channels, key = { itemKey(it) }) { channel ->
            val key = itemKey(channel)
            val first = key == itemKey(channels.first())
            val requester = if (first) firstItemFocus else itemFocusers.getOrPut(key) { FocusRequester() }
            itemFocusers[key] = requester
            LiveChannelRow(
                channel = channel,
                favorite = viewModel.isFavorite(channel),
                onClick = { viewModel.play(channel, channels) },
                onToggleFavorite = { viewModel.toggleFavorite(channel) },
                focusRequester = requester,
                onFocused = { viewModel.rememberFocusedItem(catalogArea(ContentType.LIVE), scope, key) },
            )
        }
    }
}

@Composable
private fun CatalogEmptyState(loading: Boolean) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
                Text("Loading category…", color = colors.muted, fontSize = if (touchUi) 13.sp else 13.sp)
            }
        } else {
            Text("No titles in this category.", color = colors.muted, fontSize = if (touchUi) 13.sp else 13.sp)
        }
    }
}

@Composable
private fun CategoryRailRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    TvSurface(
        modifier = modifier.fillMaxWidth().height(42.dp),
        onClick = onClick,
        active = selected,
        focusRequester = focusRequester,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(if (selected) 3.dp else 0.dp)
                    .height(18.dp)
                    .background(colors.accent, RoundedCornerShape(99.dp)),
            )
            if (selected) Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (selected) colors.accent else colors.text.copy(alpha = .86f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    TvSurface(
        modifier = modifier.height(40.dp),
        onClick = onClick,
        active = selected,
        focusRequester = focusRequester,
        radius = 20.dp,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) colors.accent else colors.text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    var focused by remember(channel.id) { mutableStateOf(false) }
    TvSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (touchUi) 60.dp else 54.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PROG_RED
                ) {
                    onToggleFavorite()
                    true
                } else {
                    false
                }
            },
        onClick = onClick,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.channelNumber?.toString() ?: "—",
                color = if (focused) colors.accent else colors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(44.dp),
            )
            Artwork(channel, Modifier.width(58.dp).height(40.dp), live = true, transparentLive = true)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                // A live-only list does not need "Live TV" repeated under every
                // channel; the name and the LIVE marker carry the row.
                Text(
                    channel.name,
                    color = colors.text,
                    fontSize = if (touchUi) 14.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (favorite) {
                TvIcon("heart", colors.danger, Modifier.size(14.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text("LIVE", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
    }
}
