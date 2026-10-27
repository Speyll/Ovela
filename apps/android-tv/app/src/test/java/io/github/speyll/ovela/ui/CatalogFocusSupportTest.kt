package io.github.speyll.ovela.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogFocusSupportTest {
    @Test
    fun allocatesOnlyForAcquiredKeysInALargeCatalog() {
        var created = 0
        val cache = KeyedLazyCache<String, String> { key ->
            created += 1
            "focus:$key"
        }

        repeat(12) { index -> cache.acquire("live:$index") }

        assertEquals(12, created)
        assertEquals(12, cache.size)
    }

    @Test
    fun reusesTheSameValueForAKey() {
        var created = 0
        val cache = KeyedLazyCache<String, String> {
            created += 1
            "focus:${created}"
        }

        val first = cache.acquire("movie:1")
        val second = cache.acquire("movie:1")

        assertSame(first, second)
        assertEquals(1, created)
        assertEquals(1, cache.size)
    }

    @Test
    fun releaseOnlyRemovesTheMatchingValue() {
        val cache = KeyedLazyCache<String, String> { it }
        val requester = cache.acquire("series:1")

        cache.release("series:1", "stale")
        assertSame(requester, cache.get("series:1"))

        cache.release("series:1", requester)
        assertNull(cache.get("series:1"))
        assertEquals(0, cache.size)
    }

    @Test
    fun releaseKeepsAValueUntilTheLastOwnerReleasesIt() {
        var created = 0
        val cache = KeyedLazyCache<String, String> {
            created += 1
            "focus:${created}"
        }

        val first = cache.acquire("live:1")
        val second = cache.acquire("live:1")
        cache.release("live:1", first)

        assertSame(first, second)
        assertSame(first, cache.get("live:1"))
        assertEquals(1, cache.size)

        cache.release("live:1", second)
        assertNull(cache.get("live:1"))
    }

    @Test
    fun lookupDoesNotAllocateForAnUncomposedItem() {
        var created = 0
        val cache = KeyedLazyCache<String, String> {
            created += 1
            it
        }

        assertNull(cache.get("movie:50000"))
        assertEquals(0, created)
        assertEquals(0, cache.size)
    }
}
