package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.R
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOME_HERO_FOCUS_SCOPE = "home:hero"

@Composable
fun HomeScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    topContentPadding: Dp = 0.dp,
) {
    val recent = state.recent.filterNot { isCatalogItemHidden(it, state.settings) }.distinctBy(::itemKey)
    val rawFeatured = recent.firstOrNull()
    val featured = rawFeatured?.let { raw ->
        val rich = state.richItems[itemKey(raw)] ?: raw
        if (rich.type == ContentType.EPISODE && !rich.parentSeriesId.isNullOrBlank() && !rich.parentSeriesName.isNullOrBlank()) {
            CatalogItem(
                id = rich.parentSeriesId,
                type = ContentType.SERIES,
                name = rich.parentSeriesName,
                imageUrl = rich.parentSeriesImageUrl ?: rich.imageUrl,
                coverUrl = rich.parentSeriesImageUrl ?: rich.coverUrl ?: rich.imageUrl,
                backdropUrl = rich.parentSeriesBackdropUrl,
                plot = rich.plot,
            )
        } else rich
    }
    val livePreview = recent.filter { it.type == ContentType.LIVE }.take(12)
    val moviePreview = recent.filter { it.type == ContentType.MOVIE }.take(12)
    val seriesPreview = recent
        .map { item ->
            if (item.type == ContentType.EPISODE && !item.parentSeriesId.isNullOrBlank() && !item.parentSeriesName.isNullOrBlank()) {
                CatalogItem(
                    id = item.parentSeriesId,
                    type = ContentType.SERIES,
                    name = item.parentSeriesName,
                    imageUrl = item.parentSeriesImageUrl ?: item.imageUrl,
                    coverUrl = item.parentSeriesImageUrl ?: item.coverUrl ?: item.imageUrl,
                    backdropUrl = item.parentSeriesBackdropUrl,
                )
            } else item
        }
        .filter { it.type == ContentType.SERIES }
        .distinctBy(::itemKey)
        .take(12)

    val rails = remember(livePreview, moviePreview, seriesPreview) {
        buildList {
            if (livePreview.isNotEmpty()) add(HomeRailSpec(ContentType.LIVE, "Continue watching Live TV", livePreview))
            if (moviePreview.isNotEmpty()) add(HomeRailSpec(ContentType.MOVIE, "Continue watching Movies", moviePreview))
            if (seriesPreview.isNotEmpty()) add(HomeRailSpec(ContentType.SERIES, "Continue watching Series", seriesPreview))
        }
    }
    val scrollState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var lastFocusedRailIndex by remember { mutableStateOf(-1) }
    val heroPlayFocus = contentEntryFocus
    val firstRailFocus = remember(rails.firstOrNull()?.scope) { FocusRequester() }
    var paintedHeroBackdrop by remember(rawFeatured?.id) { mutableStateOf(featured?.backdropUrl) }

    LaunchedEffect(rawFeatured?.id) {
        if (rawFeatured?.type == ContentType.MOVIE) viewModel.requestMovieDetails(rawFeatured)
    }
    LaunchedEffect(featured?.backdropUrl) {
        if (!featured?.backdropUrl.isNullOrBlank()) paintedHeroBackdrop = featured?.backdropUrl
    }

    val returnFocus = state.focusRequest
    LaunchedEffect(returnFocus?.scope, rails.map { it.scope }) {
        val railIndex = rails.indexOfFirst { it.scope == returnFocus?.scope }
        if (railIndex >= 0) {
            scrollState.scrollToItem(railIndex)
            delay(24L)
        }
    }
    LaunchedEffect(returnFocus, featured?.id) {
        if (returnFocus?.scope == HOME_HERO_FOCUS_SCOPE) {
            repeat(5) { attempt ->
                delay(if (attempt == 0) 32L else 24L)
                if (runCatching { heroPlayFocus.requestFocus() }.getOrDefault(false)) {
                    viewModel.consumeFocusRequest(HOME_HERO_FOCUS_SCOPE)
                    return@LaunchedEffect
                }
            }
        }
    }

    val homeBackdrop = if (featured?.type == ContentType.LIVE) null else paintedHeroBackdrop ?: featured?.backdropUrl
    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            url = homeBackdrop,
            fallbackDrawable = R.drawable.home_live_backdrop,
            modifier = Modifier.fillMaxSize(),
            imageAlpha = .48f,
            aggressiveBottomVignette = true,
        )
        Column(Modifier.fillMaxSize().padding(top = topContentPadding)) {
            HomeHero(
                featured = featured,
                actionItem = rawFeatured,
                viewModel = viewModel,
                primaryFocus = heroPlayFocus,
                downFocus = firstRailFocus.takeIf { rails.isNotEmpty() },
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(rails, key = { _, rail -> rail.scope }) { rowIndex, rail ->
                    HomeContentRail(
                        spec = rail,
                        state = state,
                        viewModel = viewModel,
                        firstItemFocus = firstRailFocus.takeIf { rowIndex == 0 },
                        focusRequest = returnFocus?.takeIf { it.scope == rail.scope },
                        onRowFocused = {
                            // The hero remains fixed. Only move the lower list when focus
                            // enters a different rail; horizontal poster movement must not
                            // repeatedly re-scroll the page and make it jump vertically.
                            if (rowIndex >= 0 && rowIndex != lastFocusedRailIndex) {
                                lastFocusedRailIndex = rowIndex
                                scrollScope.launch { scrollState.animateScrollToItem(rowIndex) }
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class HomeRailSpec(
    val type: ContentType,
    val title: String,
    val items: List<CatalogItem>,
) {
    val scope: String = homeFocusScope(type)
}

@Composable
private fun HomeHero(
    featured: CatalogItem?,
    actionItem: CatalogItem?,
    viewModel: AppViewModel,
    primaryFocus: FocusRequester,
    downFocus: FocusRequester?,
) {
    val colors = palette()
    val isLive = featured?.type == ContentType.LIVE
    val poster = featured?.coverUrl ?: featured?.imageUrl
    val title = featured?.name ?: "Your TV, movies, and series in one place"
    val description = featured?.plot ?: if (featured != null) {
        if (isLive) "Resume your last live channel or jump back into your recent viewing." else "Jump back in where you left off."
    } else {
        "Browse Live TV, movies, and series from your connected provider."
    }

    Box(Modifier.fillMaxWidth().height(194.dp)) {
        Row(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (featured != null) {
                Box(Modifier.width(if (isLive) 82.dp else 92.dp).height(if (isLive) 92.dp else 138.dp), contentAlignment = Alignment.Center) {
                    if (isLive) Artwork(featured, Modifier.size(80.dp), live = true, transparentLive = true)
                    else Artwork(featured.copy(imageUrl = poster), Modifier.fillMaxSize(), live = false)
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("LAST PLAYED", color = colors.accent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(3.dp))
                Text(title, color = colors.text, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                HeroMetadata(featured)
                Spacer(Modifier.height(5.dp))
                Text(description, color = colors.text.copy(alpha = .82f), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.68f))
                Spacer(Modifier.height(8.dp))
                if (featured != null) {
                    TvButton(
                        label = when (featured.type) {
                            ContentType.LIVE -> "Watch now"
                            ContentType.SERIES -> "View episodes"
                            else -> "Resume"
                        },
                        leading = "▶",
                        onClick = { actionItem?.let(viewModel::activate) },
                        modifier = Modifier
                            .width(122.dp)
                            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier),
                        focusRequester = primaryFocus,
                        onFocused = {
                            viewModel.rememberFocusedItem(
                                AREA_HOME,
                                HOME_HERO_FOCUS_SCOPE,
                                itemKey(featured),
                            )
                        },
                    )
                } else {
                    TvButton(
                        "Browse Live TV",
                        { viewModel.openCatalog(ContentType.LIVE) },
                        Modifier
                            .width(132.dp)
                            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier),
                        focusRequester = primaryFocus,
                        onFocused = {
                            viewModel.rememberFocusedItem(
                                AREA_HOME,
                                HOME_HERO_FOCUS_SCOPE,
                                "home:hero:browse",
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetadata(item: CatalogItem?) {
    if (item == null) return
    val colors = palette()
    val parts = buildList {
        item.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }?.let(::add)
        item.rating?.let { add("★ ${"%.1f".format(it)}") }
        item.genre?.split(',', '/', limit = 2)?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        formatRuntime(item.runtimeMinutes).takeIf { it.isNotBlank() }?.let(::add)
        if (item.type == ContentType.LIVE) add("LIVE")
    }
    Text(parts.joinToString("   •   "), color = colors.text.copy(alpha = .78f), fontSize = 8.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun HomeContentRail(
    spec: HomeRailSpec,
    state: AppUiState,
    viewModel: AppViewModel,
    firstItemFocus: FocusRequester?,
    focusRequest: FocusRequest?,
    onRowFocused: () -> Unit,
) {
    val rowState = rememberLazyListState()
    val itemFocusers = remember(spec.scope, spec.items.map(::itemKey)) {
        List(spec.items.size) { index ->
            if (index == 0 && firstItemFocus != null) firstItemFocus else FocusRequester()
        }
    }
    val targetIndex = remember(spec.items, focusRequest) {
        when {
            focusRequest == null || spec.items.isEmpty() -> -1
            focusRequest.firstItem -> 0
            focusRequest.itemKey != null -> spec.items.indexOfFirst { itemKey(it) == focusRequest.itemKey }.coerceAtLeast(0)
            else -> 0
        }
    }
    LaunchedEffect(focusRequest, targetIndex) {
        if (focusRequest != null && targetIndex in spec.items.indices) {
            rowState.scrollToItem(targetIndex)
            repeat(5) { attempt ->
                delay(if (attempt == 0) 32L else 24L)
                if (runCatching { itemFocusers[targetIndex].requestFocus() }.getOrDefault(false)) {
                    viewModel.consumeFocusRequest(spec.scope)
                    return@LaunchedEffect
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 22.dp),
    ) {
        SectionHeader(spec.title)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(if (spec.type == ContentType.LIVE) 10.dp else 12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(spec.items, key = { _, item -> itemKey(item) }) { index, item ->
                val favorite = state.favorites.any { it.type == item.type && it.id == item.id }
                val onFocused = {
                    onRowFocused()
                    viewModel.rememberFocusedItem(AREA_HOME, spec.scope, itemKey(item))
                    if (item.type == ContentType.MOVIE) viewModel.requestMovieDetails(item)
                }
                if (spec.type == ContentType.LIVE) {
                    Box(Modifier.width(104.dp).height(70.dp)) {
                        TransparentLiveLogoCard(
                            item = item,
                            favorite = favorite,
                            onClick = { viewModel.play(item) },
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = itemFocusers[index],
                            onFocused = onFocused,
                        )
                    }
                } else {
                    Box(Modifier.width(105.dp)) {
                        PosterArtCard(
                            item = state.richItems[itemKey(item)] ?: item,
                            favorite = favorite,
                            onClick = { if (item.type == ContentType.SERIES) viewModel.openDetail(item) else viewModel.play(item) },
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = itemFocusers[index],
                            onFocused = onFocused,
                        )
                    }
                }
            }
        }
    }
}
