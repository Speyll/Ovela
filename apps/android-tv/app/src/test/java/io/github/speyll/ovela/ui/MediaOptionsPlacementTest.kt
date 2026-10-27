package io.github.speyll.ovela.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOptionsPlacementTest {
    @Test
    fun anchorsAboveTriggerAndRightAlignsInLandscape() {
        assertEquals(
            MediaOptionsPlacement(x = 1668, y = 592),
            mediaOptionsPlacement(
                anchorRight = 1920,
                anchorTop = 900,
                viewportWidth = 1920,
                viewportHeight = 1080,
                panelWidth = 236,
                panelHeight = 300,
                margin = 16,
                gap = 8,
            ),
        )
    }

    @Test
    fun clampsToLeftAndTopSafeArea() {
        assertEquals(
            MediaOptionsPlacement(x = 16, y = 16),
            mediaOptionsPlacement(
                anchorRight = 100,
                anchorTop = 100,
                viewportWidth = 1920,
                viewportHeight = 1080,
                panelWidth = 236,
                panelHeight = 300,
                margin = 16,
                gap = 8,
            ),
        )
    }

    @Test
    fun clampsToRightAndBottomWhenThePanelWouldOverflow() {
        assertEquals(
            MediaOptionsPlacement(x = 1668, y = 764),
            mediaOptionsPlacement(
                anchorRight = 1920,
                anchorTop = 1080,
                viewportWidth = 1920,
                viewportHeight = 1080,
                panelWidth = 236,
                panelHeight = 300,
                margin = 16,
                gap = 8,
            ),
        )
    }

    @Test
    fun worksInPortraitAndWithoutAMeasuredTrigger() {
        assertEquals(
            MediaOptionsPlacement(x = 828, y = 1492),
            mediaOptionsPlacement(
                anchorRight = 1064,
                anchorTop = 1800,
                viewportWidth = 1080,
                viewportHeight = 1920,
                panelWidth = 236,
                panelHeight = 300,
                margin = 16,
                gap = 8,
            ),
        )
        assertEquals(
            MediaOptionsPlacement(x = 68, y = 16),
            mediaOptionsPlacement(
                anchorRight = null,
                anchorTop = null,
                viewportWidth = 320,
                viewportHeight = 240,
                panelWidth = 236,
                panelHeight = 300,
                margin = 16,
                gap = 8,
            ),
        )
    }
}
