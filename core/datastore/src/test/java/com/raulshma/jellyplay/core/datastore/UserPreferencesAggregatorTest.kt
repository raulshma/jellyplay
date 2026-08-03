package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.legacy.UserPreferencesAggregator
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards [UserPreferencesAggregator] — the single sanctioned producer of the
 * legacy [UserPreferences] aggregate. The 6 shim consumers (FactoryReset,
 * Detail, Main, Onboarding, Settings, SeerrDetail) depend on this rebuilding
 * the whole-object shape from the 18 store slices + facade extras, so a single
 * store write must propagate and a write to the facade-extras DataStore keys
 * (favorite channels, watch-later) must surface too.
 *
 * This is the read-side backstop for the legacy screens; the write side is
 * covered per-store by the domain-store tests and the projection read layer by
 * [PreferenceProjectionsTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPreferencesAggregatorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var aggregator: UserPreferencesAggregator

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            aggregator = createUserPreferencesAggregator(scope, dataStore)
            // Force the Eagerly-cached aggregate to observe the cleared state
            // before each test writes + reads.
            aggregator.preferences.first()
        }
    }

    @Test
    fun `aggregate emits store defaults before any write`() = runTest {
        // The aggregate is rebuilt from store slices, so its defaults track the
        // slice defaults (not the legacy UserPreferences data-class defaults,
        // which diverged historically). Assert representative fields per cluster
        // rather than whole-object equality against UserPreferences().
        val prefs = aggregator.preferences.value
        assertEquals(PlayerType.EXO_PLAYER, prefs.preferredPlayer)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertEquals(false, prefs.oledMode)
        assertEquals(StreamingQuality.AUTO, prefs.streamingQuality)
        assertEquals(false, prefs.pinLockEnabled)
        assertEquals(false, prefs.bassBoostEnabled)
        assertEquals(emptySet<String>(), prefs.favoriteChannels)
        assertEquals(false, prefs.onboardingCompleted)
    }

    @Test
    fun `an appearance-store write propagates to the aggregate`() = runTest {
        val before = aggregator.preferences.value
        graph.appearanceStore.setThemeMode(ThemeMode.DARK)

        val after = aggregator.preferences.first()
        assertNotEquals(before, after)
        assertEquals(ThemeMode.DARK, after.themeMode)
    }

    @Test
    fun `a playback-store write propagates to the aggregate`() = runTest {
        graph.playbackStore.setPreferredPlayer(PlayerType.MPV)
        graph.playbackStore.setStreamingQuality(StreamingQuality.UHD_4K)

        val after = aggregator.preferences.first()
        assertEquals(PlayerType.MPV, after.preferredPlayer)
        assertEquals(StreamingQuality.UHD_4K, after.streamingQuality)
    }

    @Test
    fun `a write to disparate stores is reflected together in one aggregate value`() = runTest {
        // One field per store cluster so the cross-store combine is observable.
        graph.appearanceStore.setOledMode(true)
        graph.videoPlayerStore.setVideoGesturesEnabled(false)
        graph.audioEffectsStore.setBassBoostEnabled(true)

        val after = aggregator.preferences.first()
        assertTrue(after.oledMode)
        assertEquals(false, after.videoGesturesEnabled)
        assertTrue(after.bassBoostEnabled)
    }

    @Test
    fun `a facade-extras favorite-channels write surfaces in the aggregate`() = runTest {
        // favorite_channels is a facade-owned key (no domain slice owns it) —
        // the aggregator reads it off the shared DataStore directly.
        val dataStore = TestDataStoreProvider.get(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
        )
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("favorite_channels")] =
                """["ch-1","ch-2"]"""
        }

        val after = aggregator.preferences.first()
        assertEquals(setOf("ch-1", "ch-2"), after.favoriteChannels)
    }

    @Test
    fun `a facade-extras onboarding write surfaces in the aggregate`() = runTest {
        val dataStore = TestDataStoreProvider.get(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
        )
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")] = true
        }

        val after = aggregator.preferences.first()
        assertTrue(after.onboardingCompleted)
    }
}
