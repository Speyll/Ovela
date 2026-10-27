package io.github.speyll.ovela.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.speyll.ovela.data.itemKey
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.ui.theme.palette
import kotlinx.coroutines.delay

/** Focus-safe Favorites hub with explicit edit, visibility, and reorder controls. */
@Composable
fun StableFavoritesScreen(
    initialGroupId: String,
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val allGroups = rememberStableGroups(state, includeHidden = true)
    val groups = allGroups.filterNot { it.hidden }
    val selectedId = state.selectedFavoriteGroupId
        .takeIf { id -> groups.any { it.id == id } }
        ?: initialGroupId.takeIf { id -> groups.any { it.id == id } }
        ?: groups.firstOrNull()?.id
    val selected = groups.firstOrNull { it.id == selectedId }
    val selectedGroupFocus = remember { FocusRequester() }

    // Entering a group that has no items (for example a freshly created custom
    // group) replaces the editor, so the grid effect never runs. Land on the
    // group's card in the rail rather than leaving the remote with no focus.
    LaunchedEffect(state.focusRequest, selectedId, selected?.items?.size) {
        val request = state.focusRequest ?: return@LaunchedEffect
        if (selected == null || selected.items.isNotEmpty()) return@LaunchedEffect
        if (request.scope != favoriteFocusScope(selected.id)) return@LaunchedEffect
        delay(24L)
        runCatching { selectedGroupFocus.requestFocus() }
        viewModel.consumeFocusRequest(request.scope)
    }

    LaunchedEffect(selectedId, state.selectedFavoriteGroupId) {
        if (selectedId != null && selectedId != state.selectedFavoriteGroupId) {
            viewModel.selectFavoriteGroup(selectedId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "My Groups",
                    color = palette().text,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Your favorite content, organized your way.",
                    color = palette().muted,
                    fontSize = 10.sp,
                )
            }
            StableActionButton(
                label = "Edit groups",
                onClick = {
                    StableFavoritesNavigation.pendingItemGroupId = null
                    viewModel.openFavoriteGroupsManager()
                },
                modifier = Modifier.width(112.dp),
            )
            Spacer(Modifier.width(8.dp))
            StableActionButton(
                label = "+ Add group",
                onClick = { viewModel.openFavoriteEditor() },
                modifier = Modifier.width(112.dp),
            )
        }

        if (groups.isEmpty()) {
            StableAllGroupsHidden(
                onManage = viewModel::openFavoriteGroupsManager,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(102.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        ) {
            items(groups, key = { it.id }) { group ->
                StableGroupCard(
                    group = group,
                    selected = group.id == selected?.id,
                    onClick = { viewModel.selectFavoriteGroup(group.id) },
                    modifier = Modifier.width(178.dp),
                    focusRequester = if (group.id == selected?.id) selectedGroupFocus else null,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StableSectionHeader(selected?.name.orEmpty(), selected?.items?.size ?: 0, Modifier.weight(1f))
            if (selected != null && selected.id != "all") {
                StableActionButton(
                    label = "Reorder items",
                    onClick = {
                        StableFavoritesNavigation.pendingItemGroupId = selected.id
                        viewModel.openFavoriteGroupsManager()
                    },
                    modifier = Modifier.width(124.dp),
                    enabled = true,
                )
            }
        }
        Spacer(Modifier.height(7.dp))

        if (selected == null || selected.items.isEmpty()) {
            StableEmptyFavorites(
                hasFavorites = state.favorites.isNotEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            StableFavoriteGrid(selected, state, viewModel)
        }
    }
}

@Composable
private fun StableFavoriteGrid(
    group: StableFavoriteGroup,
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val scope = favoriteFocusScope(group.id)
    val focusRequest = state.focusRequest?.takeIf { it.scope == scope }
    val targetIndex = remember(group.items, focusRequest) {
        when {
            focusRequest == null -> -1
            focusRequest.firstItem -> 0
            focusRequest.itemKey != null -> group.items.indexOfFirst { itemKey(it) == focusRequest.itemKey }.coerceAtLeast(0)
            else -> 0
        }
    }
    val gridState = rememberLazyGridState()
    val focusRequester = remember(scope, focusRequest?.itemKey, focusRequest?.firstItem) { FocusRequester() }
    var focusedIndex by remember(scope) { mutableIntStateOf(-1) }
    val columns = 4

    LaunchedEffect(focusRequest, targetIndex) {
        if (focusRequest != null && targetIndex in group.items.indices) {
            gridState.scrollToItem(gridRowStart(targetIndex, columns))
            repeat(5) { attempt ->
                delay(if (attempt == 0) 48L else 24L)
                if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                    viewModel.consumeFocusRequest(scope)
                    return@LaunchedEffect
                }
            }
        }
    }
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) gridState.ensureFocusedRowVisible(focusedIndex, columns)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visibleRows = 2
        val gap = 9.dp
        val topPadding = 2.dp
        val bottomPadding = 12.dp
        val density = LocalDensity.current
        val cardHeight = with(density) {
            val availablePx = (maxHeight - topPadding - bottomPadding - gap * (visibleRows - 1)).roundToPx()
            (availablePx / visibleRows).toDp()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(
                start = 2.dp,
                end = 2.dp,
                top = topPadding,
                bottom = bottomPadding,
            ),
        ) {
            itemsIndexed(group.items, key = { _, item -> itemKey(item) }) { index, item ->
                StableFavoriteItemCard(
                    item = item,
                    onClick = { viewModel.activate(item) },
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    focusRequester = if (focusRequest != null && index == targetIndex) focusRequester else null,
                    onFocused = {
                        focusedIndex = index
                        viewModel.rememberFocusedItem(AREA_FAVORITES, scope, itemKey(item))
                    },
                )
            }
        }
    }
}

/** Explicit D-pad reorder/edit workspace used instead of long-press gestures. */
@Composable
fun StableFavoriteGroupsManagerScreen(state: AppUiState, viewModel: AppViewModel) {
    val groups = rememberStableGroups(state, includeHidden = true)
    var itemGroupId by rememberSaveable {
        mutableStateOf(StableFavoritesNavigation.consumePendingItemGroupId())
    }
    val itemGroup = itemGroupId?.let { id -> groups.firstOrNull { it.id == id && it.id != "all" } }

    // Opening this screen replaces the Settings card that had focus, and
    // switching to the per-group item list replaces the row that had focus.
    // Request a target on both transitions so the D-pad never starts dead.
    val entryFocus = remember { FocusRequester() }
    LaunchedEffect(itemGroupId) {
        delay(24L)
        runCatching { entryFocus.requestFocus() }
    }

    if (itemGroup != null) {
        // The on-screen Back returns to the group list; make the remote Back
        // do the same instead of jumping straight out to Settings.
        BackHandler(enabled = true) { itemGroupId = null }
        StableFavoriteItemsManager(
            group = itemGroup,
            state = state,
            viewModel = viewModel,
            entryFocus = entryFocus,
            onBack = { itemGroupId = null },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Edit Favorite Groups", color = palette().text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Reorder, customize, hide, or show any group.",
                    color = palette().muted,
                    fontSize = 10.sp,
                )
            }
            StableActionButton("Back", viewModel::back, Modifier.width(78.dp))
            Spacer(Modifier.width(8.dp))
            StableActionButton("+ Add group", { viewModel.openFavoriteEditor() }, Modifier.width(108.dp))
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            items(groups, key = { it.id }) { group ->
                val index = groups.indexOfFirst { it.id == group.id }
                StableManagerGroupRow(
                    group = group,
                    canMoveUp = index > 0,
                    canMoveDown = index < groups.lastIndex,
                    focusRequester = if (index == 0) entryFocus else null,
                    onMoveUp = { viewModel.saveFavoriteGroupOrder(moveKey(groups.map { it.id }, group.id, -1)) },
                    onMoveDown = { viewModel.saveFavoriteGroupOrder(moveKey(groups.map { it.id }, group.id, 1)) },
                    onEdit = { viewModel.openFavoriteEditor(group.id) },
                    onVisibility = { viewModel.toggleFavoriteGroupHidden(group.id) },
                    onItems = if (group.id != "all") ({ itemGroupId = group.id }) else null,
                )
            }
        }
    }
}

