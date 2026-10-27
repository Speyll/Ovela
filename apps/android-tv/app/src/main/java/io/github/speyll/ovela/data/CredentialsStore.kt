package io.github.speyll.ovela.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.SourceType

class CredentialsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        // Historical store name, kept verbatim through the Ovela rename. Changing
        // it would orphan every existing install's encrypted sign-in details.
        "xtreamlytv.credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Credentials? {
        val sourceType = SourceType.fromId(preferences.getString("sourceType", null))
        val alternates = preferences.getStringSet("alternateServers", null).orEmpty().toList()
        if (sourceType == SourceType.M3U) {
            val playlistUrl = preferences.getString("playlistUrl", null)?.trim().orEmpty()
            return if (playlistUrl.isBlank()) null
            else Credentials(
                sourceType = SourceType.M3U,
                playlistUrl = playlistUrl,
                alternateServers = alternates,
            )
        }
        val server = preferences.getString("server", null)?.trim().orEmpty()
        val username = preferences.getString("username", null)?.trim().orEmpty()
        val password = preferences.getString("password", null).orEmpty()
        return if (server.isBlank() || username.isBlank() || password.isBlank()) null
        else Credentials(server, username, password, alternateServers = alternates)
    }

    fun save(credentials: Credentials) {
        preferences.edit()
            .putString("sourceType", credentials.sourceType.id)
            .putString("server", credentials.server.trimEnd('/'))
            .putString("username", credentials.username.trim())
            .putString("password", credentials.password)
            .putString("playlistUrl", credentials.playlistUrl.trim())
            .putStringSet("alternateServers", credentials.alternateServers.toSet())
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
