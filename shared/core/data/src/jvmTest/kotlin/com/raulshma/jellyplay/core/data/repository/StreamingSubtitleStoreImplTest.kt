package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM coverage for [StreamingSubtitleStoreImpl] over a temp dir (the
 * impl is file-backed only — no platform Context since the wave 18B
 * promotion out of the Android :core:data shim).
 *
 * Verifies the durable save/load/delete/clear round-trip, manifest persistence,
 * idempotent re-saves (same provider+id overwrites), and that a new instance
 * pointed at the same dir rehydrates from the on-disk manifest (survives a
 * "restart").
 */
class StreamingSubtitleStoreImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun newTempDir(): File = Files.createTempDirectory("streaming_subtitle_store_test").toFile()

    private fun store(baseDir: File) = StreamingSubtitleStoreImpl(baseDir = baseDir, json = json)

    @Test
    fun save_then_loadAll_returnsPersistedEntry() = runTest {
        val baseDir = newTempDir()
        try {
            val bytes = "1\n00:00:01,000 --> 00:00:02,000\nHi\n".toByteArray()
            val saved = store(baseDir).save(
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

            val loaded = store(baseDir).loadAll("item-1")
            assertEquals(1, loaded.size)
            assertEquals(saved, loaded.single())
            val file = store(baseDir).fileFor("item-1", loaded.single())
            assertTrue(file.exists())
            assertTrue(file.readBytes().contentEquals(bytes))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun save_sameProviderAndId_overwritesRatherThanAccumulating() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-42", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
            store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-42", "a.srt", "eng", "srt", false, false, byteArrayOf(2, 2))

            val loaded = store.loadAll("item-1")
            assertEquals(1, loaded.size, "same provider+id must replace, not append")
            val file = store.fileFor("item-1", loaded.single())
            assertTrue(file.readBytes().contentEquals(byteArrayOf(2, 2)))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun newInstanceAtSameDir_rehydratesFromManifest() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.vtt", "fra", "vtt", true, false, byteArrayOf(9))

            // Simulate a restart: new instance, same backing dir.
            val restarted = StreamingSubtitleStoreImpl(baseDir = baseDir, json = json)
            val loaded = restarted.loadAll("item-1")
            assertEquals(1, loaded.size)
            assertEquals(SubtitleProviderKind.WYZIE, loaded.single().provider)
            assertEquals("fra", loaded.single().language)
            assertTrue(restarted.fileFor("item-1", loaded.single()).readBytes().contentEquals(byteArrayOf(9)))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun delete_removesEntryAndFile() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            val saved = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
            store.save("item-1", SubtitleProviderKind.WYZIE, "wz-2", "b.vtt", "fra", "vtt", false, false, byteArrayOf(2))

            store.delete("item-1", saved)

            val loaded = store.loadAll("item-1")
            assertEquals(1, loaded.size)
            assertEquals(SubtitleProviderKind.WYZIE, loaded.single().provider)
            assertFalse(store.fileFor("item-1", saved).exists())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun clear_removesWholeItemDir() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
            store.save("item-1", SubtitleProviderKind.WYZIE, "wz-2", "b.vtt", "fra", "vtt", false, false, byteArrayOf(2))

            store.clear("item-1")

            assertTrue(store.loadAll("item-1").isEmpty())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun deletingLastEntry_removesEmptyItemDir() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            val saved = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))

            store.delete("item-1", saved)

            // An orphan empty dir would accumulate forever; assert it's gone.
            val itemDir = store.fileFor("item-1", saved).parentFile
            assertNotNull(itemDir)
            assertFalse(itemDir!!.exists(), "empty item dir must be pruned")
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun loadAll_unknownItem_returnsEmpty() = runTest {
        val baseDir = newTempDir()
        try {
            assertTrue(store(baseDir).loadAll("never-seen").isEmpty())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun markServerStreamIndex_persistsAndSurvivesRestart() = runTest {
        val baseDir = newTempDir()
        try {
            val saved = store(baseDir).save("item-1", SubtitleProviderKind.WYZIE, "wz-9", "x.srt", "eng", "srt", false, false, byteArrayOf(1))

            store(baseDir).markServerStreamIndex("item-1", saved, 7)

            // Survives a "restart": new instance over the same backing dir.
            val loaded = StreamingSubtitleStoreImpl(baseDir = baseDir, json = json).loadAll("item-1")
            assertEquals(7, loaded.single().serverStreamIndex)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun markServerStreamIndex_noOpForUnknownEntry() = runTest {
        val baseDir = newTempDir()
        try {
            store(baseDir).save("item-1", SubtitleProviderKind.WYZIE, "wz-9", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
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

            store(baseDir).markServerStreamIndex("item-1", ghost, 5)

            val loaded = store(baseDir).loadAll("item-1")
            assertEquals(1, loaded.size)
            assertNull(loaded.single().serverStreamIndex)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun attributeUploadedSubtitle_marksFreshlyAppearedMatch() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            val saved = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
            // Index 2 existed before the upload; 9 appeared after it.
            val streamsAfter = listOf(stream(2), stream(9))

            store.attributeUploadedSubtitle("item-1", saved, streamsAfter, preUploadExternalIndices = setOf(2))

            assertEquals(9, store.loadAll("item-1").single().serverStreamIndex)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun attributeUploadedSubtitle_noOpWhenNothingMatches() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            val saved = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "x.srt", "eng", "srt", false, false, byteArrayOf(1))
            val unmatching = listOf(stream(4, language = "ger", isHearingImpaired = true))

            store.attributeUploadedSubtitle("item-1", saved, unmatching, preUploadExternalIndices = emptySet())

            assertNull(store.loadAll("item-1").single().serverStreamIndex)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun purgeDeletedServerStreamCopies_exactIndexMatch_deletesOnlyThatCopy() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
            val uploaded = store.save("item-1", SubtitleProviderKind.WYZIE, "wz-1", "a.srt", "eng", "srt", false, false, byteArrayOf(1))
            store.markServerStreamIndex("item-1", uploaded, 7)
            val other = store.save("item-1", SubtitleProviderKind.OPENSUBTITLES, "os-2", "b.srt", "ger", "srt", false, false, byteArrayOf(2))

            store.purgeDeletedServerStreamCopies("item-1", index = 7, deletedStream = null)

            val remaining = store.loadAll("item-1")
            assertEquals(listOf(other.providerSubtitleId), remaining.map { it.providerSubtitleId })
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun purgeDeletedServerStreamCopies_legacyEntries_fallBackToAttributeMatch() = runTest {
        val baseDir = newTempDir()
        try {
            val store = store(baseDir)
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
        } finally {
            baseDir.deleteRecursively()
        }
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
