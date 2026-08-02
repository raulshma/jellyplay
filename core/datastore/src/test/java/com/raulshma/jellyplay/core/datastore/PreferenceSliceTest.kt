package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.AudioPlayerPreferences
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.appearanceScreen
import com.raulshma.jellyplay.core.model.audio
import com.raulshma.jellyplay.core.model.experimental
import com.raulshma.jellyplay.core.model.language
import com.raulshma.jellyplay.core.model.navigationCustomization
import com.raulshma.jellyplay.core.model.playback
import com.raulshma.jellyplay.core.model.storage
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.model.StoragePreferences
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
        // AndroidX DataStore forbids two delegates for the same file in one
        // process, so all stores share a single provided instance.
        val dataStore = TestDataStoreProvider.get(context)
        store = createUserPreferencesStore(scope, dataStore)
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

    @Test
    fun `per-screen slices equal their projection from the full preferences`() = runTest {
        // Each slice StateFlow must reflect the same values the projection
        // getter produces from the whole UserPreferences. This is order-
        // independent (unlike a defaults assertion) and catches projection drift
        // — a field the screen reads but the slice omits won't appear here, but
        // a projection that disagrees with the store's slice value will.
        val prefs = store.preferences.value
        assertEquals(prefs.playback, store.playbackPreferences.first())
        assertEquals(prefs.audio, store.audioPreferences.first())
        assertEquals(prefs.storage, store.storagePreferences.first())
        assertEquals(prefs.appearanceScreen, store.appearanceScreenPreferences.first())
        assertEquals(prefs.navigationCustomization, store.navigationCustomizationPreferences.first())
        assertEquals(prefs.language, store.languagePreferences.first())
        assertEquals(prefs.experimental, store.experimentalPreferences.first())
        assertEquals(prefs.pinnedHomeSections, store.pinnedHomeSectionsFlow.first())
    }

    @Test
    fun `a playback write is reflected in the playback slice and not the audio slice`() = runTest {
        val playbackBefore = store.playbackPreferences.first()
        val audioBefore = store.audioPreferences.first()

        store.setCinemaModeEnabled(!playbackBefore.cinemaModeEnabled)

        val playbackAfter = store.playbackPreferences.first()
        assertNotEquals(playbackBefore, playbackAfter)
        assertEquals(!playbackBefore.cinemaModeEnabled, playbackAfter.cinemaModeEnabled)
        // The audio slice must be unaffected by a playback-only write.
        assertEquals(audioBefore, store.audioPreferences.first())
    }

    @Test
    fun `an audio write is reflected in the audio slice and not the playback slice`() = runTest {
        val playbackBefore = store.playbackPreferences.first()
        store.setBassBoostEnabled(true)
        assertEquals(true, store.audioPreferences.first().bassBoostEnabled)
        // The playback slice must be unaffected by an audio-only write.
        assertEquals(playbackBefore, store.playbackPreferences.first())
    }

    @Test
    fun `a pinned-sections write is reflected in the pinned flow`() = runTest {
        val before = store.pinnedHomeSectionsFlow.first().size
        store.addPinnedHomeSection(
            com.raulshma.jellyplay.core.model.PinnedHomeSection(
                type = com.raulshma.jellyplay.core.model.PinnedSectionType.FAVORITES,
                sourceId = com.raulshma.jellyplay.core.model.PinnedHomeSection.FAVORITES_SOURCE_ID,
                title = "Favorites",
            )
        )
        assertEquals(before + 1, store.pinnedHomeSectionsFlow.first().size)
    }
}
