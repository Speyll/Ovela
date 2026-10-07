package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    item: CatalogItem,
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
) {
    if (item.type != ContentType.SERIES) {
        // v0.7 browse plays Live TV/Movies directly; this remains a safe
        // compatibility fallback for stale navigation state.
        LaunchedEffect(item.id) { viewModel.play(item) }
        return
    }

    val colors = palette()
    val episodes = state.detailEpisodes
    val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
    val selectedSeason = state.detailSelectedSeason ?: seasons.firstOrNull()
    val visibleEpisodes = if (selectedSeason == null) episodes else episodes.filter { it.season == selectedSeason }
    val episodeRows = visibleEpisodes.chunked(2)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val heroFocus = contentEntryFocus
    val firstSeasonFocus = remember(item.id) { FocusRequester() }
    val fallbackEpisodeFocus = remember(item.id, selectedSeason) { FocusRequester() }
    val episodeFocusers = remember(item.id, selectedSeason, visibleEpisodes.map(::itemKey)) {
        List(visibleEpisodes.size) { FocusRequester() }
    }
    val firstEpisodeFocus = episodeFocusers.firstOrNull() ?: fallbackEpisodeFocus
    val detailScope = detailFocusScope(item.id, selectedSeason)
    val returnFocus = state.focusRequest?.takeIf { it.scope == detailScope }

    // Fresh detail entry starts on the hero. When returning from playback the
    // screen is recreated with a FocusRequest, so this one-shot effect leaves
    // focus restoration to the episode effect below.
    LaunchedEffect(item.id) {
        if (state.focusRequest == null) {
            delay(24L)
            runCatching { heroFocus.requestFocus() }
        }
    }

    LaunchedEffect(returnFocus, visibleEpisodes.map(::itemKey)) {
        if (returnFocus != null && visibleEpisodes.isNotEmpty()) {
            val target = when {
                returnFocus.firstItem -> 0
                returnFocus.itemKey != null -> visibleEpisodes.indexOfFirst { itemKey(it) == returnFocus.itemKey }.coerceAtLeast(0)
                else -> 0
            }.coerceIn(visibleEpisodes.indices)
            val targetRow = target / 2
            listState.scrollToItem(targetRow + if (seasons.isNotEmpty()) 2 else 1)
            repeat(5) { attempt ->
                delay(if (attempt == 0) 32L else 24L)
                if (runCatching { episodeFocusers[target].requestFocus() }.getOrDefault(false)) {
                    viewModel.consumeFocusRequest(detailScope)
                    return@LaunchedEffect
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hero:${item.id}") {
            SeriesDetailHero(
                item = item,
                favorite = viewModel.isFavorite(item),
                primaryFocus = heroFocus,
                downFocus = firstSeasonFocus.takeIf { seasons.isNotEmpty() } ?: firstEpisodeFocus.takeIf { visibleEpisodes.isNotEmpty() },
                onFavorite = { viewModel.toggleFavorite(item) },
                onBack = viewModel::back,
            )
        }

        if (seasons.isNotEmpty()) {
            item(key = "seasons:${item.id}") {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Episodes", "${episodes.size} episodes", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(5.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp),
                    ) {
                        items(seasons, key = { it }) { season ->
                            SeasonTab(
                                season = season,
                                selected = selectedSeason == season,
                                onClick = { viewModel.selectDetailSeason(item.id, season) },
                                focusRequester = if (season == seasons.first()) firstSeasonFocus else null,
                                upFocus = heroFocus,
                                downFocus = firstEpisodeFocus.takeIf { visibleEpisodes.isNotEmpty() },
                            )
                        }
                    }
                }
            }
        }

        if (state.loading && visibleEpisodes.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("Loading episodes…", color = colors.muted, fontSize = 12.sp) } }
        } else if (visibleEpisodes.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().height(150.dp)) { EmptyState("No episodes", "This provider did not return episode data for this series.") } }
        } else {
            items(episodeRows.size, key = { row -> "episode-row:$selectedSeason:$row" }) { rowIndex ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    episodeRows[rowIndex].forEachIndexed { column, episode ->
                        val absoluteIndex = rowIndex * 2 + column
                        EpisodeCardV07(
                            episode = episode,
                            state = state,
                            viewModel = viewModel,
                            focusRequester = episodeFocusers.getOrNull(absoluteIndex),
                            upFocus = if (rowIndex == 0) firstSeasonFocus.takeIf { seasons.isNotEmpty() } ?: heroFocus else null,
                            onFocused = {
                                viewModel.rememberFocusedItem(
                                    detailArea(item.id),
                                    detailFocusScope(item.id, selectedSeason),
                                    itemKey(episode),
                                )
                                // Keep complete episode rows visible in both directions.
                                scrollScope.launch {
                                    listState.animateScrollToItem(rowIndex + if (seasons.isNotEmpty()) 2 else 1)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (episodeRows[rowIndex].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailHero(
    item: CatalogItem,
    favorite: Boolean,
    primaryFocus: FocusRequester,
    downFocus: FocusRequester?,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = palette()
    Box(Modifier.fillMaxWidth().height(218.dp)) {
        HeroBackdrop(item.backdropUrl, Modifier.fillMaxSize(), imageAlpha = .48f)
        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(item, Modifier.width(92.dp).height(138.dp), live = false)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = colors.text, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                DetailMetadata(item)
                Spacer(Modifier.height(5.dp))
                Text(item.plot ?: "No description supplied by this provider.", color = colors.text.copy(alpha = .82f), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.82f))
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    TvButton(
                        label = if (favorite) "♥ Favorited" else "♡ Favorite",
                        onClick = onFavorite,
                        modifier = Modifier
                            .width(118.dp)
                            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier),
                        style = TvButtonStyle.Secondary,
                        focusRequester = primaryFocus,
                    )
                    TvButton("Back", onBack, Modifier.width(72.dp), TvButtonStyle.Secondary)
                }
            }
        }
    }
}

@Composable
private fun DetailMetadata(item: CatalogItem) {
    val colors = palette()
    val values = buildList {
        item.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }?.let(::add)
        item.rating?.let { add("★ ${"%.1f".format(it)}") }
        item.genre?.split(',', '/', limit = 2)?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        formatRuntime(item.runtimeMinutes).takeIf { it.isNotBlank() }?.let(::add)
    }
    Text(values.joinToString("   •   "), color = colors.text.copy(alpha = .80f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun SeasonTab(
    season: Int,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    val requester = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Column(
        Modifier
            .then(requester)
            .then(
                Modifier.focusProperties {
                    if (upFocus != null) up = upFocus
                    if (downFocus != null) down = downFocus
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Season $season", color = if (focused) colors.accent else if (selected) colors.text else colors.muted, fontSize = 11.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(if (selected) 46.dp else 0.dp).height(2.dp).background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(99.dp)))
    }
}

@Composable
private fun EpisodeCardV07(
    episode: CatalogItem,
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocus: FocusRequester? = null,
    onFocused: () -> Unit,
) {
    val colors = palette()
    val progress = state.progress[itemKey(episode)]
    var focused by remember(episode.id) { mutableStateOf(false) }
    val requester = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        modifier
            .height(94.dp)
            .then(requester)
            .then(if (upFocus != null) Modifier.focusProperties { up = upFocus } else Modifier)
            // Keep the episode surface visually stable. Focus is communicated by
            // the same thin accent edge used elsewhere in v0.7, not by darkening
            // the entire card behind the episode.
            .background(colors.panel.copy(alpha = .24f), RoundedCornerShape(11.dp))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) colors.accent.copy(alpha = .95f) else Color.White.copy(alpha = .045f),
                shape = RoundedCornerShape(11.dp),
            )
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) { viewModel.play(episode, state.detailEpisodes); true } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { viewModel.play(episode, state.detailEpisodes) }
            .focusable()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(episode, Modifier.width(118.dp).fillMaxHeight(), live = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("S${episode.season ?: 0}  E${episode.episode ?: 0}", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(episode.name, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.plot?.let { Text(it, color = colors.muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            progress?.let { Text("Resume ${formatDuration(it.positionMs)}", color = colors.accent, fontSize = 9.sp) }
        }
        Text("▶", color = colors.accent, fontSize = 12.sp)
    }
}
