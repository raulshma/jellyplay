package com.raulshma.jellyplay.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Writer/rotation/marker suite for [DesktopCrashHandler] against a real tmpdir.
 * Hand-rolled throwables (no mocking library): custom stackTrace arrays stand
 * in for deep call stacks, nested `initCause` chains for "Caused by" trees.
 */
class DesktopCrashHandlerTest {

    private val tempDirs = mutableListOf<Path>()

    private fun newLogsDir(): Path =
        Files.createTempDirectory("jellyplay-crash-test").also { tempDirs.add(it) }

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { dir -> dir.toFile().deleteRecursively() }
        // Restore whatever handler a previous test installed.
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    /** Delivers a throwable through the INSTALLED default handler (the real path). */
    private fun crashThroughInstalledHandler(throwable: Throwable) {
        val installed = assertNotNull(
            Thread.getDefaultUncaughtExceptionHandler(),
            "install() must register the default handler",
        )
        installed.uncaughtException(Thread.currentThread(), throwable)
    }

    private class Boom(message: String) : RuntimeException(message)

    /** Synthetic stack of [count] frames so depth/cap tests have deterministic bulk. */
    private fun throwableWithFrames(count: Int, message: String = "boom"): Throwable =
        Boom(message).apply {
            stackTrace = Array(count) { i ->
                StackTraceElement("com.raulshma.jellyplay.Crash$i", "method$i", "Crash$i.kt", i)
            }
        }

    @Test
    fun `install routes uncaught exceptions to a timestamped report and marker`() {
        val logsDir = newLogsDir()
        val handler = DesktopCrashHandler(logsDir).install()

        crashThroughInstalledHandler(throwableWithFrames(3))

        val reports = logsDir.listDirectoryEntries("crash-*.log")
        assertEquals(1, reports.size, "one report written")
        assertTrue(Regex("crash-\\d+\\.log").matches(reports.single().name), "timestamped name")
        val text = reports.single().readText()
        assertTrue(text.contains("time_utc:"), "utc timestamp header")
        assertTrue(text.contains("thread: ${Thread.currentThread().name}"), "thread header")
        assertTrue(text.contains("Boom"), "exception type recorded")
        assertTrue(text.contains("boom"), "message recorded")
        assertTrue(logsDir.resolve("last-crash.txt").exists(), "marker left for next boot")
    }

    @Test
    fun `install chains to the previously installed default handler`() {
        val logsDir = newLogsDir()
        var chained: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, t -> chained = t }
        val handler = DesktopCrashHandler(logsDir).install()

        crashThroughInstalledHandler(Boom("chain-me"))

        assertEquals("chain-me", chained?.message, "previous handler still runs")
    }

    @Test
    fun `marker is consumed exactly once and points at the crash log`() {
        val logsDir = newLogsDir()
        val handler = DesktopCrashHandler(logsDir).install()

        val before = System.currentTimeMillis()
        crashThroughInstalledHandler(throwableWithFrames(2))
        val after = System.currentTimeMillis()

        val crash = assertNotNull(handler.consumePreviousCrashMarker())
        assertTrue(crash.epochMillis in before..after, "marker timestamp is the crash time")
        assertTrue(crash.logFile.exists(), "referenced log file exists")
        assertTrue(crash.logFile.name.endsWith(".log"))
        assertFalse(crash.crashedAtUtc.toString().isEmpty())

        assertNull(handler.consumePreviousCrashMarker(), "marker deleted after first consume")
        assertFalse(logsDir.resolve("last-crash.txt").exists(), "no marker residue")
    }

    @Test
    fun `boot without prior crash consumes nothing`() {
        assertNull(DesktopCrashHandler(newLogsDir()).consumePreviousCrashMarker())
    }

    @Test
    fun `rotation keeps only the newest maxFiles reports`() {
        val logsDir = newLogsDir()
        val handler = DesktopCrashHandler(logsDir, maxFiles = 3).install()

        // Seeds must respect the handler's invariant (fixed-width epoch-millis
        // names sort chronologically) — realistic values slightly in the past.
        val base = System.currentTimeMillis()
        fun seed(ageMillis: Long): String {
            val name = "crash-${base - ageMillis}.log"
            Files.writeString(logsDir.resolve(name), "seed $name")
            return name
        }
        // One more than the cap, all older than the real crashes below.
        val oldest = seed(40_000)
        seed(30_000); seed(20_000)
        val newestSeed = seed(10_000)

        repeat(2) {
            Thread.sleep(2) // distinct epoch-millis file names even on coarse clocks
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(
                Thread.currentThread(), Boom("rotation-$it"),
            )
        }

        val remaining = logsDir.listDirectoryEntries("crash-*.log").map { it.name }.sorted()
        assertEquals(3, remaining.size, "steady state never exceeds the cap")
        assertTrue(oldest !in remaining && newestSeed in remaining, "oldest deleted first")
    }

    @Test
    fun `reports are hard-capped at the byte budget with an explicit truncation note`() {
        val logsDir = newLogsDir()
        val capBytes = 4 * 1024
        val handler = DesktopCrashHandler(logsDir, maxReportBytes = capBytes).install()

        // 2000 synthetic frames at ~70 bytes/line is well past a 4 KB budget.
        crashThroughInstalledHandler(throwableWithFrames(2000))

        val report = logsDir.listDirectoryEntries("crash-*.log").single()
        assertTrue(report.fileSize() <= capBytes, "file must not exceed the cap")
        assertTrue(report.readText().contains("(truncated"), "truncation declared inside the report")
    }

    @Test
    fun `oversized cause chain still records the root exception head`() {
        val logsDir = newLogsDir()
        val handler = DesktopCrashHandler(logsDir, maxReportBytes = 1024).install()

        val deep = Boom("root-message-survives-truncation")
        var current: Throwable = deep
        repeat(50) { i ->
            current.initCause(Boom("level-$i"))
            current = current.cause!!
        }
        current.stackTrace = Array(500) { i -> StackTraceElement("Deep$i", "m", "D.kt", i) }
        crashThroughInstalledHandler(deep)

        val report = logsDir.listDirectoryEntries("crash-*.log").single()
        assertTrue(report.fileSize() <= 1024, "capped at budget")
        assertTrue(report.readText().contains("root-message-survives-truncation"), "root head kept")
    }

    @Test
    fun `coroutine exception handler writes through the same pipeline`() {
        val logsDir = newLogsDir()
        val handler = DesktopCrashHandler(logsDir)

        handler.coroutineExceptionHandler.handleException(
            kotlin.coroutines.EmptyCoroutineContext,
            throwableWithFrames(2),
        )

        assertEquals(1, logsDir.listDirectoryEntries("crash-*.log").size)
        assertNotNull(handler.consumePreviousCrashMarker())
    }

    @Test
    fun `corrupt marker degrades to no-crash instead of failing boot`() {
        val logsDir = newLogsDir()
        Files.writeString(logsDir.resolve("last-crash.txt"), "not-a-timestamp")
        assertNull(DesktopCrashHandler(logsDir).consumePreviousCrashMarker())
        assertFalse(logsDir.resolve("last-crash.txt").exists())
    }

    @Test
    fun `constructor rejects nonsense limits`() {
        assertFailsWith<IllegalArgumentException> { DesktopCrashHandler(newLogsDir(), maxFiles = 0) }
        assertFailsWith<IllegalArgumentException> { DesktopCrashHandler(newLogsDir(), maxReportBytes = -1) }
    }
}
