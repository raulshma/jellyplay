package com.raulshma.jellyplay.core.datastore.audioeffects

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the derivation integrity for the audio-effects domain: the
 * [AudioEffectsPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `AudioEffectsStore` derives
 * its `Keys` members, reset lists (and its read/restore machinery) from them —
 * so a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips) stays in [AudioEffectsStoreTest].
 */
class AudioEffectsSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [AudioEffectsStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every
     * read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "dialogue_boost_enabled" to Boolean::class,
        "dialogue_boost_strength" to String::class,
        "equalizer_enabled" to Boolean::class,
        "equalizer_settings" to String::class,
        "equalizer_preset" to String::class,
        "night_mode_enabled" to Boolean::class,
        "night_mode_strength" to String::class,
        "bass_boost_enabled" to Boolean::class,
        "bass_boost_strength" to String::class,
        "virtualizer_enabled" to Boolean::class,
        "virtualizer_strength" to Int::class,
        "reverb_preset" to String::class,
        "lr_balance" to Float::class,
        "auto_eq_by_genre" to Boolean::class,
        "pitch_semitones" to Float::class,
        "volume_boost_enabled" to Boolean::class,
        "volume_boost_gain" to Int::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(AudioEffectsStore.Keys).associateBy { it.name }
        val rows = AudioEffectsPreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based).
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each row's default through its derived write and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        AudioEffectsPreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(expectedStoredType.keys, storedByName.keys)
        expectedStoredType.forEach { (name, expectedType) ->
            assertEquals(
                expectedType,
                storedByName.getValue(name).value::class,
                "storage drift on $name",
            )
        }
    }

    @Test
    fun `resetKeysFor returns exactly the AUDIO-category keys`() {
        val store = AudioEffectsStore(TestDataStoreProvider.get(), scope)
        val audioKeys = store.resetKeysFor(PreferenceResetCategory.AUDIO).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried, in
        // group order.
        assertEquals(
            listOf(
                "dialogue_boost_enabled",
                "dialogue_boost_strength",
                "equalizer_enabled",
                "equalizer_settings",
                "equalizer_preset",
                "night_mode_enabled",
                "night_mode_strength",
                "bass_boost_enabled",
                "bass_boost_strength",
                "virtualizer_enabled",
                "virtualizer_strength",
                "reverb_preset",
                "volume_boost_enabled",
                "volume_boost_gain",
                "lr_balance",
                "auto_eq_by_genre",
                "pitch_semitones",
            ).sorted(),
            audioKeys.sorted(),
        )
        assertEquals(
            emptyList<String>(),
            store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name },
            "every audio-effects key sits in AUDIO — no other category carries one",
        )
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries`() {
        // 1:1 totality: the catalog is the rows' declared search specs (in
        // `all` order) — the only search-less row is the equalizer_settings
        // JSON blob (its face is the equalizer row). Nothing re-listed,
        // nothing hand-appended. Order is pinned as declaration order.
        val entries = AudioEffectsPreferenceSpecs.searchEntries
        assertEquals(
            AudioEffectsPreferenceSpecs.all.mapNotNull { it.search }.map { it.id },
            entries.map { it.id },
        )
        // …and no entry id is declared twice.
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [AudioEffectsSlice] property's
        // default reads its row's `.default`, so the value below IS the row's
        // — if a future edit restates a literal here (or on the slice) and the
        // two drift, this fails.
        val slice = AudioEffectsSlice()
        assertEquals(AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_ENABLED.default, slice.dialogueBoostEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.DIALOGUE_BOOST_STRENGTH.default, slice.dialogueBoostStrength)
        assertEquals(AudioEffectsPreferenceSpecs.EQUALIZER_ENABLED.default, slice.equalizerEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.EQUALIZER_SETTINGS.default, slice.equalizerSettings)
        assertEquals(AudioEffectsPreferenceSpecs.EQUALIZER_PRESET.default, slice.equalizerPreset)
        assertEquals(AudioEffectsPreferenceSpecs.NIGHT_MODE_ENABLED.default, slice.nightModeEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.NIGHT_MODE_STRENGTH.default, slice.nightModeStrength)
        assertEquals(AudioEffectsPreferenceSpecs.BASS_BOOST_ENABLED.default, slice.bassBoostEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.BASS_BOOST_STRENGTH.default, slice.bassBoostStrength)
        assertEquals(AudioEffectsPreferenceSpecs.VIRTUALIZER_ENABLED.default, slice.virtualizerEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.VIRTUALIZER_STRENGTH.default, slice.virtualizerStrength)
        assertEquals(AudioEffectsPreferenceSpecs.REVERB_PRESET.default, slice.reverbPreset)
        assertEquals(AudioEffectsPreferenceSpecs.LR_BALANCE.default, slice.lrBalance)
        assertEquals(AudioEffectsPreferenceSpecs.AUTO_EQ_BY_GENRE.default, slice.autoEqByGenre)
        assertEquals(AudioEffectsPreferenceSpecs.PITCH_SEMITONES.default, slice.pitchSemitones)
        assertEquals(AudioEffectsPreferenceSpecs.VOLUME_BOOST_ENABLED.default, slice.volumeBoostEnabled)
        assertEquals(AudioEffectsPreferenceSpecs.VOLUME_BOOST_GAIN.default, slice.volumeBoostGain)
        assertEquals(EffectStrength.MODERATE, slice.dialogueBoostStrength)
        assertEquals(EqualizerPreset.FLAT, slice.equalizerPreset)
        assertEquals(EqualizerSettings(), slice.equalizerSettings)
        assertEquals(ReverbPreset.NONE, slice.reverbPreset)
        // Totality: the 17 pinned properties are ALL the slice's fields. A
        // field added without a spec-default pin above (and without bumping
        // this count) fails here. Statics (the @Serializable Companion,
        // Compose's $stable) don't count.
        val instanceFields = AudioEffectsSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(17, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
