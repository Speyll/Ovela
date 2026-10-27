package io.github.speyll.ovela.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.ui.theme.LocalAppTheme
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.LocalTvPalette
import io.github.speyll.ovela.ui.theme.palette
import io.github.speyll.ovela.ui.theme.paletteFor
import io.github.speyll.ovela.ui.theme.OvelaTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun OvelaApp(
    viewModel: AppViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val packageManager = LocalContext.current.packageManager
    val configuration = LocalConfiguration.current
    val hasTelevision = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val touchUi = remember(packageManager, hasTelevision) {
        isTouchFirstUi(
            hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTelevision = hasTelevision,
        )
    }

    CompositionLocalProvider(
        LocalAppTheme provides state.settings.theme,
        LocalTvPalette provides paletteFor(state.settings.theme),
        LocalTouchUi provides touchUi,
    ) {
        MaterialTheme(typography = OvelaTypography) {
            AppBackground {
                if (state.initializing) {
                    StartupScreen()
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
    val searchNavFocus = remember { FocusRequester() }
    var searchWasOpen by remember { mutableStateOf(false) }
    val section = state.screen.section()
    val activeNavFocus = remember(section) { FocusRequester() }
    val catalogScreen = state.screen as? AppScreen.Catalog
    val usesFullCanvasBackdrop = state.screen == AppScreen.Home ||
        (catalogScreen != null && catalogScreen.type != ContentType.LIVE)
    val touchUi = LocalTouchUi.current
    val topNavHeight = if (touchUi) 52.dp else 60.dp

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && state.screen != AppScreen.Home && state.screen != AppScreen.Login) { viewModel.back() }

    // Selecting Live TV / Movies / Series from the top menu always lands on
    // the category list. Home starts on its first content action only when there
    // is no explicit return-focus request waiting to restore a focused item.
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
            // Some destinations genuinely have nothing focusable: a cleared Home
            // with no history, favourites or recents, or a category whose provider
            // returned no items. Requesting a missing target used to leave focus
            // on a disposed node. Fall back to the active top-nav item so the
            // remote always has a visible starting point.
            runCatching { activeNavFocus.requestFocus() }
        }
    }

    // Closing the modal search removes the only focusable node inside it. Put
    // focus back on the search icon that opened it instead of dropping it.
    LaunchedEffect(searchOpen) {
        if (searchWasOpen && !searchOpen) {
            withFrameNanos { }
            runCatching { searchNavFocus.requestFocus() }
        }
        searchWasOpen = searchOpen
    }

    // Phones get their own chrome: a compact header and a bottom tab bar. The
    // television top rail is a pointer/remote affordance and reads as a squeezed
    // TV layout on a handheld, so it is never used on touch-first devices.
    val phoneChrome = touchUi
    // The category manager is a focused management task: on a phone its bottom
    // tab bar only steals height, which is exactly what the short-landscape
    // layout needs for the category list, so it is hidden there.
    val showTabBar = phoneChrome && state.screen != AppScreen.CategoryVisibility
    val headerHeight = if (phoneChrome) 52.dp else topNavHeight
    val tabBarHeight = if (showTabBar) 62.dp else 0.dp

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (usesFullCanvasBackdrop && !phoneChrome) Modifier
                    else Modifier.padding(top = headerHeight, bottom = tabBarHeight),
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                state.error?.let {
                    ErrorBanner(it, viewModel::clearError)
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val screen = state.screen) {
                        AppScreen.Home -> HomeScreen(state, viewModel, contentEntryFocus, if (phoneChrome) 0.dp else topNavHeight)
                        is AppScreen.Catalog -> CatalogScreen(
                            screen.type,
                            state,
                            viewModel,
                            contentEntryFocus,
                            activeNavFocus,
                            if (screen.type == ContentType.LIVE || phoneChrome) 0.dp else topNavHeight,
                        )
                        is AppScreen.Detail -> DetailScreen(screen.item, state, viewModel, contentEntryFocus)
                        AppScreen.Favorites -> StableFavoritesScreen("all", state, viewModel)
                        is AppScreen.FavoriteGroupBrowser -> StableFavoritesScreen(screen.groupId, state, viewModel)
                        AppScreen.FavoriteGroupsManager -> StableFavoriteGroupsManagerScreen(state, viewModel)
                        is AppScreen.FavoriteGroupEditor -> FavoriteGroupEditorScreen(screen.groupId, state, viewModel)
                        AppScreen.Settings -> SettingsScreen(state, viewModel, contentEntryFocus)
                        AppScreen.CategoryVisibility -> CategoryVisibilityScreen(state, viewModel, contentEntryFocus)
                        else -> Unit
                    }
                }
            }
        }
        if (phoneChrome) {
            PhoneHeader(
                section = section,
                showSearch = catalogScreen != null,
                onSearch = { searchOpen = true },
                onSettings = viewModel::openSettings,
                showBack = state.screen != AppScreen.Home,
                onBack = viewModel::back,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (showTabBar) {
                PhoneTabBar(
                    section = section,
                    onHome = viewModel::openHome,
                    onLive = { viewModel.openCatalog(ContentType.LIVE) },
                    onMovies = { viewModel.openCatalog(ContentType.MOVIE) },
                    onSeries = { viewModel.openCatalog(ContentType.SERIES) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else {
            MainTopNav(
                section = section,
                showSearch = catalogScreen != null,
                contentEntryFocus = contentEntryFocus,
                activeNavFocus = activeNavFocus,
                searchNavFocus = searchNavFocus,
                onHome = viewModel::openHome,
                onLive = { viewModel.openCatalog(ContentType.LIVE) },
                onMovies = { viewModel.openCatalog(ContentType.MOVIE) },
                onSeries = { viewModel.openCatalog(ContentType.SERIES) },
                onSearch = { searchOpen = true },
                onSettings = viewModel::openSettings,
                showBack = state.screen != AppScreen.Home,
                onBack = viewModel::back,
            )
        }
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
    searchNavFocus: FocusRequester,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val touchUi = LocalTouchUi.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(if (touchUi) 52.dp else 60.dp)
            .padding(horizontal = if (touchUi) 8.dp else 24.dp),
    ) {
        val compact = touchUi || maxWidth < 700.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                if (showBack) {
                    TopNavBack(onBack)
                    Spacer(Modifier.width(2.dp))
                }
            } else {
                BrandLockup(iconSize = 24.dp, wordmarkSize = 14)
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = if (compact) Modifier.weight(1f) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopNavText(
                    "Home",
                    section == AppSection.Home,
                    contentEntryFocus,
                    activeNavFocus,
                    onHome,
                    compact,
                    if (compact) Modifier.weight(1f) else Modifier.width(68.dp),
                )
                TopNavText(
                    "Live TV",
                    section == AppSection.Live,
                    contentEntryFocus,
                    activeNavFocus,
                    onLive,
                    compact,
                    if (compact) Modifier.weight(1f) else Modifier.width(74.dp),
                )
                TopNavText(
                    "Movies",
                    section == AppSection.Movies,
                    contentEntryFocus,
                    activeNavFocus,
                    onMovies,
                    compact,
                    if (compact) Modifier.weight(1f) else Modifier.width(68.dp),
                )
                TopNavText(
                    "Series",
                    section == AppSection.Series,
                    contentEntryFocus,
                    activeNavFocus,
                    onSeries,
                    compact,
                    if (compact) Modifier.weight(1f) else Modifier.width(68.dp),
                )
            }
            if (!compact) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(2.dp))
            if (showSearch) {
                TopNavIcon("search", contentEntryFocus, onSearch, focusRequester = searchNavFocus)
                Spacer(Modifier.width(if (compact) 0.dp else 6.dp))
            }
            TopNavIcon(
                "settings",
                contentEntryFocus,
                onSettings,
                active = section == AppSection.Settings,
                activeFocusRequester = activeNavFocus,
            )
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                ClockText()
            }
        }
    }
}

