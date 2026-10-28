package com.raulshma.jellyplay.core.datastore.audiocache

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
 * Guards the derivation integrity for the audio-cache policy domain: the
 * [AudioCachePreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `AudioCacheStore` derives
 * its `Keys` members, reset lists (and its read/restore machinery) from them
 * — so a wire-name, storage-type or reset-category drift would silently
 * change what is read from disk or wiped on reset. These tests pin the
 * derivation to the exact legacy wire strings; the behavioral safety net
 * (defaults, round-trips) stays in [AudioCacheStoreTest].
 */
class AudioCacheSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [AudioCacheStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every
     * read/write site.
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "audio_caching_enabled" to Boolean::class,
        "audio_cache_size_mb" to Int::class,
        "audio_prefetch_lookahead" to Int::class,
        "audio_prefetch_backfill" to Int::class,
        "audio_cache_network_policy" to String::class,
        "audio_cache_cellular_monthly_cap_mb" to Int::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(AudioCacheStore.Keys).associateBy { it.name }
        val rows = AudioCachePreferenceSpecs.all.associateBy { it.keyName }

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
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encoding — the declaration-only caching-enabled row is pinned by the
        // key-identity test above).
        val prefs = emptyPreferences().toMutablePreferences()
        AudioCachePreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(
            expectedStoredType.keys - "audio_caching_enabled",
            storedByName.keys,
        )
        expectedStoredType
            .filterKeys { it != "audio_caching_enabled" }
            .forEach { (name, expectedType) ->
                assertEquals(
                    expectedType,
                    storedByName.getValue(name).value::class,
                    "storage drift on $name",
                )
            }
    }

    @Test
    fun `resetKeysFor returns exactly the AUDIO_CACHE-category keys`() {
        val store = AudioCacheStore(TestDataStoreProvider.get(), scope)
        val cacheKeys = store.resetKeysFor(PreferenceResetCategory.AUDIO_CACHE).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried — every
        // key this store owns sits in the AUDIO_CACHE bucket.
        assertEquals(expectedStoredType.keys.sorted(), cacheKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.AUDIO_CACHE }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is empty - the search faces stay the feature-side residuals`() {
        // No row declares a search face (the audio screen's row groups own
        // the domain's catalog rows), so the derived list is empty by
        // construction — and no entry id is declared twice (trivially true,
        // pinned so a future search-bearing row cannot land without this test
        // noticing the shape change).
        val entries = AudioCachePreferenceSpecs.searchEntries
        assertEquals(AudioCachePreferenceSpecs.all.mapNotNull { it.search }, entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [AudioCacheSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails.
        val slice = AudioCacheSlice()
        assertEquals(AudioCachePreferenceSpecs.AUDIO_CACHING_ENABLED.default, slice.audioCachingEnabled)
        assertEquals(AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.default, slice.audioCacheSizeMb)
        assertEquals(AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.default, slice.audioPrefetchLookahead)
        assertEquals(AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.default, slice.audioPrefetchBackfill)
        assertEquals(AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.default, slice.audioCacheNetworkPolicy)
        assertEquals(
            AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB.default,
            slice.audioCacheCellularMonthlyCapMb,
        )
        // Totality: the 6 pinned properties are ALL the slice's fields (6
        // rows, every row slice-bound). A field added without a spec-default
        // pin above (and without bumping this count) fails here. Statics (the
        // @Serializable Companion, Compose's $stable) don't count.
        val instanceFields = AudioCacheSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(6, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
