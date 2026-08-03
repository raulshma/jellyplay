package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **SyncPlay + casting + DVR** preference domain: the
 * SyncPlay join behaviour + sync tolerance + auto-accept invites, the default
 * casting strategy + background-casting + preferred renderer, and the DVR
 * pre/post padding + recording quality.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters, its read projection, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Note:** `live_stream_option` is intentionally NOT owned here — it belongs to
 * [com.raulshma.jellyplay.core.datastore.playback.PlaybackStore].
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class SyncPlayCastStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val SYNC_PLAY_JOIN_BEHAVIOR = stringPreferencesKey("sync_play_join_behavior")
        val SYNC_PLAY_TOLERANCE_MS = longPreferencesKey("sync_play_tolerance_ms")
        val SYNC_PLAY_AUTO_ACCEPT_INVITES = booleanPreferencesKey("sync_play_auto_accept_invites")
        val DEFAULT_CASTING_STRATEGY = stringPreferencesKey("default_casting_strategy")
        val BACKGROUND_CASTING_ENABLED = booleanPreferencesKey("background_casting_enabled")
        val PREFERRED_RENDERER = stringPreferencesKey("preferred_renderer")
        val DVR_PRE_PADDING_MINUTES = intPreferencesKey("dvr_pre_padding_minutes")
        val DVR_POST_PADDING_MINUTES = intPreferencesKey("dvr_post_padding_minutes")
        val DVR_RECORDING_QUALITY = stringPreferencesKey("dvr_recording_quality")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val syncPlayCast: StateFlow<SyncPlayCastSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SyncPlayCastSlice())

    internal fun read(prefs: Preferences): SyncPlayCastSlice = SyncPlayCastSlice(
        syncPlayJoinBehavior = readSyncPlayJoinBehavior(prefs),
        syncPlayToleranceMs = prefs[Keys.SYNC_PLAY_TOLERANCE_MS] ?: 100L,
        syncPlayAutoAcceptInvites = PreferenceCodec.readBool(prefs, Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES, "sync_play_auto_accept_invites", false),
        defaultCastingStrategy = readDefaultCastingStrategy(prefs),
        backgroundCastingEnabled = PreferenceCodec.readBool(prefs, Keys.BACKGROUND_CASTING_ENABLED, "background_casting_enabled", true),
        preferredRenderer = prefs[Keys.PREFERRED_RENDERER],
        dvrPrePaddingMinutes = PreferenceCodec.readInt(prefs, Keys.DVR_PRE_PADDING_MINUTES, "dvr_pre_padding_minutes", 0),
        dvrPostPaddingMinutes = PreferenceCodec.readInt(prefs, Keys.DVR_POST_PADDING_MINUTES, "dvr_post_padding_minutes", 0),
        dvrRecordingQuality = prefs[Keys.DVR_RECORDING_QUALITY] ?: "AUTO",
    )

    private fun readSyncPlayJoinBehavior(prefs: Preferences): SyncPlayJoinBehavior = try {
        SyncPlayJoinBehavior.valueOf(prefs[Keys.SYNC_PLAY_JOIN_BEHAVIOR] ?: SyncPlayJoinBehavior.ASK.name)
    } catch (_: Exception) { SyncPlayJoinBehavior.ASK }

    private fun readDefaultCastingStrategy(prefs: Preferences): CastingStrategy = try {
        CastingStrategy.valueOf(prefs[Keys.DEFAULT_CASTING_STRATEGY] ?: CastingStrategy.ASK.name)
    } catch (_: Exception) { CastingStrategy.ASK }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) {
        dataStore.edit { it[Keys.SYNC_PLAY_JOIN_BEHAVIOR] = behavior.name }
    }

    suspend fun setSyncPlayToleranceMs(ms: Long) {
        dataStore.edit { it[Keys.SYNC_PLAY_TOLERANCE_MS] = ms }
    }

    suspend fun setSyncPlayAutoAcceptInvites(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES] = enabled }
    }

    suspend fun setDefaultCastingStrategy(strategy: CastingStrategy) {
        dataStore.edit { it[Keys.DEFAULT_CASTING_STRATEGY] = strategy.name }
    }

    suspend fun setBackgroundCastingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_CASTING_ENABLED] = enabled }
    }

    suspend fun setPreferredRenderer(renderer: String?) {
        dataStore.edit {
            if (renderer != null) it[Keys.PREFERRED_RENDERER] = renderer else it.remove(Keys.PREFERRED_RENDERER)
        }
    }

    suspend fun setDvrPrePaddingMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DVR_PRE_PADDING_MINUTES] = minutes }
    }

    suspend fun setDvrPostPaddingMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DVR_POST_PADDING_MINUTES] = minutes }
    }

    suspend fun setDvrRecordingQuality(quality: String) {
        dataStore.edit { it[Keys.DVR_RECORDING_QUALITY] = quality }
    }

    /**
     * Keys owned by this store, for factory-reset participation. This is the
     * `SYNCPLAY_CASTING` reset category minus `LIVE_STREAM_OPTION` (owned by
     * `PlaybackStore`).
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.SYNC_PLAY_JOIN_BEHAVIOR, Keys.SYNC_PLAY_TOLERANCE_MS,
        Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES, Keys.DEFAULT_CASTING_STRATEGY,
        Keys.BACKGROUND_CASTING_ENABLED, Keys.PREFERRED_RENDERER,
        Keys.DVR_PRE_PADDING_MINUTES, Keys.DVR_POST_PADDING_MINUTES,
        Keys.DVR_RECORDING_QUALITY,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. Every key owned here descends under
     * `PreferenceResetCategory.SYNCPLAY_CASTING`. `LIVE_STREAM_OPTION` is owned
     * by `PlaybackStore`, and the runtime recall slots (favorite channels,
     * recent DLNA devices, live-TV last channel) are excluded from category
     * reset by the facade, so none appear in any category list here.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.SYNCPLAY_CASTING -> listOf(
            Keys.SYNC_PLAY_JOIN_BEHAVIOR, Keys.SYNC_PLAY_TOLERANCE_MS,
            Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES, Keys.DEFAULT_CASTING_STRATEGY,
            Keys.BACKGROUND_CASTING_ENABLED, Keys.PREFERRED_RENDERER,
            Keys.DVR_PRE_PADDING_MINUTES, Keys.DVR_POST_PADDING_MINUTES,
            Keys.DVR_RECORDING_QUALITY,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the SyncPlay + casting + DVR keys
     * owned by this store from a decoded [UserPreferences], mirroring the
     * facade's restore body exactly (including the nullable
     * [Keys.PREFERRED_RENDERER] guard).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_PLAY_JOIN_BEHAVIOR] = userPreferences.syncPlayJoinBehavior.name
            prefs[Keys.SYNC_PLAY_TOLERANCE_MS] = userPreferences.syncPlayToleranceMs
            prefs[Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES] = userPreferences.syncPlayAutoAcceptInvites
            prefs[Keys.DEFAULT_CASTING_STRATEGY] = userPreferences.defaultCastingStrategy.name
            prefs[Keys.BACKGROUND_CASTING_ENABLED] = userPreferences.backgroundCastingEnabled
            userPreferences.preferredRenderer?.let { prefs[Keys.PREFERRED_RENDERER] = it }
            prefs[Keys.DVR_PRE_PADDING_MINUTES] = userPreferences.dvrPrePaddingMinutes
            prefs[Keys.DVR_POST_PADDING_MINUTES] = userPreferences.dvrPostPaddingMinutes
            prefs[Keys.DVR_RECORDING_QUALITY] = userPreferences.dvrRecordingQuality
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (including the
     * nullable [Keys.PREFERRED_RENDERER] guard).
     */
    suspend fun restore(slice: SyncPlayCastSlice) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_PLAY_JOIN_BEHAVIOR] = slice.syncPlayJoinBehavior.name
            prefs[Keys.SYNC_PLAY_TOLERANCE_MS] = slice.syncPlayToleranceMs
            prefs[Keys.SYNC_PLAY_AUTO_ACCEPT_INVITES] = slice.syncPlayAutoAcceptInvites
            prefs[Keys.DEFAULT_CASTING_STRATEGY] = slice.defaultCastingStrategy.name
            prefs[Keys.BACKGROUND_CASTING_ENABLED] = slice.backgroundCastingEnabled
            slice.preferredRenderer?.let { prefs[Keys.PREFERRED_RENDERER] = it }
            prefs[Keys.DVR_PRE_PADDING_MINUTES] = slice.dvrPrePaddingMinutes
            prefs[Keys.DVR_POST_PADDING_MINUTES] = slice.dvrPostPaddingMinutes
            prefs[Keys.DVR_RECORDING_QUALITY] = slice.dvrRecordingQuality
        }
    }
}

/**
 * The SyncPlay + casting + DVR preference slice. Plain data class. Defaults
 * mirror the projection defaults in [SyncPlayCastStore.read].
 */
@Immutable
@Serializable
data class SyncPlayCastSlice(
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
    val defaultCastingStrategy: CastingStrategy = CastingStrategy.ASK,
    val backgroundCastingEnabled: Boolean = true,
    val preferredRenderer: String? = null,
    val dvrPrePaddingMinutes: Int = 0,
    val dvrPostPaddingMinutes: Int = 0,
    val dvrRecordingQuality: String = "AUTO",
)
