package com.raulshma.jellyplay.core.network.websocket

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `Sessions` WS push carries `Data` as a PascalCase SessionInfo[] array —
 * the shape the old consumer (`event.data.toString()` decoded as a
 * `List<SessionInfo>`) could never parse. These specimens pin the wire format,
 * wrapped in the full envelope [parseSessionsMessage] decodes.
 */
class SessionsPayloadTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun envelope(data: String): String =
        """{"MessageType":"Sessions","Data":$data}"""

    @Test
    fun decodesPascalCaseArrayWithPlayStateAndNowPlaying() {
        val rawText = envelope(
            """
            [
              {
                "Id": "abc123",
                "UserId": "u1",
                "UserName": "raul",
                "Client": "Jellyfin Web",
                "PlayState": {
                  "PositionTicks": 25000000000,
                  "IsPaused": false,
                  "IsMuted": false,
                  "VolumeLevel": 80,
                  "PlayMethod": "DirectPlay"
                },
                "NowPlayingItem": {
                  "Id": "item9",
                  "Name": "Pilot",
                  "SeriesName": "Some Show",
                  "RunTimeTicks": 1200000000000,
                  "Type": "Episode"
                }
              },
              {
                "Id": "other-session",
                "PlayState": { "IsPaused": true }
              }
            ]
            """
        )

        val sessions = parseSessionsMessage(json, rawText)

        assertEquals(2, sessions.size)
        val current = sessions.first { it.id == "abc123" }
        assertEquals("raul", current.userName)
        assertEquals(25_000_000_000L, current.playState?.positionTicks)
        assertEquals(false, current.playState?.isPaused)
        assertEquals(80, current.playState?.volumeLevel)
        assertEquals("Pilot", current.nowPlayingItem?.name)
        assertEquals("Some Show", current.nowPlayingItem?.seriesName)
    }

    @Test
    fun filtersBySessionIdBeforeMapping() {
        val rawText = envelope("""[{"Id":"a"},{"Id":"b"}]""")
        val match = parseSessionsMessage(json, rawText).firstOrNull { it.id == "b" }
        assertEquals("b", match?.id)
        assertNull(parseSessionsMessage(json, rawText).firstOrNull { it.id == "missing" })
    }

    @Test
    fun mapsOntoSharedSessionInfoModelWithTransportFields() {
        val rawText = envelope(
            """
            [{"Id":"s1","PlayState":{"PositionTicks":100,"IsPaused":true,"VolumeLevel":42},
              "NowPlayingItem":{"Id":"i1","Name":"Ep 1","SeriesName":"S","RunTimeTicks":9000}}]
            """
        )

        val model = parseSessionsMessage(json, rawText).single().toSessionInfo()

        assertEquals("s1", model.id)
        assertEquals(100L, model.playState?.positionTicks)
        assertEquals(true, model.playState?.isPaused)
        assertEquals(42, model.playState?.volumeLevel)
        assertEquals("i1", model.nowPlayingItem?.id)
        assertEquals("Ep 1", model.nowPlayingItem?.name)
        assertEquals("S", model.nowPlayingItem?.seriesName)
        assertEquals(9000L, model.nowPlayingItem?.runTimeTicks)
    }

    @Test
    fun toleratesMissingPlayStateAndUnknownKeys() {
        val rawText = envelope("""[{"Id":"s1","SomeFutureField":true}]""")
        val model = parseSessionsMessage(json, rawText).single().toSessionInfo()
        assertEquals("s1", model.id)
        assertNull(model.playState)
        assertNull(model.nowPlayingItem)
        assertTrue(model.userName.isEmpty())
    }

    @Test
    fun toleratesMissingDataField() {
        val rawText = """{"MessageType":"Sessions"}"""
        assertTrue(parseSessionsMessage(json, rawText).isEmpty())
    }
}
