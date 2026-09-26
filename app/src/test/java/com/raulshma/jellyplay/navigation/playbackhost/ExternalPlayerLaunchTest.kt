package com.raulshma.jellyplay.navigation.playbackhost

import android.content.Intent
import org.junit.Assert.assertEquals
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
 * side — the "position"/"positionMs" alias — is already pinned by
 * `ExternalPlayerPositionTicksTest`): ACTION_VIEW with the video MIME type,
 * the `title`/`return_result` extras, the ms `position` extra with its zero
 * gate, and a fresh playSessionId per launch.
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
}
