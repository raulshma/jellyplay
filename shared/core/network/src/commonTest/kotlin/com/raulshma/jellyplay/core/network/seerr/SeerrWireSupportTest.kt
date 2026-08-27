package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wasm Seerr client's wire string-shaping helpers against the
 * jvmShared `SeerrApiClientImpl` behavior they substitute for: the verbatim
 * `parseErrorMessage` texts, the login Set-Cookie join, the URLEncoder
 * stand-in, the credential→header selection (the OkHttp `withAuth` `when`),
 * the `/api/v1` URL join, and the hand-assembled request/discover paths.
 */
class SeerrWireSupportTest {

    // ── parseErrorMessage (SeerrApiClientImpl.parseErrorMessage) ───────────

    @Test
    fun `seerr error message extracts the quoted message field`() {
        assertEquals(
            "HTTP 401: Unauthorized",
            seerrHttpErrorMessage(401, """{"message":"Unauthorized"}"""),
        )
    }

    @Test
    fun `seerr error message passes non-string message values through raw`() {
        // message?.toString()?.trim('"') — a number keeps its digits, an
        // object keeps its compact JSON (quote-trim is a no-op there).
        assertEquals("HTTP 404: 404", seerrHttpErrorMessage(404, """{"message":404}"""))
        assertEquals(
            """HTTP 500: {"nested":true}""",
            seerrHttpErrorMessage(500, """{"message":{"nested":true}}"""),
        )
    }

    @Test
    fun `seerr error message echoes the body when the message field is blank`() {
        assertEquals(
            """HTTP 401: {"message":""}""",
            seerrHttpErrorMessage(401, """{"message":""}"""),
        )
    }

    @Test
    fun `seerr error message falls back to the first 200 chars for non-JSON bodies`() {
        val body = "oops <html> gateway " + "x".repeat(300)
        assertEquals("HTTP 502: ${body.take(200)}", seerrHttpErrorMessage(502, body))
    }

    @Test
    fun `seerr error message falls back for JSON arrays - not objects`() {
        // `.jsonObject` throws on arrays, same as the JVM catch branch.
        assertEquals("HTTP 404: [1,2]", seerrHttpErrorMessage(404, "[1,2]"))
    }

    @Test
    fun `seerr error message with an empty body degrades to the trailing colon`() {
        // parseJsonRequest feeds body.orEmpty() on the error path; "" fails
        // JSON parsing, so the JVM text is "HTTP $code: " with nothing after.
        assertEquals("HTTP 403: ", seerrHttpErrorMessage(403, ""))
    }

    // ── Set-Cookie join (executeRequestWithCookie) ──────────────────────────

    @Test
    fun `cookie join strips attributes and joins name-value pairs`() {
        assertEquals(
            "connect.sid=abc123; oid=irrelevant",
            joinSetCookieHeader(
                listOf(
                    "connect.sid=abc123; Path=/; HttpOnly; SameSite=Strict",
                    "oid=irrelevant; Path=/",
                ),
            ),
        )
    }

    @Test
    fun `cookie join of no values is null`() {
        assertNull(joinSetCookieHeader(emptyList()))
    }

    @Test
    fun `cookie join of a single header keeps its value intact`() {
        assertEquals("connect.sid=s%3Axyz.token", joinSetCookieHeader(listOf("connect.sid=s%3Axyz.token; Path=/")))
    }

    // ── urlFormEncode (java.net.URLEncoder UTF-8 semantics) ─────────────────

    @Test
    fun `form encoding passes URLEncoder-safe characters through`() {
        // Alphanumerics plus `.`, `-`, `*`, `_` are safe in URLEncoder.
        assertEquals("2024-01-01", urlFormEncode("2024-01-01"))
        assertEquals("a.b_c*d-e", urlFormEncode("a.b_c*d-e"))
        assertEquals("Abc123", urlFormEncode("Abc123"))
    }

