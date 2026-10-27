package io.github.speyll.ovela.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.speyll.ovela.data.itemKey
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.PlaybackProgress
import io.github.speyll.ovela.model.hiddenCategoryIds
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.palette
import kotlinx.coroutines.delay

private data class HomeRecentEntry(
    val item: CatalogItem,
    val title: String,
    val subtitle: String?,
    val progress: PlaybackProgress?,
)

@Composable
fun HomeScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    topContentPadding: Dp = 0.dp,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val recent = state.recent
        .filterNot { isCatalogItemHidden(it, state.settings) }
        .distinctBy(::itemKey)
    val onDemandRecent = recent.filter { it.type == ContentType.MOVIE || it.type == ContentType.SERIES || it.type == ContentType.EPISODE }
        .map { item ->
            val progress = state.progress[itemKey(item)]
            val parentSeries = item.type == ContentType.EPISODE && !item.parentSeriesId.isNullOrBlank()
            HomeRecentEntry(
                item = item,
                title = if (parentSeries) item.parentSeriesName ?: item.name else item.name,
                subtitle = when {
                    item.type == ContentType.EPISODE && item.season != null && item.episode != null ->
                        "S${item.season}  ·  E${item.episode}"
                    item.type == ContentType.SERIES -> "Series"
                    else -> "Movie"
                },
                progress = progress,
            )
        }
    val inProgress = onDemandRecent.filter {
        val progress = it.progress
        progress != null && progress.positionMs > 0L &&
            (progress.durationMs <= 0L || progress.positionMs < progress.durationMs * .95)
    }
    val onDemandRail = inProgress.ifEmpty { onDemandRecent }.take(10)
    val onDemandTitle = if (inProgress.isNotEmpty()) "Continue watching" else "Recently watched"
    val liveRecent = recent.filter { it.type == ContentType.LIVE }.take(8)
    val favorites = state.favorites
        .filterNot { isCatalogItemHidden(it, state.settings) }
        .distinctBy(::itemKey)
        .take(10)
    val firstFocusKey = when {
        onDemandRail.isNotEmpty() -> "recent:${itemKey(onDemandRail.first().item)}"
        liveRecent.isNotEmpty() -> "live:${itemKey(liveRecent.first())}"
        favorites.isNotEmpty() -> "favorite:${itemKey(favorites.first())}"
        else -> ""
    }
    val focusers = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()

    fun requesterFor(key: String): FocusRequester =
        if (key == firstFocusKey) contentEntryFocus else focusers.getOrPut(key) { FocusRequester() }

    LaunchedEffect(state.focusRequest, onDemandRail.map { itemKey(it.item) }, liveRecent.map(::itemKey), favorites.map(::itemKey)) {
        val request = state.focusRequest ?: return@LaunchedEffect
        val target = when {
            request.scope.startsWith("home:") -> {
                val requestedKey = request.itemKey
                when {
                    requestedKey != null && onDemandRail.any { itemKey(it.item) == requestedKey } -> "recent:$requestedKey"
                    requestedKey != null && liveRecent.any { itemKey(it) == requestedKey } -> "live:$requestedKey"
                    requestedKey != null && favorites.any { itemKey(it) == requestedKey } -> "favorite:$requestedKey"
                    request.scope == homeFocusScope(ContentType.LIVE) && liveRecent.isNotEmpty() -> "live:${itemKey(liveRecent.first())}"
                    onDemandRail.isNotEmpty() -> "recent:${itemKey(onDemandRail.first().item)}"
                    favorites.isNotEmpty() -> "favorite:${itemKey(favorites.first())}"
                    liveRecent.isNotEmpty() -> "live:${itemKey(liveRecent.first())}"
                    else -> ""
                }
            }
            else -> return@LaunchedEffect
        }
        if (target.isBlank()) return@LaunchedEffect
        delay(24L)
        val requester = if (target == firstFocusKey) contentEntryFocus else focusers[target]
        runCatching { (requester ?: contentEntryFocus).requestFocus() }
        viewModel.consumeFocusRequest(request.scope)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (touchUi) 14.dp else 32.dp,
            end = if (touchUi) 14.dp else 32.dp,
            top = topContentPadding + if (touchUi) 6.dp else 12.dp,
            bottom = if (touchUi) 20.dp else 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (touchUi) 14.dp else 18.dp),
    ) {
        if (onDemandRail.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(onDemandTitle, if (onDemandRail.size == 1) "1 title" else "${onDemandRail.size} titles")
            }
            item(key = "recent-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(if (touchUi) 10.dp else 14.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(onDemandRail, key = { "recent:${itemKey(it.item)}" }) { entry ->
                        val key = "recent:${itemKey(entry.item)}"
                        HomeRecentCard(
                            entry = entry,
                            favorite = viewModel.isFavorite(entry.item),
                            width = if (touchUi) 108.dp else 132.dp,
                            focusRequester = requesterFor(key),
                            onFocused = {
                                viewModel.rememberFocusedItem(AREA_HOME, homeFocusScope(entry.item.type), itemKey(entry.item))
                            },
                            onClick = {
                                if (entry.item.type == ContentType.EPISODE) {
                                    viewModel.play(entry.item)
                                } else {
                                    viewModel.openDetail(entry.item)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favorites-header") {
                SectionHeader("Favourites", "${favorites.size}")
            }
            item(key = "favorites-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(if (touchUi) 10.dp else 14.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(favorites, key = { "favorite:${itemKey(it)}" }) { item ->
                        val key = "favorite:${itemKey(item)}"
                        Column(Modifier.width(if (touchUi) 108.dp else 132.dp)) {
                            PosterArtCard(
                                item = item,
                                favorite = true,
                                onClick = {
                                    if (item.type == ContentType.LIVE) viewModel.play(item) else viewModel.openDetail(item)
                                },
                                focusRequester = requesterFor(key),
                                onFocused = {
                                    viewModel.rememberFocusedItem(AREA_HOME, homeFocusScope(item.type), itemKey(item))
                                },
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                item.name,
                                color = colors.text.copy(alpha = .88f),
                                fontSize = if (touchUi) 12.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (liveRecent.isNotEmpty()) {
            item(key = "live-header") {
                SectionHeader("Recent Live TV", "${liveRecent.size}")
            }
            item(key = "live-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(if (touchUi) 8.dp else 10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(liveRecent, key = { "live:${itemKey(it)}" }) { channel ->
                        val key = "live:${itemKey(channel)}"
                        HomeLiveCard(
                            channel = channel,
                            width = if (touchUi) 176.dp else 208.dp,
                            focusRequester = requesterFor(key),
                            onFocused = {
                                viewModel.rememberFocusedItem(AREA_HOME, homeFocusScope(ContentType.LIVE), itemKey(channel))
                            },
                            onClick = { viewModel.play(channel, liveRecent) },
                        )
                    }
                }
            }
        }

        if (onDemandRail.isEmpty() && liveRecent.isEmpty() && favorites.isEmpty()) {
            item(key = "home-empty") {
                Text(
                    "Start with Live TV, Movies or Series. Your recent channels and in-progress titles will appear here.",
                    color = colors.muted,
                    fontSize = if (touchUi) 12.sp else 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(.72f).padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeRecentCard(
    entry: HomeRecentEntry,
    favorite: Boolean,
    width: Dp,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val progress = entry.progress
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth()) {
            PosterArtCard(
                item = entry.item,
                favorite = favorite,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = focusRequester,
                onFocused = onFocused,
            )
            if (progress != null && progress.durationMs > 0L) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colors.background.copy(alpha = .72f), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(colors.accent, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            entry.title,
            color = colors.text,
            fontSize = if (touchUi) 12.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                progress != null && progress.positionMs > 0L -> "Resume ${formatDuration(progress.positionMs)}"
                entry.subtitle != null -> entry.subtitle
                else -> "Play"
            },
            color = if (progress != null) colors.accent else colors.muted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeLiveCard(
    channel: CatalogItem,
    width: Dp,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    TvSurface(
        modifier = Modifier.width(width).height(if (touchUi) 70.dp else 74.dp),
        onClick = onClick,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(channel, Modifier.width(56.dp).height(42.dp), live = true, transparentLive = true)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = colors.text,
                    fontSize = if (touchUi) 13.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    channel.channelNumber?.let { "Channel $it" } ?: "Live TV",
                    color = colors.muted,
                    fontSize = 10.sp,
                )
            }
        }
    }

}