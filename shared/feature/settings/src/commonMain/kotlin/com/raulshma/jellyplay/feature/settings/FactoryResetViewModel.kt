package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.buildUserPreferencesSnapshot
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.first

/**
 * Backs the Factory Reset review screen. Holds the live [preferences] (current)
 * alongside the immutable [factory] baseline (`UserPreferences()` with all
 * default args) so the UI can render a per-category current-vs-default diff and
 * changed-count without duplicating default values.
 *
 * This is a rarely-opened screen, so [preferences] is built ONE-SHOT on entry
 * from the 18 domain-store slices + `AppRuntimeStateStore` + `PinRateLimiter`
 * (see [buildFromSlices]) instead of subscribing to an eager aggregate
 * `StateFlow`. All writes flow through [PreferencesEditor] (the single
 * auditable write seam) — no new mutation path is introduced.
 */
class FactoryResetViewModel(
    private val playbackStore: PlaybackStore,
    private val appearanceStore: AppearanceStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val downloadsStore: DownloadsStore,
    private val engineStore: PlayerEngineStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val audioCacheStore: AudioCacheStore,
    private val libraryStore: LibraryStore,
    private val navigationStore: NavigationStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val notificationStore: NotificationStore,
    private val screensaverStore: ScreensaverStore,
    private val securityStore: SecurityStore,
    private val subtitleLanguageStore: SubtitleLanguageStore,
    private val syncPlayCastStore: SyncPlayCastStore,
    private val experimentalStore: ExperimentalStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val pinRateLimiter: PinRateLimiter,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /** Factory baseline — `UserPreferences` constructed with every default arg. */
    val factory: UserPreferences = UserPreferences()

    var preferences by composeState(UserPreferences())
        private set

    init {
        launch { preferences = buildFromSlices() }
    }

    /**
     * Builds the [UserPreferences] diff snapshot once from the 18 domain-store
     * slices + runtime/PIN extras. Each slice is read via a single `.first()`;
     * there is no live subscription. The field-by-field mapping lives in the
     * pure [buildUserPreferencesSnapshot] builder so it is independently testable
     * — this method only gathers the slices.
     */
    private suspend fun buildFromSlices(): UserPreferences =
        buildUserPreferencesSnapshot(
            playback = playbackStore.playback.first(),
            videoPlayer = videoPlayerStore.videoPlayer.first(),
            engine = engineStore.playerEngine.first(),
            subtitle = subtitleLanguageStore.subtitle.first(),
            audio = audioStore.audio.first(),
            audioEffects = audioEffectsStore.audioEffects.first(),
            audioCache = audioCacheStore.audioCache.first(),
            appearance = appearanceStore.appearance.first(),
            homeDiscovery = homeDiscoveryStore.homeDiscovery.first(),
            library = libraryStore.library.first(),
            navigation = navigationStore.navigation.first(),
            downloads = downloadsStore.downloads.first(),
            networkOffline = networkOfflineStore.networkOffline.first(),
            notification = notificationStore.notification.first(),
            syncPlayCast = syncPlayCastStore.syncPlayCast.first(),
            screensaver = screensaverStore.screensaver.first(),
            security = securityStore.security.first(),
            experimental = experimentalStore.experimental.first(),
            runtime = appRuntimeStateStore.state.first(),
            pinLockout = pinRateLimiter.getPinLockoutState(),
        )

    /** Resets every preference in [category] to its factory default. */
    fun resetCategory(category: PreferenceResetCategory) {
        editor.resetCategory(category)
    }

    /** Resets the entire preferences DataStore to factory defaults. */
    fun resetAll() {
        editor.clearAllPreferences()
    }
}
