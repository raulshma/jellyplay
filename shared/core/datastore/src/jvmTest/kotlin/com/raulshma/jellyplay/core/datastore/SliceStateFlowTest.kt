package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the module-wide corrupt-read policy of [dataDegradingToDefaults] /
 * [sliceStateFlow] (see `SliceStateFlow.kt`): a failed `data` read degrades to
 * the all-defaults snapshot run through the caller's read projection — it
 * never throws at collectors and never leaves the eager StateFlow frozen at
 * its seed (the fate of the old `.catch { _ -> emptyPreferences() }` spelling,
 * whose lambda value coerced to `Unit` so the flow completed empty).
 *
 * The corruption is simulated with a fake [DataStore] whose `data` flow throws
 * (a read exception that is NOT a file-corruption CorruptionException — the
 * file-level `ReplaceFileCorruptionHandler` already degrades actual corrupt
 * files to `emptyPreferences()` before the flow chain even sees them). Real
 * file-backed healthy-path behavior is covered by the per-store suites.
 *
 * [runCurrent] (not just [advanceUntilIdle]) is what flushes the
 * Eagerly-shared collector in this fake's synchronous-throw shape.
 */
class SliceStateFlowTest {

    /** A DataStore whose every read fails — the "corrupt read" stand-in. */
    private class CorruptDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw CorruptReadException()
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("read-only fake")
    }

    private class CorruptReadException : RuntimeException("simulated corrupt read")

    @Test
    fun `degraded flow emits the all-defaults snapshot and never throws`() = runTest {
        val emitted = CorruptDataStore().dataDegradingToDefaults().toList()
        assertEquals(1, emitted.size)
        assertTrue(emitted.single().asMap().isEmpty())
    }

    @Test
    fun `sliceStateFlow degrades to the read projection of defaults, not the seed`() = runTest {
        val flow = CorruptDataStore().sliceStateFlow(
            backgroundScope,
            seed = "seed",
            read = { prefs -> if (prefs.asMap().isEmpty()) "defaults" else "live" },
        )
        runCurrent()
        advanceUntilIdle()
        assertEquals("defaults", flow.value)
    }

    @Test
    fun `sliceStateFlow still serves collectors after a corrupt read`() = runTest {
        val flow = CorruptDataStore().sliceStateFlow(
            backgroundScope,
            seed = "seed",
            read = { prefs -> if (prefs.asMap().isEmpty()) "defaults" else "live" },
        )
        runCurrent()
        advanceUntilIdle()
        assertEquals("defaults", flow.first())
    }

    @Test
    fun `a store built on the chassis degrades to its default slice`() = runTest {
        val store = NavigationStore(CorruptDataStore(), backgroundScope)
        runCurrent()
        advanceUntilIdle()
        assertEquals(NavigationSlice(), store.navigation.value)
    }
}
