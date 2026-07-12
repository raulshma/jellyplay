package com.raulshma.jellyplay.feature.player.video.subtitle

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure, context-free seams of [FontProvider]:
 * - [FontProvider.writeFontsConf] generates a valid fontconfig XML with the
 *   expected system-font dirs, sans-serif alias and cachedir.
 * - [FontProvider.parseFontFamily] returns null (never throws) for a corrupt or
 *   non-font file.
 *
 * File-copy and Context.assets paths are exercised by instrumented tests in a
 * later task; this JVM suite only asserts the deterministic logic.
 */
class FontProviderTest {

    @Test
    fun writeFontsConf_producesValidXmlWithAliases() {
        val dir = createTempDirectory(prefix = "fp_test").toFile()
        val cacheDir = createTempDirectory(prefix = "fp_cache").toFile()
        try {
            FontProvider.writeFontsConf(dir, cacheDir)
            val conf = File(dir, "fonts.conf")
            assertTrue("fonts.conf should be created", conf.exists())
            val text = conf.readText()
            assertTrue("must reference system fonts dir", text.contains("/system/fonts/"))
            assertTrue("must include sans-serif alias", text.contains("sans-serif"))
            assertTrue("must include cachedir", text.contains(cacheDir.absolutePath))
        } finally {
            dir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun parseFontFamily_corruptOrMissingFile_returnsNull() {
        val dir = createTempDirectory(prefix = "fp_test").toFile()
        try {
            val bogus = File(dir, "not-a-font.ttf")
            bogus.writeBytes(byteArrayOf(0, 1, 2, 3))
            val family = FontProvider.parseFontFamily(bogus)
            assertEquals(null, family)
        } finally {
            dir.deleteRecursively()
        }
    }
}
