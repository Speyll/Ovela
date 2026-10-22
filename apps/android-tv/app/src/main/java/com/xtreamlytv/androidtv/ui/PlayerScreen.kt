package com.xtreamlytv.androidtv.ui

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xtreamlytv.androidtv.data.PlaybackFailureKind
import com.xtreamlytv.androidtv.data.PlaybackRecoveryAction
import com.xtreamlytv.androidtv.data.PlaybackRecoveryPolicy
import com.xtreamlytv.androidtv.data.PlaybackRecoveryState
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.PlayerRequest
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

private const val VOD_AUDIO_ADVANCE_MS = 280

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
    var optionsOpen by remember(request.item.id) { mutableStateOf(false) }
    var playing by remember(request.item.id) { mutableStateOf(true) }
    var position by remember(request.item.id) { mutableLongStateOf(request.startPositionMs) }
    var duration by remember(request.item.id) { mutableLongStateOf(0L) }
    var errorMessage by remember(request.item.id) { mutableStateOf<String?>(null) }
    var subtitlesAvailable by remember(request.item.id) { mutableStateOf(false) }
    var subtitlesEnabled by remember(request.item.id) { mutableStateOf(false) }

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
        val renderersFactory = if (request.item.type == ContentType.LIVE) {
            DefaultRenderersFactory(context)
        } else {
            pcmVodRenderersFactory(context)
        }
        renderersFactory
            .setEnableDecoderFallback(true)
            .also { factory ->
                // Several Android TV vendor codecs develop a small A/V offset
                // with asynchronous queueing on long-form MP4/MKV playback.
                // Live streams use a different segmented timing path and retain
                // the platform default for lowest channel-change latency.
                if (request.item.type != ContentType.LIVE) {
                    factory.forceDisableMediaCodecAsynchronousQueueing()
                }
            }
        val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val candidate = request.urlCandidates.getOrElse(candidateIndex) { request.urlCandidates.first() }
            setMediaItem(MediaItem.fromUri(candidate))
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                .build()
            val resumePosition = position.coerceAtLeast(request.startPositionMs)
            val shouldResume = resumePosition > 0L && request.item.type != ContentType.LIVE
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
                val kind = PlaybackRecoveryPolicy.classifyPlayerErrorCode(error.errorCode)
                recover(kind, error.message ?: "Unable to play this stream.")
            }
        }
        player.addListener(listener)
        onDispose {
            if (request.item.type != ContentType.LIVE) {
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

    LaunchedEffect(player) {
        var persistTicks = 0
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            persistTicks += 1
            if (persistTicks >= 10 && request.item.type != ContentType.LIVE) {
                onProgress(position, duration)
                persistTicks = 0
            }
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, playing, optionsOpen) {
        if (controlsVisible && playing && !optionsOpen) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(optionsOpen, subtitlesAvailable) {
        if (optionsOpen) {
            delay(24L)
            val target = if (subtitlesAvailable) subtitleOptionFocus else favoriteOptionFocus
            runCatching { target.requestFocus() }
        }
    }

    fun showControls() { controlsVisible = true }
    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
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
    fun closeOptions() {
        optionsOpen = false
        showControls()
        runCatching { focusRequester.requestFocus() }
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
                        event.key == Key.Back || event.key == Key.Menu -> { closeOptions(); true }
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
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.MediaPlayPause -> {
                        togglePlayback()
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 30_000L).coerceAtLeast(0L))
                        showControls()
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        player.seekTo(player.currentPosition + 30_000L)
                        showControls()
                        true
                    }
                    event.key == Key.DirectionUp -> { onPrevious(); true }
                    event.key == Key.DirectionDown -> { onNext(); true }
                    event.key == Key.Menu -> {
                        optionsOpen = true
                        controlsVisible = true
                        true
                    }
                    event.key == Key.Back -> { onBack(); true }
                    else -> { showControls(); false }
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
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            PlayerControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                request = request,
                favorite = favorite,
                playing = playing,
                position = position,
                duration = duration,
                subtitlesAvailable = subtitlesAvailable,
                subtitlesEnabled = subtitlesEnabled,
            )
        }

        if (optionsOpen) {
            PlaybackOptions(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 112.dp),
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
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE6071014), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("Recovering playback…", color = Color.White, fontSize = 13.sp)
            }
        }

        errorMessage?.let { message ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE6071014), RoundedCornerShape(14.dp))
                    .padding(20.dp),
            ) {
                Text(message, color = Color.White, fontSize = 14.sp)
            }
        }
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
    Column(
        modifier
            .width(188.dp)
            .background(Color(0xF2071014), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Playback options", color = colors.text, fontSize = 11.sp)
        TvButton(
            label = if (subtitlesAvailable) {
                "Subtitles: ${if (subtitlesEnabled) "On" else "Off"}"
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
            label = if (favorite) "♥ Favorited" else "♡ Favorite",
            onClick = onToggleFavorite,
            modifier = Modifier.fillMaxWidth(),
            style = TvButtonStyle.Secondary,
            focusRequester = favoriteFocus,
        )
    }
}

@Composable
private fun PlayerControls(
    modifier: Modifier = Modifier,
    request: PlayerRequest,
    favorite: Boolean,
    playing: Boolean,
    position: Long,
    duration: Long,
    subtitlesAvailable: Boolean,
    subtitlesEnabled: Boolean,
) {
    val colors = palette()
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xDC071014))
            .padding(horizontal = 34.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandLockup(iconSize = 30.dp, wordmarkSize = 16)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(request.item.name, color = colors.text, fontSize = 15.sp, maxLines = 1)
                Text(
                    request.item.channelNumber?.let { "Channel $it" } ?: request.item.type.title(),
                    color = colors.muted,
                    fontSize = 9.sp,
                )
            }
            if (favorite) Text("♥", color = colors.danger, fontSize = 14.sp)
            Spacer(Modifier.width(14.dp))
            Text(if (request.item.type == ContentType.LIVE) "LIVE" else formatDuration(position), color = colors.accent, fontSize = 11.sp)
        }
        if (request.item.type != ContentType.LIVE && duration > 0L) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))) {
                Box(
                    Modifier
                        .fillMaxWidth((position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colors.accent, RoundedCornerShape(50)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← 30s", color = colors.muted, fontSize = 9.sp)
            Text(if (playing) "OK Pause" else "OK Play", color = colors.text, fontSize = 10.sp)
            Text("30s →", color = colors.muted, fontSize = 9.sp)
            Text("MENU Options", color = colors.muted, fontSize = 9.sp)
            if (subtitlesAvailable) {
                Text("CC ${if (subtitlesEnabled) "On" else "Off"}", color = if (subtitlesEnabled) colors.accent else colors.muted, fontSize = 9.sp)
            }
            if (request.item.type == ContentType.LIVE) {
                Text("↑/↓ Change channel", color = colors.muted, fontSize = 9.sp)
            } else {
                Text("${formatDuration(position)} / ${formatDuration(duration)}", color = colors.muted, fontSize = 9.sp)
            }
        }
    }
}
