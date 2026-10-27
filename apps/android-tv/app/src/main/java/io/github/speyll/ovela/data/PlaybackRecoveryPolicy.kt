package io.github.speyll.ovela.data

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.util.Collections
import java.util.IdentityHashMap

enum class PlaybackFailureKind {
    TRANSIENT,
    AUTH,
    FORBIDDEN,
    NOT_FOUND,
    UNSUPPORTED,
    UNKNOWN,
}

data class PlaybackRecoveryState(
    val candidateIndex: Int,
    val candidateCount: Int,
    val retryAttempts: Int,
    val maxRetries: Int = 2,
)

sealed interface PlaybackRecoveryAction {
    data class NextCandidate(val delayMs: Long) : PlaybackRecoveryAction
    data class RetryCandidate(val delayMs: Long) : PlaybackRecoveryAction
    data class GiveUp(val permanent: Boolean) : PlaybackRecoveryAction
}

data class PlaybackRecoveryTransition(
    val session: PlaybackRecoverySession,
    val action: PlaybackRecoveryAction,
)

data class PlaybackRecoverySession(
    val candidateCount: Int,
    val candidateIndex: Int = 0,
    val retryAttemptsByCandidate: Map<Int, Int> = emptyMap(),
    val exhausted: Boolean = false,
) {
    val currentRetryAttempts: Int
        get() = retryAttemptsByCandidate[candidateIndex] ?: 0

    val manualRetryAvailable: Boolean
        get() = exhausted

    fun onFailure(kind: PlaybackFailureKind, maxRetries: Int = 2): PlaybackRecoveryTransition {
        if (exhausted) {
            return PlaybackRecoveryTransition(this, PlaybackRecoveryAction.GiveUp(permanent = false))
        }
        val action = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(
                candidateIndex = candidateIndex,
                candidateCount = candidateCount,
                retryAttempts = currentRetryAttempts,
                maxRetries = maxRetries,
            ),
            kind,
        )
        val next = when (action) {
            is PlaybackRecoveryAction.NextCandidate -> copy(
                candidateIndex = candidateIndex + 1,
                exhausted = false,
            )
            is PlaybackRecoveryAction.RetryCandidate -> copy(
                retryAttemptsByCandidate = retryAttemptsByCandidate +
                    (candidateIndex to currentRetryAttempts + 1),
                exhausted = false,
            )
            is PlaybackRecoveryAction.GiveUp -> copy(exhausted = true)
        }
        return PlaybackRecoveryTransition(next, action)
    }

    fun retryFromStart(): PlaybackRecoverySession = copy(
        candidateIndex = 0,
        retryAttemptsByCandidate = emptyMap(),
        exhausted = false,
    )
}

object PlaybackRecoveryPolicy {
    fun decide(state: PlaybackRecoveryState, kind: PlaybackFailureKind): PlaybackRecoveryAction {
        val permanent = kind == PlaybackFailureKind.AUTH ||
            kind == PlaybackFailureKind.FORBIDDEN ||
            kind == PlaybackFailureKind.NOT_FOUND ||
            kind == PlaybackFailureKind.UNSUPPORTED
        val delay = if (permanent) 0L else (250L shl state.retryAttempts.coerceAtMost(3)).coerceAtMost(2_000L)
        if (state.candidateIndex + 1 < state.candidateCount) return PlaybackRecoveryAction.NextCandidate(delay)
        if (!permanent && state.retryAttempts < state.maxRetries) return PlaybackRecoveryAction.RetryCandidate(delay)
        return PlaybackRecoveryAction.GiveUp(permanent)
    }

    fun classifyHttpStatus(status: Int): PlaybackFailureKind = when (status) {
        401 -> PlaybackFailureKind.AUTH
        403 -> PlaybackFailureKind.FORBIDDEN
        404, 410 -> PlaybackFailureKind.NOT_FOUND
        415 -> PlaybackFailureKind.UNSUPPORTED
        408, 425, 429 -> PlaybackFailureKind.TRANSIENT
        in 500..599 -> PlaybackFailureKind.TRANSIENT
        else -> PlaybackFailureKind.UNKNOWN
    }

    fun classifyPlayerErrorCode(code: Int): PlaybackFailureKind = when (code) {
        PlaybackException.ERROR_CODE_PERMISSION_DENIED,
        PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION,
        -> PlaybackFailureKind.FORBIDDEN

        PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED,
        PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
        PlaybackException.ERROR_CODE_CONCURRENT_STREAM_LIMIT,
        -> PlaybackFailureKind.AUTH

        PlaybackException.ERROR_CODE_NOT_SUPPORTED,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        in 3001..3008,
        in 4001..4006,
        -> PlaybackFailureKind.UNSUPPORTED

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailureKind.NOT_FOUND
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackFailureKind.FORBIDDEN
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackFailureKind.TRANSIENT

        else -> PlaybackFailureKind.UNKNOWN
    }

    fun classifyPlaybackFailure(errorCode: Int, httpStatus: Int?): PlaybackFailureKind =
        httpStatus?.let(::classifyHttpStatus) ?: classifyPlayerErrorCode(errorCode)

    fun classifyPlaybackException(error: PlaybackException): PlaybackFailureKind =
        classifyPlaybackFailure(error.errorCode, findHttpStatus(error.cause))

    private fun findHttpStatus(error: Throwable?): Int? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current = error
        var depth = 0
        while (current != null && depth < 8 && seen.add(current)) {
            if (current is HttpDataSource.InvalidResponseCodeException &&
                current.responseCode in 100..599
            ) {
                return current.responseCode
            }
            current = current.cause
            depth += 1
        }
        return null
    }
}
