package com.raulshma.jellyplay.core.datastore.audioeffects

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **audio-effects** preference domain: dialogue boost
 * (enabled + strength), equalizer (enabled + settings + preset), night-mode
 * effect (enabled + strength), bass boost (enabled + strength), virtualizer
 * (enabled + strength), reverb preset, L/R balance, AutoEq-by-genre, pitch
 * semitones, and volume boost (enabled + gain).
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection (including the JSON-memoized
 * [EqualizerSettings]), and reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [AudioEffectsPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding, the single-key
 * setters and [restore] delegate to the rows' derived writes, and
 * [resetKeysFor] filters the rows by reset category. The [EqualizerSettings]
 * JSON blob's decode lives in the [AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS]
 * row's read (memoised beside the row). The blob's derived write is the lenient
 * encode — value-equivalent to the defaults-encoding the pre-spec `restore`
 * used (the omitted defaults decode back to their Kotlin defaults).
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [AudioEffectsPreferenceSpecs] row + one [AudioEffectsSlice] property + one
 * [read] / [restore] row (and its setter) + one write-through test line. See
 * the spec KDoc.
 */
class AudioEffectsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [AudioEffectsPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard.
     */
    internal object Keys {
        val DIALOGUE_BOOST_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.keyName)
        val DIALOGUE_BOOST_STRENGTH = stringPreferencesKey(AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.keyName)
        val EQUALIZER_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.keyName)
        val EQUALIZER_SETTINGS = stringPreferencesKey(AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.keyName)
        val EQUALIZER_PRESET = stringPreferencesKey(AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.keyName)
        val NIGHT_MODE_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.keyName)
        val NIGHT_MODE_STRENGTH = stringPreferencesKey(AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.keyName)
        val BASS_BOOST_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.keyName)
        val BASS_BOOST_STRENGTH = stringPreferencesKey(AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.keyName)
        val VIRTUALIZER_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.keyName)
        val VIRTUALIZER_STRENGTH = intPreferencesKey(AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.keyName)
        val REVERB_PRESET = stringPreferencesKey(AudioEffectsPreferenceSpecs.REVERB_PRESET.keyName)
        val LR_BALANCE = floatPreferencesKey(AudioEffectsPreferenceSpecs.LR_BALANCE.keyName)
        val AUTO_EQ_BY_GENRE = booleanPreferencesKey(AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.keyName)
        val PITCH_SEMITONES = floatPreferencesKey(AudioEffectsPreferenceSpecs.PITCH_SEMITONES.keyName)
        val VOLUME_BOOST_ENABLED = booleanPreferencesKey(AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.keyName)
        val VOLUME_BOOST_GAIN = intPreferencesKey(AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.keyName)
    }

    val audioEffects: StateFlow<AudioEffectsSlice> =
        dataStore.sliceStateFlow(scope, seed = AudioEffectsSlice(), read = ::read)

    /**
     * Pure read of the audio-effects fields from a raw [Preferences] snapshot,
     * each field delegated to its [AudioEffectsPreferenceSpecs] row encoding.
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): AudioEffectsSlice = AudioEffectsSlice(
        dialogueBoostEnabled = AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.readFrom(prefs),
        dialogueBoostStrength = AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.readFrom(prefs),
        equalizerEnabled = AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.readFrom(prefs),
        equalizerSettings = AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.readFrom(prefs),
        equalizerPreset = AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.readFrom(prefs),
        nightModeEnabled = AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.readFrom(prefs),
        nightModeStrength = AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.readFrom(prefs),
        bassBoostEnabled = AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.readFrom(prefs),
        bassBoostStrength = AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.readFrom(prefs),
        virtualizerEnabled = AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.readFrom(prefs),
        virtualizerStrength = AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.readFrom(prefs),
        reverbPreset = AudioEffectsPreferenceSpecs.REVERB_PRESET.readFrom(prefs),
        lrBalance = AudioEffectsPreferenceSpecs.LR_BALANCE.readFrom(prefs),
        autoEqByGenre = AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.readFrom(prefs),
        pitchSemitones = AudioEffectsPreferenceSpecs.PITCH_SEMITONES.readFrom(prefs),
        volumeBoostEnabled = AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.readFrom(prefs),
        volumeBoostGain = AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, JSON — is the row's, not re-declared
    // here).
    // ------------------------------------------------------------------

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        dataStore.edit { AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.writeTo(it, strength) }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        dataStore.edit { AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.writeTo(it, settings) }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        dataStore.edit { AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.writeTo(it, preset) }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        dataStore.edit { AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.writeTo(it, strength) }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        dataStore.edit { AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.writeTo(it, strength) }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit { AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.writeTo(it, strength) }
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        dataStore.edit { AudioEffectsPreferenceSpecs.REVERB_PRESET.writeTo(it, preset) }
    }

    suspend fun setLrBalance(balance: Float) {
        dataStore.edit { AudioEffectsPreferenceSpecs.LR_BALANCE.writeTo(it, balance) }
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.writeTo(it, enabled) }
    }

    suspend fun setPitchSemitones(semitones: Float) {
        dataStore.edit { AudioEffectsPreferenceSpecs.PITCH_SEMITONES.writeTo(it, semitones) }
    }

    suspend fun setVolumeBoostEnabled(enabled: Boolean) {
        dataStore.edit { AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setVolumeBoostGain(gain: Int) {
        dataStore.edit { AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.writeTo(it, gain) }
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
     * [category] — the [AudioEffectsPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every audio-effects key
     * sits in `AUDIO`, so all other categories return an empty list. The
     * facade aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        AudioEffectsPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with).
     */
    suspend fun restore(slice: AudioEffectsSlice) {
        dataStore.edit { prefs ->
            AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.writeTo(prefs, slice.dialogueBoostEnabled)
            AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.writeTo(prefs, slice.dialogueBoostStrength)
            AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.writeTo(prefs, slice.equalizerEnabled)
            AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.writeTo(prefs, slice.equalizerSettings)
            AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.writeTo(prefs, slice.equalizerPreset)
            AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.writeTo(prefs, slice.nightModeEnabled)
            AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.writeTo(prefs, slice.nightModeStrength)
            AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.writeTo(prefs, slice.bassBoostEnabled)
            AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.writeTo(prefs, slice.bassBoostStrength)
            AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.writeTo(prefs, slice.virtualizerEnabled)
            AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.writeTo(prefs, slice.virtualizerStrength)
            AudioEffectsPreferenceSpecs.REVERB_PRESET.writeTo(prefs, slice.reverbPreset)
            AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.writeTo(prefs, slice.volumeBoostEnabled)
            AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.writeTo(prefs, slice.volumeBoostGain)
            AudioEffectsPreferenceSpecs.LR_BALANCE.writeTo(prefs, slice.lrBalance)
            AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.writeTo(prefs, slice.autoEqByGenre)
            AudioEffectsPreferenceSpecs.PITCH_SEMITONES.writeTo(prefs, slice.pitchSemitones)
        }
    }
}

