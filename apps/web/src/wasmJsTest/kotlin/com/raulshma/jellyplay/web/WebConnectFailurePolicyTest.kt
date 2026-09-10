package com.raulshma.jellyplay.web

import com.raulshma.jellyplay.core.network.api.ApiException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.io.IOException

/**
 * Pins the web connect flow's failure taxonomy ([isLikelyCorsOrTransport],
 * [friendlyProbeFailure], [friendlySignInFailure]) — the functions moved out
 * of WebConnectFlow.kt into WebConnectFailurePolicy.kt so this source set can
 * reach them. Until this landed, the KDoc itself admitted the classification
 * was "statically unverifiable" from the repo's lanes; the tests below pin
 * the LOGIC exactly as shipped (byte-for-byte the code). What they
 * deliberately do NOT pin: the browsers' actual rejection wording — that
 * stays the real-server browser pass's job (tools/e2e/web-verify.mjs). The
 * Chromium/Firefox/WebKit fragments asserted here are the taxonomy's INPUTS,
 * hand-built stand-ins for what each engine is believed to surface.
 *
 * Cover map:
 *  - TYPE signal: HttpRequestTimeoutException and IOException classify as
 *    transport, directly and nested down the cause chain.
 *  - MESSAGE signal: "Failed to fetch" (Chromium), "NetworkError" (Firefox),
 *    "Load failed" (WebKit) — case-insensitively, at any chain depth.
 *  - NEGATIVES: a server verdict (ApiException 5xx/401) and unrelated
 *    messages must not trip the transport classifier — that gate alone shows
 *    the CORS doc hint.
 *  - PROBE vs SIGN-IN phrasing: transport refusals get the diagnosable probe
 *    lines (online vs offline variants); sign-in maps ONLY an HTTP-401
 *    ApiException to the credentials line — every other failure (including
 *    403/5xx ApiExceptions) falls back to its own message, and null-message
 *    throwables get the last-resort copies.
 */
class WebConnectFailurePolicyTest {

    // ── isLikelyCorsOrTransport: TYPE signal ───────────────────────────────

    @Test
    fun `a ktor request timeout classifies as transport`() {
        val failure = HttpRequestTimeoutException(
            url = "http://media.example.com/system/info/public",
            timeoutMillis = 5_000L,
        )
        assertTrue(isLikelyCorsOrTransport(failure), "the timeout plugin's exception must be a transport verdict")
    }

    @Test
    fun `an IOException classifies as transport`() {
        assertTrue(
            isLikelyCorsOrTransport(IOException("connection reset")),
            "the assumption the Js engine wraps fetch failures in IOException is half the taxonomy",
        )
    }

    @Test
    fun `a timeout reached through the cause chain classifies as transport`() {
        val failure = RuntimeException("engine wrapper", HttpRequestTimeoutException("http://x", 1_000L))
        assertTrue(isLikelyCorsOrTransport(failure), "the cause walk must find a nested timeout")
    }

    // ── isLikelyCorsOrTransport: MESSAGE signal ────────────────────────────

    @Test
    fun `the Chromium failed-to-fetch string classifies as transport`() {
        assertTrue(
            isLikelyCorsOrTransport(RuntimeException("TypeError: Failed to fetch")),
            "the raw browser rejection string must match even without a typed IOException",
        )
    }

    @Test
    fun `the Firefox NetworkError string classifies as transport`() {
        assertTrue(
            isLikelyCorsOrTransport(RuntimeException("NetworkError when attempting to fetch resource.")),
            "the raw browser rejection string must match even without a typed IOException",
        )
    }

    @Test
    fun `the WebKit Load failed string classifies as transport`() {
        assertTrue(
            isLikelyCorsOrTransport(RuntimeException("Load failed")),
            "the raw browser rejection string must match even without a typed IOException",
        )
    }

    @Test
    fun `message fragments match case-insensitively`() {
        assertTrue(
            isLikelyCorsOrTransport(RuntimeException("FAILED TO FETCH")),
            "the taxonomy lowercases messages before matching",
        )
        assertTrue(
            isLikelyCorsOrTransport(RuntimeException("load Failed")),
            "the taxonomy lowercases messages before matching",
        )
    }

