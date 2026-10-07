package com.xtreamlytv.androidtv.ui

import org.junit.Assert.assertEquals
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
    fun pagingHelpersGuardInvalidInputs() {
        assertEquals(0, gridRowStart(-1, 5))
        assertEquals(0, gridRowStart(4, 0))
        assertEquals(0, pageStart(-1, 10))
        assertEquals(0, pageStart(5, 0))
        assertEquals(0, gridPageStart(5, columns = 0, rows = 4))
        assertEquals(0, gridPageStart(5, columns = 4, rows = 0))
    }
}
