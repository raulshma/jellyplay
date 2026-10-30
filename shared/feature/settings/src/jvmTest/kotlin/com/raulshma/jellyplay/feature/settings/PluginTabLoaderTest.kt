package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plugin tab screens' shared load holder (plain closures — the gate and
 * fetch seams are constructor lambdas, so no mocks are needed): the
 * stale-response guard (only the selected tab's fetch may land, on BOTH the
 * success and the failure arm), the quiet-absence fold and the closed-gate
 * path. The loader rides the test scope itself (the
 * [JellyPlayYourWatchingViewModelTest] idiom — one `launch` per refresh,
 * drained by the test scheduler).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginTabLoaderTest {

    /** A String-tab loader wired to the given closures (the test's one shape). */
    private fun loader(
        scope: kotlinx.coroutines.CoroutineScope,
        gateOpen: suspend () -> Boolean = { true },
        selected: () -> String,
        fetch: suspend (String) -> Result<String>,
        isCurrent: (String) -> Boolean,
        land: (String) -> Unit,
        onGateClosed: () -> Unit = {},
    ): PluginTabLoader<String, String> = PluginTabLoader(
        scope = scope,
        gateOpen = gateOpen,
        selected = selected,
        fetch = fetch,
        isCurrent = isCurrent,
        land = land,
        quiet = "quiet",
        onGateClosed = onGateClosed,
    )

    @Test
    fun `only the currently selected tab's fetch may land`() = runTest {
        var selected = "a"
        val landed = mutableListOf<Pair<String, String>>()
        // Each fetch suspends until the test completes it (the slow tabs).
        val first = CompletableDeferred<Result<String>>()
        val second = CompletableDeferred<Result<String>>()
        var call = 0

        val tabLoader = loader(
            scope = this,
            selected = { selected },
            fetch = { (if (++call == 1) first else second).await() },
            isCurrent = { query -> query == selected },
            land = { value -> landed += selected to value },
        )

        tabLoader.refresh()
        runCurrent() // the first fetch starts, captures "a", suspends in flight
        selected = "b" // the chip change while the first fetch is still in flight
        tabLoader.refresh()
        runCurrent() // the second fetch starts, captures "b", suspends in flight

        // The NEW selection's fetch lands; the stale one's FAILURE (the guard
        // covers the failure arm too) never does.
        second.complete(Result.success("two"))
        first.complete(Result.failure(IllegalStateException("stale tab")))
        advanceUntilIdle()

        assertEquals(listOf("b" to "two"), landed)
    }

    @Test
    fun `a fresh failure lands the quiet empty - never an error`() = runTest {
        var landed: String? = null

        val tabLoader = loader(
            scope = this,
            selected = { "a" },
            fetch = { Result.failure(IllegalStateException("404")) },
            isCurrent = { true },
            land = { landed = it },
        )

        tabLoader.refresh()
        advanceUntilIdle()

        assertEquals("quiet", landed)
    }

    @Test
    fun `a fresh success lands the fetched value`() = runTest {
        var landed: String? = null
        var loading = true

        val tabLoader = loader(
            scope = this,
            selected = { "a" },
            fetch = { Result.success("payload") },
            isCurrent = { true },
            land = { landed = it; loading = false },
        )

        tabLoader.refresh()
        advanceUntilIdle()

        assertEquals("payload", landed)
        assertFalse(loading)
    }

    @Test
    fun `a closed gate never fetches and lands the gate-closed face`() = runTest {
        var fetched = false
        var gateClosed = false

        val tabLoader = loader(
            scope = this,
            gateOpen = { false },
            selected = { "a" },
            fetch = { fetched = true; Result.success("payload") },
            isCurrent = { true },
            land = { },
            onGateClosed = { gateClosed = true },
        )

        tabLoader.refresh()
        advanceUntilIdle()

        assertFalse(fetched)
        assertTrue(gateClosed)
    }
}
