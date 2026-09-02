package com.raulshma.jellyplay.core.network.playback

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the pure stream/subtitle URL builders against the exact strings the
 * jvmShared PlaybackApiClientImpl produces (query params, ticks, api_key
 * suffix, static prefix, LiveStreamId echo, audio-universal shape).
 */
class PlaybackUrlBuilderTest {

    private val BASE = "https://jf.example"
    private val KEY = "token-1"
    private val USER = "user-9"

    @Test
    fun `video stream url uses static prefix with ticks and api key`() {
        assertEquals(
            "$BASE/Videos/item1/stream?static=true&mediaSourceId=ms1&startTimeTicks=123456789&api_key=$KEY",
            buildStreamUrl(
                baseUrl = BASE, apiKey = KEY, userId = USER, userServerId = null,
                itemId = "item1", mediaSourceId = "ms1", startTimeTicks = 123456789,
            ),
        )
    }

    @Test
    fun `max bitrate is appended only when positive`() {
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&maxBitrate=8000&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", maxBitrate = 8000),
        )
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", maxBitrate = 0),
        )
    }

    @Test
    fun `audio universal endpoint carries deviceId and userId`() {
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=null&userId=$USER&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", useAudioEndpoint = true),
            "userServerId null interpolates verbatim (JVM parity: serverId is never populated)",
        )
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=srv&userId=$USER&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, "srv", "i", "m", useAudioEndpoint = true),
        )
    }

    @Test
    fun `live streams skip static and echo LiveStreamId`() {
        assertEquals(
            "$BASE/Videos/i/stream?mediaSourceId=m&startTimeTicks=7&LiveStreamId=ls1&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", startTimeTicks = 7, liveStreamId = "ls1"),
        )
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=null&userId=$USER&LiveStreamId=ls2&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", useAudioEndpoint = true, liveStreamId = "ls2"),
        )
        // Blank live id counts as VOD.
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&api_key=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", liveStreamId = ""),
        )
    }

    @Test
    fun `missing session inputs yield empty url`() {
        assertEquals("", buildStreamUrl(null, KEY, USER, null, "i", "m"))
        assertEquals("", buildStreamUrl(BASE, null, USER, null, "i", "m"))
    }

    @Test
    fun `subtitle delivery url maps codecs and refuses image formats`() {
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/2/Stream.srt?api_key=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "subrip"),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/3/Stream.ass?api_key=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 3, "ASS"),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/0/Stream.srt?api_key=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 0, null),
            "null codec defaults to srt",
        )
        assertEquals("", buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "pgs"), "PGS refused")
        assertEquals("", buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "vobsub"), "VOBSUB refused")
    }

    @Test
    fun `server delivery urls are absolutized with the right separator`() {
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/1/Stream.vtt?api_key=$KEY",
            resolveSubtitleDeliveryUrl(BASE, KEY, "/Videos/i/m/Subtitles/1/Stream.vtt"),
        )
        assertEquals(
            "https://cdn.example/sub?track=9&api_key=$KEY",
            resolveSubtitleDeliveryUrl(BASE, KEY, "https://cdn.example/sub?track=9"),
        )
        assertEquals("", resolveSubtitleDeliveryUrl(null, KEY, "/sub"))
        assertEquals("", resolveSubtitleDeliveryUrl(BASE, null, "/sub"))
    }
}
