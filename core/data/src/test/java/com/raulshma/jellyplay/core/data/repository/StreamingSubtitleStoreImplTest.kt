package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage for [StreamingSubtitleStoreImpl]. The impl touches
 * `Context.filesDir`, so a pure-JVM test can't exercise it; Robolectric supplies
 * a real (temp-backed) filesDir.
 *
 * Verifies the durable save/load/delete/clear round-trip, manifest persistence,
 * idempotent re-saves (same provider+id overwrites), and that a new instance
 * pointed at the same dir rehydrates from the on-disk manifest (survives a
 * "restart").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamingSubtitleStoreImplTest {

    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true }

    private fun store() = StreamingSubtitleStoreImpl(context, json)

    @Before
    fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        // Clean the streaming-subtitles subtree Robolectric wrote to filesDir.
        java.io.File(context.filesDir, "streaming-subtitles").deleteRecursively()
    }

    @Test
    fun save_then_loadAll_returnsPersistedEntry() = runTest {
        val bytes = "1\n00:00:01,000 --> 00:00:02,000\nHi\n".toByteArray()
        val saved = store().save(
            itemId = "item-1",
            provider = SubtitleProviderKind.OPENSUBTITLES,
            providerSubtitleId = "os-42",
            fileName = "Movie.en.srt",
            language = "eng",
            codec = "srt",
            isForced = false,
            isHearingImpaired = true,
            bytes = bytes,
        )

        val loaded = store().loadAll("item-1")
        assertEquals(1, loaded.size)
        assertEquals(saved, loaded.single())
        val file = store().fileFor("item-1", loaded.single())
        assertTrue(file.exists())
        assertTrue(file.readBytes().contentEquals(bytes))
    }

    @Test
    fun save_sameProviderAndId_overwritesRatherThanAccumulating() = runTest {
        val store = store()
        store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-42", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
        store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-42", "a.srt", "eng", "srt", false, false, byteArrayOf(2, 2))

        val loaded = store.loadAll("item-1")
        assertEquals("same provider+id must replace, not append", 1, loaded.size)
        val file = store.fileFor("item-1", loaded.single())
        assertTrue(file.readBytes().contentEquals(byteArrayOf(2, 2)))
    }

    @Test
    fun newInstanceAtSameDir_rehydratesFromManifest() = runTest {
        val store = store()
        store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.vtt", "fra", "vtt", true, false, byteArrayOf(9))

        // Simulate a restart: new instance, same backing dir.
        val restarted = StreamingSubtitleStoreImpl(context, json)
        val loaded = restarted.loadAll("item-1")
        assertEquals(1, loaded.size)
        assertEquals(SubtitleProviderKind.WYZIE, loaded.single().provider)
        assertEquals("fra", loaded.single().language)
        assertTrue(restarted.fileFor("item-1", loaded.single()).readBytes().contentEquals(byteArrayOf(9)))
    }

    @Test
    fun delete_removesEntryAndFile() = runTest {
        val store = store()
        val saved = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
        store.save("item-1", SubtitleProviderKind.WYZIE, "wz-2", "b.vtt", "fra", "vtt", false, false, byteArrayOf(2))

        store.delete("item-1", saved)

        val loaded = store.loadAll("item-1")
        assertEquals(1, loaded.size)
        assertEquals(SubtitleProviderKind.WYZIE, loaded.single().provider)
        assertFalse(store.fileFor("item-1", saved).exists())
    }

    @Test
    fun clear_removesWholeItemDir() = runTest {
        val store = store()
        store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
        store.save("item-1", SubtitleProviderKind.WYZIE, "wz-2", "b.vtt", "fra", "vtt", false, false, byteArrayOf(2))

        store.clear("item-1")

        assertTrue(store.loadAll("item-1").isEmpty())
    }

    @Test
    fun deletingLastEntry_removesEmptyItemDir() = runTest {
        val store = store()
        val saved = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))

        store.delete("item-1", saved)

        // An orphan empty dir would accumulate forever; assert it's gone.
        val itemDir = store.fileFor("item-1", saved).parentFile
        assertNotNull(itemDir)
        assertFalse("empty item dir must be pruned", itemDir!!.exists())
    }

    @Test
    fun loadAll_unknownItem_returnsEmpty() = runTest {
        assertTrue(store().loadAll("never-seen").isEmpty())
    }
}
