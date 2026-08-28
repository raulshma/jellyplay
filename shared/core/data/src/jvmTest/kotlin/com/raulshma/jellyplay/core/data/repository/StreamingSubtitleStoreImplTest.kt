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
}
