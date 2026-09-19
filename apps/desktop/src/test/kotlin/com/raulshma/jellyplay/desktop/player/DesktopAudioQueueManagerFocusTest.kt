package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.focus.DefaultPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.FocusAudioAttributes
import com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState
import com.raulshma.jellyplay.core.data.playback.focus.FocusContentType
import com.raulshma.jellyplay.core.data.playback.focus.FocusListener
import com.raulshma.jellyplay.core.data.playback.focus.FocusOutcome
import com.raulshma.jellyplay.core.data.playback.focus.FocusUsage
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * ADR-0004 slice-2 pins: the desktop claim-edge choreography. The manager
 * under test is wired EXACTLY as desktopPlayerModule wires production — a
 * REAL [DefaultPlaybackFocus] over the in-process [DesktopFocusArbiter]
 * twin and a [DesktopAudioQueueManagerSurface] wrapped around the very
 * manager being driven — because the whole point of the desktop leg is the
 * composition, not any one class: MUSIC claims publish live claim-state
 * (since the ADR-0004 migration slice MUSIC joins READ_ALOUD in
 * osLegClaimants, but desktop's binding arbitrates vacuously — its
 * arbiter always grants and never fires an event — so no OS seat is
 * actually held while the Held publication is real), read-aloud's claim
 * dispatches the MUSIC victim pause back through the surface, and nothing
 * ever auto-resumes. Determinism model is the semantics suite's: the
 * UnconfinedTestDispatcher scope makes every claim/release edge fire
 * INLINE within the engine isPlaying write that triggered it, so each
 * choreography is asserted by direct reads — no polls.
 */
class DesktopAudioQueueManagerFocusTest {

    /**
     * One manager + real focus module on the test dispatcher. `focusFor`
     * swaps the PlaybackFocus seam (the denial mirror needs a scripted
     * verdict the vacuous desktop twin cannot produce); it runs during the
     * manager's initializer, AFTER [focus] is assigned (property order), so
     * the default `{ it.focus }` hands the manager the real module.
     */
    private inner class Harness(private val focusFor: (Harness) -> PlaybackFocus) {
        val engines = mutableListOf<FakeMediaEngine>()
        val resolver = FakeResolver().apply { seed("a") }

        private val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        /** The production wiring: in-process twin + surface over THIS manager. */
        val arbiter = DesktopFocusArbiter()
        val focus = DefaultPlaybackFocus(
            arbiter = arbiter,
            // lazy captures the manager property — evaluated on the first
            // pause command, long after construction (the Koin cycle breaker
            // this pins in miniature).
            surfaces = listOf(DesktopAudioQueueManagerSurface(manager = lazy { manager })),
        )

        val manager = DesktopAudioQueueManager(
            trackResolver = resolver,
            playbackRepository = FakePlaybackRepository(),
            imageUrlProvider = FakeImages(),
            queuePersistenceHelper = QueuePersistenceHelper(InMemoryQueueDao()),
            // play()'s lyrics fetch launches on the manager's scope; without
            // initialize() the fetch would throw lateinit and leak into the
            // next test's uncaught-exception gate (the RealEngine suites get
            // this from manager.start(); the focus harness never starts).
            lyricsManager = AudioLyricsManager(FakeLyricsRepository()).also { it.initialize(scope) },
            sleepTimerManager = SleepTimerManager(TestTimeSource()),
            scope = scope,
            engineFactory = { FakeMediaEngine().also { engines += it } },
            mainThreadGuard = false,
            playbackFocus = focusFor(this),
        )

        /** The one engine a single play path creates. */
        val engine: FakeMediaEngine get() = engines.first()

        fun close() {
            scope.cancel()
        }
    }

    /** Scripted denial — the ONLY unreachable branch on the vacuous twin. */
    private class DenyingFocus : PlaybackFocus {
        override val claimState = MutableStateFlow(FocusClaimState.Idle)
        override fun acquire(claimant: PlaybackSurfaceId): FocusOutcome = FocusOutcome.Denied
        override fun release(claimant: PlaybackSurfaceId) {}
    }

    private val openHarnesses = mutableListOf<Harness>()

    private fun newHarness(focusFor: (Harness) -> PlaybackFocus = { NoopPlaybackFocus }): Harness =
        Harness(focusFor).also { openHarnesses += it }

    @AfterTest
    fun tearDownHarnesses() {
        openHarnesses.forEach { it.close() }
        openHarnesses.clear()
    }

    @Test
    fun `playing edge publishes held music - the desktop claim-state is live`() {
        val h = newHarness { it.focus }

        h.manager.play("a")

        assertEquals(FocusClaimState.Held(PlaybackSurfaceId.MUSIC), h.focus.claimState.value)
        assertTrue(h.engine.isPlaying.value, "the claim did not gate playback — the grant is vacuous")
    }

    @Test
    fun `pause edge releases the claim`() {
        val h = newHarness { it.focus }
        h.manager.play("a")

        h.manager.pause()

        assertEquals(FocusClaimState.Idle, h.focus.claimState.value)
    }

    @Test
    fun `read-aloud claim pauses desktop music through the surface`() {
        val h = newHarness { it.focus }
        h.manager.play("a")

        assertEquals(FocusOutcome.Granted, h.focus.acquire(PlaybackSurfaceId.READ_ALOUD))

        assertEquals(FocusClaimState.Held(PlaybackSurfaceId.READ_ALOUD), h.focus.claimState.value)
        assertFalse(h.engine.isPlaying.value, "the matrix's MUSIC victim pause landed on the manager")
    }

    @Test
    fun `read-aloud release never auto-resumes music - manual resume only`() {
        val h = newHarness { it.focus }
        h.manager.play("a")
        h.focus.acquire(PlaybackSurfaceId.READ_ALOUD)

        h.focus.release(PlaybackSurfaceId.READ_ALOUD)

        assertEquals(FocusClaimState.Idle, h.focus.claimState.value)
        assertFalse(h.engine.isPlaying.value, "no OS focus stack, no regain path — nothing resurrects the victim")
    }

    @Test
    fun `manual resume after displacement re-claims the floor`() {
        val h = newHarness { it.focus }
        h.manager.play("a")
        h.focus.acquire(PlaybackSurfaceId.READ_ALOUD)
        h.focus.release(PlaybackSurfaceId.READ_ALOUD)

        h.manager.togglePlayPause()

        assertEquals(FocusClaimState.Held(PlaybackSurfaceId.MUSIC), h.focus.claimState.value)
        assertTrue(h.engine.isPlaying.value, "the user's play tap is the way back")
    }

    @Test
    fun `denied claim pauses the manager - the android denial mirror`() {
        val h = newHarness { DenyingFocus() }

        h.manager.play("a")

        assertEquals(1, h.engine.loadedRequests.size, "the load happened; the claim answered after")
        assertFalse(h.engine.isPlaying.value, "a denied claim MUST NOT produce audio")
    }

    @Test
    fun `in-process arbiter twin grants vacuously and never suspends anyone`() {
        val arbiter = DesktopFocusArbiter()
        var events = 0
        val listener = FocusListener { events++ }

        assertTrue(
            arbiter.request(FocusAudioAttributes(FocusUsage.MEDIA, FocusContentType.SPEECH), listener),
            "no OS seat exists to deny the request",
        )
        arbiter.abandon()
        arbiter.abandon() // idempotent

        assertEquals(0, events, "no OS authority means no focus events — nothing can land Suspended")
    }
}