@Composable
private fun TopNavBack(onClick: () -> Unit) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Back",
            color = if (focused) colors.accent else colors.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TopNavText(
    label: String,
    active: Boolean,
    contentEntryFocus: FocusRequester,
    activeFocusRequester: FocusRequester,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .then(if (active) Modifier.focusRequester(activeFocusRequester) else Modifier)
            .then(modifier)
            .height(if (compact) 48.dp else 44.dp)
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
            fontSize = if (compact) {
                if (active || focused) 10.5.sp else 9.5.sp
            } else {
                if (active || focused) 11.5.sp else 10.5.sp
            },
            fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Box(
            Modifier
                .width(if (active) if (compact) 18.dp else 26.dp else 0.dp)
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
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .then(
                when {
                    active && activeFocusRequester != null -> Modifier.focusRequester(activeFocusRequester)
                    focusRequester != null -> Modifier.focusRequester(focusRequester)
                    else -> Modifier
                },
            )
            .size(if (LocalTouchUi.current) 40.dp else 34.dp)
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
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .62f))
            // The overlay is modal, but the catalog behind it is still composed
            // and focusable. Without this, Down from the field moved focus to a
            // card under the dimmer. Up/Down are consumed here; Left/Right stay
            // with the text field so the caret still moves.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                event.key == Key.DirectionUp || event.key == Key.DirectionDown
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(0.92f)
                .widthIn(max = 720.dp)
                .background(colors.panelStrong.copy(alpha = .98f), RoundedCornerShape(22.dp))
                .padding(if (LocalTouchUi.current) 18.dp else 24.dp),
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

