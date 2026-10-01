package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The [MediaEngine] contract laws, driven through the INTERFACE (every
 * reference below is `MediaEngine`, never the concrete fake) against the
 * shared headless double — the same laws the androidHostTest lane runs
 * against the real ExoPlayerEngine adapter:
 *
 *  1. **Release idempotence** — release() is terminal and repeatable; no
 *     second call may throw, and the published surface stays parked.
 *  2. **State reset choreography** — the transport leaves (playbackState /
 *     isPlaying / position) reset on stop, and play-from-ENDED rewinds to 0
 *     (the keep-open contract every real engine implements; BasePlayerEngine's
 *     chassis owns the published-state reset lists the real adapters run).
 *  3. **bufferedRanges invariants** — an engine with no buffer surface
 *     publishes the always-empty default (never a fake full-track band),
 *     and anything published is normalized (sorted, merged, clamped,
 *     non-degenerate) — the [BufferedRanges] algebra the adapters share.
 *  4. **Capability reporting** — `capabilities` is a STABLE value the UI
 *     reads once to show/hide controls: same instance across reads, and the
 *     headless engine's all-false defaults keep every optional control
 *     gated off.
 *  5. **The request-side engineSpecific slot** — the per-engine request
 *     payload projects through `requestSpecific` (the typed
 *     [com.raulshma.jellyplay.core.model.PlaybackRequestSpecific] view) and
 *     tolerates both a config-side variant and an absent slot as `null`.
 */
class MediaEngineContractLawTest {

    // ── 1. Release idempotence ──────────────────────────────────────────────

    @Test
    fun `release is repeatable and parks the published surface`() {
        val engine: MediaEngine = FakeMediaEngine(FakeMediaEngine.LoadBehavior.AUTO_PLAY)
        engine.load(PlaybackRequest(uri = "http://x/item", title = "t", serverDurationMs = 90_000))
        assertTrue(engine.isPlaying.value)

        engine.release()
        engine.release() // the law: idempotent — no throw, no state flip-flop

        assertTrue((engine as FakeMediaEngine).released)
        assertTrue(engine.isPlaying.value, "release is terminal teardown, not a transport command — it resets nothing on the fake's published flows (the real adapters reset through the chassis, pinned in EngineStateChassisTest)")
    }

    // ── 2. State reset choreography through the interface ───────────────────

    @Test
    fun `stop resets position duration and both transport leaves`() {
        val engine: MediaEngine = FakeMediaEngine(FakeMediaEngine.LoadBehavior.AUTO_PLAY)
        engine.load(PlaybackRequest(uri = "http://x/item", title = "t", serverDurationMs = 90_000))
        engine.seekTo(30_000)

        engine.stop()

        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
        assertEquals(false, engine.isPlaying.value)
        assertEquals(0L, engine.currentPositionMs)
        assertEquals(0L, engine.durationMs)
    }

    @Test
    fun `play from ENDED rewinds to zero - the keep-open contract`() {
        val engine: MediaEngine = FakeMediaEngine(FakeMediaEngine.LoadBehavior.AUTO_PLAY)
        engine.load(PlaybackRequest(uri = "http://x/item", title = "t", serverDurationMs = 90_000))
        engine.seekTo(89_000)

        (engine as FakeMediaEngine).simulateEnded()
        assertEquals(EnginePlaybackState.ENDED, engine.playbackState.value)

        engine.play()

        assertEquals(0L, engine.currentPositionMs, "replay restarts the item, never resumes past the end")
        assertEquals(EnginePlaybackState.READY, engine.playbackState.value)
        assertTrue(engine.isPlaying.value)
    }

    // ── 3. bufferedRanges invariants ────────────────────────────────────────

    @Test
    fun `an engine with no buffer surface publishes the empty default`() {
        val engine: MediaEngine = FakeMediaEngine()
        assertEquals(BufferedRanges.EMPTY, engine.bufferedRanges)
        assertTrue(engine.bufferedRanges.value.isEmpty())
    }

    @Test
    fun `published ranges survive the normalize invariants`() {
        // The multi-band contract (sorted, merged, clamped, non-degenerate)
        // holds no matter who publishes — a real adapter derives through
        // BufferedRanges; here the same algebra is exercised through the
        // interface-adjacent surface the seek bar consumes.
        val normalized = BufferedRanges.normalize(
            listOf(80_000L..100_000L, 10_000L..20_000L, 20_000L..30_000L, 500_000L..600_000L),
            durationMs = 90_000,
        )
        assertEquals(listOf(10_000L..30_000L, 80_000L..90_000L), normalized)

        // Degenerate and out-of-bounds windows collapse to nothing.
        assertTrue(BufferedRanges.contiguous(30_000L, 30_000L, 90_000L).isEmpty())
        assertTrue(BufferedRanges.normalize(listOf(200_000L..300_000L), durationMs = 90_000L).isEmpty())
    }

    // ── 4. Capability reporting ─────────────────────────────────────────────

    @Test
    fun `capabilities is a stable value - the UI reads it once`() {
        val engine: MediaEngine = FakeMediaEngine()
        assertSame(engine.capabilities, engine.capabilities)
        assertTrue(
            engine.capabilities == EngineCapabilities(),
            "the headless engine keeps every optional control gated off (screenshot, pip, cues, …)",
        )
    }

    @Test
    fun `every config write reaches the adapter recorder`() {
        // The interface carries updateConfig verbatim to the adapter; the
        // DEDUP guard is the chassis's (EngineStateChassis.updateConfig,
        // pinned by EngineStateChassisTest in player-contract) — the headless
        // fake is a bare MediaEngine, so it records every write.
        val engine: MediaEngine = FakeMediaEngine()
        val config = EngineConfig(subtitleDelayMs = 250)
        engine.updateConfig(config)
        engine.updateConfig(config.copy(subtitleDelayMs = 500))
        val recorder: FakeMediaEngine = engine as FakeMediaEngine
        assertEquals(listOf(250L, 500L), recorder.appliedConfigs.map { it.subtitleDelayMs })
    }

    // ── 5. The request-side engineSpecific slot ─────────────────────────────

    @Test
    fun `requestSpecific projects the request variant and tolerates foreign variants`() {
        // The per-engine payload rides the sanctioned engineSpecific slot
        // behind a typed projection; adapters unpack what they consume and a
        // config-side variant landing in the request slot is tolerated as
        // null (the same tolerance EngineConfig.engineSpecific shows).
        val tls = com.raulshma.jellyplay.core.model.PlaybackTls("crt", "key")
        val request = PlaybackRequest(
            uri = "http://x/item",
            title = "t",
            engineSpecific = com.raulshma.jellyplay.core.model.PlaybackRequestSpecific(
                normalizationGain = -6.5f,
                mimeType = "video/x-matroska",
                tls = tls,
            ),
        )
        val specific = request.requestSpecific
        assertEquals(-6.5f, specific?.normalizationGain)
        assertEquals("video/x-matroska", specific?.mimeType)
        assertSame(tls, specific?.tls)

        val foreign = PlaybackRequest(uri = "u", title = "t", engineSpecific = com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig())
        assertTrue(foreign.requestSpecific == null, "a config-side variant is not the request payload")
        assertTrue(PlaybackRequest(uri = "u", title = "t").requestSpecific == null, "no slot, no payload")
    }
}
