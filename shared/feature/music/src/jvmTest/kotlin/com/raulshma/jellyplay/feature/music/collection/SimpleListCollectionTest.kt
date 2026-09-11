package com.raulshma.jellyplay.feature.music.collection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins [SimpleListCollection]'s load/refresh contract: the initial cache-
 * honouring load, the Throwable-carrying error state (the ladder renders the
 * kind's declared fallback under a null message — no strings baked here), and
 * the refresh supersession discipline (only the newest refresh may write
 * state back).
 */
class SimpleListCollectionTest {

    /** The VM-scope stand-in: same scheduler as the test, so advance* drives it. */
    private fun TestScope.collectionScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    @Test
    fun initialLoad_appliesItemsAndClearsLoading() = runTest {
        val collection = SimpleListCollection<String>(scope = collectionScope()) { _ ->
            Result.success(listOf("a", "b"))
        }
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), collection.items.value)
        assertFalse(collection.isLoading.value)
        assertNull(collection.error.value)
    }

    @Test
    fun failure_surfacesTheThrowableItself() = runTest {
        val boom = RuntimeException("nope")
        val collection = SimpleListCollection<String>(scope = collectionScope()) { _ -> Result.failure(boom) }
        advanceUntilIdle()

        assertSame(boom, collection.error.value)
        assertFalse(collection.isLoading.value)
    }

    @Test
    fun refreshSuperseded_slowInitialCannotOverwriteTheNewerRefresh() = runTest {
        var call = 0
        val collection = SimpleListCollection<String>(scope = collectionScope()) { _ ->
            when (call++) {
                // Initial load parks for a long virtual time; a forced refresh
                // overtakes it and must not be overwritten when it lands.
                0 -> {
                    delay(10_000)
                    Result.success(listOf("initial"))
                }
                else -> Result.success(listOf("refreshed"))
            }
        }
        collection.refresh(force = true)
        advanceUntilIdle()

        assertEquals(listOf("refreshed"), collection.items.value)
        assertFalse(collection.isLoading.value)
    }

    @Test
    fun refreshSuperseded_staleRefreshCannotClearLoadingOrOverwrite() = runTest {
        var call = 0
        val collection = SimpleListCollection<String>(scope = collectionScope()) { _ ->
            when (call++) {
                0 -> Result.success(listOf("initial"))
                1 -> {
                    delay(100) // fast, but only settles after the newer refresh began
                    Result.success(listOf("stale"))
                }
                else -> {
                    delay(5_000)
                    Result.success(listOf("newest"))
                }
            }
        }
        advanceUntilIdle()
        assertEquals(listOf("initial"), collection.items.value)

        collection.refresh() // settles first, already superseded
        collection.refresh() // newest, slowest
        runCurrent() // both refreshes are in flight, neither settled

        assertTrue(collection.isLoading.value)
        assertEquals(listOf("initial"), collection.items.value)
        advanceUntilIdle() // stale settles first (skipped), newest last (applied)

        assertEquals(listOf("newest"), collection.items.value)
        assertFalse(collection.isLoading.value)
        assertNull(collection.error.value)
    }
}
