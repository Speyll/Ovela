package com.xtreamlytv.androidtv.data

import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.Credentials
import com.xtreamlytv.androidtv.model.SourceType
import com.xtreamlytv.androidtv.model.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlBuilderTest {
    private val credentials = Credentials("https://provider.example/", "user name", "p/1")

    @Test
    fun liveCandidatesPreferHlsThenTransportStream() {
        val item = CatalogItem("42", ContentType.LIVE, "Channel")
        assertEquals(
            listOf(
                "https://provider.example/live/user%20name/p%2F1/42.m3u8",
                "https://provider.example/live/user%20name/p%2F1/42.ts",
            ),
            StreamUrlBuilder.candidates(credentials, item),
        )
    }

    @Test
    fun liveCandidatesCanPreferTransportStream() {
        val item = CatalogItem("42", ContentType.LIVE, "Channel")
        assertEquals(
            listOf(
                "https://provider.example/live/user%20name/p%2F1/42.ts",
                "https://provider.example/live/user%20name/p%2F1/42.m3u8",
            ),
            StreamUrlBuilder.candidates(credentials, item, StreamFormat.MPEG_TS),
        )
    }

    @Test
    fun movieExtensionIsSanitized() {
        val item = CatalogItem("7", ContentType.MOVIE, "Movie", containerExtension = "m.p4")
        assertEquals(
            "https://provider.example/movie/user%20name/p%2F1/7.mp4",
            StreamUrlBuilder.candidates(credentials, item).first(),
        )
    }

    @Test
    fun m3uCandidatesKeepDirectUrlAndAlternateHostRewrite() {
        val m3u = Credentials(
            sourceType = SourceType.M3U,
            playlistUrl = "https://provider.example/list.m3u",
            alternateServers = listOf("https://backup.example:8443"),
        )
        val item = CatalogItem(
            "m3u-1",
            ContentType.LIVE,
            "News",
            streamUrl = "https://origin.example/live/news.m3u8?token=abc",
        )
        assertEquals(
            listOf(
                "https://origin.example/live/news.m3u8?token=abc",
                "https://backup.example:8443/live/news.m3u8?token=abc",
            ),
            StreamUrlBuilder.candidates(m3u, item),
        )
    }

    @Test
    fun xtreamAlternateHostsAreTriedAfterPrimaryHost() {
        val withBackup = credentials.copy(alternateServers = listOf("https://backup.example"))
        val item = CatalogItem("42", ContentType.LIVE, "Channel")
        assertEquals(
            listOf(
                "https://provider.example/live/user%20name/p%2F1/42.m3u8",
                "https://provider.example/live/user%20name/p%2F1/42.ts",
                "https://backup.example/live/user%20name/p%2F1/42.m3u8",
                "https://backup.example/live/user%20name/p%2F1/42.ts",
            ),
            StreamUrlBuilder.candidates(withBackup, item),
        )
    }
}
