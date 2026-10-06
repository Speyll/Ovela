package com.xtreamlytv.androidtv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCachePolicyTest {
    @Test
    fun completeSeriesBoundaryIsExplicit() {
        assertTrue(shouldCacheSeriesEpisodes(319))
        assertTrue(shouldCacheSeriesEpisodes(320))
        assertFalse(shouldCacheSeriesEpisodes(321))
    }
}
