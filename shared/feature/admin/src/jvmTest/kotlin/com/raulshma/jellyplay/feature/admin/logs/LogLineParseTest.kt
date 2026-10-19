package com.raulshma.jellyplay.feature.admin.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the log-line parse ([parseLogLineSpans] + the token →
 * [LogLineRole] ladder) — the severity/color decision LogsScreen renders from
 * [AnnotatedString] spans, kept separate so it is JVM-testable. The
 * composable's role→color map is trivial by construction; everything
 * decidable is pinned here.
 */
class LogLineParseTest {

    private fun spans(line: String) = parseLogLineSpans(line)

    @Test
    fun `the severity ladder folds every token to its role, bold`() {
        val ladder = mapOf(
            "ERROR" to LogLineRole.ERROR,
            "FATAL" to LogLineRole.ERROR,
            "WARN" to LogLineRole.WARNING,
            "WARNING" to LogLineRole.WARNING,
            "INFO" to LogLineRole.INFO,
            "DEBUG" to LogLineRole.DEBUG,
            "TRACE" to LogLineRole.DEBUG,
        )
        ladder.forEach { (token, role) ->
            val parsed = spans("2026-10-19 12:00:00.001 $token Server started")
            assertEquals(3, parsed.size, "at token $token")
            val tokenSpan = parsed[1]
            assertEquals(token, tokenSpan.text, "at token $token — text kept verbatim")
            assertEquals(role, tokenSpan.role, "at token $token")
            assertTrue(tokenSpan.bold, "at token $token — the severity span is bold")
        }
    }

    @Test
    fun `tokens match case-insensitively and keep their original casing`() {
        val parsed = spans("err Error running task")
        assertEquals("Error", parsed[1].text)
        assertEquals(LogLineRole.ERROR, parsed[1].role)
        assertTrue(parsed[1].bold)
    }

    @Test
    fun `a token inside a word does not split the line`() {
        // \b guards: "INFORMATION" must not yield its INFO prefix, and
        // "WARNINGX"/"warned" contain no whole-word token.
        listOf(
            "INFORMATION extra",
            "warned about the leash",
            "DEBUGGING the parser",
        ).forEach { line ->
            val parsed = spans(line)
            assertEquals(1, parsed.size, "at line '$line'")
            assertEquals(line, parsed[0].text)
            assertEquals(null, parsed[0].role)
            assertEquals(false, parsed[0].bold)
        }
    }

    @Test
    fun `warn yields to warning inside the longer word`() {
        // The alternation order makes the shorter WARN arm fail its trailing
        // word boundary inside WARNING — the longer token wins.
        val parsed = spans("WARNING disk almost full")
        assertEquals("WARNING", parsed[1].text)
        assertEquals(LogLineRole.WARNING, parsed[1].role)
    }

    @Test
    fun `the line splits into prefix token suffix verbatim`() {
        val parsed = spans("2026-10-19 INFO Hello world")
        assertEquals(
            listOf(
                LogLineSpan("2026-10-19 ", role = null, bold = false),
                LogLineSpan("INFO", role = LogLineRole.INFO, bold = true),
                LogLineSpan(" Hello world", role = null, bold = false),
            ),
            parsed,
        )
    }

    @Test
    fun `a token at either edge still produces every span`() {
        assertEquals(
            listOf(
                LogLineSpan("", role = null, bold = false),
                LogLineSpan("ERROR", role = LogLineRole.ERROR, bold = true),
                LogLineSpan(" boom", role = null, bold = false),
            ),
            spans("ERROR boom"),
        )
        assertEquals(
            listOf(
                LogLineSpan("boom ", role = null, bold = false),
                LogLineSpan("TRACE", role = LogLineRole.DEBUG, bold = true),
                LogLineSpan("", role = null, bold = false),
            ),
            spans("boom TRACE"),
        )
    }

    @Test
    fun `a line without a token is one plain unbolded span`() {
        val parsed = spans("no severity here at all")
        assertEquals(listOf(LogLineSpan("no severity here at all", role = null, bold = false)), parsed)
    }

    @Test
    fun `an empty line parses to one empty span`() {
        assertEquals(listOf(LogLineSpan("", role = null, bold = false)), spans(""))
    }
}
