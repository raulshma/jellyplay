package com.raulshma.jellyplay.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 13B session harness — pins the shape of the hand-rolled
 * `<logs>/session-harness.json` payload (SessionHarnessReport.toJson): the
 * runner script greps `overallPass` and humans read step details out of it,
 * so the JSON must stay parseable and carry the documented keys. Parsing via
 * kotlinx-serialization-json (on the classpath as an implementation dep)
 * rather than string-contains, so escaping bugs actually fail.
 */
class SessionHarnessReportTest {

    private fun sampleReport() = SessionHarnessReport(
        startedAtMs = 1_000L,
        finishedAtMs = 9_000L,
        overallPass = true,
        fatal = null,
        machine = mapOf(
            "os.name" to "Windows 11",
            "surfaceMode" to "HWND",
            "engineDisplayName" to "mpv",
        ),
        steps = listOf(
            StepResult(
                name = "LOGIN",
                pass = true,
                atMs = 1_100L,
                durationMs = 812L,
                details = mapOf("user" to "harness"),
                error = null,
            ),
            StepResult(
                name = "ESC_SEQUENCE",
                pass = false,
                atMs = 8_000L,
                durationMs = 10_100L,
                details = mapOf(
                    "finding" to "path C:\\Temp \"quoted\"\nsecond line",
                ),
                error = "ESC did not pop the player route",
            ),
        ),
    )

    @Test
    fun `json carries the documented top-level keys`() {
        val root = Json.parseToJsonElement(sampleReport().toJson()).jsonObject
        assertEquals("desktop-session", root["harness"]!!.jsonPrimitive.content)
        assertTrue(root["overallPass"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1_000L, root["startedAtMs"]!!.jsonPrimitive.content.toLong())
        assertEquals(9_000L, root["finishedAtMs"]!!.jsonPrimitive.content.toLong())
        // fatal: null (present steps + no fatal ⇒ overallPass reflects steps only)
        assertNull(root["fatal"]?.let { if (it.jsonPrimitive.isString) it.jsonPrimitive.content else null })
        assertTrue("surfaceMode" in root["machine"]!!.jsonObject.keys)
        assertEquals("HWND", root["machine"]!!.jsonObject["surfaceMode"]!!.jsonPrimitive.content)
        assertEquals("mpv", root["machine"]!!.jsonObject["engineDisplayName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `steps array carries name pass timings error and details`() {
        val steps = Json.parseToJsonElement(sampleReport().toJson())
            .jsonObject["steps"]!!.jsonArray
        assertEquals(2, steps.size)
        val login = steps[0].jsonObject
        assertEquals("LOGIN", login["name"]!!.jsonPrimitive.content)
        assertTrue(login["pass"]!!.jsonPrimitive.content.toBoolean())
        assertNull(login["error"]?.let { if (it.jsonPrimitive.isString) it.jsonPrimitive.content else null })
        assertEquals("harness", login["details"]!!.jsonObject["user"]!!.jsonPrimitive.content)
        val esc = steps[1].jsonObject
        assertFalse(esc["pass"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            "ESC did not pop the player route",
            esc["error"]!!.jsonPrimitive.content,
        )
        // Escaping round-trips: backslash, double quote and a newline survive.
        assertEquals(
            "path C:\\Temp \"quoted\"\nsecond line",
            esc["details"]!!.jsonObject["finding"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `failed runs serialize fatal and overallPass false`() {
        val json = sampleReport()
            .copy(overallPass = false, fatal = "auto-exit deadline reached")
            .toJson()
        val root = Json.parseToJsonElement(json).jsonObject
        assertFalse(root["overallPass"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            "auto-exit deadline reached",
            root["fatal"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `empty details map serializes as empty object`() {
        val json = SessionHarnessReport(
            startedAtMs = 0,
            finishedAtMs = 1,
            overallPass = true,
            fatal = null,
            machine = emptyMap(),
            steps = listOf(StepResult("CONFIG", true, 0, 1, emptyMap(), null)),
        ).toJson()
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(0, root["machine"]!!.jsonObject.size)
        assertEquals(0, root["steps"]!!.jsonArray[0].jsonObject["details"]!!.jsonObject.size)
    }
}
