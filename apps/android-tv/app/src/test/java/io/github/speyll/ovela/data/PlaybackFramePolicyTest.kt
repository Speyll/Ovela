package io.github.speyll.ovela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFramePolicyTest {

    private fun noFrameDeadline() = PlaybackFrameAction.RecoverNoPicture(PlaybackFramePolicy.NO_FRAME_DETAIL)
    private fun noTrackDeadline() = PlaybackFrameAction.RecoverNoPicture(PlaybackFramePolicy.NO_TRACK_DETAIL)

    @Test
    fun aSupportedVideoTrackWithoutARenderedFrameStillRecovers() {
        // The regression this fix targets: a decodable video track exists, media
        // position advances, audio is audible, and no frame ever renders.
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = true,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS,
            ),
        )

        assertEquals(noFrameDeadline(), action)
    }

    @Test
    fun aTrackWithoutAFrameWaitsInsideTheFrameGrace() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = true,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS - 1L,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun theFrameDeadlineIsLongerThanTheNoTrackDeadline() {
        // Conservative ordering: a configured decoder gets more time than a
        // stream that never even announced video.
        assertTrue(PlaybackFramePolicy.NO_FRAME_GRACE_MS > PlaybackFramePolicy.NO_TRACK_GRACE_MS)
    }

    @Test
    fun aFrameArrivingBeforeTheDeadlineKeepsPlaybackAlive() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = true,
                hasVideoTrack = true,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS * 10L,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS * 10L,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun aFrameArrivingMidPlaybackStopsBothBudgets() {
        var withoutFrame = 0L
        var withoutTrack = 0L
        var recoveries = 0

        // Mirrors PlayerScreen: each advancing tick adds its elapsed time while
        // no frame has rendered, and the frame resets both budgets.
        repeat(40) { tick ->
            val renderedFrame = tick >= 3 // frame arrives at ~4.5s
            if (renderedFrame) {
                withoutFrame = 0L
                withoutTrack = 0L
            } else {
                withoutFrame += 1_500L
                withoutTrack += 1_500L
            }
            val action = PlaybackFramePolicy.decide(
                PlaybackFrameState(
                    hasRenderedFrame = renderedFrame,
                    hasVideoTrack = true,
                    advancingWithoutTrackMs = withoutTrack,
                    advancingWithoutFrameMs = withoutFrame,
                ),
            )
            if (action is PlaybackFrameAction.RecoverNoPicture) recoveries += 1
        }

        assertEquals(0, recoveries)
    }

    @Test
    fun audioOnlyPlaybackWithoutAnyVideoTrackRecoversSooner() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = false,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS,
            ),
        )

        assertEquals(noTrackDeadline(), action)
    }

    @Test
    fun audioOnlyPlaybackIsNotFailedImmediately() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = false,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS - 1L,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS - 1L,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun advancingPositionOverTimeCannotMaskABlackScreen() {
        var withoutFrame = 0L
        var recoveries = 0
        var loopRunning = true

        repeat(24) {
            if (!loopRunning) return@repeat
            withoutFrame += 1_500L
            val action = PlaybackFramePolicy.decide(
                PlaybackFrameState(
                    hasRenderedFrame = false,
                    hasVideoTrack = true,
                    advancingWithoutFrameMs = withoutFrame,
                ),
            )
            if (action is PlaybackFrameAction.RecoverNoPicture) {
                recoveries += 1
                loopRunning = false // watchdog stops after handing off to recovery
            }
        }

        assertEquals(1, recoveries)
    }

    @Test
    fun noAdvanceIsLeftToTheStartupWatchdog() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = true,
                advancingWithoutFrameMs = 0L,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun anExplicitPauseSuppressesBothDeadlines() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = true,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS * 2L,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS * 2L,
                paused = true,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun backgroundedPlaybackIsNeitherResumedNorFailedOver() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasRenderedFrame = false,
                hasVideoTrack = false,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS * 2L,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS * 2L,
                resumePendingMs = PlaybackFramePolicy.RESUME_GRACE_MS * 2L,
                paused = true,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun endedPlaybackIsNotFailedOver() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasVideoTrack = true,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS,
                ended = true,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun aRecoveryAlreadyInFlightIsNotDuplicated() {
        // A stale tick from a superseded candidate must stay inert.
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasVideoTrack = true,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS,
                recovering = true,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun resumeWaitsWithinItsGracePeriod() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(resumePendingMs = PlaybackFramePolicy.RESUME_GRACE_MS - 1L),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun resumeLeavesTheWaitingStateAtItsDeadline() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(resumePendingMs = PlaybackFramePolicy.RESUME_GRACE_MS),
        )

        assertEquals(PlaybackFrameAction.AbandonResume, action)
    }

    @Test
    fun resumeProceedsAsSoonAsAVideoTrackIsConfigured() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(hasVideoTrack = true, resumePendingMs = 1L),
        )

        assertEquals(PlaybackFrameAction.AbandonResume, action)
    }

    @Test
    fun resumeNeverFailsOverWhileItIsStillWaiting() {
        val action = PlaybackFramePolicy.decide(
            PlaybackFrameState(
                hasVideoTrack = false,
                advancingWithoutTrackMs = PlaybackFramePolicy.NO_TRACK_GRACE_MS,
                advancingWithoutFrameMs = PlaybackFramePolicy.NO_FRAME_GRACE_MS,
                resumePendingMs = 1L,
            ),
        )

        assertEquals(PlaybackFrameAction.Wait, action)
    }

    @Test
    fun resumeWaitIsOneShotAndKeepsTheSavedPosition() {
        val wait = ResumeWait(targetMs = 640_000L)

        assertTrue(wait.isPending)
        assertTrue(wait.takeIfPending())
        assertFalse(wait.isPending)
        assertFalse(wait.takeIfPending())
        // Abandoning the wait must still seek to the saved position, never to 0.
        assertEquals(640_000L, wait.targetMs)
    }

    @Test
    fun resumeWaitStaysInertWithoutASavedPosition() {
        val wait = ResumeWait(targetMs = 0L)

        assertFalse(wait.isPending)
        assertFalse(wait.takeIfPending())
        assertEquals(0L, wait.targetMs)
    }
}
