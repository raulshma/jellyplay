package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [ThemeMusicPlayer]'s ambient-playback gating:
 *
 * - The backdrop-theme-music preference gates everything: with it off, no
 *   theme-song lookup ever happens.
 * - With it on, the first theme song of the item is resolved to a stream URL
 *   and played on a dedicated player at ambient volume, looping a single
 *   item; a failed lookup or a blank stream URL is swallowed (no player).
 * - Re-requesting the same item while nothing is playing re-fetches (the
 *   no-op guard only applies to a genuinely playing track).
 * - `stop()` and `release()` are safe when nothing is playing, and release
 *   kills the scope (later requests are inert).
 *
 * The dedicated ExoPlayer is real (Robolectric); collaborators are mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeMusicPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val appearanceStore: AppearanceStore = mockk()

    private val appearance = MutableStateFlow(AppearanceSlice())

    @Before
    fun setUp() {
        every { appearanceStore.appearance } returns appearance
        coEvery { mediaRepository.getThemeSongs(any()) } returns Result.success(
            listOf(MediaItem(id = "theme1", name = "Theme", mediaType = MediaType.MUSIC)),
        )
        every {
            playbackRepository.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        } returns "http://stream/theme"
    }

    private fun player() = ThemeMusicPlayer(
        context = context,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        appearanceStore = appearanceStore,
    )

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test
    fun `a disabled preference never looks up theme songs`() {
        val p = player()

        p.playThemeFor("item1")
        idle()

        coVerify(exactly = 0) { mediaRepository.getThemeSongs(any()) }
        p.release()
    }

    @Test
    fun `an enabled preference resolves and plays the first theme song`() {
        appearance.value = AppearanceSlice(backdropThemeMusicEnabled = true)
        val p = player()

        p.playThemeFor("item1")
        idle()

        coVerify(exactly = 1) { mediaRepository.getThemeSongs("item1") }
        verify(exactly = 1) {
            playbackRepository.getStreamUrl(itemId = "theme1", mediaSourceId = "theme1", startTimeTicks = any(), liveStreamId = any())
        }
        p.release()
    }

    @Test
    fun `a failed theme lookup is swallowed without crashing`() {
        appearance.value = AppearanceSlice(backdropThemeMusicEnabled = true)
        coEvery { mediaRepository.getThemeSongs("item1") } returns Result.failure(IllegalStateException("down"))
        val p = player()

        p.playThemeFor("item1")
        idle()

        verify(exactly = 0) {
            playbackRepository.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        }
        p.release()
    }

    @Test
    fun `a blank stream URL never starts the player`() {
        appearance.value = AppearanceSlice(backdropThemeMusicEnabled = true)
        every {
            playbackRepository.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        } returns ""
        val p = player()

        p.playThemeFor("item1")
        idle()

        // No crash and the request pipeline ran to its guard — a second call
        // re-fetches (no player existed to short-circuit on).
        p.playThemeFor("item1")
        idle()
        coVerify(exactly = 2) { mediaRepository.getThemeSongs("item1") }
        p.release()
    }

    @Test
    fun `re-requesting the same item while not yet playing re-fetches`() {
        appearance.value = AppearanceSlice(backdropThemeMusicEnabled = true)
        val p = player()

        p.playThemeFor("item1")
        idle()
        p.playThemeFor("item1")
        idle()

        coVerify(exactly = 2) { mediaRepository.getThemeSongs("item1") }
        p.release()
    }

    @Test
    fun `stop and release are safe when nothing plays`() {
        val p = player()

        p.stop()
        p.stop()
        p.release()
        p.release()

        assertTrue(true) // the assertion is: no exception from the no-op paths
    }

    @Test
    fun `requests after release are inert`() {
        appearance.value = AppearanceSlice(backdropThemeMusicEnabled = true)
        val p = player()

        p.release()
        p.playThemeFor("item1")
        idle()

        coVerify(exactly = 0) { mediaRepository.getThemeSongs(any()) }
    }
}
