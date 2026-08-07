package com.raulshma.jellyplay.feature.player.video

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenshotSaverTest {

    @Test
    fun `sanitizeTitle keeps alphanumerics, spaces, underscores and dashes`() {
        assertEquals("Episode 5 - Finale", ScreenshotSaver.sanitizeTitle("Episode 5 - Finale"))
    }

    @Test
    fun `sanitizeTitle strips illegal filesystem characters`() {
        assertEquals("RickMorty S01E01", ScreenshotSaver.sanitizeTitle("Rick&Morty: S01E01!"))
    }

    @Test
    fun `sanitizeTitle blanks to frame`() {
        assertEquals("frame", ScreenshotSaver.sanitizeTitle(""))
        assertEquals("frame", ScreenshotSaver.sanitizeTitle("   "))
    }

    @Test
    fun `sanitizeTitle falls back to frame when only illegal characters`() {
        assertEquals("frame", ScreenshotSaver.sanitizeTitle("###???"))
    }

    @Test
    fun `sanitizeTitle trims surrounding whitespace`() {
        assertEquals("Jaws", ScreenshotSaver.sanitizeTitle("  Jaws  "))
    }

    @Test
    fun `sanitizeTitle keeps mixed case and numbers`() {
        assertEquals("Blade Runner 2049", ScreenshotSaver.sanitizeTitle("Blade Runner 2049"))
    }

    @Test
    fun `buildShareIntent sets mime type`() {
        val intent = ScreenshotSaver.buildShareIntent(Uri.parse("content://media/external/images/media/1"))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
    }

    @Test
    fun `buildShareIntent carries the uri as stream extra and grants read access`() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val intent = ScreenshotSaver.buildShareIntent(uri)

        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(uri, stream)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}