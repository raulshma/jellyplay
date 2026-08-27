package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/**
 * Wave 12A Skia startup/memory baseline — measurement-only scaffold (no
 * optimization applied; the numbers this produces are the measuring stick,
 * see docs/perf/desktop-skia-baseline.md).
 *
 * Marks are plain AtomicLong stores (~zero cost, always on). Everything that
 * touches disk or schedules exit is gated behind `jellyplay.perf.*` system
 * properties, so production boots gain nothing but four nanosecond writes:
 *
 *  * `jellyplay.perf.dataDir=<dir>`     — reroute DesktopPaths under [dir]
 *    (surgical override in [DesktopPaths.resolve]; keeps measurements out of
 *    real appdata).
 *  * `jellyplay.perf.autoExitSeconds=N` — schedule a daemon-timer N seconds out
 *    that calls `exitProcess(0)`. BLUNT by design: it bypasses graceful
 *    teardown (download loops, audio manager die with the process). It never
 *    fires in production because the property is only set by the perf harness.
 *    It also cannot leave a false crash marker: DesktopCrashHandler writes
 *    markers from the uncaught-exception path only, which exitProcess(0)
 *    bypasses. The measurement-only tools/perf/desktop-baseline.sh sets this.
 *  * `jellyplay.perf.heapSampleSeconds=S`— at S seconds uptime record JVM heap
 *    into `<logs>/memory-latest.json` (the OS working set is captured
 *    externally by the harness via PowerShell, keyed by PID).
 *  * `jellyplay.perf.persist=true`      — persist marks without scheduling
 *    anything else (used for manual one-off runs).
 *
 * Output schema (single compact JSON line each):
 *  * `<logs>/startup-latest.json`: bootNanos/koinStartedNanos/windowShownNanos/
 *    firstFrameNanos (absolute monotonic System.nanoTime, machine-local) plus
 *    koinStartMs/windowShownMs/firstFrameMs deltas relative to bootNanos;
 *    unrecorded marks serialize as null.
 *  * `<logs>/memory-latest.json`: sampledUptimeMs + used/max heap bytes.
 */
