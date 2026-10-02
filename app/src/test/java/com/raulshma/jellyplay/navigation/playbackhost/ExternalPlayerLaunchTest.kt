package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [externalPlayerLaunch] — the OUTBOUND extras vocabulary of the
 * external-player hand-off, moved verbatim out of MainViewModel so the
 * launch-spec construction lives beside [ExternalPlayerHost] (the RESULT
 * side — the per-contract outcome parse — is pinned by
 * `ExternalPlayerPositionTicksTest`): ACTION_VIEW with the video MIME type,
 * the `title`/`return_result` extras, the ms `position` extra with its zero
 * gate, a fresh playSessionId per launch, and the subtitle hand-off extras
 * (`subs`/`subs.name`/`subs.filename`/`subs.enable`, plus VLC's
 * `subtitles_location`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExternalPlayerLaunchTest {

    @Test
    fun `builds an action_view intent with the resolved url typed video and the title extra`() {
        val launch = externalPlayerLaunch(
            itemId = "item-1",
            resolvedUrl = "https://server/videos/1/stream",
            title = "Movie",
            startPositionTicks = 0L,
        )

        assertEquals(Intent.ACTION_VIEW, launch.intent.action)
        assertEquals("https://server/videos/1/stream", launch.intent.data.toString())
        assertEquals("video/*", launch.intent.type)
        assertEquals("Movie", launch.intent.getStringExtra("title"))
        assertEquals("item-1", launch.itemId)
    }

    @Test
    fun `advertises return_result and carries the resume position in milliseconds`() {
        val launch = externalPlayerLaunch(
            itemId = "item-1",
            resolvedUrl = "https://server/v",
            title = "Movie",
            startPositionTicks = 900_000_000L,
        )

        assertTrue(launch.intent.getBooleanExtra("return_result", false))
        // Ticks → ms (÷ 10 000).
        assertEquals(90_000L, launch.intent.getLongExtra("position", -1L))
        assertEquals(900_000_000L, launch.startPositionTicks)
    }

    @Test
    fun `a zero start position omits the position extra entirely`() {
        val launch = externalPlayerLaunch(
            itemId = "item-1",
            resolvedUrl = "file:///data/movie.mp4",
            title = "Downloaded",
            startPositionTicks = 0L,
        )

        assertEquals(-1L, launch.intent.getLongExtra("position", -1L))
    }

    @Test
    fun `each launch mints a fresh play session id`() {
        val first = externalPlayerLaunch("item-1", "https://server/v", "T", 0L)
        val second = externalPlayerLaunch("item-1", "https://server/v", "T", 0L)

        assertTrue(first.playSessionId.isNotBlank())
        assertNotEquals(first.playSessionId, second.playSessionId)
    }

    // ── subtitle hand-off extras ───────────────────────────────────────────

    @Test
    fun `subtitle payload populates the subs uri array with the name and filename faces`() {
        val launch = externalPlayerLaunch(
            itemId = "item-1",
            resolvedUrl = "https://server/v",
            title = "Movie",
            startPositionTicks = 0L,
            subtitles = listOf(
                ExternalSubtitle("https://server/v/1.srt", "English (SRT)", "eng"),
                ExternalSubtitle("https://server/v/2.ass", "Deutsch", "ger"),
            ),
        )

        val subs = launch.intent.getParcelableArrayListExtra<Uri>("subs")!!
        assertEquals(listOf("https://server/v/1.srt", "https://server/v/2.ass"), subs.map { it.toString() })
        assertTrue(launch.intent.hasExtra("subs.name"))
        assertEquals(listOf("English (SRT)", "Deutsch"), launch.intent.getStringArrayExtra("subs.name")!!.toList())
        assertTrue(launch.intent.hasExtra("subs.filename"))
        assertEquals(listOf("eng", "ger"), launch.intent.getStringArrayExtra("subs.filename")!!.toList())
        assertEquals(2, launch.subtitles.size)
    }

    @Test
    fun `the selected track rides subs_enable and vlc also gets subtitles_location`() {
        val launch = externalPlayerLaunch(
            itemId = "item-1",
            resolvedUrl = "https://server/v",
            title = "Movie",
            startPositionTicks = 0L,
            subtitles = listOf(
                ExternalSubtitle("https://server/v/1.srt", "English", "eng"),
                ExternalSubtitle("https://server/v/2.ass", "Deutsch", "ger", isSelected = true),
            ),
            preferredApp = ExternalPlayerApp.VLC,
        )

        assertEquals(
            "https://server/v/2.ass",
            launch.intent.getParcelableExtra<Uri>("subs.enable")!!.toString(),
        )
        assertEquals("https://server/v/2.ass", launch.intent.getStringExtra("subtitles_location"))
    }

    @Test
    fun `subtitles_location is a vlc-only extra`() {
        val subtitles = listOf(ExternalSubtitle("https://server/v/1.srt", "English", "eng", isSelected = true))

        val mpvLaunch = externalPlayerLaunch(
            "item-1", "https://server/v", "Movie", 0L,
            subtitles = subtitles, preferredApp = ExternalPlayerApp.MPV,
        )
        val chooserLaunch = externalPlayerLaunch(
            "item-1", "https://server/v", "Movie", 0L,
            subtitles = subtitles,
        )

        assertFalse(mpvLaunch.intent.hasExtra("subtitles_location"))
        assertFalse(chooserLaunch.intent.hasExtra("subtitles_location"))
    }

    @Test
    fun `no selection means the enable and location extras are omitted`() {
        val launch = externalPlayerLaunch(
            "item-1", "https://server/v", "Movie", 0L,
            subtitles = listOf(ExternalSubtitle("https://server/v/1.srt", "English", "eng")),
            preferredApp = ExternalPlayerApp.VLC,
        )

        assertFalse(launch.intent.hasExtra("subs.enable"))
        assertFalse(launch.intent.hasExtra("subtitles_location"))
    }

    @Test
    fun `a launch without subtitles carries none of the subtitle extras`() {
        val launch = externalPlayerLaunch("item-1", "https://server/v", "Movie", 0L)

        assertFalse(launch.intent.hasExtra("subs"))
        assertFalse(launch.intent.hasExtra("subs.name"))
        assertFalse(launch.intent.hasExtra("subs.filename"))
        assertFalse(launch.intent.hasExtra("subs.enable"))
        assertFalse(launch.intent.hasExtra("subtitles_location"))
        assertTrue(launch.subtitles.isEmpty())
    }
}
