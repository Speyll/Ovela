package io.github.speyll.ovela.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLayoutSupportTest {
    @Test
    fun gridRowStartAlignsAnyCardToItsRow() {
        assertEquals(0, gridRowStart(0, 5))
        assertEquals(0, gridRowStart(4, 5))
        assertEquals(5, gridRowStart(5, 5))
        assertEquals(10, gridRowStart(13, 5))
    }

    @Test
    fun pageStartAlignsCategoryPagesToTenButtons() {
        assertEquals(0, pageStart(0, 10))
        assertEquals(0, pageStart(9, 10))
        assertEquals(10, pageStart(10, 10))
        assertEquals(20, pageStart(29, 10))
    }

    @Test
    fun gridPageStartAlignsLiveTvToFourByFourPages() {
        assertEquals(0, gridPageStart(0, columns = 4, rows = 4))
        assertEquals(0, gridPageStart(15, columns = 4, rows = 4))
        assertEquals(16, gridPageStart(16, columns = 4, rows = 4))
        assertEquals(32, gridPageStart(47, columns = 4, rows = 4))
    }

    @Test
    fun touchUiIsOnlyUsedOutsideLeanbackDevices() {
        assertTrue(isTouchFirstUi(hasLeanback = false, hasTelevision = false))
        assertFalse(isTouchFirstUi(hasLeanback = true, hasTelevision = false))
        assertFalse(isTouchFirstUi(hasLeanback = false, hasTelevision = true))
    }

    @Test
    fun phoneCatalogWheelKeepsATouchableMinimumWidth() {
        assertEquals(132f, touchCatalogWheelWidthDp(screenWidthDp = 411, touchUi = true), 0.01f)
        assertEquals(268f, touchCatalogWheelWidthDp(screenWidthDp = 1920, touchUi = true), 0.01f)
        assertEquals(268f * 411f / 1920f, touchCatalogWheelWidthDp(screenWidthDp = 411, touchUi = false), 0.01f)
    }

    @Test
    fun phoneLoginStacksVerticallyButTelevisionDoesNot() {
        assertTrue(loginStacksVertically(touchUi = true, widthDp = 411, heightDp = 891))
        assertTrue(loginStacksVertically(touchUi = true, widthDp = 891, heightDp = 411))
        assertFalse(loginStacksVertically(touchUi = true, widthDp = 1280, heightDp = 800))
        assertFalse(loginStacksVertically(touchUi = false, widthDp = 411, heightDp = 891))
    }

    @Test
    fun touchPosterColumnsGrowWithAvailableWidth() {
        // Phone portrait keeps its familiar three columns.
        assertEquals(3, touchPosterColumns(contentWidthDp = 365f))
        // A landscape phone fits a dense poster wall instead of three huge cards.
        assertEquals(7, touchPosterColumns(contentWidthDp = 863f))
        // Narrow phones never drop below three columns.
        assertEquals(3, touchPosterColumns(contentWidthDp = 292f))
        assertEquals(3, touchPosterColumns(contentWidthDp = 0f))
        // Tablets land between the two.
        assertEquals(5, touchPosterColumns(contentWidthDp = 620f))
    }

    @Test
    fun categoryManagerCompressesOnShortLandscapeScreens() {
        // Phone landscape with chrome removed leaves ~360dp: compact header.
        assertTrue(categoryManagerCompact(availableHeightDp = 360f))
        assertTrue(categoryManagerCompact(availableHeightDp = 479f))
        // Phone portrait and any television leave room for the full header.
        assertFalse(categoryManagerCompact(availableHeightDp = 480f))
        assertFalse(categoryManagerCompact(availableHeightDp = 777f))
        assertFalse(categoryManagerCompact(availableHeightDp = 1080f))
    }

    @Test
    fun pagingHelpersGuardInvalidInputs() {
        assertEquals(0, gridRowStart(-1, 5))
        assertEquals(0, gridRowStart(4, 0))
        assertEquals(0, pageStart(-1, 10))
        assertEquals(0, pageStart(5, 0))
        assertEquals(0, gridPageStart(5, columns = 0, rows = 4))
        assertEquals(0, gridPageStart(5, columns = 4, rows = 0))
    }
}
