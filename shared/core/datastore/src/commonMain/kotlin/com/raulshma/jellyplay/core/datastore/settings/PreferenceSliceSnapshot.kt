package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
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
import com.raulshma.jellyplay.core.model.PinLockoutState
import kotlinx.serialization.json.JsonElement

/**
 * One-shot snapshot of the 18 domain-store slices plus [AppRuntimeState] and
 * [PinLockoutState] — the diff shape shared by import preview
 * (current-vs-incoming-backup) and factory reset (current-vs-factory).
 *
 * This replaces the former `legacy.UserPreferences` aggregate as the diff
 * carrier: where the old path *rebuilt* the ~150-field aggregate from the
 * slices (a second hand-written copy of every field mapping, in
 * `UserPreferencesSnapshotBuilder`), the diff now reads the slices directly —
 * the same values the screens consume, with no intermediate re-mapping to
 * keep in sync. `pinLockout` is carried alongside because the import-preview
 * current snapshot reads it (it never diffs — no presentation row reads it —
 * but the read is part of the one-shot gather).
 */
data class PreferenceSliceSnapshot(
    val playback: PlaybackSlice,
    val videoPlayer: VideoPlayerSlice,
    val engine: PlayerEngineSlice,
    val subtitle: SubtitleSlice,
    val audio: AudioSlice,
    val audioEffects: AudioEffectsSlice,
    val audioCache: AudioCacheSlice,
    val appearance: AppearanceSlice,
    val homeDiscovery: HomeDiscoverySlice,
    val library: LibrarySlice,
    val navigation: NavigationSlice,
    val downloads: DownloadsSlice,
    val networkOffline: NetworkOfflineSlice,
    val notification: NotificationSlice,
    val syncPlayCast: SyncPlayCastSlice,
    val screensaver: ScreensaverSlice,
    val security: SecuritySlice,
    val experimental: ExperimentalSlice,
    val runtime: AppRuntimeState,
    val pinLockout: PinLockoutState,
) {
    companion object {
        /**
         * The factory baseline: every slice at its default, empty runtime state,
         * no PIN lockout. `FactoryResetViewModel` diffs the live snapshot against
         * this; it is the value the old `UserPreferences()` baseline reproduced.
         */
        val FACTORY: PreferenceSliceSnapshot = PreferenceSliceSnapshot(
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
    }
}

/**
 * Builds a [PreferenceSliceSnapshot] from a decoded v2 [SettingsBackup], for
 * the import-preview "incoming" side. Each slice is decoded leniently: missing
 * keys fall back to the slice's defaults, and malformed elements are ignored
 * (matching `restoreV2` forward-compat). Pure (no IO) so the ViewModel only
 * handles the payload read.
 *
 * `pinLockout` is not part of the backup (it is runtime state in
 * `PinRateLimiter`); callers should pass [PinLockoutState.NOT_LOCKED] unless
 * they have a reason to preserve it.
 */
fun buildPreferenceSliceSnapshotFromBackup(
    backup: SettingsBackup,
    pinLockout: PinLockoutState = PinLockoutState.NOT_LOCKED,
    json: kotlinx.serialization.json.Json = PreferencesJson.import,
): PreferenceSliceSnapshot {
    fun <T> decodeOrDefault(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: T,
    ): T {
        val element: JsonElement = backup.slices[key] ?: return default
        return runCatching { json.decodeFromJsonElement(serializer, element) }.getOrDefault(default)
    }

    return PreferenceSliceSnapshot(
        playback = decodeOrDefault(BackupSliceKey.PLAYBACK, PlaybackSlice.serializer(), PlaybackSlice()),
        appearance = decodeOrDefault(BackupSliceKey.APPEARANCE, AppearanceSlice.serializer(), AppearanceSlice()),
        videoPlayer = decodeOrDefault(BackupSliceKey.VIDEO_PLAYER, VideoPlayerSlice.serializer(), VideoPlayerSlice()),
        downloads = decodeOrDefault(BackupSliceKey.DOWNLOADS, DownloadsSlice.serializer(), DownloadsSlice()),
        engine = decodeOrDefault(BackupSliceKey.PLAYER_ENGINE, PlayerEngineSlice.serializer(), PlayerEngineSlice()),
        homeDiscovery = decodeOrDefault(BackupSliceKey.HOME_DISCOVERY, HomeDiscoverySlice.serializer(), HomeDiscoverySlice()),
        audio = decodeOrDefault(BackupSliceKey.AUDIO, AudioSlice.serializer(), AudioSlice()),
        audioEffects = decodeOrDefault(BackupSliceKey.AUDIO_EFFECTS, AudioEffectsSlice.serializer(), AudioEffectsSlice()),
        audioCache = decodeOrDefault(BackupSliceKey.AUDIO_CACHE, AudioCacheSlice.serializer(), AudioCacheSlice()),
        library = decodeOrDefault(BackupSliceKey.LIBRARY, LibrarySlice.serializer(), LibrarySlice()),
        navigation = decodeOrDefault(BackupSliceKey.NAVIGATION, NavigationSlice.serializer(), NavigationSlice()),
        networkOffline = decodeOrDefault(BackupSliceKey.NETWORK_OFFLINE, NetworkOfflineSlice.serializer(), NetworkOfflineSlice()),
        notification = decodeOrDefault(BackupSliceKey.NOTIFICATION, NotificationSlice.serializer(), NotificationSlice()),
        syncPlayCast = decodeOrDefault(BackupSliceKey.SYNC_PLAY_CAST, SyncPlayCastSlice.serializer(), SyncPlayCastSlice()),
        screensaver = decodeOrDefault(BackupSliceKey.SCREENSAVER, ScreensaverSlice.serializer(), ScreensaverSlice()),
        security = decodeOrDefault(BackupSliceKey.SECURITY, SecuritySlice.serializer(), SecuritySlice()),
        subtitle = decodeOrDefault(BackupSliceKey.SUBTITLE, SubtitleSlice.serializer(), SubtitleSlice()),
        experimental = decodeOrDefault(BackupSliceKey.EXPERIMENTAL, ExperimentalSlice.serializer(), ExperimentalSlice()),
        runtime = backup.extras,
        pinLockout = pinLockout,
    )
}
