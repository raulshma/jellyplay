package com.raulshma.jellyplay.core.data.playback

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.playback.focus.AudioPlaybackManagerSurface
import io.mockk.every
import io.mockk.mockk
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
 * Pins the transient-focus-loss duck round-trip on the MUSIC surface —
 * [AudioPlaybackManager.duck]/[AudioPlaybackManager.restoreFromDuck] and the
 * [AudioPlaybackManagerSurface] commands the focus executor calls through:
 *
 *  - the pre-duck level is captured only when it is louder than the duck
 *    target, and the regain restores exactly it;
 *  - a muted (volume ≤ 0) player never gets re-raised by a duck and has
 *    nothing to restore;
 *  - re-ducking at the target is idempotent and re-captures only a louder
 *    level, so a stale marker can never store the duck level itself.
 *
 * The suite's construction harness is the `AudioPlaybackManagerQueueSemanticsTest`
 * shape: relaxed collaborators everywhere, the stateful stub ExoPlayer
 * injected through the test factory (the stub becomes the live engine on the
 * first `getOrCreatePlayer`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioPlaybackManagerDuckTest {

    /** The stub's backing volume cell — both the getter and the setter JVM names read/write it. */
    private val playerVolume = floatArrayOf(1f)

    private val player: ExoPlayer = mockk(relaxed = true) {
        every { volume } answers { playerVolume[0] }
        every { setVolume(any<Float>()) } answers { playerVolume[0] = firstArg() }
    }

    private val scope = CoroutineScope(SupervisorJob())

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
        effectsProcessor = mockk(relaxed = true),
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
        // assigns it as the manager's live engine — the same seat duck()
        // reads.
        manager.ensureAudioSession()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `duck lowers to the target and the regain restores the captured level`() {
        playerVolume[0] = 1f

        manager.duck(volume = 0.2f)
        assertEquals(0.2f, playerVolume[0], "the transient loss ducks the music")

        manager.restoreFromDuck()
        assertEquals(1f, playerVolume[0], "the regain restores the pre-duck level")
    }

    @Test
    fun `restore without a duck is a no-op`() {
        playerVolume[0] = 0.7f

        manager.restoreFromDuck()

        assertEquals(0.7f, playerVolume[0])
    }

    @Test
    fun `a muted player is never re-raised by a duck`() {
        playerVolume[0] = 0f

        manager.duck(volume = 0.2f)
        assertEquals(0f, playerVolume[0], "duck must not speak for a muted player")

        manager.restoreFromDuck()
        assertEquals(0f, playerVolume[0], "and nothing was captured to restore")
    }

    @Test
    fun `re-ducking at the target never captures the duck level itself`() {
        playerVolume[0] = 0.9f

        manager.duck(volume = 0.2f)
        manager.duck(volume = 0.2f) // the call-stack re-entry / second loss at the same target
        assertEquals(0.2f, playerVolume[0])

        manager.restoreFromDuck()
        assertEquals(0.9f, playerVolume[0], "the marker still holds the ORIGINAL louder level")
    }

    @Test
    fun `a quieter-than-target level raises to the duck target without capturing`() {
        // The documented edge (the review note): a user-set 0.1 is quieter
        // than the duck target — the duck raises to 0.2 for audibility and
        // captures nothing, so the restore leaves the duck level standing.
        playerVolume[0] = 0.1f

        manager.duck(volume = 0.2f)
        assertEquals(0.2f, playerVolume[0])

        manager.restoreFromDuck()
        assertEquals(0.2f, playerVolume[0], "no pre-duck marker — the level stays at the duck target")
    }

    @Test
    fun `the MUSIC surface commands ride through to the manager`() {
        playerVolume[0] = 1f
        val surface = AudioPlaybackManagerSurface(lazy { manager })

        surface.duck(0.2f)
        assertEquals(0.2f, playerVolume[0], "the focus executor's duck reaches the manager's software gain")

        surface.restore()
        assertEquals(1f, playerVolume[0], "the focus executor's restore reaches the manager")
    }
}
