package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.ContentType
import java.net.URI
import java.security.MessageDigest

data class M3uEntry(
    val id: String,
    val name: String,
    val url: String,
    val group: String,
    val categoryId: String,
    val type: ContentType,
    val logoUrl: String?,
    val tvgId: String?,
    val tvgName: String?,
    val channelNumber: String?,
    val containerExtension: String?,
)

data class M3uCategory(
    val id: String,
    val name: String,
    val type: ContentType,
)

data class M3uPlaylist(
    val entries: List<M3uEntry>,
    val categories: List<M3uCategory>,
    val epgUrls: List<String>,
    val warnings: List<String>,
)

object M3uParser {
    private val attributePattern = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))""")

    fun parse(text: String): M3uPlaylist {
        val lines = text.removePrefix("\uFEFF").lineSequence().toList()
        val entries = mutableListOf<M3uEntry>()
        val warnings = mutableListOf<String>()
        val epgUrls = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var expected = false
        var metadata = emptyMap<String, String>()
        var displayName = ""
        var pendingGroup = ""
        var sawHeader = false

        fun addEntry(url: String) {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank() || cleanUrl.startsWith("#")) return
            val group = metadata["group-title"].orEmpty().ifBlank { pendingGroup }.trim()
            val type = contentType(cleanUrl, group)
            val name = displayName.ifBlank { metadata["tvg-name"].orEmpty() }.ifBlank { nameFromUrl(cleanUrl) }
            val id = stableId(cleanUrl, name, group)
            if (!seen.add(id)) {
                warnings += "Duplicate playlist entry skipped: $name"
                return
            }
            val categoryId = categoryId(group)
            entries += M3uEntry(
                id = id,
                name = name,
                url = cleanUrl,
                group = group,
                categoryId = categoryId,
                type = type,
                logoUrl = metadata["tvg-logo"]?.trim()?.takeIf(String::isNotBlank),
                tvgId = metadata["tvg-id"]?.trim()?.takeIf(String::isNotBlank),
                tvgName = metadata["tvg-name"]?.trim()?.takeIf(String::isNotBlank),
                channelNumber = (metadata["tvg-chno"] ?: metadata["channel-number"])?.trim()?.takeIf(String::isNotBlank),
                containerExtension = if (type == ContentType.MOVIE) extension(cleanUrl).takeIf(String::isNotBlank) else null,
            )
        }

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    sawHeader = true
                    val attrs = parseAttributes(line.substringAfter("#EXTM3U", ""))
                    listOf("url-tvg", "x-tvg-url").forEach { key ->
                        attrs[key].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).forEach { value ->
                            if (value !in epgUrls) epgUrls += value
                        }
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    if (expected) warnings += "Playlist entry without a URL skipped: ${displayName.ifBlank { "unknown" }}"
                    val body = line.substringAfter("#EXTINF:", "")
                    val comma = titleSeparator(body)
                    val info = if (comma >= 0) body.substring(0, comma) else body
                    displayName = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    metadata = parseAttributes(info)
                    expected = true
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter("#EXTGRP:", "").trim()
                }
                line.startsWith("#") -> Unit
                expected -> {
                    addEntry(line)
                    expected = false
                    displayName = ""
                    metadata = emptyMap()
                    pendingGroup = ""
                }
                else -> addEntry(line)
            }
        }
        if (expected) warnings += "Playlist entry without a URL skipped: ${displayName.ifBlank { "unknown" }}"
        if (!sawHeader && lines.any(String::isNotBlank)) warnings += "Playlist is missing the #EXTM3U header."
        val categories = entries.map { M3uCategory(it.categoryId, it.group.ifBlank { "Ungrouped" }, it.type) }
            .distinctBy { it.id to it.type }
        return M3uPlaylist(entries, categories, epgUrls, warnings)
    }

    private fun titleSeparator(value: String): Int {
        var quote = '\u0000'
        value.forEachIndexed { index, character ->
            if (quote != '\u0000') {
                if (character == quote) quote = '\u0000'
            } else if (character == '"' || character == '\'') {
                quote = character
            } else if (character == ',') {
                return index
            }
        }
        return -1
    }

    fun parseAttributes(value: String): Map<String, String> =
        attributePattern.findAll(value).associate { match ->
            val key = match.groupValues[1].lowercase()
            key to (match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] })
        }

    fun contentType(url: String, group: String): ContentType {
        val extension = extension(url)
        if (extension in movieExtensions) return ContentType.MOVIE
        if (Regex("""\b(vod|movies?|films?|cinema)\b""", RegexOption.IGNORE_CASE).containsMatchIn(group)) return ContentType.MOVIE
        return ContentType.LIVE
    }

    fun extension(url: String): String = runCatching {
        URI(url).path.orEmpty().substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    }.getOrDefault(url.substringBefore('?').substringAfterLast('.', "").lowercase())

    fun rewriteHost(url: String, host: String): String? {
        val normalizedHost = normalizeHost(host) ?: return null
        return runCatching {
            val original = URI(url)
            val alternate = URI(normalizedHost)
            URI(
                alternate.scheme ?: original.scheme,
                alternate.userInfo,
                alternate.host,
                alternate.port,
                original.path,
                original.query,
                original.fragment,
            ).toString()
        }.getOrNull()
    }

    private fun normalizeHost(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (Regex("""^[a-z][a-z0-9+.-]*://""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return runCatching {
            val uri = URI(withScheme)
            require(!uri.host.isNullOrBlank()) { "Host is missing" }
            withScheme
        }.getOrNull()
    }

    private fun nameFromUrl(url: String): String = runCatching {
        URI(url).path.orEmpty().substringAfterLast('/').substringBefore('?').ifBlank { "Untitled channel" }
    }.getOrDefault("Untitled channel")

    private fun categoryId(group: String): String = "m3u-group-" + digest(group.ifBlank { "ungrouped" }.lowercase()).take(12)

    private fun stableId(url: String, name: String, group: String): String =
        "m3u-" + digest("$url\n$name\n$group").take(20)

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val movieExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "webm", "mpg", "mpeg")
}
