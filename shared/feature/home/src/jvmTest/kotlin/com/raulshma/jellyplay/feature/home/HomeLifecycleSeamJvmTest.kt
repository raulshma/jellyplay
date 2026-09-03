package com.raulshma.jellyplay.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Invariants pinned (JVM actual of the home process-lifecycle seam, see
 * [registerHomeProcessLifecycle]):
 *  - Desktop has no process lifecycle: registration returns `null` — neither
 *    the onStart nor the onStop callback ever fires.
 *  - The returned-null contract is what lets [HomeViewModel]'s onCleared
 *    invoke the removal handle un-guarded (`processLifecycleRemoval?.invoke()`
 *    degrades to a no-op instead of crashing).
 *
 * The [HomeRefresherTest]/[HomeViewModelTest] suites depend on this exact
 * semantics: the JVM refresher's start/stop is driven by the tests directly
 * because the seam never wires a lifecycle.
 */
class HomeLifecycleSeamJvmTest {

    @Test
    fun jvmRegistration_returnsNull_neverFiresCallbacks() {
        var started = false
        var stopped = false

        val removal = registerHomeProcessLifecycle(
            onStart = { started = true },
            onStop = { stopped = true },
        )

        assertNull(removal, "desktop has no process lifecycle — no removal handle exists")
        assertFalse(started)
        assertFalse(stopped)
    }

    @Test
    fun jvmRegistration_isRepeatable_andAlwaysNull() {
        // Repeated registration (VM recreation) never throws on the JVM.
        val first = registerHomeProcessLifecycle(onStart = {}, onStop = {})
        val second = registerHomeProcessLifecycle(onStart = {}, onStop = {})

        assertNull(first)
        assertNull(second)
    }
}
