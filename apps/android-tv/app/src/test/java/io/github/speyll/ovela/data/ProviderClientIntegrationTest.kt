package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.Credentials
import io.github.speyll.ovela.model.StreamFormat
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderClientIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: LoopbackServer

    @Before
    fun startServer() {
        server = LoopbackServer()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun authenticatesAndParsesXtreamCatalogsFromLoopback() = runBlocking {
        val client = XtreamClient(credentials("demo user", "p@ss"))

        val summary = client.authenticate()
        assertEquals("mock-user", summary.username)
        assertEquals("Active", summary.status)

        val liveCategories = client.categories(ContentType.LIVE)
        assertEquals(listOf("1", "2"), liveCategories.map { it.id })
        assertEquals("Fixture News", liveCategories.first().name)

        val live = client.items(ContentType.LIVE, "1")
        assertEquals(1, live.size)
        assertEquals("Fixture News One", live.single().name)
        assertEquals("1", live.single().channelNumber)

        val movies = client.items(ContentType.MOVIE, "10")
        assertEquals(1, movies.size)
        assertEquals("mp4", movies.single().containerExtension)

        val richMovie = client.movieInfo(movies.single())
        assertEquals("Fixture Movie", richMovie.name)
        assertEquals("1080p", richMovie.resolution)
        assertEquals("Stereo", richMovie.audioLayout)

        val series = client.items(ContentType.SERIES, "20").single()
        val detail = client.seriesDetail(series)
        assertEquals("Fixture Series", detail.first.name)
        assertEquals(1, detail.second.size)
        assertEquals("Fixture Episode", detail.second.single().name)
        assertEquals(1, detail.second.single().season)
    }

    @Test
    fun buildsPlaybackUrlsFromParsedXtreamCatalogItems() = runBlocking {
        val client = XtreamClient(credentials("demo", "p@ss"))
        val base = "http://127.0.0.1:${server.port}"

        val live = client.items(ContentType.LIVE, "1").single()
        assertEquals(
            listOf(
                "$base/live/demo/p%40ss/1001.m3u8",
                "$base/live/demo/p%40ss/1001.ts",
            ),
            client.streamCandidates(live, StreamFormat.AUTO),
        )

        val movie = client.items(ContentType.MOVIE, "10").single()
        assertEquals(
            listOf("$base/movie/demo/p%40ss/2001.mp4"),
            client.streamCandidates(movie, StreamFormat.AUTO),
        )

        val series = client.items(ContentType.SERIES, "20").single()
        val episode = client.seriesDetail(series).second.single()
        assertEquals("3001-1-1", episode.id)
        assertEquals(
            listOf("$base/series/demo/p%40ss/3001-1-1.mp4"),
            client.streamCandidates(episode, StreamFormat.AUTO),
        )
    }

    @Test
    fun followsLiveRedirectToSeparateMediaHostAndLoadsHlsPlaylist() {
        val media = LoopbackServer("127.0.0.2")
        try {
            server.redirectLiveTo = "http://127.0.0.2:${media.port}/media/hls/index.m3u8"
            val client = XtreamClient(credentials("demo", "p@ss"))
            val live = runBlocking { client.items(ContentType.LIVE, "1").single() }
            val candidate = client.streamCandidates(live, StreamFormat.AUTO).first()
            assertEquals(
                "http://127.0.0.1:${server.port}/live/demo/p%40ss/1001.m3u8",
                candidate,
            )
            assertTrue(!candidate.contains("127.0.0.2"))

            val playlistConnection = URI(candidate).toURL().openConnection() as HttpURLConnection
            playlistConnection.instanceFollowRedirects = true
            playlistConnection.connectTimeout = 5_000
            playlistConnection.readTimeout = 5_000
            playlistConnection.requestMethod = "GET"
            playlistConnection.useCaches = false
            playlistConnection.connect()
            try {
                assertEquals(200, playlistConnection.responseCode)
                assertEquals(
                    "http://127.0.0.2:${media.port}/media/hls/index.m3u8",
                    playlistConnection.url.toString(),
                )
                assertTrue(playlistConnection.contentType.orEmpty().contains("mpegurl", ignoreCase = true))
                assertEquals(
                    "#EXTM3U\n#EXTINF:4.0,\nsegment-000.ts\n",
                    playlistConnection.inputStream.bufferedReader().use { it.readText() },
                )
            } finally {
                playlistConnection.disconnect()
            }

            val segmentUrl = URI(candidate)
                .resolve("http://127.0.0.2:${media.port}/media/hls/index.m3u8")
                .resolve("segment-000.ts")
                .toString()
            assertEquals("http://127.0.0.2:${media.port}/media/hls/segment-000.ts", segmentUrl)
            val segmentConnection = URI(segmentUrl).toURL().openConnection() as HttpURLConnection
            segmentConnection.connectTimeout = 5_000
            segmentConnection.readTimeout = 5_000
            segmentConnection.requestMethod = "GET"
            segmentConnection.useCaches = false
            segmentConnection.connect()
            try {
                assertEquals(200, segmentConnection.responseCode)
                assertEquals("fixture-segment", segmentConnection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                segmentConnection.disconnect()
            }
        } finally {
            media.close()
        }
    }

    @Test
    fun surfacesInvalidExpiredMalformedAndHttpFailures() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { XtreamClient(credentials("demo", "wrong")).authenticate() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { XtreamClient(credentials("bad-json", "p@ss")).authenticate() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { XtreamClient(credentials("http-error", "p@ss")).authenticate() }
        }
        val expired = runBlocking { XtreamClient(credentials("expired", "p@ss")).authenticate() }
        assertEquals("Expired", expired.status)
    }

    @Test
    fun handlesEmptyCatalogResponses() = runBlocking {
        val client = XtreamClient(credentials("empty", "p@ss"))
        assertNotNull(client.authenticate())
        assertTrue(client.categories(ContentType.LIVE).isEmpty())
        assertTrue(client.items(ContentType.LIVE, "1").isEmpty())
    }

    @Test
    fun loadsM3uAndBuildsAlternateCandidatesWithoutLosingQuery() = runBlocking {
        val client = M3uClient(
            Credentials.m3u(
                "http://127.0.0.1:${server.port}/playlist.m3u",
                listOf("https://backup.example:8443"),
            ),
        )
        assertEquals("3 entries", client.authenticate().status)
        assertEquals(
            2,
            client.categories(ContentType.LIVE).size + client.categories(ContentType.MOVIE).size,
        )

        val live = client.items(ContentType.LIVE, "all").first()
        val candidates = client.streamCandidates(live, StreamFormat.AUTO)
        assertEquals(2, candidates.size)
        assertEquals("http://127.0.0.1:${server.port}/media/hls/index.m3u8?token=abc", candidates[0])
        assertEquals("https://backup.example:8443/media/hls/index.m3u8?token=abc", candidates[1])
    }

    @Test
    fun rejectsEmptyM3uAndMissingRemotePlaylist() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                M3uClient(Credentials.m3u("http://127.0.0.1:${server.port}/empty.m3u")).authenticate()
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                M3uClient(Credentials.m3u("http://127.0.0.1:${server.port}/missing.m3u")).authenticate()
            }
        }
    }

    @Test
    fun rejectsOversizedLocalM3uBeforeReadingIt() {
        val file = temporaryFolder.newFile("large.m3u")
        RandomAccessFile(file, "rw").use { it.setLength(33L * 1024 * 1024) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { M3uClient(Credentials.m3u(file.toURI().toString())).authenticate() }
        }
    }

    @Test
    fun readsImportedM3uFromLocalFile() = runBlocking {
        val file = temporaryFolder.newFile("local.m3u")
        file.writeText(server.playlist)
        val client = M3uClient(Credentials.m3u(file.toURI().toString()))
        assertEquals("3 entries", client.authenticate().status)
        assertEquals("News One", client.items(ContentType.LIVE, "all").first().name)
    }

    private fun credentials(username: String, password: String): Credentials =
        Credentials.xtream("http://127.0.0.1:${server.port}", username, password)
}

