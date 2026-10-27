package io.github.speyll.ovela.ui

/**
 * Anchored placement for the playback options panel. It prefers the space
 * above the trigger, right-aligned to it, then clamps the panel into the
 * visible root bounds. A null anchor means the controls have not been measured
 * yet; the fallback is the bottom-right safe area.
 */
internal data class MediaOptionsPlacement(val x: Int, val y: Int)

internal fun mediaOptionsPlacement(
    anchorRight: Int?,
    anchorTop: Int?,
    viewportWidth: Int,
    viewportHeight: Int,
    panelWidth: Int,
    panelHeight: Int,
    margin: Int,
    gap: Int,
): MediaOptionsPlacement {
    val safeMargin = margin.coerceAtLeast(0)
    val maxX = (viewportWidth - panelWidth - safeMargin).coerceAtLeast(safeMargin)
    val maxY = (viewportHeight - panelHeight - safeMargin).coerceAtLeast(safeMargin)
    val right = anchorRight ?: viewportWidth
    val top = anchorTop ?: viewportHeight
    val preferredX = right - panelWidth
    val preferredY = top - panelHeight - gap.coerceAtLeast(0)
    return MediaOptionsPlacement(
        x = preferredX.coerceIn(safeMargin, maxX),
        y = preferredY.coerceIn(safeMargin, maxY),
    )
}
