package com.xtreamlytv.androidtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.LocalAppTheme
import com.xtreamlytv.androidtv.ui.theme.LocalTvPalette
import com.xtreamlytv.androidtv.ui.theme.palette
import com.xtreamlytv.androidtv.ui.theme.paletteFor
import com.xtreamlytv.androidtv.ui.theme.XtreamlyTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun XtreamlyTvApp(
    viewModel: AppViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var startupAnimationFinished by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalAppTheme provides state.settings.theme,
        LocalTvPalette provides paletteFor(state.settings.theme),
    ) {
        MaterialTheme(typography = XtreamlyTypography) {
            AppBackground {
                if (state.initializing || !startupAnimationFinished) {
                    AppLoadingScreen(onFinished = { startupAnimationFinished = true })
                } else {
                    when (val screen = state.screen) {
                        AppScreen.Login -> LoginScreen(state, viewModel::connect)
                        is AppScreen.Player -> PlayerScreen(
                            request = screen.request,
                            favorite = viewModel.isFavorite(screen.request.item),
                            onBack = viewModel::back,
                            onPrevious = { viewModel.playAdjacent(-1) },
                            onNext = { viewModel.playAdjacent(1) },
                            onToggleFavorite = { viewModel.toggleFavorite(screen.request.item) },
                            onProgress = { position, duration -> viewModel.savePlaybackProgress(screen.request.item, position, duration) },
                            onEnded = { viewModel.clearPlaybackProgress(screen.request.item) },
                        )
                        else -> AppShell(state, viewModel)
                    }
                    if (
                        state.loading &&
                        state.screen != AppScreen.Login &&
                        state.screen !is AppScreen.Player &&
                        state.screen !is AppScreen.Detail &&
                        state.screen !is AppScreen.Catalog
                    ) {
                        LoadingOverlay()
                    }
                }
            }
        }
    }

}

@Composable
private fun AppShell(state: AppUiState, viewModel: AppViewModel) {
    var searchOpen by remember { mutableStateOf(false) }
    val contentEntryFocus = remember(state.screen) { FocusRequester() }
    val section = state.screen.section()
    val activeNavFocus = remember(section) { FocusRequester() }
    val catalogScreen = state.screen as? AppScreen.Catalog
    val usesFullCanvasBackdrop = state.screen == AppScreen.Home ||
        (catalogScreen != null && catalogScreen.type != ContentType.LIVE)
    val topNavHeight = 60.dp

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && state.screen != AppScreen.Home && state.screen != AppScreen.Login) { viewModel.back() }

    // Selecting Live TV / Movies / Series from the top menu always lands on
    // the category wheel. Home starts on the hero action only when there is no
    // explicit return-focus request waiting to restore a previously focused item.
    LaunchedEffect(state.screen) {
        val shouldEnterContent =
            state.focusRequest == null &&
                (state.screen is AppScreen.Catalog ||
                    state.screen == AppScreen.Settings ||
                    state.screen == AppScreen.Home)
        if (shouldEnterContent) {
            // Compose can briefly retain focus on the activating top-nav item while
            // the destination subtree is being attached. Retry across a few frames.
            repeat(5) {
                withFrameNanos { }
                delay(24L)
                if (runCatching { contentEntryFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (usesFullCanvasBackdrop) Modifier else Modifier.padding(top = topNavHeight)),
        ) {
            Column(Modifier.fillMaxSize()) {
                state.error?.let {
                    ErrorBanner(it, viewModel::clearError)
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val screen = state.screen) {
                        AppScreen.Home -> HomeScreen(state, viewModel, contentEntryFocus, topNavHeight)
                        is AppScreen.Catalog -> CatalogScreen(
                            screen.type,
                            state,
                            viewModel,
                            contentEntryFocus,
                            activeNavFocus,
                            if (screen.type == ContentType.LIVE) 0.dp else topNavHeight,
                        )
                        is AppScreen.Detail -> DetailScreen(screen.item, state, viewModel, contentEntryFocus)
                        AppScreen.Favorites -> StableFavoritesScreen("all", state, viewModel)
                        is AppScreen.FavoriteGroupBrowser -> StableFavoritesScreen(screen.groupId, state, viewModel)
                        AppScreen.FavoriteGroupsManager -> StableFavoriteGroupsManagerScreen(state, viewModel)
                        is AppScreen.FavoriteGroupEditor -> FavoriteGroupEditorScreen(screen.groupId, state, viewModel)
                        AppScreen.Settings -> SettingsScreen(state, viewModel, contentEntryFocus)
                        else -> Unit
                    }
                }
            }
        }
        MainTopNav(
            section = section,
            showSearch = catalogScreen != null,
            contentEntryFocus = contentEntryFocus,
            activeNavFocus = activeNavFocus,
            onHome = viewModel::openHome,
            onLive = { viewModel.openCatalog(ContentType.LIVE) },
            onMovies = { viewModel.openCatalog(ContentType.MOVIE) },
            onSeries = { viewModel.openCatalog(ContentType.SERIES) },
            onSearch = { searchOpen = true },
            onSettings = viewModel::openSettings,
        )
    }

    if (searchOpen && catalogScreen != null) {
        ScopedSearchOverlay(
            type = catalogScreen.type,
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            onClose = { searchOpen = false },
        )
    }
}

