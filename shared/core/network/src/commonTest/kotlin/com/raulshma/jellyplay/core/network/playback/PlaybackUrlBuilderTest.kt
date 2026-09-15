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
            "$BASE/Videos/item1/stream?static=true&mediaSourceId=ms1&startTimeTicks=123456789&ApiKey=$KEY",
            buildStreamUrl(
                baseUrl = BASE, apiKey = KEY, userId = USER, userServerId = null,
                itemId = "item1", mediaSourceId = "ms1", startTimeTicks = 123456789,
            ),
        )
    }

    @Test
    fun `max bitrate is appended only when positive`() {
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&maxBitrate=8000&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", maxBitrate = 8000),
        )
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", maxBitrate = 0),
        )
    }

    @Test
    fun `audio universal endpoint carries deviceId and userId`() {
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=null&userId=$USER&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", useAudioEndpoint = true),
            "userServerId null interpolates verbatim (JVM parity: serverId is never populated)",
        )
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=srv&userId=$USER&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, "srv", "i", "m", useAudioEndpoint = true),
        )
    }

    @Test
    fun `live streams skip static and echo LiveStreamId`() {
        assertEquals(
            "$BASE/Videos/i/stream?mediaSourceId=m&startTimeTicks=7&LiveStreamId=ls1&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", startTimeTicks = 7, liveStreamId = "ls1"),
        )
        assertEquals(
            "$BASE/Audio/i/universal?mediaSourceId=m&startTimeTicks=0&deviceId=null&userId=$USER&LiveStreamId=ls2&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", useAudioEndpoint = true, liveStreamId = "ls2"),
        )
        // Blank live id counts as VOD.
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&ApiKey=$KEY",
            buildStreamUrl(BASE, KEY, USER, null, "i", "m", liveStreamId = ""),
        )
    }

    @Test
    fun `missing session inputs yield empty url`() {
        assertEquals("", buildStreamUrl(null, KEY, USER, null, "i", "m"))
        assertEquals("", buildStreamUrl(BASE, null, USER, null, "i", "m"))
    }

    @Test
    fun `book download url is the raw Download endpoint with api key`() {
        assertEquals(
            "$BASE/Items/item1/Download?ApiKey=$KEY",
            buildBookDownloadUrl(BASE, KEY, "item1"),
        )
    }

    @Test
    fun `trailing slash on the base url is trimmed`() {
        // Regression guard: the jvmShared impl once interpolated
        // activeBaseUrl raw, so a trailing-slash base used to
        // yield "//Videos/…" on the JVM only.
        assertEquals(
            "$BASE/Videos/i/stream?static=true&mediaSourceId=m&startTimeTicks=0&ApiKey=$KEY",
            buildStreamUrl("$BASE/", KEY, USER, null, "i", "m"),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/0/Stream.srt?ApiKey=$KEY",
            buildSubtitleDeliveryUrl("$BASE/", KEY, "i", "m", 0, null),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/1/Stream.vtt?ApiKey=$KEY",
            resolveSubtitleDeliveryUrl("$BASE/", KEY, "/Videos/i/m/Subtitles/1/Stream.vtt"),
        )
        assertEquals(
            "$BASE/Items/item1/Download?ApiKey=$KEY",
            buildBookDownloadUrl("$BASE/", KEY, "item1"),
        )
    }

    @Test
    fun `subtitle delivery url maps codecs and refuses image formats`() {
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/2/Stream.srt?ApiKey=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "subrip"),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/3/Stream.ass?ApiKey=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 3, "ASS"),
        )
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/0/Stream.srt?ApiKey=$KEY",
            buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 0, null),
            "null codec defaults to srt",
        )
        assertEquals("", buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "pgs"), "PGS refused")
        assertEquals("", buildSubtitleDeliveryUrl(BASE, KEY, "i", "m", 2, "vobsub"), "VOBSUB refused")
    }

    @Test
    fun `server delivery urls are absolutized with the right separator`() {
        assertEquals(
            "$BASE/Videos/i/m/Subtitles/1/Stream.vtt?ApiKey=$KEY",
            resolveSubtitleDeliveryUrl(BASE, KEY, "/Videos/i/m/Subtitles/1/Stream.vtt"),
        )
        assertEquals(
            "https://cdn.example/sub?track=9&ApiKey=$KEY",
            resolveSubtitleDeliveryUrl(BASE, KEY, "https://cdn.example/sub?track=9"),
        )
        assertEquals("", resolveSubtitleDeliveryUrl(null, KEY, "/sub"))
        assertEquals("", resolveSubtitleDeliveryUrl(BASE, null, "/sub"))
    }

    @Test
    fun `delivery url fold appends the token when absent`() {
        assertEquals(
            "$BASE/transcode?ApiKey=$KEY",
            resolveDeliveryUrlWithApiKey(BASE, "/transcode", KEY),
        )
        assertEquals(
            "$BASE/transcode?profile=main&ApiKey=$KEY",
            resolveDeliveryUrlWithApiKey(BASE, "/transcode?profile=main", KEY),
            "existing query params get the & separator",
        )
    }

    @Test
    fun `token-less delivery url fold trims the base trailing slash`() {
        // Regression guard: core/data's blank-token transcode branch once
        // hand-joined "$server$transcodeUrl", which produced "//path" for a
        // trailing-slash server URL — only this fold trims.
        assertEquals(
            "$BASE/transcode",
            resolveDeliveryUrl("$BASE/", "/transcode"),
        )
        assertEquals(
            "https://edge.example/hls/mono.m3u8",
            resolveDeliveryUrl(BASE, "https://edge.example/hls/mono.m3u8"),
            "an already-absolute delivery url passes through as-is",
        )
    }

    @Test
    fun `delivery url fold never double-appends a pre-baked token`() {
        assertEquals(
            "$BASE/transcode?ApiKey=baked",
            resolveDeliveryUrlWithApiKey(BASE, "/transcode?ApiKey=baked", KEY),
            "capital ApiKey already present is left untouched",
        )
        assertEquals(
            "$BASE/transcode?api_key=baked",
            resolveDeliveryUrlWithApiKey(BASE, "/transcode?api_key=baked", KEY),
            "legacy lowercase api_key (pre-12 servers) is left untouched",
        )
    }

    @Test
    fun `delivery url fold absolutizes and trims the base trailing slash`() {
        assertEquals(
            "$BASE/transcode?ApiKey=$KEY",
            resolveDeliveryUrlWithApiKey("$BASE/", "/transcode", KEY),
            "a trailing slash on the base can never produce a // path",
        )
        assertEquals(
            "https://edge.example/hls/mono.m3u8?ApiKey=$KEY",
            resolveDeliveryUrlWithApiKey(BASE, "https://edge.example/hls/mono.m3u8", KEY),
            "an already-absolute delivery url passes through as-is",
        )
    }
}
