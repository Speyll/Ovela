package com.xtreamlytv.androidtv.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.xtreamlytv.androidtv.R
import com.xtreamlytv.androidtv.model.AppTheme
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.LocalAppTheme
import com.xtreamlytv.androidtv.ui.theme.XtreamlyFontFamily
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay

private fun webOsBackgroundAsset(theme: AppTheme): Int = when (theme) {
    AppTheme.BLUE -> R.drawable.xtreamlytv_background_blue
    AppTheme.TEAL -> R.drawable.xtreamlytv_background_teal
    AppTheme.GRAPHITE -> R.drawable.xtreamlytv_background_gray
    AppTheme.PURPLE -> R.drawable.xtreamlytv_background_purple
    AppTheme.PINK -> R.drawable.xtreamlytv_background_pink
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val colors = palette()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF010204)),
    ) {
        // Keep the upper application canvas genuinely black and introduce the
        // selected appearance hue only as the screen approaches the bottom.
        // This intentionally mirrors the visual read of the webOS shell instead
        // of tinting the entire Android canvas with the theme background color.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .72f to Color.Transparent,
                        .80f to colors.background.copy(alpha = .18f),
                        .89f to colors.background2.copy(alpha = .48f),
                        .95f to colors.accentSecondary.copy(alpha = .075f),
                        1f to colors.accent.copy(alpha = .15f),
                    ),
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to colors.accent.copy(alpha = .18f),
                        .36f to colors.accent.copy(alpha = .10f),
                        .72f to colors.accentSecondary.copy(alpha = .035f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(size.width * .50f, size.height * 1.04f),
                    radius = size.height * .32f,
                ),
            )
        }
        content()
    }
}

private fun wordmarkAsset(theme: AppTheme): String = when (theme) {
    AppTheme.BLUE -> "file:///android_asset/branding/xtreamlytv-wordmark-blue.svg"
    AppTheme.TEAL -> "file:///android_asset/branding/xtreamlytv-wordmark-teal.svg"
    AppTheme.GRAPHITE -> "file:///android_asset/branding/xtreamlytv-wordmark-gray.svg"
    AppTheme.PURPLE -> "file:///android_asset/branding/xtreamlytv-wordmark-purple.svg"
    AppTheme.PINK -> "file:///android_asset/branding/xtreamlytv-wordmark-pink.svg"
}

