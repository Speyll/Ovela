package io.github.speyll.ovela.data

import androidx.media3.common.PlaybackException
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

    @Test
    fun classifiesHttpStatusPrecisely() {
        assertEquals(PlaybackFailureKind.AUTH, PlaybackRecoveryPolicy.classifyHttpStatus(401))
        assertEquals(PlaybackFailureKind.FORBIDDEN, PlaybackRecoveryPolicy.classifyHttpStatus(403))
        assertEquals(PlaybackFailureKind.NOT_FOUND, PlaybackRecoveryPolicy.classifyHttpStatus(404))
        assertEquals(PlaybackFailureKind.NOT_FOUND, PlaybackRecoveryPolicy.classifyHttpStatus(410))
        assertEquals(PlaybackFailureKind.UNSUPPORTED, PlaybackRecoveryPolicy.classifyHttpStatus(415))
        assertEquals(PlaybackFailureKind.TRANSIENT, PlaybackRecoveryPolicy.classifyHttpStatus(408))
        assertEquals(PlaybackFailureKind.TRANSIENT, PlaybackRecoveryPolicy.classifyHttpStatus(425))
        assertEquals(PlaybackFailureKind.TRANSIENT, PlaybackRecoveryPolicy.classifyHttpStatus(429))
        assertEquals(PlaybackFailureKind.TRANSIENT, PlaybackRecoveryPolicy.classifyHttpStatus(500))
        assertEquals(PlaybackFailureKind.TRANSIENT, PlaybackRecoveryPolicy.classifyHttpStatus(599))
        assertEquals(PlaybackFailureKind.UNKNOWN, PlaybackRecoveryPolicy.classifyHttpStatus(400))
    }

    @Test
    fun httpStatusTakesPrecedenceOverGenericBadStatusPlayerCode() {
        assertEquals(
            PlaybackFailureKind.AUTH,
            PlaybackRecoveryPolicy.classifyPlaybackFailure(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                401,
            ),
        )
        assertEquals(
            PlaybackFailureKind.NOT_FOUND,
            PlaybackRecoveryPolicy.classifyPlaybackFailure(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                404,
            ),
        )
        assertEquals(
            PlaybackFailureKind.UNKNOWN,
            PlaybackRecoveryPolicy.classifyPlaybackFailure(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                null,
            ),
        )
    }

    @Test
    fun classifiesActionablePlayerErrorsAsPermanent() {
        assertEquals(
            PlaybackFailureKind.UNSUPPORTED,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE),
        )
        assertEquals(
            PlaybackFailureKind.NOT_FOUND,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
        )
        assertEquals(
            PlaybackFailureKind.FORBIDDEN,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_NO_PERMISSION),
        )
        assertEquals(
            PlaybackFailureKind.UNSUPPORTED,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED),
        )
        assertEquals(
            PlaybackFailureKind.AUTH,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED),
        )
        assertEquals(
            PlaybackFailureKind.UNSUPPORTED,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED),
        )
    }

    @Test
    fun keepsNetworkFailuresRecoverable() {
        assertEquals(
            PlaybackFailureKind.TRANSIENT,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            PlaybackFailureKind.TRANSIENT,
            PlaybackRecoveryPolicy.classifyPlayerErrorCode(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
    }
}
