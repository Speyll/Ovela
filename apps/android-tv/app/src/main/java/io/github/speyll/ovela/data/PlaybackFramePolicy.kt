package io.github.speyll.ovela.data

/**
 * Frame-level health of a single player instance.
 *
 * Every input is an explicit value supplied by the caller: there is no clock,
 * no player, and no coroutine in here, so decisions are deterministic and can
 * be unit tested with plain numbers.
 *
 * A video *track* and a rendered *frame* are deliberately separate signals. A
 * stream can advertise a decodable video track and still never put a picture on
 * screen, so track presence alone must not be treated as evidence of video, and
 * neither must advancing media position or audible audio.
 */
data class PlaybackFrameState(
    /** A video frame has actually been rendered by the player. */
    val hasRenderedFrame: Boolean = false,
    /** The player reports at least one supported video track. */
    val hasVideoTrack: Boolean = false,
    /** Advancing playback time during which no video track existed at all. */
    val advancingWithoutTrackMs: Long = 0L,
    /** Advancing playback time during which no frame was rendered. */
    val advancingWithoutFrameMs: Long = 0L,
    /** Elapsed time the VOD resume is still waiting for video, or null when not waiting. */
    val resumePendingMs: Long? = null,
    /** Playback already reached the end of the item. */
    val ended: Boolean = false,
    /** A recovery transition is already in flight for this player instance. */
    val recovering: Boolean = false,
    /**
     * Playback is intentionally paused by the viewer, or the app is not in the
     * foreground. Watchdog decisions must never start playback on their own in
     * this state.
     */
    val paused: Boolean = false,
)

sealed interface PlaybackFrameAction {
    /** Do nothing; the existing movement watchdogs own this state. */
    data object Wait : PlaybackFrameAction

    /** Stop waiting for the video-track callback and resume at the saved position. */
    data object AbandonResume : PlaybackFrameAction

    /** Sustained advancing playback without a picture; fail over. */
    data class RecoverNoPicture(val detail: String) : PlaybackFrameAction
}

/**
 * Decides whether playback is running without a picture.
 *
 * The grace periods are intentionally generous: many live streams emit audio
 * for a moment before the video track is configured, and a slow TV decoder must
 * not be treated as a failure. Only *continuous* advancing playback reaches a
 * deadline, so a paused or buffering player never accumulates budget.
 *
 * Two distinct black-screen causes are covered:
 *  - no video track at all (audio-only radio, or a provider that drops video),
 *    failing over sooner;
 *  - a supported video track that never renders a frame (decoder or surface
 *    failure), given a longer, but still bounded, deadline.
 */
object PlaybackFramePolicy {
    /** Advancing playback with no video track at all before failing over. */
    const val NO_TRACK_GRACE_MS = 12_000L

    /**
     * Advancing playback with a configured video track but no rendered frame.
     * Longer than [NO_TRACK_GRACE_MS] because decoder configuration and the
     * first key frame legitimately take time on weak hardware.
     */
    const val NO_FRAME_GRACE_MS = 20_000L

    /** How long a VOD resume may wait for video before continuing anyway. */
    const val RESUME_GRACE_MS = 8_000L

    const val NO_TRACK_DETAIL = "Audio is playing but the stream has no video track."
    const val NO_FRAME_DETAIL = "The stream is playing but no video picture was rendered."

    fun decide(state: PlaybackFrameState): PlaybackFrameAction {
        // A paused player never accumulates advancing time, and must not be
        // resumed or failed over behind the viewer's back.
        if (state.ended || state.recovering || state.paused) return PlaybackFrameAction.Wait

        val resumePendingMs = state.resumePendingMs
        if (resumePendingMs != null) {
            // The seek only needs the decoder configured, which a video track
            // already proves. Waiting for a *frame* here would be wrong: the
            // seek itself is usually what produces the first frame.
            if (state.hasVideoTrack || state.hasRenderedFrame) return PlaybackFrameAction.AbandonResume
            return if (resumePendingMs >= RESUME_GRACE_MS) {
                PlaybackFrameAction.AbandonResume
            } else {
                PlaybackFrameAction.Wait
            }
        }

        if (state.hasRenderedFrame) return PlaybackFrameAction.Wait
        if (!state.hasVideoTrack && state.advancingWithoutTrackMs >= NO_TRACK_GRACE_MS) {
            return PlaybackFrameAction.RecoverNoPicture(NO_TRACK_DETAIL)
        }
        if (state.advancingWithoutFrameMs >= NO_FRAME_GRACE_MS) {
            return PlaybackFrameAction.RecoverNoPicture(NO_FRAME_DETAIL)
        }
        // Without any advancing position the startup watchdog owns the state.
        return PlaybackFrameAction.Wait
    }
}

/**
 * One-shot guard for the VOD resume seek.
 *
 * The saved [targetMs] is kept for the lifetime of the wait so abandoning it
 * still seeks to the saved position: resume never restarts from zero and the
 * stored progress is never discarded. Only the first caller wins, which keeps
 * the player's track callback and the frame watchdog from seeking twice.
 *
 * Both callers run on the player's application thread, so no synchronization is
 * required; the flag is still a plain value to keep the type testable offline.
 */
class ResumeWait(val targetMs: Long) {
    var isPending: Boolean = targetMs > 0L
        private set

    fun takeIfPending(): Boolean {
        if (!isPending) return false
        isPending = false
        return true
    }
}
