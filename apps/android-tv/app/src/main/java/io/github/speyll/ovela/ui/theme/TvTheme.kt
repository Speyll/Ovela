package io.github.speyll.ovela.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import io.github.speyll.ovela.R
import io.github.speyll.ovela.model.AppTheme

@Immutable
data class TvPalette(
    val background: Color,
    val background2: Color,
    val background3: Color,
    val panel: Color,
    val panelStrong: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentInk: Color,
    val danger: Color = BrandTokens.Danger,
)

val LocalAppTheme = staticCompositionLocalOf { AppTheme.BLUE }
val LocalTvPalette = staticCompositionLocalOf { paletteFor(AppTheme.BLUE) }
val LocalTouchUi = staticCompositionLocalOf { false }

/**
 * Inter is bundled for Android and self-hosted by webOS. It carries the Ovela
 * identity: neutral, slightly narrow, and legible at television distance
 * without the rounded softness of a consumer streaming brand.
 */
val OvelaFontFamily = FontFamily(
    Font(R.font.inter_regular, weight = FontWeight.Normal),
    Font(R.font.inter_medium, weight = FontWeight.Medium),
    Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
    Font(R.font.inter_bold, weight = FontWeight.Bold),
)

private val MaterialTypography = Typography()

/**
 * Display and headline styles are tracked tighter and set heavier than the
 * Material defaults so a large screen reads as editorial rather than as an
 * enlarged phone interface.
 */
val OvelaTypography = Typography(
    displayLarge = MaterialTypography.displayLarge.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    displayMedium = MaterialTypography.displayMedium.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    displaySmall = MaterialTypography.displaySmall.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.015).em,
    ),
    headlineLarge = MaterialTypography.headlineLarge.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.015).em,
    ),
    headlineMedium = MaterialTypography.headlineMedium.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.01).em,
    ),
    headlineSmall = MaterialTypography.headlineSmall.copy(
        fontFamily = OvelaFontFamily,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = MaterialTypography.titleLarge.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Medium),
    titleMedium = MaterialTypography.titleMedium.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Medium),
    titleSmall = MaterialTypography.titleSmall.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Medium),
    bodyLarge = MaterialTypography.bodyLarge.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Normal),
    bodyMedium = MaterialTypography.bodyMedium.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Normal),
    bodySmall = MaterialTypography.bodySmall.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Normal),
    labelLarge = MaterialTypography.labelLarge.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Medium),
    labelMedium = MaterialTypography.labelMedium.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = MaterialTypography.labelSmall.copy(fontFamily = OvelaFontFamily, fontWeight = FontWeight.Normal),
)

@Composable
fun palette(): TvPalette = LocalTvPalette.current

/*
 * Every surface colour comes from the generated BrandTokens, which are written
 * from packages/design-tokens/tokens.json by `npm run tokens`. Changing the
 * accent or the neutrals in that one file re-skins the whole Android app; no
 * screen holds its own palette literal.
 */

/** Lift a colour toward white by [amount] (0..1). */
private fun lift(base: Color, amount: Float): Color = lerp(base, Color.White, amount)

/** Drop a colour toward black by [amount] (0..1). */
private fun sink(base: Color, amount: Float): Color = lerp(base, Color.Black, amount)

/** A very dark wash of the accent, used for type sitting on an accent fill. */
private fun inkOf(accent: Color): Color = lerp(BrandTokens.Background, accent, 0.06f)

private val Ovela = TvPalette(
    background = BrandTokens.Background,
    background2 = BrandTokens.Background2,
    background3 = BrandTokens.Background3,
    panel = BrandTokens.Surface,
    panelStrong = BrandTokens.PanelStrong,
    text = BrandTokens.Text,
    muted = BrandTokens.Muted,
    accent = BrandTokens.Accent,
    accentSecondary = BrandTokens.AccentSecondary,
    accentInk = BrandTokens.AccentInk,
    danger = BrandTokens.Danger,
)

/**
 * The monochrome companion skin. Same neutrals, ivory highlight instead of
 * amber, for viewers who want the brand without a coloured accent.
 */
private val NoirSilver = Color(0xFFC3C8CF)

private val OvelaNoir = TvPalette(
    background = lift(BrandTokens.Background, 0.024f),
    background2 = lift(BrandTokens.Background, 0.05f),
    background3 = lift(BrandTokens.Background, 0.006f),
    panel = lift(BrandTokens.Surface, 0.014f),
    panelStrong = lift(BrandTokens.Surface, 0.055f),
    text = lift(BrandTokens.Text, 0.012f),
    muted = lift(BrandTokens.Muted, 0.012f),
    accent = NoirSilver,
    accentSecondary = sink(NoirSilver, 0.14f),
    accentInk = inkOf(NoirSilver),
    danger = BrandTokens.Danger,
)

/** Appearance choices offered in Settings, in presentation order. */
val selectableThemes: List<AppTheme> = listOf(AppTheme.BLUE, AppTheme.GRAPHITE)

fun AppTheme.displayLabel(): String = when (this) {
    AppTheme.GRAPHITE -> "Ovela Noir"
    else -> "Ovela"
}

/**
 * Retired skins (teal, purple, pink) resolve to the Ovela palette so a saved
 * preference from an older build still renders an on-brand interface instead of
 * a dead or off-palette screen. `noir` (graphite) stays distinct.
 */
fun paletteFor(theme: AppTheme): TvPalette = when (theme) {
    AppTheme.GRAPHITE -> OvelaNoir
    AppTheme.BLUE, AppTheme.TEAL, AppTheme.PURPLE, AppTheme.PINK -> Ovela
}