    @Test
    fun `form encoding writes plus for spaces and percent-encodes reserved characters`() {
        assertEquals("star+wars", urlFormEncode("star wars"))
        assertEquals("a%26b%3Dc", urlFormEncode("a&b=c"))
        assertEquals("%2B49", urlFormEncode("+49"))
    }

    @Test
    fun `form encoding percent-encodes non-ASCII as uppercase UTF-8 bytes`() {
        assertEquals("caf%C3%A9", urlFormEncode("café"))
        assertEquals("%E6%97%A5%E6%9C%AC", urlFormEncode("日本"))
    }

    @Test
    fun `form encoding is byte-whole for multi-byte characters`() {
        // Encoding the whole string up front means surrogate pairs become one
        // UTF-8 sequence, never two split surrogate halves.
        assertEquals("%F0%9F%8E%AC", urlFormEncode("\uD83C\uDFAC"))
    }

    // ── withAuth as data (credential → header selection) ────────────────────

    @Test
    fun `api key credentials select the X-Api-Key header`() {
        assertEquals(
            listOf("X-Api-Key" to "my-seerr-key"),
            seerrAuthHeaders(SeerrCredentials.ApiKey("my-seerr-key")),
        )
    }

    @Test
    fun `session cookie credentials select the Cookie header`() {
        assertEquals(
            listOf("Cookie" to "connect.sid=abc"),
            seerrAuthHeaders(SeerrCredentials.SessionCookie("connect.sid=abc")),
        )
    }

    // ── URL join (buildUrl) ─────────────────────────────────────────────────

    @Test
    fun `seerr url join trims the base trailing slash and prefixes the api root`() {
        assertEquals("http://seerr:5055/api/v1/status", seerrApiUrl("http://seerr:5055/", "/status"))
        assertEquals("http://seerr:5055/api/v1/status", seerrApiUrl("http://seerr:5055", "/status"))
        assertEquals(
            "http://host/seerr/api/v1/request/7",
            seerrApiUrl("http://host/seerr///", "/request/7"),
            "a reverse-proxy subpath keeps its inner slashes; only trailing ones are trimmed",
        )
    }

    // ── hand-assembled paths (getRequests + discover) ────────────────────────

    @Test
    fun `requests path carries the six always-present params in JVM order`() {
        assertEquals(
            "/request?take=10&skip=0&filter=pending&sort=added&sortDirection=desc",
            seerrRequestsPath(
                take = 10, skip = 0, filter = "pending", sort = "added",
                sortDirection = "desc", requestedBy = null, mediaType = null, search = null,
            ),
        )
    }

    @Test
    fun `requests path appends optional params in JVM order and encodes search`() {
        assertEquals(
            "/request?take=20&skip=40&filter=all&sort=added&sortDirection=asc&requestedBy=3&mediaType=movie&search=star+wars",
            seerrRequestsPath(
                take = 20, skip = 40, filter = "all", sort = "added", sortDirection = "asc",
                requestedBy = 3, mediaType = "movie", search = "star wars",
            ),
        )
    }

    @Test
    fun `requests path skips a blank search entirely`() {
        // `search?.takeIf { it.isNotBlank() }` — the JVM drops blank searches.
        assertEquals(
            "/request?take=10&skip=0&filter=pending&sort=added&sortDirection=desc",
            seerrRequestsPath(
                take = 10, skip = 0, filter = "pending", sort = "added",
                sortDirection = "desc", requestedBy = null, mediaType = null, search = "   ",
            ),
        )
    }

    @Test
    fun `discover paths always carry page and encode the date filter only when present`() {
        assertEquals("/discover/movies?page=1", seerrDiscoverMoviesPath(1, null))
        assertEquals(
            "/discover/movies?page=2&primaryReleaseDateGte=2024-01-01",
            seerrDiscoverMoviesPath(2, "2024-01-01"),
        )
        assertEquals("/discover/tv?page=1", seerrDiscoverTvPath(1, null))
        assertEquals(
            "/discover/tv?page=3&firstAirDateGte=2023-06-01",
            seerrDiscoverTvPath(3, "2023-06-01"),
        )
    }
}
