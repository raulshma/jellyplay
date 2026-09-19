package com.raulshma.jellyplay.core.ui.components

import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-shape pins for the DateLabels seam (the platform-neutral contracts are
 * in commonTest's DateLabelsTest): every shape renders through java.time with
 * Locale.getDefault() — pinned to English here for determinism, the same
 * technique CalendarGroupingTest/DurationFormatterTest use. Byte-parity with
 * the pre-promotion feature bodies is what these assertions freeze.
 *
 * The wasmJs FIXED-ENGLISH degrade documented once on the DateLabels KDoc is
 * pinned here too, source-scan style (the WasmMirrorContractTest precedent —
 * the wasm browser test lane never runs, so the degrade is ratcheted from the
 * JVM build): the wasm actual must format from its own English tables and
 * must not consult host-locale services (Intl/DateTimeFormat), while the JVM
 * actual must keep Locale.getDefault().
 */
class DateLabelsJvmTest {

    private val originalLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun pinEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ── the five JVM shapes ───────────────────────────────────────────────

    @Test
    fun `short month day renders as MMM d`() {
        // 2026-07-13 is a Monday.
        assertEquals("Jul 13", shortMonthDay(LocalDate(2026, 7, 13)))
    }

    @Test
    fun `short month day year renders as MMM d, yyyy`() {
        assertEquals("Jan 5, 2024", shortMonthDayYear(LocalDate(2024, 1, 5)))
        assertEquals("Dec 31, 2023", shortMonthDayYear(LocalDate(2023, 12, 31)))
    }

    @Test
    fun `long month day year renders as MMMM d, yyyy`() {
        assertEquals("January 5, 2026", longMonthDayYear(LocalDate(2026, 1, 5)))
        assertEquals("July 14, 2026", longMonthDayYear(LocalDate(2026, 7, 14)))
    }

    @Test
    fun `month year renders as MMMM yyyy`() {
        assertEquals("July 2026", monthYear(2026, 7))
        assertEquals("January 2026", monthYear(2026, 1))
    }

    @Test
    fun `weekday short month day renders as EEE, MMM d`() {
        assertEquals("Mon, Jul 13", weekdayShortMonthDay(LocalDate(2026, 7, 13)))
        assertEquals("Sun, Jul 19", weekdayShortMonthDay(LocalDate(2026, 7, 19)))
    }

    @Test
    fun `one decimal keeps the percent-one-f contract`() {
        assertEquals("4.0", oneDecimal(4.0))
        assertEquals("12.3", oneDecimal(12.34))
        assertEquals("1.3", oneDecimal(1.25)) // HALF_UP at the first decimal
        assertEquals("-1.3", oneDecimal(-1.25)) // sign symmetric
    }

    @Test
    fun `relative instant label renders older entries with the default-locale MMM d`() {
        // Mirror the newsletter test's expectation construction: the expected
        // string comes from DateTimeFormatter at the (pinned) default locale.
        val older = java.time.LocalDate.now().minusDays(3)
        val stamp = older.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        assertEquals(older.format(DateTimeFormatter.ofPattern("MMM d")), relativeInstantDateLabel(stamp))
    }

    // ── the fixed-English degrade, source-scan contract ───────────────────

    /** This module's root (the nearest ancestor holding our source sets). */
    private val moduleRoot: File by lazy {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "src/jvmShared").isDirectory) dir = dir.parentFile
        assertTrue(
            File(dir, "src/jvmShared").isDirectory,
            "could not locate src/jvmShared from ${System.getProperty("user.dir")}",
        )
        dir
    }

    @Test
    fun `wasm actual renders fixed english from its own tables and consults no locale service`() {
        val wasmActual = File(
            moduleRoot,
            "src/wasmJsMain/kotlin/com/raulshma/jellyplay/core/ui/components/DateLabels.wasmJs.kt",
        ).let { file ->
            assertTrue(file.isFile, "missing wasm DateLabels actual at ${file.absolutePath}")
            file.readText(Charsets.UTF_8)
        }

        // The single English table set (the one the feature actuals used to
        // hand-roll individually).
        assertTrue(
            "\"Jan\", \"Feb\", \"Mar\"" in wasmActual && "\"January\", \"February\", \"March\"" in wasmActual &&
                "\"Mon\", \"Tue\", \"Wed\"" in wasmActual,
            "wasm actual lost its English month/day tables",
        )
        // The degrade is FIXED English: no host-locale lookups anywhere.
        assertTrue(
            "Intl." !in wasmActual && "DateTimeFormat" !in wasmActual,
            "wasm actual must not consult host-locale services (Intl/DateTimeFormat) — that is the documented degrade's flip side",
        )
    }

    @Test
    fun `jvm actual keeps host-locale rendering`() {
        val jvmActual = File(
            moduleRoot,
            "src/jvmShared/kotlin/com/raulshma/jellyplay/core/ui/components/DateLabels.jvmShared.kt",
        ).let { file ->
            assertTrue(file.isFile, "missing jvmShared DateLabels actual at ${file.absolutePath}")
            file.readText(Charsets.UTF_8)
        }
        assertTrue(
            "Locale.getDefault()" in jvmActual,
            "jvm actual must keep Locale.getDefault() (the host-locale behavior android + desktop shipped)",
        )
    }
}
