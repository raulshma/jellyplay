package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.settings.ExternalBackupSlice
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.platformEngineSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Backs the v2 settings-backup split: export/import round-trips through
 * per-domain slices (no aggregate) and the security-sensitive lock config
 * only restores when the caller opts in. Legacy v0/v1 imports sunset in
 * v0.11 — the aggregate restore ladder and its suites are gone.
 */
class SettingsBackupMigrationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: UserPreferencesStore
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            store = createUserPreferencesStore(scope, dataStore)
            // Drain the Eagerly-cached slice flows so the cleared state is
            // observed before each test writes + reads.
            drainInitialSlices()
        }
    }

    @Test
    fun `v2 export round-trips every slice back to the same values`() = runTest {
        // Mutate one field per cluster so the round-trip is observable across
        // stores (defaults would round-trip trivially and hide decode bugs).
        graph.playbackStore.setPreferredPlayer(PlayerType.MPV)
        graph.playbackStore.setStreamingQuality(StreamingQuality.FHD_1080P)
        drainAfterWrite()

        val snapshot = store.snapshotForBackup()
        val backup = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)

        // Wipe and re-import — values must return.
        dataStore.edit { it.clear() }
        drainInitialSlices()
        assertEquals(platformEngineSupport.default, store.preferredPlayerSnapshot())

        store.restoreV2(backup, restoreSecuritySensitive = true)
        drainAfterWrite()

        assertEquals(PlayerType.MPV, store.preferredPlayerSnapshot())
        assertEquals(StreamingQuality.FHD_1080P, store.streamingQualitySnapshot())
    }

    @Test
    fun `v2 export then envelope round-trip decodes back to the same slices`() = runTest {
        graph.playbackStore.setPreferredPlayer(PlayerType.MPV)
        drainAfterWrite()

        val snapshot = store.snapshotForBackup()
        val original = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)
        val encoded = PreferencesJson.export.encodeToString(SettingsBackup.serializer(), original)
        val decoded = PreferencesJson.import.decodeFromString(SettingsBackup.serializer(), encoded)

        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.slices.keys, decoded.slices.keys)
        assertEquals(snapshot.extras, decoded.extras)
    }

    @Test
    fun `v2 export skips aggregate and reports the v2 schema version`() = runTest {
        val snapshot = store.snapshotForBackup()
        val backup = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)

        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION, backup.schemaVersion)
        // Every domain slice is present.
        assertTrue(backup.slices.containsKey(BackupSliceKey.PLAYBACK))
        assertTrue(backup.slices.containsKey(BackupSliceKey.SECURITY))
        assertTrue(backup.slices.containsKey(BackupSliceKey.PLAYER_ENGINE))
    }

    @Test
    fun `restoreV2 without security opt-in does not overwrite existing lock config`() = runTest {
        // Seed an existing lock config so we can tell restore apart.
        loadSecurityLocked()

        // Build a v2 backup whose security slice is the UNLOCKED default, so a
        // successful overwrite would clear the lock. Without the opt-in the
        // lock config must survive.
        val snapshot = store.snapshotForBackup()
        val unlocked = PreferencesJson.export.encodeToJsonElement(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice.serializer(),
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice(),
        )
        val backup = SettingsBackup(
            slices = snapshot.slices + (BackupSliceKey.SECURITY to unlocked),
            extras = snapshot.extras,
        )

        store.restoreV2(backup, restoreSecuritySensitive = false)
        drainAfterWrite()

        // Lock config is the existing one, not overwritten by the import.
        val after = store.securityStoreSnapshot()
        assertTrue(after.pinLockEnabled)
        assertEquals(after.pinHash, "existing-hash")
    }

    @Test
    fun `restoreV2 with security opt-in overwrites the lock config`() = runTest {
        loadSecurityLocked()

        val snapshot = store.snapshotForBackup()
        val unlocked = PreferencesJson.export.encodeToJsonElement(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice.serializer(),
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice(),
        )
        val backup = SettingsBackup(
            slices = snapshot.slices + (BackupSliceKey.SECURITY to unlocked),
            extras = snapshot.extras,
        )

        store.restoreV2(backup, restoreSecuritySensitive = true)
        drainAfterWrite()

        val after = store.securityStoreSnapshot()
        assertFalse(after.pinLockEnabled)
    }

    @Test
    fun `restoreV2 tolerates a missing slice key`() = runTest {
        // Drop one slice — import must not throw (older v2 export forwards-compat).
        val snapshot = store.snapshotForBackup()
        val partial = snapshot.slices.toMutableMap().apply { remove(BackupSliceKey.PLAYBACK) }.toMap()
        val backup = SettingsBackup(slices = partial, extras = snapshot.extras)

        store.restoreV2(backup, restoreSecuritySensitive = true)
        drainAfterWrite()
        // No exception == pass; playback stays at its default.
        assertEquals(platformEngineSupport.default, store.preferredPlayerSnapshot())
    }

    @Test
    fun `extras round-trip favorites and watch-later playlist`() = runTest {
        val snapshot = store.snapshotForBackup().copy(
            extras = AppRuntimeState(
                favoriteChannels = setOf("ch1", "ch2"),
                watchLaterPlaylistId = "playlist-7",
                onboardingCompleted = true,
            ),
        )
        val backup = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)

        store.restoreV2(backup, restoreSecuritySensitive = true)
        drainAfterWrite()

        val restored = store.snapshotForBackup().extras
        assertEquals(setOf("ch1", "ch2"), restored.favoriteChannels)
        assertEquals(restored.watchLaterPlaylistId, "playlist-7")
        assertTrue(restored.onboardingCompleted)
    }

    // ------------------------------------------------------------------
    // Wave 2 — external backup slices (the ExternalBackupSlice seam)
    // ------------------------------------------------------------------

    @Test
    fun `external slice rides the snapshot and restores through restoreV2`() = runTest {
        val external = RecordingExternalSlice(BackupSliceKey.WIDGET)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(external))
        external.state = buildJsonObject { put("widget_config", "set") }
        drainStore(externalStore)

        val snapshot = externalStore.snapshotForBackup()
        assertTrue(BackupSliceKey.WIDGET in snapshot.slices, "a non-null external read must ride the envelope")

        // Alter + restore: the incoming element lands at the source.
        external.state = null
        val backup = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)
        externalStore.restoreV2(backup, restoreSecuritySensitive = true)
        drainStore(externalStore)

        assertEquals(
            snapshot.slices.getValue(BackupSliceKey.WIDGET),
            external.state,
            "restoreV2 must fan the slice element back to the source",
        )
    }

    @Test
    fun `external slice with a null read is omitted from the snapshot`() = runTest {
        val external = RecordingExternalSlice(BackupSliceKey.PLAYLISTS)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(external))
        drainStore(externalStore)

        val snapshot = externalStore.snapshotForBackup()

        assertFalse(BackupSliceKey.PLAYLISTS in snapshot.slices, "null read = nothing to back up")
    }

    @Test
    fun `restoreV2 tolerates a backup without the external slice`() = runTest {
        val external = RecordingExternalSlice(BackupSliceKey.ITEM_PREFS)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(external))
        external.state = buildJsonObject { put("ITEM/item-1", "payload") }
        drainStore(externalStore)

        // A v2 backup WITHOUT the external key (an older export) must not
        // touch the external domain — omitted slice, never a wipe.
        val snapshot = externalStore.snapshotForBackup()
        val partial = snapshot.slices.toMutableMap().apply { remove(BackupSliceKey.ITEM_PREFS) }.toMap()
        externalStore.restoreV2(SettingsBackup(slices = partial, extras = snapshot.extras))
        drainStore(externalStore)

        assertEquals(
            buildJsonObject { put("ITEM/item-1", "payload") },
            external.state,
            "a missing external slice key must leave the domain untouched",
        )
    }

    @Test
    fun `restoreV2Categories leaves external slices to the includeExtras arm`() = runTest {
        val external = RecordingExternalSlice(BackupSliceKey.INTEGRATIONS)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(external))
        drainStore(externalStore)
        val backup = SettingsBackup(
            slices = mapOf(BackupSliceKey.INTEGRATIONS to buildJsonObject { put("seerr_enabled", true) }),
            extras = AppRuntimeState(),
        )

        externalStore.restoreV2Categories(
            backup,
            categories = setOf(PreferenceResetCategory.APPEARANCE),
            includeExtras = false,
        )
        drainStore(externalStore)
        assertNull(external.state, "a category import must never touch the not-category-bound external slices")

        externalStore.restoreV2Categories(
            backup,
            categories = setOf(PreferenceResetCategory.APPEARANCE),
            includeExtras = true,
        )
        drainStore(externalStore)
        assertEquals(buildJsonObject { put("seerr_enabled", true) }, external.state)
    }

    @Test
    fun `restoreExternalSlice restores just the named slice`() = runTest {
        val integrations = RecordingExternalSlice(BackupSliceKey.INTEGRATIONS)
        val widget = RecordingExternalSlice(BackupSliceKey.WIDGET)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(integrations, widget))
        drainStore(externalStore)
        val backup = SettingsBackup(
            slices = mapOf(
                BackupSliceKey.INTEGRATIONS to buildJsonObject { put("seerr_enabled", true) },
                BackupSliceKey.WIDGET to buildJsonObject { put("widget_config", true) },
            ),
            extras = AppRuntimeState(),
        )

        externalStore.restoreExternalSlice(backup, BackupSliceKey.INTEGRATIONS)
        drainStore(externalStore)

        assertEquals(buildJsonObject { put("seerr_enabled", true) }, integrations.state)
        assertNull(widget.state, "the unnamed sibling slice must stay untouched")
    }

    @Test
    fun `externalSliceSnapshot reports the live external slices with null for empty domains`() = runTest {
        val filled = RecordingExternalSlice(BackupSliceKey.WIDGET)
        val empty = RecordingExternalSlice(BackupSliceKey.PLAYLISTS)
        val externalStore = createUserPreferencesStore(scope, dataStore, listOf(filled, empty))
        filled.state = buildJsonObject { put("widget_config", true) }
        drainStore(externalStore)

        val snapshot = externalStore.externalSliceSnapshot()

        assertEquals(setOf(BackupSliceKey.WIDGET, BackupSliceKey.PLAYLISTS), snapshot.keys)
        assertEquals(buildJsonObject { put("widget_config", true) }, snapshot.getValue(BackupSliceKey.WIDGET))
        assertNull(snapshot.getValue(BackupSliceKey.PLAYLISTS))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun drainInitialSlices() {
        store.snapshotForBackup()
    }

    private fun drainAfterWrite() {
        runBlocking { store.snapshotForBackup() }
    }

    /** Same drain for a store variant constructed per-test (external slices). */
    private suspend fun drainStore(target: UserPreferencesStore) {
        target.snapshotForBackup()
    }

    private suspend fun loadSecurityLocked() {
        val slice = store.securityStoreSnapshot().copy(pinLockEnabled = true, pinHash = "existing-hash")
        // Drive the lock keys via a synthesized v2 restore with opt-in.
        val element = PreferencesJson.export.encodeToJsonElement(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice.serializer(),
            slice,
        )
        val backup = SettingsBackup(
            slices = mapOf(BackupSliceKey.SECURITY to element),
            extras = AppRuntimeState(),
        )
        store.restoreV2(backup, restoreSecuritySensitive = true)
        drainAfterWrite()
    }
}

