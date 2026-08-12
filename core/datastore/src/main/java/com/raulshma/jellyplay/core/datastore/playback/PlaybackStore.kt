package com.raulshma.jellyplay.core.datastore.playback

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **media-delivery** preference domain: which engine runs,
 * how the stream is delivered (quality / direct-play / transcoding / live), the
 * frame-rate↔refresh-rate matching invariant, decoder + audio-passthrough, and
 * the playback-lifecycle behaviour toggles (keep-screen, focus-loss, autoplay
 * countdown, background video audio, user-data sync, TV Watch Next).
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the cross-key invariants below), its read
 * projection, its legacy migration, and its reset-key list end-to-end — behind a
 * narrow interface. Mirrors the `ServerIdentityStore` / `WidgetDataStore` /
 * `PinRateLimiter` shape.
 *
 * **Cross-key invariants owned here:**
 *  - [setFrameRateMatching] / [setRefreshRateMode] keep `FRAME_RATE_MATCHING`
 *    (legacy bool) and `REFRESH_RATE_MODE` (enum) in sync in a single edit.
 *  - [readPlaybackMode] migrates the legacy `force_direct_play` boolean to the
 *    `PlaybackMode` enum when the typed key is absent.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; the key strings
 * match the legacy `UserPreferencesStore.Keys` names so existing data is read
 * in place — no migration file, no second delegate.
 */
