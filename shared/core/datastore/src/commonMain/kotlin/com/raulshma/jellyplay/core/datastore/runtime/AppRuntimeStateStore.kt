package com.raulshma.jellyplay.core.datastore.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/**
 * App-level runtime state that no preference domain owns: favorite live-TV
 * channels, the last-watched live-TV channel, the "Watch Later" playlist id,
 * the first-run onboarding flag, and the recently-seen DLNA device list.
 *
 * These fields accreted on `UserPreferencesStore` as `FacadeExtras`. They are
 * not user *preferences* (they describe what the app is doing right now, or
 * what it has already done once) so they live behind their own seam instead of
 * being shuffled into a domain slice. PIN rate-limit counters, the other
 * facade-extra, already own their home in `PinRateLimiter` and stay there.
 *
 * **Storage**: reuses the shared `"user_prefs"` DataStore file with identical
 * key strings, so there is no migration — both classes reach the same file via
 * the `@UserPreferencesDataStore`-qualified singleton.
 *
 * **Backup**: [AppRuntimeState] is `@Serializable` and is the v2 backup payload
 * for these fields. [restorePreferences] remains for decoding a legacy v0/v1
 * `UserPreferences` aggregate during import.
 */
class AppRuntimeStateStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope
    private val sharedPrefs: Flow<Preferences> = dataStore.data
    private val json get() = PreferenceCodec.json

    internal object Keys {
        val FAVORITE_CHANNELS = stringPreferencesKey("favorite_channels")
        val LIVE_TV_LAST_CHANNEL_ID = stringPreferencesKey("live_tv_last_channel_id")
        val WATCH_LATER_PLAYLIST_ID = stringPreferencesKey("watch_later_playlist_id")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val RECENT_DLNA_DEVICES = stringPreferencesKey("recent_dlna_devices")
    }

    /**
     * Combined snapshot of every runtime-state field. Consumers that react to
     * any of them (e.g. backup/export) collect this single flow.
     */
    val state: StateFlow<AppRuntimeState> = sharedPrefs
        .map { prefs ->
            AppRuntimeState(
                favoriteChannels = readFavoriteChannels(prefs),
                liveTvLastChannelId = prefs[Keys.LIVE_TV_LAST_CHANNEL_ID],
                watchLaterPlaylistId = prefs[Keys.WATCH_LATER_PLAYLIST_ID],
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                recentDlnaDevices = readRecentDlnaDevices(prefs),
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AppRuntimeState())

    /**
     * Last-watched live-TV channel id, used by the live player (shared `feature/player-live`) to reopen
     * the player on the same channel across launches. `null` when no channel
     * has been watched yet (or after [setLiveTvLastChannelId] is called with
     * `null`).
     */
    fun observeLiveTvLastChannelId(): Flow<String?> = sharedPrefs.map { prefs ->
        prefs[Keys.LIVE_TV_LAST_CHANNEL_ID]
    }.distinctUntilChanged()

    val recentDlnaDevices: Flow<List<DlnaDeviceRef>>
        get() = sharedPrefs.map { prefs -> readRecentDlnaDevices(prefs) }.distinctUntilChanged()

    suspend fun setLiveTvLastChannelId(channelId: String?) {
        dataStore.edit { prefs ->
            if (channelId == null) {
                prefs.remove(Keys.LIVE_TV_LAST_CHANNEL_ID)
            } else {
                prefs[Keys.LIVE_TV_LAST_CHANNEL_ID] = channelId
            }
        }
    }

    suspend fun setFavoriteChannels(channels: Set<String>) {
        dataStore.edit { it[Keys.FAVORITE_CHANNELS] = json.encodeToString(channels) }
    }

    suspend fun setWatchLaterPlaylistId(playlistId: String?) {
        dataStore.edit {
            if (playlistId != null) it[Keys.WATCH_LATER_PLAYLIST_ID] = playlistId
            else it.remove(Keys.WATCH_LATER_PLAYLIST_ID)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    /**
     * One-shot persisted read of the first-run flag, for decide-once boot
     * gates (the desktop shell's one-time onboarding push): unlike [state] —
     * an Eagerly-shared StateFlow seeded with the all-default
     * AppRuntimeState before the first file read lands — this reads straight
     * from the DataStore, so a gate consumer cannot mistake the seed for
     * "never onboarded" and re-fire the wizard for an already-onboarded user.
     */
    suspend fun isOnboardingCompleted(): Boolean =
        sharedPrefs.first()[Keys.ONBOARDING_COMPLETED] ?: false

    suspend fun addRecentDlnaDevice(device: DlnaDeviceRef) {
        dataStore.edit { prefs ->
            val updated = (listOf(device) + readRecentDlnaDevices(prefs).filter { it.id != device.id })
                .distinctBy { it.id }
                .take(5)
            prefs[Keys.RECENT_DLNA_DEVICES] = json.encodeToString(updated)
        }
    }

    suspend fun removeRecentDlnaDevice(deviceId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.RECENT_DLNA_DEVICES] =
                json.encodeToString(readRecentDlnaDevices(prefs).filter { it.id != deviceId })
        }
    }

    /**
     * Faithful inverse of [state]'s projection: writes every field of [slice]
     * back to the DataStore using the same encoding as the legacy facade
     * setters. Nullable ids are skipped on `null` (matching the legacy
     * `?.let` behaviour) rather than removed.
     */
    suspend fun restore(slice: AppRuntimeState) {
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITE_CHANNELS] = json.encodeToString(slice.favoriteChannels)
            slice.liveTvLastChannelId?.let { prefs[Keys.LIVE_TV_LAST_CHANNEL_ID] = it }
            slice.watchLaterPlaylistId?.let { prefs[Keys.WATCH_LATER_PLAYLIST_ID] = it }
            prefs[Keys.ONBOARDING_COMPLETED] = slice.onboardingCompleted
            prefs[Keys.RECENT_DLNA_DEVICES] = json.encodeToString(slice.recentDlnaDevices)
        }
    }

    /**
     * Legacy v0/v1 backup-import path: projects the matching fields off a
     * decoded `UserPreferences` aggregate. Retained until v2 backups
     * supersede the legacy single-aggregate format; removed once v1 import
     * is dropped.
     */
    internal suspend fun restorePreferences(userPreferences: UserPreferences) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = userPreferences.onboardingCompleted
            userPreferences.watchLaterPlaylistId?.let { prefs[Keys.WATCH_LATER_PLAYLIST_ID] = it }
            prefs[Keys.FAVORITE_CHANNELS] = json.encodeToString(userPreferences.favoriteChannels)
        }
    }

    private fun readFavoriteChannels(prefs: Preferences): Set<String> {
        val raw = prefs[Keys.FAVORITE_CHANNELS] ?: return emptySet()
        return try {
            json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun readRecentDlnaDevices(prefs: Preferences): List<DlnaDeviceRef> {
        return prefs[Keys.RECENT_DLNA_DEVICES]?.let {
            try {
                json.decodeFromString<List<DlnaDeviceRef>>(it)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }
}

/**
 * App-level runtime state that no preference domain owns. Backs the v2 backup
 * payload for these fields. Plain data class (Compose-free) so the datastore
 * module stays framework-light.
 */
@Serializable
data class AppRuntimeState(
    val favoriteChannels: Set<String> = emptySet(),
    val liveTvLastChannelId: String? = null,
    val watchLaterPlaylistId: String? = null,
    val onboardingCompleted: Boolean = false,
    val recentDlnaDevices: List<DlnaDeviceRef> = emptyList(),
)
