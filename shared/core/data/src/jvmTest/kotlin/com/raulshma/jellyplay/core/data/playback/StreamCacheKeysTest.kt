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
 *    the token param in both spellings, `ApiKey` and the legacy `api_key`,
 *    so token rotation does not invalidate cached bytes) and keeps
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
    fun `strips the ApiKey spelling and normalizes both spellings to one key`() {
        // The app's URL builders emit the capital ApiKey; pre-12 servers bake
        // the legacy lowercase alias. Both must strip, or a token rotation
        // (or the spelling mix itself) splits the cache in two.
        val newSpelling = stripVolatileQueryParams(
            "http://host/videos/abc/stream?ApiKey=secret&static=true",
            STRIP_SAFE_QUERY_PARAMS,
        )
        val legacySpelling = stripVolatileQueryParams(
            "http://host/videos/abc/stream?api_key=secret&static=true",
            STRIP_SAFE_QUERY_PARAMS,
        )

        assertEquals("http://host/videos/abc/stream?static=true", newSpelling)
        assertEquals(newSpelling, legacySpelling)
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

    // ── stripVolatileQueryParams (fast path skips the URI rewrite) ──────

    @Test
    fun `query without volatile params returns the url untouched`() {
        // Media chunk URLs rarely carry the token param (auth rides in
        // headers): the fast path must produce exactly what the URI rewrite
        // produced — here, the identity.
        val url = "http://host:8096/videos/abc/1.ts?DeviceId=dev1&static=true"

        assertEquals(url, stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS))
    }

    @Test
    fun `upper-cased param spelling takes the slow path and is kept`() {
        // The fast-path trigger is case-insensitive but stripping stays
        // case-sensitive: API_KEY must survive, exactly as before the fast path.
        val url = "http://host/stream?API_KEY=secret&static=true"

        assertEquals(url, stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS))
    }

    @Test
    fun `question mark in the fragment alone does not strip`() {
        // rawQuery is null (the '?' lives in the fragment) — the fast path
        // skips the rewrite and the URL passes through verbatim.
        val url = "http://host/stream#next?api_key=secret"

        assertEquals(url, stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS))
    }

    @Test
    fun `empty params set returns the url untouched`() {
        val url = "http://host/stream?static=true"

        assertEquals(url, stripVolatileQueryParams(url, emptySet()))
    }

    @Test
    fun `percent-escaped urls keep taking the URI rewrite path`() {
        // Pins the quirk the fast-path guards must preserve: the round trip
        // re-escapes '%' in the query, so %2F becomes %252F. The fast path
        // must never short-circuit a URL carrying an escape.
        assertEquals(
            "http://host/stream?Title=50%252F50",
            stripVolatileQueryParams("http://host/stream?Title=50%2F50", STRIP_SAFE_QUERY_PARAMS),
        )
    }

    @Test
    fun `registry authority urls keep taking the URI rewrite path`() {
        // Underscore hosts parse as registry-based authorities and the round
        // trip re-anchors them ("http://foo_bar/…" → "http:/…"). The fast
        // path must not swallow that normalization.
        assertEquals(
            "http:/videos/1.ts?static=true",
            stripVolatileQueryParams("http://foo_bar/videos/1.ts?static=true", STRIP_SAFE_QUERY_PARAMS),
        )
    }

    @Test
    fun `empty query still drops the bare question mark`() {
        // "…?" parses with an EMPTY (non-null) query, so the round trip
        // reprints the URL without it — the fast path preserves that.
        assertEquals(
            "http://host/stream",
            stripVolatileQueryParams("http://host/stream?", STRIP_SAFE_QUERY_PARAMS),
        )
    }

    @Test
    fun `malformed url without volatile params passes through unchanged`() {
        // Fallback path: the strip regex cannot match and the "?&"/"&&"
        // cleanup has nothing to clean — the URL comes back as-is, the same
        // identity the fast path would have returned.
        val url = "bad url?static=true"

        assertEquals(url, stripVolatileQueryParams(url, STRIP_SAFE_QUERY_PARAMS))
    }
}
