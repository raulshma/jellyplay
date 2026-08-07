package com.raulshma.jellyplay.core.datastore.subtitle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the subtitle &amp; language preference store, focusing on the JSON-
 * encoded style round-trip and the per-item-delay LRU invariant that previously
 * lived inline in the `UserPreferencesStore` god object with **no** unit
 * coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleLanguageStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: SubtitleLanguageStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = SubtitleLanguageStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.subtitle.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.subtitle.first()
        assertNull(slice.preferredSubtitleLanguage)
        assertNull(slice.preferredAudioLanguage)
        assertNull(slice.appLanguage)
        assertFalse(slice.subtitlesForcedOnly)
        assertFalse(slice.hdrSubtitleStyleEnabled)
        assertFalse(slice.preferAudioDescription)
        assertFalse(slice.highContrastSubtitles)
        assertTrue(slice.subtitlePreviewInSettings)
        assertTrue(slice.subtitleDelayByItem.isEmpty())
        assertEquals(SubtitleStyle(), slice.subtitleStyle)
        // HDR default carries the larger font + half-opaque backdrop.
        assertEquals(
            SubtitleStyle(fontSize = 28, backgroundOpacity = 0.5f, edgeType = SubtitleEdgeType.OUTLINE),
            slice.hdrSubtitleStyle,
        )
    }

    @Test
    fun `setSubtitleStyle round-trips`() = runTest {
        val style = SubtitleStyle(
            applyCustomStyle = true,
            fontSize = 36,
            backgroundOpacity = 0.7f,
            edgeType = SubtitleEdgeType.DROP_SHADOW,
        )
        store.setSubtitleStyle(style)
        assertEquals(style, store.subtitle.first().subtitleStyle)
    }

    @Test
    fun `setSubtitleDelayForItem sets a non-zero delay`() = runTest {
        store.setSubtitleDelayForItem("item-1", 500L)
        val delays = store.subtitle.first().subtitleDelayByItem
        assertEquals(500L, delays["item-1"])
    }

    @Test
    fun `setSubtitleDelayForItem with zero delay removes the entry`() = runTest {
        store.setSubtitleDelayForItem("item-1", 500L)
        store.setSubtitleDelayForItem("item-1", 0L)
        assertTrue(store.subtitle.first().subtitleDelayByItem.isEmpty())
    }

    @Test
    fun `setPreferredSubtitleLanguage null removes the value`() = runTest {
        store.setPreferredSubtitleLanguage("eng")
        assertEquals("eng", store.subtitle.first().preferredSubtitleLanguage)
        store.setPreferredSubtitleLanguage(null)
        assertNull(store.subtitle.first().preferredSubtitleLanguage)
    }

    @Test
    fun `setHdrSubtitleStyle round-trips`() = runTest {
        val hdr = SubtitleStyle(fontSize = 40, backgroundOpacity = 0.6f, edgeType = SubtitleEdgeType.NONE)
        store.setHdrSubtitleStyle(hdr)
        assertEquals(hdr, store.subtitle.first().hdrSubtitleStyle)
    }

    @Test
    fun `setSubtitlesForcedOnly round-trips`() = runTest {
        store.setSubtitlesForcedOnly(true)
        assertTrue(store.subtitle.first().subtitlesForcedOnly)
        store.setSubtitlesForcedOnly(false)
        assertFalse(store.subtitle.first().subtitlesForcedOnly)
    }
}
