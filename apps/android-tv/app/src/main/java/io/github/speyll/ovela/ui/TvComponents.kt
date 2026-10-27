package io.github.speyll.ovela.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import io.github.speyll.ovela.R
import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.OvelaFontFamily
import io.github.speyll.ovela.ui.theme.palette

private const val TMDB_BACKDROP_WIDTH = "w1280"
private const val TMDB_POSTER_WIDTH = "w500"

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val colors = palette()
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background3),
    ) {
        // Near-black canvas with one very low-alpha warm wash near the bottom
        // edge. Two draw calls, no blur, no animated gradient: the cost is the
        // same order as the previous implementation and it keeps the UI dark
        // enough that artwork supplies the colour on screen.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .64f to Color.Transparent,
                        .88f to colors.background2.copy(alpha = .60f),
                        1f to colors.background.copy(alpha = .92f),
                    ),
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colors.accent.copy(alpha = .050f),
                        .55f to colors.accent.copy(alpha = .018f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(size.width * .5f, size.height * 1.02f),
                    radius = size.height * .44f,
                ),
            )
        }
        content()
    }
}

/**
 * The Ovela lockup: the source-extracted O mark and horizontal wordmark.
 * Uses the supplied brand assets rather than a code-drawn approximation.
 */
@Composable
fun BrandLockup(iconSize: Dp = 34.dp, wordmarkSize: Int = 18) {
    val colors = palette()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.ovela_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(colors.accent)
        )
        Spacer(Modifier.width(iconSize * .32f))
        Image(
            painter = painterResource(id = R.drawable.ovela_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(wordmarkSize.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvSurface(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    radius: Dp = 12.dp,
    background: Color? = null,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onFocused: (() -> Unit)? = null,
    focusBorder: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (focused) colors.text.copy(alpha = .96f) else Color.White.copy(alpha = 0.07f),
        label = "tvFocusBorder",
    )
    val fill by animateColorAsState(
        targetValue = background ?: if (active) colors.accent.copy(alpha = 0.10f) else colors.panel.copy(alpha = 0.92f),
        label = "tvSurfaceFill",
    )
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val activationModifier = if (onLongClick == null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = longClickLabel,
        )
    }
    Box(
        modifier
            .then(requesterModifier)
            .clip(RoundedCornerShape(radius))
            .background(fill)
            .then(
                if (focusBorder) Modifier.border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(radius))
                else Modifier,
            )
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (focusState.isFocused) onFocused?.invoke()
            }
            .then(activationModifier)
            .focusable(enabled),
    ) { content() }
}

