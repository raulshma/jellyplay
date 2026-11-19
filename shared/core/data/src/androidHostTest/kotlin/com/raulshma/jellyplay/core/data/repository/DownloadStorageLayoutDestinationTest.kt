package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [DownloadStorageLayout.destinationBaseDir]'s pref → directory mapping —
 * the same rule [DownloadStorageLayout.resolve] writes downloads through, and
 * the one the download-sheet free-space probe stats. Regression guard for the
 * probe's hardcoded primary-external volume: with an SD card selected as the
 * download location, the sheet reported internal storage's free space.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadStorageLayoutDestinationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val layout = DownloadStorageLayout(context)

    @Test
    fun `INTERNAL selects the filesDir downloads subtree`() {
        assertEquals(File(context.filesDir, "downloads"), layout.destinationBaseDir("MOVIE", "INTERNAL"))
        assertEquals(File(context.filesDir, "downloads/music"), layout.destinationBaseDir("AUDIO", "INTERNAL"))
        // Created on demand, exactly as resolve() always did.
        assertTrue(File(context.filesDir, "downloads").exists())
    }

    @Test
    fun `EXTERNAL selects the primary app-private external root per media type`() {
        assertEquals(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            layout.destinationBaseDir("MOVIE", "EXTERNAL"),
        )
        assertEquals(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            layout.destinationBaseDir("AUDIO", "EXTERNAL"),
        )
        // Not the filesDir subtree — a genuinely different volume path.
        val dir = layout.destinationBaseDir("MOVIE", "EXTERNAL")
        assertFalse(dir.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun `out-of-range EXTERNAL_N falls back to the last available root`() {
        // Robolectric exposes a single external root, so EXTERNAL_9 (SD card
        // unmounted, index gone) must coerce onto it — the filesDir fallback
        // only triggers when NO external root exists.
        assertEquals(
            layout.destinationBaseDir("MOVIE", "EXTERNAL"),
            layout.destinationBaseDir("MOVIE", "EXTERNAL_9"),
        )
    }

    @Test
    fun `unparseable pref is treated as primary external like resolve does`() {
        assertEquals(
            layout.destinationBaseDir("MOVIE", "EXTERNAL"),
            layout.destinationBaseDir("MOVIE", "SD_CARD_GARBAGE"),
        )
    }
}
