package com.raulshma.jellyplay.core.datastore.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.createPreferenceSliceGraph
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileSlice
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.platformEngineSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [PreferenceSnapshotReader.snapshotOnce] — the one-shot slice gather
 * (18 domain-store slices + runtime + PIN lockout) the factory-reset review
 * screen builds its current-vs-factory diff from. Pins: a cleared graph reads
 * back exactly [PreferenceSliceSnapshot.FACTORY], store/runtime/PIN writes
 * land in the snapshot, and the `volumeProfile` slice deliberately stays at
 * its default (behavior parity with the former per-VM `buildFromSlices`
 * assemblies, which never read the volume-profile store).
 */
class PreferenceSnapshotReaderTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var graph: com.raulshma.jellyplay.core.datastore.PreferenceSliceGraph
    private lateinit var pinRateLimiter: PinRateLimiter

    private val reader: PreferenceSnapshotReader
        get() = PreferenceSnapshotReader(
            stores = graph.preferenceStores,
            appRuntimeStateStore = graph.appRuntimeStateStore,
            pinRateLimiter = pinRateLimiter,
        )

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            pinRateLimiter = PinRateLimiter(dataStore, scope)
            pinRateLimiter.resetPinLockout()
        }
    }

    @Test
    fun `cleared stores read back the factory baseline`() = runTest {
        // Two desktop read-clamps diverge from the value-level FACTORY
        // baseline on a cleared graph: `normalizePreferredPlayer` degrades the
        // absent EXO_PLAYER choice to the platform default (MPV on desktop),
        // and the legacy `force_direct_play` boolean reads its historical
        // `true` default as FORCE_DIRECT_PLAY. Exactly what the former per-VM
        // assemblies read — the reader must not normalize any differently.
        val cleared = PreferenceSliceSnapshot.FACTORY.copy(
            playback = PreferenceSliceSnapshot.FACTORY.playback.copy(
                preferredPlayer = platformEngineSupport.default,
                playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
            ),
        )
        assertEquals(cleared, reader.snapshotOnce())
    }

    @Test
    fun `store, runtime and PIN-lockout writes land in the one-shot snapshot`() = runTest {
        graph.appearanceStore.setThemeMode(ThemeMode.DARK)
        graph.appearanceStore.setOledMode(true)
        graph.appRuntimeStateStore.setOnboardingCompleted(true)
        val lockout = pinRateLimiter.recordFailedPinAttempt()

        val snapshot = reader.snapshotOnce()

        assertEquals(ThemeMode.DARK, snapshot.appearance.themeMode)
        assertEquals(true, snapshot.appearance.oledMode)
        assertEquals(true, snapshot.runtime.onboardingCompleted)
        assertEquals(PinLockoutState(failedAttempts = 1, lockoutUntilEpochMs = 0L), snapshot.pinLockout)
        assertEquals(lockout, snapshot.pinLockout)
    }

    @Test
    fun `the volume-profile slice is not gathered and stays at its default`() = runTest {
        graph.preferenceStores.volumeProfile.setRememberVolumePerContentType(true)

        val snapshot = reader.snapshotOnce()

        assertEquals(VolumeProfileSlice(), snapshot.volumeProfile)
    }
}