@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TvButtonStyle = TvButtonStyle.Primary,
    enabled: Boolean = true,
    leading: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    // One flat fill per state. The previous two-tone gradient read as generic
    // streaming UI; a single confident accent block is the Ovela signature.
    val background = when (style) {
        TvButtonStyle.Primary -> SolidColor(colors.accent)
        TvButtonStyle.Secondary -> SolidColor(colors.panelStrong.copy(alpha = .92f))
        TvButtonStyle.Danger -> SolidColor(colors.danger.copy(alpha = 0.18f))
    }
    val textColor = when (style) {
        TvButtonStyle.Primary -> colors.accentInk
        TvButtonStyle.Secondary -> colors.text
        TvButtonStyle.Danger -> colors.danger
    }
    TvSurface(
        modifier = modifier.height(if (touchUi) 44.dp else 32.dp),
        onClick = onClick,
        enabled = enabled,
        background = Color.Transparent,
        radius = if (touchUi) 12.dp else 10.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(background).padding(horizontal = if (touchUi) 14.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!leading.isNullOrBlank()) {
                Text(leading, color = textColor, fontSize = if (touchUi) 12.sp else 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = textColor.copy(alpha = if (enabled) 1f else 0.55f),
                fontSize = if (touchUi) 12.sp else 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

enum class TvButtonStyle { Primary, Secondary, Danger }

@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    var focused by remember { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, color = colors.muted, fontSize = if (touchUi) 11.sp else 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(if (touchUi) 5.dp else 4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .then(requesterModifier)
                .fillMaxWidth()
                .height(if (touchUi) 48.dp else 36.dp)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = if (touchUi) 13.sp else 10.5.sp,
                fontFamily = OvelaFontFamily,
            ),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (touchUi) 12.dp else 10.dp))
                        .background(colors.background.copy(alpha = 0.72f))
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) colors.accent else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(if (touchUi) 12.dp else 10.dp),
                        )
                        .padding(horizontal = if (touchUi) 14.dp else 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank() && placeholder.isNotBlank()) {
                        Text(placeholder, color = colors.muted.copy(alpha = 0.72f), fontSize = if (touchUi) 13.sp else 10.5.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    var focused by remember { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(requesterModifier).height(if (touchUi) 48.dp else 38.dp).onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = TextStyle(
            color = colors.text,
            fontSize = if (touchUi) 13.sp else 11.sp,
            fontFamily = OvelaFontFamily,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panelStrong.copy(alpha = .95f))
                    .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
                    .padding(horizontal = if (touchUi) 16.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvIcon("search", if (focused) colors.accent else colors.muted, Modifier.size(if (touchUi) 18.dp else 16.dp))
                Spacer(Modifier.width(if (touchUi) 10.dp else 8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(placeholder, color = colors.muted.copy(alpha = .7f), fontSize = if (touchUi) 13.sp else 11.sp)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
fun TvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    TvSurface(
        modifier = modifier.height(if (touchUi) 36.dp else 30.dp),
        onClick = onClick,
        active = selected,
        radius = if (touchUi) 18.dp else 15.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = if (touchUi) 13.dp else 11.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) colors.accent else colors.text,
                fontSize = if (touchUi) 11.sp else 9.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, meta: String? = null, modifier: Modifier = Modifier) {
    val colors = palette()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
        )
        if (!meta.isNullOrBlank()) {
            Text(
                meta.uppercase(),
                color = colors.muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

/** Legacy-compatible card used by Favorites management screens. */
@Composable
fun LiveItemCard(
    item: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 76.dp,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    TvSurface(modifier.height(cardHeight), onClick, focusRequester = focusRequester, onFocused = onFocused) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(item, Modifier.size(38.dp), live = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(item.name, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.channelNumber?.let { "CH $it" } ?: "Live programming", color = colors.muted, fontSize = 9.sp, maxLines = 1)
            }
            if (favorite) TvIcon("heart", colors.danger, Modifier.size(14.dp))
        }
    }
}

/** Legacy-compatible poster card used by Favorites management screens. */
@Composable
fun PosterItemCard(
    item: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 188.dp,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    TvSurface(modifier.height(cardHeight), onClick, focusRequester = focusRequester, onFocused = onFocused) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Artwork(item, Modifier.fillMaxSize(), live = false)
                if (favorite) TvIcon("heart", colors.danger, Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
                Text(item.name, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.rating?.let { "★ ${"%.1f".format(it)}" } ?: item.type.title(), color = colors.muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun PosterArtCard(
    item: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    var focused by remember(item.id) { mutableStateOf(false) }
    val requester = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier
            .then(requester)
            .aspectRatio(2f / 3f)
            .zIndex(if (focused) 2f else 0f)
            .graphicsLayer {
                scaleX = if (focused) 1.01f else 1f
                scaleY = if (focused) 1.01f else 1f
            }
            .shadow(if (focused) 8.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(if (focused) 1.5.dp else 0.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focus ->
                focused = focus.isFocused
                if (focus.isFocused) onFocused?.invoke()
            }
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Artwork(item, Modifier.fillMaxSize(), live = false)
        if (favorite) TvIcon("heart", colors.danger, Modifier.align(Alignment.TopEnd).padding(9.dp).size(15.dp))
    }
}

@Composable
fun TransparentLiveLogoCard(
    item: CatalogItem,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    var focused by remember(item.id) { mutableStateOf(false) }
    val requester = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Column(
        modifier
            .then(requester)
            .clip(RoundedCornerShape(10.dp))
            .border(if (focused) 1.dp else 0.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focus -> focused = focus.isFocused; if (focus.isFocused) onFocused?.invoke() }
            .clickable(onClick = onClick)
            .focusable()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Artwork(item, Modifier.fillMaxSize(.82f), live = true, transparentLive = true)
            if (favorite) TvIcon("heart", colors.danger, Modifier.align(Alignment.TopEnd).size(13.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(item.name, color = colors.text, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Artwork(item: CatalogItem, modifier: Modifier, live: Boolean, transparentLive: Boolean = false) {
    val colors = palette()
    val shape = RoundedCornerShape(if (live) 9.dp else 14.dp)
    val rawUrl = if (!item.coverUrl.isNullOrBlank() && !live) item.coverUrl else item.imageUrl
    val url = if (live) rawUrl else posterRequestUrl(rawUrl)
    Box(
        modifier
            .clip(shape)
            .then(
                if (transparentLive) Modifier
                else Modifier.background(Brush.linearGradient(listOf(colors.panelStrong, colors.accent.copy(alpha = 0.14f), colors.panelStrong))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().padding(if (live && !transparentLive) 4.dp else 0.dp),
                contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
            )
        } else {
            Text(initials(item.name), color = colors.accent, fontSize = if (live) 12.sp else 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Hoisted: this runs once per visible artwork on every recomposition, and
// compiling the pattern there measurably churns the allocator on TV hardware.
private val TMDB_SIZE_SEGMENT = Regex(
    "/t/p/(?:w\\d+(?:_and_h\\d+_[^/]+)?|original)/",
    RegexOption.IGNORE_CASE,
)

// Composition runs on the main thread only, so a small access-ordered map is
// enough to keep the size rewrite off every card's recomposition.
private val sizedArtworkUrlCache = object : LinkedHashMap<String, String>(64, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 192
}

private fun tmdbArtworkRequestUrl(url: String?, width: String): String? {
    val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.contains("image.tmdb.org", ignoreCase = true)) return value
    val key = "$width|$value"
    sizedArtworkUrlCache[key]?.let { return it }
    return value.replace(TMDB_SIZE_SEGMENT, "/t/p/$width/").also { sizedArtworkUrlCache[key] = it }
}

internal fun posterRequestUrl(url: String?): String? = tmdbArtworkRequestUrl(url, TMDB_POSTER_WIDTH)

internal fun backdropRequestUrl(url: String?): String? = tmdbArtworkRequestUrl(url, TMDB_BACKDROP_WIDTH)

@Composable
fun HeroBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackDrawable: Int? = null,
    imageAlpha: Float = .34f,
    aggressiveBottomVignette: Boolean = false,
) {
    val ink = palette().background3
    Box(modifier) {
        when {
            !url.isNullOrBlank() -> AsyncImage(
                model = backdropRequestUrl(url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
            fallbackDrawable != null -> Image(
                painter = painterResource(fallbackDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
                contentScale = ContentScale.Crop,
            )
        }

        // Two flat scrims. Artwork is atmosphere, not a background image: one
        // horizontal wash keeps copy legible, one bottom fade dissolves the hero
        // into the rails. The old four-layer stack drew the same pixels four
        // times and read as heavy on TV hardware.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to ink.copy(alpha = .90f),
                        .42f to ink.copy(alpha = .46f),
                        .72f to Color.Transparent,
                    ),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to ink.copy(alpha = .92f),
                        .30f to Color.Transparent,
                        .70f to Color.Transparent,
                        1f to ink.copy(alpha = if (aggressiveBottomVignette) .96f else .62f),
                    ),
                ),
            ),
        )
    }
}

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    val colors = palette()
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(8.dp))
        Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(description, color = colors.muted, fontSize = 11.sp)
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = palette()
    TvSurface(modifier.fillMaxWidth().height(48.dp), { onDismiss?.invoke() }, enabled = onDismiss != null, background = colors.danger.copy(alpha = 0.16f)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = colors.danger, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
            if (onDismiss != null) Text("Dismiss", color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LoadingOverlay(label: String = "Loading…") {
    val colors = palette()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp), color = colors.accent, strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text(label, color = colors.text, fontSize = 12.sp)
        }
    }
}

/**
 * Shown only while the app is genuinely restoring credentials or connecting.
 * There is no fixed display duration, tagline, chime or decorative animation:
 * the shell replaces this as soon as initialization resolves.
 */
@Composable
fun StartupScreen() {
    val colors = palette()
    Box(
        Modifier.fillMaxSize().background(colors.background3),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(iconSize = 52.dp, wordmarkSize = 26)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

fun ContentType.title(): String = when (this) {
    ContentType.LIVE -> "Live TV"
    ContentType.MOVIE -> "Movies"
    ContentType.SERIES -> "Series"
    ContentType.EPISODE -> "Episode"
}

fun ContentType.iconName(): String = when (this) {
    ContentType.LIVE -> "tv"
    ContentType.MOVIE -> "popcorn"
    ContentType.SERIES, ContentType.EPISODE -> "play"
}

@Composable
fun TvIcon(name: String, color: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(iconResource(name)),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit,
    )
}

private fun iconResource(name: String): Int = when (name) {
    "home" -> R.drawable.ic_home
    "tv" -> R.drawable.ic_tv
    "popcorn" -> R.drawable.ic_popcorn
    "play" -> R.drawable.ic_play_box
    "heart" -> R.drawable.ic_heart
    "settings" -> R.drawable.ic_settings
    "plus" -> R.drawable.ic_plus
    "folder" -> R.drawable.ic_folder
    "star" -> R.drawable.ic_star
    "trophy" -> R.drawable.ic_trophy
    "smile" -> R.drawable.ic_smile
    "search" -> R.drawable.ic_search
    else -> R.drawable.ic_folder
}

fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatRuntime(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return ""
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h${if (remainder > 0) " ${remainder}m" else ""}" else "${minutes}m"
}

private fun initials(name: String): String = name
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "TV" }
