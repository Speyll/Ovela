package com.xtreamlytv.androidtv.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.EpgProgram
import com.xtreamlytv.androidtv.ui.theme.palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

private const val MEDIA_HERO_FOCUS_PREFIX = "__media_hero__:"
private const val LIVE_GUIDE_PAGE_SIZE = 5

@Composable
fun CatalogScreen(
    type: ContentType,
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    navReturnFocus: FocusRequester,
    topContentPadding: Dp = 0.dp,
) {
    Box(Modifier.fillMaxSize()) {
        if (type == ContentType.LIVE) {
            LiveGuideScreen(state, viewModel, contentEntryFocus, navReturnFocus)
        } else {
            MediaCatalogScreen(type, state, viewModel, contentEntryFocus, navReturnFocus, topContentPadding)
        }

        if (state.loading) {
            CatalogLoadingPill(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp),
            )
        }
    }
}

@Composable
private fun CatalogLoadingPill(modifier: Modifier = Modifier) {
    val colors = palette()
    Row(
        modifier
            .background(colors.panelStrong.copy(alpha = .94f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = colors.accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(2.dp))
        Text("Loading category…", color = colors.text.copy(alpha = .84f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

private fun v07Categories(type: ContentType, state: AppUiState): List<Category> {
    val favoriteKeys = state.favorites.filter { it.type == type }.map(::itemKey).toSet()
    return buildList {
        add(Category("__favorites__", "♥ Favorites"))
        state.favoriteGroups.filter { group -> group.itemKeys.any { it in favoriteKeys } }.forEach { group ->
            add(Category("__group__:${group.id}", "♡ ${group.name}"))
        }
        addAll(state.categories[type].orEmpty())
    }
}

private data class WheelTextFit(
    val cssSizePx: Float,
    val cssLetterSpacingPx: Float,
    val text: String,
)

private fun fitWebOsWheelText(
    label: String,
    center: Boolean,
    webScale: Float,
    paint: TextPaint,
    regularTypeface: Typeface,
    boldTypeface: Typeface,
): WheelTextFit {
    val sizes = if (center) floatArrayOf(27f, 23f, 20f, 18f) else floatArrayOf(24f, 21f, 19f, 17f)
    val spacings = floatArrayOf(0f, -.15f, -.25f, -.35f)
    val availablePx = 254f * webScale
    paint.typeface = if (center) boldTypeface else regularTypeface

    for (index in sizes.indices) {
        val sizePx = sizes[index] * webScale
        paint.textSize = sizePx
        paint.letterSpacing = if (sizePx > 0f) (spacings[index] * webScale) / sizePx else 0f
        if (paint.measureText(label) <= availablePx + webScale) {
            return WheelTextFit(sizes[index], spacings[index], label)
        }
    }

    val cssSize = sizes.last()
    val cssSpacing = spacings.last()
    val sizePx = cssSize * webScale
    paint.textSize = sizePx
    paint.letterSpacing = if (sizePx > 0f) (cssSpacing * webScale) / sizePx else 0f
    val ellipsis = "…"
    var count = label.length
    while (count > 0 && paint.measureText(label.substring(0, count).trimEnd() + ellipsis) > availablePx) {
        count--
    }
    val shortened = if (count >= label.length) label else label.substring(0, count).trimEnd() + ellipsis
    return WheelTextFit(cssSize, cssSpacing, shortened)
}

private fun drawWebOsWheelText(
    canvas: android.graphics.Canvas,
    paint: TextPaint,
    regularTypeface: Typeface,
    boldTypeface: Typeface,
    label: String,
    x: Float,
    y: Float,
    webScale: Float,
    visualScale: Float,
    center: Boolean,
    focused: Boolean,
    rowAlpha: Float,
    accentColor: Int,
    mutedColor: Int,
    centerColor: Int,
) {
    val fit = fitWebOsWheelText(label, center, webScale, paint, regularTypeface, boldTypeface)
    val sizePx = fit.cssSizePx * webScale * visualScale
    paint.textSize = sizePx
    paint.letterSpacing = if (sizePx > 0f) (fit.cssLetterSpacingPx * webScale * visualScale) / sizePx else 0f
    paint.typeface = if (center) boldTypeface else regularTypeface
    paint.color = if (center && focused) accentColor else if (center) centerColor else mutedColor
    paint.alpha = ((if (center) .96f else rowAlpha) * 255f).toInt().coerceIn(0, 255)

    val metrics = paint.fontMetrics
    val baseline = y - (metrics.ascent + metrics.descent) / 2f

    if (center) {
        if (focused) {
            paint.setShadowLayer(14f * webScale, 0f, 0f, AndroidColor.argb(87, AndroidColor.red(accentColor), AndroidColor.green(accentColor), AndroidColor.blue(accentColor)))
            canvas.drawText(fit.text, x, baseline, paint)
            paint.setShadowLayer(12f * webScale, 0f, 2f * webScale, AndroidColor.argb(158, 0, 0, 0))
            canvas.drawText(fit.text, x, baseline, paint)
        } else {
            paint.setShadowLayer(12f * webScale, 0f, 2f * webScale, AndroidColor.argb(133, 0, 0, 0))
            canvas.drawText(fit.text, x, baseline, paint)
        }
        paint.clearShadowLayer()
    }
    canvas.drawText(fit.text, x, baseline, paint)
}

@Composable
private fun webOsWheelWidth(): Dp {
    val configuration = LocalConfiguration.current
    // Visible webOS label box is 268 CSS px on a 1920px application canvas.
    return (268f * configuration.screenWidthDp / 1920f).dp
}

@Composable
private fun CategoryWheel(
    type: ContentType,
    state: AppUiState,
    viewModel: AppViewModel,
    focusRequester: FocusRequester,
    navReturnFocus: FocusRequester,
    onEnterContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val categories = v07Categories(type, state)
    val activeId = state.selectedCategories[type]?.id
    var wheelIndex by remember(type, categories.map { it.id }, activeId) {
        mutableIntStateOf(categories.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: 0)
    }
    var focused by remember { mutableStateOf(false) }

    fun activateCenter() {
        val category = categories.getOrNull(wheelIndex) ?: return
        if (category.id == activeId) {
            onEnterContent()
        } else {
            // Keep the wheel focused while provider data is loading. The
            // ViewModel publishes an explicit first-item FocusRequest only after
            // the newly selected category has real items, so the destination
            // card is guaranteed to exist before we transfer focus.
            runCatching { focusRequester.requestFocus() }
            if (category.id.startsWith("__")) {
                viewModel.selectVirtualCategory(type, category, deferFirstItemFocus = true)
            } else {
                viewModel.selectCategory(type, category, deferFirstItemFocus = true)
            }
        }
    }

    val visibleLabels = (-3..3).map { offset ->
        categories.getOrNull(modIndex(wheelIndex + offset, categories.size))?.name.orEmpty()
    }

    Box(
        modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || categories.isEmpty()) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        wheelIndex = (wheelIndex - 1 + categories.size) % categories.size
                        true
                    }
                    Key.DirectionDown -> {
                        wheelIndex = (wheelIndex + 1) % categories.size
                        true
                    }
                    Key.DirectionLeft, Key.Back -> {
                        runCatching { navReturnFocus.requestFocus() }
                        true
                    }
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                        activateCenter()
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { activateCenter() }
            .focusable(),
    ) {
        val regularTypeface = remember { Typeface.create("Arial", Typeface.NORMAL) }
        val boldTypeface = remember { Typeface.create("Arial", Typeface.BOLD) }
        val paint = remember {
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isDither = true
            }
        }
        val accentArgb = colors.accent.toArgb()
        val mutedArgb = colors.muted.toArgb()
        val centerArgb = Color(0xFFF2F8FA).toArgb()

        Canvas(Modifier.fillMaxSize()) {
            // The wheel itself is 268 webOS CSS pixels wide. Drawing in native
            // canvas pixels lets us preserve the exact 24/27px typography and
            // 106px row rhythm after the 1920px webOS canvas is scaled to the TV.
            val webScale = (size.width / 268f).coerceAtLeast(.5f)
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val rowSpacing = 106f * webScale
            val visualScales = floatArrayOf(.83f, .90f, .96f, 1f, .96f, .90f, .83f)
            val rowAlphas = floatArrayOf(.14f, .30f, .58f, 1f, .58f, .30f, .14f)

            visibleLabels.take(7).forEachIndexed { index, label ->
                val offset = index - 3
                drawWebOsWheelText(
                    canvas = nativeCanvas,
                    paint = paint,
                    regularTypeface = regularTypeface,
                    boldTypeface = boldTypeface,
                    label = label,
                    x = centerX,
                    y = centerY + offset * rowSpacing,
                    webScale = webScale,
                    visualScale = visualScales[index],
                    center = offset == 0,
                    focused = focused,
                    rowAlpha = rowAlphas[index],
                    accentColor = accentArgb,
                    mutedColor = mutedArgb,
                    centerColor = centerArgb,
                )
            }
        }
    }
}


private fun modIndex(value: Int, count: Int): Int = if (count <= 0) 0 else ((value % count) + count) % count

@Composable
private fun MediaCatalogScreen(
    type: ContentType,
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    navReturnFocus: FocusRequester,
    topContentPadding: Dp,
) {
    val query = state.searchQuery.trim()
    val items = remember(state.items, query) {
        if (query.isBlank()) state.items else state.items.filter { it.name.contains(query, ignoreCase = true) }
    }
    val wheelFocus = contentEntryFocus
    val heroFocus = remember(type) { FocusRequester() }
    val rowState = rememberLazyListState()
    val posterFocusers = remember(type, items.map { it.id }) { List(items.size) { FocusRequester() } }
    val scope = catalogFocusScope(type, state.selectedCategories[type]?.id.orEmpty())
    val focusRequest = state.focusRequest?.takeIf { it.scope == scope }
    var focusedIndex by remember(type, state.selectedCategories[type]?.id) { mutableIntStateOf(0) }
    var heroFromPoster by remember(type) { mutableStateOf(false) }
    var paintedBackdrop by remember(type) { mutableStateOf<String?>(null) }
    val focusedItem = items.getOrNull(focusedIndex)
    val richItem = focusedItem?.let { state.richItems[itemKey(it)] ?: it }

    LaunchedEffect(focusRequest, items.size) {
        if (focusRequest != null && items.isNotEmpty()) {
            val requestedKey = focusRequest.itemKey
            val restoreHero = requestedKey?.startsWith(MEDIA_HERO_FOCUS_PREFIX) == true
            val itemFocusKey = requestedKey?.removePrefix(MEDIA_HERO_FOCUS_PREFIX)
            val target = when {
                focusRequest.firstItem -> 0
                itemFocusKey != null -> items.indexOfFirst { itemKey(it) == itemFocusKey }.coerceAtLeast(0)
                else -> 0
            }.coerceIn(items.indices)
            focusedIndex = target
            rowState.scrollToItem(target)
            repeat(12) { attempt ->
                delay(if (attempt == 0) 48L else 24L)
                val restored = if (restoreHero) {
                    runCatching { heroFocus.requestFocus() }.getOrDefault(false)
                } else {
                    runCatching { posterFocusers[target].requestFocus() }.getOrDefault(false)
                }
                if (restored) {
                    viewModel.consumeFocusRequest(scope)
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(focusedIndex, focusedItem?.id, items.size) {
        if (type == ContentType.MOVIE && focusedItem != null && items.isNotEmpty()) {
            val start = (focusedIndex - 2).coerceAtLeast(0)
            val end = (focusedIndex + 2).coerceAtMost(items.lastIndex)
            val nearby = buildList {
                add(focusedItem)
                for (index in start..end) {
                    if (index != focusedIndex) add(items[index])
                }
            }
            viewModel.warmMovieDetails(nearby)
        }
    }

    LaunchedEffect(focusedItem?.id, richItem?.backdropUrl) {
        val nextBackdrop = richItem?.backdropUrl
        if (!nextBackdrop.isNullOrBlank()) {
            paintedBackdrop = nextBackdrop
        } else {
            val waitingForId = focusedItem?.id
            delay(900L)
            if (focusedItem?.id == waitingForId) {
                paintedBackdrop = null
            }
        }
    }

    LaunchedEffect(state.selectedCategories[type]?.id, items.size) {
        if (focusRequest == null) {
            if (items.isNotEmpty()) {
                focusedIndex = 0
                runCatching { rowState.scrollToItem(0) }
            } else {
                focusedIndex = 0
            }
        }
    }

    fun focusFirstPoster() {
        if (items.isNotEmpty()) {
            focusedIndex = 0
            // This path is used when Center is pressed on the already-active
            // wheel category. The normal category-change path uses focusRequest.
            runCatching { posterFocusers.first().requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Let the media hero art paint all the way to the physical left edge.
        // The wheel remains a transparent overlay, matching the webOS full-bleed feel.
        if (items.isNotEmpty()) {
            HeroBackdrop(
                url = richItem?.backdropUrl ?: paintedBackdrop,
                modifier = Modifier.fillMaxSize(),
                imageAlpha = .48f,
            )
        }
        Row(Modifier.fillMaxSize().padding(top = topContentPadding)) {
            CategoryWheel(
                type = type,
                state = state,
                viewModel = viewModel,
                focusRequester = wheelFocus,
                navReturnFocus = navReturnFocus,
                onEnterContent = ::focusFirstPoster,
                modifier = Modifier.width(webOsWheelWidth()).fillMaxHeight(),
            )
            Spacer(Modifier.width(2.dp))
            if (items.isEmpty()) {
            EmptyState(
                if (state.loading) "Loading…" else "Nothing to show",
                if (query.isNotBlank()) "Try another search term." else "Choose another category.",
                Modifier.weight(1f),
            )
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val gap = 12.dp
                val posterWidth = ((maxWidth - gap * 5) / 6).coerceAtLeast(118.dp)
                val railHeight = posterWidth * 1.5f + 18.dp
                Column(Modifier.fillMaxSize()) {
                    MediaHero(
                        item = richItem ?: items.first(),
                        type = type,
                        categoryItemCount = if (state.loading) null else state.items.size,
                        favorite = richItem?.let(viewModel::isFavorite) ?: false,
                        heroFocus = heroFocus,
                        posterFocus = posterFocusers.getOrNull(focusedIndex),
                        onPrimary = {
                            val item = focusedItem
                            if (item != null) {
                                if (type == ContentType.SERIES) viewModel.openDetail(item)
                                else viewModel.play(state.richItems[itemKey(item)] ?: item)
                            }
                        },
                        onFavorite = { focusedItem?.let(viewModel::toggleFavorite) },
                        onBackToPoster = {
                            if (heroFromPoster) posterFocusers.getOrNull(focusedIndex)?.let { runCatching { it.requestFocus() } }
                        },
                        onHeroFocused = {
                            focusedItem?.let {
                                viewModel.rememberFocusedItem(
                                    catalogArea(type),
                                    scope,
                                    MEDIA_HERO_FOCUS_PREFIX + itemKey(it),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxWidth().height(railHeight),
                        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item ->
                            val favorite = viewModel.isFavorite(item)
                            PosterArtCard(
                                item = state.richItems[itemKey(item)] ?: item,
                                favorite = favorite,
                                onClick = {
                                    focusedIndex = index
                                    heroFromPoster = true
                                    runCatching { heroFocus.requestFocus() }
                                },
                                modifier = Modifier
                                    .width(posterWidth)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                                            runCatching { wheelFocus.requestFocus() }
                                            true
                                        } else false
                                    }
                                    .then(
                                        if (index == 0) Modifier.focusProperties { left = wheelFocus; up = heroFocus }
                                        else Modifier.focusProperties { up = heroFocus },
                                    ),
                                focusRequester = posterFocusers[index],
                                onFocused = {
                                    focusedIndex = index
                                    heroFromPoster = false
                                    viewModel.rememberFocusedItem(catalogArea(type), catalogFocusScope(type, state.selectedCategories[type]?.id.orEmpty()), itemKey(item))
                                },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MediaHero(
    item: CatalogItem,
    type: ContentType,
    categoryItemCount: Int?,
    favorite: Boolean,
    heroFocus: FocusRequester,
    posterFocus: FocusRequester?,
    onPrimary: () -> Unit,
    onFavorite: () -> Unit,
    onBackToPoster: () -> Unit,
    onHeroFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    Box(modifier) {
        categoryItemCount?.let { count ->
            Text(
                text = "$count ${if (count == 1) "ITEM" else "ITEMS"}",
                color = colors.text.copy(alpha = .68f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = .6.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp),
            )
        }

        // The parent paints one full-screen backdrop and one full-screen mask.
        // Do not add a second local HeroBackdrop here; that was the rectangular
        // "masking box" visible only inside the hero stage.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(.62f)
                .padding(top = 12.dp, start = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                item.name,
                color = colors.text,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            val meta = buildList {
                item.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }?.let(::add)
                item.rating?.let { add("★ ${"%.1f".format(it)}") }
                item.genre?.split(',', '/', limit = 2)?.firstOrNull()?.trim()?.let(::add)
                formatRuntime(item.runtimeMinutes).takeIf { it.isNotBlank() }?.let(::add)
            }
            Text(
                meta.joinToString("   •   "),
                color = colors.text.copy(alpha = .82f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                item.plot ?: "No description supplied by this provider.",
                color = colors.text.copy(alpha = .80f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TvButton(
                label = if (type == ContentType.SERIES) "View Episodes" else "Play Now",
                leading = "▶",
                onClick = onPrimary,
                modifier = Modifier
                    .width(if (type == ContentType.SERIES) 136.dp else 112.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) { onBackToPoster(); true } else false
                    }
                    .then(if (posterFocus != null) Modifier.focusProperties { down = posterFocus } else Modifier),
                focusRequester = heroFocus,
                onFocused = onHeroFocused,
            )
            TvButton(
                label = if (favorite) "♥ Favorited" else "♡ Favorite",
                onClick = onFavorite,
                modifier = Modifier
                    .width(118.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) { onBackToPoster(); true } else false
                    },
                style = TvButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun MovieFacts(item: CatalogItem) {
    val colors = palette()
    val facts = buildList {
        item.director?.let { add("DIRECTOR  $it") }
        item.cast?.let { add("CAST  $it") }
        item.country?.let { add("LANGUAGE / COUNTRY  $it") }
        listOfNotNull(item.resolution, item.audioLayout).takeIf { it.isNotEmpty() }?.joinToString(" • ")?.let { add("QUALITY  $it") }
    }
    if (facts.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    facts.take(4).forEach { fact ->
        Text(fact, color = colors.text.copy(alpha = .70f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.62f))
    }
}

@Composable
private fun LiveGuideScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
    navReturnFocus: FocusRequester,
) {
    val type = ContentType.LIVE
    val query = state.searchQuery.trim()
    val channels = remember(state.items, query) {
        if (query.isBlank()) state.items else state.items.filter { it.name.contains(query, ignoreCase = true) }
    }
    val wheelFocus = contentEntryFocus
    val rowFocusers = remember(channels.map { it.id }) { List(channels.size) { FocusRequester() } }
    val guideListState = rememberLazyListState()
    val scope = catalogFocusScope(type, state.selectedCategories[type]?.id.orEmpty())
    val focusRequest = state.focusRequest?.takeIf { it.scope == scope }
    var focusedIndex by remember(state.selectedCategories[type]?.id) { mutableIntStateOf(0) }
    var pendingFocusIndex by remember(state.selectedCategories[type]?.id) { mutableStateOf<Int?>(null) }
    var pendingPageDelta by remember(state.selectedCategories[type]?.id) { mutableIntStateOf(0) }

    LaunchedEffect(focusRequest, channels.map { it.id }) {
        if (channels.isEmpty()) return@LaunchedEffect

        if (focusRequest != null) {
            val target = when {
                focusRequest.firstItem -> 0
                focusRequest.itemKey != null -> channels.indexOfFirst { itemKey(it) == focusRequest.itemKey }.coerceAtLeast(0)
                else -> 0
            }.coerceIn(channels.indices)
            focusedIndex = target
            pendingPageDelta = 0
            pendingFocusIndex = target
        } else if (focusedIndex !in channels.indices) {
            focusedIndex = focusedIndex.coerceIn(channels.indices)
        }
    }

    LaunchedEffect(state.selectedCategories[type]?.id) {
        if (focusRequest == null) {
            focusedIndex = 0
            pendingPageDelta = 0
        }
    }

    LaunchedEffect(focusedIndex, channels.map { it.id }) {
        if (channels.isEmpty()) return@LaunchedEffect
        val preloadStart = (focusedIndex - 2).coerceAtLeast(0)
        viewModel.ensureEpg(channels.drop(preloadStart).take(LIVE_GUIDE_PAGE_SIZE + 2))
    }

    LaunchedEffect(pendingFocusIndex, channels.map { it.id }) {
        val target = pendingFocusIndex ?: return@LaunchedEffect
        if (target !in channels.indices) {
            pendingFocusIndex = null
            pendingPageDelta = 0
            return@LaunchedEffect
        }

        val maxFirst = (channels.size - LIVE_GUIDE_PAGE_SIZE).coerceAtLeast(0)
        val currentFirst = guideListState.firstVisibleItemIndex.coerceIn(0, maxFirst)
        val currentLast = (currentFirst + LIVE_GUIDE_PAGE_SIZE - 1).coerceAtMost(channels.lastIndex)

        val scrollTarget = when {
            pendingPageDelta != 0 ->
                (currentFirst + pendingPageDelta * LIVE_GUIDE_PAGE_SIZE).coerceIn(0, maxFirst)
            target < currentFirst -> target.coerceIn(0, maxFirst)
            target > currentLast -> (target - LIVE_GUIDE_PAGE_SIZE + 1).coerceIn(0, maxFirst)
            else -> null
        }

        if (scrollTarget != null && scrollTarget != guideListState.firstVisibleItemIndex) {
            guideListState.scrollToItem(scrollTarget)
        }

        // Wait for the lazy row containing the requested channel to be attached
        // before requesting focus. Keeping every channel in one keyed LazyColumn
        // avoids the old five-row sliding window replacing the currently-focused
        // composable as soon as row 3 was crossed.
        repeat(8) { attempt ->
            delay(if (attempt == 0) 24L else 18L)
            if (runCatching { rowFocusers[target].requestFocus() }.getOrDefault(false)) {
                pendingFocusIndex = null
                pendingPageDelta = 0
                if (focusRequest != null) viewModel.consumeFocusRequest(scope)
                return@LaunchedEffect
            }
        }
    }

    fun focusChannel(index: Int, pageDelta: Int = 0) {
        if (index in channels.indices) {
            focusedIndex = index
            pendingPageDelta = pageDelta
            pendingFocusIndex = index
        }
    }

    Row(Modifier.fillMaxSize()) {
        CategoryWheel(
            type = type,
            state = state,
            viewModel = viewModel,
            focusRequester = wheelFocus,
            navReturnFocus = navReturnFocus,
            onEnterContent = { if (channels.isNotEmpty()) focusChannel(0) },
            modifier = Modifier.width(webOsWheelWidth()).fillMaxHeight(),
        )
        Spacer(Modifier.width(2.dp))
        if (channels.isEmpty()) {
            EmptyState(if (state.loading) "Loading…" else "No channels", "Choose another category.", Modifier.weight(1f))
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val channelColumnWidth = 184.dp
                val headerHeight = 30.dp
                val footerHeight = 18.dp
                val rowGap = 3.dp
                val start = guideWindowStart()
                val end = start + 90 * 60_000L
                val now = System.currentTimeMillis()
                val timelineWidth = maxWidth - channelColumnWidth
                val rowsHeight = (maxHeight - headerHeight - 3.dp - footerHeight - 4.dp).coerceAtLeast(100.dp)
                val rowHeight = (
                    (rowsHeight.value - rowGap.value * (LIVE_GUIDE_PAGE_SIZE - 1)) /
                        LIVE_GUIDE_PAGE_SIZE.toFloat()
                    ).coerceAtLeast(1f).dp

                Column(Modifier.fillMaxSize()) {
                    GuideHeader(channels.size, channelColumnWidth)
                    Spacer(Modifier.height(3.dp))
                    LazyColumn(
                        state = guideListState,
                        modifier = Modifier.fillMaxWidth().height(rowsHeight),
                        verticalArrangement = Arrangement.spacedBy(rowGap),
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { absoluteIndex, channel ->
                            GuideRow(
                                channel = channel,
                                programs = state.epg[channel.id].orEmpty(),
                                favorite = viewModel.isFavorite(channel),
                                focusRequester = rowFocusers[absoluteIndex],
                                channelColumnWidth = channelColumnWidth,
                                onFocus = {
                                    focusedIndex = absoluteIndex
                                    pendingFocusIndex = null
                                    pendingPageDelta = 0
                                    viewModel.rememberFocusedItem(catalogArea(type), scope, itemKey(channel))
                                },
                                onPlay = { viewModel.play(channel, channels) },
                                onFavorite = { viewModel.toggleFavorite(channel) },
                                onMoveVertical = { delta ->
                                    focusChannel((absoluteIndex + delta).coerceIn(channels.indices))
                                },
                                onPageVertical = { delta ->
                                    focusChannel(
                                        (absoluteIndex + delta * LIVE_GUIDE_PAGE_SIZE).coerceIn(channels.indices),
                                        pageDelta = delta,
                                    )
                                },
                                onBack = { runCatching { wheelFocus.requestFocus() } },
                                modifier = Modifier.fillMaxWidth().height(rowHeight),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().height(footerHeight),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "RED  ${if (viewModel.isFavorite(channels[focusedIndex.coerceIn(channels.indices)])) "Remove favorite" else "Add favorite"}",
                            color = palette().muted,
                            fontSize = 8.sp,
                        )
                    }
                }

                // One continuous current-time marker over the visible guide rows.
                if (now in start..end) {
                    val fraction = ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .offset(x = channelColumnWidth + timelineWidth * fraction, y = headerHeight + 3.dp)
                            .width(2.dp)
                            .height(rowsHeight)
                            .background(palette().accent)
                            .zIndex(20f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(channelCount: Int, channelColumnWidth: androidx.compose.ui.unit.Dp) {
    val colors = palette()
    val start = guideWindowStart()
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$channelCount CHANNELS",
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(channelColumnWidth).padding(start = 8.dp),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..3).forEach { segment ->
                Text(formatGuideTime(start + segment * 30 * 60_000L), color = colors.muted, fontSize = 8.5.sp)
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: CatalogItem,
    programs: List<EpgProgram>,
    favorite: Boolean,
    focusRequester: FocusRequester,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onMoveVertical: (delta: Int) -> Unit,
    onPageVertical: (delta: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    var focused by remember(channel.id) { mutableStateOf(false) }
    Row(
        modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_PROG_RED -> { onFavorite(); true }
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { onPageVertical(-1); true }
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { onPageVertical(1); true }
                    event.key == Key.DirectionUp -> { onMoveVertical(-1); true }
                    event.key == Key.DirectionDown -> { onMoveVertical(1); true }
                    event.key == Key.DirectionCenter || event.key == Key.Enter -> { onPlay(); true }
                    event.key == Key.Back -> { onBack(); true }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPlay,
            )
            .focusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .width(channelColumnWidth)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(
                    if (focused) colors.accent.copy(alpha = .09f) else colors.panel.copy(alpha = .18f),
                    RoundedCornerShape(7.dp),
                )
                .border(
                    if (focused) 1.5.dp else .5.dp,
                    if (focused) colors.accent.copy(alpha = .95f) else Color.White.copy(alpha = .04f),
                    RoundedCornerShape(7.dp),
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(channel, Modifier.size(30.dp), live = true, transparentLive = true)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                if (focused) {
                    Text(
                        channel.name,
                        color = colors.text,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1_200,
                            repeatDelayMillis = 1_000,
                            velocity = 18.dp,
                        ),
                    )
                } else {
                    Text(
                        channel.name,
                        color = colors.text,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                channel.channelNumber?.let { Text("CH $it", color = colors.muted, fontSize = 7.sp, maxLines = 1) }
            }
            if (favorite) Text("♥", color = colors.danger, fontSize = 10.sp)
        }
        GuideTimeline(programs, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun GuideTimeline(programs: List<EpgProgram>, modifier: Modifier = Modifier) {
    val colors = palette()
    val start = guideWindowStart()
    val end = start + 90 * 60_000L
    BoxWithConstraints(modifier) {
        val width = maxWidth
        val visiblePrograms = programs.filter { it.endEpochMs > start && it.startEpochMs < end }
        if (visiblePrograms.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(3.dp).background(Color.White.copy(alpha = .028f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Program information unavailable", color = colors.muted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 7.dp))
            }
        } else {
            visiblePrograms.forEach { program ->
                val leftFraction = ((program.startEpochMs.coerceAtLeast(start) - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                val rightFraction = ((program.endEpochMs.coerceAtMost(end) - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                val fraction = (rightFraction - leftFraction).coerceAtLeast(.04f)
                Box(
                    Modifier
                        .offset(x = width * leftFraction)
                        .width(width * fraction)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                        .background(Color.White.copy(alpha = .045f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Column {
                        Text(program.title, color = colors.text, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatGuideTime(program.startEpochMs), color = colors.muted, fontSize = 7.5.sp)
                    }
                }
            }
        }
    }
}

private fun guideWindowStart(now: Long = System.currentTimeMillis()): Long {
    val halfHour = 30 * 60_000L
    return now - (now % halfHour)
}

private fun formatGuideTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
