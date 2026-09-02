package com.raulshma.jellyplay.feature.settings.platform

import com.raulshma.jellyplay.feature.settings.StorageSizeEstimate
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Wave 21B: the desktop storage actual walks/clears the roots the desktop
 * data seams own — `<dataDir>/downloads` (DesktopDownloadStorageLayout's
 * root, nested music subtree included) and `<configDir>/http-cache`
 * (DesktopNetworkModule's OkHttp cache) — with the image bucket delegating
 * to the injected handle. Everything here is plain temp-dir filesystem plus
 * a hand-fake image cache; no real appdata is touched.
 */
class DesktopStorageAreasTest {

    private class RecordingImageCache(private val sizeBytes: Long = 0L) : DesktopImageCacheOps {
        var clears = 0
            private set

        override suspend fun sizeEstimateBytes(): Long = sizeBytes

        override suspend fun clear() {
            clears++
        }
    }

    private class Roots {
        val base = createTempDirectory("jp-storage-areas").toFile()
        val downloads = File(base, "downloads").apply { mkdirs() }
        val httpCache = File(base, "http-cache").apply { mkdirs() }

        fun areas(imageCache: DesktopImageCacheOps = RecordingImageCache()) =
            DesktopStorageAreas(
                downloadsRoot = downloads,
                httpCacheRoot = httpCache,
                imageCache = imageCache,
            )
    }

    @Test
    fun `size estimate walks downloads including the nested music subtree`() = runTest {
        val roots = Roots()
        // DesktopDownloadStorageLayout's two roots: video under downloads/,
        // audio under downloads/music/.
        File(roots.downloads, "movie_42.mkv").writeBytes(ByteArray(2_000))
        val music = File(roots.downloads, "music").apply { mkdirs() }
        File(music, "track_7.flac").writeBytes(ByteArray(500))
        File(roots.httpCache, "journal").writeBytes(ByteArray(100))
        val imageCache = RecordingImageCache(sizeBytes = 9L)

        val estimate = roots.areas(imageCache).sizeEstimateBytes(downloadStorageLocation = "INTERNAL")

        assertEquals(
            StorageSizeEstimate(
                cacheBytes = 100L,
                externalCacheBytes = 0L,
                downloadsBytes = 2_500L,
                imageCacheBytes = 9L,
            ),
            estimate,
            "buckets must mirror the walked roots + injected image-cache size",
        )
    }

    @Test
    fun `size estimate ignores the storage-location preference`() = runTest {
        // Single volume (DesktopDownloadStorageLayout / DesktopStorageMounts
        // Provider ignore the pref) — same numbers whatever the picker says.
        val roots = Roots()
        File(roots.downloads, "episode_1.mkv").writeBytes(ByteArray(1_000))
        val areas = roots.areas()

        val internal = areas.sizeEstimateBytes(downloadStorageLocation = "INTERNAL")
        val external = areas.sizeEstimateBytes(downloadStorageLocation = "EXTERNAL")

        assertEquals(internal, external, "desktop has one volume: the pref must not change the estimate")
    }

    @Test
    fun `missing roots estimate zero without creating them`() = runTest {
        // A fresh install may have neither dir yet (downloads/http-cache are
        // created lazily by their owners) — the walk must not mkdir anything.
        val base = createTempDirectory("jp-storage-missing").toFile()
        val areas = DesktopStorageAreas(
            downloadsRoot = File(base, "downloads"),
            httpCacheRoot = File(base, "http-cache"),
            imageCache = RecordingImageCache(),
        )

        val estimate = areas.sizeEstimateBytes(downloadStorageLocation = "INTERNAL")

        assertEquals(
            StorageSizeEstimate(0L, 0L, 0L, 0L),
            estimate,
            "missing roots must read as zeros",
        )
        assertFalse(File(base, "downloads").exists(), "estimate must not create the downloads root")
        assertFalse(File(base, "http-cache").exists(), "estimate must not create the http-cache root")
    }

    @Test
    fun `clear cache empties http-cache contents but keeps the directory`() = runTest {
        val roots = Roots()
        val journal = File(roots.httpCache, "journal").apply { writeBytes(ByteArray(10)) }
        val responses = File(roots.httpCache, "responses").apply { mkdirs() }
        File(responses, "body-1").writeBytes(ByteArray(10))

        roots.areas().clearCache()

        assertFalse(journal.exists(), "cache files must be deleted")
        assertFalse(responses.exists(), "cache subdirectories must be deleted")
        assertTrue(roots.httpCache.isDirectory, "the cache ROOT must survive for the live OkHttp Cache")
    }

    @Test
    fun `clear cache leaves downloads untouched`() = runTest {
        val roots = Roots()
        val movie = File(roots.downloads, "movie_42.mkv").apply { writeBytes(ByteArray(10)) }

        roots.areas().clearCache()

        assertTrue(movie.exists(), "clear-cache must not touch the downloads root")
    }

    @Test
    fun `clear cache on a missing root is a no-op`() = runTest {
        val base = createTempDirectory("jp-storage-clear-miss").toFile()
        val areas = DesktopStorageAreas(
            downloadsRoot = File(base, "downloads"),
            httpCacheRoot = File(base, "http-cache"),
            imageCache = RecordingImageCache(),
        )

        areas.clearCache()

        assertFalse(File(base, "http-cache").exists(), "no-op clear must not create the cache root")
    }

    @Test
    fun `clear image cache delegates to the injected handle`() = runTest {
        val imageCache = RecordingImageCache()
        val areas = Roots().areas(imageCache)

        areas.clearImageCache()

        assertEquals(1, imageCache.clears, "the image-cache clear must reach the injected handle")
    }
}