    @Test
    fun `a message fragment on a nested cause classifies as transport`() {
        val failure = RuntimeException("ktor Js engine wrapper", RuntimeException("TypeError: Failed to fetch"))
        assertTrue(
            isLikelyCorsOrTransport(failure),
            "the friendly line + CORS hint must survive an engine whose wrapping differs",
        )
    }

    // ── isLikelyCorsOrTransport: negatives ─────────────────────────────────

    @Test
    fun `a server verdict does not classify as transport`() {
        assertFalse(
            isLikelyCorsOrTransport(ApiException.fromHttp(500, "HTTP 500: Internal Server Error")),
            "a real HTTP response is a server verdict — it must never show the CORS hint",
        )
    }

    @Test
    fun `an unrelated message does not classify as transport`() {
        assertFalse(
            isLikelyCorsOrTransport(RuntimeException("server exploded")),
            "no fragment match means no transport classification",
        )
    }

    @Test
    fun `a 401 ApiException with a friendly message does not classify as transport`() {
        assertFalse(
            isLikelyCorsOrTransport(
                ApiException(isRetryable = false, httpCode = 401, isAccessDenied = true, message = "Access token is invalid."),
            ),
            "wrong credentials must not be mistaken for a blocked request",
        )
    }

    // ── friendlyProbeFailure: phrasing ─────────────────────────────────────

    @Test
    fun `probe transport failure while online gets the refused-or-timed-out line`() {
        assertEquals(
            "Could not reach the server (request refused or timed out).",
            friendlyProbeFailure(IOException("fetch aborted"), browserOnline = true),
        )
    }

    @Test
    fun `probe transport failure while offline gets the no-connectivity line`() {
        assertEquals(
            "The browser reports no connectivity.",
            friendlyProbeFailure(RuntimeException("Failed to fetch"), browserOnline = false),
        )
    }

    @Test
    fun `probe non-transport failure falls back to the failure's own message`() {
        val failure = RuntimeException("HTTP 502: Bad Gateway")
        assertEquals(
            "HTTP 502: Bad Gateway",
            friendlyProbeFailure(failure, browserOnline = true),
            "a typed server verdict is shown as-is — never replaced by the transport line",
        )
    }

    @Test
    fun `probe non-transport failure without a message gets the last-resort line`() {
        assertEquals(
            "Could not reach the server.",
            friendlyProbeFailure(RuntimeException(), browserOnline = true),
        )
    }

    // ── friendlySignInFailure: 401 vs message fallback ─────────────────────

    @Test
    fun `sign-in 401 maps to the credentials line regardless of the exception message`() {
        assertEquals(
            "Incorrect username or password.",
            friendlySignInFailure(
                ApiException(isRetryable = false, httpCode = 401, isAccessDenied = true, message = "Access token is invalid."),
            ),
            "invalid credentials must read as invalid credentials, not as the client's raw text",
        )
    }

    @Test
    fun `sign-in non-401 ApiException keeps its own message`() {
        assertEquals(
            "HTTP 500: Internal Server Error",
            friendlySignInFailure(ApiException.fromHttp(500, "HTTP 500: Internal Server Error")),
            "only 401 gets the credentials line — a 5xx is not 'wrong password'",
        )
    }

    @Test
    fun `sign-in 403 ApiException falls back to its message like any non-401`() {
        assertEquals(
            "HTTP 403: Forbidden",
            friendlySignInFailure(ApiException.fromHttp(403, "HTTP 403: Forbidden")),
            "the mapping keys on httpCode == 401 exactly; access-denied-but-not-401 is NOT the credentials line",
        )
    }

    @Test
    fun `sign-in plain failure message passes through verbatim`() {
        assertEquals(
            "Connection timed out while calling the server.",
            friendlySignInFailure(RuntimeException("Connection timed out while calling the server.")),
            "the client's classified retryable messages ride through untouched",
        )
    }

    @Test
    fun `sign-in failure without a message gets the last-resort line`() {
        assertEquals("Sign-in failed.", friendlySignInFailure(RuntimeException()))
    }
}
