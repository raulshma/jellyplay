package com.raulshma.jellyplay.desktop.player

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extraction-logic pins for the bundled Anime4K packs: the pure
 * needs-install decision matrix, idempotent re-extract, version-bump
 * re-extract, and the user-shader listing — all against a temp directory and
 * an in-memory resource opener (no real classpath or fixed filesystem).
 */
class Anime4KShaderInstallerTest {

    private fun tempDir(): Path = Files.createTempDirectory("jellyplay-shaders-test")

    /** In-memory "classpath": name → bytes. */
    private fun fakeOpener(vararg names: String): (String) -> java.io.InputStream? {
        val files = names.associateWith { name -> "glsl:$name".toByteArray() }
        return { name -> files[name]?.let { java.io.ByteArrayInputStream(it) } }
    }

    private val bundled = listOf("A.glsl", "B.glsl")

    // ── needsInstall: the pure policy matrix ────────────────────────────────

    @Test
    fun needsInstall_matrix() {
        val manifest = Anime4KShaderInstaller.Manifest(version = Anime4KShaderInstaller.VERSION, files = bundled)
        // Fresh install (no manifest).
        assertTrue(Anime4KShaderInstaller.needsInstall(null, Anime4KShaderInstaller.VERSION, allFilesPresent = true))
        // Current version + all files present → skip.
        assertFalse(
            Anime4KShaderInstaller.needsInstall(manifest, Anime4KShaderInstaller.VERSION, allFilesPresent = true),
        )
        // Version bump → re-extract.
        assertTrue(Anime4KShaderInstaller.needsInstall(manifest, "5.0.0", allFilesPresent = true))
        // User deleted a chain file → re-extract even at the same version.
        assertTrue(
            Anime4KShaderInstaller.needsInstall(manifest, Anime4KShaderInstaller.VERSION, allFilesPresent = false),
        )
    }

    @Test
    fun manifest_jsonRoundTrip() {
        val doc = Anime4KShaderInstaller.manifestJson(
            Anime4KShaderInstaller.Manifest(version = "4.0.1", files = listOf("A.glsl", "B.glsl")),
        )
        val parsed = Anime4KShaderInstaller.parseManifest(doc)
        assertEquals(Anime4KShaderInstaller.Manifest(version = "4.0.1", files = listOf("A.glsl", "B.glsl")), parsed)
        // Corrupt document → null (a missing manifest ⇒ re-extract).
        assertEquals(null, Anime4KShaderInstaller.parseManifest("{ not json"))
    }

    // ── ensureInstalled: idempotence + version bump against a real temp dir ─

    @Test
    fun ensureInstalled_firstRunExtracts_secondRunSkips() {
        val dir = tempDir()
        val installer = Anime4KShaderInstaller(
            targetDir = dir.toFile(),
            bundledFiles = bundled,
            resourceOpener = fakeOpener(*bundled.toTypedArray()),
        )
        assertEquals(Anime4KShaderInstaller.Result.INSTALLED, installer.ensureInstalled())
        for (name in bundled) {
            assertEquals("glsl:$name", dir.resolve(name).readText())
        }
        val manifest = Anime4KShaderInstaller.parseManifest(dir.resolve("manifest.json").readText())
        assertNotNull(manifest)
        assertEquals(Anime4KShaderInstaller.VERSION, manifest.version)

        // Idempotent: the second startup is a no-op (and does not rewrite —
        // the manifest file's mtime is the observable).
        val before = Files.getLastModifiedTime(dir.resolve("manifest.json"))
        assertEquals(Anime4KShaderInstaller.Result.CURRENT, installer.ensureInstalled())
        assertEquals(before, Files.getLastModifiedTime(dir.resolve("manifest.json")))
    }

    @Test
    fun ensureInstalled_userDeletedFile_reExtracts() {
        val dir = tempDir()
        val installer = Anime4KShaderInstaller(
            targetDir = dir.toFile(),
            bundledFiles = bundled,
            resourceOpener = fakeOpener(*bundled.toTypedArray()),
        )
        installer.ensureInstalled()
        Files.delete(dir.resolve("A.glsl"))
        assertEquals(Anime4KShaderInstaller.Result.INSTALLED, installer.ensureInstalled())
        assertEquals("glsl:A.glsl", dir.resolve("A.glsl").readText())
    }

    @Test
    fun ensureInstalled_missingBundledResource_degradesToFailedWithoutCrash() {
        val dir = tempDir()
        // The opener only knows one of the two bundled files (a broken build).
        val installer = Anime4KShaderInstaller(
            targetDir = dir.toFile(),
            bundledFiles = bundled,
            resourceOpener = fakeOpener("A.glsl"),
        )
        assertEquals(Anime4KShaderInstaller.Result.FAILED, installer.ensureInstalled())
        // No manifest written on the failed pass — the next launch retries.
        assertFalse(Files.exists(dir.resolve("manifest.json")))
    }

    // ── user shaders: non-bundled *.glsl in the same dir are listable ───────

    @Test
    fun listUserShaderFiles_returnsOnlyNonBundledGlslSorted() {
        val dir = tempDir()
        val installer = Anime4KShaderInstaller(
            targetDir = dir.toFile(),
            bundledFiles = bundled,
            resourceOpener = fakeOpener(*bundled.toTypedArray()),
        )
        // Empty/nonexistent dir → nothing.
        assertTrue(installer.listUserShaderFiles().isEmpty())

        installer.ensureInstalled()
        dir.resolve("FSRCNNN_x2.glsl").writeBytes(byteArrayOf())
        dir.resolve("artcnn.glsl").writeBytes(byteArrayOf())
        dir.resolve("readme.txt").writeText("not a shader")
        val users = installer.listUserShaderFiles()
        // Case-insensitive sort.
        assertEquals(listOf("artcnn.glsl", "FSRCNNN_x2.glsl"), users.map { it.name })
    }
}