@Composable
fun BrandLockup(iconSize: Dp = 34.dp, wordmarkSize: Int = 18) {
    val theme = LocalAppTheme.current
    val lockupHeight = maxOf(iconSize.value, wordmarkSize * 1.35f).dp
    AsyncImage(
        model = wordmarkAsset(theme),
        contentDescription = "XtreamlyTV",
        modifier = Modifier.height(lockupHeight).width(lockupHeight * 4.184f),
        contentScale = ContentScale.Fit,
    )
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
        if (focused) Color.White.copy(alpha = .98f) else Color.White.copy(alpha = 0.06f),
        label = "tvFocusBorder",
    )
    val fill by animateColorAsState(
        targetValue = background ?: if (active) colors.accent.copy(alpha = 0.12f) else colors.panel.copy(alpha = 0.86f),
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
                if (focusBorder) Modifier.border(if (focused) 2.5.dp else 1.dp, border, RoundedCornerShape(radius))
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
    val background = when (style) {
        TvButtonStyle.Primary -> Brush.horizontalGradient(listOf(colors.accent, colors.accentSecondary))
        TvButtonStyle.Secondary -> Brush.horizontalGradient(listOf(colors.panelStrong.copy(alpha = .82f), colors.panelStrong.copy(alpha = .82f)))
        TvButtonStyle.Danger -> Brush.horizontalGradient(listOf(colors.danger.copy(alpha = 0.22f), colors.danger.copy(alpha = 0.22f)))
    }
    val textColor = when (style) {
        TvButtonStyle.Primary -> colors.accentInk
        TvButtonStyle.Secondary -> colors.text
        TvButtonStyle.Danger -> Color(0xFFFFA0AA)
    }
    TvSurface(
        modifier = modifier.height(32.dp),
        onClick = onClick,
        enabled = enabled,
        background = Color.Transparent,
        radius = 10.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!leading.isNullOrBlank()) {
                Text(leading, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = textColor.copy(alpha = if (enabled) 1f else 0.55f),
                fontSize = 10.5.sp,
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
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, color = colors.muted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.then(requesterModifier).fillMaxWidth().height(36.dp).onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 10.5.sp, fontFamily = XtreamlyFontFamily),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background.copy(alpha = 0.72f))
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank() && placeholder.isNotBlank()) Text(placeholder, color = colors.muted.copy(alpha = 0.72f), fontSize = 10.5.sp)
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
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    var focused by remember { mutableStateOf(false) }
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(requesterModifier).height(38.dp).onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 11.sp, fontFamily = XtreamlyFontFamily),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panelStrong.copy(alpha = .95f))
                    .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvIcon("search", if (focused) colors.accent else colors.muted, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) Text(placeholder, color = colors.muted.copy(alpha = .7f), fontSize = 11.sp)
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
    TvSurface(
        modifier = modifier.height(30.dp),
        onClick = onClick,
        active = selected,
        radius = 15.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) colors.accent else colors.text, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SectionHeader(title: String, meta: String? = null, modifier: Modifier = Modifier) {
    val colors = palette()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (!meta.isNullOrBlank()) Text(meta, color = colors.muted, fontSize = 8.sp)
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
    val url = if (!item.coverUrl.isNullOrBlank() && !live) item.coverUrl else item.imageUrl
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

private fun highResolutionBackdropUrl(url: String?): String? {
    val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!value.contains("image.tmdb.org", ignoreCase = true)) return value
    return value.replace(
        Regex("/t/p/(?:w\\d+(?:_and_h\\d+_[^/]+)?|original)/", RegexOption.IGNORE_CASE),
        "/t/p/original/",
    )
}

