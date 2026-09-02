package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [DesktopStartupPerf]'s measurement contract (wave 12A baseline
 * scaffold): marks are always-on AtomicLong writes, disk output happens ONLY
 * when a `jellyplay.perf.*` property is present, the flush fires from
 * whichever of window-shown / first-frame lands LAST once BOTH marks exist
 * (a plain persist run must never latch a partial JSON), and the emitted
 * single-line JSON carries the boot mark, per-mark null-vs-number MISSING_MARK
 * handling, and 3-decimal locale-independent deltas relative to boot.
 *
 * Exact delta math is pinned through the one injectable mark:
 * [DesktopStartupPerf.markFirstFrame] takes an explicit nanos value, so with a
 * constructor-supplied boot the first-frame delta is exact ("1.500" for boot +
 * 1_500_000L). The auto-exit / heap-sample timer hooks are deliberately NOT
 * exercised here — autoExit calls exitProcess(0) and would kill this JVM.
 */
class DesktopStartupPerfTest {

    private val tempDirs = mutableListOf<Path>()
    private var originalLocale: Locale? = null

    private fun newLogsDir(): Path = Files.createTempDirectory("jellyplay-perf-test").also { tempDirs.add(it) }

    private fun startupJsonOf(dir: Path): String {
        val file = dir.resolve("startup-latest.json")
        assertTrue(Files.exists(file), "startup-latest.json must exist after a complete flush")
        return Files.readString(file)
    }

    /** Extracts `"key":<raw value>` from the compact single-line JSON. */
    private fun jsonValue(json: String, key: String): String {
        val match = Regex("\"$key\":([^,}]+)").find(json)
        assertTrue(match != null, "key '$key' missing in: $json")
        return match!!.groupValues[1].trim()
    }

    @BeforeTest
    fun clearPerfProperties() {
        // persistRequested snapshots System properties at construction — every
        // test starts from a clean slate regardless of execution order.
        listOf(
            DesktopStartupPerf.PROP_DATA_DIR,
            DesktopStartupPerf.PROP_AUTO_EXIT_SECONDS,
            DesktopStartupPerf.PROP_HEAP_SAMPLE_SECONDS,
            DesktopStartupPerf.PROP_PERSIST,
        ).forEach { System.clearProperty(it) }
    }

    @AfterTest
    fun cleanup() {
        clearPerfProperties()
        originalLocale?.let { Locale.setDefault(it) }
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    // ── gating: no properties → no output ─────────────────────────────────

    @Test
    fun noPerfPropertyMeansNoDiskOutputEvenWithAllMarksLanded() {
        val dir = newLogsDir()
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = System.nanoTime())

        assertFalse(perf.persistRequested)
        perf.markKoinStarted()
        perf.markWindowShown()
        perf.markFirstFrame(System.nanoTime())

        assertFalse(Files.exists(dir.resolve("startup-latest.json")), "production boots write nothing")
        assertFalse(Files.exists(dir.resolve("memory-latest.json")))
    }

