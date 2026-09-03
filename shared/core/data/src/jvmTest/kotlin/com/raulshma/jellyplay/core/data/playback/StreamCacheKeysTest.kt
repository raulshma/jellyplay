package com.raulshma.jellyplay.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the byte-cache key hygiene rules in [StreamCacheKeys] shared by the
 * audio and video stream caches:
 *
 *  - [isSessionKeyedUrl] rejects any URL carrying a session-scoped param
 *    (`PlaySessionId` / `TranscodingJobId` / `LiveStreamId`), case-insensitively
 *    — such URLs must never be byte-cached because the bytes differ per session;
 *  - [stripVolatileQueryParams] removes only the strip-safe params (today:
 *    `api_key`, so token rotation does not invalidate cached bytes) and keeps
 *    every content-bearing param, via the primary [java.net.URI] rewrite;
 *  - a URL with no query passes through untouched;
 *  - a URL [java.net.URI] cannot parse falls back to the regex strip instead of
 *    throwing.
 */
class StreamCacheKeysTest {

    // ── isSessionKeyedUrl ───────────────────────────────────────────────

    @Test
    fun `url with PlaySessionId is session keyed`() {
        assertTrue(isSessionKeyedUrl("http://host/videos/1/stream?PlaySessionId=abc"))
    }

    @Test
    fun `url with TranscodingJobId is session keyed`() {
        assertTrue(isSessionKeyedUrl("http://host/videos/1/stream?TranscodingJobId=j1"))
    }

    @Test
    fun `url with LiveStreamId is session keyed`() {
        assertTrue(isSessionKeyedUrl("http://host/live?LiveStreamId=abc"))
    }

    @Test
    fun `session param detection is case insensitive`() {
        assertTrue(isSessionKeyedUrl("http://host/stream?playsessionid=abc"))
        assertTrue(isSessionKeyedUrl("http://host/stream?PLAYSESSIONID=abc"))
        assertTrue(isSessionKeyedUrl("http://host/stream?Transcodingjobid=abc"))
    }

    @Test
    fun `plain stream url with only content params is not session keyed`() {
        assertFalse(isSessionKeyedUrl("http://host/videos/1/stream?static=true&api_key=tok"))
        assertFalse(isSessionKeyedUrl("http://host/audio/1/stream.m3u8"))
    }

    // ── stripVolatileQueryParams (URI rewrite path) ─────────────────────

    @Test
    fun `strips api_key and keeps all other params`() {
        val stripped = stripVolatileQueryParams(
            "http://host:8096/videos/abc/stream?api_key=secret&DeviceId=dev1&static=true",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host:8096/videos/abc/stream?DeviceId=dev1&static=true", stripped)
    }

    @Test
    fun `strips api_key from the middle of the query preserving order`() {
        val stripped = stripVolatileQueryParams(
            "http://host/stream?a=1&api_key=secret&b=2",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host/stream?a=1&b=2", stripped)
    }

    @Test
    fun `query that only held api_key drops the query entirely`() {
        val stripped = stripVolatileQueryParams(
            "http://host/stream?api_key=secret",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host/stream", stripped)
    }

    @Test
    fun `params other than api_key are never stripped`() {
        // StartAt / PlaySessionId are content-bearing or session-scoped — the
        // strip must be limited to the passed set, never a broad sweep.
        val stripped = stripVolatileQueryParams(
            "http://host/stream?PlaySessionId=abc&StartTimeTicks=100",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host/stream?PlaySessionId=abc&StartTimeTicks=100", stripped)
    }

    @Test
    fun `fragment survives the rewrite`() {
        val stripped = stripVolatileQueryParams(
            "http://host/stream?api_key=secret#audio",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host/stream#audio", stripped)
    }

    @Test
    fun `url without a query is returned unchanged`() {
        val url = "http://host/stream.m3u8"

        assertEquals(url, stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS))
    }

    @Test
    fun `stripping a volatile param produces a different key than the original`() {
        val original = "http://host/stream?api_key=tokenA&static=true"
        val rotated = "http://host/stream?api_key=tokenB&static=true"

        assertEquals(
            stripVolatileQueryParams(original, STRIP_SAFE_QUERY_PARAMS),
            stripVolatileQueryParams(rotated, STRIP_SAFE_QUERY_PARAMS),
        )
        assertNotEquals(original, stripVolatileQueryParams(original, STRIP_SAFE_QUERY_PARAMS))
    }

    // ── stripVolatileQueryParams (malformed-URL fallback path) ──────────

    @Test
    fun `malformed url falls back to the regex strip without throwing`() {
        // A space makes URI(url) throw; the fallback regex still removes the
        // api_key pair.
        val stripped = stripVolatileQueryParams(
            "bad url?api_key=secret",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("bad url", stripped)
    }

    @Test
    fun `malformed url fallback keeps non-volatile params`() {
        val stripped = stripVolatileQueryParams(
            "bad url?api_key=secret&static=true",
            STRIP_SAFE_QUERY_PARAMS,
        )

        // The regex removes "?api_key=secret"; the remainder keeps the "&"
        // separator as-is — imperfect, but the volatile pair is gone.
        assertEquals("bad url&static=true", stripped)
        assertTrue("static=true" in stripped)
        assertFalse("api_key" in stripped)
    }

    @Test
    fun `fallback regex is memoized per param set`() {
        // Same call shape twice: must be stable (and not blow up on cache reuse).
        val first = stripVolatileQueryParams("bad url?api_key=x&keep=1", STRIP_SAFE_QUERY_PARAMS)
        val second = stripVolatileQueryParams("bad url?api_key=x&keep=1", STRIP_SAFE_QUERY_PARAMS)

        assertEquals(first, second)
        assertEquals("bad url&keep=1", first)
    }
}
