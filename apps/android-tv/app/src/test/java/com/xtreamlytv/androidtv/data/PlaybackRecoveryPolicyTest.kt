package com.xtreamlytv.androidtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun triesAnotherCandidateBeforeRepeating() {
        val action = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(candidateIndex = 0, candidateCount = 2, retryAttempts = 0),
            PlaybackFailureKind.NOT_FOUND,
        )
        assertTrue(action is PlaybackRecoveryAction.NextCandidate)
        assertEquals(0L, (action as PlaybackRecoveryAction.NextCandidate).delayMs)
    }

    @Test
    fun retriesTransientFailuresOnlyWithinBudget() {
        val retry = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(candidateIndex = 0, candidateCount = 1, retryAttempts = 0, maxRetries = 2),
            PlaybackFailureKind.TRANSIENT,
        )
        assertTrue(retry is PlaybackRecoveryAction.RetryCandidate)
        val exhausted = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(candidateIndex = 0, candidateCount = 1, retryAttempts = 2, maxRetries = 2),
            PlaybackFailureKind.TRANSIENT,
        )
        assertTrue(exhausted is PlaybackRecoveryAction.GiveUp)
    }

    @Test
    fun permanentFailureWithoutFallbackStops() {
        val action = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(candidateIndex = 0, candidateCount = 1, retryAttempts = 0),
            PlaybackFailureKind.AUTH,
        )
        assertTrue(action is PlaybackRecoveryAction.GiveUp)
        assertTrue((action as PlaybackRecoveryAction.GiveUp).permanent)
    }
}
