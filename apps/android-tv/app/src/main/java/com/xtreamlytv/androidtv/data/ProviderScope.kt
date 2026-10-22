package com.xtreamlytv.androidtv.data

import com.xtreamlytv.androidtv.model.Credentials
import java.security.MessageDigest

internal fun providerScope(credentials: Credentials): String {
    val normalized = if (credentials.isM3u) {
        "m3u\n" + credentials.playlistUrl.trim()
    } else {
        ProviderUrl.normalize(credentials.server).lowercase() + "\n" + credentials.username.trim()
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
