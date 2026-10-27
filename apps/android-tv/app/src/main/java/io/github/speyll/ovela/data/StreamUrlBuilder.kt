package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.StreamFormat
import java.net.URLEncoder

object StreamUrlBuilder {
    fun candidates(
        credentials: Credentials,
        item: CatalogItem,
        preferredFormat: StreamFormat = StreamFormat.AUTO,
    ): List<String> {
        val direct = item.streamUrl?.takeIf(String::isNotBlank)
        if (credentials.isM3u || direct != null) {
            if (direct == null) return emptyList()
            return buildList {
                add(direct)
                credentials.alternateServers.forEach { host ->
                    M3uParser.rewriteHost(direct, host)?.let { if (it !in this) add(it) }
                }
            }
        }
        val servers = buildList {
            ProviderUrl.normalize(credentials.server).let(::add)
            credentials.alternateServers.forEach { value ->
                runCatching { ProviderUrl.normalize(value) }.getOrNull()?.let { if (it !in this) add(it) }
            }
        }
        return servers.flatMap { server ->
            candidatesForServer(credentials.copy(server = server), item, preferredFormat)
        }.distinct()
    }

    private fun candidatesForServer(
        credentials: Credentials,
        item: CatalogItem,
        preferredFormat: StreamFormat,
    ): List<String> {
        val server = credentials.server.trim().trimEnd('/')
        val user = credentials.username.encodePathSegment()
        val pass = credentials.password.encodePathSegment()
        return when (item.type) {
            ContentType.LIVE -> {
                val hls = "$server/live/$user/$pass/${item.id}.m3u8"
                val transportStream = "$server/live/$user/$pass/${item.id}.ts"
                when (preferredFormat) {
                    StreamFormat.AUTO, StreamFormat.HLS -> listOf(hls, transportStream)
                    StreamFormat.MPEG_TS -> listOf(transportStream, hls)
                }
            }
            ContentType.MOVIE -> listOf(
                "$server/movie/$user/$pass/${item.id}.${safeExtension(item.containerExtension, "mp4")}",
                "$server/movie/$user/$pass/${item.id}.mp4",
            ).distinct()
            ContentType.EPISODE -> listOf(
                "$server/series/$user/$pass/${item.id}.${safeExtension(item.containerExtension, "mp4")}",
                "$server/series/$user/$pass/${item.id}.mp4",
            ).distinct()
            ContentType.SERIES -> emptyList()
        }
    }

    private fun String.encodePathSegment(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

    private fun safeExtension(value: String?, fallback: String): String =
        value.orEmpty().lowercase().filter { it.isLetterOrDigit() }.ifBlank { fallback }
}
