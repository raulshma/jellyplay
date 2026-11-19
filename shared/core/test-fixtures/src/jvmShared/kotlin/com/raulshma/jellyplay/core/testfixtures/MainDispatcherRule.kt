package com.raulshma.jellyplay.core.testfixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 rule installing a [TestDispatcher] as `Dispatchers.Main` for the
 * duration of each test (`starting` → `setMain`, `finished` →
 * `resetMain`), so ViewModel code touching `Dispatchers.Main` runs against
 * a controllable test scheduler without a device. Register with
 * `@get:Rule`.
 *
 * Retires the hand-inlined boilerplate this rule was copied from ~160 times:
 * every jvmTest class that privately declared a `StandardTestDispatcher` (or
 * `UnconfinedTestDispatcher`), called `Dispatchers.setMain(...)` in setup
 * and `Dispatchers.resetMain()` in teardown, each copy apologizing with the
 * same dead reference — "MainDispatcherRule (:core:testing) inlined —
 * jvmTest has no access to that module" (the dissolved :core:testing module
 * never had a jvmTest-visible home; ADR-0006 rule 4 gives this module as the
 * sanctioned cross-lane one). The rule carries the androidHostTest copy's
 * exact shape (it is that copy, moved here verbatim) so no timing semantics
 * change at the migration: same setMain-in-starting/resetMain-in-finished
 * ordering, same per-test-instance dispatcher lifetime.
 *
 * Lives in jvmShared, not jvmMain: the rule is JUnit4-shaped (TestWatcher),
 * but androidHostTest lanes compile this module's jvmShared layer too, so
 * both JVM test lanes share the single copy.
 *
 * The dispatcher is exposed as a public val on purpose — the dominant
 * boilerplate passed the same dispatcher to `runTest(...)` so the test body
 * and Main-dispatched code shared one scheduler; pass
 * [testDispatcher] there (or construct the rule with an explicit
 * dispatcher, e.g. `MainDispatcherRule(UnconfinedTestDispatcher(scheduler))`
 * for a test-owned `TestCoroutineScheduler`) and the scheduling topology is
 * byte-identical to the hand-inlined version. Default is
 * [StandardTestDispatcher], matching the dominant copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
