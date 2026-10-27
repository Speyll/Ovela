package io.github.speyll.ovela.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.SourceType
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.palette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(state: AppUiState, onConnect: (Credentials) -> Unit) {
    var sourceType by remember(state.credentials) {
        mutableStateOf(state.credentials?.sourceType ?: SourceType.XTREAM)
    }
    var server by remember(state.credentials) { mutableStateOf(state.credentials?.server.orEmpty()) }
    var username by remember(state.credentials) { mutableStateOf(state.credentials?.username.orEmpty()) }
    var password by remember(state.credentials) { mutableStateOf(state.credentials?.password.orEmpty()) }
    var playlistUrl by remember(state.credentials) { mutableStateOf(state.credentials?.playlistUrl.orEmpty()) }
    var alternateHosts by remember(state.credentials) {
        mutableStateOf(state.credentials?.alternateServers?.joinToString(", ").orEmpty())
    }
    var localError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val serverFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val playlistFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.filesDir, "imported-playlist.m3u")
                    val temporary = File.createTempFile("playlist-import", ".tmp", context.filesDir)
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("The selected file could not be opened.")
                        input.use { source ->
                            temporary.outputStream().use { destination ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val read = source.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    if (total > MAX_IMPORTED_PLAYLIST_BYTES) {
                                        error("The selected playlist is too large to import.")
                                    }
                                    destination.write(buffer, 0, read)
                                }
                            }
                        }
                        if (!temporary.renameTo(target)) {
                            target.delete()
                            if (!temporary.renameTo(target)) error("The playlist could not be saved.")
                        }
                        target
                    } finally {
                        temporary.delete()
                    }
                }
            }
            importing = false
            imported.onSuccess { file ->
                playlistUrl = "file://${file.absolutePath}"
                sourceType = SourceType.M3U
                localError = null
            }.onFailure {
                localError = "Could not import the selected playlist."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.credentials?.isM3u == true) {
            if (playlistUrl.isBlank()) playlistFocus.requestFocus() else connectFocus.requestFocus()
        } else {
            when {
                server.isBlank() -> serverFocus.requestFocus()
                username.isBlank() -> usernameFocus.requestFocus()
                password.isBlank() -> passwordFocus.requestFocus()
                else -> connectFocus.requestFocus()
            }
        }
    }

    fun connect() {
        val alternates = alternateHosts.split(',', '\n').map(String::trim).filter(String::isNotBlank)
        when (sourceType) {
            SourceType.XTREAM -> when {
                server.isBlank() -> {
                    localError = "Enter your provider URL."
                    serverFocus.requestFocus()
                }
                username.isBlank() -> {
                    localError = "Enter your provider username."
                    usernameFocus.requestFocus()
                }
                password.isBlank() -> {
                    localError = "Enter your provider password."
                    passwordFocus.requestFocus()
                }
                else -> {
                    localError = null
                    onConnect(Credentials.xtream(server, username, password).copy(alternateServers = alternates))
                }
            }
            SourceType.M3U -> {
                if (playlistUrl.isBlank()) {
                    localError = "Enter an M3U playlist URL or import a local playlist."
                    playlistFocus.requestFocus()
                } else {
                    localError = null
                    onConnect(Credentials.m3u(playlistUrl, alternates))
                }
            }
        }
    }

    fun submit() {
        keyboardController?.hide()
        connect()
    }

    val touchUi = LocalTouchUi.current
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxWidth <= 1000.dp || maxHeight <= 600.dp
        val stacked = loginStacksVertically(touchUi, maxWidth.value.toInt(), maxHeight.value.toInt())

        if (stacked) {
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoginPhoneBrand()
                ProviderLoginCard(
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                    sourceType = sourceType,
                    server = server,
                    username = username,
                    password = password,
                    playlistUrl = playlistUrl,
                    alternateHosts = alternateHosts,
                    loading = state.loading,
                    importing = importing,
                    message = localError ?: state.error,
                    serverFocus = serverFocus,
                    usernameFocus = usernameFocus,
                    passwordFocus = passwordFocus,
                    playlistFocus = playlistFocus,
                    connectFocus = connectFocus,
                    onSourceTypeChange = { sourceType = it; localError = null },
                    onServerChange = { server = it; localError = null },
                    onUsernameChange = { username = it; localError = null },
                    onPasswordChange = { password = it; localError = null },
                    onPlaylistChange = { playlistUrl = it; localError = null },
                    onAlternateHostsChange = { alternateHosts = it; localError = null },
                    onImport = { filePicker.launch(arrayOf("*/*")) },
                    onSubmit = ::submit,
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(
                    horizontal = if (compact) 34.dp else 70.dp,
                    vertical = if (compact) 28.dp else 60.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 30.dp else 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginBrandPanel(Modifier.weight(1.08f), compact)
                ProviderLoginCard(
                    modifier = Modifier.weight(0.92f).fillMaxHeight(),
                    compact = compact,
                    sourceType = sourceType,
                    server = server,
                    username = username,
                    password = password,
                    playlistUrl = playlistUrl,
                    alternateHosts = alternateHosts,
                    loading = state.loading,
                    importing = importing,
                    message = localError ?: state.error,
                    serverFocus = serverFocus,
                    usernameFocus = usernameFocus,
                    passwordFocus = passwordFocus,
                    playlistFocus = playlistFocus,
                    connectFocus = connectFocus,
                    onSourceTypeChange = { sourceType = it; localError = null },
                    onServerChange = { server = it; localError = null },
                    onUsernameChange = { username = it; localError = null },
                    onPasswordChange = { password = it; localError = null },
                    onPlaylistChange = { playlistUrl = it; localError = null },
                    onAlternateHostsChange = { alternateHosts = it; localError = null },
                    onImport = { filePicker.launch(arrayOf("*/*")) },
                    onSubmit = ::submit,
                )
            }
        }
    }
}

