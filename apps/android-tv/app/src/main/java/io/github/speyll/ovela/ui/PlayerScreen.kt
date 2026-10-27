package io.github.speyll.ovela.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.speyll.ovela.BuildConfig
import io.github.speyll.ovela.R
import io.github.speyll.ovela.data.PlaybackFailureKind
import io.github.speyll.ovela.data.PlaybackRecoveryAction
import io.github.speyll.ovela.data.PlaybackRecoveryPolicy
import io.github.speyll.ovela.data.PlaybackRecoveryState
import io.github.speyll.ovela.data.PlayerControlPolicy
import io.github.speyll.ovela.data.PrimaryPlayerAction
import io.github.speyll.ovela.data.SurfaceInteractionAction
import io.github.speyll.ovela.data.VerticalPlayerAction
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.PlayerRequest
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

private const val VOD_AUDIO_ADVANCE_MS = 280
private const val TAG = "OvelaPlayer"

/** Idle time before the overlay retracts while playback continues. */
private const val CONTROLS_AUTO_HIDE_MS = 4_200L

/** Shared horizontal content inset for every player overlay row. */
private val PLAYER_TOUCH_INSET = 16.dp
private val PLAYER_TV_INSET = 36.dp

/** Remote/touch seek step for on-demand content. */
private const val SEEK_STEP_MS = 30_000L

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlayerRequest,
    favorite: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val subtitleOptionFocus = remember(request.item.id) { FocusRequester() }
    val favoriteOptionFocus = remember(request.item.id) { FocusRequester() }
    var candidateIndex by remember(request.item.id) { mutableIntStateOf(0) }
    var retrySerial by remember(request.item.id) { mutableIntStateOf(0) }
    var recoveryAttempts by remember(request.item.id) { mutableIntStateOf(0) }
    var recovering by remember(request.item.id) { mutableStateOf(false) }
    var renderedFirstFrame by remember(request.item.id, candidateIndex, retrySerial) { mutableStateOf(false) }
    val recoveryScope = rememberCoroutineScope()
    var controlsVisible by remember(request.item.id) { mutableStateOf(true) }
    var interactionTick by remember(request.item.id) { mutableIntStateOf(0) }
    var optionsOpen by remember(request.item.id) { mutableStateOf(false) }
    var playing by remember(request.item.id) { mutableStateOf(true) }
    var position by remember(request.item.id) { mutableLongStateOf(request.startPositionMs) }
    var duration by remember(request.item.id) { mutableLongStateOf(0L) }
    // Always-current resume anchor. It is a plain holder, not Compose state, so
    // recovery retries resume from the real position even while the overlay is
    // hidden and [position] is not being republished to the UI.
    val resumeAnchor = remember(request.item.id) { longArrayOf(request.startPositionMs) }
    var errorMessage by remember(request.item.id) { mutableStateOf<String?>(null) }
    var subtitlesAvailable by remember(request.item.id) { mutableStateOf(false) }
    var subtitlesEnabled by remember(request.item.id) { mutableStateOf(false) }
    // Aspect ratio of the picture that is actually on screen. A pillarboxed
    // stream leaves black side bars, and controls sitting in those bars read as
    // detached from the video; the overlay uses this to stay on the picture.
    var videoAspect by remember(request.item.id) { mutableStateOf(0f) }

    val isLive = request.item.type == ContentType.LIVE

    fun recover(kind: PlaybackFailureKind, detail: String? = null) {
        if (recovering) return
        val action = PlaybackRecoveryPolicy.decide(
            PlaybackRecoveryState(
                candidateIndex = candidateIndex,
                candidateCount = request.urlCandidates.size,
                retryAttempts = recoveryAttempts,
                maxRetries = 3,
            ),
            kind,
        )
        when (action) {
            is PlaybackRecoveryAction.NextCandidate -> {
                if (candidateIndex + 1 >= request.urlCandidates.size) {
                    errorMessage = detail ?: "Unable to play this stream."
                    recovering = false
                    return
                }
                recovering = true
                errorMessage = null
                recoveryScope.launch {
                    delay(action.delayMs)
                    recoveryAttempts += 1
                    retrySerial = 0
                    candidateIndex += 1
                    recovering = false
                }
            }
            is PlaybackRecoveryAction.RetryCandidate -> {
                recovering = true
                errorMessage = null
                recoveryScope.launch {
                    delay(action.delayMs)
                    recoveryAttempts += 1
                    retrySerial += 1
                    recovering = false
                }
            }
            is PlaybackRecoveryAction.GiveUp -> {
                recovering = false
                errorMessage = when (kind) {
                    PlaybackFailureKind.AUTH -> "The provider rejected access to this stream."
                    PlaybackFailureKind.FORBIDDEN -> "This stream is not authorized."
                    PlaybackFailureKind.NOT_FOUND -> "This stream is no longer available."
                    PlaybackFailureKind.UNSUPPORTED -> "This stream uses a format this device cannot play."
                    PlaybackFailureKind.TRANSIENT, PlaybackFailureKind.UNKNOWN ->
                        detail ?: "The stream did not become playable. Check the provider and try again."
                }
            }
        }
    }

    val player = remember(request.item.id, candidateIndex, retrySerial) {
        val renderersFactory = if (isLive) {
            DefaultRenderersFactory(context)
        } else {
            pcmVodRenderersFactory(context)
        }
        renderersFactory
            .setEnableDecoderFallback(true)
            .also { factory ->
                if (!isLive) {
                    factory.forceDisableMediaCodecAsynchronousQueueing()
                }
            }
        val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()
        val loadControl = if (isLive) createLiveLoadControl() else null
        val dataSourceFactory = if (isLive) {
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("Ovela/${BuildConfig.VERSION_NAME}")
        } else null
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl ?: DefaultLoadControl.Builder().build())
            .setMediaSourceFactory(
                if (isLive) {
                    HlsMediaSource.Factory(dataSourceFactory!!)
                } else {
                    DefaultMediaSourceFactory(
                        DefaultHttpDataSource.Factory()
                            .setConnectTimeoutMs(8000)
                            .setReadTimeoutMs(8000)
                            .setAllowCrossProtocolRedirects(true)
                            .setUserAgent("Ovela/${BuildConfig.VERSION_NAME}"),
                    )
                },
            )
            .build().apply {
                val candidate = request.urlCandidates.getOrElse(candidateIndex) { request.urlCandidates.first() }
                setMediaItem(MediaItem.fromUri(candidate))
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(audioOffloadPreferences)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                    .build()
                val resumePosition = resumeAnchor[0].coerceAtLeast(request.startPositionMs)
                val shouldResume = resumePosition > 0L && !isLive
                if (shouldResume) {
                    // Some MediaTek decoders retain an incorrect crop/aspect when
                    // their first decode begins at a mid-stream seek point. Allow
                    // the initial track format to configure the decoder, then seek
                    // before starting playback.
                    playWhenReady = false
                    addListener(object : Player.Listener {
                        private var resumePending = true

                        override fun onTracksChanged(tracks: Tracks) {
                            if (!resumePending || tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }) return
                            resumePending = false
                            removeListener(this)
                            seekTo(resumePosition)
                            playWhenReady = true
                        }
                    })
                } else {
                    playWhenReady = true
                }
                prepare()
                Log.i(TAG, "Player prepared for ${request.item.name} (live=$isLive)")
            }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) errorMessage = null
            }

            override fun onRenderedFirstFrame() {
                renderedFirstFrame = true
                Log.i(TAG, "First frame rendered for ${request.item.name}")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val aspect = if (videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    0f
                }
                videoAspect = if (aspect.isFinite() && aspect > 0f) aspect else 0f
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onTracksChanged(tracks: Tracks) {
                subtitlesAvailable = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_TEXT && group.isSupported
                }
                if (!subtitlesAvailable) subtitlesEnabled = false
            }

            override fun onPlayerError(error: PlaybackException) {
                val kind = PlaybackRecoveryPolicy.classifyPlaybackException(error)
                recover(kind, error.message ?: "Unable to play this stream.")
            }
        }
        player.addListener(listener)
        onDispose {
            if (!isLive) {
                onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.takeIf { it > 0L } ?: 0L)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        var resumeAfterStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterStart = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeAfterStart) {
                    player.play()
                    resumeAfterStart = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, candidateIndex, retrySerial) {
        val startedAt = SystemClock.elapsedRealtime()
        var lastAdvanceAt = startedAt
        var lastPosition = -1L
        var sawProgress = false
        var firstFrameObserved = false
        while (true) {
            delay(1500L)
            if (recovering) continue
            val now = SystemClock.elapsedRealtime()
            val position = player.currentPosition.coerceAtLeast(0L)
            if (player.isPlaying) {
                if (position != lastPosition) {
                    lastPosition = position
                    lastAdvanceAt = now
                    sawProgress = true
                }
            }
            if (renderedFirstFrame && !firstFrameObserved) {
                firstFrameObserved = true
                lastAdvanceAt = now
            }
            if (!renderedFirstFrame && !sawProgress && now - startedAt > 18_000L &&
                player.playWhenReady && player.playbackState != Player.STATE_ENDED
            ) {
                recover(PlaybackFailureKind.TRANSIENT, "The stream did not start. Check the provider connection.")
                break
            }
            if ((renderedFirstFrame || sawProgress) && now - lastAdvanceAt > 15_000L &&
                player.playWhenReady && player.playbackState != Player.STATE_ENDED
            ) {
                recover(PlaybackFailureKind.TRANSIENT, "The stream stopped responding. Reconnecting…")
                break
            }
        }
    }

    // The transport readout is only published while the overlay is on screen, so
    // a hidden overlay does not recompose the player every half second.
    LaunchedEffect(player) {
        var persistTicks = 0
        while (true) {
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            val currentDuration = player.duration.takeIf { it > 0L } ?: 0L
            resumeAnchor[0] = currentPosition
            if (controlsVisible) {
                position = currentPosition
                duration = currentDuration
            }
            persistTicks += 1
            if (persistTicks >= 10 && !isLive) {
                onProgress(currentPosition, currentDuration)
                persistTicks = 0
            }
            delay(500)
        }
    }

    // Auto-hide: only while something is actually playing. A paused VOD keeps
    // its controls, an open options panel keeps them, and every interaction
    // restarts the countdown instead of stacking timers. Cancelling this effect
    // (item change, options, pause) leaves no pending hide behind.
    LaunchedEffect(controlsVisible, playing, optionsOpen, interactionTick) {
        if (!PlayerControlPolicy.shouldAutoHide(controlsVisible, playing, optionsOpen)) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        controlsVisible = false
    }

    LaunchedEffect(optionsOpen, subtitlesAvailable) {
        if (optionsOpen) {
            delay(24L)
            val target = if (subtitlesAvailable) subtitleOptionFocus else favoriteOptionFocus
            runCatching { target.requestFocus() }
        }
    }

    fun showControls() {
        controlsVisible = true
        interactionTick += 1
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun closeOptions() {
        optionsOpen = false
        showControls()
        runCatching { focusRequester.requestFocus() }
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
        showControls()
    }

    fun seekToPosition(targetMs: Long) {
        player.seekTo(targetMs.coerceAtLeast(0L))
        showControls()
    }

    fun setSubtitles(enabled: Boolean) {
        if (!subtitlesAvailable) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        subtitlesEnabled = enabled
        showControls()
    }

    /**
     * One gesture for every surface: a tap, a Magic-Remote-style pointer click
     * or a mouse click on the picture. It never changes transport state; live
     * playback keeps running while the overlay appears.
     */
    fun onSurfaceInteraction() {
        // Never strand a paused viewer or an open panel without its controls.
        val canHide = playing || isLive
        when (PlayerControlPolicy.surfaceInteraction(controlsVisible, optionsOpen, canHide)) {
            SurfaceInteractionAction.CLOSE_OPTIONS -> closeOptions()
            SurfaceInteractionAction.HIDE_CONTROLS -> hideControls()
            SurfaceInteractionAction.SHOW_CONTROLS -> showControls()
        }
    }

    /** The primary remote action, resolved by the shared player policy. */
    fun onPrimaryAction() {
        when (PlayerControlPolicy.mediaKeyAction(isLive)) {
            PrimaryPlayerAction.TOGGLE_PLAYBACK -> togglePlayback()
            PrimaryPlayerAction.SHOW_CONTROLS -> showControls()
        }
    }

    BackHandler {
        if (optionsOpen) closeOptions() else onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (optionsOpen) {
                    return@onPreviewKeyEvent when {
                        event.key == Key.Back || event.key == Key.Menu -> {
                            closeOptions(); true
                        }
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CAPTIONS && subtitlesAvailable -> {
                            setSubtitles(!subtitlesEnabled)
                            true
                        }
                        else -> false
                    }
                }

                when {
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CAPTIONS && subtitlesAvailable -> {
                        setSubtitles(!subtitlesEnabled)
                        true
                    }
                    event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.MediaPlayPause ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY ||
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        onPrimaryAction()
                        true
                    }
                    // Rewind/forward stays available on both live and on-demand
                    // exactly as before; live never gets a seek bar in the UI.
                    event.key == Key.DirectionLeft -> {
                        seekBy(-SEEK_STEP_MS)
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        seekBy(SEEK_STEP_MS)
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        if (PlayerControlPolicy.verticalAction(isLive) == VerticalPlayerAction.CHANGE_CHANNEL) {
                            onPrevious()
                        } else {
                            showControls()
                        }
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        if (PlayerControlPolicy.verticalAction(isLive) == VerticalPlayerAction.CHANGE_CHANNEL) {
                            onNext()
                        } else {
                            showControls()
                        }
                        true
                    }
                    event.key == Key.Menu -> {
                        optionsOpen = true
                        showControls()
                        true
                    }
                    event.key == Key.Back -> {
                        onBack(); true
                    }
                    else -> {
                        showControls(); false
                    }
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    keepScreenOn = true
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // Transparent gesture layer. It sits under the overlay so every control
        // still receives its own clicks, and it owns no focus, so D-pad focus
        // stays on the player root.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(request.item.id) {
                    detectTapGestures(onTap = { onSurfaceInteraction() })
                },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerOverlay(
                request = request,
                favorite = favorite,
                isLive = isLive,
                videoAspect = videoAspect,
                playing = playing,
                position = position,
                duration = duration,
                subtitlesAvailable = subtitlesAvailable,
                subtitlesEnabled = subtitlesEnabled,
                onTogglePlayback = ::togglePlayback,
                onSeek = ::seekToPosition,
                onSeekBy = ::seekBy,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleFavorite = onToggleFavorite,
                onToggleSubtitles = { setSubtitles(!subtitlesEnabled) },
                onBack = onBack,
                onInteraction = ::showControls,
            )
        }

        if (optionsOpen) {
            PlaybackOptions(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // Same content inset as the overlay rows, sitting directly
                    // above the action row it belongs to.
                    .padding(
                        end = if (LocalTouchUi.current) PLAYER_TOUCH_INSET else PLAYER_TV_INSET,
                        bottom = if (LocalTouchUi.current) 66.dp else 82.dp,
                    ),
                favorite = favorite,
                subtitlesAvailable = subtitlesAvailable,
                subtitlesEnabled = subtitlesEnabled,
                subtitleFocus = subtitleOptionFocus,
                favoriteFocus = favoriteOptionFocus,
                onToggleSubtitles = { setSubtitles(!subtitlesEnabled) },
                onToggleFavorite = {
                    onToggleFavorite()
                    showControls()
                },
            )
        }

        if (recovering) {
            val colors = palette()
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.background3.copy(alpha = .88f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Reconnecting…", color = colors.text, fontSize = 11.5.sp)
            }
        }

        errorMessage?.let { message ->
            val colors = palette()
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background3.copy(alpha = .94f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Playback interrupted",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    color = colors.muted,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
                if (!LocalTouchUi.current) {
                    Spacer(Modifier.height(10.dp))
                    Text("Press Back to leave the player", color = colors.muted.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun createLiveLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(1500, 30000, 500, 1500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(-1)
        .build()

/* -------------------------------------------------------------------------
 * Player overlay
 *
 * Layout follows the familiar video-player hierarchy: a compact title block at
 * the top, transport in the middle, progress and secondary actions along the
 * bottom. Everything is Ovela's charcoal and warm ivory; nothing is borrowed
 * from YouTube branding and no control is shown that Ovela cannot act on.
 * ---------------------------------------------------------------------- */

@Composable
private fun PlayerOverlay(
    request: PlayerRequest,
    favorite: Boolean,
    isLive: Boolean,
    videoAspect: Float,
    playing: Boolean,
    position: Long,
    duration: Long,
    subtitlesAvailable: Boolean,
    subtitlesEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onBack: () -> Unit,
    onInteraction: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val baseInset = if (touchUi) PLAYER_TOUCH_INSET else PLAYER_TV_INSET
    val verticalPadding = if (touchUi) 10.dp else 18.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the overlay on the picture: when the stream is pillarboxed (a
        // landscape phone showing a 16:9 feed, for example) the controls move in
        // from the black side bars instead of floating outside the video. The
        // correction is capped so an unusually narrow stream cannot squeeze the
        // controls into a sliver.
        val sideBar = if (videoAspect > 0f) {
            val fittedWidth = minOf(maxWidth.value, (maxHeight.value * videoAspect).coerceAtMost(maxWidth.value))
            (((maxWidth.value - fittedWidth) / 2f).coerceAtLeast(0f)).dp.coerceAtMost(maxWidth * 0.12f)
        } else {
            0.dp
        }
        val horizontalPadding = baseInset + sideBar

        // Legibility scrims: soft at the edges, fully transparent over the
        // picture so the video is never dimmed where it matters.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(.26f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to colors.background3.copy(alpha = .78f),
                            .55f to colors.background3.copy(alpha = .22f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.50f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            .42f to colors.background3.copy(alpha = .22f),
                            .74f to colors.background3.copy(alpha = .62f),
                            1f to colors.background3.copy(alpha = .94f),
                        ),
                    ),
                ),
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            // Top aligned so the chevron and the trailing icons sit on the title
            // line instead of drifting down beside the two-line metadata block.
            verticalAlignment = Alignment.Top,
        ) {
            if (touchUi) {
                PlayerIconButton(
                    icon = "back",
                    label = "Close player",
                    onClick = onBack,
                )
                Spacer(Modifier.width(8.dp))
            }
            PlayerTitleBlock(
                request = request,
                isLive = isLive,
                modifier = Modifier.weight(1f),
            )
            // Subtitles are the only secondary action with a place of their own;
            // everything else lives in the bottom action row or the options
            // panel, so the overlay never shows a redundant overflow menu.
            if (subtitlesAvailable) {
                Spacer(Modifier.width(4.dp))
                PlayerIconButton(
                    icon = "captions",
                    label = if (subtitlesEnabled) "Turn subtitles off" else "Turn subtitles on",
                    selected = subtitlesEnabled,
                    onClick = onToggleSubtitles,
                )
            }
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (touchUi) 26.dp else 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCircleButton(
                icon = "prev",
                label = if (isLive) "Previous channel" else "Previous item",
                onClick = onPrevious,
                touchUi = touchUi,
            )
            if (!isLive) {
                PlayerCircleButton(
                    icon = if (playing) "pause" else "play",
                    label = if (playing) "Pause" else "Play",
                    onClick = onTogglePlayback,
                    touchUi = touchUi,
                    prominent = true,
                )
            }
            PlayerCircleButton(
                icon = "next",
                label = if (isLive) "Next channel" else "Next item",
                onClick = onNext,
                touchUi = touchUi,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            if (!isLive && duration > 0L) {
                PlayerTimeline(
                    position = position,
                    duration = duration,
                    onSeek = onSeek,
                    onInteraction = onInteraction,
                )
                Spacer(Modifier.height(if (touchUi) 10.dp else 12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isLive) {
                    PlayerActionChip(
                        label = "−30s",
                        onClick = { onSeekBy(-SEEK_STEP_MS) },
                        touchUi = touchUi,
                    )
                    Spacer(Modifier.width(8.dp))
                    PlayerActionChip(
                        label = "+30s",
                        onClick = { onSeekBy(SEEK_STEP_MS) },
                        touchUi = touchUi,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                PlayerActionChip(
                    label = if (favorite) "Favorited" else "Favorite",
                    icon = "heart",
                    active = favorite,
                    onClick = onToggleFavorite,
                    touchUi = touchUi,
                )
                Spacer(Modifier.weight(1f))
                if (!touchUi) {
                    Text(
                        "MENU  Options",
                        color = colors.muted.copy(alpha = .78f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = .8.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTitleBlock(
    request: PlayerRequest,
    isLive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val item = request.item
    val meta = buildList {
        if (!isLive) {
            val seasonEpisode = listOfNotNull(
                item.season?.let { "S$it" },
                item.episode?.let { "E$it" },
            ).joinToString(" ")
            val series = item.parentSeriesName?.takeIf { it.isNotBlank() }
            when {
                series != null && seasonEpisode.isNotBlank() -> add("$seasonEpisode · $series")
                series != null -> add(series)
            }
            formatRuntime(item.runtimeMinutes).takeIf { it.isNotBlank() }?.let { add(it) }
            item.rating?.let { add("★ %.1f".format(it)) }
        } else {
            item.channelNumber?.takeIf { it.isNotBlank() }?.let { add("Channel $it") }
        }
        item.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("  ·  ")

    Column(modifier) {
        Text(
            item.name,
            color = colors.text,
            fontSize = if (touchUi) 15.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLive) {
                LiveBadge()
                if (meta.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    color = colors.muted,
                    fontSize = if (touchUi) 10.5.sp else 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!isLive) {
                Text(
                    item.type.title(),
                    color = colors.muted,
                    fontSize = if (touchUi) 10.5.sp else 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    val colors = palette()
    Row(
        Modifier
            .height(17.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.accent.copy(alpha = .15f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(colors.accent))
        Spacer(Modifier.width(4.dp))
        Text(
            "LIVE",
            color = colors.accent,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .9.sp,
        )
    }
}

@Composable
private fun PlayerCircleButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    touchUi: Boolean,
    prominent: Boolean = false,
) {
    val colors = palette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val size = when {
        prominent && touchUi -> 60.dp
        prominent -> 68.dp
        touchUi -> 44.dp
        else -> 46.dp
    }
    val background = when {
        pressed -> colors.panelStrong.copy(alpha = .92f)
        prominent -> colors.panelStrong.copy(alpha = .78f)
        else -> Color.White.copy(alpha = .06f)
    }
    val border = if (prominent) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .08f)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .focusProperties { canFocus = false }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(playerIconResource(icon)),
            contentDescription = null,
            modifier = Modifier.size(if (prominent) (if (touchUi) 24.dp else 26.dp) else (if (touchUi) 18.dp else 17.dp)),
            colorFilter = ColorFilter.tint(colors.text.copy(alpha = .95f)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PlayerIconButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(if (touchUi) 40.dp else 36.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = .18f)
                    pressed -> Color.White.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .focusProperties { canFocus = false }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(playerIconResource(icon)),
            contentDescription = null,
            modifier = Modifier.size(if (touchUi) 19.dp else 18.dp),
            colorFilter = ColorFilter.tint(if (selected) colors.accent else colors.text.copy(alpha = .92f)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PlayerActionChip(
    label: String,
    onClick: () -> Unit,
    touchUi: Boolean,
    icon: String? = null,
    active: Boolean = false,
) {
    val colors = palette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .height(if (touchUi) 34.dp else 30.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                when {
                    active -> colors.accent.copy(alpha = .16f)
                    pressed -> Color.White.copy(alpha = .15f)
                    else -> Color.White.copy(alpha = .07f)
                },
            )
            .border(
                1.dp,
                if (active) colors.accent.copy(alpha = .45f) else Color.White.copy(alpha = .09f),
                RoundedCornerShape(99.dp),
            )
            .focusProperties { canFocus = false }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (touchUi) 13.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TvIcon(
                icon,
                if (active) colors.accent else colors.text.copy(alpha = .88f),
                Modifier.size(if (touchUi) 14.dp else 13.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (active) colors.accent else colors.text.copy(alpha = .9f),
            fontSize = if (touchUi) 11.5.sp else 10.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlayerTimeline(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (duration > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = scrubFraction ?: playedFraction
    val shownPosition = scrubFraction?.let { (it * duration).toLong() } ?: position
    val railHeight = if (scrubFraction != null) 5.dp else 3.dp
    val knob = if (touchUi) 12.dp else 10.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatDuration(shownPosition),
            color = colors.text.copy(alpha = .92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = if (touchUi) 46.dp else 54.dp),
        )
        Spacer(Modifier.width(if (touchUi) 6.dp else 10.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(if (touchUi) 26.dp else 20.dp)
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var fraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        scrubFraction = fraction
                        onInteraction()
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            scrubFraction = fraction
                            change.consume()
                            if (!change.pressed) break
                        }
                        onSeek((fraction * duration).toLong())
                        scrubFraction = null
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(railHeight)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = .22f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(shownFraction)
                    .height(railHeight)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.accent),
            )
            val travel = (maxWidth - knob).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .offset(x = travel * shownFraction)
                    .size(knob)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
        Spacer(Modifier.width(if (touchUi) 6.dp else 10.dp))
        Text(
            formatDuration(duration),
            color = colors.muted,
            fontSize = 11.sp,
            modifier = Modifier.widthIn(min = if (touchUi) 46.dp else 54.dp),
            textAlign = TextAlign.End,
        )
    }
}

private fun playerIconResource(name: String): Int = when (name) {
    "play" -> R.drawable.ic_player_play
    "pause" -> R.drawable.ic_player_pause
    "prev" -> R.drawable.ic_player_prev
    "next" -> R.drawable.ic_player_next
    "captions" -> R.drawable.ic_player_captions
    "more" -> R.drawable.ic_player_more
    "back" -> R.drawable.ic_player_back
    else -> R.drawable.ic_player_more
}

@Composable
private fun PlaybackOptions(
    modifier: Modifier = Modifier,
    favorite: Boolean,
    subtitlesAvailable: Boolean,
    subtitlesEnabled: Boolean,
    subtitleFocus: FocusRequester,
    favoriteFocus: FocusRequester,
    onToggleSubtitles: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    Column(
        modifier
            .width(if (touchUi) 172.dp else 184.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background3.copy(alpha = .96f))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "OPTIONS",
            color = colors.muted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        TvButton(
            label = if (subtitlesAvailable) {
                "Subtitles ${if (subtitlesEnabled) "on" else "off"}"
            } else {
                "Subtitles unavailable"
            },
            onClick = onToggleSubtitles,
            enabled = subtitlesAvailable,
            modifier = Modifier.fillMaxWidth(),
            style = TvButtonStyle.Secondary,
            focusRequester = subtitleFocus,
        )
        TvButton(
            label = if (favorite) "Favorited" else "Add to favorites",
            onClick = onToggleFavorite,
            modifier = Modifier.fillMaxWidth(),
            style = TvButtonStyle.Secondary,
            focusRequester = favoriteFocus,
        )
    }
}

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(UnstableApi::class)
private fun pcmVodRenderersFactory(context: Context): DefaultRenderersFactory =
    object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder()
            // A context-free sink intentionally advertises no encoded-audio
            // passthrough. The codec therefore decodes AC-3/E-AC-3 to PCM and
            // Media3 can keep audio on the same clock as rendered video.
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(VodAudioAdvanceProcessor(VOD_AUDIO_ADVANCE_MS)))
            .build()
    }

/**
 * Advances decoded VOD audio to counter the excess delay added by the TV's
 * video-processing compensation path. Dropping PCM only at each new stream
 * keeps the correction stable without changing playback speed or Live TV.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class VodAudioAdvanceProcessor(
    private val advanceMs: Int,
) : BaseAudioProcessor() {
    private var bytesToSkip = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val framesToSkip = inputAudioFormat.sampleRate.toLong() * advanceMs / 1_000L
        bytesToSkip = (framesToSkip * inputAudioFormat.bytesPerFrame)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val skippedBytes = minOf(inputBuffer.remaining(), bytesToSkip)
        inputBuffer.position(inputBuffer.position() + skippedBytes)
        bytesToSkip -= skippedBytes

        if (inputBuffer.hasRemaining()) {
            replaceOutputBuffer(inputBuffer.remaining()).apply {
                put(inputBuffer)
                flip()
            }
        }
    }
}
