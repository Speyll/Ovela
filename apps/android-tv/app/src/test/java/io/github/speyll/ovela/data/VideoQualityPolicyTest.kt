package io.github.speyll.ovela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityPolicyTest {
    @Test
    fun fixedBitrateStreamWithOneRenditionOffersNoChoice() {
        assertFalse(VideoQualityPolicy.supportsSelection(listOf(1080)))
        assertFalse(VideoQualityPolicy.supportsSelection(emptyList()))
        assertFalse(VideoQualityPolicy.supportsSelection(listOf(0, -1)))
    }

    @Test
    fun optionsAreDeduplicatedAndOrderedHighToLow() {
        assertEquals(
            listOf(2160, 1080, 720, 480),
            VideoQualityPolicy.options(listOf(720, 1080, 720, 0, 2160, -1, 480, 1080)),
        )
    }

    @Test
    fun multipleRealRenditionsEnableSelection() {
        assertTrue(VideoQualityPolicy.supportsSelection(listOf(1080, 720)))
    }
}