@Composable
private fun MainTopNav(
    section: AppSection,
    showSearch: Boolean,
    contentEntryFocus: FocusRequester,
    activeNavFocus: FocusRequester,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = palette()
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockup(iconSize = 24.dp, wordmarkSize = 14)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            TopNavText("Home", section == AppSection.Home, contentEntryFocus, activeNavFocus, onHome)
            TopNavText("Live TV", section == AppSection.Live, contentEntryFocus, activeNavFocus, onLive)
            TopNavText("Movies", section == AppSection.Movies, contentEntryFocus, activeNavFocus, onMovies)
            TopNavText("Series", section == AppSection.Series, contentEntryFocus, activeNavFocus, onSeries)
        }
        Spacer(Modifier.weight(1f))
        if (showSearch) {
            TopNavIcon("search", contentEntryFocus, onSearch)
            Spacer(Modifier.width(6.dp))
        }
        TopNavIcon("settings", contentEntryFocus, onSettings, active = section == AppSection.Settings, activeFocusRequester = activeNavFocus)
        Spacer(Modifier.width(8.dp))
        ClockText()
    }
}

@Composable
private fun TopNavText(
    label: String,
    active: Boolean,
    contentEntryFocus: FocusRequester,
    activeFocusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .then(if (active) Modifier.focusRequester(activeFocusRequester) else Modifier)
            .width(if (label == "Live TV") 74.dp else 68.dp)
            .height(44.dp)
            .focusProperties { down = contentEntryFocus }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (active) runCatching { contentEntryFocus.requestFocus() } else onClick() }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (focused) colors.accent else if (active) colors.text else colors.muted,
            fontSize = if (active || focused) 11.5.sp else 10.5.sp,
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(if (active) 26.dp else 0.dp)
                .height(1.5.dp)
                .background(if (active) colors.accent else Color.Transparent, RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun TopNavIcon(
    icon: String,
    contentEntryFocus: FocusRequester,
    onClick: () -> Unit,
    active: Boolean = false,
    activeFocusRequester: FocusRequester? = null,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(if (active && activeFocusRequester != null) Modifier.focusRequester(activeFocusRequester) else Modifier)
            .size(34.dp)
            .focusProperties { down = contentEntryFocus }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (active) runCatching { contentEntryFocus.requestFocus() } else onClick() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        TvIcon(icon, if (focused) colors.accent else if (active) colors.text else colors.muted, Modifier.size(18.dp))
    }
}

@Composable
private fun ScopedSearchOverlay(
    type: ContentType,
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = palette()
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { inputFocus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 34.dp)
                .width(720.dp)
                .background(colors.panelStrong.copy(alpha = .98f), RoundedCornerShape(22.dp))
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search this category", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(type.title(), color = colors.muted, fontSize = 12.sp)
                }
                Text("BACK to close", color = colors.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            SearchField(value, onValueChange, "Search ${type.title().lowercase()}", focusRequester = inputFocus)
        }
    }
}

@Composable
private fun ClockText() {
    val colors = palette()
    var clock by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clock = formatClock()
        }
    }
    Text(clock, color = colors.text.copy(alpha = .86f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
}

private fun formatClock(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

private enum class AppSection { Home, Live, Movies, Series, Favorites, Settings }

private fun AppScreen.section(): AppSection = when (this) {
    AppScreen.Home -> AppSection.Home
    is AppScreen.Catalog -> when (type) {
        ContentType.LIVE -> AppSection.Live
        ContentType.MOVIE -> AppSection.Movies
        ContentType.SERIES, ContentType.EPISODE -> AppSection.Series
    }
    is AppScreen.Detail -> origin.section()
    AppScreen.Favorites, is AppScreen.FavoriteGroupBrowser, AppScreen.FavoriteGroupsManager, is AppScreen.FavoriteGroupEditor -> AppSection.Settings
    AppScreen.Settings -> AppSection.Settings
    is AppScreen.Player -> origin.section()
    AppScreen.Login -> AppSection.Home
}
