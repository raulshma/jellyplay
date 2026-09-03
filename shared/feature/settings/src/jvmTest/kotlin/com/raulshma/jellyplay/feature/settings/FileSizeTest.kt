package com.raulshma.jellyplay.feature.settings

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.AssumptionViolatedException

/**
 * Pins the shared `directorySizeBytes` walk (FileSize.kt, commonMain pure JVM)
 * that backs BOTH [SettingsViewModel] cache-size and the
 * [StorageSettingsViewModel] storage breakdown — it used to be duplicated in
 * the two ViewModels, so a fix to one could silently diverge from the other.
 *
 * Invariants under test: nested dirs sum, symlinks inside the tree are skipped
 * (circular-link guard) while a symlinked ROOT is still measured (the OS
 * symlinked-onto-external-storage cache dir case), and traversal is capped at
 * MAX_DEPTH=10 so a pathological tree cannot be walked forever. Plain temp
 * dirs via `createTempDirectory` (kotlin.test — no JUnit4 TemporaryFolder),
 * cleaned up in [tearDown].
 *
 * Symlink tests degrade to assumption-skips on filesystems where link
 * creation is unavailable (Windows without admin/developer mode).
 */
class FileSizeTest {

    private val tempRoots = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        tempRoots.forEach { it.deleteRecursively() }
    }

    private fun newTempDir(prefix: String): File =
        createTempDirectory(prefix).toFile().also { tempRoots.add(it) }

    /** Links [target] under [link]; assumption-skips the test if the FS refuses. */
    private fun linkOrSkip(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: UnsupportedOperationException) {
            throw AssumptionViolatedException("symlinks unsupported on this filesystem: ${e.message}")
        } catch (e: java.io.IOException) {
            throw AssumptionViolatedException("symlink creation unavailable (no privilege?): ${e.message}")
        }
    }

    // ------------------------------------------------------------- happy path

    @Test
    fun `nested directory tree sums every file byte`() {
        val root = newTempDir("jp-size-nested")
        File(root, "top.bin").writeBytes(ByteArray(1_000))
        val mid = File(root, "mid").apply { mkdirs() }
        File(mid, "level2.bin").writeBytes(ByteArray(500))
        val leaf = File(mid, "leaf").apply { mkdirs() }
        File(leaf, "level3.bin").writeBytes(ByteArray(250))

        assertEquals(1_750L, directorySizeBytes(root))
    }

    @Test
    fun `empty directory measures zero`() {
        val root = newTempDir("jp-size-empty")

        assertEquals(0L, directorySizeBytes(root))
    }

    // ---------------------------------------------------------- symlink guard

    @Test
    fun `symlinked child directory is skipped`() {
        val root = newTempDir("jp-size-sym-child")
        val outside = newTempDir("jp-size-sym-outside")
        File(outside, "outside.bin").writeBytes(ByteArray(5_000))
        File(root, "inside.bin").writeBytes(ByteArray(100))
        linkOrSkip(File(root, "link"), outside)

        assertEquals(
            100L,
            directorySizeBytes(root),
            "the walk must not follow symlinked children (circular-link guard)",
        )
    }

    @Test
    fun `symlinked child file is skipped`() {
        val root = newTempDir("jp-size-sym-file")
        val outside = newTempDir("jp-size-sym-file-outside")
        File(outside, "big.bin").writeBytes(ByteArray(9_000))
        File(root, "inside.bin").writeBytes(ByteArray(50))
        linkOrSkip(File(root, "link.bin"), File(outside, "big.bin"))

        assertEquals(50L, directorySizeBytes(root))
    }

    @Test
    fun `symlinked root is still measured`() {
        val base = newTempDir("jp-size-sym-root")
        val real = File(base, "real").apply { mkdirs() }
        File(real, "cache.bin").writeBytes(ByteArray(300))
        val linked = File(base, "linked")

        linkOrSkip(linked, real)

        assertEquals(
            300L,
            directorySizeBytes(linked),
            "the root itself is never symlink-guarded — a cache dir the OS " +
                "symlinked onto external storage must still report its size",
        )
    }

    // ------------------------------------------------------------- depth cap

    @Test
    fun `deep tree beyond the depth cap is not descended`() {
        val root = newTempDir("jp-size-deep")
        var dir = root
        repeat(15) { dir = File(dir, "n$it").apply { mkdirs() } }
        // Depth 15 file — past MAX_DEPTH=10, never reached.
        File(dir, "bottom.bin").writeBytes(ByteArray(10_000))

        assertEquals(0L, directorySizeBytes(root), "nothing may be counted past the depth cap")
    }

    @Test
    fun `depth cap boundary - depth ten counted, depth eleven skipped`() {
        val root = newTempDir("jp-size-cap")
        // root(0) / shallow(1) … file in d9 sits at depth 10 → counted;
        // d10 is a dir at depth 10 → not descended → its file (11) skipped.
        File(root, "shallow.bin").writeBytes(ByteArray(1))
        var dir = root
        repeat(10) { i ->
            dir = File(dir, "d${i + 1}").apply { mkdirs() }
            File(dir, "deep.bin").writeBytes(ByteArray(2))
        }

        // counted: shallow(1) + deep files in d1..d9 (depths 2..10)
        assertEquals(1L + 9 * 2L, directorySizeBytes(root))
    }
}
