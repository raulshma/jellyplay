package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Direct [PhotoFolderChildUrlsStore] tests: the prefetch/merge/cap policy
 * previously testable only through the whole HomeViewModel (Robolectric) is
 * now pinned without any Android lifecycle stack. Plain JUnit +
 * [MainDispatcherRule] + MockK, mirroring HomeRefresherTest's scope hand-off
 * pattern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoFolderChildUrlsStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var prefetcher: PhotoFolderPrefetcher
    private var storeScope: CoroutineScope? = null

    @Before
    fun setUp() {
        prefetcher = mockk(relaxed = true)
    }

    @After
    fun stopStore() {
        storeScope?.cancel()
    }

    private fun TestScope.buildStore(): PhotoFolderChildUrlsStore {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        storeScope = scope
        return PhotoFolderChildUrlsStore(scope = scope, prefetcher = prefetcher)
    }

    @Test
    fun prefetch_callsPrefetcher_andUpdatesState() = runTest {
        val items = listOf(folder("p1"))
        coEvery { prefetcher.prefetch(items, any()) } returns mapOf("p1" to listOf("url1", "url2"))
        val store = buildStore()

        store.prefetch(items)
        advanceUntilIdle()

        assertEquals(mapOf("p1" to listOf("url1", "url2")), store.childUrls.value)
    }

    @Test
    fun prefetch_passesAlreadyCachedKeysAsAlreadyFetched() = runTest {
        val first = listOf(folder("p1"), folder("p2"))
        coEvery { prefetcher.prefetch(first, any()) } returns mapOf(
            "p1" to listOf("url1"),
            "p2" to listOf("url2"),
        )
        val store = buildStore()
        store.prefetch(first)
        advanceUntilIdle()

        // Second prefetch over the same folders must hand the cached keys to
        // the prefetcher so it skips them (the incremental-fetch contract).
        val second = listOf(folder("p1"), folder("p2"))
        coEvery { prefetcher.prefetch(second, alreadyFetched = setOf("p1", "p2")) } returns emptyMap()
        store.prefetch(second)
        advanceUntilIdle()

        // Still the first round's results — nothing new was fetched.
        assertEquals(
            mapOf("p1" to listOf("url1"), "p2" to listOf("url2")),
            store.childUrls.value,
        )
    }

    @Test
    fun prefetch_mergesNewResults_intoExistingEntries() = runTest {
        coEvery { prefetcher.prefetch(listOf(folder("p1")), any()) } returns mapOf("p1" to listOf("url1"))
        coEvery { prefetcher.prefetch(listOf(folder("p2")), any()) } returns mapOf("p2" to listOf("url2"))
        val store = buildStore()

        store.prefetch(listOf(folder("p1")))
        advanceUntilIdle()
        store.prefetch(listOf(folder("p2")))
        advanceUntilIdle()

        assertEquals(
            mapOf("p1" to listOf("url1"), "p2" to listOf("url2")),
            store.childUrls.value,
        )
    }

    @Test
    fun prefetch_evictsOldestEntries_beyondCacheCap() = runTest {
        val store = buildStore()
        // Fill beyond the cap: ids f1..f55, one prefetch each, insertion order
        // = eviction order (oldest dropped first).
        (1..55).forEach { i ->
            val folder = listOf(folder("f$i"))
            coEvery { prefetcher.prefetch(folder, any()) } returns mapOf("f$i" to listOf("u$i"))
            store.prefetch(folder)
            advanceUntilIdle()
        }

        val cached = store.childUrls.value
        assertEquals(50, cached.size)
        // The five oldest ids were evicted; the newest survive in order.
        assertEquals(listOf("f6", "f55"), listOf(cached.keys.first(), cached.keys.last()))
    }

    private fun folder(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.PHOTO_FOLDER)
}
