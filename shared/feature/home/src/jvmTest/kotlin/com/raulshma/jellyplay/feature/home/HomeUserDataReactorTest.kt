package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.RecentLocalUserDataWritesImpl
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.feature.home.testutil.FakeTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Direct [HomeUserDataReactor] tests: the user-data reaction chain in
 * isolation — debounce, throttle deferral, pending latch, echo/push
 * demotion, the raced-sync override — with NO refresher constructed. The
 * port is a recording double: [HomeUserDataReactor.Port.launchUserDataRefresh]
 * is captured, never run; a test executes the captured force verdict (the
 * echo/push + raced-sync demotion) and the latch clears explicitly, exactly
 * where the refresher's job body would. Same harness idiom as
 * [HomeRefresherTest]: [StandardTestDispatcher] over Main, runCurrent /
 * advanceTimeBy — NEVER advanceUntilIdle, the deferral timer is a delay
 * chain — and the teardown cancels the scope so no collector or timer
 * survives the body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeUserDataReactorTest {

    // MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module. Same idiom as HomeRefresherTest.
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var fakeTimeSource: FakeTimeSource

    /**
     * The real write-log the reactor consults, on the fake clock — the
     * echo-demotion tests record into it before emitting a user-data
     * change.
     */
    private lateinit var localWrites: RecentLocalUserDataWritesImpl

    private var reactorScope: CoroutineScope? = null

    /** Hot flow behind the [UserDataChanges] seam; tests emit into it. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)

    /** Hand fake of the one-member [UserDataChanges] seam. */
    private val userDataChanges = object : UserDataChanges {
        override val userDataChanges: Flow<UserDataChange> = userDataEvents
    }

    private val port = RecordingPort()

    /**
     * Records every launch WITHOUT running it (the job replacement is the
     * refresher's half) and carries the two gates' states the tests flip.
     * [Launch.force] executes the body's at-execution-time force verdict;
     * [Launch.land] the land-only latch clears.
     */
    private class RecordingPort : HomeUserDataReactor.Port {
        var isForeground = true
        var offline = false
        var lastRefreshTimeMillis = 0L
        var racedPendingSync = false
        val launches = mutableListOf<Launch>()

        class Launch(val decideForce: () -> Boolean, val onLanded: () -> Unit) {
            fun force(): Boolean = decideForce()
            fun land() = onLanded()
        }

        override fun isAppInForeground(): Boolean = isForeground
        override fun isOffline(): Boolean = offline
        override fun lastRefreshTimeMillis(): Long = lastRefreshTimeMillis
        override fun lastFetchRacedPendingSync(): Boolean = racedPendingSync
        override fun launchUserDataRefresh(decideForce: () -> Boolean, onLanded: () -> Unit) {
            launches += Launch(decideForce, onLanded)
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        fakeTimeSource = FakeTimeSource()
    }

    @AfterTest
    fun tearDown() {
        reactorScope?.cancel()
        Dispatchers.resetMain()
    }

    /**
     * Builds the reactor on its own scope over the test scheduler (the
     * HomeRefresherTest hand-off idiom — NOT a child of the TestScope, so
     * the deferral timer's delay chain never blocks runTest's completion
     * wait; cancelling it remains each test's job, see the class KDoc).
     */
    private fun TestScope.buildReactor(): HomeUserDataReactor {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        reactorScope = scope
        localWrites = RecentLocalUserDataWritesImpl(clock = { fakeTimeSource.nowMs })
        return HomeUserDataReactor(
            scope = scope,
            clock = fakeTimeSource,
            userDataChanges = userDataChanges,
            recentLocalWrites = localWrites,
            port = port,
        )
    }

    // ── external push vs echo of own writes ─────────────────────────────────

    @Test
    fun externalPush_pastThrottleWindow_launchesForcedRefresh() = runTest {
        // The primed refresh clock (what a fetch would have stamped).
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent() // the collector subscribes

        // Past the 60s user-data throttle window since the primed stamp, so
        // the change refresh runs immediately.
        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000) // past the 1s debounce
        runCurrent()

        assertEquals(1, port.launches.size, "an external push fans out into exactly one refresh")
        assertTrue(port.launches.single().force(), "an external push stays forced")
    }

    @Test
    fun echoOfOwnWrites_demotesToNonForced() = runTest {
        // The socket echo of THIS device's own write burst is item-id
        // identical to the write (UserDataChange carries only user + item
        // ids), so the write-log is what lets the flush tell it apart from
        // an external push. With every named item recently recorded, the
        // fetch demotes to TTL semantics — still debounced and throttled.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 61_000L
        localWrites.record(listOf("i1"))
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, port.launches.size)
        assertFalse(port.launches.single().force(), "the echo of this device's own write runs non-forced")
    }

    @Test
    fun echoContainingAForeignItemId_staysForced() = runTest {
        // The demotion is all-or-nothing: one foreign id means the change
        // could name server-side state this device cannot know, so the
        // forced path stays.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 61_000L
        localWrites.record(listOf("mine"))
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("mine", "external")))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(port.launches.single().force())
    }

    @Test
    fun echoWithWritePastEchoWindow_staysForced() = runTest {
        // A log entry past the echo window does not demote: the write is
        // old enough that the push may as well be external.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 61_000L
        localWrites.record(listOf("i1"))
        fakeTimeSource.nowMs = 61_000L + RecentLocalUserDataWritesImpl.ECHO_WINDOW_MS
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(port.launches.single().force())
    }

    @Test
    fun echoWhileRacedSyncBypassArmed_staysForced_andSkipsThrottleDeferral() = runTest {
        // The drained play must fan out: when the going-online handshake's
        // fetch painted a pre-drain snapshot, the drain's completion echo is
        // the sync-complete signal — it must not defer behind the throttle
        // AND it overrides the demotion (a TTL-semantics fetch could serve
        // the pre-drain cache).
        port.lastRefreshTimeMillis = 1_000L
        port.racedPendingSync = true
        val reactor = buildReactor()
        runCurrent()

        // INSIDE the 60s throttle window — the bypass must skip the deferral.
        fakeTimeSource.nowMs = 30_000L
        localWrites.record(listOf("i1"))
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100)
        runCurrent()

        assertEquals(1, port.launches.size, "the sync-complete echo refreshes regardless of the clock")
        assertTrue(port.launches.single().force(), "the raced-sync bypass overrides the echo demotion")
    }

    // ── throttle deferral (trailing edge) ───────────────────────────────────

    @Test
    fun pushInsideThrottleWindow_defersToExpiry_notDropped() = runTest {
        // The final echo of a playback session lands <60s after the last
        // refresh — dropping it strands Continue Watching / Next Up at
        // mid-playback state. It must instead be deferred to throttle
        // expiry and fire on its own.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 30_000L // since = 29_000, inside the window
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100) // the debounce lands, the deferral arms
        runCurrent()
        assertEquals(0, port.launches.size, "no immediate fetch — the throttle bounds the cadence")

        // The deferral timer fires after WINDOW - since = 31_000 of
        // scheduler time from the debounce.
        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(1, port.launches.size, "the deferred fetch fires at throttle expiry")
        assertTrue(port.launches.single().force())
    }

    @Test
    fun furtherEchoInsideThrottle_reArmsTheDeferralTrailingEdge() = runTest {
        // Each echo inside the window cancels and re-arms the timer — a
        // playback session's position saves collapse into ONE fetch after
        // the last save, not one per save.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 30_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100) // debounce lands at t=1_100; timer arms for t≈32_000
        runCurrent()
        advanceTimeBy(4_000) // t=5_100

        // A second echo re-arms the timer 31_000 past ITS debounce (t≈37_100)
        // — cancelling the first arm before it can fire.
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i2")))
        advanceTimeBy(25_900) // t=31_000 — just before the FIRST arm could fire
        runCurrent()
        assertEquals(0, port.launches.size, "the re-arm cancelled the first deferral")

        advanceTimeBy(6_100) // t≈37_100 — the re-armed expiry
        runCurrent()
        assertEquals(1, port.launches.size, "exactly one fetch for the whole burst")
    }

    @Test
    fun debouncedBurst_isSingleRefresh_consultingTheLastChangeIds() = runTest {
        // A burst inside the debounce window collapses into one refresh, and
        // the demotion consults only the MERGED (last) change's item ids.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("external")))
        advanceTimeBy(500) // inside the 1s debounce — restarts the timer
        localWrites.record(listOf("own"))
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("own")))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, port.launches.size, "the burst is one debounced refresh")
        assertFalse(port.launches.single().force(), "the flush consults the last change's ids only")
    }

    // ── the pending-flush latch ─────────────────────────────────────────────

    @Test
    fun flushWithoutArmedLatch_isANoOp() = runTest {
        val reactor = buildReactor()
        runCurrent()

        reactor.flushPendingUserDataChange()
        runCurrent()

        assertEquals(0, port.launches.size, "the latch gate: a flush with nothing armed must not fetch")
    }

    @Test
    fun changeWhileBackgrounded_latches_thenDrainsOnFlush() = runTest {
        // Return-to-Home promptness: a change armed while backgrounded must
        // fetch on the start() flush — the user is looking at the screen
        // now, so the throttle does not apply even inside its window.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        port.isForeground = false
        fakeTimeSource.nowMs = 30_000L // inside the throttle window too
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(0, port.launches.size, "backgrounded: latched, not fetched")

        port.isForeground = true
        reactor.flushPendingUserDataChange()
        assertEquals(1, port.launches.size, "the flush is the latch drain — no throttle gate")
        assertTrue(port.launches.single().force())
    }

    @Test
    fun changeWhileOffline_latches_thenDrainsOnFlush() = runTest {
        // Offline behaves like backgrounded: the change is applied on the
        // next flush instead of waiting for the periodic loop to happen
        // across the disconnect.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        port.offline = true
        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(0, port.launches.size, "offline: latched, not fetched")

        port.offline = false
        reactor.flushPendingUserDataChange()
        assertEquals(1, port.launches.size)
        assertTrue(port.launches.single().force())
    }

    @Test
    fun flushLatch_staysArmedUntilTheFetchLands() = runTest {
        // The pending flag is NOT cleared before the fetch lands — a stop()
        // mid-fetch re-arms the flush for the next onStart, so a second
        // flush while still armed re-launches (replacing the refresh job),
        // and only the land-only clears consume the latch.
        val reactor = buildReactor()
        runCurrent()

        port.isForeground = false
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100)
        runCurrent()

        port.isForeground = true
        reactor.flushPendingUserDataChange()
        reactor.flushPendingUserDataChange()
        assertEquals(2, port.launches.size, "still armed until the fetch lands: the second flush re-launches")

        port.launches.last().land()
        reactor.flushPendingUserDataChange()
        assertEquals(2, port.launches.size, "the land-only clears consumed the latch")
    }

    @Test
    fun cancelDeferralTimer_dropsTheScheduledExpiry_latchStaysArmed() = runTest {
        // stop() / SignedOut / the user switch cancel the deferral timer —
        // the deferred fetch never fires on its own, but the change is not
        // lost: the next flush still drains the armed latch.
        port.lastRefreshTimeMillis = 1_000L
        val reactor = buildReactor()
        runCurrent()

        fakeTimeSource.nowMs = 30_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_100)
        runCurrent()
        reactor.cancelDeferralTimer()

        advanceTimeBy(60_000) // well past where the deferral would have fired
        runCurrent()
        assertEquals(0, port.launches.size, "the cancelled deferral timer never fires")

        reactor.flushPendingUserDataChange()
        assertEquals(1, port.launches.size, "the latched change still drains on the next flush")
    }
}
