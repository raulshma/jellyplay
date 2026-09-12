package com.raulshma.jellyplay.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure helpers on [DownloadStorageLayout] — filename sanitization and
 * container-extension derivation. The full [DownloadStorageLayout.resolve]
 * path needs a Robolectric `Context` (covered transitively by
 * [DownloadRepositoryImplResumeTest]'s Robolectric setup); these helpers are
 * pure functions and get direct JVM tests.
 *
 * Pre-extraction these rules were inlined in `DownloadRepositoryImpl.startDownloadInternal`
 * and had no test surface — a malformed container string or a path-traversal
 * attempt in a media name would only surface as a corrupt filename at runtime.
 */
class DownloadStorageLayoutTest {

    // sanitizeName — exposed for testing via the internal visibility.
    // Constructed via the resolve() entry point's sibling helpers; we test the
    // pure pieces directly without a Context.

    private val layout = DownloadStorageLayout(context = mockContext())

    @Test
    fun `sanitizeName replaces unsafe filesystem characters with underscore`() {
        // Spaces, slashes, colons, quotes — all unsafe on Android FAT/exFat.
        assertEquals("My_Movie", layout.sanitizeName("My Movie"))
        assertEquals("a_b_c", layout.sanitizeName("a/b\\c"))
        // Each unsafe char becomes its own underscore — colon AND space both.
        assertEquals("Title__Colon", layout.sanitizeName("Title: Colon"))
        assertEquals("No_Quotes_", layout.sanitizeName("No\"Quotes'"))
    }

    @Test
    fun `sanitizeName keeps alphanumerics dots and dashes`() {
        assertEquals("Movie-2024.mp4", layout.sanitizeName("Movie-2024.mp4"))
        assertEquals("A.B.C-1", layout.sanitizeName("A.B.C-1"))
    }

    @Test
    fun `sanitizeName does not mutate already-safe names`() {
        val safe = "Inception.2010.UHD"
        assertEquals(safe, layout.sanitizeName(safe))
    }

    @Test
    fun `sanitizeName truncates path-traversal attempts`() {
        // "../etc/passwd" must never leak as a literal path component.
        val sanitized = layout.sanitizeName("../etc/passwd")
        assertNotEquals("../etc/passwd", sanitized)
        assertTrue("no slash should survive: $sanitized", !sanitized.contains("/"))
    }

    @Test
    fun `deriveExtension prefers the reported container`() {
        assertEquals("mkv", layout.deriveExtension("mkv", isAudioType = false))
        assertEquals("mp4", layout.deriveExtension("mp4", isAudioType = false))
        assertEquals("flac", layout.deriveExtension("flac", isAudioType = true))
    }

    @Test
    fun `deriveExtension falls back to mp4 for video when container missing`() {
        assertEquals("mp4", layout.deriveExtension(null, isAudioType = false))
        assertEquals("mp4", layout.deriveExtension("", isAudioType = false))
        assertEquals("mp4", layout.deriveExtension("   ", isAudioType = false))
    }

    @Test
    fun `deriveExtension falls back to mp3 for audio when container missing`() {
        assertEquals("mp3", layout.deriveExtension(null, isAudioType = true))
        assertEquals("mp3", layout.deriveExtension("", isAudioType = true))
    }

    @Test
    fun `deriveExtension rejects malformed containers that fail the regex`() {
        // Too short, too long, or containing non-alphanumerics: fall back.
        assertEquals("mp4", layout.deriveExtension("m", isAudioType = false))         // < 2 chars
        assertEquals("mp4", layout.deriveExtension("verylongcontainer", isAudioType = false)) // > 8 chars
        assertEquals("mp4", layout.deriveExtension("mp4;evil", isAudioType = false))  // non-alnum
        assertEquals("mp4", layout.deriveExtension("../etc", isAudioType = false))    // path-traversal
    }

    /**
     * A no-op Context stub. The pure helpers tested here never touch it; we
     * just need a non-null instance to construct the layout.
     */
    private fun mockContext(): android.content.Context = io.mockk.mockk(relaxed = true)
}
