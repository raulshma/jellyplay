package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class TaskBundleTest {

    @Test
    fun `replace cancels the previous occupant of the key`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        val firstStarted = CompletableDeferred<Unit>()
        val first = bundle.replace("k") { launch { firstStarted.complete(Unit); while (true) delay(1_000) } }
        firstStarted.await()
        val second = bundle.replace("k") { launch { while (true) delay(1_000) } }
        runCurrent()
        assertTrue(!first.isActive)
        assertTrue(second.isActive)
        assertNotEquals(first, second)
    }

    @Test
    fun `keys are independent slots`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        val a = bundle.replace("a") { launch { while (true) delay(1_000) } }
        val b = bundle.replace("b") { launch { while (true) delay(1_000) } }
        bundle.replace("a2") { launch { while (true) delay(1_000) } } // unrelated key, no interference
        runCurrent()
        assertTrue(a.isActive)
        assertTrue(b.isActive)
        assertEquals(a, bundle["a"])
        assertEquals(b, bundle["b"])
    }

    @Test
    fun `cancel cancels and forgets`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val job = bundle.replace("k") { launch { started.complete(Unit); while (true) delay(1_000) } }
        started.await()
        bundle.cancel("k")
        runCurrent()
        assertTrue(!job.isActive)
        assertNull(bundle["k"])
        // Cancelling an empty slot is a no-op, not a crash.
        bundle.cancel("missing")
    }

    @Test
    fun `forget hands the job off without cancelling it`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val job = bundle.replace("k") { launch { started.complete(Unit); while (true) delay(1_000) } }
        started.await()
        bundle.forget("k")
        assertNull(bundle["k"])
        runCurrent()
        assertTrue(job.isActive)
        job.cancelAndJoin()
    }

    @Test
    fun `cancelAll cancels every slot but leaves the scope alive`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        val started = CompletableDeferred<Unit>()
        bundle.replace("a") { launch { started.complete(Unit); while (true) delay(1_000) } }
        bundle.replace("b") { launch { while (true) delay(1_000) } }
        started.await()
        bundle.cancelAll()
        runCurrent()
        assertNull(bundle["a"])
        assertNull(bundle["b"])
        // The scope (this runTest coroutine) still works:
        val probe = launch { }
        probe.join()
        assertTrue(probe.isCompleted)
    }

    @Test
    fun `get returns the tracked job so release paths can join it`() = runTest {
        val bundle = TaskBundle(backgroundScope)
        var returned: Job? = null
        returned = bundle.replace("k") { launch { delay(50) } }
        assertEquals(returned, bundle["k"])
    }
}
