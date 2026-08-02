package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the in-player video preference store, focusing on the
 * segment-behaviour legacy migration and bounds coercions that previously lived
 * inline in the `UserPreferencesStore` god object with **no** unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPlayerStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: VideoPlayerStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = VideoPlayerStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.videoPlayer.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.videoPlayer.first()
        assertEquals(10_000L, slice.videoSeekDurationMs)
        assertEquals(5_000L, slice.videoControlsTimeoutMs)
        assertEquals(120_000L, slice.videoSwipeSeekMaxMs)
        assertEquals(0.5f, slice.videoBrightnessLevel)
        assertEquals(1.0f, slice.videoDefaultSpeed)
        assertEquals(SegmentBehavior.DEFAULT_BEHAVIORS, slice.segmentBehaviors)
        assertTrue(slice.videoGesturesEnabled)
        assertTrue(slice.videoAutoplayNext)
        assertFalse(slice.incognitoModeEnabled)
    }

    @Test
    fun `setSegmentBehaviors round-trips`() = runTest {
        val custom = SegmentBehavior.DEFAULT_BEHAVIORS + (
            MediaSegmentType.INTRO to SegmentBehavior.IGNORE
        )
        store.setSegmentBehaviors(custom)
        val slice = store.videoPlayer.first()
        assertEquals(SegmentBehavior.IGNORE, slice.segmentBehaviors[MediaSegmentType.INTRO])
        // Untouched types keep their default behaviour after the merge.
        assertEquals(SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.COMMERCIAL], slice.segmentBehaviors[MediaSegmentType.COMMERCIAL])
    }

    @Test
    fun `legacy skip_intro_enabled + auto_skip_intro booleans migrate into segment behaviors`() = runTest {
        // Pre-blob install: the JSON `segment_behaviors` blob is absent, so the
        // four legacy booleans feed the INTRO/OUTRO SegmentBehaviors.
        dataStore.edit {
            // Legacy keys are stored as strings in the god object; emulate that
            // shape so the toBoolean() fallback path is exercised.
            it[stringLegacyKey("skip_intro_enabled")] = "true"
            it[stringLegacyKey("auto_skip_intro")] = "true"
        }
        val slice = store.videoPlayer.first()
        // auto_skip_intro=true wins over skip_intro_enabled=true → AUTO_SKIP.
        assertEquals(SegmentBehavior.AUTO_SKIP, slice.segmentBehaviors[MediaSegmentType.INTRO])
        // No outro legacy key written: skip_outro default true → SHOW_BUTTON.
        assertEquals(SegmentBehavior.SHOW_BUTTON, slice.segmentBehaviors[MediaSegmentType.OUTRO])
    }

    @Test
    fun `setVideoBrightnessLevel round-trips`() = runTest {
        store.setVideoBrightnessLevel(0.75f)
        assertEquals(0.75f, store.videoPlayer.first().videoBrightnessLevel)
    }

    @Test
    fun `setVideoGesturesEnabled toggles`() = runTest {
        store.setVideoGesturesEnabled(false)
        assertFalse(store.videoPlayer.first().videoGesturesEnabled)
        store.setVideoGesturesEnabled(true)
        assertTrue(store.videoPlayer.first().videoGesturesEnabled)
    }

    private fun stringLegacyKey(name: String) =
        androidx.datastore.preferences.core.stringPreferencesKey(name)
}
