package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun markServerStreamIndex_persistsAndSurvivesRestart() = runTest {
        val saved = store().save("item-1", SubtitleProviderKind.WYZIE, "wz-9", "x.srt", "eng", "srt", false, false, byteArrayOf(1))

        store().markServerStreamIndex("item-1", saved, 7)

        // Survives a "restart": new instance over the same backing dir.
        val loaded = StreamingSubtitleStoreImpl(context, json).loadAll("item-1")
        assertEquals(7, loaded.single().serverStreamIndex)
    }

    @Test
    fun markServerStreamIndex_noOpForUnknownEntry() = runTest {
        store().save("item-1", SubtitleProviderKind.WYZIE, "wz-9", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
        val ghost = com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle(
            provider = SubtitleProviderKind.WYZIE,
            providerSubtitleId = "wz-gone",
            fileName = "gone.srt",
            language = "eng",
            codec = "srt",
            isForced = false,
            isHearingImpaired = false,
            fileRelativePath = "wyzie_wz-gone.srt",
        )

        store().markServerStreamIndex("item-1", ghost, 5)

        val loaded = store().loadAll("item-1")
        assertEquals(1, loaded.size)
        assertNull(loaded.single().serverStreamIndex)
    }

    @Test
    fun attributeUploadedSubtitle_marksFreshlyAppearedMatch() = runTest {
        val store = store()
        val saved = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
        // Index 2 existed before the upload; 9 appeared after it.
        val streamsAfter = listOf(stream(2), stream(9))

        store.attributeUploadedSubtitle("item-1", saved, streamsAfter, preUploadExternalIndices = setOf(2))

        assertEquals(9, store.loadAll("item-1").single().serverStreamIndex)
    }

    @Test
    fun attributeUploadedSubtitle_noOpWhenNothingMatches() = runTest {
        val store = store()
        val saved = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
        val unmatching = listOf(stream(4, language = "ger", isHearingImpaired = true))

        store.attributeUploadedSubtitle("item-1", saved, unmatching, preUploadExternalIndices = emptySet())

        assertNull(store.loadAll("item-1").single().serverStreamIndex)
    }

    @Test
    fun purgeDeletedServerStreamCopies_exactIndexMatch_deletesOnlyThatCopy() = runTest {
        val store = store()
        val uploaded = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
        store.markServerStreamIndex("item-1", uploaded, 7)
        val other = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-2", "b.srt", "ger", "srt", false, false, byteArrayOf(2))

        store.purgeDeletedServerStreamCopies("item-1", index = 7, deletedStream = null)

        val remaining = store.loadAll("item-1")
        assertEquals(listOf(other.providerSubtitleId), remaining.map { it.providerSubtitleId })
    }

    @Test
    fun purgeDeletedServerStreamCopies_legacyEntries_fallBackToAttributeMatch() = runTest {
        val store = store()
        // Legacy copy: never uploaded, so no recorded index — matched by attributes.
        val legacy = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-old", "old.srt", "eng", "srt", false, false, byteArrayOf(1))
        // Uploaded copy of a DIFFERENT stream (index 3): must survive on index alone,
        // even though its attributes also match the deleted stream's.
        val uploadedOther = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-up", "up.srt", "eng", "srt", false, false, byteArrayOf(2))
        store.markServerStreamIndex("item-1", uploadedOther, 3)

        store.purgeDeletedServerStreamCopies("item-1", index = 7, deletedStream = stream(7, "eng"))

        val remaining = store.loadAll("item-1")
        assertEquals(listOf(uploadedOther.providerSubtitleId), remaining.map { it.providerSubtitleId })
        assertFalse(store.fileFor("item-1", legacy).exists())
    }

    private fun stream(
        index: Int,
        language: String? = "eng",
        isHearingImpaired: Boolean = false,
    ): com.raulshma.jellyplay.core.model.MediaStream =
        com.raulshma.jellyplay.core.model.MediaStream(
            index = index,
            type = com.raulshma.jellyplay.core.model.StreamType.SUBTITLE,
            language = language,
            codec = "subrip",
            isExternal = true,
            isForced = false,
            isHearingImpaired = isHearingImpaired,
        )
}
