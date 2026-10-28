package com.raulshma.jellyplay.core.datastore.engine

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the derivation integrity for the player-engine domain: the
 * [PlayerEnginePreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `PlayerEngineStore` derives
 * its `Keys` members, reset lists (and its read/config-write machinery) from
 * them — so a wire-name, storage-type or reset-category drift would silently
 * change what is read from disk or wiped on reset. These tests pin the
 * derivation to the exact legacy wire strings; the behavioral safety net
 * (defaults, round-trips, the LRU caps) stays in [PlayerEngineStoreTest].
 */
class PlayerEngineSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [PlayerEngineStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "mpv_config" to String::class,
        "libvlc_config" to String::class,
        "exo_config" to String::class,
        "media_stream_selections" to String::class,
        "video_effects_selections" to String::class,
    )

    /**
     * The two per-item map rows carry the documented write opt-out (encode =
     * null — their writes are the store's read-modify-write verbs, never a
     * single-slot write), so the storage pin writes a representative wire
     * value the way those verbs encode it.
     */
    private val optOutRepresentativeWrites: Map<String, String> = mapOf(
        "media_stream_selections" to "{}",
        "video_effects_selections" to "{}",
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(PlayerEngineStore.Keys).associateBy { it.name }
        val rows = PlayerEnginePreferenceSpecs.all.associateBy { it.keyName }

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
    fun `every row's write stores its declared storage's value type`() {
        // Write each row's default through its derived write (the opt-out rows
        // through their representative verb-encoded value) and assert the
        // stored value's runtime type matches the pinned legacy storage table.
        val prefs = emptyPreferences().toMutablePreferences()
        PlayerEnginePreferenceSpecs.all.forEach { spec -> spec.writeDefault(prefs) }
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
    fun `resetKeysFor returns exactly the PLAYER_ENGINES-category keys`() {
        val store = PlayerEngineStore(TestDataStoreProvider.get(), scope)
        val engineKeys = store.resetKeysFor(PreferenceResetCategory.PLAYER_ENGINES).map { it.name }
        // The three engine-config blobs; the two per-item maps are runtime
        // state and appear in no category list.
        assertEquals(
            listOf(
                "mpv_config",
                "libvlc_config",
                "exo_config",
            ).sorted(),
            engineKeys.sorted(),
        )
        assertEquals(
            emptyList<String>(),
            store.resetKeysFor(PreferenceResetCategory.PLAYBACK).map { it.name },
            "the per-item maps carry no reset category — no other category carries a key",
        )
    }

    @Test
    fun `searchEntries is empty - the blob rows declare no search faces`() {
        // The blobs' user faces are the engine screens' individual knobs (the
        // feature-side residuals); the per-item maps are not settings.
        assertEquals(emptyList(), PlayerEnginePreferenceSpecs.searchEntries)
        assertEquals(0, PlayerEnginePreferenceSpecs.all.count { it.search != null })
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [PlayerEngineSlice] property's
        // default reads its row's `.default`, so the value below IS the row's
        // — if a future edit restates a literal here (or on the slice) and the
        // two drift, this fails.
        val slice = PlayerEngineSlice()
        assertEquals(PlayerEnginePreferenceSpecs.MPV_CONFIG.default, slice.mpvConfig)
        assertEquals(PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.default, slice.libVlcConfig)
        assertEquals(PlayerEnginePreferenceSpecs.EXO_CONFIG.default, slice.exoPlayerConfig)
        assertEquals(PlayerEnginePreferenceSpecs.MEDIA_STREAM_SELECTIONS.default, slice.mediaStreamSelections)
        assertEquals(PlayerEnginePreferenceSpecs.VIDEO_EFFECTS_SELECTIONS.default, slice.videoEffectsByItem)
        assertEquals(MpvEngineConfig(), slice.mpvConfig)
        assertEquals(LibVlcEngineConfig(), slice.libVlcConfig)
        assertEquals(ExoPlayerEngineConfig(), slice.exoPlayerConfig)
        // Totality: the 5 pinned properties are ALL the slice's fields. A
        // field added without a spec-default pin above (and without bumping
        // this count) fails here. Statics (the @Serializable Companion,
        // Compose's $stable) don't count.
        val instanceFields = PlayerEngineSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(5, instanceFields)
    }

    /** Writes [spec]'s default (opt-out rows via their representative wire value). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        val hasDerivedWrite = encoding?.write != null
        if (hasDerivedWrite) {
            (this as PreferenceSpec<Any>).writeTo(prefs, default)
        } else {
            val representative = optOutRepresentativeWrites.getValue(keyName)
            @Suppress("UNCHECKED_CAST")
            prefs[typedKey() as Preferences.Key<String>] = representative
        }
    }
}