@Composable
fun HeroBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackDrawable: Int? = null,
    imageAlpha: Float = .50f,
    aggressiveBottomVignette: Boolean = false,
) {
    val ink = Color(0xFF010204)
    Box(modifier) {
        when {
            !url.isNullOrBlank() -> AsyncImage(
                model = highResolutionBackdropUrl(url),
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

        // Keep provider artwork atmospheric rather than dominant. This neutral
        // veil is intentionally applied before the directional legibility masks
        // so bright backdrops do not compete with navigation or metadata.
        Box(
            Modifier.fillMaxSize().background(ink.copy(alpha = .14f)),
        )

        // Text legibility mask. Keep the copy side dark without making the
        // provider artwork look like it lives inside another rectangular card.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to ink.copy(alpha = .92f),
                        .18f to ink.copy(alpha = .78f),
                        .38f to ink.copy(alpha = .50f),
                        .58f to ink.copy(alpha = .18f),
                        .78f to Color.Transparent,
                        1f to ink.copy(alpha = .08f),
                    ),
                ),
            ),
        )

        // Fade the first quarter of every hero decisively into the black top
        // navigation. This removes the bright horizontal seam that was visible
        // whenever a provider backdrop started with a light sky / highlight.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to ink.copy(alpha = .99f),
                        .07f to ink.copy(alpha = .96f),
                        .16f to ink.copy(alpha = .78f),
                        .27f to ink.copy(alpha = .38f),
                        .38f to Color.Transparent,
                    ),
                ),
            ),
        )

        // Home needs a stronger lower vignette so the hero dissolves into its
        // Continue Watching rails. Catalog/detail heroes retain more artwork.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = if (aggressiveBottomVignette) {
                        arrayOf(
                            0f to Color.Transparent,
                            .48f to Color.Transparent,
                            .66f to ink.copy(alpha = .24f),
                            .82f to ink.copy(alpha = .70f),
                            1f to ink.copy(alpha = .98f),
                        )
                    } else {
                        arrayOf(
                            0f to Color.Transparent,
                            .64f to Color.Transparent,
                            .82f to ink.copy(alpha = .32f),
                            1f to ink.copy(alpha = .74f),
                        )
                    },
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
            Text(message, color = Color(0xFFFFB3BB), fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
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

@Composable
fun AppLoadingScreen(onFinished: () -> Unit) {
    val theme = LocalAppTheme.current
    val colors = palette()
    val context = LocalContext.current
    val themeName = when (theme) {
        AppTheme.BLUE -> "blue"
        AppTheme.TEAL -> "teal"
        AppTheme.GRAPHITE -> "gray"
        AppTheme.PURPLE -> "purple"
        AppTheme.PINK -> "pink"
    }

    var webReady by remember { mutableStateOf(false) }
    var animationStarted by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var soundPool by remember { mutableStateOf<SoundPool?>(null) }
    var soundId by remember { mutableStateOf(0) }
    var soundLoaded by remember { mutableStateOf(false) }

    // Load the sting asynchronously while the WebView warms. Playback begins
    // only when the actual webOS animation is ready, keeping sound and motion in
    // sync on a true cold boot.
    DisposableEffect(Unit) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { _, loadedId, status ->
            if (status == 0 && loadedId == soundId) {
                soundLoaded = true
            }
        }
        soundId = pool.load(context, R.raw.startup_chime, 1)

        onDispose {
            runCatching { webView?.stopLoading() }
            runCatching { webView?.destroy() }
            webView = null
            runCatching { pool.release() }
            soundPool = null
        }
    }

    // Once Chromium has actually painted the local startup page, restart every
    // CSS/Web animation from t=0. This preserves the exact webOS intro instead of
    // revealing it halfway through after a slow first WebView initialization.
    LaunchedEffect(webReady) {
        if (!webReady || animationStarted) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect

        // Give the committed page one frame to paint before making it visible.
        delay(32L)
        view.evaluateJavascript(
            """
            (function () {
              try {
                document.getAnimations().forEach(function (a) {
                  a.cancel();
                  a.play();
                });
              } catch (e) {}
            })();
            """.trimIndent(),
            null,
        )
        animationStarted = true
    }

    LaunchedEffect(animationStarted, soundLoaded) {
        if (animationStarted && soundLoaded) {
            soundPool?.let { pool ->
                runCatching { pool.play(soundId, .46f, .46f, 1, 0, 1f) }
            }
        }
    }

    // Measure the hold from the animation start, not from Activity creation.
    // Cold WebView startup can take longer, but it can no longer consume the
    // animation's display window and leave the user with sound-only startup.
    LaunchedEffect(animationStarted) {
        if (!animationStarted) return@LaunchedEffect
        delay(2050L)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF02050A)),
        contentAlignment = Alignment.Center,
    ) {
        // Deterministic native fallback shown while Chromium initializes.
        // It is intentionally static; the exact webOS animation starts only once
        // its page is ready, so cold and warm launches now follow the same sequence.
        Row(
            modifier = Modifier
                .graphicsLayer { alpha = if (animationStarted) 0f else 1f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "X",
                color = colors.accent,
                fontFamily = XtreamlyFontFamily,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-3).sp,
            )
            Text(
                "treamlyTV",
                color = Color(0xFFF4F8FF),
                fontFamily = XtreamlyFontFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (animationStarted) 1f else 0f },
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.rgb(2, 5, 10))
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    tag = themeName
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (view === webView || webView == null) {
                                webReady = true
                            }
                        }
                    }
                    webView = this
                    loadUrl("file:///android_asset/startup/index.html?theme=$themeName")
                }
            },
            update = { view ->
                if (view.tag != themeName) {
                    animationStarted = false
                    webReady = false
                    view.tag = themeName
                    view.loadUrl("file:///android_asset/startup/index.html?theme=$themeName")
                }
            },
        )
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
