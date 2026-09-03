package com.raulshma.jellyplay.core.ui.settingssearch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the local settings-search pipeline ([settingsSearchResults]) without
 * resolving any Compose string resources (the provider's catalog is empty or
 * instrumented — `resolve()` over a NON-empty catalog would call `getString`,
 * which these tests never trigger):
 *
 *  - a BLANK query emits an empty list WITHOUT touching the provider's
 *    catalog (the catalog getter throws if touched — the short-circuit is the
 *    contract, not an implementation detail);
 *  - rapid keystrokes are debounced: only the FINAL query of a burst reaches
 *    the catalog (observed via a read-counting provider);
 *  - identical consecutive queries resolve exactly once (distinctUntilChanged
 *    sits before the resolve step);
 *  - the empty catalog yields empty results for any query.
 *
 * Note on timing: the pipeline runs on Dispatchers.Default (flowOn), so the
 * 120 ms debounce elapses in REAL time even under a test dispatcher — the
 * assertions wait with generous margins instead of virtual-time advances.
 */
class SettingsSearchFlowTest {

    /** Provider whose catalog getter throws — proves blank queries never touch it. */
    private class ExplodingProvider : SettingsSearchProvider {
        override val items: List<SettingsSearchItem>
            get() = throw IllegalStateException("blank query must not resolve the catalog")
    }

    /** Provider that counts catalog reads and serves an empty catalog. */
    private class CountingProvider : SettingsSearchProvider {
        var reads = 0
            private set
        override val items: List<SettingsSearchItem>
            get() {
                reads++
                return emptyList()
            }
    }

    private fun realTimeTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withContext(Dispatchers.Default) { withTimeout(10_000) { block() } }
    }

    @Test
    fun blankQuery_emitsEmptyListWithoutTouchingTheCatalog() = realTimeTest {
        val first = settingsSearchResults(MutableStateFlow(""), ExplodingProvider()).first()

        assertTrue(first.isEmpty())
    }

    @Test
    fun emptyCatalog_yieldsEmptyResultsForAnyQuery() = realTimeTest {
        val results = settingsSearchResults(flowOf("playback"), CountingProvider()).first()

        assertEquals(emptyList(), results)
    }

    @Test
    fun rapidBurst_debouncesToTheFinalQueryOnly() = realTimeTest {
        val provider = CountingProvider()
        // All three keystrokes land well inside the 120 ms debounce window.
        val burst = flow {
            emit("")
            emit("the")
            emit("them")
            emit("themes")
        }

        val results = settingsSearchResults(burst, provider).first()

        // Only the final query was resolved; the blank query and intermediate
        // keystrokes were dropped by the debounce.
        assertEquals(emptyList(), results)
        assertEquals(1, provider.reads, "burst must resolve only the final query")
    }

    @Test
    fun identicalConsecutiveQueries_resolveOnlyOnce() = realTimeTest {
        val provider = CountingProvider()
        val queries = flow {
            emit("theme")
            emit("theme") // duplicate — must be dropped before the resolve step
            emit("themes")
        }

        settingsSearchResults(queries, provider).first()

        assertEquals(1, provider.reads, "duplicate consecutive queries must not re-resolve")
    }

    @Test
    fun distinctQueries_afterTheDebounceWindow_resolveAgain() = realTimeTest {
        val provider = CountingProvider()
        val queries = MutableStateFlow("theme")

        val job = launch { settingsSearchResults(queries, provider).take(2).collect { } }
        delay(400) // "theme" settles (debounce 120 ms) and resolves once
        queries.value = "appearance" // distinct query after the window
        delay(600)
        job.join()

        assertEquals(2, provider.reads, "a new query after the window must resolve again")
    }
}