@Composable
private fun LoginPhoneBrand() {
    val colors = palette()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandLockup(iconSize = 40.dp, wordmarkSize = 26)
        Spacer(Modifier.height(6.dp))
        Text(
            "Your television,\nquietly organised.",
            color = colors.text,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        )
        Text(
            "Live TV, movies and series from your own provider.",
            color = colors.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun LoginBrandPanel(modifier: Modifier, compact: Boolean) {
    val colors = palette()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        BrandLockup(iconSize = if (compact) 40.dp else 50.dp, wordmarkSize = if (compact) 27 else 33)
        Spacer(Modifier.height(if (compact) 30.dp else 46.dp))
        Text(
            "Your television,\nquietly organised.",
            color = colors.text,
            fontSize = if (compact) 27.sp else 40.sp,
            lineHeight = if (compact) 31.sp else 45.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        Text(
            "Connect your own provider. Ovela keeps Live TV, movies and series in one calm place — nothing is included, and nothing leaves this device.",
            color = colors.muted,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 17.sp else 21.sp,
            modifier = Modifier.fillMaxWidth(if (compact) 1f else .92f),
        )
        Spacer(Modifier.height(if (compact) 22.dp else 34.dp))
        Text(
            "LIVE TV   ·   MOVIES   ·   SERIES   ·   FAVOURITES",
            color = colors.muted.copy(alpha = .78f),
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = if (compact) 1.4.sp else 2.sp,
        )
    }
}

@Composable
private fun ProviderLoginCard(
    modifier: Modifier,
    compact: Boolean,
    sourceType: SourceType,
    server: String,
    username: String,
    password: String,
    playlistUrl: String,
    alternateHosts: String,
    loading: Boolean,
    importing: Boolean,
    message: String?,
    serverFocus: FocusRequester,
    usernameFocus: FocusRequester,
    passwordFocus: FocusRequester,
    playlistFocus: FocusRequester,
    connectFocus: FocusRequester,
    onSourceTypeChange: (SourceType) -> Unit,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPlaylistChange: (String) -> Unit,
    onAlternateHostsChange: (String) -> Unit,
    onImport: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val cleartext = sourceType == SourceType.XTREAM && server.trim().startsWith("http://", ignoreCase = true)
    val statusText = message ?: when {
        sourceType == SourceType.M3U -> "Playlist URLs and imported files stay on this device. Generic M3U entries are shown as live unless the URL or group identifies a movie."
        cleartext -> "HTTP providers are supported, but credentials are not protected in transit."
        else -> "Use the base address supplied by your Xtream provider."
    }
    val statusColor = when {
        message != null -> colors.danger
        cleartext -> colors.accentSecondary
        else -> colors.muted
    }

    Column(
        modifier
            .background(colors.panel.copy(alpha = 0.95f), RoundedCornerShape(if (compact) 20.dp else 28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(if (compact) 20.dp else 28.dp))
            .padding(if (touchUi) 18.dp else if (compact) 22.dp else 34.dp),
    ) {
        Text("Connect a source", color = colors.text, fontSize = if (compact) 22.sp else 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text("Use an Xtream account or an M3U playlist.", color = colors.muted, fontSize = if (compact) 11.sp else 14.sp)
        Spacer(Modifier.height(if (compact) 11.dp else 16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TvChip(
                label = "Xtream",
                selected = sourceType == SourceType.XTREAM,
                onClick = {
                    onSourceTypeChange(SourceType.XTREAM)
                    runCatching { serverFocus.requestFocus() }
                },
            )
            TvChip(
                label = "M3U playlist",
                selected = sourceType == SourceType.M3U,
                onClick = {
                    onSourceTypeChange(SourceType.M3U)
                    runCatching { playlistFocus.requestFocus() }
                },
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

        if (sourceType == SourceType.XTREAM) {
            TvTextField(
                "Server URL",
                server,
                onServerChange,
                placeholder = "http://provider.example:port",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
                focusRequester = serverFocus,
            )
            Spacer(Modifier.height(if (touchUi) 12.dp else if (compact) 9.dp else 14.dp))
            TvTextField(
                "Username",
                username,
                onUsernameChange,
                placeholder = "Provider username",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                focusRequester = usernameFocus,
            )
            Spacer(Modifier.height(if (touchUi) 12.dp else if (compact) 9.dp else 14.dp))
            TvTextField(
                "Password",
                password,
                onPasswordChange,
                placeholder = "Provider password",
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                focusRequester = passwordFocus,
            )
            Spacer(Modifier.height(if (touchUi) 10.dp else if (compact) 8.dp else 12.dp))
            TvTextField(
                "Alternate hosts (optional, comma-separated)",
                alternateHosts,
                onAlternateHostsChange,
                placeholder = "https://backup.example:443",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
        } else {
            TvTextField(
                "Playlist URL",
                playlistUrl,
                onPlaylistChange,
                placeholder = "https://provider.example/playlist.m3u",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                focusRequester = playlistFocus,
            )
            Spacer(Modifier.height(if (touchUi) 10.dp else if (compact) 8.dp else 12.dp))
            TvTextField(
                "Alternate hosts (optional, comma-separated)",
                alternateHosts,
                onAlternateHostsChange,
                placeholder = "https://backup.example:443",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            Spacer(Modifier.height(if (touchUi) 10.dp else if (compact) 8.dp else 10.dp))
            TvButton(
                label = if (importing) "Importing…" else "Import local M3U file",
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && !importing,
                style = TvButtonStyle.Secondary,
            )
        }

        Spacer(Modifier.height(if (touchUi) 10.dp else if (compact) 8.dp else 12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (sourceType == SourceType.M3U) 44.dp else if (touchUi) 40.dp else 34.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                statusText,
                color = statusColor,
                fontSize = if (touchUi) 11.sp else if (compact) 9.sp else 11.sp,
                lineHeight = if (touchUi) 14.sp else if (compact) 12.sp else 15.sp,
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(8.dp))
        TvButton(
            label = when {
                loading -> if (sourceType == SourceType.M3U) "Loading playlist…" else "Connecting…"
                message != null -> "Try again"
                else -> "Connect"
            },
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && !importing,
            leading = if (loading) "◌" else null,
            focusRequester = connectFocus,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Ovela does not provide channels or subscriptions. Saved source details remain on this device.",
            color = colors.muted.copy(alpha = 0.72f),
            fontSize = 8.sp,
            lineHeight = 11.sp,
        )
    }
}

private const val MAX_IMPORTED_PLAYLIST_BYTES = 32L * 1024 * 1024
