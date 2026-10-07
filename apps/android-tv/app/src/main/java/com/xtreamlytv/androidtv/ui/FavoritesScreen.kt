package com.xtreamlytv.androidtv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.FavoriteGroup
import com.xtreamlytv.androidtv.ui.theme.palette

@Composable
fun FavoritesHomeScreen(state: AppUiState, viewModel: AppViewModel) {
    FavoritesHubScreen(selectedGroupId = "all", state = state, viewModel = viewModel)
}

@Composable
fun FavoriteGroupBrowserScreen(groupId: String, state: AppUiState, viewModel: AppViewModel) {
    FavoritesHubScreen(selectedGroupId = groupId, state = state, viewModel = viewModel)
}

@Composable
private fun FavoritesHubScreen(
    selectedGroupId: String,
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val groups = orderedGroups(state)
    val selected = groups.firstOrNull { it.id == selectedGroupId } ?: groups.first()
    val selectedItems = orderedGroupItems(state, selected)
    val selectedByKey = selectedItems.associateBy(::itemKey)
    val recent = if (selected.id == "all") {
        state.recent.mapNotNull { selectedByKey[itemKey(it)] }.distinctBy(::itemKey).take(8)
    } else {
        emptyList()
    }

    var movingGroupId by remember { mutableStateOf<String?>(null) }
    var groupDraft by remember { mutableStateOf<List<String>>(emptyList()) }
    var movingItemKey by remember(selected.id) { mutableStateOf<String?>(null) }
    var itemDraft by remember(selected.id) { mutableStateOf<List<String>>(emptyList()) }

    val visibleGroups = if (movingGroupId != null) {
        val byId = groups.associateBy { it.id }
        groupDraft.mapNotNull(byId::get)
    } else {
        groups
    }
    val visibleItems = if (movingItemKey != null) {
        val byKey = selectedItems.associateBy(::itemKey)
        itemDraft.mapNotNull(byKey::get)
    } else {
        selectedItems
    }

    fun cancelMove() {
        movingGroupId = null
        movingItemKey = null
        groupDraft = emptyList()
        itemDraft = emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val activeGroup = movingGroupId
                if (activeGroup != null) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            groupDraft = moveEntry(groupDraft, activeGroup, -1)
                            true
                        }
                        Key.DirectionRight -> {
                            groupDraft = moveEntry(groupDraft, activeGroup, 1)
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            viewModel.saveFavoriteGroupOrder(groupDraft)
                            cancelMove()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            cancelMove()
                            true
                        }
                        else -> false
                    }
                } else {
                    val activeItem = movingItemKey
                    if (activeItem != null) {
                        val columns = FAVORITE_GRID_COLUMNS
                        val delta = when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            Key.DirectionUp -> -columns
                            Key.DirectionDown -> columns
                            else -> 0
                        }
                        when {
                            delta != 0 -> {
                                itemDraft = moveEntry(itemDraft, activeItem, delta)
                                true
                            }
                            event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter -> {
                                viewModel.saveFavoriteItemOrder(selected.id, itemDraft)
                                cancelMove()
                                true
                            }
                            event.key == Key.Back || event.key == Key.Escape -> {
                                cancelMove()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My Groups", color = palette().text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Your favorite content, organized your way.", color = palette().muted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        movingGroupId != null -> "Move with ◀ ▶  •  OK to save  •  Back to cancel"
                        movingItemKey != null -> "Move with the D-pad  •  OK to save  •  Back to cancel"
                        else -> "Long-press a card to reorder"
                    },
                    color = if (movingGroupId != null || movingItemKey != null) palette().accent else palette().muted,
                    fontSize = 8.sp,
                    fontWeight = if (movingGroupId != null || movingItemKey != null) FontWeight.SemiBold else FontWeight.Normal,
                )
                TvButton(
                    label = "Edit groups",
                    onClick = viewModel::openFavoriteGroupsManager,
                    modifier = Modifier.width(102.dp),
                    style = TvButtonStyle.Secondary,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        val groupListState = rememberLazyListState()
        val groupScrolling = groupListState.isScrollInProgress
        LaunchedEffect(groupScrolling) {
            if (!groupScrolling && groupListState.firstVisibleItemScrollOffset != 0) {
                groupListState.animateScrollToItem(groupListState.firstVisibleItemIndex)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp)) {
            val groupGap = 8.dp
            val groupCardWidth = (maxWidth - groupGap * 4f) / 5f
            LazyRow(
                state = groupListState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(groupGap),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(visibleGroups, key = { it.id }) { group ->
                    FavoriteGroupCard(
                        group = group,
                        selected = group.id == selected.id,
                        moving = group.id == movingGroupId,
                        onClick = {
                            if (movingGroupId == null && movingItemKey == null) viewModel.openFavoriteGroup(group.id)
                        },
                        onLongClick = {
                            if (movingItemKey == null) {
                                movingGroupId = group.id
                                groupDraft = groups.map { it.id }
                            }
                        },
                        modifier = Modifier.width(groupCardWidth),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Crossfade(targetState = selected.id, label = "favoriteGroupContent") { groupId ->
            val activeGroup = groups.firstOrNull { it.id == groupId } ?: selected
            Column(Modifier.fillMaxSize()) {
                if (activeGroup.id == "all" && recent.isNotEmpty()) {
                    SectionHeader("Recently watched favorites", itemCountLabel(recent.size))
                    Spacer(Modifier.height(7.dp))
                    val recentListState = rememberLazyListState()
                    val recentScrolling = recentListState.isScrollInProgress
                    LaunchedEffect(recentScrolling) {
                        if (!recentScrolling && recentListState.firstVisibleItemScrollOffset != 0) {
                            recentListState.animateScrollToItem(recentListState.firstVisibleItemIndex)
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp)) {
                        val recentGap = 8.dp
                        val recentCardWidth = (maxWidth - recentGap * 3f) / 4f
                        LazyRow(
                            state = recentListState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(recentGap),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(recent, key = { itemKey(it) }) { item ->
                                FavoriteMixedCard(
                                    item = item,
                                    favorite = true,
                                    onClick = { viewModel.activate(item) },
                                    modifier = Modifier.width(recentCardWidth),
                                    cardHeight = 82.dp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                SectionHeader(activeGroup.name, itemCountLabel(visibleItems.size))
                Spacer(Modifier.height(7.dp))
                if (state.favorites.isEmpty()) {
                    EmptyState("No favorites yet", "Open a channel, movie, or series and choose Add favorite.")
                } else if (visibleItems.isEmpty()) {
                    EmptyState("No items in this group", "Use Edit groups to add favorites to this collection.")
                } else {
                    FavoriteGrid(
                        groupId = activeGroup.id,
                        items = visibleItems,
                        movingItemKey = movingItemKey,
                        onActivate = viewModel::activate,
                        onLongClick = { item ->
                            if (movingGroupId == null) {
                                movingItemKey = itemKey(item)
                                itemDraft = selectedItems.map(::itemKey)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteGrid(
    groupId: String,
    items: List<CatalogItem>,
    movingItemKey: String?,
    onActivate: (CatalogItem) -> Unit,
    onLongClick: (CatalogItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = FAVORITE_GRID_COLUMNS
        val gap = 8.dp
        val visibleRows = when {
            maxHeight >= 330.dp -> 4
            maxHeight >= 245.dp -> 3
            else -> 2
        }
        val cardHeight = (maxHeight - gap * (visibleRows - 1).toFloat()) / visibleRows.toFloat()
        val gridState = rememberLazyGridState()
        val scrolling = gridState.isScrollInProgress

        LaunchedEffect(scrolling, groupId) {
            if (!scrolling && gridState.firstVisibleItemScrollOffset != 0) {
                val rowStart = (gridState.firstVisibleItemIndex / columns) * columns
                gridState.animateScrollToItem(rowStart)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { _, item ->
                FavoriteMixedCard(
                    item = item,
                    favorite = true,
                    onClick = { if (movingItemKey == null) onActivate(item) },
                    onLongClick = { onLongClick(item) },
                    moving = movingItemKey == itemKey(item),
                    modifier = Modifier.fillMaxWidth(),
                    cardHeight = cardHeight,
                )
            }
        }
    }
}

@Composable
fun FavoriteGroupsManagerScreen(state: AppUiState, viewModel: AppViewModel) {
    val groups = orderedGroups(state)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Edit Favorite Groups", color = palette().text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Customize collections or create a new group.", color = palette().muted, fontSize = 10.sp)
            }
            TvButton("Back", viewModel::back, Modifier.width(78.dp), TvButtonStyle.Secondary)
            Spacer(Modifier.width(7.dp))
            TvButton("Add group", { viewModel.openFavoriteEditor() }, Modifier.width(96.dp), leading = "+")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(groups, key = { it.id }) { group ->
                val custom = state.favoriteGroups.firstOrNull { it.id == group.id }
                TvSurface(
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    onClick = { if (custom != null) viewModel.openFavoriteEditor(group.id) },
                    enabled = custom != null,
                    background = palette().panel.copy(alpha = 0.88f),
                    radius = 11.dp,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).background(group.color.groupColor(), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TvIcon(group.icon, Color.White, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(group.name, color = palette().text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (custom == null) "Built-in group" else itemCountLabel(group.items.size),
                                color = palette().muted,
                                fontSize = 9.sp,
                            )
                        }
                        Text(
                            if (custom == null) "Built-in" else "Edit  ›",
                            color = if (custom == null) palette().muted else palette().accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteGroupEditorScreen(groupId: String?, state: AppUiState, viewModel: AppViewModel) {
    val builtInId = groupId?.takeIf { it in BuiltInFavoriteGroupIds }
    val existing = state.favoriteGroups.firstOrNull { it.id == groupId }
    val builtInAppearance = builtInId?.let { favoriteGroupAppearance(it, state.favoriteGroupAppearances) }

    var name by remember(groupId, builtInAppearance) {
        mutableStateOf(builtInAppearance?.name ?: existing?.name.orEmpty())
    }
    var icon by remember(groupId, builtInAppearance) {
        mutableStateOf(builtInAppearance?.icon ?: existing?.icon ?: "folder")
    }
    var color by remember(groupId, builtInAppearance) {
        mutableStateOf(builtInAppearance?.color ?: existing?.color ?: "purple")
    }
    var selectedKeys by remember(groupId) { mutableStateOf(existing?.itemKeys ?: emptySet()) }
    var filter by remember(groupId) { mutableStateOf<ContentType?>(null) }
    val visible = filterItems(state.favorites, filter)
    val builtIn = builtInId != null

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(230.dp)
                .fillMaxHeight()
                .background(palette().panel.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Text(if (groupId == null) "Add Group" else "Edit Group", color = palette().text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (builtIn) "Customize this built-in group name, icon, and color."
                else "Choose a name, icon, color, and favorites.",
                color = palette().muted,
                fontSize = 9.sp,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            TvTextField("Group name", name, { name = it.take(36) }, placeholder = "Weekend Movies")
            Spacer(Modifier.height(10.dp))
            Text("Group icon", color = palette().muted, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(GROUP_ICONS) { option ->
                    TvSurface(
                        modifier = Modifier.size(34.dp),
                        onClick = { icon = option },
                        active = icon == option,
                        radius = 9.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TvIcon(option, if (icon == option) palette().accent else palette().muted, Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("Group color", color = palette().muted, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(GROUP_COLORS) { option ->
                    TvSurface(
                        Modifier.size(30.dp),
                        onClick = { color = option },
                        active = color == option,
                        background = option.groupColor(),
                        radius = 15.dp,
                    ) {}
                }
            }
            if (!builtIn) {
                Spacer(Modifier.height(10.dp))
                Text("${selectedKeys.size} selected favorites", color = palette().accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            TvButton(
                "Save group",
                {
                    if (builtInId != null) viewModel.saveBuiltInFavoriteGroupAppearance(builtInId, name, icon, color)
                    else viewModel.saveFavoriteGroup(groupId, name, icon, color, selectedKeys)
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TvButton("Cancel", viewModel::back, Modifier.fillMaxWidth(), TvButtonStyle.Secondary)
            if (existing != null) {
                Spacer(Modifier.height(6.dp))
                TvButton("Delete group", { viewModel.deleteFavoriteGroup(existing.id) }, Modifier.fillMaxWidth(), TvButtonStyle.Danger)
            }
        }

        if (builtIn) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Choose favorites", color = palette().text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Press OK to add or remove an item from this group.", color = palette().muted, fontSize = 9.sp)
                Spacer(Modifier.height(7.dp))
                FilterRow(filter) { filter = it }
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    EmptyState("No favorites match this filter", "Add favorites from the catalog first.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { itemKey(it) }) { item ->
                            val selected = itemKey(item) in selectedKeys
                            SelectableFavoriteCard(
                                item = item,
                                selected = selected,
                                onClick = {
                                    selectedKeys = if (selected) selectedKeys - itemKey(item) else selectedKeys + itemKey(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: ContentType?, onSelect: (ContentType?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TvChip("All", selected == null, { onSelect(null) })
        TvChip("Live TV", selected == ContentType.LIVE, { onSelect(ContentType.LIVE) })
        TvChip("Movies", selected == ContentType.MOVIE, { onSelect(ContentType.MOVIE) })
        TvChip("Series", selected == ContentType.SERIES, { onSelect(ContentType.SERIES) })
    }
}

@Composable
private fun FavoriteMixedCard(
    item: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 82.dp,
    onLongClick: (() -> Unit)? = null,
    moving: Boolean = false,
) {
    val colors = palette()
    TvSurface(
        modifier = modifier.height(cardHeight),
        onClick = onClick,
        onLongClick = onLongClick,
        longClickLabel = "Reorder favorite",
        active = moving,
        background = if (moving) colors.accent.copy(alpha = 0.20f) else null,
    ) {
        Row(Modifier.fillMaxSize().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(item, Modifier.width(48.dp).height((cardHeight - 18.dp).coerceAtMost(58.dp)), live = item.type == ContentType.LIVE)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.name,
                    color = colors.text,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (moving) "Moving • use D-pad" else item.type.title(),
                    color = if (moving) colors.accent else colors.muted,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = if (moving) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (favorite) TvIcon(if (moving) "plus" else "heart", if (moving) colors.accent else colors.danger, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SelectableFavoriteCard(item: CatalogItem, selected: Boolean, onClick: () -> Unit) {
    TvSurface(Modifier.fillMaxWidth().height(78.dp), onClick = onClick, active = selected) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(item, Modifier.width(44.dp).height(52.dp), live = item.type == ContentType.LIVE)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, color = palette().text, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.type.title(), color = palette().muted, fontSize = 7.sp, lineHeight = 8.sp)
            }
            Text(if (selected) "✓" else "+", color = if (selected) palette().accent else palette().muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FavoriteGroupCard(
    group: DisplayGroup,
    selected: Boolean,
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val selectionModifier = if (selected || moving) {
        Modifier.border(
            width = if (moving) 3.dp else 2.dp,
            color = if (moving) Color.White else colors.accent,
            shape = RoundedCornerShape(12.dp),
        )
    } else {
        Modifier
    }
    TvSurface(
        modifier = modifier.height(84.dp).then(selectionModifier),
        onClick = onClick,
        onLongClick = onLongClick,
        longClickLabel = "Reorder favorite group",
        active = selected || moving,
        background = if (moving) colors.accent.copy(alpha = 0.26f) else group.color.groupColor().copy(alpha = if (selected) 0.90f else 0.72f),
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvIcon(group.icon, Color.White, Modifier.size(23.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    group.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (moving) "Moving • ◀ ▶" else itemCountLabel(group.items.size),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = if (moving) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private data class DisplayGroup(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val items: List<CatalogItem>,
)

private fun orderedGroups(state: AppUiState): List<DisplayGroup> {
    val groups = systemGroups(state) + state.favoriteGroups.map { it.toDisplayGroup(state) }
    val byId = groups.associateBy { it.id }
    val order = (state.favoriteGroupOrder + groups.map { it.id }).distinct()
    return order.mapNotNull(byId::get)
}

private fun orderedGroupItems(state: AppUiState, group: DisplayGroup): List<CatalogItem> {
    val order = state.favoriteItemOrders[group.id].orEmpty()
    if (order.isEmpty()) return group.items
    val byKey = group.items.associateBy(::itemKey)
    return order.mapNotNull(byKey::get) + group.items.filterNot { itemKey(it) in order }
}

private fun systemGroups(state: AppUiState): List<DisplayGroup> = listOf(
    DisplayGroup("all", "All Favorites", "heart", "purple", state.favorites),
    DisplayGroup("live", "Live TV", "tv", "blue", state.favorites.filter { it.type == ContentType.LIVE }),
    DisplayGroup("movie", "Movies", "popcorn", "teal", state.favorites.filter { it.type == ContentType.MOVIE }),
    DisplayGroup("series", "Series", "play", "orange", state.favorites.filter { it.type == ContentType.SERIES }),
)

private fun FavoriteGroup.toDisplayGroup(state: AppUiState): DisplayGroup = DisplayGroup(
    id = id,
    name = name,
    icon = icon,
    color = color,
    items = state.favorites.filter { itemKey(it) in itemKeys },
)

private fun filterItems(items: List<CatalogItem>, type: ContentType?): List<CatalogItem> =
    if (type == null) items else items.filter { it.type == type }

private fun itemCountLabel(count: Int): String = "$count ${if (count == 1) "item" else "items"}"

private fun moveEntry(order: List<String>, key: String, delta: Int): List<String> {
    val from = order.indexOf(key)
    if (from < 0 || order.isEmpty()) return order
    val to = (from + delta).coerceIn(0, order.lastIndex)
    if (to == from) return order
    return order.toMutableList().also {
        val value = it.removeAt(from)
        it.add(to, value)
    }
}

private fun String.groupColor(): Color = when (this) {
    "blue" -> Color(0xFF2E78B7)
    "teal" -> Color(0xFF2E8B78)
    "orange" -> Color(0xFFB76524)
    "rose" -> Color(0xFFA64764)
    "lime" -> Color(0xFF708B2C)
    "slate" -> Color(0xFF475569)
    else -> Color(0xFF7244C7)
}

private const val FAVORITE_GRID_COLUMNS = 4
private val GROUP_ICONS = listOf("heart", "tv", "popcorn", "play", "smile", "trophy", "folder", "star")
private val GROUP_COLORS = listOf("purple", "blue", "teal", "orange", "rose", "lime", "slate")
