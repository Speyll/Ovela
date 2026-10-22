package com.xtreamlytv.androidtv.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.SourceType
import com.xtreamlytv.androidtv.ui.theme.palette
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth <= 1000.dp || maxHeight <= 600.dp
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
                onConnect = ::connect,
            )
        }
    }
}

@Composable
private fun LoginBrandPanel(modifier: Modifier, compact: Boolean) {
    val colors = palette()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        BrandLockup(iconSize = if (compact) 72.dp else 104.dp, wordmarkSize = if (compact) 35 else 48)
        Spacer(Modifier.height(if (compact) 18.dp else 28.dp))
        Text(
            "Your provider. Your screen. Your way.",
            color = colors.text,
            fontSize = if (compact) 22.sp else 31.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (compact) 27.sp else 37.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "A focused living-room client for Xtream accounts and M3U playlists, designed for remote navigation and local privacy.",
            color = colors.muted,
            fontSize = if (compact) 13.sp else 17.sp,
            lineHeight = if (compact) 18.sp else 23.sp,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Live TV", "Movies", "Series", "Favorites", "No tracking").forEach { label ->
                Box(
                    Modifier
                        .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(label, color = colors.text.copy(alpha = 0.86f), fontSize = 10.sp)
                }
            }
        }
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
    onConnect: () -> Unit,
) {
    val colors = palette()
    val cleartext = sourceType == SourceType.XTREAM && server.trim().startsWith("http://", ignoreCase = true)
    val statusText = message ?: when {
        sourceType == SourceType.M3U -> "Playlist URLs and imported files stay on this TV. Generic M3U entries are shown as live unless the URL or group identifies a movie."
        cleartext -> "HTTP providers are supported, but credentials are not protected in transit."
        else -> "Use the base address supplied by your Xtream provider."
    }
    val statusColor = when {
        message != null -> Color(0xFFFF9CA5)
        cleartext -> Color(0xFFFFC66D)
        else -> colors.muted
    }

    Column(
        modifier
            .background(colors.panel.copy(alpha = 0.95f), RoundedCornerShape(if (compact) 20.dp else 28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(if (compact) 20.dp else 28.dp))
            .padding(if (compact) 22.dp else 34.dp),
    ) {
        Text("Connect a source", color = colors.text, fontSize = if (compact) 22.sp else 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text("Use an Xtream account or an M3U playlist.", color = colors.muted, fontSize = if (compact) 11.sp else 14.sp)
        Spacer(Modifier.height(if (compact) 11.dp else 16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TvChip(
                label = "Xtream",
                selected = sourceType == SourceType.XTREAM,
                onClick = { onSourceTypeChange(SourceType.XTREAM) },
            )
            TvChip(
                label = "M3U playlist",
                selected = sourceType == SourceType.M3U,
                onClick = { onSourceTypeChange(SourceType.M3U) },
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

        if (sourceType == SourceType.XTREAM) {
            TvTextField("Server URL", server, onServerChange, placeholder = "http://provider.example:port", focusRequester = serverFocus)
            Spacer(Modifier.height(if (compact) 9.dp else 14.dp))
            TvTextField("Username", username, onUsernameChange, placeholder = "Provider username", focusRequester = usernameFocus)
            Spacer(Modifier.height(if (compact) 9.dp else 14.dp))
            TvTextField("Password", password, onPasswordChange, placeholder = "Provider password", password = true, focusRequester = passwordFocus)
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            TvTextField(
                "Alternate hosts (optional, comma-separated)",
                alternateHosts,
                onAlternateHostsChange,
                placeholder = "https://backup.example:443",
            )
        } else {
            TvTextField(
                "Playlist URL",
                playlistUrl,
                onPlaylistChange,
                placeholder = "https://provider.example/playlist.m3u",
                focusRequester = playlistFocus,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            TvTextField(
                "Alternate hosts (optional, comma-separated)",
                alternateHosts,
                onAlternateHostsChange,
                placeholder = "https://backup.example:443",
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            TvButton(
                label = if (importing) "Importing…" else "Import local M3U file",
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && !importing,
                style = TvButtonStyle.Secondary,
            )
        }

        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Box(Modifier.fillMaxWidth().height(if (sourceType == SourceType.M3U) 44.dp else 34.dp), contentAlignment = Alignment.CenterStart) {
            Text(statusText, color = statusColor, fontSize = if (compact) 9.sp else 11.sp, lineHeight = if (compact) 12.sp else 15.sp, maxLines = 3)
        }
        Spacer(Modifier.height(8.dp))
        TvButton(
            label = when {
                loading -> if (sourceType == SourceType.M3U) "Loading playlist…" else "Connecting…"
                message != null -> "Try again"
                else -> "Connect"
            },
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && !importing,
            leading = if (loading) "◌" else null,
            focusRequester = connectFocus,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "XtreamlyTV does not provide channels or subscriptions. Saved source details remain on this TV.",
            color = colors.muted.copy(alpha = 0.72f),
            fontSize = 8.sp,
            lineHeight = 11.sp,
        )
    }
}

private const val MAX_IMPORTED_PLAYLIST_BYTES = 32L * 1024 * 1024