@Composable
private fun StableFavoriteItemsManager(
    group: StableFavoriteGroup,
    state: AppUiState,
    viewModel: AppViewModel,
    entryFocus: FocusRequester,
    onBack: () -> Unit,
) {
    val current = rememberStableGroups(state, includeHidden = true).firstOrNull { it.id == group.id } ?: group

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reorder ${current.name}", color = palette().text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Use Move up and Move down. Changes are saved immediately.", color = palette().muted, fontSize = 10.sp)
            }
            StableActionButton("Back", onBack, Modifier.width(78.dp), focusRequester = entryFocus)
        }

        Spacer(Modifier.height(12.dp))
        if (current.items.size < 2) {
            StableEmptyFavorites(hasFavorites = state.favorites.isNotEmpty(), modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                items(current.items, key = { itemKey(it) }) { item ->
                    val index = current.items.indexOfFirst { itemKey(it) == itemKey(item) }
                    StableManagerItemRow(
                        item = item,
                        position = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < current.items.lastIndex,
                        onMoveUp = {
                            viewModel.saveFavoriteItemOrder(
                                current.id,
                                moveKey(current.items.map(::itemKey), itemKey(item), -1),
                            )
                        },
                        onMoveDown = {
                            viewModel.saveFavoriteItemOrder(
                                current.id,
                                moveKey(current.items.map(::itemKey), itemKey(item), 1),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StableManagerGroupRow(
    group: StableFavoriteGroup,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onVisibility: () -> Unit,
    onItems: (() -> Unit)?,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(palette().panel.copy(alpha = 0.90f), RoundedCornerShape(11.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StableGroupGlyph(group, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    color = palette().text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${group.items.size} items · ${if (group.hidden) "Hidden" else if (group.custom) "Custom" else "Built-in"}",
                    color = palette().muted,
                    fontSize = 9.sp,
                )
            }
            onItems?.let {
                StableActionButton("Items", it, Modifier.width(66.dp), focusRequester = focusRequester)
                Spacer(Modifier.width(6.dp))
            }
            StableActionButton(
                "Edit",
                onEdit,
                Modifier.width(60.dp),
                focusRequester = if (onItems == null) focusRequester else null,
            )
            Spacer(Modifier.width(6.dp))
            StableActionButton(if (group.hidden) "Show" else "Hide", onVisibility, Modifier.width(64.dp))
            Spacer(Modifier.width(6.dp))
            StableActionButton("Move up", onMoveUp, Modifier.width(76.dp), enabled = canMoveUp)
            Spacer(Modifier.width(6.dp))
            StableActionButton("Move down", onMoveDown, Modifier.width(84.dp), enabled = canMoveDown)
        }
    }
}

@Composable
private fun StableManagerItemRow(
    item: CatalogItem,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(palette().panel.copy(alpha = 0.90f), RoundedCornerShape(11.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(position.toString(), color = palette().accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp))
            StableArtwork(item, Modifier.width(48.dp).height(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = palette().text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.type.stableLabel(), color = palette().muted, fontSize = 8.sp)
            }
            StableActionButton("Move up", onMoveUp, Modifier.width(82.dp), enabled = canMoveUp)
            Spacer(Modifier.width(7.dp))
            StableActionButton("Move down", onMoveDown, Modifier.width(92.dp), enabled = canMoveDown)
        }
    }
}

@Composable
private fun StableGroupCard(
    group: StableFavoriteGroup,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvSurface(
        modifier = modifier.height(96.dp),
        onClick = onClick,
        active = selected,
        background = group.color.copy(alpha = if (selected) 0.82f else 0.62f),
        radius = 14.dp,
        focusRequester = focusRequester,
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StableGroupGlyph(group, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("${group.items.size} ${if (group.items.size == 1) "item" else "items"}", color = Color.White.copy(alpha = 0.76f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun StableGroupGlyph(group: StableFavoriteGroup, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        TvIcon(group.icon, Color.White, Modifier.size(22.dp))
    }
}

@Composable
private fun StableFavoriteItemCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    TvSurface(
        modifier = modifier,
        onClick = onClick,
        background = palette().panel.copy(alpha = 0.92f),
        radius = 11.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            StableArtwork(item, Modifier.width(58.dp).fillMaxHeight())
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = palette().text, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(item.type.stableLabel(), color = palette().muted, fontSize = 8.sp)
            }
            Text("♥", color = palette().danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StableArtwork(item: CatalogItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    if (item.imageUrl.isNullOrBlank()) {
        Box(modifier = modifier.background(Color.White.copy(alpha = 0.08f), shape), contentAlignment = Alignment.Center) {
            Text(item.name.take(1).uppercase(), color = palette().muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.name,
            modifier = modifier.background(Color.White.copy(alpha = 0.06f), shape),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun StableActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    TvSurface(
        modifier = modifier.height(42.dp),
        onClick = onClick,
        enabled = enabled,
        background = if (enabled) palette().panel.copy(alpha = 0.94f) else palette().panel.copy(alpha = 0.42f),
        radius = 10.dp,
        focusRequester = focusRequester,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) palette().text else palette().muted.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun StableSectionHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette().text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count ${if (count == 1) "item" else "items"}", color = palette().muted, fontSize = 9.sp)
    }
}

@Composable
private fun StableEmptyFavorites(hasFavorites: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♡", color = palette().accent, fontSize = 38.sp)
            Spacer(Modifier.height(6.dp))
            Text(if (hasFavorites) "No items in this group" else "No favorites yet", color = palette().text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (hasFavorites) "Open Edit groups to add favorites or choose another group."
                else "Focus a channel, movie, or series and add it to Favorites.",
                color = palette().muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun StableAllGroupsHidden(onManage: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TvIcon("heart", palette().accent, Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("All groups are hidden", color = palette().text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Open Edit groups to show the collections you want on this screen.", color = palette().muted, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            StableActionButton("Edit groups", onManage, Modifier.width(116.dp))
        }
    }
}

private object StableFavoritesNavigation {
    var pendingItemGroupId: String? = null
    fun consumePendingItemGroupId(): String? = pendingItemGroupId.also { pendingItemGroupId = null }
}

private data class StableFavoriteGroup(
    val id: String,
    val name: String,
    val icon: String,
    val color: Color,
    val items: List<CatalogItem>,
    val custom: Boolean,
    val hidden: Boolean,
)

@Composable
private fun rememberStableGroups(state: AppUiState, includeHidden: Boolean): List<StableFavoriteGroup> = remember(
    state.favorites,
    state.favoriteGroups,
    state.favoriteGroupOrder,
    state.favoriteItemOrders,
    state.favoriteGroupAppearances,
    state.hiddenFavoriteGroupIds,
    includeHidden,
) {
    val favoritesByKey = state.favorites.associateBy(::itemKey)
    val builtIns = BuiltInFavoriteGroupIds.map { id ->
        val appearance = favoriteGroupAppearance(id, state.favoriteGroupAppearances)
        val items = when (id) {
            "live" -> state.favorites.filter { it.type == ContentType.LIVE }
            "movie" -> state.favorites.filter { it.type == ContentType.MOVIE }
            "series" -> state.favorites.filter { it.type == ContentType.SERIES }
            else -> state.favorites
        }
        StableFavoriteGroup(
            id = id,
            name = appearance.name,
            icon = appearance.icon,
            color = stableGroupColor(appearance.color),
            items = items,
            custom = false,
            hidden = id in state.hiddenFavoriteGroupIds,
        )
    }
    val custom = state.favoriteGroups.map { group ->
        StableFavoriteGroup(
            id = group.id,
            name = group.name,
            icon = group.icon,
            color = stableGroupColor(group.color),
            items = group.itemKeys.mapNotNull(favoritesByKey::get),
            custom = true,
            hidden = group.id in state.hiddenFavoriteGroupIds,
        )
    }
    val unordered = builtIns + custom
    val byId = unordered.associateBy { it.id }
    val ids = (state.favoriteGroupOrder + unordered.map { it.id }).distinct()
    ids.mapNotNull(byId::get)
        .map { group -> group.copy(items = applyStableOrder(group.items, state.favoriteItemOrders[group.id].orEmpty())) }
        .filter { includeHidden || !it.hidden }
}

private fun applyStableOrder(items: List<CatalogItem>, order: List<String>): List<CatalogItem> {
    if (order.isEmpty()) return items
    val byKey = items.associateBy(::itemKey)
    return (order.mapNotNull(byKey::get) + items.filterNot { itemKey(it) in order }).distinctBy(::itemKey)
}

private fun moveKey(order: List<String>, key: String, delta: Int): List<String> {
    val from = order.indexOf(key)
    if (from < 0) return order
    val to = (from + delta).coerceIn(0, order.lastIndex)
    if (from == to) return order
    return order.toMutableList().apply {
        val moved = removeAt(from)
        add(to, moved)
    }
}

private fun ContentType.stableLabel(): String = when (this) {
    ContentType.LIVE -> "Live TV"
    ContentType.MOVIE -> "Movie"
    ContentType.SERIES -> "Series"
    ContentType.EPISODE -> "Episode"
}

private fun stableGroupColor(value: String): Color = when (value.lowercase()) {
    "blue" -> Color(0xFF3157A4)
    "teal" -> Color(0xFF087C78)
    "green" -> Color(0xFF3F7D46)
    "orange" -> Color(0xFFB75E24)
    "red" -> Color(0xFFA63B45)
    "pink", "rose" -> Color(0xFFA63D76)
    "lime" -> Color(0xFF697E2B)
    "slate" -> Color(0xFF4F5D6A)
    else -> Color(0xFF6D49A7)
}
