package com.raulshma.jellyplay.core.datastore.network

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/**
 * Guards the derivation integrity for the network / offline-mode domain: the
 * [NetworkOfflinePreferenceSpecs] rows are the single declaration of every
 * spec-declarable key's wire name / storage type / reset category, and
 * `NetworkOfflineStore` derives its `Keys` members, reset lists (and its
 * read/restore machinery) from them — so a wire-name, storage-type or
 * reset-category drift would silently change what is read from disk or wiped
 * on reset. These tests pin the derivation to the exact legacy wire strings;
 * the behavioral safety net (defaults, round-trips, the trust-host grants)
 * stays in [NetworkOfflineStoreTest].
 */
class NetworkOfflineSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [NetworkOfflineStore.Keys] members — the exact
     * expectations the hand-written pre-spec store encoded at every
     * read/write site. The hand-residual trust-host set key is not here (no
     * spec row — see the pinned residual test below).
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "manual_offline_enabled" to Boolean::class,
        "auto_offline_enabled" to Boolean::class,
        "manual_bandwidth_cap" to Long::class,
        "home_network_streaming_quality" to String::class,
        "metered_network_behavior" to String::class,
        "adaptive_bitrate_enabled" to Boolean::class,
        "data_saver_enabled" to Boolean::class,
        "verbose_network_logging" to Boolean::class,
        "network_timeout_preset" to String::class,
        "max_cache_size_mb" to Int::class,
        "auto_delete_cache" to Boolean::class,
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(NetworkOfflineStore.Keys).associateBy { it.name }
        val rows = NetworkOfflinePreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted — EXCEPT the one
        // hand-residual key: the trust-host grant set is a DataStore StringSet
        // slot and the spec tier's PreferenceStorage declares single-value
        // storages only, so its Keys member has no row (pinned below).
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(
            rows.keys,
            declared.keys - setOf(NetworkOfflineStore.Keys.SELF_SIGNED_TRUST_HOSTS.name),
        )

        // Each Keys member IS the row's key rebuilt from the single-declared
        // wire name (Preferences.Key equality is name-based) — the migration
        // fallback consults keyName itself, so this equality is what keeps a
        // stored value readable across the derivation.
        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `the trust-host StringSet key stays the one hand-residual row-free key`() {
        val declared = reflectKeys(NetworkOfflineStore.Keys).associateBy { it.name }
        val rowNames = NetworkOfflinePreferenceSpecs.all.map { it.keyName }.toSet()
        // Exactly one key outside the rows, and it is the StringSet slot —
        // with its storage type pinned (a silent re-declaration as a
        // single-value slot would orphan every stored grant).
        assertEquals(setOf(NetworkOfflineStore.Keys.SELF_SIGNED_TRUST_HOSTS.name), declared.keys - rowNames)
        assertEquals(
            stringSetPreferencesKey("self_signed_trust_hosts"),
            declared.getValue(NetworkOfflineStore.Keys.SELF_SIGNED_TRUST_HOSTS.name),
        )
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encoding — the declaration-only home-quality row is pinned by the
        // key-identity test above).
        val prefs = emptyPreferences().toMutablePreferences()
        NetworkOfflinePreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(
            expectedStoredType.keys - "home_network_streaming_quality",
            storedByName.keys,
        )
        expectedStoredType
            .filterKeys { it != "home_network_streaming_quality" }
            .forEach { (name, expectedType) ->
                assertEquals(
                    expectedType,
                    storedByName.getValue(name).value::class,
                    "storage drift on $name",
                )
            }
    }

    @Test
    fun `resetKeysFor returns exactly the DOWNLOADS_NETWORK-category keys`() {
        val store = NetworkOfflineStore(TestDataStoreProvider.get(), scope)
        val networkKeys = store.resetKeysFor(PreferenceResetCategory.DOWNLOADS_NETWORK).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried: the
        // eleven spec rows plus the hand-residual trust-host set (trust grants
        // are security-relevant — every reset lane that touches the network
        // domain clears them, fail closed).
        assertEquals(
            (expectedStoredType.keys + NetworkOfflineStore.Keys.SELF_SIGNED_TRUST_HOSTS.name).sorted(),
            networkKeys.sorted(),
        )
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.DOWNLOADS_NETWORK }
            .forEach { category ->
                assertEquals(
                    emptyList<Preferences.Key<*>>(),
                    store.resetKeysFor(category),
                    "$category unexpectedly non-empty",
                )
            }
    }

    @Test
    fun `searchEntries is empty - the search faces stay the feature-side residuals`() {
        // No row declares a search face (the network screen's row groups own
        // the domain's catalog rows), so the derived list is empty by
        // construction — and no entry id is declared twice (trivially true,
        // pinned so a future search-bearing row cannot land without this test
        // noticing the shape change).
        val entries = NetworkOfflinePreferenceSpecs.searchEntries
        assertEquals(NetworkOfflinePreferenceSpecs.all.mapNotNull { it.search }, entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [NetworkOfflineSlice] property's
        // default reads its row's `.default`, so the value below IS the
        // row's — if a future edit restates a literal here (or on the slice)
        // and the two drift, this fails. The hand-residual trust-host set
        // keeps its `emptySet()` literal (no row to read a default from).
        val slice = NetworkOfflineSlice()
        assertEquals(NetworkOfflinePreferenceSpecs.MANUAL_OFFLINE_ENABLED.default, slice.manualOfflineEnabled)
        assertEquals(NetworkOfflinePreferenceSpecs.AUTO_OFFLINE_ENABLED.default, slice.autoOfflineEnabled)
        assertEquals(NetworkOfflinePreferenceSpecs.MANUAL_BANDWIDTH_CAP.default, slice.manualBandwidthCap)
        assertEquals(NetworkOfflinePreferenceSpecs.HOME_STREAMING_QUALITY.default, slice.homeStreamingQuality)
        assertEquals(NetworkOfflinePreferenceSpecs.METERED_NETWORK_BEHAVIOR.default, slice.meteredNetworkBehavior)
        assertEquals(NetworkOfflinePreferenceSpecs.ADAPTIVE_BITRATE_ENABLED.default, slice.adaptiveBitrateEnabled)
        assertEquals(NetworkOfflinePreferenceSpecs.DATA_SAVER_ENABLED.default, slice.dataSaverEnabled)
        assertEquals(NetworkOfflinePreferenceSpecs.VERBOSE_NETWORK_LOGGING.default, slice.verboseNetworkLogging)
        assertEquals(NetworkOfflinePreferenceSpecs.NETWORK_TIMEOUT_PRESET.default, slice.networkTimeoutPreset)
        assertEquals(NetworkOfflinePreferenceSpecs.MAX_CACHE_SIZE_MB.default, slice.maxCacheSizeMb)
        assertEquals(NetworkOfflinePreferenceSpecs.AUTO_DELETE_CACHE.default, slice.autoDeleteCache)
        // Totality: the 12 pinned properties are ALL the slice's fields (11
        // rows + the hand-residual trust-host set). A field added without a
        // spec-default pin above (and without bumping this count) fails here.
        // Statics (the @Serializable Companion, Compose's $stable) don't
        // count.
        val instanceFields = NetworkOfflineSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(12, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
