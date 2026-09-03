package com.raulshma.jellyplay.core.ui.components

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the JVM (desktop) actuals of the component logging seam ([UiLog.kt]):
 *
 *  - `logUiWarning` writes `W/<tag>: <message>` to STDERR — the level prefix
 *    format is load-bearing for log scraping;
 *  - a throwable is appended as `: <throwable>` (its toString), keeping the
 *    single-line shape;
 *  - `logUiDebug` writes `D/<tag>: <message>` to STDOUT so debug chatter stays
 *    out of the stderr warning stream.
 */
class UiLogJvmTest {

    private val originalErr = System.err
    private val originalOut = System.out
    private val errBuffer = ByteArrayOutputStream()
    private val outBuffer = ByteArrayOutputStream()

    @BeforeTest
    fun redirectStreams() {
        System.setErr(PrintStream(errBuffer, true, Charsets.UTF_8))
        System.setOut(PrintStream(outBuffer, true, Charsets.UTF_8))
    }

    @AfterTest
    fun restoreStreams() {
        System.setErr(originalErr)
        System.setOut(originalOut)
    }

    @Test
    fun logUiWarning_writesTaggedMessageToStderr() {
        logUiWarning("TAG", "something odd")

        assertEquals("W/TAG: something odd", errBuffer.toString(Charsets.UTF_8).trim())
    }

    @Test
    fun logUiWarning_appendsThrowableToTheSameLine() {
        logUiWarning("TAG", "decode failed", IllegalStateException("no pixels"))

        val line = errBuffer.toString(Charsets.UTF_8).trim()
        assertEquals("W/TAG: decode failed: java.lang.IllegalStateException: no pixels", line)
    }

    @Test
    fun logUiDebug_writesTaggedMessageToStdout() {
        logUiDebug("TAG", "cache hit")

        assertEquals("D/TAG: cache hit", outBuffer.toString(Charsets.UTF_8).trim())
        assertTrue(errBuffer.size() == 0, "debug chatter must not pollute stderr")
    }
}
