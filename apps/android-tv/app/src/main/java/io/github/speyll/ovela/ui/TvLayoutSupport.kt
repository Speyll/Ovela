package io.github.speyll.ovela.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.delay

internal class KeyedLazyCache<K : Any, V : Any>(private val create: (K) -> V) {
    private val values = HashMap<K, V>()
    private val owners = HashMap<K, Int>()

    val size: Int get() = values.size

    fun acquire(key: K): V {
        owners[key] = (owners[key] ?: 0) + 1
        return values.getOrPut(key) { create(key) }
    }

    fun get(key: K): V? = values[key]

    fun release(key: K, value: V) {
        if (values[key] !== value) return
        val remaining = (owners[key] ?: 1) - 1
        if (remaining <= 0) {
            values.remove(key)
            owners.remove(key)
        } else {
            owners[key] = remaining
        }
    }
}

internal fun isTouchFirstUi(hasLeanback: Boolean, hasTelevision: Boolean): Boolean =
    !hasLeanback && !hasTelevision

internal fun touchCatalogWheelWidthDp(screenWidthDp: Int, touchUi: Boolean): Float {
    val scaled = 268f * screenWidthDp / 1920f
    return if (touchUi) scaled.coerceAtLeast(132f) else scaled
}

/**
 * Poster columns for touch screens. A fixed count wastes a landscape phone
 * (three poster-width cards across 890dp); the count grows with the available
 * width instead, while portrait keeps its familiar three columns.
 */
internal fun touchPosterColumns(
    contentWidthDp: Float,
    gapDp: Float = 9f,
    posterMinWidthDp: Float = 108f,
): Int {
    if (contentWidthDp <= 0f) return 3
    val perColumn = (posterMinWidthDp + gapDp).coerceAtLeast(1f)
    return (((contentWidthDp + gapDp) / perColumn).toInt()).coerceAtLeast(3)
}

internal fun loginStacksVertically(touchUi: Boolean, widthDp: Int, heightDp: Int): Boolean =
    touchUi && (widthDp < 900 || heightDp < 600 || heightDp > widthDp)

/**
 * Below this available height the category manager compresses its header
 * (search and section tabs share a row, the facet rows collapse into one
 * horizontally scrollable strip) so the category list keeps a usable share of
 * the screen. This is the short-landscape phone case. */
internal const val CATEGORY_MANAGER_COMPACT_HEIGHT_DP = 480f

internal fun categoryManagerCompact(availableHeightDp: Float): Boolean =
    availableHeightDp < CATEGORY_MANAGER_COMPACT_HEIGHT_DP

internal fun pageStart(index: Int, pageSize: Int): Int {
    if (index < 0 || pageSize <= 0) return 0
    return (index / pageSize) * pageSize
}

internal fun gridRowStart(index: Int, columns: Int): Int {
    if (index < 0 || columns <= 0) return 0
    return (index / columns) * columns
}

internal fun gridPageStart(index: Int, columns: Int, rows: Int): Int {
    if (columns <= 0 || rows <= 0) return 0
    return pageStart(index, columns * rows)
}

/**
 * Foundation lazy containers only scroll enough to make a newly focused item
 * intersect the viewport. On TV that can leave the focus ring and part of the
 * card clipped. Align the row only when an item is not fully visible.
 */
internal suspend fun LazyGridState.ensureFocusedRowVisible(index: Int, columns: Int) {
    if (index < 0 || columns <= 0) return
    delay(24L)

    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
    val fullyVisible = item != null &&
        item.offset.y >= layout.viewportStartOffset &&
        item.offset.y + item.size.height <= layout.viewportEndOffset

    if (!fullyVisible) {
        scrollToItem(gridRowStart(index, columns))
    }
}

/**
 * Live TV is presented as fixed pages so the viewport always contains a
 * complete 4x4 set rather than partial rows above or below the focus.
 */
internal suspend fun LazyGridState.snapFocusedGridPage(index: Int, columns: Int, rows: Int) {
    if (index < 0 || columns <= 0 || rows <= 0) return
    delay(24L)

    val target = gridPageStart(index, columns, rows)
    if (firstVisibleItemIndex != target || firstVisibleItemScrollOffset != 0) {
        scrollToItem(target)
    }
}

/**
 * Category rails use fixed ten-button pages. Focus may move within a page
 * without shifting the rail; crossing a page boundary snaps to the next ten.
 */
internal suspend fun LazyListState.snapFocusedListPage(index: Int, pageSize: Int) {
    if (index < 0 || pageSize <= 0) return
    delay(24L)

    val target = pageStart(index, pageSize)
    if (firstVisibleItemIndex != target || firstVisibleItemScrollOffset != 0) {
        scrollToItem(target)
    }
}

internal suspend fun LazyListState.ensureFocusedItemVisible(index: Int) {
    if (index < 0) return
    delay(24L)

    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
    val fullyVisible = item != null &&
        item.offset >= layout.viewportStartOffset &&
        item.offset + item.size <= layout.viewportEndOffset

    if (!fullyVisible) scrollToItem(index)
}
