package io.github.speyll.ovela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlPolicyTest {
    @Test
    fun livePrimaryActionShowsControlsAndNeverPauses() {
        assertEquals(PrimaryPlayerAction.SHOW_CONTROLS, PlayerControlPolicy.primaryAction(isLive = true))
        assertEquals(PrimaryPlayerAction.SHOW_CONTROLS, PlayerControlPolicy.mediaKeyAction(isLive = true))
    }

    @Test
    fun onDemandPrimaryActionTogglesPlayback() {
        assertEquals(PrimaryPlayerAction.TOGGLE_PLAYBACK, PlayerControlPolicy.primaryAction(isLive = false))
        assertEquals(PrimaryPlayerAction.TOGGLE_PLAYBACK, PlayerControlPolicy.mediaKeyAction(isLive = false))
    }

    @Test
    fun liveVerticalKeysChangeChannel() {
        assertEquals(VerticalPlayerAction.CHANGE_CHANNEL, PlayerControlPolicy.verticalAction(isLive = true))
    }

    @Test
    fun onDemandVerticalKeysNeverChangeItems() {
        assertEquals(VerticalPlayerAction.SHOW_CONTROLS, PlayerControlPolicy.verticalAction(isLive = false))
    }

    @Test
    fun tappingThePictureRevealsHiddenControls() {
        assertEquals(
            SurfaceInteractionAction.SHOW_CONTROLS,
            PlayerControlPolicy.surfaceInteraction(controlsVisible = false, optionsOpen = false, canHide = true),
        )
    }

    @Test
    fun tappingThePictureAgainHidesVisibleControls() {
        assertEquals(
            SurfaceInteractionAction.HIDE_CONTROLS,
            PlayerControlPolicy.surfaceInteraction(controlsVisible = true, optionsOpen = false, canHide = true),
        )
    }

    @Test
    fun tappingThePictureNeverRequestsATransportChange() {
        // The surface action enum has no play/pause member at all: a live tap
        // can only move the overlay, so it can never pause the feed.
        SurfaceInteractionAction.entries.forEach { action ->
            assertTrue(
                action == SurfaceInteractionAction.SHOW_CONTROLS ||
                    action == SurfaceInteractionAction.HIDE_CONTROLS ||
                    action == SurfaceInteractionAction.CLOSE_OPTIONS,
            )
        }
    }

    @Test
    fun pausedOnDemandKeepsItsControlsWhenThePictureIsTapped() {
        assertEquals(
            SurfaceInteractionAction.SHOW_CONTROLS,
            PlayerControlPolicy.surfaceInteraction(controlsVisible = true, optionsOpen = false, canHide = false),
        )
    }

    @Test
    fun tappingThePictureClosesAnOpenOptionsPanelFirst() {
        assertEquals(
            SurfaceInteractionAction.CLOSE_OPTIONS,
            PlayerControlPolicy.surfaceInteraction(controlsVisible = true, optionsOpen = true, canHide = true),
        )
    }

    @Test
    fun autoHideOnlyWhileVisiblePlayingAndPanelClosed() {
        assertTrue(PlayerControlPolicy.shouldAutoHide(controlsVisible = true, playing = true, optionsOpen = false))
        assertFalse(PlayerControlPolicy.shouldAutoHide(controlsVisible = false, playing = true, optionsOpen = false))
        assertFalse(PlayerControlPolicy.shouldAutoHide(controlsVisible = true, playing = false, optionsOpen = false))
        assertFalse(PlayerControlPolicy.shouldAutoHide(controlsVisible = true, playing = true, optionsOpen = true))
    }
}
