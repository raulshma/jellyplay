package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the pre-warm behavior that backs the settings-open ANR fix
 * (docs/e2e/device-locale-pass.md): the catalog warm pass is single-flight,
 * runs as a child of the injected scope (the applicationScope on production —
 * a Dispatchers.Default scope, off the main thread), rethrows cancellation
 * while swallowing pass failures, and allows re-kicks after completion.
 * The pass is injectable, so these tests never touch the real
 * compose-resources runtime (its desktop system-environment path needs the
 * Skiko AWT natives, absent in headless tests). The injected scope uses
 * [UnconfinedTestDispatcher] so each pass runs eagerly up to its first
 * suspension — a queued `backgroundScope` launch is never drained by the
 * test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSearchCatalogPrewarmTest {

    private fun TestScope.eagerScope(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `warm is single-flight while the first pass is still running`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var passCount = 0
        val prewarmer = SettingsSearchCatalogPrewarmer(eagerScope()) {
            passCount++
            gate.await()
        }

        val first = prewarmer.warm()
        val second = prewarmer.warm()

        assertSame(first, second, "second warm() must return the running pass, not start a new one")
        assertEquals(1, passCount)
        gate.complete(Unit)
        first.join()
        assertEquals(1, passCount, "completed single pass, no duplicate warm")
    }

    @Test
    fun `a failed pass is swallowed and does not fail the scope`() = runTest {
        val prewarmer = SettingsSearchCatalogPrewarmer(eagerScope()) {
            error("resolve exploded")
        }

        val job = prewarmer.warm()

        assertTrue(job.isCompleted, "a swallowed failure must complete the pass job normally")
        assertFalse(job.isCancelled)
    }

    @Test
    fun `cancelling the injected scope cancels the warm pass`() = runTest {
        val scope = eagerScope()
        val gate = CompletableDeferred<Unit>()
        val prewarmer = SettingsSearchCatalogPrewarmer(scope) { gate.await() }

        val job = prewarmer.warm()
        assertTrue(job.isActive, "pass must be parked on the gate")

        // The definitive child-of-injected-scope proof: cancelling the
        // injected scope's job tears the pass down with it.
        scope.coroutineContext[Job]!!.cancelChildren()

        assertTrue(job.isCancelled, "cancellation must propagate through the rethrow")
    }

    @Test
    fun `a new pass may start after the previous one completed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val prewarmer = SettingsSearchCatalogPrewarmer(eagerScope()) { gate.await() }

        val first = prewarmer.warm()
        gate.complete(Unit)
        first.join()
        val second = prewarmer.warm()

        assertNotSame(first, second, "a completed pass must not block a re-kick (cheap post-warm)")
        second.join()
        assertTrue(second.isCompleted)
    }
}
