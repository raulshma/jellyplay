package com.raulshma.jellyplay.core.datastore.playback

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.platformEngineSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

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
 * **Stage B spec derivation:** every key this store owns is declared exactly
 * once as a row in [PlaybackPreferenceSpecs] (wire name, default, reset
 * category, read/write encoding) and the machinery below is derived from those
 * rows — each [Keys] member rebuilds its row's typed key from the row's wire
 * name, each [read] projection row delegates to its row encoding, the
 * single-key setters and [restore] delegate to the rows' derived writes, and
 * [resetKeysFor] filters the rows by reset category. The migration semantics
 * (legacy string-key fallback, `force_direct_play` → [PlaybackMode], the
 * passthrough-codec legacy default, the corrupt-`refresh_rate_mode` rescue)
 * live in the rows' encodings now, next to the key they apply to.
 *
 * **Cross-key invariants and legacy pairings owned here (hand-written by
 * decision — an invariant spanning two keys cannot be a row encoding):**
 *  - [setFrameRateMatching] / [setRefreshRateMode] keep `FRAME_RATE_MATCHING`
 *    (legacy bool) and `REFRESH_RATE_MODE` (enum) in sync in a single edit.
 *  - the PLAYBACK_MODE spec row's read migrates the legacy `force_direct_play`
 *    boolean to the [PlaybackMode] enum when the typed key is absent.
 *  - the AUDIO_PASSTHROUGH_CODECS spec row's read migrates the legacy
 *    single-boolean passthrough surface: an absent `audio_passthrough_codecs`
 *    key reads the full historical codec list (the old boolean's meaning).
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; the key strings
 * match the legacy `UserPreferencesStore.Keys` names so existing data is read
 * in place — no migration file, no second delegate.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [PlaybackPreferenceSpecs] row + one [PlaybackSlice] property + one [read] /
 * [restore] row (and its setter) + one write-through test line. See the spec
 * KDoc.
 */
class PlaybackStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [PlaybackPreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for the JVM reset-coverage guard and the
     * key-identity reference for the hand-written cross-key invariant setters
     * below.
     */
    internal object Keys {
        val PREFERRED_PLAYER = stringPreferencesKey(PlaybackPreferenceSpecs.PREFERRED_PLAYER.keyName)
        val PREFERRED_EXTERNAL_PLAYER = stringPreferencesKey(PlaybackPreferenceSpecs.PREFERRED_EXTERNAL_PLAYER.keyName)
        val STREAMING_QUALITY = stringPreferencesKey(PlaybackPreferenceSpecs.STREAMING_QUALITY.keyName)
        val CELLULAR_STREAMING_QUALITY = stringPreferencesKey(PlaybackPreferenceSpecs.CELLULAR_STREAMING_QUALITY.keyName)
        val FORCE_DIRECT_PLAY = booleanPreferencesKey(PlaybackPreferenceSpecs.FORCE_DIRECT_PLAY.keyName)
        val PLAYBACK_MODE = stringPreferencesKey(PlaybackPreferenceSpecs.PLAYBACK_MODE.keyName)
        val DECODER_MODE = stringPreferencesKey(PlaybackPreferenceSpecs.DECODER_MODE.keyName)
        val AUDIO_PASSTHROUGH = booleanPreferencesKey(PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH.keyName)
        val AUDIO_PASSTHROUGH_CODECS = stringPreferencesKey(PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS.keyName)
        val MAX_AUDIO_CHANNELS = stringPreferencesKey(PlaybackPreferenceSpecs.MAX_AUDIO_CHANNELS.keyName)
        val DOWNMIX_BOOST_DB = floatPreferencesKey(PlaybackPreferenceSpecs.DOWNMIX_BOOST_DB.keyName)
        val FRAME_RATE_MATCHING = booleanPreferencesKey(PlaybackPreferenceSpecs.FRAME_RATE_MATCHING.keyName)
        val REFRESH_RATE_MODE = stringPreferencesKey(PlaybackPreferenceSpecs.REFRESH_RATE_MODE.keyName)
        val LIVE_STREAM_OPTION = stringPreferencesKey(PlaybackPreferenceSpecs.LIVE_STREAM_OPTION.keyName)
        val OFFLINE_PLAYBACK_PREFERENCE = stringPreferencesKey(PlaybackPreferenceSpecs.OFFLINE_PLAYBACK_PREFERENCE.keyName)
        val KEEP_SCREEN_ON_DURING_VIDEO = booleanPreferencesKey(PlaybackPreferenceSpecs.KEEP_SCREEN_ON_DURING_VIDEO.keyName)
        val PAUSE_ON_AUDIO_FOCUS_LOSS = booleanPreferencesKey(PlaybackPreferenceSpecs.PAUSE_ON_AUDIO_FOCUS_LOSS.keyName)
        val DUCK_ON_TRANSIENT_FOCUS_LOSS = booleanPreferencesKey(PlaybackPreferenceSpecs.DUCK_ON_TRANSIENT_FOCUS_LOSS.keyName)
        val AUTO_PLAY_COUNTDOWN_SEC = intPreferencesKey(PlaybackPreferenceSpecs.AUTO_PLAY_COUNTDOWN_SEC.keyName)
        val BACKGROUND_VIDEO_AUDIO_ENABLED = booleanPreferencesKey(PlaybackPreferenceSpecs.BACKGROUND_VIDEO_AUDIO_ENABLED.keyName)
        val AUTO_ENTER_PIP = booleanPreferencesKey(PlaybackPreferenceSpecs.AUTO_ENTER_PIP.keyName)
        val PGS_SUBTITLE_DIRECT_PLAY = booleanPreferencesKey(PlaybackPreferenceSpecs.PGS_SUBTITLE_DIRECT_PLAY.keyName)
        val USER_DATA_SYNC_ENABLED = booleanPreferencesKey(PlaybackPreferenceSpecs.USER_DATA_SYNC_ENABLED.keyName)
        val ANDROID_TV_WATCH_NEXT_ENABLED = booleanPreferencesKey(PlaybackPreferenceSpecs.ANDROID_TV_WATCH_NEXT_ENABLED.keyName)
    }

    /**
     * The media-delivery preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val playback: StateFlow<PlaybackSlice> =
        dataStore.sliceStateFlow(scope, seed = PlaybackSlice(), read = ::read)

    /**
     * Pure read of the media-delivery fields from a raw [Preferences] snapshot,
     * each field delegated to its [PlaybackPreferenceSpecs] row encoding.
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): PlaybackSlice = PlaybackSlice(
        preferredPlayer = PlaybackPreferenceSpecs.PREFERRED_PLAYER.readFrom(prefs),
        preferredExternalPlayer = PlaybackPreferenceSpecs.PREFERRED_EXTERNAL_PLAYER.readFrom(prefs),
        streamingQuality = PlaybackPreferenceSpecs.STREAMING_QUALITY.readFrom(prefs),
        cellularStreamingQuality = PlaybackPreferenceSpecs.CELLULAR_STREAMING_QUALITY.readFrom(prefs),
        playbackMode = PlaybackPreferenceSpecs.PLAYBACK_MODE.readFrom(prefs),
        liveStreamOption = PlaybackPreferenceSpecs.LIVE_STREAM_OPTION.readFrom(prefs),
        offlinePlaybackPreference = PlaybackPreferenceSpecs.OFFLINE_PLAYBACK_PREFERENCE.readFrom(prefs),
        decoderMode = PlaybackPreferenceSpecs.DECODER_MODE.readFrom(prefs),
        audioPassthrough = PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH.readFrom(prefs),
        audioPassthroughCodecs = PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS.readFrom(prefs),
        maxAudioChannels = PlaybackPreferenceSpecs.MAX_AUDIO_CHANNELS.readFrom(prefs),
        downmixBoostDb = PlaybackPreferenceSpecs.DOWNMIX_BOOST_DB.readFrom(prefs),
        frameRateMatching = PlaybackPreferenceSpecs.FRAME_RATE_MATCHING.readFrom(prefs),
        refreshRateMode = PlaybackPreferenceSpecs.REFRESH_RATE_MODE.readFrom(prefs),
        keepScreenOnDuringVideo = PlaybackPreferenceSpecs.KEEP_SCREEN_ON_DURING_VIDEO.readFrom(prefs),
        pauseOnAudioFocusLoss = PlaybackPreferenceSpecs.PAUSE_ON_AUDIO_FOCUS_LOSS.readFrom(prefs),
        duckOnTransientFocusLoss = PlaybackPreferenceSpecs.DUCK_ON_TRANSIENT_FOCUS_LOSS.readFrom(prefs),
        autoPlayCountdownSec = PlaybackPreferenceSpecs.AUTO_PLAY_COUNTDOWN_SEC.readFrom(prefs),
        backgroundVideoAudioEnabled = PlaybackPreferenceSpecs.BACKGROUND_VIDEO_AUDIO_ENABLED.readFrom(prefs),
        autoEnterPip = PlaybackPreferenceSpecs.AUTO_ENTER_PIP.readFrom(prefs),
        pgsSubtitleDirectPlay = PlaybackPreferenceSpecs.PGS_SUBTITLE_DIRECT_PLAY.readFrom(prefs),
        userDataSyncEnabled = PlaybackPreferenceSpecs.USER_DATA_SYNC_ENABLED.readFrom(prefs),
        androidTvWatchNextEnabled = PlaybackPreferenceSpecs.ANDROID_TV_WATCH_NEXT_ENABLED.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, JSON — is the row's, not re-declared
    // here); cross-key invariants live below, hand-written.
    // ------------------------------------------------------------------

    suspend fun setPreferredPlayer(playerType: PlayerType) {
        dataStore.edit { PlaybackPreferenceSpecs.PREFERRED_PLAYER.writeTo(it, playerType) }
    }

    suspend fun setPreferredExternalPlayer(app: ExternalPlayerApp) {
        dataStore.edit { PlaybackPreferenceSpecs.PREFERRED_EXTERNAL_PLAYER.writeTo(it, app) }
    }

    suspend fun setStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { PlaybackPreferenceSpecs.STREAMING_QUALITY.writeTo(it, quality) }
    }

    suspend fun setCellularStreamingQuality(quality: StreamingQuality) {
        dataStore.edit { PlaybackPreferenceSpecs.CELLULAR_STREAMING_QUALITY.writeTo(it, quality) }
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        dataStore.edit { PlaybackPreferenceSpecs.PLAYBACK_MODE.writeTo(it, mode) }
    }

    suspend fun setLiveStreamOption(option: LiveStreamOption) {
        dataStore.edit { PlaybackPreferenceSpecs.LIVE_STREAM_OPTION.writeTo(it, option) }
    }

    suspend fun setOfflinePlaybackPreference(preference: OfflinePlaybackPreference) {
        dataStore.edit { PlaybackPreferenceSpecs.OFFLINE_PLAYBACK_PREFERENCE.writeTo(it, preference) }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        dataStore.edit { PlaybackPreferenceSpecs.DECODER_MODE.writeTo(it, mode) }
    }

    suspend fun setAudioPassthrough(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH.writeTo(it, enabled) }
    }

    /**
     * The per-codec passthrough allow-list, stored as a JSON set of enum
     * names. Writing it (even empty) marks the surface as touched — the
     * legacy-boolean migration in the AUDIO_PASSTHROUGH_CODECS row's read
     * stops applying.
     */
    suspend fun setAudioPassthroughCodecs(codecs: Set<AudioPassthroughCodec>) {
        dataStore.edit { PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS.writeTo(it, codecs) }
    }

    suspend fun setMaxAudioChannels(mode: MaxAudioChannelsEnum) {
        dataStore.edit { PlaybackPreferenceSpecs.MAX_AUDIO_CHANNELS.writeTo(it, mode) }
    }

    /** The stereo-downmix loudness compensation in dB; clamped to 0–12. */
    suspend fun setDownmixBoostDb(db: Float) {
        dataStore.edit {
            PlaybackPreferenceSpecs.DOWNMIX_BOOST_DB.writeTo(it, db.coerceIn(MIN_DOWNMIX_BOOST_DB, MAX_DOWNMIX_BOOST_DB))
        }
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
        dataStore.edit { PlaybackPreferenceSpecs.KEEP_SCREEN_ON_DURING_VIDEO.writeTo(it, enabled) }
    }

    suspend fun setPauseOnAudioFocusLoss(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.PAUSE_ON_AUDIO_FOCUS_LOSS.writeTo(it, enabled) }
    }

    suspend fun setDuckOnTransientFocusLoss(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.DUCK_ON_TRANSIENT_FOCUS_LOSS.writeTo(it, enabled) }
    }

    suspend fun setAutoPlayCountdownSec(seconds: Int) {
        dataStore.edit { PlaybackPreferenceSpecs.AUTO_PLAY_COUNTDOWN_SEC.writeTo(it, seconds) }
    }

    suspend fun setBackgroundVideoAudioEnabled(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.BACKGROUND_VIDEO_AUDIO_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Whether leaving the player during playback auto-enters
     * picture-in-picture. Default `true` preserves the historical behaviour;
     * turning it off makes Home/recents background the app normally instead
     * (issue #167). The manual PiP button in the controls is unaffected.
     */
    suspend fun setAutoEnterPip(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.AUTO_ENTER_PIP.writeTo(it, enabled) }
    }

    suspend fun setPgsSubtitleDirectPlay(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.PGS_SUBTITLE_DIRECT_PLAY.writeTo(it, enabled) }
    }

    suspend fun setUserDataSyncEnabled(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.USER_DATA_SYNC_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setAndroidTvWatchNextEnabled(enabled: Boolean) {
        dataStore.edit { PlaybackPreferenceSpecs.ANDROID_TV_WATCH_NEXT_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [PlaybackPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. A single store can own
     * keys across several categories (e.g. `LIVE_STREAM_OPTION` sits in
     * `SYNCPLAY_CASTING` while most others are `PLAYBACK`), so each store
     * scopes its own keys per category. The facade aggregates these lists
     * instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        PlaybackPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with, plus the `live_stream_option` key the legacy facade-level
     * `restorePreferences` omitted).
     */
    suspend fun restore(slice: PlaybackSlice) {
        dataStore.edit { prefs ->
            PlaybackPreferenceSpecs.PREFERRED_PLAYER.writeTo(prefs, slice.preferredPlayer)
            PlaybackPreferenceSpecs.PREFERRED_EXTERNAL_PLAYER.writeTo(prefs, slice.preferredExternalPlayer)
            PlaybackPreferenceSpecs.STREAMING_QUALITY.writeTo(prefs, slice.streamingQuality)
            PlaybackPreferenceSpecs.CELLULAR_STREAMING_QUALITY.writeTo(prefs, slice.cellularStreamingQuality)
            PlaybackPreferenceSpecs.PLAYBACK_MODE.writeTo(prefs, slice.playbackMode)
            PlaybackPreferenceSpecs.LIVE_STREAM_OPTION.writeTo(prefs, slice.liveStreamOption)
            PlaybackPreferenceSpecs.OFFLINE_PLAYBACK_PREFERENCE.writeTo(prefs, slice.offlinePlaybackPreference)
            PlaybackPreferenceSpecs.DECODER_MODE.writeTo(prefs, slice.decoderMode)
            PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH.writeTo(prefs, slice.audioPassthrough)
            PlaybackPreferenceSpecs.AUDIO_PASSTHROUGH_CODECS.writeTo(prefs, slice.audioPassthroughCodecs)
            PlaybackPreferenceSpecs.MAX_AUDIO_CHANNELS.writeTo(prefs, slice.maxAudioChannels)
            PlaybackPreferenceSpecs.DOWNMIX_BOOST_DB.writeTo(prefs, slice.downmixBoostDb)
            PlaybackPreferenceSpecs.FRAME_RATE_MATCHING.writeTo(prefs, slice.frameRateMatching)
            PlaybackPreferenceSpecs.REFRESH_RATE_MODE.writeTo(prefs, slice.refreshRateMode)
            PlaybackPreferenceSpecs.KEEP_SCREEN_ON_DURING_VIDEO.writeTo(prefs, slice.keepScreenOnDuringVideo)
            PlaybackPreferenceSpecs.PAUSE_ON_AUDIO_FOCUS_LOSS.writeTo(prefs, slice.pauseOnAudioFocusLoss)
            PlaybackPreferenceSpecs.DUCK_ON_TRANSIENT_FOCUS_LOSS.writeTo(prefs, slice.duckOnTransientFocusLoss)
            PlaybackPreferenceSpecs.AUTO_PLAY_COUNTDOWN_SEC.writeTo(prefs, slice.autoPlayCountdownSec)
            PlaybackPreferenceSpecs.BACKGROUND_VIDEO_AUDIO_ENABLED.writeTo(prefs, slice.backgroundVideoAudioEnabled)
            PlaybackPreferenceSpecs.AUTO_ENTER_PIP.writeTo(prefs, slice.autoEnterPip)
            PlaybackPreferenceSpecs.PGS_SUBTITLE_DIRECT_PLAY.writeTo(prefs, slice.pgsSubtitleDirectPlay)
            PlaybackPreferenceSpecs.USER_DATA_SYNC_ENABLED.writeTo(prefs, slice.userDataSyncEnabled)
            PlaybackPreferenceSpecs.ANDROID_TV_WATCH_NEXT_ENABLED.writeTo(prefs, slice.androidTvWatchNextEnabled)
        }
    }
}

/**
 * The media-delivery preference slice. Plain data class (Compose-free) so the
 * datastore module stays framework-light. Defaults mirror the projection
 * defaults in [PlaybackStore.read] (declared on the [PlaybackPreferenceSpecs]
 * rows).
 */
@Immutable
@Serializable
data class PlaybackSlice(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val preferredExternalPlayer: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val cellularStreamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    val offlinePlaybackPreference: OfflinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val audioPassthroughCodecs: Set<AudioPassthroughCodec> = DEFAULT_AUDIO_PASSTHROUGH_CODECS,
    val maxAudioChannels: MaxAudioChannelsEnum = MaxAudioChannelsEnum.AUTO,
    val downmixBoostDb: Float = DEFAULT_DOWNMIX_BOOST_DB,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val keepScreenOnDuringVideo: Boolean = true,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoEnterPip: Boolean = true,
    val pgsSubtitleDirectPlay: Boolean = false,
    val userDataSyncEnabled: Boolean = true,
    val androidTvWatchNextEnabled: Boolean = true,
)

/**
 * Parse + clamp a stored `preferred_player` value against the engines the
 * running binary actually ships ([platformEngineSupport]). The single choke
 * point every preferred-engine read flows through — [PlaybackStore.read] (via
 * the PREFERRED_PLAYER spec row), and therefore every projection fold,
 * `PlayerSessionManager` engine selection, and backup import — so a choice
 * restored from another platform's backup (ExoPlayer onto desktop, mpv onto a
 * build without it) degrades to the platform default instead of reaching an
 * unregistered factory. Non-destructive by design: the raw value stays on
 * disk, so the same backup keeps both platforms valid.
 */
internal fun normalizePreferredPlayer(raw: String?): PlayerType {
    val parsed = try {
        PlayerType.fromStoredName(raw ?: PlayerType.EXO_PLAYER.name)
    } catch (_: Exception) {
        PlayerType.EXO_PLAYER
    }
    return if (platformEngineSupport.isAvailable(parsed)) parsed else platformEngineSupport.default
}
