package com.raulshma.jellyplay.web

import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * Pins the shared page-lifetime side-effect holder ([WebSideEffectScope])
 * both web controllers ([WebConnectController], [WebSeerrController]) now
 * launch their fire-and-forget work through. The contract is exactly the
 * hand-rolled shape it replaced: jobs run for real (Dispatchers.Default,
 * asserted here via polling, mirroring WebConnectControllerTest), a failing
 * job is CONTAINED (silent degrade — broken stores keep the UI usable) and
 * must not tear down siblings (SupervisorJob). No browser, no fetch.
 */
class WebSideEffectScopeTest {

    /** Polls [condition] on the event loop until true or [timeoutMs] elapses. */
    private suspend fun awaitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (!condition()) {
            if (deadline.hasPassedNow()) throw AssertionError("condition not met within ${timeoutMs}ms")
            delay(25)
        }
    }

    @Test
    fun `jobs run to completion`() = runTest {
        val scope = WebSideEffectScope()
        var ran = false
        scope.launchDegrading { ran = true }
        awaitUntil { ran }
    }

    @Test
    fun `a failing job degrades silently and siblings still run`() = runTest {
        val scope = WebSideEffectScope()
        var siblingRan = false
        scope.launchDegrading { throw RuntimeException("storage exploded") }
        scope.launchDegrading { siblingRan = true }
        awaitUntil { siblingRan }
        // Reaching here IS the containment proof: the earlier throw never
        // surfaced, and the SupervisorJob kept the sibling alive.
    }
}