private class LoopbackServer(host: String = "127.0.0.1") : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName(host))
    val port: Int = socket.localPort
    @Volatile
    var redirectLiveTo: String? = null
    val playlist = """
        #EXTM3U url-tvg="https://epg.example/guide.xml"
        #EXTINF:-1 tvg-id="news" tvg-logo="https://img.example/news.png" group-title="News",News One
        http://127.0.0.1:$port/media/hls/index.m3u8?token=abc
        #EXTINF:-1 group-title="Movies",Example Film
        http://127.0.0.1:$port/media/clip.mp4?token=abc
        #EXTINF:-1 group-title="News",Direct TS
        http://127.0.0.1:$port/media/clip.ts?token=abc
    """.trimIndent()
    private val worker = Thread({ serve() }, "fixture-loopback-server").apply {
        isDaemon = true
        start()
    }

    private fun serve() {
        while (!socket.isClosed) {
            try {
                socket.accept().use { connection -> handle(connection) }
            } catch (_: SocketException) {
                return
            } catch (_: IOException) {
                if (socket.isClosed) return
            }
        }
    }

    private fun handle(connection: Socket) {
        val input = connection.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        val target = requestLine.split(' ').getOrNull(1) ?: return
        while (true) {
            val header = input.readLine() ?: break
            if (header.isEmpty()) break
        }
        val uri = URI(target)
        val response = route(uri.path.orEmpty(), queryParameters(uri.rawQuery.orEmpty()))
        val body = response.body.toByteArray(StandardCharsets.UTF_8)
        val extraHeaders = response.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val output = connection.getOutputStream()
        output.write(
            (
                "HTTP/1.1 ${response.status} ${statusText(response.status)}\r\n" +
                    "Content-Type: ${response.contentType}\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    extraHeaders +
                    "Connection: close\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII),
        )
        output.write(body)
        output.flush()
    }

    private fun route(path: String, query: Map<String, String>): Response = when (path) {
        "/player_api.php" -> api(query)
        "/playlist.m3u" -> Response(200, playlist, "audio/x-mpegurl; charset=utf-8")
        "/empty.m3u" -> Response(200, "#EXTM3U\n", "audio/x-mpegurl; charset=utf-8")
        "/media/hls/index.m3u8" -> Response(
            200,
            "#EXTM3U\n#EXTINF:4.0,\nsegment-000.ts\n",
            "application/vnd.apple.mpegurl",
        )
        "/media/hls/segment-000.ts" -> Response(200, "fixture-segment", "video/mp2t")
        else -> {
            val redirect = redirectLiveTo
            if (path.startsWith("/live/") && redirect != null) {
                Response(302, "", "text/html", mapOf("Location" to redirect))
            } else {
                Response(404, """{"error":"not found"}""", "application/json")
            }
        }
    }

    private fun api(query: Map<String, String>): Response {
        val action = query["action"].orEmpty()
        val username = query["username"].orEmpty()
        val password = query["password"].orEmpty()
        if (username == "http-error") return Response(503, "{}", "application/json")
        if (username == "bad-json") return Response(200, "{", "application/json")
        if (password != "p@ss") {
            return Response(
                200,
                """{"user_info":{"auth":0,"message":"Synthetic credentials rejected"}}""",
                "application/json",
            )
        }
        if (username == "expired") {
            return Response(
                200,
                """{"user_info":{"auth":1,"username":"expired-user","status":"Expired","exp_date":"1700000000"}}""",
                "application/json",
            )
        }
        if (username == "empty" && action.isNotBlank()) return Response(200, "[]", "application/json")
        val body = when (action) {
            "" -> """{"user_info":{"auth":1,"username":"mock-user","status":"Active","exp_date":"1893456000"}}"""
            "get_live_categories" -> """[{"category_id":"1","category_name":"Fixture News"},{"category_id":"2","category_name":"Fixture Sports"}]"""
            "get_vod_categories" -> """[{"category_id":"10","category_name":"Fixture Movies"}]"""
            "get_series_categories" -> """[{"category_id":"20","category_name":"Fixture Series"}]"""
            "get_live_streams" -> """[{"stream_id":1001,"name":"Fixture News One","category_id":"1","num":"1"}]"""
            "get_vod_streams" -> """[{"stream_id":2001,"name":"Fixture Movie","category_id":"10","container_extension":"mp4"}]"""
            "get_series" -> """[{"series_id":3001,"name":"Fixture Series","category_id":"20","cover":"https://img.example/series.png"}]"""
            "get_vod_info" -> """{"info":{"name":"Fixture Movie","plot":"Fixture plot","duration_secs":"5400","video":{"height":1080},"audio":{"channels":2}},"movie_data":{"container_extension":"mp4"}}"""
            "get_series_info" -> """{"info":{"name":"Fixture Series"},"episodes":{"1":[{"id":"3001-1-1","title":"Fixture Episode","episode_num":1,"container_extension":"mp4"}]}}"""
            else -> return Response(400, """{"error":"unsupported action"}""", "application/json")
        }
        return Response(200, body, "application/json")
    }

    private fun queryParameters(rawQuery: String): Map<String, String> =
        rawQuery.split("&")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore("=")
                val value = pair.substringAfter("=", "")
                URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        302 -> "Found"
        400 -> "Bad Request"
        404 -> "Not Found"
        503 -> "Service Unavailable"
        else -> "Response"
    }

    override fun close() {
        socket.close()
        worker.join(2_000)
    }

    private data class Response(
        val status: Int,
        val body: String,
        val contentType: String,
        val headers: Map<String, String> = emptyMap(),
    )
}
