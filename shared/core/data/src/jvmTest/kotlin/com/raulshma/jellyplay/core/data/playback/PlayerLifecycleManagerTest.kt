package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [PlayerLifecycleManager], the Activity↔engine lifecycle
 * bridge. Pins the decision logic, not the plumbing:
 *  - `onActivityPause` is suppressed while background video audio is enabled
 *    (the engine must keep running in background);
 *  - `onActivityResume` always reaches the active engine;
 *  - the delegation is direct to [PlayerLifecycleManager.activeCallbacks] —
 *    a cleared (reset) or never-set manager must not touch any engine.
 */
class PlayerLifecycleManagerTest {

    private val playbackStore: PlaybackStore = mockk()
    private val playbackSlice = MutableStateFlow(PlaybackSlice())
    private val callbacks: PlayerLifecycleCallbacks = mockk(relaxed = true)

    private lateinit var manager: PlayerLifecycleManager

    @BeforeTest
    fun setup() {
        every { playbackStore.playback } returns playbackSlice
        manager = PlayerLifecycleManager(playbackStore)
        manager.activeCallbacks = callbacks
    }

    @Test
    fun `activity pause delegates to the active engine`() {
        manager.onActivityPause()

        verify(exactly = 1) { callbacks.onActivityPause() }
    }

    @Test
    fun `activity pause is suppressed while background video audio is enabled`() {
        playbackSlice.value = PlaybackSlice(backgroundVideoAudioEnabled = true)

        manager.onActivityPause()

        // The engine keeps running in background — pausing it would kill the audio.
        verify(exactly = 0) { callbacks.onActivityPause() }
    }

    @Test
    fun `activity resume always delegates, even with background audio on`() {
        playbackSlice.value = PlaybackSlice(backgroundVideoAudioEnabled = true)

        manager.onActivityResume()

        verify(exactly = 1) { callbacks.onActivityResume() }
    }

    @Test
    fun `with no active engine the lifecycle calls are no-ops`() {
        manager.reset()

        manager.onActivityPause()
        manager.onActivityResume()

        verify { callbacks wasNot io.mockk.Called }
    }

    @Test
    fun `a never-set manager is a no-op`() {
        val fresh = PlayerLifecycleManager(playbackStore)

        fresh.onActivityPause()
        fresh.onActivityResume()

        verify { callbacks wasNot io.mockk.Called }
    }

    @Test
    fun `reset clears the active engine callbacks`() {
        manager.reset()

        assertNull(manager.activeCallbacks)

        manager.onActivityPause()

        verify(exactly = 0) { callbacks.onActivityPause() }
    }

    @Test
    fun `isBackgroundAudioEnabled mirrors the store's playback slice`() {
        assertFalse(manager.isBackgroundAudioEnabled)

        playbackSlice.value = PlaybackSlice(backgroundVideoAudioEnabled = true)

        assertTrue(manager.isBackgroundAudioEnabled)
    }
}
