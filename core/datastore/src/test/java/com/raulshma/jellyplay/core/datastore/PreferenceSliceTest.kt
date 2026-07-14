package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AudioPlayerPreferences
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.model.SubtitlePreferences
import com.raulshma.jellyplay.core.model.SyncPlayPreferences
import com.raulshma.jellyplay.core.model.VideoPlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the per-domain preference slices on [UserPreferencesStore]:
 * each slice reflects writes to its own keys and is de-duplicated via
 * `distinctUntilChanged` so an unrelated write does not produce a new
 * slice value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferenceSliceTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: UserPreferencesStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = UserPreferencesStore(context, scope)
    }

    @Test
    fun `slices expose defaults before any write`() = runTest {
        assertEquals(VideoPlayerPreferences(), store.videoPlayerPreferences.first())
        assertEquals(AudioPlayerPreferences(), store.audioPlayerPreferences.first())
        assertEquals(SubtitlePreferences(), store.subtitlePreferences.first())
        assertEquals(SecurityPreferences(), store.securityPreferences.first())
        assertEquals(DownloadPreferences(), store.downloadPreferences.first())
        assertEquals(SyncPlayPreferences(), store.syncPlayPreferences.first())
        assertEquals(AppearancePreferences(), store.appearancePreferences.first())
    }

    @Test
    fun `a video write is reflected only in the video slice`() = runTest {
        val videoBefore = store.videoPlayerPreferences.first()
        val audioBefore = store.audioPlayerPreferences.first()

        store.setVideoGesturesEnabled(!videoBefore.videoGesturesEnabled)

        val videoAfter = store.videoPlayerPreferences.first()
        assertNotEquals(videoBefore, videoAfter)
        assertEquals(!videoBefore.videoGesturesEnabled, videoAfter.videoGesturesEnabled)
        // The audio slice must be unaffected by a video-only write.
        assertEquals(audioBefore, store.audioPlayerPreferences.first())
    }

    @Test
    fun `an appearance write does not change the security slice`() = runTest {
        val securityBefore = store.securityPreferences.first()
        store.setOledMode(true)
        assertEquals(securityBefore, store.securityPreferences.first())
    }
}
