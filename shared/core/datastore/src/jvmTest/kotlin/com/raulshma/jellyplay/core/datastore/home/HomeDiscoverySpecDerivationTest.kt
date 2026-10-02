package com.raulshma.jellyplay.core.datastore.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the derivation integrity for the home discovery domain: the
 * [HomeDiscoveryPreferenceSpecs] rows are the single declaration of every
 * key's wire name / storage type / default / reset category, and
 * `HomeDiscoveryStore` derives its `Keys` members, its per-user key set (via
 * the spec-side key-rebuild hook), its migration copy lists, and its reset
 * lists from them — so a wire-name, storage-type or reset-category drift would
 * silently change what is read from disk or wiped on reset. These tests pin
 * the derivation to the exact legacy strings the pre-spec hand-written store
 * carried; the behavioral safety net (namespacing, the first-user-claims
 * migration, section-config commands) stays in [HomeDiscoveryStoreTest].
 */
class HomeDiscoverySpecDerivationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: HomeDiscoveryStore
    private lateinit var identityStore: ServerIdentityStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            identityStore = ServerIdentityStore(dataStore, scope)
            store = HomeDiscoveryStore(dataStore, scope, identityStore)
            store.homeDiscovery.first()
        }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun activate(userId: String) = identityStore.setActiveUser(userId)

    private suspend fun raw(): Preferences = dataStore.data.first()

    /**
     * The legacy wire name → stored value type each slot must keep — the exact
     * expectations the hand-written pre-spec store encoded at every read/write
     * site (the JSON blobs and enum slots are STRING-typed on disk).
     */
    private val expectedStoredType: Map<String, kotlin.reflect.KClass<*>> = mapOf(
        "home_mode" to String::class,
        "home_hero_enabled" to Boolean::class,
        "home_backdrop_enabled" to Boolean::class,
        "home_enabled_section_types" to String::class,
        "home_enabled_section_types_version" to Int::class,
        "home_section_order" to String::class,
        "home_library_section_overrides" to String::class,
        "home_hidden_library_section_ids" to String::class,
        "pinned_home_sections" to String::class,
        "home_discover_rows" to String::class,
        "home_layout_presets" to String::class,
        "continue_watching_click_behavior" to String::class,
        "show_unwatched_badge" to Boolean::class,
        "hide_watched_items" to Boolean::class,
        "show_watched_checkmark" to Boolean::class,
        "show_external_ratings" to Boolean::class,
        "merge_continue_watching_next_up" to Boolean::class,
        "next_up_max_days" to Int::class,
        "next_up_rewatching" to Boolean::class,
        "classic_rows" to Boolean::class,
        "next_up_excluded_series_ids" to String::class,
        "hidden_cw_item_ids" to String::class,
        "last_viewed_season_by_series" to String::class,
        "show_clock_on_home" to Boolean::class,
        "show_settings_in_home_search" to Boolean::class,
        "hide_top_header_on_scroll" to Boolean::class,
        "home_ns_migrated" to Boolean::class,
    )

    private fun expectedKey(name: String, type: kotlin.reflect.KClass<*>): Preferences.Key<*> = when (type) {
        Boolean::class -> booleanPreferencesKey(name)
        Int::class -> intPreferencesKey(name)
        String::class -> stringPreferencesKey(name)
        else -> error("unexpected storage type for $name")
    }

    @Test
    fun `every spec row's derived key matches the store's declared Keys`() {
        val declared = com.raulshma.jellyplay.core.datastore.reflectKeys(HomeDiscoveryStore.Keys)
            .associateBy { it.name }
        val rows = HomeDiscoveryPreferenceSpecs.all.associateBy { it.keyName }

        // Same key set: a row without a Keys member (or vice versa) means the
        // store and its declaration have drifted.
        assertEquals(expectedStoredType.keys, rows.keys)
        assertEquals(rows.keys, declared.keys)

        rows.forEach { (name, spec) ->
            assertEquals(spec.typedKey(), declared.getValue(name), "key drift on $name")
        }
    }

    @Test
    fun `every row rebuilds its legacy storage type`() {
        // The JSON/enum rows are declaration-only rows (the store's
        // per-user reader supplies their codec), so the write-type pin of the
        // playback test does not apply — the STORAGE pin is the typed key each
        // row rebuilds from its wire name (Preferences.Key equality is
        // class-sensitive, so this pins the slot type per row).
        HomeDiscoveryPreferenceSpecs.all.forEach { spec ->
            assertEquals(
                expectedKey(spec.keyName, expectedStoredType.getValue(spec.keyName)),
                spec.typedKey(),
                "storage drift on ${spec.keyName}",
            )
        }
    }

    @Test
    fun `resetKeysFor returns exactly the HOME_DISCOVERY-category keys`() {
        val storeKeys = store.resetKeysFor(PreferenceResetCategory.HOME_DISCOVERY).map { it.name }
        // The exact 27 keys the hand-written pre-spec reset list carried (the
        // 25 legacy/canonical flat keys + the hidden-library migration source
        // + the global namespace marker).
        assertEquals(expectedStoredType.keys.sorted(), storeKeys.sorted())
        PreferenceResetCategory.entries
            .filter { it != PreferenceResetCategory.HOME_DISCOVERY }
            .forEach { category ->
                assertTrue(store.resetKeysFor(category).isEmpty(), "$category unexpectedly non-empty")
            }
    }

    @Test
    fun `the namespace migration copy lists derive from the rows by storage`() {
        // legacyKeys: every row except the global marker.
        assertEquals(
            expectedStoredType.keys.filter { it != "home_ns_migrated" }.sorted(),
            HomeDiscoveryPreferenceSpecs.legacyKeys.map { it.name }.sorted(),
        )

        val migrationNames = HomeDiscoveryPreferenceSpecs.migrationRows.map { it.keyName }
        // The hidden-library key is a migration SOURCE only, and the marker is
        // global — neither is ever a copy destination.
        assertFalse("home_hidden_library_section_ids" in migrationNames)
        assertFalse("home_ns_migrated" in migrationNames)
        assertEquals(expectedStoredType.size - 2, migrationNames.size)

        // The per-type copy lists, exactly the hand-written predecessor's
        // three lists (booleans, ints, strings — enum slots included).
        assertEquals(
            listOf(
                "classic_rows",
                "hide_top_header_on_scroll",
                "hide_watched_items",
                "home_backdrop_enabled",
                "home_hero_enabled",
                "merge_continue_watching_next_up",
                "next_up_rewatching",
                "show_clock_on_home",
                "show_external_ratings",
                "show_settings_in_home_search",
                "show_unwatched_badge",
                "show_watched_checkmark",
            ).sorted(),
            HomeDiscoveryPreferenceSpecs.booleanCopyRows.map { it.keyName }.sorted(),
        )
        assertEquals(
            listOf("home_enabled_section_types_version", "next_up_max_days").sorted(),
            HomeDiscoveryPreferenceSpecs.intCopyRows.map { it.keyName }.sorted(),
        )
        assertEquals(
            listOf(
                "continue_watching_click_behavior",
                "home_discover_rows",
                "home_enabled_section_types",
                "home_layout_presets",
                "home_library_section_overrides",
                "home_mode",
                "home_section_order",
                "hidden_cw_item_ids",
                "last_viewed_season_by_series",
                "next_up_excluded_series_ids",
                "pinned_home_sections",
            ).sorted(),
            HomeDiscoveryPreferenceSpecs.stringCopyRows.map { it.keyName }.sorted(),
        )
    }

    // ------------------------------------------------------------------
    // The per-user key-rebuild hook
    // ------------------------------------------------------------------

    @Test
    fun `the per-user hook rebuilds namespaced keys from the one declared wire name`() {
        val userId = "user7"
        assertEquals("u_user7::home_hero_enabled", HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.userKey(userId).name)
        assertEquals(
            booleanPreferencesKey("u_user7::home_hero_enabled"),
            HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.userKey(userId),
        )
        assertEquals(
            intPreferencesKey("u_user7::next_up_max_days"),
            HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.userKey(userId),
        )
        assertEquals(
            stringPreferencesKey("u_user7::home_section_order"),
            HomeDiscoveryPreferenceSpecs.HOME_SECTION_ORDER.rawUserKey(userId),
        )
        // The grammar is UserNamespacedKeys' — including the last-:: split for
        // ::-bearing user ids.
        assertEquals(
            UserNamespacedKeys.name("a::b", "home_mode"),
            HomeDiscoveryPreferenceSpecs.HOME_MODE.rawUserKey("a::b").name,
        )
    }

    @Test
    fun `readBoolForUser reads the namespaced slot with the namespaced legacy string fallback`() {
        val typed = emptyPreferences().toMutablePreferences()
        typed[booleanPreferencesKey("u_u::show_clock_on_home")] = true
        assertEquals(true, HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.readBoolForUser(typed, "u"))

        // Absent typed slot, legacy STRING value under the NAMESPACED name —
        // the fallback consults key.name, never the canonical flat name.
        val legacy = emptyPreferences().toMutablePreferences()
        legacy[stringPreferencesKey("u_u::home_hero_enabled")] = "false"
        assertEquals(false, HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.readBoolForUser(legacy, "u"))
        legacy[stringPreferencesKey("u_u::home_hero_enabled")] = "true"
        assertEquals(true, HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.readBoolForUser(legacy, "u"))

        // Absent everywhere → the row default.
        assertEquals(false, HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME.readBoolForUser(emptyPreferences(), "u"))
        assertEquals(true, HomeDiscoveryPreferenceSpecs.HOME_HERO_ENABLED.readBoolForUser(emptyPreferences(), "u"))
    }

    @Test
    fun `readIntForUser and readEnumForUser parse the namespaced slots`() {
        val legacy = emptyPreferences().toMutablePreferences()
        legacy[stringPreferencesKey("u_u::next_up_max_days")] = "14"
        assertEquals(14, HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.readIntForUser(legacy, "u"))
        assertEquals(0, HomeDiscoveryPreferenceSpecs.NEXT_UP_MAX_DAYS.readIntForUser(emptyPreferences(), "u"))

        val enumPrefs = emptyPreferences().toMutablePreferences()
        enumPrefs[stringPreferencesKey("u_u::home_mode")] = "MUSIC"
        assertEquals(HomeMode.MUSIC, HomeDiscoveryPreferenceSpecs.HOME_MODE.readEnumForUser(enumPrefs, "u"))
        // Corrupt or absent falls back to the row default (the enumRow
        // encoding's toEnumOrNull seam).
        val corrupt = emptyPreferences().toMutablePreferences()
        corrupt[stringPreferencesKey("u_u::home_mode")] = "nonsense"
        assertEquals(HomeMode.VIDEO, HomeDiscoveryPreferenceSpecs.HOME_MODE.readEnumForUser(corrupt, "u"))
        assertEquals(HomeMode.VIDEO, HomeDiscoveryPreferenceSpecs.HOME_MODE.readEnumForUser(emptyPreferences(), "u"))
    }

    // ------------------------------------------------------------------
    // One knob's full row, pinned end-to-end through the store
    // ------------------------------------------------------------------

    @Test
    fun `show clock on home full row is derived from its spec`() = runTest {
        val row = HomeDiscoveryPreferenceSpecs.SHOW_CLOCK_ON_HOME

        // Key: the row's rebuilt canonical key IS the store's Keys member, and
        // the per-user hook rebuilds the namespaced slot from the same name.
        assertEquals("show_clock_on_home", row.keyName)
        assertEquals(HomeDiscoveryStore.Keys.SHOW_CLOCK_ON_HOME, row.typedKey())
        assertEquals(
            booleanPreferencesKey(UserNamespacedKeys.name("userA", row.keyName)),
            row.userKey("userA"),
        )
        // Default: served on absence.
        assertEquals(false, row.default)

        // Write-through: the setter writes the row's namespaced slot, the read
        // projection reads it back through the row's per-user encoding.
        activate("userA")
        store.setShowClockOnHome(true)
        assertEquals(true, raw()[booleanPreferencesKey("u_userA::show_clock_on_home")])
        assertEquals(true, store.read(raw()).showClockOnHome)

        // Restore: writes the field back through the same row.
        store.restore(HomeDiscoverySlice(showClockOnHome = false))
        assertEquals(false, raw()[booleanPreferencesKey("u_userA::show_clock_on_home")])

        // Reset: participates in the HOME_DISCOVERY category via its row.
        assertTrue(
            store.resetKeysFor(PreferenceResetCategory.HOME_DISCOVERY).contains(row.typedKey()),
        )
    }
}
