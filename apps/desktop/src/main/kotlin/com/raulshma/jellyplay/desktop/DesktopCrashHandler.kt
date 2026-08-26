package com.raulshma.jellyplay.desktop

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Crash-log scaffold (wave 10A release engineering): writes timestamped plain-
 * text reports for uncaught exceptions into `DesktopPaths.logsDirNio`
 * (`<appdata>/JellyPlay/data/logs` on Windows) and leaves a marker file that
 * the NEXT boot consumes to surface "the previous session ended unexpectedly"
 * in the About dialog.
 *
 * Two entry points, one write path:
 *  * [install] — hooks [Thread.setDefaultUncaughtExceptionHandler], chaining
 *    to whatever handler was installed before (null on a stock JVM). Covers
 *    AWT/EDT and arbitrary threads.
 *  * [coroutineExceptionHandler] — for scopes that swallow child failures and
 *    would otherwise never reach the thread default (Compose's `application`
 *    scope already propagates to the default handler; this exists so future
 *    scopes can opt in explicitly).
 *
 * On disk: one `crash-<epochMillis>.log` per event (`CREATE_NEW` reserves the
 * name; a same-millisecond second crash retries as `-<seq>` instead of
 * truncating the first report — millis both sorts lexicographically and is
 * collision-disambiguated in-process), rotation keeps the
 * newest [maxFiles] reports deleting older ones at crash time (no background
 * sweeper), and each report is hard-capped at [maxReportBytes] by appending
 * line-granular content until the budget is spent — the tail is cut and marked
 * `(truncated)` rather than ever writing an oversized file.
 *
 * The marker (`last-crash.txt`) holds `<epochMillis> <logFileName>`; it is
 * written AT CRASH TIME and consumed+deleted at next boot via
 * [consumePreviousCrashMarker]. There is no clean-shutdown ping: any marker
 * found at boot therefore means "an uncaught throwable was recorded last
 * session", not necessarily "the OS killed us".
 */
