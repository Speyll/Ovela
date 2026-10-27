package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("m3u/$name")) {
            "Missing fixture $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun parsesMetadataCategoriesAndMovieUrls() {
        val playlist = M3uParser.parse(fixture("basic.m3u"))
        assertEquals(listOf("https://epg.example/guide.xml"), playlist.epgUrls)
        assertEquals(2, playlist.entries.size)
        assertEquals("News One", playlist.entries[0].name)
        assertEquals(ContentType.LIVE, playlist.entries[0].type)
        assertEquals("Movies", playlist.entries[1].group)
        assertEquals(ContentType.MOVIE, playlist.entries[1].type)
        assertEquals("mp4", playlist.entries[1].containerExtension)
        assertEquals("https://img.example/news.png", playlist.entries[0].logoUrl)
        assertEquals(2, playlist.categories.size)
    }

    @Test
    fun keepsCommasInTitlesAndQuotedAttributes() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="news,us" group-title="News, Weather",World Report, Evening
            https://provider.example/live/news.m3u8
            """.trimIndent(),
        )
        assertEquals(1, playlist.entries.size)
        assertEquals("World Report, Evening", playlist.entries[0].name)
        assertEquals("News, Weather", playlist.entries[0].group)
        assertEquals("news,us", playlist.entries[0].tvgId)
    }

    @Test
    fun preservesValidEntriesAroundMalformedMetadata() {
        val playlist = M3uParser.parse(fixture("malformed.m3u"))
        assertEquals(3, playlist.entries.size)
        assertTrue(playlist.warnings.any { it.contains("Broken without URL") })
        assertEquals("bare-entry.ts", playlist.entries.last().name)
    }

    @Test
    fun rewritesAlternateHostWithoutLosingQueryParameters() {
        val playlist = M3uParser.parse(fixture("query-urls.m3u"))
        val rewritten = M3uParser.rewriteHost(
            playlist.entries[0].url,
            "https://backup.example:8443/",
        )
        assertEquals(
            "https://backup.example:8443/live/channel.m3u8?token=abc&expires=123",
            rewritten,
        )
        assertNotNull(rewritten)
    }
}
