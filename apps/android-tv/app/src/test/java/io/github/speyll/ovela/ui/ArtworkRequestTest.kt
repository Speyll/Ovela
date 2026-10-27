package io.github.speyll.ovela.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkRequestTest {
    @Test
    fun boundsOriginalTmdbArtworkToATelevisionBackdropSize() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            backdropRequestUrl("https://image.tmdb.org/t/p/original/backdrop.jpg"),
        )
    }

    @Test
    fun boundsTmdbPosterArtworkForCatalogCards() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/poster.jpg",
            posterRequestUrl("https://image.tmdb.org/t/p/w600_and_h900_bestv2/poster.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/poster.jpg",
            posterRequestUrl("https://image.tmdb.org/t/p/original/poster.jpg"),
        )
    }

    @Test
    fun replacesExistingTmdbSizeVariants() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            backdropRequestUrl("https://image.tmdb.org/t/p/w600_and_h900_bestv2/backdrop.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            backdropRequestUrl("https://image.tmdb.org/t/p/w780/backdrop.jpg"),
        )
    }

    @Test
    fun leavesProviderAndLocalArtworkUntouched() {
        assertEquals(
            "https://provider.example/art/backdrop.jpg",
            backdropRequestUrl("https://provider.example/art/backdrop.jpg"),
        )
        assertEquals(
            "file:///android_asset/branding/backdrop.jpg",
            backdropRequestUrl("file:///android_asset/branding/backdrop.jpg"),
        )
    }

    @Test
    fun rejectsMissingArtwork() {
        assertNull(backdropRequestUrl(null))
        assertNull(backdropRequestUrl("   "))
    }
}
