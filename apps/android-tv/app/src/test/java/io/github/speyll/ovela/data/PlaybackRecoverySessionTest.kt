package io.github.speyll.ovela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoverySessionTest {
    @Test
    fun exposesManualRetryAfterAutomaticRecoveryIsExhausted() {
        var session = PlaybackRecoverySession(candidateCount = 1)

        repeat(2) {
            val transition = session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 2)
            assertTrue(transition.action is PlaybackRecoveryAction.RetryCandidate)
            session = transition.session
        }
        val exhausted = session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 2)
        assertTrue(exhausted.action is PlaybackRecoveryAction.GiveUp)
        assertTrue(exhausted.session.exhausted)
        assertTrue(exhausted.session.manualRetryAvailable)

        val restarted = exhausted.session.retryFromStart()
        assertFalse(restarted.exhausted)
        assertEquals(0, restarted.candidateIndex)
        assertEquals(0, restarted.currentRetryAttempts)
    }

    @Test
    fun lastCandidateStartsWithFreshRetryBudget() {
        var session = PlaybackRecoverySession(candidateCount = 2)
        val advance = session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 1)
        assertTrue(advance.action is PlaybackRecoveryAction.NextCandidate)
        assertEquals(1, advance.session.candidateIndex)
        assertEquals(0, advance.session.currentRetryAttempts)

        val retry = advance.session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 1)
        assertTrue(retry.action is PlaybackRecoveryAction.RetryCandidate)
        assertEquals(1, retry.session.currentRetryAttempts)

        val exhausted = retry.session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 1)
        assertTrue(exhausted.action is PlaybackRecoveryAction.GiveUp)
        assertTrue(exhausted.session.exhausted)
    }

    @Test
    fun manualRetryStartsAgainFromFirstCandidate() {
        var session = PlaybackRecoverySession(candidateCount = 2)
        session = session.onFailure(PlaybackFailureKind.NOT_FOUND, maxRetries = 1).session
        assertEquals(1, session.candidateIndex)
        session = session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 1).session
        val terminal = session.onFailure(PlaybackFailureKind.TRANSIENT, maxRetries = 1)
        assertTrue(terminal.action is PlaybackRecoveryAction.GiveUp)

        val restarted = terminal.session.retryFromStart()
        assertEquals(0, restarted.candidateIndex)
        assertEquals(0, restarted.currentRetryAttempts)
        assertFalse(restarted.exhausted)
    }
}
