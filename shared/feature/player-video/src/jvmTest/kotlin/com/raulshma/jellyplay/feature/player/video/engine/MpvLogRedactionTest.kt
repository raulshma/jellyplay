package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins [MpvLogRedaction] — every auth spelling an mpv log line can carry:
 * the capital `ApiKey` query param (Jellyfin 12) and its legacy lowercase
 * `api_key` alias, the percent-encoded form mpv prints, the legacy
 * `X-Emby-Token` header, and the `Authorization: MediaBrowser Token="…"`
 * header media fetches send. The engine delegates its `redactSensitive`
 * here, so this is the pin for the android engine's log redaction too.
 */
class MpvLogRedactionTest {

    @Test
    fun `redacts both ApiKey spellings and keeps the surrounding URL`() {
        assertEquals(
            "http://srv/v.mp4?ApiKey=***&x=1",
            MpvLogRedaction.redact("http://srv/v.mp4?ApiKey=abc123&x=1"),
        )
        assertEquals(
            "http://srv/v.mp4?api_key=***",
            MpvLogRedaction.redact("http://srv/v.mp4?api_key=abc123"),
        )
        // Case-insensitive, and the token ends at the next param or space.
        assertEquals(
            "OPEN url=http://srv/v.mp4?apikey=***&api-key=***",
            MpvLogRedaction.redact("OPEN url=http://srv/v.mp4?apikey=abc123&api-key=def456"),
        )
    }

    @Test
    fun `redacts the percent-encoded ApiKey form`() {
        assertEquals(
            "http://srv/v.mp4?ApiKey%3D***",
            MpvLogRedaction.redact("http://srv/v.mp4?ApiKey%3Dabc123"),
        )
    }

    @Test
    fun `redacts the legacy X-Emby-Token header`() {
        assertEquals(
            "header: X-Emby-Token: ***",
            MpvLogRedaction.redact("header: X-Emby-Token: abc123"),
        )
    }

    @Test
    fun `redacts the Authorization MediaBrowser token header`() {
        assertEquals(
            """header: Authorization: MediaBrowser Token="***""" + '"',
            MpvLogRedaction.redact("""header: Authorization: MediaBrowser Token="abc123""""),
        )
        // A line carrying several spellings redacts all of them at once.
        assertEquals(
            """auth Authorization: MediaBrowser Token="***"?ApiKey=*** X-Emby-Token: ***""",
            MpvLogRedaction.redact(
                """auth Authorization: MediaBrowser Token="abc123"?ApiKey=def456 X-Emby-Token: ghi789""",
            ),
        )
    }

    @Test
    fun `leaves tokenless lines untouched`() {
        assertEquals(
            "http://srv/v.mp4?other=1",
            MpvLogRedaction.redact("http://srv/v.mp4?other=1"),
        )
    }
}