@Singleton
class PlaybackStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val PREFERRED_PLAYER = stringPreferencesKey("preferred_player")
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val CELLULAR_STREAMING_QUALITY = stringPreferencesKey("cellular_streaming_quality")
        val FORCE_DIRECT_PLAY = booleanPreferencesKey("force_direct_play")
        val PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
        val FRAME_RATE_MATCHING = booleanPreferencesKey("frame_rate_matching")
        val REFRESH_RATE_MODE = stringPreferencesKey("refresh_rate_mode")
        val LIVE_STREAM_OPTION = stringPreferencesKey("live_stream_option")
        val KEEP_SCREEN_ON_DURING_VIDEO = booleanPreferencesKey("keep_screen_on_during_video")
        val PAUSE_ON_AUDIO_FOCUS_LOSS = booleanPreferencesKey("pause_on_audio_focus_loss")
        val DUCK_ON_TRANSIENT_FOCUS_LOSS = booleanPreferencesKey("duck_on_transient_focus_loss")
        val AUTO_PLAY_COUNTDOWN_SEC = intPreferencesKey("auto_play_countdown_sec")
        val BACKGROUND_VIDEO_AUDIO_ENABLED = booleanPreferencesKey("background_video_audio_enabled")
        val PGS_SUBTITLE_DIRECT_PLAY = booleanPreferencesKey("pgs_subtitle_direct_play")
        val USER_DATA_SYNC_ENABLED = booleanPreferencesKey("user_data_sync_enabled")
        val ANDROID_TV_WATCH_NEXT_ENABLED = booleanPreferencesKey("android_tv_watch_next_enabled")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> androidx.datastore.preferences.core.emptyPreferences() }

    /**
     * The media-delivery preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val playback: StateFlow<PlaybackSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlaybackSlice())

    /**
     * Pure read of the media-delivery fields from a raw [Preferences] snapshot.
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): PlaybackSlice = PlaybackSlice(
        preferredPlayer = readPreferredPlayer(prefs),
        streamingQuality = readStreamingQuality(prefs),
        cellularStreamingQuality = readCellularStreamingQuality(prefs),
        playbackMode = readPlaybackMode(prefs),
        liveStreamOption = readLiveStreamOption(prefs),
        decoderMode = readDecoderMode(prefs),
        audioPassthrough = PreferenceCodec.readBool(prefs, Keys.AUDIO_PASSTHROUGH, "audio_passthrough", false),
        frameRateMatching = PreferenceCodec.readBool(prefs, Keys.FRAME_RATE_MATCHING, "frame_rate_matching", false),
        refreshRateMode = readRefreshRateMode(prefs),
        keepScreenOnDuringVideo = PreferenceCodec.readBool(prefs, Keys.KEEP_SCREEN_ON_DURING_VIDEO, "keep_screen_on_during_video", true),
        pauseOnAudioFocusLoss = PreferenceCodec.readBool(prefs, Keys.PAUSE_ON_AUDIO_FOCUS_LOSS, "pause_on_audio_focus_loss", true),
        duckOnTransientFocusLoss = PreferenceCodec.readBool(prefs, Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS, "duck_on_transient_focus_loss", false),
        autoPlayCountdownSec = PreferenceCodec.readInt(prefs, Keys.AUTO_PLAY_COUNTDOWN_SEC, "auto_play_countdown_sec", 10),
        backgroundVideoAudioEnabled = PreferenceCodec.readBool(prefs, Keys.BACKGROUND_VIDEO_AUDIO_ENABLED, "background_video_audio_enabled", false),
        pgsSubtitleDirectPlay = PreferenceCodec.readBool(prefs, Keys.PGS_SUBTITLE_DIRECT_PLAY, "pgs_subtitle_direct_play", false),
        userDataSyncEnabled = PreferenceCodec.readBool(prefs, Keys.USER_DATA_SYNC_ENABLED, "user_data_sync_enabled", true),
        androidTvWatchNextEnabled = PreferenceCodec.readBool(prefs, Keys.ANDROID_TV_WATCH_NEXT_ENABLED, "android_tv_watch_next_enabled", true),
    )

    private fun readPreferredPlayer(prefs: Preferences): PlayerType = try {
        PlayerType.fromStoredName(prefs[Keys.PREFERRED_PLAYER] ?: PlayerType.EXO_PLAYER.name)
    } catch (_: Exception) {
        PlayerType.EXO_PLAYER
    }

    private fun readStreamingQuality(prefs: Preferences): StreamingQuality = try {
        StreamingQuality.valueOf(prefs[Keys.STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
    } catch (_: Exception) {
        StreamingQuality.AUTO
    }

    private fun readCellularStreamingQuality(prefs: Preferences): StreamingQuality = try {
        StreamingQuality.valueOf(prefs[Keys.CELLULAR_STREAMING_QUALITY] ?: StreamingQuality.AUTO.name)
    } catch (_: Exception) {
        StreamingQuality.AUTO
    }

    private fun readLiveStreamOption(prefs: Preferences): LiveStreamOption = try {
        LiveStreamOption.valueOf(prefs[Keys.LIVE_STREAM_OPTION] ?: LiveStreamOption.AUTO.name)
    } catch (_: Exception) {
        LiveStreamOption.AUTO
    }

    private fun readDecoderMode(prefs: Preferences): DecoderMode = try {
        DecoderMode.valueOf(prefs[Keys.DECODER_MODE] ?: DecoderMode.HW_PREFERRED.name)
    } catch (_: Exception) {
        DecoderMode.HW_PREFERRED
    }

    /**
     * Reads [PlaybackSlice.playbackMode]. Migrates the legacy boolean
     * `force_direct_play` key when the new enum key is absent: a legacy value of
     * `true` (the historical default) maps to [PlaybackMode.FORCE_DIRECT_PLAY]
     * to preserve the prior behaviour of always requesting a static stream;
     * `false` maps to [PlaybackMode.AUTO] so the server negotiates the best
     * method.
     */
    private fun readPlaybackMode(prefs: Preferences): PlaybackMode {
        prefs[Keys.PLAYBACK_MODE]?.let { raw ->
            return try { PlaybackMode.valueOf(raw) } catch (_: Exception) { PlaybackMode.AUTO }
        }
        val legacyForce = PreferenceCodec.readBool(prefs, Keys.FORCE_DIRECT_PLAY, "force_direct_play", true)
        return if (legacyForce) PlaybackMode.FORCE_DIRECT_PLAY else PlaybackMode.AUTO
    }

    private fun readRefreshRateMode(prefs: Preferences): RefreshRateMode = try {
        RefreshRateMode.valueOf(prefs[Keys.REFRESH_RATE_MODE] ?: RefreshRateMode.OFF.name)
    } catch (_: Exception) {
        // Legacy migration: a user with the old boolean on but no mode stored
        // is mapped to FRAME_RATE_ONLY (the old behaviour).
        if (PreferenceCodec.readBool(prefs, Keys.FRAME_RATE_MATCHING, "frame_rate_matching", false)) {
            RefreshRateMode.FRAME_RATE_ONLY
        } else {
            RefreshRateMode.OFF
        }
    }

    // ------------------------------------------------------------------
    // Setters — cross-key invariants live here, behind a narrow surface.
    // ------------------------------------------------------------------

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        dataStore.edit { it[Keys.PREFERRED_PLAYER] = playerType.name }
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it[Keys.STREAMING_QUALITY] = quality.name }
    }

    suspend fun setCellularStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { it[Keys.CELLULAR_STREAMING_QUALITY] = quality.name }
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        dataStore.edit { it[Keys.PLAYBACK_MODE] = mode.name }
    }

    suspend fun setLiveStreamOption(option: LiveStreamOption) {
        dataStore.edit { it[Keys.LIVE_STREAM_OPTION] = option.name }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_PASSTHROUGH] = enabled }
    }

    /**
     * Enables/disables frame-rate matching, keeping the legacy boolean in sync
     * with the new [RefreshRateMode] enum in a single atomic edit. `true` maps
     * to the least-surprising frame-rate-only mode (the old single-resolution
     * behaviour); the picker can then upgrade it to include resolution.
     */
    suspend fun setFrameRateMatching(enabled: Boolean) {
        dataStore.edit {
            it[Keys.FRAME_RATE_MATCHING] = enabled
            if (enabled && it[Keys.REFRESH_RATE_MODE] == null) {
                it[Keys.REFRESH_RATE_MODE] = RefreshRateMode.FRAME_RATE_ONLY.name
            } else if (!enabled) {
                it[Keys.REFRESH_RATE_MODE] = RefreshRateMode.OFF.name
            }
        }
    }

    suspend fun setRefreshRateMode(mode: RefreshRateMode) {
        dataStore.edit {
            it[Keys.REFRESH_RATE_MODE] = mode.name
            it[Keys.FRAME_RATE_MATCHING] = mode != RefreshRateMode.OFF
        }
    }

    suspend fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON_DURING_VIDEO] = enabled }
    }

    suspend fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        dataStore.edit { it[Keys.PAUSE_ON_AUDIO_FOCUS_LOSS] = enabled }
    }

    suspend fun setDuckOnTransientFocusLoss(enabled: Boolean) {
        dataStore.edit { it[Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS] = enabled }
    }

    suspend fun setAutoPlayCountdownSec(seconds: Int) {
        dataStore.edit { it[Keys.AUTO_PLAY_COUNTDOWN_SEC] = seconds }
    }

    suspend fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_VIDEO_AUDIO_ENABLED] = enabled }
    }

    suspend fun setPgsSubtitleDirectPlay(enabled: Boolean) {
        dataStore.edit { it[Keys.PGS_SUBTITLE_DIRECT_PLAY] = enabled }
    }

    suspend fun setUserDataSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.USER_DATA_SYNC_ENABLED] = enabled }
    }

    suspend fun setAndroidTvWatchNextEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ANDROID_TV_WATCH_NEXT_ENABLED] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Aggregated by
     * the facade's reset-coverage guard.
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.PREFERRED_PLAYER,
        Keys.STREAMING_QUALITY,
        Keys.CELLULAR_STREAMING_QUALITY,
        Keys.FORCE_DIRECT_PLAY,
        Keys.PLAYBACK_MODE,
        Keys.DECODER_MODE,
        Keys.AUDIO_PASSTHROUGH,
        Keys.FRAME_RATE_MATCHING,
        Keys.REFRESH_RATE_MODE,
        Keys.LIVE_STREAM_OPTION,
        Keys.KEEP_SCREEN_ON_DURING_VIDEO,
        Keys.PAUSE_ON_AUDIO_FOCUS_LOSS,
        Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS,
        Keys.AUTO_PLAY_COUNTDOWN_SEC,
        Keys.BACKGROUND_VIDEO_AUDIO_ENABLED,
        Keys.PGS_SUBTITLE_DIRECT_PLAY,
        Keys.USER_DATA_SYNC_ENABLED,
        Keys.ANDROID_TV_WATCH_NEXT_ENABLED,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. A single store can own keys across several categories (e.g.
     * [Keys.LIVE_STREAM_OPTION] sits in `SYNCPLAY_CASTING` while the rest are
     * `PLAYBACK`), so each store scopes its own keys per category. The facade
     * aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.PLAYBACK -> listOf(
            Keys.PREFERRED_PLAYER,
            Keys.STREAMING_QUALITY,
            Keys.CELLULAR_STREAMING_QUALITY,
            Keys.FORCE_DIRECT_PLAY,
            Keys.PLAYBACK_MODE,
            Keys.DECODER_MODE,
            Keys.AUDIO_PASSTHROUGH,
            Keys.FRAME_RATE_MATCHING,
            Keys.REFRESH_RATE_MODE,
            Keys.KEEP_SCREEN_ON_DURING_VIDEO,
            Keys.PAUSE_ON_AUDIO_FOCUS_LOSS,
            Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS,
            Keys.AUTO_PLAY_COUNTDOWN_SEC,
            Keys.BACKGROUND_VIDEO_AUDIO_ENABLED,
        )
        PreferenceResetCategory.SUBTITLES_LANGUAGE -> listOf(Keys.PGS_SUBTITLE_DIRECT_PLAY)
        PreferenceResetCategory.SYNCPLAY_CASTING -> listOf(Keys.LIVE_STREAM_OPTION)
        PreferenceResetCategory.MISC_APP -> listOf(
            Keys.USER_DATA_SYNC_ENABLED,
            Keys.ANDROID_TV_WATCH_NEXT_ENABLED,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the media-delivery keys owned by
     * this store from a decoded [UserPreferences]. The facade calls this (and
     * every other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly. The legacy
     * `force_direct_play` boolean is not written back — [readPlaybackMode]
     * migrates it from [PlaybackSlice.playbackMode],
     * and the typed key takes precedence, so re-entering the enum is enough.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.PREFERRED_PLAYER] = userPreferences.preferredPlayer.name
            it[Keys.STREAMING_QUALITY] = userPreferences.streamingQuality.name
            it[Keys.CELLULAR_STREAMING_QUALITY] = userPreferences.cellularStreamingQuality.name
            it[Keys.PLAYBACK_MODE] = userPreferences.playbackMode.name
            it[Keys.DECODER_MODE] = userPreferences.decoderMode.name
            it[Keys.AUDIO_PASSTHROUGH] = userPreferences.audioPassthrough
            it[Keys.FRAME_RATE_MATCHING] = userPreferences.frameRateMatching
            it[Keys.REFRESH_RATE_MODE] = userPreferences.refreshRateMode.name
            it[Keys.KEEP_SCREEN_ON_DURING_VIDEO] = userPreferences.keepScreenOnDuringVideo
            it[Keys.PAUSE_ON_AUDIO_FOCUS_LOSS] = userPreferences.pauseOnAudioFocusLoss
            it[Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS] = userPreferences.duckOnTransientFocusLoss
            it[Keys.AUTO_PLAY_COUNTDOWN_SEC] = userPreferences.autoPlayCountdownSec
            it[Keys.BACKGROUND_VIDEO_AUDIO_ENABLED] = userPreferences.backgroundVideoAudioEnabled
            it[Keys.PGS_SUBTITLE_DIRECT_PLAY] = userPreferences.pgsSubtitleDirectPlay
            it[Keys.USER_DATA_SYNC_ENABLED] = userPreferences.userDataSyncEnabled
            it[Keys.ANDROID_TV_WATCH_NEXT_ENABLED] = userPreferences.androidTvWatchNextEnabled
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences], plus the
     * `live_stream_option` gap key that [restorePreferences] omits.
     */
    suspend fun restore(slice: PlaybackSlice) {
        dataStore.edit { it ->
            it[Keys.PREFERRED_PLAYER] = slice.preferredPlayer.name
            it[Keys.STREAMING_QUALITY] = slice.streamingQuality.name
            it[Keys.CELLULAR_STREAMING_QUALITY] = slice.cellularStreamingQuality.name
            it[Keys.PLAYBACK_MODE] = slice.playbackMode.name
            it[Keys.LIVE_STREAM_OPTION] = slice.liveStreamOption.name
            it[Keys.DECODER_MODE] = slice.decoderMode.name
            it[Keys.AUDIO_PASSTHROUGH] = slice.audioPassthrough
            it[Keys.FRAME_RATE_MATCHING] = slice.frameRateMatching
            it[Keys.REFRESH_RATE_MODE] = slice.refreshRateMode.name
            it[Keys.KEEP_SCREEN_ON_DURING_VIDEO] = slice.keepScreenOnDuringVideo
            it[Keys.PAUSE_ON_AUDIO_FOCUS_LOSS] = slice.pauseOnAudioFocusLoss
            it[Keys.DUCK_ON_TRANSIENT_FOCUS_LOSS] = slice.duckOnTransientFocusLoss
            it[Keys.AUTO_PLAY_COUNTDOWN_SEC] = slice.autoPlayCountdownSec
            it[Keys.BACKGROUND_VIDEO_AUDIO_ENABLED] = slice.backgroundVideoAudioEnabled
            it[Keys.PGS_SUBTITLE_DIRECT_PLAY] = slice.pgsSubtitleDirectPlay
            it[Keys.USER_DATA_SYNC_ENABLED] = slice.userDataSyncEnabled
            it[Keys.ANDROID_TV_WATCH_NEXT_ENABLED] = slice.androidTvWatchNextEnabled
        }
    }
}

/**
 * The media-delivery preference slice. Plain data class (Compose-free) so the
 * datastore module stays framework-light. Defaults mirror the projection
 * defaults in [PlaybackStore.read].
 */
@Immutable
@Serializable
data class PlaybackSlice(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val keepScreenOnDuringVideo: Boolean = true,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val backgroundVideoAudioEnabled: Boolean = false,
    val pgsSubtitleDirectPlay: Boolean = false,
    val userDataSyncEnabled: Boolean = true,
    val androidTvWatchNextEnabled: Boolean = true,
)
