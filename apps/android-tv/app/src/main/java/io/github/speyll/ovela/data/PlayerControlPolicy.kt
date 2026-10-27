package io.github.speyll.ovela.data

/**
 * What the primary remote/touch action should do for the current media type.
 *
 * Live TV is a continuous feed: the primary action is a control-surface
 * gesture, never a transport toggle. On-demand content keeps the familiar
 * play/pause behaviour.
 *
 * This is intentionally a pure decision so the input mapping can be tested
 * without a player, a device, or a Compose host.
 */
enum class PrimaryPlayerAction {
    TOGGLE_PLAYBACK,
    SHOW_CONTROLS,
}

enum class VerticalPlayerAction {
    CHANGE_CHANNEL,
    SHOW_CONTROLS,
}

/** What a tap or pointer click on the picture should do. */
enum class SurfaceInteractionAction {
    CLOSE_OPTIONS,
    HIDE_CONTROLS,
    SHOW_CONTROLS,
}

object PlayerControlPolicy {
    fun primaryAction(isLive: Boolean): PrimaryPlayerAction =
        if (isLive) PrimaryPlayerAction.SHOW_CONTROLS else PrimaryPlayerAction.TOGGLE_PLAYBACK

    /** OK/Enter and MediaPlayPause share the same product rule. */
    fun mediaKeyAction(isLive: Boolean): PrimaryPlayerAction = primaryAction(isLive)

    /**
     * Up/Down only changes the feed on Live TV. On demand, direction keys must
     * never move to another movie or episode by accident; they reveal controls
     * like the other non-transport actions.
     */
    fun verticalAction(isLive: Boolean): VerticalPlayerAction =
        if (isLive) VerticalPlayerAction.CHANGE_CHANNEL else VerticalPlayerAction.SHOW_CONTROLS

    /**
     * A tap or click on the picture is a control-surface gesture, never a
     * transport toggle: showing the overlay on Live TV must not interrupt the
     * feed, and on-demand keeps play/pause on its dedicated control.
     *
     * [canHide] is false while a viewer has deliberately paused on-demand
     * content or while the options panel is open; the overlay then stays put
     * instead of hiding the only way back.
     */
    fun surfaceInteraction(
        controlsVisible: Boolean,
        optionsOpen: Boolean,
        canHide: Boolean,
    ): SurfaceInteractionAction = when {
        optionsOpen -> SurfaceInteractionAction.CLOSE_OPTIONS
        controlsVisible && canHide -> SurfaceInteractionAction.HIDE_CONTROLS
        else -> SurfaceInteractionAction.SHOW_CONTROLS
    }

    /**
     * Auto-hide only runs when a visible overlay has nothing to protect: a
     * paused feed or an open panel keeps its controls, so a focused control can
     * never vanish mid-navigation.
     */
    fun shouldAutoHide(
        controlsVisible: Boolean,
        playing: Boolean,
        optionsOpen: Boolean,
    ): Boolean = controlsVisible && playing && !optionsOpen
}
