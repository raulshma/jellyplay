package com.raulshma.jellyplay.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Android 16 QPR lock-screen widget curation: all widgets are
 * lock-screen eligible by default on API 36+, and the opt-OUT is the
 * `not_keyguard` category declared in an `xml-v36` metadata override. The
 * curation shows ONLY Now Playing + Continue Watching on the lock screen —
 * both grid widgets (Library / Seerr recommendations) must carry the
 * `not_keyguard` category in their xml-v36 copies, and neither of the two
 * curated widgets may declare it (their xml files have no override at all —
 * the legacy `keyguard` value is a no-op).
 *
 * File-based parse (the app unit-test lane has no resource table — see the
 * WidgetWorkSchedulerTest KDoc): the metadata XMLs are text resources, so the
 * source tree is the same source of truth the merger packages.
 */
class LockScreenWidgetCurationTest {

    /** app/src/main/res — the test's working dir is the app module dir. */
    private val resDir: File = File(System.getProperty("user.dir"))
        .resolve("src/main/res")

    private fun categoryOf(resFolder: String, fileName: String): String {
        val text = resDir.resolve("$resFolder/$fileName").readText()
        val match = Regex("android:widgetCategory\\s*=\\s*\"([^\"]+)\"").find(text)
        return match?.groupValues.orEmpty().firstOrNull().orEmpty()
    }

    @Test
    fun `library recommendations opts out of the lock screen on API 36`() {
        val category = categoryOf("xml-v36", "library_recommendations_widget_info.xml")
        assertTrue(
            "the xml-v36 library grid must declare home_screen|not_keyguard",
            "not_keyguard" in category && "home_screen" in category,
        )
    }

    @Test
    fun `seerr recommendations opts out of the lock screen on API 36`() {
        val category = categoryOf("xml-v36", "seerr_recommendations_widget_info.xml")
        assertTrue(
            "the xml-v36 seerr grid must declare home_screen|not_keyguard",
            "not_keyguard" in category && "home_screen" in category,
        )
    }

    @Test
    fun `the curated widgets have no not_keyguard override`() {
        // Now Playing + Continue Watching stay lock-screen eligible: their base
        // metadata must not declare the opt-out, and no xml-v36 override file
        // may exist for them.
        for (fileName in listOf("now_playing_widget_info.xml", "continue_watching_widget_info.xml")) {
            assertFalse(
                "$fileName must not declare not_keyguard",
                "not_keyguard" in categoryOf("xml", fileName),
            )
            assertFalse(
                "$fileName must have no xml-v36 override file",
                resDir.resolve("xml-v36/$fileName").exists(),
            )
        }
    }

    @Test
    fun `now playing renders its full layout at the lock-screen host size`() {
        // The lock-screen host renders ~4 cells wide x ~3 cells tall — far
        // above the 280dp full-ladder width and the 100dp progress-row height.
        val layout = responsiveNowPlayingLayout(widthDp = 320, heightDp = 220)
        assertTrue(layout.showAlbumArt)
        assertTrue(layout.showProgressContainer)
        assertTrue(layout.showPosition)
        assertTrue(layout.showRewind && layout.showForward)
        assertTrue(layout.showPlayPause)
        assertTrue(WidgetLayoutThresholds.CW_HEADER_HIDE_HEIGHT_DP < 220)
    }
}