// ----------------------------------------------------------------------
// Test-only read accessors on UserPreferencesStore. Kept minimal: only the
// slices these migration tests assert on. They reach the owning store's
// `first()` so the test reads the same canonical slice export/import uses.
// ----------------------------------------------------------------------
suspend fun UserPreferencesStore.preferredPlayerSnapshot(): PlayerType =
    playbackSliceSnapshot().preferredPlayer

suspend fun UserPreferencesStore.streamingQualitySnapshot(): StreamingQuality =
    playbackSliceSnapshot().streamingQuality

suspend fun UserPreferencesStore.playbackSliceSnapshot(): com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice =
    snapshotForBackup().let { snap ->
        PreferencesJson.import.decodeFromJsonElement(
            com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice.serializer(),
            snap.slices.getValue(BackupSliceKey.PLAYBACK),
        )
    }

suspend fun UserPreferencesStore.securityStoreSnapshot(): com.raulshma.jellyplay.core.datastore.security.SecuritySlice =
    snapshotForBackup().let { snap ->
        PreferencesJson.import.decodeFromJsonElement(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice.serializer(),
            snap.slices.getValue(BackupSliceKey.SECURITY),
        )
    }

/**
 * Minimal in-memory [ExternalBackupSlice]: the state the source "persists" is
 * just a settable element, so the tests can observe exactly what the store
 * fans in and out without Room.
 */
private class RecordingExternalSlice(override val key: String) : ExternalBackupSlice {
    var state: JsonElement? = null

    override suspend fun read(): JsonElement? = state

    override suspend fun restore(element: JsonElement) {
        state = element as? JsonObject ?: state
    }
}
