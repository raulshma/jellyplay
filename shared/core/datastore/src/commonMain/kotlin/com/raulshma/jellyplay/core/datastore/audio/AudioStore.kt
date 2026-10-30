package com.raulshma.jellyplay.core.datastore.audio

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
 * **Spec derivation:** every key this store owns is declared exactly once as
 * a row in [AudioPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding, the single-key
 * setters and [restore] delegate to the rows' derived writes, and
 * [resetKeysFor] filters the rows by reset category. The legacy `REPLAYGAIN`
 * → `TRACK` normalization-mode alias lives in the
 * [AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE] row's read now, next to the
 * key it applies to.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [AudioPreferenceSpecs] row + one [AudioSlice] property + one [read] /
 * [restore] row (and its setter) + one write-through test line. See the spec
 * KDoc.
 */
class AudioStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [AudioPreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for the JVM reset-coverage guard.
     */
    internal object Keys {
        val AUDIO_DEFAULT_SPEED = floatPreferencesKey(AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.keyName)
        val AUDIO_NIGHT_MODE_VOLUME = floatPreferencesKey(AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.keyName)
        val AUDIO_NIGHT_MODE_GAIN = intPreferencesKey(AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.keyName)
        val AUDIO_SKIP_PREVIOUS_THRESHOLD_MS =
            longPreferencesKey(AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.keyName)
        val AUDIO_AUTOPLAY_NEXT = booleanPreferencesKey(AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.keyName)
        val AUDIO_PRELOAD_BUFFER_SIZE = stringPreferencesKey(AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.keyName)
        val AUDIO_NORMALIZATION_MODE = stringPreferencesKey(AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.keyName)
        val AUDIO_NORMALIZATION_ENABLED = booleanPreferencesKey(AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.keyName)
        val REPLAYGAIN_PRE_AMP_DB = floatPreferencesKey(AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.keyName)
        val CHANNEL_MIX_MODE = stringPreferencesKey(AudioPreferenceSpecs.CHANNEL_MIX_MODE.keyName)
        val CHANNEL_MIX_ENABLED = booleanPreferencesKey(AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.keyName)
        val AUDIO_GAPLESS_ENABLED = booleanPreferencesKey(AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.keyName)
        val AUDIO_CROSSFADE_DURATION_MS =
            longPreferencesKey(AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.keyName)
        val AUDIO_DELAY_MS = longPreferencesKey(AudioPreferenceSpecs.AUDIO_DELAY_MS.keyName)
        val AUDIO_LYRICS_VISIBLE = booleanPreferencesKey(AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.keyName)
        val AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey(AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.keyName)
        val SLEEP_TIMER_DURATION_MS = longPreferencesKey(AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.keyName)
        val SLEEP_TIMER_END_OF_EPISODE = booleanPreferencesKey(AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.keyName)
    }

    val audio: StateFlow<AudioSlice> =
        dataStore.sliceStateFlow(scope, seed = AudioSlice(), read = ::read)

    /**
     * Pure read of the audio-player fields from a raw [Preferences] snapshot,
     * each field delegated to its [AudioPreferenceSpecs] row encoding.
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): AudioSlice = AudioSlice(
        audioDefaultSpeed = AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.readFrom(prefs),
        audioNightModeVolume = AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.readFrom(prefs),
        audioNightModeGain = AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.readFrom(prefs),
        audioSkipPreviousThresholdMs = AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.readFrom(prefs),
        audioAutoplayNext = AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.readFrom(prefs),
        audioPreloadBufferSize = AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.readFrom(prefs),
        audioNormalizationMode = AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.readFrom(prefs),
        audioNormalizationEnabled = AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.readFrom(prefs),
        replayGainPreAmpDb = AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.readFrom(prefs),
        channelMixMode = AudioPreferenceSpecs.CHANNEL_MIX_MODE.readFrom(prefs),
        channelMixEnabled = AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.readFrom(prefs),
        audioGaplessEnabled = AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.readFrom(prefs),
        audioCrossfadeDurationMs = AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.readFrom(prefs),
        audioDelayMs = AudioPreferenceSpecs.AUDIO_DELAY_MS.readFrom(prefs),
        audioLyricsVisible = AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.readFrom(prefs),
        audioVisualizerEnabled = AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.readFrom(prefs),
        sleepTimerDurationMs = AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.readFrom(prefs),
        sleepTimerEndOfEpisode = AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, plain slot — is the row's, not
    // re-declared here).
    // ------------------------------------------------------------------

    suspend fun setAudioDefaultSpeed(speed: Float) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.writeTo(it, speed) }
    }

    suspend fun setAudioNightModeVolume(volume: Float) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.writeTo(it, volume) }
    }

    suspend fun setAudioNightModeGain(gain: Int) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.writeTo(it, gain) }
    }

    suspend fun setAudioSkipPreviousThresholdMs(ms: Long) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.writeTo(it, ms) }
    }

    suspend fun setAudioAutoplayNext(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.writeTo(it, enabled) }
    }

    suspend fun setAudioPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.writeTo(it, size) }
    }

    suspend fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.writeTo(it, mode) }
    }

    suspend fun setAudioNormalizationEnabled(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setReplayGainPreAmpDb(db: Float) {
        dataStore.edit { AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.writeTo(it, db) }
    }

    suspend fun setChannelMixMode(mode: ChannelMixMode) {
        dataStore.edit { AudioPreferenceSpecs.CHANNEL_MIX_MODE.writeTo(it, mode) }
    }

    suspend fun setChannelMixEnabled(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setAudioGaplessEnabled(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setAudioCrossfadeDurationMs(ms: Long) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.writeTo(it, ms) }
    }

    suspend fun setAudioDelay(ms: Long) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_DELAY_MS.writeTo(it, ms) }
    }

    suspend fun setAudioLyricsVisible(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.writeTo(it, enabled) }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setSleepTimerDurationMs(ms: Long) {
        dataStore.edit { AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.writeTo(it, ms) }
    }

    suspend fun setSleepTimerEndOfEpisode(enabled: Boolean) {
        dataStore.edit { AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.writeTo(it, enabled) }
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
     * [category] — the [AudioPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. A single store can own
     * keys across several categories ([Keys.AUDIO_DELAY_MS] sits in `PLAYBACK`
     * in the legacy mapping while the rest are `AUDIO`), so each store scopes
     * its own keys per category. The facade aggregates these lists instead of
     * a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        AudioPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with).
     */
    suspend fun restore(slice: AudioSlice) {
        dataStore.edit { prefs ->
            AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.writeTo(prefs, slice.audioDefaultSpeed)
            AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.writeTo(prefs, slice.audioNightModeVolume)
            AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.writeTo(prefs, slice.audioNightModeGain)
            AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.writeTo(prefs, slice.audioSkipPreviousThresholdMs)
            AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.writeTo(prefs, slice.audioAutoplayNext)
            AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.writeTo(prefs, slice.audioPreloadBufferSize)
            AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.writeTo(prefs, slice.audioNormalizationMode)
            AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.writeTo(prefs, slice.audioNormalizationEnabled)
            AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.writeTo(prefs, slice.replayGainPreAmpDb)
            AudioPreferenceSpecs.CHANNEL_MIX_MODE.writeTo(prefs, slice.channelMixMode)
            AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.writeTo(prefs, slice.channelMixEnabled)
            AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.writeTo(prefs, slice.audioGaplessEnabled)
            AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.writeTo(prefs, slice.audioCrossfadeDurationMs)
            AudioPreferenceSpecs.AUDIO_DELAY_MS.writeTo(prefs, slice.audioDelayMs)
            AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.writeTo(prefs, slice.audioLyricsVisible)
            AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.writeTo(prefs, slice.audioVisualizerEnabled)
            AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.writeTo(prefs, slice.sleepTimerDurationMs)
            AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.writeTo(prefs, slice.sleepTimerEndOfEpisode)
        }
    }
}

/**
 * The audio-player preference slice. Plain data class.
 * Every default IS its [AudioPreferenceSpecs] row's declared default — read
 * off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an
 * absent key reads) and the slice (what a fresh in-memory instance serves).
 * The spec-default equality is pinned field-for-field by
 * `AudioSpecDerivationTest`.
 */
@Immutable
@Serializable
data class AudioSlice(
    val audioDefaultSpeed: Float = AudioPreferenceSpecs.AUDIO_DEFAULT_SPEED.default,
    val audioNightModeVolume: Float = AudioPreferenceSpecs.AUDIO_NIGHT_MODE_VOLUME.default,
    val audioNightModeGain: Int = AudioPreferenceSpecs.AUDIO_NIGHT_MODE_GAIN.default,
    val audioSkipPreviousThresholdMs: Long = AudioPreferenceSpecs.AUDIO_SKIP_PREVIOUS_THRESHOLD_MS.default,
    val audioAutoplayNext: Boolean = AudioPreferenceSpecs.AUDIO_AUTOPLAY_NEXT.default,
    val audioPreloadBufferSize: PreloadBufferSize = AudioPreferenceSpecs.AUDIO_PRELOAD_BUFFER_SIZE.default,
    val audioNormalizationMode: AudioNormalizationMode = AudioPreferenceSpecs.AUDIO_NORMALIZATION_MODE.default,
    val audioNormalizationEnabled: Boolean = AudioPreferenceSpecs.AUDIO_NORMALIZATION_ENABLED.default,
    val replayGainPreAmpDb: Float = AudioPreferenceSpecs.REPLAYGAIN_PRE_AMP_DB.default,
    val channelMixMode: ChannelMixMode = AudioPreferenceSpecs.CHANNEL_MIX_MODE.default,
    val channelMixEnabled: Boolean = AudioPreferenceSpecs.CHANNEL_MIX_ENABLED.default,
    val audioGaplessEnabled: Boolean = AudioPreferenceSpecs.AUDIO_GAPLESS_ENABLED.default,
    val audioCrossfadeDurationMs: Long = AudioPreferenceSpecs.AUDIO_CROSSFADE_DURATION_MS.default,
    val audioDelayMs: Long = AudioPreferenceSpecs.AUDIO_DELAY_MS.default,
    val audioLyricsVisible: Boolean = AudioPreferenceSpecs.AUDIO_LYRICS_VISIBLE.default,
    val audioVisualizerEnabled: Boolean = AudioPreferenceSpecs.AUDIO_VISUALIZER_ENABLED.default,
    val sleepTimerDurationMs: Long = AudioPreferenceSpecs.SLEEP_TIMER_DURATION_MS.default,
    val sleepTimerEndOfEpisode: Boolean = AudioPreferenceSpecs.SLEEP_TIMER_END_OF_EPISODE.default,
)
