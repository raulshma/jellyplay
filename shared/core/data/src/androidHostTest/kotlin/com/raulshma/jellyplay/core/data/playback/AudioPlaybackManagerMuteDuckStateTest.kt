package com.raulshma.jellyplay.core.data.playback

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the EXPLICIT mute/duck machine ([MuteDuckVolumeState] in
 * [AudioPlaybackManager]) — the migration of the recorded Tier-1 finding
 * ("muted-ness inferred from volume > 0f") to owned state, with every
 * observable byte preserved. The volume inference used to be the ONLY truth
 * about mute-ness, so these pins enumerate the edges where the former
 * inference delivered a verdict and the explicit state must land on the SAME
 * verdict:
 *
 *  - the mute/unmute round trip rides the captured level (the legacy
 *    silent-player capture quirk — `1f` — is preserved, not fixed);
 *  - an external engine-volume write while muted (user path, software path,
 *    the increment paths) ENDS the mute exactly as the inference verdict did:
 *    the next toggle re-captures the new audible level instead of restoring a
 *    stale capture;
 *  - the crossfade restore edges (the crossfader cancel's `1f` unwind —
 *    driven here through [AudioPlaybackManager.setGaplessEnabled] — and the
 *    swap's full-volume secondary, pinned at the handoff suite) run the same
 *    reconcile;
 *  - the duck refuses the explicit mute AND the unmuted-silent player (the
 *    volume arm's byte behavior), captures only louder levels, and a mute
 *    captured mid-duck ends at the duck regain.
 *
 * The suite's construction harness is the `AudioPlaybackManagerDuckTest`
 * shape: relaxed collaborators everywhere, the stateful stub ExoPlayer
 * injected through the test factory (the stub becomes the live engine on the
 * first `getOrCreatePlayer`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioPlaybackManagerMuteDuckStateTest {

    /** The stub's backing volume cell — both the getter and the setter JVM names read/write it. */
    private val playerVolume = floatArrayOf(1f)

    private val player: ExoPlayer = mockk(relaxed = true) {
        every { volume } answers { playerVolume[0] }
        every { setVolume(any<Float>()) } answers { playerVolume[0] = firstArg() }
    }

    private val scope = CoroutineScope(SupervisorJob())

    /** Held so the night-mode-edge pin can read back the manager's port wiring. */
    private val effectsProcessor: AudioEffectsProcessor = mockk(relaxed = true)

    private val manager: AudioPlaybackManager = AudioPlaybackManager(
        context = ApplicationProvider.getApplicationContext(),
        detailFeed = mockk(relaxed = true),
        libraryBrowser = mockk(relaxed = true),
        playbackRepository = mockk(relaxed = true),
        imageUrlProvider = mockk(relaxed = true),
        playbackSourceResolver = mockk(relaxed = true),
        sessionManager = mockk(relaxed = true),
        audioStore = mockk(relaxed = true),
        audioEffectsStore = mockk(relaxed = true),
        playbackStore = mockk(relaxed = true),
        queuePersistenceHelper = mockk(relaxed = true),
        bandwidthMonitor = mockk(relaxed = true),
        bandwidthInterceptor = mockk(relaxed = true),
        lyricsManager = mockk(relaxed = true),
        effectsProcessor = effectsProcessor,
        sleepCountdown = mockk(relaxed = true),
        jellyfinRemotePlayCastStrategy = mockk(relaxed = true),
        audioStreamCache = mockk(relaxed = true),
        audioPrefetchEngine = mockk(relaxed = true),
        playbackScope = scope,
        playerFactory = { player },
    )

    @BeforeTest
    fun setUp() {
        shadowOf(Looper.getMainLooper()).idle()
        // The cold session path builds the player through the factory and
        // assigns it as the manager's live engine — the same seat every
        // mute/duck op reads.
        manager.ensureAudioSession()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    // ── the explicit round trip ──────────────────────────────────────────

    @Test
    fun `mute and unmute round-trip the captured level through the explicit state`() {
        playerVolume[0] = 0.6f

        manager.setMuted(muted = true)
        assertEquals(0f, playerVolume[0], "the explicit mute writes silence")

        manager.setMuted(muted = false)
        assertEquals(0.6f, playerVolume[0], "the unmute restores the CAPTURE, not an inference")
    }

    @Test
    fun `toggleMute reads the explicit state, not the volume`() {
        playerVolume[0] = 0.6f

        manager.toggleMute()
        assertEquals(0f, playerVolume[0])

        manager.toggleMute()
        assertEquals(0.6f, playerVolume[0], "the toggle's truth is the state, so it restores")
    }

    @Test
    fun `a mute captured on a silent player restores to full volume (legacy quirk preserved)`() {
        // The capture formula is byte-preserved: muting an ALREADY silent
        // player re-captures 1f (the old inline `if (current > 0f) current
        // else 1f`), so mute-mute-unmute lands at full volume. Recorded as
        // preserved legacy, deliberately not fixed.
        playerVolume[0] = 0f

        manager.setMuted(muted = true)
        assertEquals(0f, playerVolume[0])

        manager.setMuted(muted = true) // the re-mute while muted
        manager.setMuted(muted = false)
        assertEquals(1f, playerVolume[0], "the silent re-mute captured 1f — the legacy formula")
    }

    // ── external writes while muted: the inference verdict, preserved ────

    @Test
    fun `an external user volume write while muted ends the mute as the inference verdict did`() {
        playerVolume[0] = 0.6f
        manager.setMuted(muted = true)
        assertEquals(0f, playerVolume[0])

        // The remote SetVolume path lands while the explicit mute stands.
        manager.setVolume(0.5f)
        assertEquals(0.5f, playerVolume[0], "the write is audible — as it always was")

        // The former inference read 0.5 > 0f and judged UNMUTED, so the next
        // toggle re-muted at 0.5. The explicit state must land on the same
        // verdict (a stale mute would have restored 0.6 here instead).
        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the toggle re-captured the NEW audible level")

        manager.toggleMute()
        assertEquals(0.5f, playerVolume[0], "and the unmute restores that new capture")
    }

    @Test
    fun `an external software write while muted ends the mute too`() {
        playerVolume[0] = 0.6f
        manager.setMuted(muted = true)

        // The sleep-timer fade / duck path (isUserChange = false) lands while
        // muted — the fade is louder than silence, so the inference judged
        // unmuted and the state reconciles the same way.
        manager.setVolume(0.4f, isUserChange = false)
        assertEquals(0.4f, playerVolume[0])

        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the toggle re-captured the faded level")
        manager.toggleMute()
        assertEquals(0.4f, playerVolume[0])
    }

    @Test
    fun `increaseVolume from a muted player unmutes at the increment`() {
        playerVolume[0] = 0.6f
        manager.setMuted(muted = true)

        manager.increaseVolume() // 0f + 0.05
        assertEquals(0.05f, playerVolume[0], "audible — the mute ended, as the inference judged")

        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the toggle mutes at the incremented level")
        manager.toggleMute()
        assertEquals(0.05f, playerVolume[0])
    }

    // ── the crossfade restore edges ──────────────────────────────────────

    @Test
    fun `the crossfade cancel restore ends a mute (the port reaches the manager's reconcile)`() {
        playerVolume[0] = 0.7f
        manager.setMuted(muted = true)
        assertEquals(0f, playerVolume[0])

        // setGaplessEnabled(true) fires crossfader.cancel(), which always
        // restores the live engine to 1f and signals the restore edge. The
        // former inference read 1f > 0f and judged unmuted; the explicit
        // state lands on the same verdict through the crossfader port.
        manager.setGaplessEnabled(true)
        assertEquals(1f, playerVolume[0])

        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the toggle re-captured the restored 1f")
        manager.toggleMute()
        assertEquals(1f, playerVolume[0])
    }

    // ── the night-mode volume edge (REVIEW FIX) ──────────────────────────

    @Test
    fun `the night-mode volume-write edge ends a mute as the inference verdict did`() {
        // The wiring lives in start() (the port is assigned beside the
        // playerProvider). The rest of the suite never needs it — start()'s
        // persistence restore is inert against the relaxed collaborators. The
        // assignment is captured through a stubbed setter: a relaxed mock's
        // property getter serves a relaxed default rather than the recorded
        // write, so a plain read-back cannot prove WHICH lambda landed.
        val wired = slot<() -> Unit>()
        every { effectsProcessor.onNightModeVolumeWrite = capture(wired) } just Runs
        manager.start()
        shadowOf(Looper.getMainLooper()).idle()

        playerVolume[0] = 0.6f
        manager.setMuted(muted = true)
        assertEquals(0f, playerVolume[0])

        // applyNightMode's tail (either arm — here the disable arm's `1f`
        // unwind, simulated at the stub) invokes the assigned port; the port
        // must be the manager's reconcile.
        playerVolume[0] = 1f
        wired.captured.invoke()

        // The former inference read 1f > 0f and judged UNMUTED; the explicit
        // state must land on the same verdict — the next toggle re-muted at
        // the new level, and the unmute after restores that capture.
        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the reconcile ended the mute; the toggle re-captured the audible level")
        manager.toggleMute()
        assertEquals(1f, playerVolume[0], "and the unmute restores that new capture")
    }

    // ── the duck rows of the machine ─────────────────────────────────────

    @Test
    fun `a duck never speaks for the explicit mute and captures nothing`() {
        playerVolume[0] = 0.8f
        manager.setMuted(muted = true)

        manager.duck(volume = 0.2f)
        assertEquals(0f, playerVolume[0], "the duck refused the explicit mute")
        manager.restoreFromDuck()
        assertEquals(0f, playerVolume[0], "nothing was captured to restore")

        manager.setMuted(muted = false)
        assertEquals(0.8f, playerVolume[0], "the duck attempt left the mute capture untouched")
    }

    @Test
    fun `a duck on an unmuted silent player is still refused (the volume arm preserved)`() {
        // The explicit mute alone would allow this duck; the guard's volume
        // arm keeps the byte behavior for an unmuted-at-zero player (the user
        // slider at zero).
        playerVolume[0] = 0f

        manager.duck(volume = 0.2f)
        assertEquals(0f, playerVolume[0], "duck must not speak for a silent player")
        manager.restoreFromDuck()
        assertEquals(0f, playerVolume[0], "and nothing was captured to restore")
    }

    @Test
    fun `a mute captured mid-duck ends at the duck regain`() {
        playerVolume[0] = 0.9f
        manager.duck(volume = 0.2f)
        manager.setMuted(muted = true) // the loss upgrades to a mute while ducked
        assertEquals(0f, playerVolume[0])

        manager.restoreFromDuck()
        assertEquals(0.9f, playerVolume[0], "the regain restores the pre-duck level — audible")
        // The former inference judged unmuted at the regain (0.9 > 0f); the
        // state must land on the same verdict, so the toggle re-mutes at 0.9.
        manager.toggleMute()
        assertEquals(0f, playerVolume[0], "the toggle re-captured the restored level")
        manager.toggleMute()
        assertEquals(0.9f, playerVolume[0])
    }
}
