package com.raulshma.jellyplay.core.datastore.audioeffects

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ReverbPreset
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
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class AudioEffectsStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val DIALOGUE_BOOST_ENABLED = booleanPreferencesKey("dialogue_boost_enabled")
        val DIALOGUE_BOOST_STRENGTH = stringPreferencesKey("dialogue_boost_strength")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_SETTINGS = stringPreferencesKey("equalizer_settings")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val NIGHT_MODE_STRENGTH = stringPreferencesKey("night_mode_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val BASS_BOOST_STRENGTH = stringPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val LR_BALANCE = floatPreferencesKey("lr_balance")
        val AUTO_EQ_BY_GENRE = booleanPreferencesKey("auto_eq_by_genre")
        val PITCH_SEMITONES = floatPreferencesKey("pitch_semitones")
        val VOLUME_BOOST_ENABLED = booleanPreferencesKey("volume_boost_enabled")
        val VOLUME_BOOST_GAIN = intPreferencesKey("volume_boost_gain")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    /**
     * Memoisation holder for the JSON-decoded [EqualizerSettings] blob, keyed on
     * the raw string so the decode is skipped when the underlying key has not
     * changed on a given `dataStore.data` emission.
     */
    private var cachedEqualizerSettings: ParsedCache<EqualizerSettings?> = ParsedCache(null, null)

    val audioEffects: StateFlow<AudioEffectsSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AudioEffectsSlice())

    internal fun read(prefs: Preferences): AudioEffectsSlice {
        val equalizerSettingsRaw = prefs[Keys.EQUALIZER_SETTINGS]
        val equalizerSettings = if (equalizerSettingsRaw != cachedEqualizerSettings.raw) {
            try {
                equalizerSettingsRaw?.let { PreferenceCodec.json.decodeFromString<EqualizerSettings>(it) }
            } catch (_: Exception) {
                null
            }.also { cachedEqualizerSettings = ParsedCache(equalizerSettingsRaw, it) }
        } else {
            cachedEqualizerSettings.value
        }
        return AudioEffectsSlice(
            dialogueBoostEnabled = PreferenceCodec.readBool(prefs, Keys.DIALOGUE_BOOST_ENABLED, "dialogue_boost_enabled", false),
            dialogueBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.DIALOGUE_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) {
                EffectStrength.MODERATE
            },
            equalizerEnabled = PreferenceCodec.readBool(prefs, Keys.EQUALIZER_ENABLED, "equalizer_enabled", false),
            equalizerSettings = equalizerSettings ?: EqualizerSettings(),
            equalizerPreset = try {
                EqualizerPreset.valueOf(prefs[Keys.EQUALIZER_PRESET] ?: EqualizerPreset.FLAT.name)
            } catch (_: Exception) {
                EqualizerPreset.FLAT
            },
            nightModeEnabled = PreferenceCodec.readBool(prefs, Keys.NIGHT_MODE_ENABLED, "night_mode_enabled", false),
            nightModeStrength = try {
                EffectStrength.valueOf(prefs[Keys.NIGHT_MODE_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) {
                EffectStrength.MODERATE
            },
            bassBoostEnabled = PreferenceCodec.readBool(prefs, Keys.BASS_BOOST_ENABLED, "bass_boost_enabled", false),
            bassBoostStrength = try {
                EffectStrength.valueOf(prefs[Keys.BASS_BOOST_STRENGTH] ?: EffectStrength.MODERATE.name)
            } catch (_: Exception) {
                EffectStrength.MODERATE
            },
            virtualizerEnabled = PreferenceCodec.readBool(prefs, Keys.VIRTUALIZER_ENABLED, "virtualizer_enabled", false),
            virtualizerStrength = PreferenceCodec.readInt(prefs, Keys.VIRTUALIZER_STRENGTH, "virtualizer_strength", 500),
            reverbPreset = try {
                ReverbPreset.valueOf(prefs[Keys.REVERB_PRESET] ?: ReverbPreset.NONE.name)
            } catch (_: Exception) {
                ReverbPreset.NONE
            },
            lrBalance = PreferenceCodec.readFloat(prefs, Keys.LR_BALANCE, "lr_balance", 0f),
            autoEqByGenre = PreferenceCodec.readBool(prefs, Keys.AUTO_EQ_BY_GENRE, "auto_eq_by_genre", false),
            pitchSemitones = PreferenceCodec.readFloat(prefs, Keys.PITCH_SEMITONES, "pitch_semitones", 0f),
            volumeBoostEnabled = PreferenceCodec.readBool(prefs, Keys.VOLUME_BOOST_ENABLED, "volume_boost_enabled", false),
            volumeBoostGain = PreferenceCodec.readInt(prefs, Keys.VOLUME_BOOST_GAIN, "volume_boost_gain", 0),
        )
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setDialogueBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DIALOGUE_BOOST_ENABLED] = enabled }
    }

    suspend fun setDialogueBoostStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.DIALOGUE_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setEqualizerSettings(settings: EqualizerSettings) {
        dataStore.edit { it[Keys.EQUALIZER_SETTINGS] = PreferenceCodec.json.encodeToString(settings) }
    }

    suspend fun setEqualizerPreset(preset: EqualizerPreset) {
        dataStore.edit { it[Keys.EQUALIZER_PRESET] = preset.name }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NIGHT_MODE_ENABLED] = enabled }
    }

    suspend fun setNightModeStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.NIGHT_MODE_STRENGTH] = strength.name }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BASS_BOOST_ENABLED] = enabled }
    }

    suspend fun setBassBoostStrength(strength: EffectStrength) {
        dataStore.edit { it[Keys.BASS_BOOST_STRENGTH] = strength.name }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIRTUALIZER_ENABLED] = enabled }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit { it[Keys.VIRTUALIZER_STRENGTH] = strength }
    }

    suspend fun setReverbPreset(preset: ReverbPreset) {
        dataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setLrBalance(balance: Float) {
        dataStore.edit { it[Keys.LR_BALANCE] = balance }
    }

    suspend fun setAutoEqByGenre(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_EQ_BY_GENRE] = enabled }
    }

    suspend fun setPitchSemitones(semitones: Float) {
        dataStore.edit { it[Keys.PITCH_SEMITONES] = semitones }
    }

    suspend fun setVolumeBoostEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_BOOST_ENABLED] = enabled }
    }

    suspend fun setVolumeBoostGain(gain: Int) {
        dataStore.edit { it[Keys.VOLUME_BOOST_GAIN] = gain }
    }

    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.DIALOGUE_BOOST_ENABLED, Keys.DIALOGUE_BOOST_STRENGTH,
        Keys.EQUALIZER_ENABLED, Keys.EQUALIZER_SETTINGS, Keys.EQUALIZER_PRESET,
        Keys.NIGHT_MODE_ENABLED, Keys.NIGHT_MODE_STRENGTH,
        Keys.BASS_BOOST_ENABLED, Keys.BASS_BOOST_STRENGTH,
        Keys.VIRTUALIZER_ENABLED, Keys.VIRTUALIZER_STRENGTH,
        Keys.REVERB_PRESET, Keys.VOLUME_BOOST_ENABLED, Keys.VOLUME_BOOST_GAIN,
        Keys.LR_BALANCE, Keys.AUTO_EQ_BY_GENRE, Keys.PITCH_SEMITONES,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. Every audio-effects key sits in `AUDIO`, so all other
     * categories return an empty list. The facade aggregates these lists
     * instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.AUDIO -> listOf(
            Keys.DIALOGUE_BOOST_ENABLED, Keys.DIALOGUE_BOOST_STRENGTH,
            Keys.EQUALIZER_ENABLED, Keys.EQUALIZER_SETTINGS, Keys.EQUALIZER_PRESET,
            Keys.NIGHT_MODE_ENABLED, Keys.NIGHT_MODE_STRENGTH,
            Keys.BASS_BOOST_ENABLED, Keys.BASS_BOOST_STRENGTH,
            Keys.VIRTUALIZER_ENABLED, Keys.VIRTUALIZER_STRENGTH,
            Keys.REVERB_PRESET, Keys.VOLUME_BOOST_ENABLED, Keys.VOLUME_BOOST_GAIN,
            Keys.LR_BALANCE, Keys.AUTO_EQ_BY_GENRE, Keys.PITCH_SEMITONES,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the audio-effects keys owned by this
     * store from a decoded [UserPreferences]. The facade calls this (and every
     * other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly, including the
     * [EqualizerSettings] JSON blob written via [PreferenceCodec.encodeDefaultsJson].
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.DIALOGUE_BOOST_ENABLED] = userPreferences.dialogueBoostEnabled
            it[Keys.DIALOGUE_BOOST_STRENGTH] = userPreferences.dialogueBoostStrength.name
            it[Keys.EQUALIZER_ENABLED] = userPreferences.equalizerEnabled
            it[Keys.EQUALIZER_SETTINGS] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<EqualizerSettings>(),
                userPreferences.equalizerSettings,
            )
            it[Keys.EQUALIZER_PRESET] = userPreferences.equalizerPreset.name
            it[Keys.NIGHT_MODE_ENABLED] = userPreferences.nightModeEnabled
            it[Keys.NIGHT_MODE_STRENGTH] = userPreferences.nightModeStrength.name
            it[Keys.BASS_BOOST_ENABLED] = userPreferences.bassBoostEnabled
            it[Keys.BASS_BOOST_STRENGTH] = userPreferences.bassBoostStrength.name
            it[Keys.VIRTUALIZER_ENABLED] = userPreferences.virtualizerEnabled
            it[Keys.VIRTUALIZER_STRENGTH] = userPreferences.virtualizerStrength
            it[Keys.REVERB_PRESET] = userPreferences.reverbPreset.name
            it[Keys.VOLUME_BOOST_ENABLED] = userPreferences.volumeBoostEnabled
            it[Keys.VOLUME_BOOST_GAIN] = userPreferences.volumeBoostGain
            it[Keys.LR_BALANCE] = userPreferences.lrBalance
            it[Keys.AUTO_EQ_BY_GENRE] = userPreferences.autoEqByGenre
            it[Keys.PITCH_SEMITONES] = userPreferences.pitchSemitones
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences], including the
     * [EqualizerSettings] JSON blob via [PreferenceCodec.encodeDefaultsJson].
     */
    suspend fun restore(slice: AudioEffectsSlice) {
        dataStore.edit { it ->
            it[Keys.DIALOGUE_BOOST_ENABLED] = slice.dialogueBoostEnabled
            it[Keys.DIALOGUE_BOOST_STRENGTH] = slice.dialogueBoostStrength.name
            it[Keys.EQUALIZER_ENABLED] = slice.equalizerEnabled
            it[Keys.EQUALIZER_SETTINGS] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<EqualizerSettings>(),
                slice.equalizerSettings,
            )
            it[Keys.EQUALIZER_PRESET] = slice.equalizerPreset.name
            it[Keys.NIGHT_MODE_ENABLED] = slice.nightModeEnabled
            it[Keys.NIGHT_MODE_STRENGTH] = slice.nightModeStrength.name
            it[Keys.BASS_BOOST_ENABLED] = slice.bassBoostEnabled
            it[Keys.BASS_BOOST_STRENGTH] = slice.bassBoostStrength.name
            it[Keys.VIRTUALIZER_ENABLED] = slice.virtualizerEnabled
            it[Keys.VIRTUALIZER_STRENGTH] = slice.virtualizerStrength
            it[Keys.REVERB_PRESET] = slice.reverbPreset.name
            it[Keys.VOLUME_BOOST_ENABLED] = slice.volumeBoostEnabled
            it[Keys.VOLUME_BOOST_GAIN] = slice.volumeBoostGain
            it[Keys.LR_BALANCE] = slice.lrBalance
            it[Keys.AUTO_EQ_BY_GENRE] = slice.autoEqByGenre
            it[Keys.PITCH_SEMITONES] = slice.pitchSemitones
        }
    }
}

/**
 * The audio-effects preference slice. Plain data class.
 * Defaults mirror the projection defaults in [AudioEffectsStore.read].
 */
@Immutable
@Serializable
data class AudioEffectsSlice(
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val lrBalance: Float = 0f,
    val autoEqByGenre: Boolean = false,
    val pitchSemitones: Float = 0f,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
)
