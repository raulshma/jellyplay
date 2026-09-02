package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for the pure [buildUserPreferencesSnapshot] builder. No stores,
 * no IO — just slice value objects in, [UserPreferences] out. Guards the
 * 1:1 field wiring extracted from `FactoryResetViewModel.buildFromSlices`.
 */
class UserPreferencesSnapshotBuilderTest {

    /** All-default slices + NOT_LOCKED pin must reproduce `UserPreferences()`. */
    @Test
    fun `defaults reproduce UserPreferences factory baseline`() {
        val built = buildUserPreferencesSnapshot(
            playback = PlaybackSlice(),
            videoPlayer = VideoPlayerSlice(),
            engine = PlayerEngineSlice(),
            subtitle = SubtitleSlice(),
            audio = AudioSlice(),
            audioEffects = AudioEffectsSlice(),
            audioCache = AudioCacheSlice(),
            appearance = AppearanceSlice(),
            homeDiscovery = HomeDiscoverySlice(),
            library = LibrarySlice(),
            navigation = NavigationSlice(),
            downloads = DownloadsSlice(),
            networkOffline = NetworkOfflineSlice(),
            notification = NotificationSlice(),
            syncPlayCast = SyncPlayCastSlice(),
            screensaver = ScreensaverSlice(),
            security = SecuritySlice(),
            experimental = ExperimentalSlice(),
            runtime = AppRuntimeState(),
            pinLockout = PinLockoutState.NOT_LOCKED,
        )

        assertEquals(UserPreferences(), built)
    }

    /** A non-default value on each of several representative slices must land. */
    @Test
    fun `slice values flow to the matching UserPreferences fields`() {
        val built = buildUserPreferencesSnapshot(
            playback = PlaybackSlice(preferredPlayer = PlayerType.LIBVLC),
            appearance = AppearanceSlice(
                themeMode = ThemeMode.DARK,
                dynamicTheming = false,
                colorStyle = ColorStyle.VIBRANT,
                accentColorSwatch = "custom",
            ),
            security = SecuritySlice(pinLockEnabled = true, pinHash = "hash"),
            experimental = ExperimentalSlice(
                enabledExperimentalFeatures = setOf(ExperimentalFeature.MEDIA_CARD_PEEK),
            ),
            runtime = AppRuntimeState(onboardingCompleted = true),
            pinLockout = PinLockoutState(failedAttempts = 3, lockoutUntilEpochMs = 99L),
            videoPlayer = VideoPlayerSlice(),
            engine = PlayerEngineSlice(),
            subtitle = SubtitleSlice(),
            audio = AudioSlice(),
            audioEffects = AudioEffectsSlice(),
            audioCache = AudioCacheSlice(),
            homeDiscovery = HomeDiscoverySlice(),
            library = LibrarySlice(),
            navigation = NavigationSlice(),
            downloads = DownloadsSlice(),
            networkOffline = NetworkOfflineSlice(),
            notification = NotificationSlice(),
            syncPlayCast = SyncPlayCastSlice(),
            screensaver = ScreensaverSlice(),
        )

        assertEquals(PlayerType.LIBVLC, built.preferredPlayer)
        assertEquals(ThemeMode.DARK, built.themeMode)
        assertFalse(built.dynamicTheming)
        assertEquals(ColorStyle.VIBRANT, built.colorStyle)
        assertEquals(built.accentColorSwatch, "custom")
        assertTrue(built.pinLockEnabled)
        assertEquals(built.pinHash, "hash")
        assertTrue(ExperimentalFeature.MEDIA_CARD_PEEK in built.enabledExperimentalFeatures)
        assertTrue(built.onboardingCompleted)
        assertEquals(3, built.pinFailedAttempts)
        assertEquals(99L, built.pinLockoutUntilEpochMs)
    }

    /** Null pin hash (no PIN set) must pass through, not default to non-null. */
    @Test
    fun `null pin hash passes through`() {
        val built = buildUserPreferencesSnapshot(
            playback = PlaybackSlice(),
            videoPlayer = VideoPlayerSlice(),
            engine = PlayerEngineSlice(),
            subtitle = SubtitleSlice(),
            audio = AudioSlice(),
            audioEffects = AudioEffectsSlice(),
            audioCache = AudioCacheSlice(),
            appearance = AppearanceSlice(),
            homeDiscovery = HomeDiscoverySlice(),
            library = LibrarySlice(),
            navigation = NavigationSlice(),
            downloads = DownloadsSlice(),
            networkOffline = NetworkOfflineSlice(),
            notification = NotificationSlice(),
            syncPlayCast = SyncPlayCastSlice(),
            screensaver = ScreensaverSlice(),
            security = SecuritySlice(pinHash = null),
            experimental = ExperimentalSlice(),
            runtime = AppRuntimeState(),
            pinLockout = PinLockoutState.NOT_LOCKED,
        )

        assertNull(built.pinHash)
    }
}