class DesktopStartupPerf(
    logsDirNio: Path,
    bootT0Nanos: Long,
) {
    private val logFile = logsDirNio.resolve("startup-latest.json")
    private val memoryFile = logsDirNio.resolve("memory-latest.json")

    private val boot = AtomicLong(bootT0Nanos)
    private val koinStarted = AtomicLong(MISSING_MARK)
    private val windowShown = AtomicLong(MISSING_MARK)
    private val firstFrame = AtomicLong(MISSING_MARK)

    private val flushGuard = AtomicBoolean(false)
    private var timer: Timer? = null

    /** True when any jellyplay.perf property is set → JSON output enabled. */
    val persistRequested: Boolean =
        listOf(PROP_DATA_DIR, PROP_AUTO_EXIT_SECONDS, PROP_HEAP_SAMPLE_SECONDS, PROP_PERSIST)
            .any { System.getProperty(it) != null }

    fun markKoinStarted() {
        koinStarted.set(System.nanoTime())
    }

    /** Called from the AWT event thread when the ComposeWindow becomes visible. */
    fun markWindowShown() {
        windowShown.set(System.nanoTime())
    }

    /** Called after the frame clock produces the first real frame. */
    fun markFirstFrame(atNanos: Long) {
        firstFrame.set(atNanos)
        // First frame is the last startup mark to land — persist once, here.
        if (persistRequested) flushStartupJson()
    }

    /**
     * Schedules autoExit and heap sampling when their properties request them.
     * Both fire through one daemon Timer; timers do not block main(), so a run
     * without the properties schedules literally nothing.
     */
    fun scheduleMeasurementHooksIfRequested() {
        val autoExitSeconds = System.getProperty(PROP_AUTO_EXIT_SECONDS)?.toIntOrNull()
        val heapSampleSeconds = System.getProperty(PROP_HEAP_SAMPLE_SECONDS)?.toDoubleOrNull()

        if (autoExitSeconds == null && heapSampleSeconds == null) return

        val t = Timer("jellyplay-perf", true)
        timer = t
        println(
            "[JellyPlay][perf] measurement mode: " +
                "autoExitSeconds=$autoExitSeconds heapSampleSeconds=$heapSampleSeconds " +
                "persistRequested=$persistRequested",
        )
        println(
            "[JellyPlay][perf] NOTE: jellyplay.perf.autoExitSeconds exits via " +
                "exitProcess(0) from a timer thread — it BYPASSES graceful teardown " +
                "(download/audio loops are killed mid-flight). Measurement-only; " +
                "never enable on a profile you care about.",
        )
        heapSampleSeconds?.takeIf { it > 0 }?.let { seconds ->
            t.schedule(
                object : java.util.TimerTask() {
                    override fun run() = writeMemorySnapshot(seconds)
                },
                (seconds * 1000).toLong(),
            )
        }
        autoExitSeconds?.takeIf { it > 0 }?.let { seconds ->
            t.schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        // Best-effort: even a hung shell leaves diagnosable marks.
                        if (persistRequested) flushStartupJson(force = true)
                        println("[JellyPlay][perf] auto-exit (${seconds}s) — exiting now")
                        exitProcess(EXIT_CODE)
                    }
                },
                seconds * 1000L,
            )
        }
    }

    private fun writeMemorySnapshot(sampleAtSeconds: Double) {
        try {
            val runtime = Runtime.getRuntime()
            val json = buildString {
                append('{')
                append("\"sampledUptimeMs\":").append(fmtMs((System.nanoTime() - boot.get()) / 1_000_000.0))
                append(",\"usedHeapBytes\":").append(runtime.totalMemory() - runtime.freeMemory())
                append(",\"maxHeapBytes\":").append(runtime.maxMemory())
                append('}')
            }
            Files.createDirectories(memoryFile.toAbsolutePath().parent)
            Files.writeString(memoryFile.toAbsolutePath(), json)
            println("[JellyPlay][perf] memory snapshot written at ${sampleAtSeconds}s: $json")
        } catch (e: Exception) {
            // A missing snapshot must not break the run or the exit path.
            System.err.println("[JellyPlay][perf] memory snapshot write failed: $e")
        }
    }

    private fun flushStartupJson(force: Boolean = false) {
        // Normal path: exactly one thread wins the single write. The forced
        // auto-exit path rewrites unconditionally so a run that died before its
        // first frame still leaves whatever marks did land on disk.
        if (!force && !flushGuard.compareAndSet(false, true)) return
        try {
            val b = boot.get()
            val json = buildString {
                append('{')
                append("\"bootNanos\":").append(b)
                append(",\"koinStartedNanos\":").append(jsonNumber(koinStarted.get()))
                append(",\"windowShownNanos\":").append(jsonNumber(windowShown.get()))
                append(",\"firstFrameNanos\":").append(jsonNumber(firstFrame.get()))
                append(",\"koinStartMs\":").append(jsonDeltaMs(b, koinStarted.get()))
                append(",\"windowShownMs\":").append(jsonDeltaMs(b, windowShown.get()))
                append(",\"firstFrameMs\":").append(jsonDeltaMs(b, firstFrame.get()))
                append(",\"persistRequested\":").append(persistRequested)
                append('}')
            }
            Files.createDirectories(logFile.toAbsolutePath().parent)
            Files.writeString(logFile.toAbsolutePath(), json)
        } catch (e: Exception) {
            System.err.println("[JellyPlay][perf] startup-marks write failed: $e")
        }
    }

    private fun jsonNumber(v: Long): Any = if (v == MISSING_MARK) "null" else v.toString()
    private fun jsonDeltaMs(base: Long, mark: Long): Any =
        if (mark == MISSING_MARK || base == MISSING_MARK) "null" else fmtMs((mark - base) / 1_000_000.0)

    private fun fmtMs(v: Double): String {
        // Fixed 3-decimal render — locale-independent, valid JSON.
        return String.format(java.util.Locale.ROOT, "%.3f", v)
    }

    companion object {
        const val PROP_DATA_DIR = "jellyplay.perf.dataDir"
        const val PROP_AUTO_EXIT_SECONDS = "jellyplay.perf.autoExitSeconds"
        const val PROP_HEAP_SAMPLE_SECONDS = "jellyplay.perf.heapSampleSeconds"
        const val PROP_PERSIST = "jellyplay.perf.persist"
        const val EXIT_CODE = 0

        /** Sentinel for "not recorded yet" in mark slots. */
        const val MISSING_MARK = -1L
    }
}
