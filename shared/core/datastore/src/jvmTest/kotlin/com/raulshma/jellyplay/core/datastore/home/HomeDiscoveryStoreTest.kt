package com.raulshma.jellyplay.core.datastore.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.createUserPreferencesStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the home-discovery preference store, focusing on the per-user
 * `u_<userId>::` key namespacing (isolation across user switches, the one-time
 * global first-user-claims migration from the legacy flat keys, and the
 * pre-login no-namespace behaviour), the JSON-decoded section-type sets, the
 * one-shot legacy `home_hidden_library_section_ids` migration, factory reset
 * of dynamic namespaced keys, and backup restore into the active user's
 * namespace.
 *
 * Value assertions go through the pure [HomeDiscoveryStore.read] projection
 * over a directly-read snapshot (deterministic); the namespace migration is
 * invoked explicitly the same way the legacy hidden-library test drives its
 * migration (production runs it from the store's init). One bounded-wait test
 * covers the reactive `homeDiscovery` StateFlow re-derivation on user switch.
 */
class HomeDiscoveryStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: HomeDiscoveryStore
    private lateinit var identityStore: ServerIdentityStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            identityStore = ServerIdentityStore(dataStore, scope)
            store = HomeDiscoveryStore(dataStore, scope, identityStore)
            // Drain the Eagerly-cached slice so the cleared, signed-out state is
            // observed before each test writes + reads. No active user is set
            // here on purpose: tests activate users explicitly so the migration
            // marker is not claimed before a test seeds its legacy keys.
            store.homeDiscovery.first()
        }
    }

    @AfterTest
    fun tearDown() {
        // Stop this instance's Eagerly sharing + migration collectors so they
        // do not keep reacting to later tests' user switches on the shared
        // DataStore singleton.
        scope.cancel()
    }

    /** Switches the active user through the production seam (`active_user_id`). */
    private suspend fun activate(userId: String) {
        identityStore.setActiveUser(userId)
    }

    /** Deterministic slice read: pure projection over the committed snapshot. */
    private suspend fun slice(): HomeDiscoverySlice = store.read(dataStore.data.first())

    private suspend fun raw(): Preferences = dataStore.data.first()

    @Test
    fun `defaults when empty`() = runTest {
        val slice = slice()
        assertEquals(HomeMode.VIDEO, slice.homeMode)
        assertEquals(HomeSectionType.CONFIGURABLE.toSet(), slice.enabledHomeSectionTypes)
        assertEquals(HomeSectionType.CONFIGURABLE, slice.homeSectionOrder)
        assertTrue(slice.libraryHomeSectionOverrides.isEmpty())
        assertTrue(slice.pinnedHomeSections.isEmpty())
        assertEquals(ContinueWatchingClickBehavior.DETAILS, slice.continueWatchingClickBehavior)
        // Default off — current pinned behaviour until the user opts in.
        assertEquals(false, slice.hideTopHeaderOnScroll)
    }

    @Test
    fun `setHideTopHeaderOnScroll round-trips`() = runTest {
        activate("userA")
        store.setHideTopHeaderOnScroll(true)
        assertEquals(true, slice().hideTopHeaderOnScroll)
        store.setHideTopHeaderOnScroll(false)
        assertEquals(false, slice().hideTopHeaderOnScroll)
    }

    @Test
    fun `setEnabledHomeSectionTypes round-trips`() = runTest {
        activate("userA")
        val types = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP)
        store.setEnabledHomeSectionTypes(types)
        assertEquals(types, slice().enabledHomeSectionTypes)
    }

    @Test
    fun `setLastViewedSeason round-trips two series`() = runTest {
        activate("userA")
        store.setLastViewedSeason("seriesA", "season2")
        store.setLastViewedSeason("seriesB", "season5")
        val map = slice().lastViewedSeasonBySeries
        assertEquals(map["seriesA"], "season2")
        assertEquals(map["seriesB"], "season5")
        assertEquals(2, map.size)
    }

    @Test
    fun `setLastViewedSeason overwrites the same series`() = runTest {
        activate("userA")
        store.setLastViewedSeason("seriesA", "season1")
        store.setLastViewedSeason("seriesA", "season3")
        val map = slice().lastViewedSeasonBySeries
        assertEquals(map["seriesA"], "season3")
        assertEquals(1, map.size)
    }

    @Test
    fun `setLastViewedSeason JSON survives a cold re-read`() = runTest {
        activate("userA")
        store.setLastViewedSeason("seriesA", "season4")
        store.setLastViewedSeason("seriesB", "season1")
        // Read the raw on-disk value straight from the DataStore file (under
        // userA's namespace) and decode it exactly as a fresh store would,
        // proving the persisted JSON format round-trips.
        val raw = dataStore.data.first()[stringPreferencesKey("u_userA::last_viewed_season_by_series")]
        val decoded = PreferenceCodec.json.decodeFromString<Map<String, String>>(raw!!)
        assertEquals(decoded["seriesA"], "season4")
        assertEquals(decoded["seriesB"], "season1")
        assertEquals(2, decoded.size)
    }

    @Test
    fun `lastViewedSeasonBySeries defaults to empty`() = runTest {
        assertTrue(slice().lastViewedSeasonBySeries.isEmpty())
    }

    @Test
    fun `legacy hidden library section ids migrate to overrides under the active user`() = runTest {
        // Seed the legacy all-or-nothing "hide library from home" key, activate
        // a user, then run the one-shot namespace migration explicitly
        // (production runs it from the store's init; calling it directly
        // avoids init/stateIn ordering races). The legacy conversion is folded
        // into that migration, so its output is claimed by the same pass.
        dataStore.edit {
            it[stringPreferencesKey("home_hidden_library_section_ids")] = """["lib_a","lib_b"]"""
        }
        activate("userA")
        store.ensureNamespacedMigration("userA")
        val overrides = slice().libraryHomeSectionOverrides
        assertEquals(setOf("lib_a", "lib_b"), overrides.keys)
        assertEquals(
            setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
            overrides["lib_a"],
        )
        // The namespaced copy exists and the legacy source key is dropped.
        assertNotNull(raw()[stringPreferencesKey("u_userA::home_library_section_overrides")])
        assertNull(raw()[stringPreferencesKey("home_hidden_library_section_ids")])
    }

    // ------------------------------------------------------------------
    // Per-user keying
    // ------------------------------------------------------------------

    @Test
    fun `user B does not inherit user A's configuration`() = runTest {
        activate("userA")
        store.setHomeSectionOrder(listOf(HomeSectionType.NEXT_UP))
        store.setNextUpExcludedSeriesIds(setOf("seriesA1"))
        store.hideCwItem("cwA1")
        store.ensureNamespacedMigration("userA")
        val aSlice = slice()
        assertEquals(setOf("seriesA1"), aSlice.nextUpExcludedSeriesIds)
        assertEquals(setOf("cwA1"), aSlice.hiddenCwItemIds)
        assertEquals(HomeSectionType.NEXT_UP, aSlice.homeSectionOrder.first())

        // Switch to B: everything falls back to defaults — B starts clean.
        activate("userB")
        store.ensureNamespacedMigration("userB")
        val bSlice = slice()
        assertTrue(bSlice.nextUpExcludedSeriesIds.isEmpty())
        assertTrue(bSlice.hiddenCwItemIds.isEmpty())
        assertEquals(HomeSectionType.CONFIGURABLE, bSlice.homeSectionOrder)

        // Switch back to A: A's values are intact.
        activate("userA")
        val aAgain = slice()
        assertEquals(setOf("seriesA1"), aAgain.nextUpExcludedSeriesIds)
        assertEquals(setOf("cwA1"), aAgain.hiddenCwItemIds)
        assertEquals(HomeSectionType.NEXT_UP, aAgain.homeSectionOrder.first())
    }

    @Test
    fun `homeDiscovery flow re-derives on user switch`() = runTest {
        // The reactive StateFlow must serve each active user their own slice.
        // Bounded wait: the committed state is asserted separately below.
        activate("userA")
        store.setHomeMode(HomeMode.MUSIC)
        val aFlow = withTimeoutOrNull(10_000) { store.homeDiscovery.first { it.homeMode == HomeMode.MUSIC } }
        assertNotNull(aFlow, "user A's slice never reached homeDiscovery")

        activate("userB")
        val bFlow = withTimeoutOrNull(10_000) { store.homeDiscovery.first { it.homeMode == HomeMode.VIDEO } }
        assertNotNull(bFlow, "user B's default slice never reached homeDiscovery")

        // And the underlying keys are per-user on disk.
        assertEquals(HomeMode.MUSIC.name, raw()[stringPreferencesKey("u_userA::home_mode")])
        assertNull(raw()[stringPreferencesKey("u_userB::home_mode")])
    }

    @Test
    fun `legacy flat keys are claimed by the first active user`() = runTest {
        // Legacy (pre-namespacing) install: flat keys, no marker.
        dataStore.edit {
            it[stringPreferencesKey("home_section_order")] = """["NEXT_UP"]"""
            it[stringPreferencesKey("next_up_excluded_series_ids")] = """["legacySeries"]"""
            it[booleanPreferencesKey("home_hero_enabled")] = false
        }
        activate("userA")
        store.ensureNamespacedMigration("userA")
        val aSlice = slice()
        assertEquals(setOf("legacySeries"), aSlice.nextUpExcludedSeriesIds)
        assertEquals(false, aSlice.homeHeroEnabled)
        assertEquals(HomeSectionType.NEXT_UP, aSlice.homeSectionOrder.first())

        // The u_A:: copies exist and the global marker is set.
        assertEquals(raw()[stringPreferencesKey("u_userA::next_up_excluded_series_ids")], """["legacySeries"]""")
        assertEquals(false, raw()[booleanPreferencesKey("u_userA::home_hero_enabled")])
        assertEquals(true, raw()[booleanPreferencesKey("home_ns_migrated")])

        // A later user does NOT inherit the legacy values.
        activate("userB")
        store.ensureNamespacedMigration("userB")
        val bSlice = slice()
        assertTrue(bSlice.nextUpExcludedSeriesIds.isEmpty())
        assertEquals(HomeSectionType.CONFIGURABLE, bSlice.homeSectionOrder)
        assertEquals(true, bSlice.homeHeroEnabled)
        assertNull(raw()[stringPreferencesKey("u_userB::next_up_excluded_series_ids")])
    }

    @Test
    fun `legacy string-typed bool and int slots are parsed into typed namespaced copies`() = runTest {
        // Pre-typed-migration install: the bool/int home keys still hold their
        // legacy STRING form, and the typed-key migration (an unordered
        // concurrent init launch) has not converted them when the namespace
        // edit runs. The copy must parse the strings into the TYPED namespaced
        // slot — a raw string copy would read as the default once the global
        // typed-migration flag disables the read-side string fallback.
        dataStore.edit {
            it[stringPreferencesKey("home_hero_enabled")] = "false"
            it[stringPreferencesKey("next_up_max_days")] = "14"
        }
        activate("userA")
        store.ensureNamespacedMigration("userA")

        // The slice reflects the legacy values...
        val slice = slice()
        assertEquals(false, slice.homeHeroEnabled)
        assertEquals(14, slice.nextUpMaxDays)
        // ...and the namespaced copies are TYPED, not string (a typed read of a
        // string slot would throw ClassCastException, failing this test).
        assertEquals(false, raw()[booleanPreferencesKey("u_userA::home_hero_enabled")])
        assertEquals(14, raw()[intPreferencesKey("u_userA::next_up_max_days")])
    }

    @Test
    fun `migration is idempotent and never clobbers newer values`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("next_up_excluded_series_ids")] = """["legacySeries"]"""
        }
        activate("userA")
        store.ensureNamespacedMigration("userA")
        assertEquals(setOf("legacySeries"), slice().nextUpExcludedSeriesIds)

        // A writes a newer value post-migration, then the migration re-runs —
        // it must fast-path on the global marker and keep the newer value.
        store.setNextUpExcludedSeriesIds(setOf("newer"))
        store.ensureNamespacedMigration("userA")
        assertEquals(setOf("newer"), slice().nextUpExcludedSeriesIds)

        // Other users' reads never re-claim either.
        activate("userB")
        store.ensureNamespacedMigration("userB")
        assertTrue(slice().nextUpExcludedSeriesIds.isEmpty())
        activate("userA")
        store.ensureNamespacedMigration("userA")
        assertEquals(setOf("newer"), slice().nextUpExcludedSeriesIds)
        assertEquals(true, raw()[booleanPreferencesKey("home_ns_migrated")])
    }

    @Test
    fun `no active user reads defaults and writes are skipped`() = runTest {
        // Read: the pre-login slice is exactly the default projection.
        assertEquals(HomeDiscoverySlice(), store.homeDiscovery.first())
        assertEquals(HomeDiscoverySlice(), slice())

        // Write: no exception, and nothing lands anywhere (no namespace
        // exists pre-login — home config is unreachable before sign-in).
        store.setHomeMode(HomeMode.MUSIC)
        store.setHomeSectionOrder(listOf(HomeSectionType.NEXT_UP))
        val raw = raw()
        assertTrue(raw.asMap().keys.none { it.name.startsWith("u_") }, "expected no namespaced keys, got: " + raw.asMap().keys.joinToString { it.name })
        assertEquals(HomeMode.VIDEO, slice().homeMode)
    }

    @Test
    fun `factory reset clears both users' namespaced keys, legacy keys and the marker`() = runTest {
        // Two users with namespaced state, plus a leftover legacy flat key.
        activate("userA")
        store.setHomeMode(HomeMode.MUSIC)
        store.setNextUpExcludedSeriesIds(setOf("a1"))
        store.ensureNamespacedMigration("userA")
        activate("userB")
        store.setHomeMode(HomeMode.MUSIC)
        store.ensureNamespacedMigration("userB")
        dataStore.edit { it[stringPreferencesKey("home_mode")] = HomeMode.MUSIC.name }
        // A user id containing "::" (setActiveUser input is unvalidated) must
        // not defeat the stripping: the canonical suffix sits after the LAST
        // separator, so this key parses as user "a::b" / canonical home_mode.
        dataStore.edit { it[stringPreferencesKey("u_a::b::home_mode")] = HomeMode.MUSIC.name }

        // Factory reset drives the facade's HOME_DISCOVERY category.
        createUserPreferencesStore(scope, dataStore).resetCategory(PreferenceResetCategory.HOME_DISCOVERY)

        val raw = raw()
        assertTrue(raw.asMap().keys.none { it.name.startsWith("u_") }, "expected no namespaced keys, got: " + raw.asMap().keys.joinToString { it.name })
        assertNull(raw[stringPreferencesKey("u_a::b::home_mode")])
        assertNull(raw[stringPreferencesKey("home_mode")])
        assertNull(raw[stringPreferencesKey("next_up_excluded_series_ids")])
        assertNull(raw[booleanPreferencesKey("home_ns_migrated")])

        // The still-active user (B) reads defaults again.
        assertEquals(HomeMode.VIDEO, slice().homeMode)
    }

    @Test
    fun `backup restore applies canonical payload into the current user's namespace`() = runTest {
        activate("userA")
        // A v1 backup carries canonical (user-portable) values; restoring it
        // writes into whoever is active — userA here.
        store.restorePreferences(
            UserPreferences(homeMode = HomeMode.MUSIC, nextUpExcludedSeriesIds = setOf("fromBackup")),
        )
        val restored = slice()
        assertEquals(HomeMode.MUSIC, restored.homeMode)
        assertEquals(setOf("fromBackup"), restored.nextUpExcludedSeriesIds)
        assertEquals(HomeMode.MUSIC.name, raw()[stringPreferencesKey("u_userA::home_mode")])

        // Another user stays untouched by the restore.
        activate("userB")
        val bSlice = slice()
        assertEquals(HomeMode.VIDEO, bSlice.homeMode)
        assertTrue(bSlice.nextUpExcludedSeriesIds.isEmpty())
        assertNull(raw()[stringPreferencesKey("u_userB::home_mode")])
    }
}
