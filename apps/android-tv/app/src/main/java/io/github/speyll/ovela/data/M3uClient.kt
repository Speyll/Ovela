package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.EpgProgram
import io.github.speyll.ovela.model.ProviderSummary
import io.github.speyll.ovela.model.StreamFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class M3uClient(
    private val credentials: Credentials,
    private val http: OkHttpClient = defaultHttpClient(),
) : SourceClient {
    private val lock = Mutex()
    private var loaded: M3uPlaylist? = null

    override suspend fun authenticate(): ProviderSummary = withContext(Dispatchers.IO) {
        val playlist = playlist()
        ProviderSummary(
            username = "M3U playlist",
            status = "${playlist.entries.size} entries",
            expiration = null,
        )
    }

    override suspend fun categories(type: ContentType): List<Category> = withContext(Dispatchers.IO) {
        if (type == ContentType.SERIES || type == ContentType.EPISODE) return@withContext emptyList()
        playlist().categories
            .filter { it.type == type }
            .map { Category(it.id, it.name) }
    }

    override suspend fun items(type: ContentType, categoryId: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (type == ContentType.SERIES || type == ContentType.EPISODE) return@withContext emptyList()
        playlist().entries
            .asSequence()
            .filter { it.type == type && (categoryId == "all" || it.categoryId == categoryId) }
            .map(::toCatalogItem)
            .toList()
    }

    override suspend fun movieInfo(movie: CatalogItem): CatalogItem = movie

    override suspend fun seriesDetail(series: CatalogItem): Pair<CatalogItem, List<CatalogItem>> = series to emptyList()

    override suspend fun shortEpg(streamId: String, limit: Int): List<EpgProgram> = emptyList()

    override fun streamCandidates(item: CatalogItem, preferredFormat: StreamFormat): List<String> {
        val url = item.streamUrl ?: return emptyList()
        val candidates = mutableListOf(url)
        credentials.alternateServers.forEach { host ->
            M3uParser.rewriteHost(url, host)?.let { if (it !in candidates) candidates += it }
        }
        return candidates
    }

    private suspend fun playlist(): M3uPlaylist = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            M3uParser.parse(readPlaylist()).also { playlist ->
                if (playlist.entries.isEmpty()) error("The playlist did not contain any playable entries.")
            }
        }.also { loaded = it }
    }

    private fun readPlaylist(): String {
        val source = credentials.playlistUrl.trim()
        if (source.isBlank()) error("Enter an M3U playlist URL.")
        if (source.startsWith("file:")) {
            val file = runCatching { File(URI(source)) }.getOrElse { error("The imported playlist URL is invalid.") }
            if (!file.isFile) error("The imported playlist is no longer available.")
            if (file.length() > MAX_PLAYLIST_BYTES) error("Playlist is too large to load safely.")
            return file.readText()
        }
        if (!source.startsWith("http://", true) && !source.startsWith("https://", true)) {
            error("M3U playlists must use an HTTP, HTTPS, or imported file URL.")
        }
        val request = Request.Builder().url(source).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Playlist returned HTTP ${response.code}.")
            val body = response.body ?: error("Playlist response was empty.")
            val length = body.contentLength()
            if (length > MAX_PLAYLIST_BYTES) error("Playlist is too large to load safely.")
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_PLAYLIST_BYTES) {
                        error("Playlist is too large to load safely.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun toCatalogItem(entry: M3uEntry): CatalogItem = CatalogItem(
        id = entry.id,
        type = entry.type,
        name = entry.name,
        categoryId = entry.categoryId,
        imageUrl = entry.logoUrl,
        streamUrl = entry.url,
        containerExtension = entry.containerExtension,
        channelNumber = entry.channelNumber,
        genre = entry.group.takeIf(String::isNotBlank),
    )

    companion object {
        private const val MAX_PLAYLIST_BYTES = 32 * 1024 * 1024

        private fun defaultHttpClient() = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
