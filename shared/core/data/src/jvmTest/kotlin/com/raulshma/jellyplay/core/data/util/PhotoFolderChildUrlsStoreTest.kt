package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Direct [PhotoFolderChildUrlsStore] tests: the prefetch/merge/cap policy
 * is pinned without any host view model or Android lifecycle stack. Plain
 * JUnit + MockK, mirroring [PhotoFolderPrefetcherTest]; the store's scope
 * is the runTest scheduler so `advanceUntilIdle` drives the launched
 * prefetch jobs (the home suite's scope hand-off pattern, without its
 * Main-dispatcher stack).
 */
class PhotoFolderChildUrlsStoreTest {

    private val prefetcher: PhotoFolderPrefetcher = mockk(relaxed = true)
    private var storeScope: CoroutineScope? = null

    @AfterTest
    fun stopStore() {
        storeScope?.cancel()
    }

    private fun TestScope.buildStore(): PhotoFolderChildUrlsStore {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        storeScope = scope
        return PhotoFolderChildUrlsStore(scope = scope, prefetcher = prefetcher)
    }

    @Test
    fun `prefetch calls the prefetcher and updates state`() = runTest {
        val items = listOf(folder("p1"))
        coEvery { prefetcher.prefetch(items, any()) } returns mapOf("p1" to listOf("url1", "url2"))
        val store = buildStore()

        store.prefetch(items)
        advanceUntilIdle()

        assertEquals(mapOf("p1" to listOf("url1", "url2")), store.childUrls.value)
    }

    @Test
    fun `prefetch passes already-cached keys as alreadyFetched`() = runTest {
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
    fun `prefetch merges new results into existing entries`() = runTest {
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
    fun `prefetch evicts oldest entries beyond the cache cap`() = runTest {
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
        assertEquals(PhotoFolderChildUrlsStore.PHOTO_FOLDER_CACHE_CAP, cached.size)
        // The five oldest ids were evicted; the newest survive in order.
        assertEquals(listOf("f6", "f55"), listOf(cached.keys.first(), cached.keys.last()))
    }

    private fun folder(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.PHOTO_FOLDER)
}
