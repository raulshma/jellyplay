package com.raulshma.jellyplay.feature.player.video.subtitle

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure, context-free seam of [FontProvider]:
 * - [FontProvider.parseFontFamily] returns null (never throws) for a corrupt or
 *   non-font file.
 *
 * File-copy, stale-fonts.conf cleanup, and Context.assets paths are exercised by
 * instrumented tests; this JVM suite only asserts the deterministic parse logic.
 */
class FontProviderTest {

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