/**
 * Handheld header: brand on the left, the two actions that are not tabs on the
 * right. Deliberately short so the content keeps the vertical space.
 */
@Composable
private fun PhoneHeader(
    section: AppSection,
    showSearch: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            PhoneIconButton(onClick = onBack) {
                Canvas(Modifier.size(18.dp)) {
                    val stroke = size.minDimension * .095f
                    drawLine(
                        colors.text,
                        Offset(size.width * .62f, size.height * .18f),
                        Offset(size.width * .34f, size.height * .5f),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        colors.text,
                        Offset(size.width * .34f, size.height * .5f),
                        Offset(size.width * .62f, size.height * .82f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        BrandLockup(iconSize = 22.dp, wordmarkSize = 17)
        Spacer(Modifier.weight(1f))
        if (showSearch) {
            PhoneIconButton(onClick = onSearch) {
                TvIcon("search", colors.muted, Modifier.size(20.dp))
            }
        }
        PhoneIconButton(onClick = onSettings) {
            TvIcon(
                "settings",
                if (section == AppSection.Settings) colors.accent else colors.muted,
                Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PhoneIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Bottom tab bar for phones. Icons plus labels, one accent at a time, and a
 * short indicator so the active destination is legible without colour alone.
 */
@Composable
private fun PhoneTabBar(
    section: AppSection,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = .98f)),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .06f)))
        Row(Modifier.fillMaxWidth().height(61.dp)) {
            PhoneTab("Home", "home", section == AppSection.Home, onHome)
            PhoneTab("Live TV", "tv", section == AppSection.Live, onLive)
            PhoneTab("Movies", "popcorn", section == AppSection.Movies, onMovies)
            PhoneTab("Series", "play", section == AppSection.Series, onSeries)
        }
    }
}

@Composable
private fun RowScope.PhoneTab(
    label: String,
    icon: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = palette()
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(20.dp).height(2.dp).background(
            if (active) colors.accent else Color.Transparent,
            RoundedCornerShape(99.dp),
        ))
        Spacer(Modifier.height(7.dp))
        TvIcon(icon, if (active) colors.accent else colors.muted, Modifier.size(21.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (active) colors.text else colors.muted,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

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
    AppScreen.CategoryVisibility -> AppSection.Settings
    is AppScreen.Player -> origin.section()
    AppScreen.Login -> AppSection.Home
}
