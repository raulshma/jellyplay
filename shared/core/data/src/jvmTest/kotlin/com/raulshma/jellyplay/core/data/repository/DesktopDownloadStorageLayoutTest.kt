package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [DesktopDownloadStorageLayout] — the desktop actual of the
 * [DownloadStorageLayoutContract]: audio types land in `<dataDir>/downloads/music`
 * with an mp3 default extension, everything else in `<dataDir>/downloads` with
 * an mp4 default, the server container overrides the extension when safe, the
 * display name is sanitized and the idHint guarantees uniqueness, and
 * `storageLocationPref` is ignored (desktop has a single volume).
 *
 * Space-guard note: `resolve` reads the real `File.getUsableSpace()` of the
 * destination directory, so the insufficient-space branch
 * (`IllegalStateException` under [DownloadStorageLayoutContract.MIN_FREE_BYTES])
 * cannot be triggered without a File seam to fake a nearly-full volume. The
 * guard's boundary decision itself is asserted directly against
 * [DownloadStorageLayoutContract.hasMinimumFreeSpace]; on this machine the
 * positive-path resolve also asserts the wiring accepts a healthy volume.
 */
class DesktopDownloadStorageLayoutTest {

    private lateinit var dataDir: java.nio.file.Path

    @BeforeTest
    fun setup() {
        dataDir = Files.createTempDirectory("jellyplay-downloads-test")
    }

    @AfterTest
    fun teardown() {
        dataDir.toFile().deleteRecursively()
    }

    // ── Path structure ──────────────────────────────────────────────────

    @Test
    fun `audio types resolve under downloads music`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        val audio = layout.resolve(MediaType.AUDIO.name, storageLocationPref = "INTERNAL", name = "Song", idHint = "id0001", container = null)
        val music = layout.resolve(MediaType.MUSIC.name, storageLocationPref = "INTERNAL", name = "Song", idHint = "id0001", container = null)

        assertEquals(dataDir.resolve("downloads/music"), audio.baseDir.toPath())
        assertTrue(audio.baseDir.exists(), "resolve must create the base directory")
        assertEquals(audio.baseDir, music.baseDir)
    }

    @Test
    fun `video types resolve directly under downloads`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        val movie = layout.resolve(MediaType.MOVIE.name, "EXTERNAL", "Movie", "id0001", container = null)
        val episode = layout.resolve(MediaType.EPISODE.name, "INTERNAL", "Episode", "id0001", container = null)
        val unknown = layout.resolve("SOMETHING_ELSE", "INTERNAL", "Other", "id0001", container = null)

        assertEquals(dataDir.resolve("downloads"), movie.baseDir.toPath())
        assertEquals(movie.baseDir, episode.baseDir)
        assertEquals(movie.baseDir, unknown.baseDir)
        assertTrue(movie.baseDir.exists())
    }

    // ── Filename rules ──────────────────────────────────────────────────

    @Test
    fun `file names are sanitized and carry the idHint`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        val resolved = layout.resolve(MediaType.MOVIE.name, "INTERNAL", "My Favorite Movie", "abcd1234", container = null)

        assertEquals("My_Favorite_Movie_abcd1234.mp4", resolved.fileName)
        assertEquals(resolved.baseDir.toPath().resolve(resolved.fileName).toString(), resolved.filePath)
        assertTrue(resolved.filePath.startsWith(resolved.baseDir.absolutePath))
    }

    @Test
    fun `unsafe name characters are replaced with underscores`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        val resolved = layout.resolve(MediaType.MOVIE.name, "INTERNAL", "a/b:c?d", "id0001", container = null)

        assertEquals("a_b_c_d_id0001.mp4", resolved.fileName)
    }

    @Test
    fun `the server container overrides the default extension only when safe`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        // Real container is preserved (ExoPlayer selects its extractor from it).
        assertEquals("My_Favorite_Movie_id0001.mkv", layout.resolve(MediaType.MOVIE.name, "I", "My Favorite Movie", "id0001", container = "mkv").fileName)
        assertEquals("Song_id0001.flac", layout.resolve(MediaType.AUDIO.name, "I", "Song", "id0001", container = "flac").fileName)

        // Missing or unsafe containers fall back to the platform defaults.
        assertEquals("My_Favorite_Movie_id0001.mp4", layout.resolve(MediaType.MOVIE.name, "I", "My Favorite Movie", "id0001", container = null).fileName)
        assertEquals("My_Favorite_Movie_id0001.mp4", layout.resolve(MediaType.MOVIE.name, "I", "My Favorite Movie", "id0001", container = "  ").fileName)
        assertEquals("My_Favorite_Movie_id0001.mp4", layout.resolve(MediaType.MOVIE.name, "I", "My Favorite Movie", "id0001", container = "../evil").fileName)
        assertEquals("Song_id0001.mp3", layout.resolve(MediaType.AUDIO.name, "I", "Song", "id0001", container = "not/valid").fileName)
    }

    @Test
    fun `storageLocationPref is ignored on desktop`() {
        val layout = DesktopDownloadStorageLayout(dataDir)

        val internal = layout.resolve(MediaType.MOVIE.name, "INTERNAL", "Same", "id0001", container = null)
        val external = layout.resolve(MediaType.MOVIE.name, "EXTERNAL", "Same", "id0001", container = null)

        assertEquals(internal.filePath, external.filePath)
    }

    // ── Space guard ─────────────────────────────────────────────────────

    @Test
    fun `the space guard boundary sits at MIN_FREE_BYTES`() {
        assertFalse(DownloadStorageLayoutContract.hasMinimumFreeSpace(DownloadStorageLayoutContract.MIN_FREE_BYTES - 1))
        assertTrue(DownloadStorageLayoutContract.hasMinimumFreeSpace(DownloadStorageLayoutContract.MIN_FREE_BYTES))
    }

    @Test
    fun `resolve succeeds when the volume clears the free-space floor`() {
        // Exercises the real getUsableSpace wiring on a healthy temp volume.
        // If this volume is genuinely below MIN_FREE_BYTES (100 MB) the guard
        // fires — in that case the test environment, not the layout, is broken.
        val layout = DesktopDownloadStorageLayout(dataDir)

        if (dataDir.toFile().usableSpace >= DownloadStorageLayoutContract.MIN_FREE_BYTES) {
            val resolved = layout.resolve(MediaType.MOVIE.name, "INTERNAL", "Healthy", "id0001", container = null)
            assertEquals("Healthy_id0001.mp4", resolved.fileName)
        } else {
            assertFailsWith<IllegalStateException> {
                layout.resolve(MediaType.MOVIE.name, "INTERNAL", "Healthy", "id0001", container = null)
            }
        }
    }
}
