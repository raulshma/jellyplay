package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.AppearancePreferences
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.ExperimentalPreferences
import com.raulshma.jellyplay.core.model.LanguagePreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitlePreferences
import com.raulshma.jellyplay.core.model.SyncPlayPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.VideoPlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [PreferenceProjections] — the read layer that combines the store
 * slices into the per-domain / per-screen preference types. Each projection
 * must reflect writes to its own stores and stay unchanged when an unrelated
 * store is written (the whole point of splitting the aggregate: a sub-screen
 * collecting one slice recomposes only when its own fields change).
 *
 * Replaces the demolished `PreferenceSliceTest`, which asserted the same
 * properties against the legacy `UserPreferencesStore.<slice>Preferences`
 * facade flows + `val UserPreferences.<slice>` extension getters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferenceProjectionsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var graph: PreferenceSliceGraph
    private val projections: PreferenceProjections get() = graph.projections

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            // Drain the WhileSubscribed/Eagerly-cached slice flows so the
            // cleared state is observed before each test writes + reads.
            drainInitialSlices()
        }
    }

    private suspend fun drainInitialSlices() {
        projections.videoPlayerPreferences.first()
        projections.audioPlayerPreferences.first()
        projections.subtitlePreferences.first()
        projections.securityPreferences.first()
        projections.downloadPreferences.first()
        projections.syncPlayPreferences.first()
        projections.appearancePreferences.first()
        projections.playbackPreferences.first()
        projections.audioPreferences.first()
        projections.storagePreferences.first()
        projections.navigationCustomizationPreferences.first()
        projections.languagePreferences.first()
        projections.experimentalPreferences.first()
        projections.appearanceScreenPreferences.first()
        projections.notificationPreferences.first()
    }

    @Test
    fun `projections expose defaults before any write`() = runTest {
        assertEquals(VideoPlayerPreferences(), projections.videoPlayerPreferences.first())
        assertEquals(SecurityPreferences(), projections.securityPreferences.first())
        assertEquals(AppearancePreferences(), projections.appearancePreferences.first())
        assertEquals(PlaybackPreferences(), projections.playbackPreferences.first())
        assertEquals(AudioPreferences(), projections.audioPreferences.first())
        assertEquals(SubtitlePreferences(), projections.subtitlePreferences.first())
        assertEquals(DownloadPreferences(), projections.downloadPreferences.first())
        assertEquals(SyncPlayPreferences(), projections.syncPlayPreferences.first())
        assertEquals(LanguagePreferences(), projections.languagePreferences.first())
        assertEquals(ExperimentalPreferences(), projections.experimentalPreferences.first())
        assertEquals(NavigationCustomizationPreferences(), projections.navigationCustomizationPreferences.first())
        assertEquals(AppearanceScreenPreferences(), projections.appearanceScreenPreferences.first())
    }

    @Test
    fun `a video-player write is reflected only in the video-player projection`() = runTest {
        val videoBefore = projections.videoPlayerPreferences.first()
        val audioBefore = projections.audioPlayerPreferences.first()

        graph.videoPlayerStore.setVideoGesturesEnabled(!videoBefore.videoGesturesEnabled)

        val videoAfter = projections.videoPlayerPreferences.first()
        assertNotEquals(videoBefore, videoAfter)
        assertEquals(!videoBefore.videoGesturesEnabled, videoAfter.videoGesturesEnabled)
        // The audio projection must be unaffected by a video-only write.
        assertEquals(audioBefore, projections.audioPlayerPreferences.first())
    }

    @Test
    fun `an appearance write does not change the security projection`() = runTest {
        val securityBefore = projections.securityPreferences.first()
        graph.appearanceStore.setOledMode(true)
        assertEquals(securityBefore, projections.securityPreferences.first())
    }

    @Test
    fun `a playback write is reflected in the playback projection and not the audio projection`() = runTest {
        val playbackBefore = projections.playbackPreferences.first()
        val audioBefore = projections.audioPreferences.first()

        graph.videoPlayerStore.setCinemaModeEnabled(!playbackBefore.cinemaModeEnabled)

        val playbackAfter = projections.playbackPreferences.first()
        assertNotEquals(playbackBefore, playbackAfter)
        assertEquals(!playbackBefore.cinemaModeEnabled, playbackAfter.cinemaModeEnabled)
        // The audio projection must be unaffected by a playback-only write.
        assertEquals(audioBefore, projections.audioPreferences.first())
    }

    @Test
    fun `an audio-effects write is reflected in the audio projection and not the playback projection`() = runTest {
        val playbackBefore = projections.playbackPreferences.first()
        graph.audioEffectsStore.setBassBoostEnabled(true)
        assertTrue(projections.audioPreferences.first().bassBoostEnabled)
        // The playback projection must be unaffected by an audio-only write.
        assertEquals(playbackBefore, projections.playbackPreferences.first())
    }

    @Test
    fun `a multi-store write is reflected in the cross-store appearance-screen projection`() = runTest {
        val screenBefore = projections.appearanceScreenPreferences.first()
        // themeMode lives on AppearanceStore; homeHeroEnabled on HomeDiscoveryStore —
        // the appearance-screen projection combines both, so a write to each must
        // surface in the same projection value.
        graph.appearanceStore.setThemeMode(ThemeMode.DARK)
        graph.homeDiscoveryStore.setHomeHeroEnabled(!screenBefore.homeHeroEnabled)

        val screenAfter = projections.appearanceScreenPreferences.first()
        assertNotEquals(screenBefore, screenAfter)
        assertEquals(ThemeMode.DARK, screenAfter.themeMode)
        assertEquals(!screenBefore.homeHeroEnabled, screenAfter.homeHeroEnabled)
    }

    @Test
    fun `a playback preferred-player write reaches the video-player projection`() = runTest {
        // preferredPlayer is owned by PlaybackStore but read by the video-player
        // surface — the projection must span stores.
        val before = projections.videoPlayerPreferences.first().preferredPlayer
        graph.playbackStore.setPreferredPlayer(
            if (before == PlayerType.MPV) PlayerType.EXO_PLAYER else PlayerType.MPV,
        )
        val after = projections.videoPlayerPreferences.first().preferredPlayer
        assertNotEquals(before, after)
    }

    @Test
    fun `streaming quality write reaches both playback and storage projections`() = runTest {
        // streamingQuality (PlaybackStore) is read by both PlaybackSettingsScreen
        // and StorageSettingsScreen — both projections must reflect the write.
        val newQuality = StreamingQuality.UHD_4K
        graph.playbackStore.setStreamingQuality(newQuality)
        assertEquals(newQuality, projections.playbackPreferences.first().streamingQuality)
        assertEquals(newQuality, projections.storagePreferences.first().streamingQuality)
    }

    @Test
    fun `an unrelated store write does not flip an unaffected projection`() = runTest {
        val navBefore = projections.navigationCustomizationPreferences.first()
        val hapticsBefore = graph.appearanceStore.appearance.value.hapticsEnabled
        // Writing appearance must not change navigation-customization fields.
        graph.appearanceStore.setHapticsEnabled(!hapticsBefore)
        assertEquals(navBefore, projections.navigationCustomizationPreferences.first())
        // Sanity: the appearance slice really did change.
        assertNotEquals(hapticsBefore, graph.appearanceStore.appearance.value.hapticsEnabled)
    }

    @Test
    fun `a download write is reflected in the download projection and not the appearance projection`() = runTest {
        val downloadBefore = projections.downloadPreferences.first()
        val appearanceBefore = projections.appearancePreferences.first()

        graph.downloadsStore.setWifiOnlyDownloads(!downloadBefore.wifiOnlyDownloads)

        val downloadAfter = projections.downloadPreferences.first()
        assertNotEquals(downloadBefore.wifiOnlyDownloads, downloadAfter.wifiOnlyDownloads)
        // An unrelated projection stays put.
        assertEquals(appearanceBefore, projections.appearancePreferences.first())
    }

    @Test
    fun `a notification newsletter write is reflected in the appearance-screen projection`() = runTest {
        // The notification store's only public setters touch the newsletter
        // config, which the appearance-screen projection surfaces (not the
        // notificationPreferences projection). Assert the write reaches it.
        val before = projections.appearanceScreenPreferences.first().newsletterEnabled
        graph.notificationStore.setNewsletterEnabled(!before)
        val after = projections.appearanceScreenPreferences.first().newsletterEnabled
        assertNotEquals(before, after)
    }

    @Test
    fun `a navigation style write is reflected in navigationCustomizationPreferences`() = runTest {
        val before = projections.navigationCustomizationPreferences.first().navigationStyle
        val target = if (before == com.raulshma.jellyplay.core.model.NavigationStyle.EXPRESSIVE)
            com.raulshma.jellyplay.core.model.NavigationStyle.CLASSIC
        else
            com.raulshma.jellyplay.core.model.NavigationStyle.EXPRESSIVE
        graph.navigationStore.setNavigationStyle(target)
        val after = projections.navigationCustomizationPreferences.first().navigationStyle
        assertEquals(target, after)
    }

    @Test
    fun `a syncplay write is reflected in the syncplay projection`() = runTest {
        val before = projections.syncPlayPreferences.first().syncPlayAutoAcceptInvites
        graph.syncPlayCastStore.setSyncPlayAutoAcceptInvites(!before)
        val after = projections.syncPlayPreferences.first().syncPlayAutoAcceptInvites
        assertNotEquals(before, after)
    }

    @Test
    fun `an experimental-features write is reflected in the experimental projection`() = runTest {
        val before = projections.experimentalPreferences.first().enabledExperimentalFeatures
        graph.experimentalStore.setEnabledExperimentalFeatures(before + ExperimentalFeature.HOME_CARD_CLIPPING)
        val after = projections.experimentalPreferences.first().enabledExperimentalFeatures
        assertTrue(after.contains(ExperimentalFeature.HOME_CARD_CLIPPING))
        assertNotEquals(before, after)
    }

    @Test
    fun `a subtitle forced-only write is reflected in the language projection`() = runTest {
        // subtitlesForcedOnly is read by the LanguageSettings projection (it is
        // absent from the narrower SubtitlePreferences projection).
        val before = projections.languagePreferences.first().subtitlesForcedOnly
        graph.subtitleLanguageStore.setSubtitlesForcedOnly(!before)

        assertEquals(!before, projections.languagePreferences.first().subtitlesForcedOnly)
    }
}
