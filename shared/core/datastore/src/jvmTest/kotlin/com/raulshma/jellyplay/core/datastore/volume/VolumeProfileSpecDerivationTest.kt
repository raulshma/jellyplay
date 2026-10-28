package com.raulshma.jellyplay.core.datastore.volume

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the volume-memory domain: the
 * [VolumeProfilePreferenceSpecs] rows are the single declaration of every
 * key's wire name / storage type / reset category, and `VolumeProfileStore`
 * derives its `Keys` members, reset lists (and its read machinery) from them —
 * so a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (the JSON
 * round-trip, coercion, corrupt-blob tolerance) stays in
 * [VolumeProfileStoreTest].
 */
class VolumeProfileSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [VolumeProfileStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "volume_profiles" to String::class,
        "remember_volume_per_content_type" to Boolean::class,
    )

    private fun expectedKey(name: String, type: KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(VolumeProfileStore.Keys).associateBy { it.name }
        val rows = VolumeProfilePreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based) — the migration
        // fallback consults keyName itself, so this equality is what keeps a
        // stored value readable across the derivation.
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encode — the volume_profiles blob opts out of the derived write (the
        // read-modify-write setters + the identity-by-absence restore stay
        // hand-written), so its storage type is pinned by the typed-key
        // equality in the test above).
        val prefs = emptyPreferences().toMutablePreferences()
        VolumeProfilePreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(setOf("remember_volume_per_content_type"), storedByName.keys)
        expectedStoredType.forEach { (name, expectedType) ->
            storedByName[name]?.let {
                assertEquals(
                    expectedType,
                    it.value::class,
                    "storage drift on $name",
                )
            }
        }
    }

    @Test
    fun `resetKeysFor returns exactly the PLAYBACK-category keys in declaration order`() {
        val store = VolumeProfileStore(TestDataStoreProvider.get(), scope)
        val playbackKeys = store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried, in its
        // order (blob, then toggle) — every key this store owns sits in the
        // PLAYBACK bucket.
        assertEquals(
            listOf("volume_profiles", "remember_volume_per_content_type"),
            playbackKeys,
        )
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.PLAYBACK }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is exactly the searchable rows' entries`() {
        // The toggle carries the domain's only search face (desktop-only,
        // reusing the hand fused row's ss_* keys); the blob's user face IS the
        // toggle. 1:1 totality, and no entry id is declared twice.
        val entries = VolumeProfilePreferenceSpecs.searchEntries
        assertEquals(
            VolumeProfilePreferenceSpecs.all.mapNotNull { it.search }.map { it.id },
            entries.map { it.id },
        )
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [VolumeProfileSlice] property's
        // default reads its row's `.default`, so the value below IS the row's
        // — if a future edit restates a literal here (or on the slice) and the
        // two drift, this fails.
        val slice = VolumeProfileSlice()
        assertEquals(VolumeProfilePreferenceSpecs.VOLUME_PROFILES.default, slice.volumes)
        assertEquals(VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.default, slice.rememberVolumePerContentType)
        // Totality: the 2 pinned properties are ALL the slice's fields (2
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = VolumeProfileSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(2, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