    @Test
    fun anyPerfPropertyValueCountsAsPersistRequestedEvenLiterallyFalse() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "false")
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = System.nanoTime())
        // The presence check is intentional: the harness always passes "true",
        // and treating any value as opt-in keeps manual one-off runs simple.
        assertTrue(perf.persistRequested)
    }

    // ── flush ordering: the LAST of the two late marks flushes ────────────

    @Test
    fun windowShownAloneDoesNotFlushUntilFirstFrameArrives() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = System.nanoTime())

        perf.markKoinStarted()
        perf.markWindowShown()
        assertFalse(Files.exists(dir.resolve("startup-latest.json")), "incomplete marks must stay unwritten")

        perf.markFirstFrame(System.nanoTime())
        assertTrue(Files.exists(dir.resolve("startup-latest.json")), "first-frame landing last triggers the flush")
    }

    @Test
    fun firstFrameBeforeWindowShownStillFlushesWhenVisibilityArrives() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val boot = System.nanoTime()
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = boot)

        perf.markFirstFrame(boot + 5_000_000L)
        assertFalse(Files.exists(dir.resolve("startup-latest.json")))

        perf.markWindowShown()
        assertTrue(Files.exists(dir.resolve("startup-latest.json")), "visibility landing last must flush — ordering independence")
    }

    // ── JSON schema + MISSING_MARK handling ───────────────────────────────

    @Test
    fun flushedJsonCarriesAllFieldsWithNullsForMissingMarks() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val boot = 1_000_000_000_000L
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = boot)

        perf.markWindowShown()
        perf.markFirstFrame(boot + 1_500_000L) // koinStarted deliberately never marked

        val json = startupJsonOf(dir)
        assertEquals(boot.toString(), jsonValue(json, "bootNanos"))
        assertEquals("null", jsonValue(json, "koinStartedNanos"), "unmarked marks serialize as JSON null")
        assertEquals("null", jsonValue(json, "koinStartMs"))
        val windowShownNanos = jsonValue(json, "windowShownNanos")
        assertTrue(windowShownNanos.toLongOrNull() != null && windowShownNanos != "null", "in: $json")
        val windowShownMs = jsonValue(json, "windowShownMs").toDouble()
        assertTrue(windowShownMs >= 0.0, "visibility is marked after boot in wall-clock terms: $json")
        assertEquals("1.500", jsonValue(json, "firstFrameMs"), "(boot + 1.5 ms) − boot, exact 3-decimal render")
        assertEquals((boot + 1_500_000L).toString(), jsonValue(json, "firstFrameNanos"))
        assertEquals("true", jsonValue(json, "persistRequested"))
    }

    @Test
    fun deltaMathIsExactForAnInjectedFirstFrameNanos() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val boot = 42_000_000_000L
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = boot)

        perf.markWindowShown()
        perf.markFirstFrame(boot + 12_345_678L)

        // 12_345_678 ns → 12.345678 ms → %.3f rounds half-up to 12.346.
        assertEquals("12.346", jsonValue(startupJsonOf(dir), "firstFrameMs"))
    }

    @Test
    fun msFieldsRenderWithADotDecimalRegardlessOfDefaultLocale() {
        val dir = newLogsDir()
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY) // comma-decimal locale
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val boot = 42_000_000_000L
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = boot)

        perf.markWindowShown()
        perf.markFirstFrame(boot + 2_500_000L)

        val json = startupJsonOf(dir)
        assertEquals("2.500", jsonValue(json, "firstFrameMs"), "Locale.ROOT formatting — a comma would break the JSON")
    }

    @Test
    fun bootOnlyMarksStayNullInEveryDelta() {
        val dir = newLogsDir()
        System.setProperty(DesktopStartupPerf.PROP_PERSIST, "true")
        val boot = 7_000_000_000L
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = boot)

        // Window-shown internally marks System.nanoTime() and the flush only
        // fires once BOTH late marks exist, so the fully-empty case is
        // unobservable (it stays unwritten — pinned above). The next best
        // MISSING_MARK pin: koin was never marked → its delta stays null in
        // an otherwise-complete JSON.
        perf.markWindowShown()
        perf.markFirstFrame(System.nanoTime())

        val json = startupJsonOf(dir)
        assertEquals("null", jsonValue(json, "koinStartMs"))
        assertNull(jsonValue(json, "koinStartMs").toDoubleOrNull(), "null is not a number")
    }

    @Test
    fun scheduleHooksWithNoPropertiesSchedulesNothingAndTouchesNoDisk() {
        val dir = newLogsDir()
        val perf = DesktopStartupPerf(logsDirNio = dir, bootT0Nanos = System.nanoTime())

        // Neither autoExit nor heapSample set: returns without creating a
        // Timer. (The armed variants call exitProcess / write on real timers —
        // measurement-harness territory, never exercised from unit tests.)
        perf.scheduleMeasurementHooksIfRequested()

        assertFalse(Files.exists(dir.resolve("memory-latest.json")))
        assertFalse(Files.exists(dir.resolve("startup-latest.json")))
    }
}
