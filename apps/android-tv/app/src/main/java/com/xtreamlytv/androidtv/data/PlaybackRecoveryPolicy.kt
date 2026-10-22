package com.xtreamlytv.androidtv.data

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
        in 500..599 -> PlaybackFailureKind.TRANSIENT
        else -> PlaybackFailureKind.UNKNOWN
    }

    fun classifyPlayerErrorCode(code: Int): PlaybackFailureKind = when (code) {
        2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008 -> PlaybackFailureKind.TRANSIENT
        3001, 3002, 3003, 3004, 3005, 3006, 3007, 3008 -> PlaybackFailureKind.UNSUPPORTED
        4001, 4002, 4003, 4004, 4005 -> PlaybackFailureKind.UNSUPPORTED
        else -> PlaybackFailureKind.UNKNOWN
    }
}
