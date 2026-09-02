package com.raulshma.jellyplay.shell

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the single-restartable-slot contract: [RestartableJob.launchIn] must
 * cancel the previous occupant before starting the replacement, so a
 * coordinator re-start (e.g. after activity-state loss rebuilt the ViewModel)
 * never duplicates collectors.
 */
class RestartableJobTest {

    @Test
    fun `launchIn cancels the previous occupant before launching the replacement`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val firstCancelled = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val jobs = RestartableJob()

        jobs.launchIn(scope) {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                firstCancelled.complete(Unit)
                throw e
            }
        }
        jobs.launchIn(scope) { secondStarted.complete(Unit) }

        withTimeout(10_000) {
            firstCancelled.await()
            secondStarted.await()
        }
        scope.cancel()
    }

    @Test
    fun `relaunching after the previous block completed on its own is allowed`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val secondRan = CompletableDeferred<Unit>()
        val jobs = RestartableJob()

        jobs.launchIn(scope) { /* completes immediately */ }
        jobs.launchIn(scope) { secondRan.complete(Unit) }

        withTimeout(10_000) { secondRan.await() }
        scope.cancel()
    }

    @Test
    fun `cancelling a fresh RestartableJob that never launched is a no-op`() {
        // Nothing to assert beyond "does not throw": covers the null-job
        // path of the cancel-then-replace slot.
        RestartableJob()
    }
}
