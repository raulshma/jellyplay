package com.raulshma.jellyplay.core.datastore.audioeffects

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the audio-effects preference store, focusing on the JSON-encoded
 * [EqualizerSettings] round-trip and the strength/effect toggles that
 * previously lived inline in the `UserPreferencesStore` god object with **no**
 * unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioEffectsStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AudioEffectsStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = AudioEffectsStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.audioEffects.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.audioEffects.first()
        assertFalse(slice.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, slice.dialogueBoostStrength)
        assertFalse(slice.equalizerEnabled)
        assertEquals(EqualizerPreset.FLAT, slice.equalizerPreset)
        assertEquals(ReverbPreset.NONE, slice.reverbPreset)
        assertEquals(EqualizerSettings(), slice.equalizerSettings)
    }

    @Test
    fun `setEqualizerSettings round-trips as JSON`() = runTest {
        val settings = EqualizerSettings(bandLevels = List(10) { 100 })
        store.setEqualizerSettings(settings)
        assertEquals(settings, store.audioEffects.first().equalizerSettings)
    }

    @Test
    fun `setDialogueBoostStrength round-trips`() = runTest {
        store.setDialogueBoostStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, store.audioEffects.first().dialogueBoostStrength)
    }

    @Test
    fun `setReverbPreset round-trips`() = runTest {
        store.setReverbPreset(ReverbPreset.LARGE_HALL)
        assertEquals(ReverbPreset.LARGE_HALL, store.audioEffects.first().reverbPreset)
    }
}
