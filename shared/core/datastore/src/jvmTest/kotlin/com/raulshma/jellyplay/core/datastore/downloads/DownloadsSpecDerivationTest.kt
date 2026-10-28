package com.raulshma.jellyplay.core.datastore.downloads

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.reflectKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the downloads domain: the
 * [DownloadsPreferenceSpecs] rows are the single declaration of every key's
 * wire name / storage type / reset category, and `DownloadsStore` derives its
 * `Keys` members, reset lists (and its read/restore machinery) from them — so
 * a wire-name, storage-type or reset-category drift would silently change
 * what is read from disk or wiped on reset. These tests pin the derivation to
 * the exact legacy wire strings; the behavioral safety net (defaults,
 * round-trips, the 1..6 band, the legacy-boolean fallback and the namespaced
 * allow-list) stays in [DownloadsStoreTest].
 */
class DownloadsSpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * The legacy wire name → stored value type each slot must keep. DataStore
     * dispatches its file serializer on the stored VALUE type (its Key is one
     * generic class), so this table is what makes a stored slot readable
     * through the typed [DownloadsStore.Keys] members — the exact expectations
     * the hand-written pre-spec store encoded at every read/write site (the
     * enum and JSON-blob slots are STRING-typed on disk).
     */
    private val expectedStoredType: Map<String, KClass<*>> = mapOf(
        "wifi_only_downloads" to Boolean::class,
        "download_connections" to Int::class,
        "max_concurrent_downloads" to Int::class,
        "download_quality" to String::class,
        "smart_downloads_enabled" to Boolean::class,
        "auto_download_mode" to String::class,
        "auto_download_new_episodes" to Boolean::class,
        "auto_download_lookahead" to Int::class,
        "auto_download_max_per_pass" to Int::class,
        "auto_download_keep_days" to Int::class,
        "auto_download_servers" to String::class,
        "max_download_storage_gb" to Int::class,
        "downloads_list_filter" to String::class,
        "downloads_list_sort" to String::class,
        "downloads_list_status_filter" to String::class,
        "downloads_selection_hint_seen" to Boolean::class,
        "download_storage_location" to String::class,
        "auto_delete_after_watch" to Boolean::class,
        "cellular_download_size_warning_mb" to Int::class,
        "download_schedule_enabled" to Boolean::class,
        "download_schedule_start" to Int::class,
        "download_schedule_end" to Int::class,
        "download_schedule_wifi_only" to Boolean::class,
    )

    private fun expectedKey(name: String, type: KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        Int::class -> intPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    /**
     * The rows with no derived write — the mode row (its write is the
     * cross-key legacy mirror), the namespaced allow-list declaration, and
     * the declaration-only plain-read rows — named here so the write-through
     * pin's scope is legible (and pinned by
     * [every row rebuilds its legacy storage type] instead).
     */
    private val noDerivedWriteRows = setOf(
        "auto_download_mode",
        "auto_download_new_episodes",
        "auto_download_servers",
        "downloads_list_filter",
        "downloads_list_sort",
        "downloads_list_status_filter",
        "downloads_selection_hint_seen",
        "download_storage_location",
        "auto_delete_after_watch",
        "download_schedule_enabled",
        "download_schedule_wifi_only",
    )

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = reflectKeys(DownloadsStore.Keys).associateBy { it.name }
        val rows = DownloadsPreferenceSpecs.all.associateBy { it.keyName }

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
    fun `every row rebuilds its legacy storage type`() {
        // The no-derived-write rows (the mode's cross-key mirror, the
        // namespaced allow-list declaration, the declaration-only plain-read
        // rows) cannot carry the write-through pin, so the STORAGE pin is the
        // typed key each row rebuilds from its wire name (Preferences.Key
        // equality is class-sensitive — the home domain's storage-pin shape).
        DownloadsPreferenceSpecs.all.forEach { spec ->
            assertEquals(
                expectedKey(spec.keyName, expectedStoredType.getValue(spec.keyName)),
                spec.typedKey(),
                "storage drift on ${spec.keyName}",
            )
        }
    }

    @Test
    fun `every row's derived write stores its declared storage's value type`() {
        // Write each derived-write row's default through its derived write and
        // assert the stored value's runtime type matches the pinned legacy
        // storage table (the playback pin, scoped to the rows that carry an
        // encoding with a write — the rest are pinned by the storage-type
        // test above). The row-without-write set is pinned against the
        // declaration side so this scope cannot silently widen or shrink.
        val derivedWriteNames = DownloadsPreferenceSpecs.all
            .filter { it.encoding?.write != null }
            .map { it.keyName }
            .toSet()
        assertEquals(expectedStoredType.keys - noDerivedWriteRows, derivedWriteNames)

        val prefs = emptyPreferences().toMutablePreferences()
        DownloadsPreferenceSpecs.all
            .filter { it.keyName in derivedWriteNames }
            .forEach { spec -> spec.writeDefault(prefs) }
        val storedByName = prefs.asMap().entries.associateBy { it.key.name }

        assertEquals(derivedWriteNames, storedByName.keys)
        expectedStoredType
            .filterKeys { it in derivedWriteNames }
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
        val dataStore = TestDataStoreProvider.get()
        val store = DownloadsStore(dataStore, scope, ServerIdentityStore(dataStore, scope))
        val downloadKeys = store.resetKeysFor(PreferenceResetCategory.DOWNLOADS_NETWORK).map { it.name }
        // The exact keys the hand-written pre-spec reset list carried — the
        // canonical allow-list key included (its namespaced instances are
        // stripped by removeDynamicResetKeys, a behavioral test in
        // DownloadsStoreTest's orbit).
        assertEquals(expectedStoredType.keys.sorted(), downloadKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.DOWNLOADS_NETWORK }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `searchEntries is empty - the search faces stay the feature-side residuals`() {
        // No row declares a search face (the storage screen's downloads group
        // owns the domain's catalog rows), so the derived list is empty by
        // construction — and no entry id is declared twice (trivially true,
        // pinned so a future search-bearing row cannot land without this test
        // noticing the shape change).
        val entries = DownloadsPreferenceSpecs.searchEntries
        assertEquals(DownloadsPreferenceSpecs.all.mapNotNull { it.search }, entries)
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "a search id is declared twice")
    }

    @Test
    fun `every slice default equals its spec default`() {
        // The single-default guard: each [DownloadsSlice] property's default
        // reads its row's `.default`, so the value below IS the row's — if a
        // future edit restates a literal here (or on the slice) and the two
        // drift, this fails. The four banded rows keep their pre-band
        // declared defaults (3, 3, 3, 0), which is what the slice always
        // served. The legacy-only auto_download_new_episodes row has no
        // slice property by design.
        val slice = DownloadsSlice()
        assertEquals(DownloadsPreferenceSpecs.WIFI_ONLY_DOWNLOADS.default, slice.wifiOnlyDownloads)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOAD_CONNECTIONS.default, slice.downloadConnections)
        assertEquals(DownloadsPreferenceSpecs.MAX_CONCURRENT_DOWNLOADS.default, slice.maxConcurrentDownloads)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOAD_QUALITY.default, slice.downloadQuality)
        assertEquals(DownloadsPreferenceSpecs.SMART_DOWNLOADS_ENABLED.default, slice.smartDownloadsEnabled)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MODE.default, slice.autoDownloadMode)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_LOOKAHEAD.default, slice.autoDownloadLookahead)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_MAX_PER_PASS.default, slice.autoDownloadMaxPerPass)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_KEEP_DAYS.default, slice.autoDownloadKeepDays)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DOWNLOAD_SERVERS.default, slice.autoDownloadServers)
        assertEquals(DownloadsPreferenceSpecs.MAX_DOWNLOAD_STORAGE_GB.default, slice.maxDownloadStorageGb)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOADS_LIST_FILTER.default, slice.downloadsListFilter)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOADS_LIST_SORT.default, slice.downloadsListSort)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOADS_LIST_STATUS_FILTER.default, slice.downloadsListStatusFilter)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOADS_SELECTION_HINT_SEEN.default, slice.selectionHintSeen)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOAD_STORAGE_LOCATION.default, slice.downloadStorageLocation)
        assertEquals(DownloadsPreferenceSpecs.AUTO_DELETE_AFTER_WATCH.default, slice.autoDeleteAfterWatch)
        assertEquals(DownloadsPreferenceSpecs.CELLULAR_DOWNLOAD_SIZE_WARNING_MB.default, slice.cellularDownloadSizeWarningMb)
        assertEquals(DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_ENABLED.default, slice.downloadScheduleEnabled)
        assertEquals(
            DownloadScheduleWindow(
                startHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_START.default,
                endHour = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_END.default,
                wifiOnly = DownloadsPreferenceSpecs.DOWNLOAD_SCHEDULE_WIFI_ONLY.default,
            ),
            slice.downloadScheduleWindow,
        )
        // Totality: the 20 pinned properties are ALL the slice's fields (23
        // rows - the legacy-only auto_download_new_episodes with no slice
        // property - the two extra schedule-window rows folded into one
        // field). A field added without a spec-default pin above (and without
        // bumping this count) fails here. Statics (the @Serializable
        // Companion, Compose's $stable) don't count.
        val instanceFields = DownloadsSlice()::class.java.declaredFields
            .count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(20, instanceFields)
    }

    /** Writes [spec]'s default through its derived write (the default IS the row's T). */
    @Suppress("UNCHECKED_CAST")
    private fun PreferenceSpec<*>.writeDefault(prefs: MutablePreferences) {
        (this as PreferenceSpec<Any>).writeTo(prefs, default)
    }
}
