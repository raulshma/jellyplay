package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the SHARED Radarr/Sonarr `parseErrorMessage` (the two jvmShared impls
 * are character-identical here — one wasm helper serves both) against the
 * JVM strings: re-parsed COMPACT JSON (raw whitespace never survives), the
 * bare-`HTTP $code` blank branch, and the 200-char non-JSON fallback.
 */
class ArrHttpErrorTest {

    @Test
    fun `error message re-serializes the parsed body compactly`() {
        assertEquals(
            """HTTP 500: {"errorMessage":"Validation failed"}""",
            arrHttpErrorMessage(500, """{  "errorMessage" :  "Validation failed"  }"""),
            "raw spacing/newlines are compacted away by element.toString()",
        )
    }

    @Test
    fun `error message truncates the compacted body at 200 chars`() {
        val long = """{"errorMessage":"${"x".repeat(300)}"}"""
        assertEquals(
            "HTTP 503: ${arrSeerrWireJson.parseToJsonElement(long).toString().take(200)}",
            arrHttpErrorMessage(503, long),
        )
    }

    @Test
    fun `error message passes parsed scalars through verbatim`() {
        // "null" parses to JsonNull whose toString is "null" — not blank, so
        // it flows into the message (the bare-`HTTP $code` else branch is
        // defensive only: a successfully parsed element never stringifies
        // blank on the JVM either).
        assertEquals("HTTP 401: null", arrHttpErrorMessage(401, "null"))
        assertEquals("HTTP 400: 0", arrHttpErrorMessage(400, "0"))
    }

    @Test
    fun `error message falls back to the first 200 chars for non-JSON bodies`() {
        val body = "<html>Bad Gateway " + "y".repeat(300)
        assertEquals("HTTP 502: ${body.take(200)}", arrHttpErrorMessage(502, body))
    }

    @Test
    fun `error message with an empty body degrades to the trailing colon`() {
        assertEquals("HTTP 404: ", arrHttpErrorMessage(404, ""))
    }
}
