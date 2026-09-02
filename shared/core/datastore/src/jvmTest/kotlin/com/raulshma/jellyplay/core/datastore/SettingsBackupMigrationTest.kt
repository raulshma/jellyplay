package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Backs the v2 settings-backup split: export/import round-trips through
 * per-domain slices (no aggregate), a legacy v1 single-aggregate backup still
 * imports via the per-store `restorePreferences(UserPreferences)` path, and the
 * security-sensitive lock config only restores when the caller opts in.
 *
 * The v0/v1 paths are exercised by decoding a hand-written [UserPreferences]
 * aggregate (the v1 `preferences` payload) and fanning it to the legacy
 * orchestrator; the v2 path exercises the full encode → envelope → decode →
 * `restoreV2` round-trip.
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
        assertEquals(PlayerType.EXO_PLAYER, store.preferredPlayerSnapshot())

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
    fun `legacy v1 aggregate backup imports via per-store restorePreferences`() = runTest {
        // A v1 backup was a single enveloped UserPreferences aggregate. Mutate
        // one field on it and fan via the legacy orchestrator — the value must
        // land in the owning store's slice.
        val legacy = UserPreferences(preferredPlayer = PlayerType.MPV)
        store.restorePreferences(legacy, restoreSecuritySensitive = false)
        drainAfterWrite()

        assertEquals(PlayerType.MPV, store.preferredPlayerSnapshot())
    }

    @Test
    fun `legacy v1 envelope decodes to the aggregate preferences`() = runTest {
        val prefs = UserPreferences(preferredPlayer = PlayerType.MPV)
        val v1 = LegacySettingsBackup(preferences = prefs)
        val encoded = PreferencesJson.export.encodeToString(LegacySettingsBackup.serializer(), v1)
        val decoded = PreferencesJson.import.decodeFromString(LegacySettingsBackup.serializer(), encoded)

        assertEquals(SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(PlayerType.MPV, decoded.preferences.preferredPlayer)
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
        assertEquals(PlayerType.EXO_PLAYER, store.preferredPlayerSnapshot())
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
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun drainInitialSlices() {
        store.snapshotForBackup()
    }

    private fun drainAfterWrite() {
        runBlocking { store.snapshotForBackup() }
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
