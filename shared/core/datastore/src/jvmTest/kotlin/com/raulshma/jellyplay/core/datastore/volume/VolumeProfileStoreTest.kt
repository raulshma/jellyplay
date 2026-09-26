package com.raulshma.jellyplay.core.datastore.volume

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VolumeBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the per-content-type volume-memory store: defaults, the
 * JSON map round-trip, coercion, corrupt-blob tolerance, and PLAYBACK reset
 * participation.
 */
class VolumeProfileStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: VolumeProfileStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = VolumeProfileStore(dataStore, scope)
            store.volumeProfile.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.volumeProfile.first()
        assertTrue(slice.volumes.isEmpty())
        assertNull(slice.volumeFor(VolumeBucket.VIDEO))
        // Default ON where the feature is available at all.
        assertTrue(slice.rememberVolumePerContentType)
    }

    @Test
    fun `setVolume round-trips per bucket`() = runTest {
        store.setVolume(VolumeBucket.VIDEO, 0.8f)
        store.setVolume(VolumeBucket.AUDIOBOOK, 0.3f)
        val slice = store.volumeProfile.first()
        assertEquals(0.8f, slice.volumeFor(VolumeBucket.VIDEO))
        assertEquals(0.3f, slice.volumeFor(VolumeBucket.AUDIOBOOK))
        assertNull(slice.volumeFor(VolumeBucket.MUSIC))
    }

    @Test
    fun `setVolume coerces out-of-range levels`() = runTest {
        store.setVolume(VolumeBucket.MUSIC, 1.5f)
        store.setVolume(VolumeBucket.OTHER, -0.2f)
        val slice = store.volumeProfile.first()
        assertEquals(1f, slice.volumeFor(VolumeBucket.MUSIC))
        assertEquals(0f, slice.volumeFor(VolumeBucket.OTHER))
    }

    @Test
    fun `setVolume preserves the other buckets`() = runTest {
        store.setVolume(VolumeBucket.VIDEO, 0.7f)
        store.setVolume(VolumeBucket.MUSIC, 0.4f)
        assertEquals(0.7f, store.volumeProfile.first().volumeFor(VolumeBucket.VIDEO))
    }

    @Test
    fun `clearVolume forgets one bucket`() = runTest {
        store.setVolume(VolumeBucket.VIDEO, 0.7f)
        store.setVolume(VolumeBucket.MUSIC, 0.4f)
        store.clearVolume(VolumeBucket.VIDEO)
        val slice = store.volumeProfile.first()
        assertNull(slice.volumeFor(VolumeBucket.VIDEO))
        assertEquals(0.4f, slice.volumeFor(VolumeBucket.MUSIC))
    }

    @Test
    fun `setRememberVolumePerContentType round-trips`() = runTest {
        store.setRememberVolumePerContentType(false)
        assertFalse(store.volumeProfile.first().rememberVolumePerContentType)
    }

    @Test
    fun `corrupt blob degrades to no remembered levels`() = runTest {
        dataStore.edit { it[VolumeProfileStore.Keys.VOLUME_PROFILES] = "{not json" }
        assertTrue(store.volumeProfile.first().volumes.isEmpty())
    }

    @Test
    fun `unknown bucket names in the blob are dropped`() = runTest {
        dataStore.edit {
            it[VolumeProfileStore.Keys.VOLUME_PROFILES] = """{"VIDEO":0.9,"FUTURE_TYPE":0.5}"""
        }
        val slice = store.volumeProfile.first()
        assertEquals(0.9f, slice.volumeFor(VolumeBucket.VIDEO))
        assertTrue(slice.volumes.size == 1)
    }

    @Test
    fun `restore round-trips the whole slice`() = runTest {
        val slice = VolumeProfileSlice(
            volumes = mapOf(VolumeBucket.MUSIC to 0.55f),
            rememberVolumePerContentType = false,
        )
        store.restore(slice)
        assertEquals(slice, store.volumeProfile.first())
    }

    @Test
    fun `both keys reset under PLAYBACK`() = runTest {
        store.setVolume(VolumeBucket.VIDEO, 0.9f)
        store.setRememberVolumePerContentType(false)
        assertEquals(
            listOf(
                VolumeProfileStore.Keys.VOLUME_PROFILES,
                VolumeProfileStore.Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE,
            ),
            store.resetKeysFor(PreferenceResetCategory.PLAYBACK),
        )
        assertTrue(store.resetKeysFor(PreferenceResetCategory.APPEARANCE).isEmpty())
    }
}
