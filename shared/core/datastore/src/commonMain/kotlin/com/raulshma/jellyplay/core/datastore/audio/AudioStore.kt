package com.raulshma.jellyplay.core.datastore.audio

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **audio-player** preference domain: playback speed,
 * night-mode volume/gain, skip-previous threshold, autoplay, preload buffer,
 * replaygain/normalization (mode + enabled, with the legacy `REPLAYGAIN`
 * → `TRACK` alias), channel-mix (mode + enabled), gapless, crossfade, audio
 * delay, lyrics visibility, visualizer, and the sleep timer.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection, and reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class AudioStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val AUDIO_DEFAULT_SPEED = floatPreferencesKey("audio_default_speed")
        val AUDIO_NIGHT_MODE_VOLUME = floatPreferencesKey("audio_night_mode_volume")
        val AUDIO_NIGHT_MODE_GAIN = intPreferencesKey("audio_night_mode_gain")
        val AUDIO_SKIP_PREVIOUS_THRESHOLD_MS = longPreferencesKey("audio_skip_previous_threshold_ms")
        val AUDIO_AUTOPLAY_NEXT = booleanPreferencesKey("audio_autoplay_next")
        val AUDIO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("audio_preload_buffer_size")
        val AUDIO_NORMALIZATION_MODE = stringPreferencesKey("audio_normalization_mode")
        val AUDIO_NORMALIZATION_ENABLED = booleanPreferencesKey("audio_normalization_enabled")
        val REPLAYGAIN_PRE_AMP_DB = floatPreferencesKey("replaygain_pre_amp_db")
        val CHANNEL_MIX_MODE = stringPreferencesKey("channel_mix_mode")
        val CHANNEL_MIX_ENABLED = booleanPreferencesKey("channel_mix_enabled")
        val AUDIO_GAPLESS_ENABLED = booleanPreferencesKey("audio_gapless_enabled")
        val AUDIO_CROSSFADE_DURATION_MS = longPreferencesKey("audio_crossfade_duration_ms")
        val AUDIO_DELAY_MS = longPreferencesKey("audio_delay_ms")
        val AUDIO_LYRICS_VISIBLE = booleanPreferencesKey("audio_lyrics_visible")
        val AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val SLEEP_TIMER_DURATION_MS = longPreferencesKey("sleep_timer_duration_ms")
        val SLEEP_TIMER_END_OF_EPISODE = booleanPreferencesKey("sleep_timer_end_of_episode")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val audio: StateFlow<AudioSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AudioSlice())

    internal fun read(prefs: Preferences): AudioSlice = AudioSlice(
        audioDefaultSpeed = PreferenceCodec.readFloat(prefs, Keys.AUDIO_DEFAULT_SPEED, "audio_default_speed", 1.0f),
        audioNightModeVolume = PreferenceCodec.readFloat(prefs, Keys.AUDIO_NIGHT_MODE_VOLUME, "audio_night_mode_volume", 0.4f),
        audioNightModeGain = PreferenceCodec.readInt(prefs, Keys.AUDIO_NIGHT_MODE_GAIN, "audio_night_mode_gain", 1200),
        audioSkipPreviousThresholdMs = PreferenceCodec.readLong(prefs, Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, "audio_skip_previous_threshold_ms", 3_000L),
        audioAutoplayNext = PreferenceCodec.readBool(prefs, Keys.AUDIO_AUTOPLAY_NEXT, "audio_autoplay_next", true),
        audioPreloadBufferSize = try {
            PreloadBufferSize.valueOf(prefs[Keys.AUDIO_PRELOAD_BUFFER_SIZE] ?: PreloadBufferSize.MEDIUM.name)
        } catch (_: Exception) {
            PreloadBufferSize.MEDIUM
        },
        audioNormalizationMode = readNormalizationMode(prefs),
        audioNormalizationEnabled = PreferenceCodec.readBool(prefs, Keys.AUDIO_NORMALIZATION_ENABLED, "audio_normalization_enabled", false),
        replayGainPreAmpDb = PreferenceCodec.readFloat(prefs, Keys.REPLAYGAIN_PRE_AMP_DB, "replaygain_pre_amp_db", 0f),
        channelMixMode = try {
            ChannelMixMode.valueOf(prefs[Keys.CHANNEL_MIX_MODE] ?: ChannelMixMode.AUTO.name)
        } catch (_: Exception) {
            ChannelMixMode.AUTO
        },
        channelMixEnabled = PreferenceCodec.readBool(prefs, Keys.CHANNEL_MIX_ENABLED, "channel_mix_enabled", false),
        audioGaplessEnabled = PreferenceCodec.readBool(prefs, Keys.AUDIO_GAPLESS_ENABLED, "audio_gapless_enabled", true),
        audioCrossfadeDurationMs = PreferenceCodec.readLong(prefs, Keys.AUDIO_CROSSFADE_DURATION_MS, "audio_crossfade_duration_ms", 0L),
        audioDelayMs = PreferenceCodec.readLong(prefs, Keys.AUDIO_DELAY_MS, "audio_delay_ms", 0L),
        audioLyricsVisible = PreferenceCodec.readBool(prefs, Keys.AUDIO_LYRICS_VISIBLE, "audio_lyrics_visible", false),
        audioVisualizerEnabled = PreferenceCodec.readBool(prefs, Keys.AUDIO_VISUALIZER_ENABLED, "audio_visualizer_enabled", false),
        sleepTimerDurationMs = PreferenceCodec.readLong(prefs, Keys.SLEEP_TIMER_DURATION_MS, "sleep_timer_duration_ms", 0L),
        sleepTimerEndOfEpisode = PreferenceCodec.readBool(prefs, Keys.SLEEP_TIMER_END_OF_EPISODE, "sleep_timer_end_of_episode", false),
    )

    /**
     * Reads [AudioSlice.audioNormalizationMode]. Maps the legacy `"REPLAYGAIN"`
     * stored value to [AudioNormalizationMode.TRACK] (the closest modern
     * equivalent) before falling back to the enum valueOf parse.
     */
    private fun readNormalizationMode(prefs: Preferences): AudioNormalizationMode = try {
        when (val stored = prefs[Keys.AUDIO_NORMALIZATION_MODE] ?: AudioNormalizationMode.NONE.name) {
            "REPLAYGAIN" -> AudioNormalizationMode.TRACK
            else -> AudioNormalizationMode.valueOf(stored)
        }
    } catch (_: Exception) {
        AudioNormalizationMode.NONE
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setAudioDefaultSpeed(speed: Float) {
        dataStore.edit { it[Keys.AUDIO_DEFAULT_SPEED] = speed }
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_VOLUME] = volume }
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        dataStore.edit { it[Keys.AUDIO_NIGHT_MODE_GAIN] = gain }
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = ms }
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        dataStore.edit { it[Keys.AUDIO_NORMALIZATION_MODE] = mode.name }
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_NORMALIZATION_ENABLED] = enabled }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        dataStore.edit { it[Keys.REPLAYGAIN_PRE_AMP_DB] = db }
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        dataStore.edit { it[Keys.CHANNEL_MIX_MODE] = mode.name }
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CHANNEL_MIX_ENABLED] = enabled }
    }

    suspend fun setAudioGaplessEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_GAPLESS_ENABLED] = enabled }
    }

    suspend fun setAudioCrossfadeDurationMs(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_CROSSFADE_DURATION_MS] = ms }
    }

    suspend fun setAudioDelay(ms: Long) {
        dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    suspend fun setAudioLyricsVisible(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_LYRICS_VISIBLE] = enabled }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        dataStore.edit { it[Keys.SLEEP_TIMER_DURATION_MS] = ms }
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        dataStore.edit { it[Keys.SLEEP_TIMER_END_OF_EPISODE] = enabled }
    }

    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.AUDIO_DEFAULT_SPEED, Keys.AUDIO_NIGHT_MODE_VOLUME, Keys.AUDIO_NIGHT_MODE_GAIN,
        Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, Keys.AUDIO_AUTOPLAY_NEXT, Keys.AUDIO_PRELOAD_BUFFER_SIZE,
        Keys.AUDIO_NORMALIZATION_MODE, Keys.AUDIO_NORMALIZATION_ENABLED, Keys.REPLAYGAIN_PRE_AMP_DB,
        Keys.CHANNEL_MIX_MODE, Keys.CHANNEL_MIX_ENABLED, Keys.AUDIO_GAPLESS_ENABLED,
        Keys.AUDIO_CROSSFADE_DURATION_MS, Keys.AUDIO_DELAY_MS, Keys.AUDIO_LYRICS_VISIBLE,
        Keys.AUDIO_VISUALIZER_ENABLED, Keys.SLEEP_TIMER_DURATION_MS, Keys.SLEEP_TIMER_END_OF_EPISODE,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. A single store can own keys across several categories
     * ([Keys.AUDIO_DELAY_MS] sits in `PLAYBACK` in the legacy mapping while the
     * rest are `AUDIO`), so each store scopes its own keys per category. The
     * facade aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.PLAYBACK -> listOf(Keys.AUDIO_DELAY_MS)
        PreferenceResetCategory.AUDIO -> listOf(
            Keys.AUDIO_DEFAULT_SPEED, Keys.AUDIO_NIGHT_MODE_VOLUME, Keys.AUDIO_NIGHT_MODE_GAIN,
            Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS, Keys.AUDIO_AUTOPLAY_NEXT, Keys.AUDIO_PRELOAD_BUFFER_SIZE,
            Keys.AUDIO_NORMALIZATION_MODE, Keys.AUDIO_NORMALIZATION_ENABLED, Keys.REPLAYGAIN_PRE_AMP_DB,
            Keys.CHANNEL_MIX_MODE, Keys.CHANNEL_MIX_ENABLED, Keys.AUDIO_GAPLESS_ENABLED,
            Keys.AUDIO_CROSSFADE_DURATION_MS, Keys.AUDIO_LYRICS_VISIBLE,
            Keys.AUDIO_VISUALIZER_ENABLED, Keys.SLEEP_TIMER_DURATION_MS, Keys.SLEEP_TIMER_END_OF_EPISODE,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the audio-player keys owned by this
     * store from a decoded [UserPreferences]. The facade calls this (and every
     * other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly. [Keys.AUDIO_LYRICS_VISIBLE]
     * is runtime reading-state that the projection reads from its stored slot,
     * so it is not written back.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.AUDIO_DEFAULT_SPEED] = userPreferences.audioDefaultSpeed
            it[Keys.AUDIO_NIGHT_MODE_VOLUME] = userPreferences.audioNightModeVolume
            it[Keys.AUDIO_NIGHT_MODE_GAIN] = userPreferences.audioNightModeGain
            it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = userPreferences.audioSkipPreviousThresholdMs
            it[Keys.AUDIO_AUTOPLAY_NEXT] = userPreferences.audioAutoplayNext
            it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = userPreferences.audioPreloadBufferSize.name
            it[Keys.AUDIO_NORMALIZATION_MODE] = userPreferences.audioNormalizationMode.name
            it[Keys.AUDIO_NORMALIZATION_ENABLED] = userPreferences.audioNormalizationEnabled
            it[Keys.REPLAYGAIN_PRE_AMP_DB] = userPreferences.replayGainPreAmpDb
            it[Keys.CHANNEL_MIX_MODE] = userPreferences.channelMixMode.name
            it[Keys.CHANNEL_MIX_ENABLED] = userPreferences.channelMixEnabled
            it[Keys.AUDIO_GAPLESS_ENABLED] = userPreferences.audioGaplessEnabled
            it[Keys.AUDIO_CROSSFADE_DURATION_MS] = userPreferences.audioCrossfadeDurationMs
            it[Keys.AUDIO_DELAY_MS] = userPreferences.audioDelayMs
            it[Keys.AUDIO_VISUALIZER_ENABLED] = userPreferences.audioVisualizerEnabled
            it[Keys.SLEEP_TIMER_DURATION_MS] = userPreferences.sleepTimerDurationMs
            it[Keys.SLEEP_TIMER_END_OF_EPISODE] = userPreferences.sleepTimerEndOfEpisode
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences], plus the
     * `audio_lyrics_visible` gap key that [restorePreferences] omits.
     */
    suspend fun restore(slice: AudioSlice) {
        dataStore.edit { it ->
            it[Keys.AUDIO_DEFAULT_SPEED] = slice.audioDefaultSpeed
            it[Keys.AUDIO_NIGHT_MODE_VOLUME] = slice.audioNightModeVolume
            it[Keys.AUDIO_NIGHT_MODE_GAIN] = slice.audioNightModeGain
            it[Keys.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS] = slice.audioSkipPreviousThresholdMs
            it[Keys.AUDIO_AUTOPLAY_NEXT] = slice.audioAutoplayNext
            it[Keys.AUDIO_PRELOAD_BUFFER_SIZE] = slice.audioPreloadBufferSize.name
            it[Keys.AUDIO_NORMALIZATION_MODE] = slice.audioNormalizationMode.name
            it[Keys.AUDIO_NORMALIZATION_ENABLED] = slice.audioNormalizationEnabled
            it[Keys.REPLAYGAIN_PRE_AMP_DB] = slice.replayGainPreAmpDb
            it[Keys.CHANNEL_MIX_MODE] = slice.channelMixMode.name
            it[Keys.CHANNEL_MIX_ENABLED] = slice.channelMixEnabled
            it[Keys.AUDIO_GAPLESS_ENABLED] = slice.audioGaplessEnabled
            it[Keys.AUDIO_CROSSFADE_DURATION_MS] = slice.audioCrossfadeDurationMs
            it[Keys.AUDIO_DELAY_MS] = slice.audioDelayMs
            it[Keys.AUDIO_LYRICS_VISIBLE] = slice.audioLyricsVisible
            it[Keys.AUDIO_VISUALIZER_ENABLED] = slice.audioVisualizerEnabled
            it[Keys.SLEEP_TIMER_DURATION_MS] = slice.sleepTimerDurationMs
            it[Keys.SLEEP_TIMER_END_OF_EPISODE] = slice.sleepTimerEndOfEpisode
        }
    }
}

/**
 * The audio-player preference slice. Plain data class.
 * Defaults mirror the projection defaults in [AudioStore.read].
 */
@Immutable
@Serializable
data class AudioSlice(
    val audioDefaultSpeed: Float = 1.0f,
    val audioNightModeVolume: Float = 0.4f,
    val audioNightModeGain: Int = 1200,
    val audioSkipPreviousThresholdMs: Long = 3_000L,
    val audioAutoplayNext: Boolean = true,
    val audioPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val replayGainPreAmpDb: Float = 0f,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val audioGaplessEnabled: Boolean = true,
    val audioCrossfadeDurationMs: Long = 0L,
    val audioDelayMs: Long = 0L,
    val audioLyricsVisible: Boolean = false,
    val audioVisualizerEnabled: Boolean = false,
    val sleepTimerDurationMs: Long = 0L,
    val sleepTimerEndOfEpisode: Boolean = false,
)
