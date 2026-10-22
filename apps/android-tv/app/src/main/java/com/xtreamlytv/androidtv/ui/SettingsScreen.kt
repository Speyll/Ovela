package com.xtreamlytv.androidtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.BuildConfig
import com.xtreamlytv.androidtv.model.AppTheme
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.SourceType
import com.xtreamlytv.androidtv.model.StreamFormat
import com.xtreamlytv.androidtv.ui.theme.palette
import com.xtreamlytv.androidtv.ui.theme.paletteFor
import java.net.URI
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
) {
    var editingProvider by remember { mutableStateOf(false) }
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
    val scrollState = rememberScrollState()

    // When the edit button is replaced by the first text field, keep the
    // content-entry focus target valid instead of letting focus jump back to
    // the Settings cog.
    LaunchedEffect(editingProvider) {
        if (editingProvider) {
            delay(24L)
            runCatching { contentEntryFocus.requestFocus() }
        }
    }

    // Settings is intentionally a regular scroll column rather than a lazy
    // list. All focus targets stay composed, so D-pad focus can continue down
    // the complete page and Compose can bring the focused control into view.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsCard("Provider", "Your saved Xtream account or M3U playlist. Edit only when you want to reconnect with different details.") {
            if (!editingProvider) {
                if (state.credentials?.isM3u == true) {
                    ProviderSummaryRow("Source", "M3U playlist")
                    ProviderSummaryRow("Playlist", state.credentials?.playlistUrl?.let(::maskPlaylistUrl) ?: "Not configured")
                    ProviderSummaryRow(
                        "Alternate hosts",
                        state.credentials?.alternateServers?.size?.toString() ?: "0",
                    )
                } else {
                    ProviderSummaryRow("Source", "Xtream account")
                    ProviderSummaryRow("Server", state.credentials?.server ?: "Not configured")
                    ProviderSummaryRow("Username", state.credentials?.username ?: "Not configured")
                    ProviderSummaryRow("Password", if (state.credentials == null) "Not configured" else "••••••••")
                    ProviderSummaryRow(
                        "Alternate hosts",
                        state.credentials?.alternateServers?.size?.toString() ?: "0",
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TvButton(
                        "Edit provider",
                        { editingProvider = true },
                        Modifier.width(112.dp),
                        focusRequester = contentEntryFocus,
                    )
                    TvButton("Disconnect provider", viewModel::disconnect, Modifier.width(142.dp), TvButtonStyle.Danger)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TvChip(
                        label = "Xtream",
                        selected = sourceType == SourceType.XTREAM,
                        onClick = { sourceType = SourceType.XTREAM },
                    )
                    TvChip(
                        label = "M3U playlist",
                        selected = sourceType == SourceType.M3U,
                        onClick = { sourceType = SourceType.M3U },
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (sourceType == SourceType.XTREAM) {
                    TvTextField(
                        "Server URL",
                        server,
                        { server = it },
                        placeholder = "http://provider.example:port",
                        focusRequester = contentEntryFocus,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvTextField("Username", username, { username = it }, Modifier.weight(1f))
                        TvTextField("Password", password, { password = it }, Modifier.weight(1f), password = true)
                    }
                    Spacer(Modifier.height(7.dp))
                    TvTextField(
                        "Alternate hosts (optional, comma-separated)",
                        alternateHosts,
                        { alternateHosts = it },
                        placeholder = "https://backup.example:443",
                    )
                } else {
                    TvTextField(
                        "Playlist URL",
                        playlistUrl,
                        { playlistUrl = it },
                        placeholder = "https://provider.example/playlist.m3u",
                        focusRequester = contentEntryFocus,
                    )
                    Spacer(Modifier.height(7.dp))
                    TvTextField(
                        "Alternate hosts (optional, comma-separated)",
                        alternateHosts,
                        { alternateHosts = it },
                        placeholder = "https://backup.example:443",
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TvButton(
                        "Save and reconnect",
                        {
                            val alternates = alternateHosts.split(',', '\n')
                                .map(String::trim)
                                .filter(String::isNotBlank)
                            val next = if (sourceType == SourceType.M3U) {
                                Credentials.m3u(playlistUrl, alternates)
                            } else {
                                Credentials.xtream(server, username, password).copy(alternateServers = alternates)
                            }
                            viewModel.updateProvider(next)
                            editingProvider = false
                        },
                        Modifier.width(142.dp),
                    )
                    TvButton("Cancel", { editingProvider = false }, Modifier.width(76.dp), TvButtonStyle.Secondary)
                }
            }
        }

        SettingsCard("Appearance", "Choose a skin. The layout remains identical while the background, panels, focus ring, and highlights change hue.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeChoice(
                        theme = theme,
                        selected = state.settings.theme == theme,
                        onClick = { viewModel.updateSettings(state.settings.copy(theme = theme)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingsCard("Favorites & Lists", "Favorites live inside the Live TV, Movies, and Series category wheels, matching webOS v0.7.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvChip(
                    label = if (state.settings.favoriteAutoSort) "Auto-sort A–Z: On" else "Auto-sort A–Z: Off",
                    selected = state.settings.favoriteAutoSort,
                    onClick = { viewModel.updateSettings(state.settings.copy(favoriteAutoSort = !state.settings.favoriteAutoSort)) },
                )
                TvButton("Manage favorite lists", viewModel::openFavoriteGroupsManager, Modifier.width(162.dp), TvButtonStyle.Secondary)
            }
        }

        SettingsCard(
            "Provider category visibility",
            "Hide provider categories from the app, including adult or XXX groups. Hidden content is removed from category wheels, Home, and Favorites. This local filter is not PIN-protected.",
        ) {
            val hiddenCount = state.settings.hiddenCatalogCategories.values.sumOf { it.size }
            Text(
                if (hiddenCount == 0) "All provider categories are visible." else "$hiddenCount provider categories hidden.",
                color = palette().muted,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(8.dp))
            TvButton("Manage category visibility", viewModel::openCategoryVisibility, Modifier.width(176.dp), TvButtonStyle.Secondary)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsCard(
                title = "Playback compatibility",
                description = "Automatic mode tries HLS and MPEG-TS. VOD and episodes use their provider container.",
                modifier = Modifier.weight(1f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreamFormat.entries.forEach { format ->
                        SettingChoice(
                            label = format.label,
                            selected = state.settings.streamFormat == format,
                            onClick = { viewModel.updateSettings(state.settings.copy(streamFormat = format)) },
                        )
                    }
                }
            }
            SettingsCard(
                title = "Performance",
                description = "Category data plus Movie/Series metadata are cached locally. Artwork uses Coil/HTTP disk cache for fast revisits across app restarts.",
                modifier = Modifier.weight(1f),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 5).forEach { count ->
                        TvChip(
                            label = when (count) { 2 -> "2 · Low"; 3 -> "3 · Balanced"; else -> "5 · Fast" },
                            selected = state.settings.maxCachedCategories == count,
                            onClick = { viewModel.updateSettings(state.settings.copy(maxCachedCategories = count)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TvButton("Clear media caches", viewModel::clearCatalogCache, Modifier.width(138.dp), TvButtonStyle.Secondary)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsCard(
                title = "Native networking",
                description = "Android TV connects directly to your provider. The optional webOS CORS bridge is not required on this platform.",
                modifier = Modifier.weight(1f),
            ) {
                DiagnosticPill("Direct provider access")
                DiagnosticPill("HTTP and HTTPS supported")
                DiagnosticPill("Automatic container fallback")
            }
            SettingsCard(
                title = "Catalog diagnostics",
                description = "Provider catalogs remain category-scoped so the full library is never loaded into TV memory at once.",
                modifier = Modifier.weight(1f),
            ) {
                DiagnosticPill("${state.loadedItems.values.sumOf { it.size }} items currently indexed")
                DiagnosticPill("${state.categories.values.sumOf { it.size }} provider categories")
                DiagnosticPill("Adaptive lazy rendering active")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsCard(
                title = "About XtreamlyTV",
                description = "Open-source living-room IPTV client. No channels or subscriptions are included.",
                modifier = Modifier.weight(1f),
            ) {
                ProviderSummaryRow("Version", BuildConfig.VERSION_NAME)
                ProviderSummaryRow("Application ID", "com.github.xtreamlytv.androidtv")
                ProviderSummaryRow("Platform", "Android TV ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                ProviderSummaryRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                ProviderSummaryRow("Playback", "AndroidX Media3 ExoPlayer")
            }
            SettingsCard(
                title = "Privacy & history",
                description = "No analytics, advertising, or tracking. Credentials, favorites, groups, recent items, and resume positions remain on this TV.",
                modifier = Modifier.weight(1f),
            ) {
                Text("${state.recent.size} recent items · ${state.progress.size} resume positions", color = palette().muted, fontSize = 9.sp)
                Spacer(Modifier.height(9.dp))
                TvButton("Clear watch history", viewModel::clearHistory, Modifier.width(132.dp), TvButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = palette()
    Column(
        modifier
            .background(colors.panel.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(description, color = colors.muted, fontSize = 8.5.sp, lineHeight = 11.sp)
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun ProviderSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = palette().muted, fontSize = 8.5.sp, modifier = Modifier.width(78.dp))
        Text(value, color = palette().text, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ThemeChoice(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val preview = paletteFor(theme)
    TvSurface(modifier.height(50.dp), onClick = onClick, active = selected) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(24.dp)
                    .height(24.dp)
                    .background(Brush.linearGradient(listOf(preview.accent, preview.accentSecondary)), RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(7.dp))
            Text(theme.label, color = palette().text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) Text("✓", color = palette().accent, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TvSurface(Modifier.fillMaxWidth().height(32.dp), onClick = onClick, active = selected) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, color = if (selected) palette().accent else palette().text, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text(if (selected) "✓" else "", color = palette().accent, fontSize = 9.sp)
        }
    }
}

@Composable
private fun DiagnosticPill(label: String) {
    Text(
        label,
        color = palette().text.copy(alpha = 0.88f),
        fontSize = 8.sp,
        modifier = Modifier
            .padding(bottom = 5.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun maskPlaylistUrl(value: String): String {
    if (value.isBlank()) return "Not configured"
    return runCatching {
        val uri = URI(value)
        val host = uri.host
        if (host.isNullOrBlank()) {
            "Configured playlist"
        } else {
            val port = if (uri.port >= 0) ":" + uri.port else ""
            uri.scheme + "://" + host + port + "/…"
        }
    }.getOrDefault("Configured playlist")
}