/**
 * The audio-effects preference slice. Plain data class.
 * Every default IS its [AudioEffectsPreferenceSpecs] row's declared default —
 * read off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an absent
 * key reads) and the slice (what a fresh in-memory instance serves). The
 * spec-default equality is pinned field-for-field by
 * `AudioEffectsSpecDerivationTest`.
 */
@Immutable
@Serializable
data class AudioEffectsSlice(
    val dialogueBoostEnabled: Boolean = AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.default,
    val dialogueBoostStrength: EffectStrength = AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.default,
    val equalizerEnabled: Boolean = AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.default,
    val equalizerSettings: EqualizerSettings = AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.default,
    val equalizerPreset: EqualizerPreset = AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.default,
    val nightModeEnabled: Boolean = AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.default,
    val nightModeStrength: EffectStrength = AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.default,
    val bassBoostEnabled: Boolean = AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.default,
    val bassBoostStrength: EffectStrength = AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.default,
    val virtualizerEnabled: Boolean = AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.default,
    val virtualizerStrength: Int = AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.default,
    val reverbPreset: ReverbPreset = AudioEffectsPreferenceSpecs.REVERB_PRESET.default,
    val lrBalance: Float = AudioEffectsPreferenceSpecs.LR_BALANCE.default,
    val autoEqByGenre: Boolean = AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.default,
    val pitchSemitones: Float = AudioEffectsPreferenceSpecs.PITCH_SEMITONES.default,
    val volumeBoostEnabled: Boolean = AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.default,
    val volumeBoostGain: Int = AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.default,
)
