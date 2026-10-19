package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.HomeSectionType
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ONE round-trip seam test for every real [ProfileSyncAdapter] (all nine run
 * through the INTERFACE, never the concrete type — the sync-adapter-base
 * contract, GLOSSARY.md): seed → snapshot → fresh-mirror dirty detection →
 * markSynced → local divergence (a value change, or a removal where the
 * namespace roams tombstones) → adoption back through the SPI → the outbound
 * tombstone face and its adopted clear for the tombstone adapters. Each
 * adapter's dedicated suite owns the per-namespace payload grammar; this
 * suite pins only what every adapter owes the engine.
 */
class MirrorProfileSyncAdapterRoundTripTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var prefsDataStore: DataStore<Preferences>
    private lateinit var homeDataStore: DataStore<Preferences>
    private lateinit var homeStore: HomeDiscoveryStore
    private lateinit var integrationsDataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
            // Production-shaped: ONE user-prefs store hosts every mirror.
            mirrorStore = tempDataStore("mirror")
            prefsDataStore = tempDataStore("prefs")
            homeDataStore = tempDataStore("home").also { it.edit { prefs -> prefs.clear() } }
            val identityStore = ServerIdentityStore(homeDataStore, scope)
            homeStore = HomeDiscoveryStore(homeDataStore, scope, identityStore)
            identityStore.setActiveUser(USER)
            integrationsDataStore = tempDataStore("integrations").also { it.edit { prefs -> prefs.clear() } }
        }
    }

    @AfterTest
    fun teardown() {
        database.close()
        scope.cancel()
    }

    private fun tempDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-adapter-roundtrip-test").apply { mkdirs() }
            File(dir, "$name-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }

    /** Bounded wait for the home store's Eagerly-cached projection (the cw/homelayout suite idiom). */
    private suspend fun settleHome(predicate: (com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice) -> Boolean) {
        withTimeoutOrNull(5_000) {
            homeStore.homeDiscovery.first(predicate)
        } ?: error("homeDiscovery never settled")
    }

    // ------------------------------------------------------------------
    // the cases: every real adapter, through the interface
    // ------------------------------------------------------------------

    /**
     * One adapter's round-trip script. [removal] selects the divergence kind:
     * false = a VALUE change at [mutatedKey] (resolved by adopting the synced
     * value back), true = the key's local REMOVAL (resolved by
     * [ProfileSyncAdapter.deleteRemote]). [tombstoneFace] is non-null exactly
     * for the tombstone-roaming namespaces: it performs one more local delete
     * of an already-synced key.
     */
    private class Case(
        val name: String,
        val adapter: ProfileSyncAdapter,
        val seed: suspend () -> Unit,
        val keys: Set<String>,
        val mutatedKey: String,
        val mutate: suspend () -> Unit,
        val removal: Boolean,
        /** Settles the owning projection after an applyRemote adoption (StateFlow-backed stores). */
        val afterAdopt: (suspend (JsonElement) -> Unit)? = null,
        /** Performs one more local delete of an already-synced key (null = value-only namespace). */
        val tombstoneFace: (suspend (ProfileSyncAdapter) -> Unit)? = null,
        val tombstoneKey: String? = null,
    )

    private fun cases(): List<Case> {
        val bookmarkDao = database.bookBookmarkDao()
        val annotationDao = database.bookAnnotationDao()
        val historyDao = database.searchHistoryDao()
        val smartDao = database.smartPlaylistDao()
        val moodDao = database.moodPlaylistDao()
        val itemPrefsDao = database.itemPlaybackPreferenceDao()
        val sha1 = { text: String ->
            MessageDigest.getInstance("SHA-1")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
        return listOf(
            Case(
                name = "prefs",
                adapter = JellyPlayPreferencesSyncAdapter(prefsDataStore),
                seed = { prefsDataStore.edit { it[stringPreferencesKey("theme")] = "dark" } },
                keys = setOf("theme"),
                mutatedKey = "theme",
                mutate = { prefsDataStore.edit { it[stringPreferencesKey("theme")] = "light" } },
                removal = false,
            ),
            Case(
                name = "books",
                adapter = JellyPlayBookmarksSyncAdapter(bookmarkDao, mirrorStore),
                seed = { bookmarkDao.upsert(BookBookmarkEntity(itemId = "book-1", positionTicks = 12_345L, cfi = "epubcfi(/6/4)", chapterLabel = "Chapter 1", createdAt = 1_000L)) },
                keys = setOf("book-1/12345"),
                mutatedKey = "book-1/12345",
                mutate = {
                    bookmarkDao.deleteAtPosition("book-1", 12_345L)
                    bookmarkDao.upsert(BookBookmarkEntity(itemId = "book-1", positionTicks = 12_345L, cfi = "epubcfi(/6/4)", chapterLabel = "Renamed", createdAt = 1_000L))
                },
                removal = false,
                tombstoneFace = { bookmarkDao.deleteAtPosition("book-1", 12_345L) },
                tombstoneKey = "book-1/12345",
            ),
            Case(
                name = "reader",
                adapter = JellyPlayReaderSyncAdapter(annotationDao, mirrorStore),
                seed = {
                    annotationDao.upsert(
                        BookAnnotationEntity(itemId = "book-1", cfi = "epubcfi(/6/4)", style = "HIGHLIGHT", color = "YELLOW", anchorText = "text", note = null, chapterLabel = "Chapter 1", createdAt = 1L, updatedAt = 1L),
                    )
                },
                keys = setOf("ann/book-1"),
                mutatedKey = "ann/book-1",
                mutate = {
                    annotationDao.deleteByItemId("book-1")
                    annotationDao.upsert(BookAnnotationEntity(itemId = "book-1", cfi = "epubcfi(/6/4)", style = "HIGHLIGHT", color = "YELLOW", anchorText = "text", note = null, chapterLabel = "Chapter 1", createdAt = 1L, updatedAt = 2L))
                },
                removal = false,
                tombstoneFace = { annotationDao.deleteByItemId("book-1") },
                tombstoneKey = "ann/book-1",
            ),
            Case(
                name = "search",
                adapter = JellyPlaySearchHistorySyncAdapter(historyDao, mirrorStore, userIdProvider = { USER }),
                seed = { historyDao.insertAndEvict(SearchHistoryEntity(query = "query-alpha", userId = USER, searchedAt = 10L)) },
                keys = setOf(sha1("query-alpha")),
                mutatedKey = sha1("query-alpha"),
                mutate = { historyDao.insertAndEvict(SearchHistoryEntity(query = "query-alpha", userId = USER, searchedAt = 20L)) },
                removal = false,
                tombstoneFace = {
                    val row = historyDao.getRecent(USER, 50).first().single()
                    historyDao.deleteById(row.id)
                },
                tombstoneKey = sha1("query-alpha"),
            ),
            Case(
                name = "cw",
                adapter = JellyPlayCwSyncAdapter(homeStore, mirrorStore),
                seed = {
                    homeStore.hideCwItem("cw-1")
                    settleHome { it.hiddenCwItemIds == setOf("cw-1") }
                },
                keys = setOf("hidden/cw-1"),
                mutatedKey = "hidden/cw-1",
                // A local un-hide is a key REMOVAL (the value face carries no
                // payload to change) — the divergence shows as the tombstone.
                mutate = {
                    homeStore.unhideCwItem("cw-1")
                    settleHome { it.hiddenCwItemIds.isEmpty() }
                },
                removal = true,
                tombstoneFace = { syncAdapter ->
                    // Re-hide (the key must be the SYNCED baseline again
                    // before its un-hide reads as an outbound tombstone).
                    homeStore.hideCwItem("cw-1")
                    settleHome { it.hiddenCwItemIds == setOf("cw-1") }
                    syncAdapter.markSynced(syncAdapter.snapshot())
                    // The local delete under test.
                    homeStore.unhideCwItem("cw-1")
                    settleHome { it.hiddenCwItemIds.isEmpty() }
                },
                tombstoneKey = "hidden/cw-1",
            ),
            Case(
                name = "playlists",
                adapter = JellyPlayPlaylistsSyncAdapter(smartDao, moodDao, mirrorStore),
                seed = {
                    smartDao.insert(SmartPlaylistEntity(id = "s1", name = "Smart", criteriaJson = "{}"))
                    moodDao.insert(MoodPlaylistEntity(id = "m1", name = "Mood", emoji = "🙂", description = "", genreKeywordsJson = "[]"))
                    moodDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "s1"))
                },
                keys = setOf("smart/s1", "mood/m1", "moodpref/s1"),
                mutatedKey = "mood/m1",
                mutate = { moodDao.insert(MoodPlaylistEntity(id = "m1", name = "Mood renamed", emoji = "🙂", description = "", genreKeywordsJson = "[]")) },
                removal = false,
                tombstoneFace = { smartDao.deleteById("s1") },
                tombstoneKey = "smart/s1",
            ),
            Case(
                name = "itemprefs",
                adapter = JellyPlayItemPrefsSyncAdapter(itemPrefsDao, mirrorStore),
                seed = {
                    itemPrefsDao.upsert(
                        ItemPlaybackPreferenceEntity(scope = "ITEM", key = "item-1", audioLanguage = null, subtitleLanguage = "en", subtitleDisabled = null, updatedAt = 1L),
                    )
                },
                keys = setOf("ITEM/item-1"),
                mutatedKey = "ITEM/item-1",
                mutate = {
                    itemPrefsDao.upsert(
                        ItemPlaybackPreferenceEntity(scope = "ITEM", key = "item-1", audioLanguage = null, subtitleLanguage = "de", subtitleDisabled = null, updatedAt = 2L),
                    )
                },
                removal = false,
                tombstoneFace = { itemPrefsDao.deleteByKey("ITEM", "item-1") },
                tombstoneKey = "ITEM/item-1",
            ),
            Case(
                name = "homelayout",
                adapter = JellyPlayHomeLayoutSyncAdapter(homeStore, mirrorStore),
                seed = { settleHome { true } },
                keys = setOf(
                    "home_enabled_section_types",
                    "home_section_order",
                    "home_library_section_overrides",
                    "pinned_home_sections",
                    "home_discover_rows",
                    "home_layout_presets",
                ),
                mutatedKey = "home_enabled_section_types",
                mutate = {
                    homeStore.setEnabledHomeSectionTypes(setOf(HomeSectionType.LIVE_TV))
                    settleHome { it.enabledHomeSectionTypes == setOf(HomeSectionType.LIVE_TV) }
                },
                removal = false,
                // The store's own setter is the adoption path — the projection
                // must land the adopted set before the snapshot re-reads.
                afterAdopt = { value ->
                    val names = (value as kotlinx.serialization.json.JsonArray)
                        .map { (it as JsonPrimitive).content }.toSet()
                    settleHome { slice -> slice.enabledHomeSectionTypes.map { it.name }.toSet() == names }
                },
            ),
            Case(
                name = "integrations",
                adapter = run {
                    val secureStorage = FakeSecureKeyValueStorage()
                    JellyPlayIntegrationsSyncAdapter(
                        seerrPreferencesStore = SeerrPreferencesStore(
                            dataStore = integrationsDataStore,
                            secureCredentialsStore = SeerrSecureCredentialsStore(secureStorage),
                            externalScope = scope,
                        ),
                        arrPreferencesStore = ArrPreferencesStore(
                            dataStore = integrationsDataStore,
                            secureCredentialsStore = ArrSecureCredentialsStore(secureStorage),
                            externalScope = scope,
                        ),
                        subtitleProviderPreferencesStore = SubtitleProviderPreferencesStore(
                            dataStore = integrationsDataStore,
                            secureCredentialsStore = SubtitleProviderSecureCredentialsStore(secureStorage),
                        ),
                        mirrorStore = mirrorStore,
                    )
                },
                seed = { integrationsDataStore.edit { it[booleanPreferencesKey("seerr_enabled")] = true } },
                keys = setOf("seerr_enabled"),
                mutatedKey = "seerr_enabled",
                mutate = { integrationsDataStore.edit { it[booleanPreferencesKey("seerr_enabled")] = false } },
                removal = false,
            ),
        )
    }

    /** Minimal in-memory stand-in for the OS-encrypted key-value store (the integrations suite idiom). */
    private class FakeSecureKeyValueStorage : SecureKeyValueStorage {
        val raw = mutableMapOf<String, String>()
        override fun getString(key: String, defValue: String?): String? = raw[key] ?: defValue
        override fun putString(key: String, value: String?) {
            if (value == null) raw.remove(key) else raw[key] = value
        }

        override fun remove(key: String) {
            raw.remove(key)
        }
    }

    // ------------------------------------------------------------------
    // the parameterized round trip
    // ------------------------------------------------------------------

    @Test
    fun everyAdapter_roundTripsThroughTheSpi() = runBlocking {
        cases().forEach { case ->
            val adapter = case.adapter
            assertEquals(case.name, adapter.namespace, "the adapter must own its namespace")

            // (a) seed → snapshot carries the keys; a fresh mirror reads everything dirty.
            case.seed()
            val synced = adapter.snapshot()
            assertTrue(case.keys.all { it in synced.keys }, "${case.name}: seeded keys must snapshot")
            assertEquals(synced.keys, adapter.dirtyValues(synced).keys, "${case.name}: a fresh mirror reads everything dirty")

            // (b) markSynced → clean, nothing deleted.
            adapter.markSynced(synced)
            assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty(), "${case.name}: marked keys stop reading dirty")
            assertTrue(adapter.deletedKeys(adapter.snapshot()).isEmpty(), "${case.name}: nothing deleted on a fresh cycle")

            // (c) local divergence at the mutated key — exactly one dirty key
            // (value change) or one tombstone (removal), the rest silent.
            case.mutate()
            val diverged = adapter.snapshot()
            if (case.removal) {
                assertTrue(case.mutatedKey !in diverged, "${case.name}: the removal leaves the snapshot")
                assertEquals(setOf(case.mutatedKey), adapter.deletedKeys(diverged), "${case.name}: the removal reads as the outbound tombstone")
                // Resolution through the SPI: the engine's tombstone confirm.
                adapter.deleteRemote(setOf(case.mutatedKey))
                assertTrue(adapter.deletedKeys(adapter.snapshot()).isEmpty(), "${case.name}: the adopted tombstone clears")
            } else {
                assertTrue(case.mutatedKey in diverged, "${case.name}: the value change keeps the key")
                assertEquals(
                    setOf(case.mutatedKey),
                    adapter.dirtyValues(diverged).keys,
                    "${case.name}: exactly the edited key reads dirty",
                )
                // Resolution through the SPI: adopt the synced value back.
                val adoptedValue = synced.getValue(case.mutatedKey)
                adapter.applyRemote(mapOf(case.mutatedKey to adoptedValue))
                case.afterAdopt?.invoke(adoptedValue)
                val restored = adapter.snapshot()
                assertEquals(adoptedValue, restored.getValue(case.mutatedKey), "${case.name}: the adopted value lands")
                assertTrue(adapter.dirtyValues(restored).isEmpty(), "${case.name}: the mirror re-aligns with the adopted value")
            }

            // (d) the outbound tombstone face for the tombstone-roaming
            // namespaces: one more local delete of a synced key reads as the
            // tombstone, and its adoption clears it.
            if (case.tombstoneFace != null) {
                val tombstoneKey = requireNotNull(case.tombstoneKey)
                case.tombstoneFace.invoke(adapter)
                val afterDelete = adapter.snapshot()
                assertTrue(tombstoneKey !in afterDelete, "${case.name}: the deleted key leaves the snapshot")
                assertEquals(setOf(tombstoneKey), adapter.deletedKeys(afterDelete), "${case.name}: the local delete is the outbound tombstone")
                adapter.deleteRemote(setOf(tombstoneKey))
                assertTrue(adapter.deletedKeys(adapter.snapshot()).isEmpty(), "${case.name}: the adopted tombstone stops reading deleted")
            }
        }
    }

    private companion object {
        const val USER = "user-1"
    }
}