class DesktopCrashHandler(
    private val logsDir: Path,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxReportBytes: Int = DEFAULT_MAX_REPORT_BYTES,
) {
    init {
        require(maxFiles >= 1) { "maxFiles must be >= 1, got $maxFiles" }
        require(maxReportBytes > 0) { "maxReportBytes must be > 0, got $maxReportBytes" }
    }

    /** Marker for [install] idempotency (see its KDoc). */
    private var installed = false

    /** What the previous session left behind, after [consumePreviousCrashMarker]. */
    data class PreviousCrash(
        val logFile: Path,
        val epochMillis: Long,
    ) {
        val crashedAtUtc: Instant get() = Instant.ofEpochMilli(epochMillis)
    }

    fun install(): DesktopCrashHandler {
        if (installed) return this // idempotent: double-install would chain this handler to itself
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write first — if the chained handler exits the VM we still have
            // our report on disk.
            writeCrashReport(throwable, threadName = thread.name)
            previous?.uncaughtException(thread, throwable)
        }
        return this
    }

    /**
     * Scope-level twin of the default handler. Deliberately does NOT rethrow:
     * logging is the contract, recovery policy belongs to the scope.
     */
    val coroutineExceptionHandler: kotlinx.coroutines.CoroutineExceptionHandler =
        kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            writeCrashReport(throwable, threadName = Thread.currentThread().name)
        }

    /**
     * Reads and deletes the previous-session marker. Returns null when this
     * boot follows a session without recorded crashes (or the marker was
     * unreadable garbage — treated as absent rather than crashing the boot).
     */
    fun consumePreviousCrashMarker(): PreviousCrash? {
        val marker = logsDir.resolve(MARKER_FILE)
        val payload = try {
            marker.takeIf { Files.exists(it) }?.readText()?.trim()
        } catch (_: Exception) {
            null // corrupt/unreadable marker degrades to "no known previous crash"
        } ?: return null
        try {
            marker.deleteIfExists()
        } catch (e: Exception) {
            // A locked/undeletable marker must not crash the boot; leaving it
            // only means the next boot surfaces the same previous-crash note.
            System.err.println("[JellyPlay] previous-crash marker cleanup failed: $e")
        }

        val parts = payload.split(' ', limit = 2)
        val millis = parts.firstOrNull()?.toLongOrNull() ?: return null
        val fileName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return PreviousCrash(logFile = logsDir.resolve(fileName), epochMillis = millis)
    }

    /** One synchronized block: crashes can race (EDT + worker thread). */
    private fun writeCrashReport(throwable: Throwable, threadName: String) {
        synchronized(this) {
            try {
                logsDir.createDirectories()
                rotateOldReports()
            } catch (_: Exception) {
                // Unwritable logs dir must not mask the original crash; fall
                // through and let the report write attempt its own failure.
            }
            try {
                val epochMillis = System.currentTimeMillis()
                val content = try {
                    renderReport(epochMillis, throwable, threadName)
                } catch (t: Throwable) {
                    // OOM/StackOverflowError land here precisely when the UEH
                    // fires hardest — degrade to a header-only report instead
                    // of losing everything to a failed render.
                    minimalReport(epochMillis, throwable, threadName)
                }
                val reportFile = writeNewReportFile(epochMillis, content)
                logsDir.resolve(MARKER_FILE).writeText("$epochMillis ${reportFile.name}")
            } catch (e: Exception) {
                // Disk-full or locked dir: nothing sensible left to do from a
                // crash path — the chained/default handler still runs. Never
                // fully silent: the failure itself must stay observable.
                System.err.println("[JellyPlay] crash-report write failed: $e")
            }
        }
    }

    /**
     * Reserves a report name no other writer holds: the plain millis name
     * first, then `-<seq>` disambiguators. `CREATE_NEW` makes a losing racer
     * retry instead of truncating the winner's file (the old CREATE/TRUNCATE
     * default silently destroyed same-millisecond reports).
     */
    private fun writeNewReportFile(epochMillis: Long, content: String): Path {
        var seq = 0
        while (true) {
            val name = if (seq == 0) "crash-$epochMillis.log" else "crash-$epochMillis-$seq.log"
            val candidate = logsDir.resolve(name)
            try {
                Files.writeString(candidate, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                return candidate
            } catch (_: FileAlreadyExistsException) {
                seq++
            }
        }
    }

    /**
     * Last-resort report for when [renderReport] itself throws — sized to
     * stay far under any sane cap and to avoid anything heavier than string
     * concatenation (no PrintWriter walk, no stack-trace expansion).
     */
    private fun minimalReport(epochMillis: Long, throwable: Throwable, threadName: String): String = buildString {
        appendLine("JellyPlay desktop crash report")
        appendLine("time_utc: ${Instant.ofEpochMilli(epochMillis)}")
        appendLine("thread: $threadName")
        appendLine("(full report render failed)")
        appendLine(throwable.toString())
    }

    /**
     * Makes room for the new report: trims DOWN TO maxFiles-1 existing ones,
     * deleting however many OLDEST exceed that. Oldest = smallest name — the
     * name embeds fixed-width epoch-millis so lexical order is chronological
     * order for anything this handler itself wrote (variable-width hand-made
     * names would break that assumption).
     */
    private fun rotateOldReports() {
        if (!logsDir.isDirectory()) return
        val existing = logsDir.listDirectoryEntries(REPORT_GLOB)
        val keepRoom = maxFiles - 1
        if (existing.size <= keepRoom) return
        val excess = existing.size - keepRoom
        existing.sortedBy { it.name } // ascending → oldest first
            .take(excess)
            .forEach { path ->
                runCatching { path.deleteIfExists() }.onFailure { e ->
                    System.err.println("[JellyPlay] crash-report rotation delete failed for ${path.name}: $e")
                }
            }
    }

    private fun renderReport(epochMillis: Long, throwable: Throwable, threadName: String): String {
        val fullBody = buildString {
            appendLine("JellyPlay desktop crash report")
            appendLine("time_utc: ${Instant.ofEpochMilli(epochMillis)}")
            appendLine("thread: $threadName")
            appendLine()
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            append(sw.toString().trimEnd())
            appendLine()
        }
        if (fullBody.toByteArray().size <= maxReportBytes) return fullBody

        // Line-granular truncation: keep whole lines while they fit, then mark
        // the cut instead of writing a mid-character byte blob. The reserve is
        // BYTE-counted (the note contains an em dash = 3 UTF-8 bytes) or the
        // finished file can exceed the cap by the char/byte delta.
        var budget = maxReportBytes - TRUNCATION_NOTE.toByteArray(Charsets.UTF_8).size
        return buildString {
            for (line in fullBody.lineSequence()) {
                val encoded = (line + "\n").toByteArray(Charsets.UTF_8).size
                if (encoded > budget) break
                append(line).append('\n')
                budget -= encoded
            }
            append(TRUNCATION_NOTE)
        }
    }

    private companion object {
        const val MARKER_FILE = "last-crash.txt"
        const val REPORT_GLOB = "crash-*.log" // bare glob — kotlin.io.path schema, not java.nio's "glob:" URIs
        const val TRUNCATION_NOTE = "(truncated — report exceeded the size cap)"
        const val DEFAULT_MAX_FILES = 5
        const val DEFAULT_MAX_REPORT_BYTES = 200 * 1024
    }
}
