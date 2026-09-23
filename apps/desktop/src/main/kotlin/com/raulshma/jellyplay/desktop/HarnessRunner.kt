package com.raulshma.jellyplay.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.delay

/**
 * Thrown by [HarnessRunner.StepContext.fail] — a step FAIL, never a process
 * crash. Steps can also throw it directly (the session harness's
 * `ensurePlayerRouteTop` does) to carry step details into the report.
 */
internal class StepFailure(
    message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

/**
 * The one runner chassis behind all three desktop E2E lanes
 * ([DesktopSessionHarness], [DesktopFlowHarness],
 * [DesktopNativeDialogHarness]; extracted from their byte-identical
 * private Runner twins). Owns the step ledger ([step] + [StepFailure]),
 * the [awaitUntil] poller, the auto-exit deadline timer
 * ([armAutoExit], the DesktopStartupPerf pattern — a hung run still
 * leaves a partial report and a defined exit) and the
 * [SessionHarnessReport] writer (the hand-rolled JSON shape is pinned by
 * SessionHarnessReportTest and stays byte-for-byte).
 *
 * Per-lane config arrives via the constructor:
 *  - [logTag] — the `[JellyPlay][…]` log prefix ("harness" / "flowpass" /
 *    "dialogpass"); also names the daemon auto-exit timer.
 *  - [harnessName] / [reportFileName] — the report's `harness` identity and
 *    the `<logs>/…` file (`desktop-session`·`session-harness.json`,
 *    `desktop-native-dialog-flows`·`flow-harness.json`,
 *    `desktop-native-dialog`·`dialog-harness.json`).
 *  - [pollIntervalMs] — the [awaitUntil] cadence, deliberately kept
 *    per lane (see below).
 *  - [exitCodeReflectsReport] — session/dialog passes always
 *    `exitProcess(0)` (the runner script greps `overallPass` out of the
 *    report, which is the verdict), while the flows lane wires the process
 *    exit code to CI directly (1 on fatal, 1 when `overallPass` is false).
 *  - [machineFacts] — the report's per-lane `machine` map supplier.
 *
 * Each harness object keeps its own `currentFatalHandler` static hook so a
 * crash in one lane routes to that lane's active runner only
 * (`runIfRequested`'s catch calls it; a harness crash is a harness FAIL,
 * never an app crash report).
 */
internal class HarnessRunner(
    private val logTag: String,
    private val harnessName: String,
    private val reportFileName: String,
    private val logsDir: Path,
    private val autoExitSeconds: Int,
    private val pollIntervalMs: Long,
    private val exitCodeReflectsReport: Boolean,
    private val machineFacts: () -> Map<String, String>,
) {
    /** Shared t=+ms origin: the evidence-helper stamps correlate against this. */
    val startedAtMs: Long = System.currentTimeMillis()

    private val finished = AtomicBoolean(false)
    private val lock = Any()
    private val steps = ArrayList<StepResult>()
    private var fatal: Throwable? = null

    @Volatile
    private var lastOverallPass: Boolean = false

    /** Deadline timer: write whatever exists, exit (perf-harness twin). */
    fun armAutoExit() {
        Timer("jellyplay-$logTag", true).schedule(
            object : TimerTask() {
                override fun run() {
                    System.err.println(
                        "[JellyPlay][$logTag] auto-exit deadline (${autoExitSeconds}s) reached",
                    )
                    finishWithFatal(IllegalStateException("auto-exit deadline reached"))
                }
            },
            autoExitSeconds * 1000L,
        )
    }

    fun finishWithFatal(e: Throwable) {
        if (!finished.compareAndSet(false, true)) return
        synchronized(lock) { fatal = e }
        runCatching { writeReport() }
        exitProcess(if (exitCodeReflectsReport) 1 else 0)
    }

    fun writeReportAndExit() {
        if (!finished.compareAndSet(false, true)) return
        runCatching { writeReport() }
        exitProcess(
            if (exitCodeReflectsReport) {
                if (lastOverallPass) 0 else 1
            } else {
                0
            },
        )
    }

    /**
     * Runs [block]; PASS when it returns a details map, FAIL when it
     * throws (a [StepFailure] carries optional details; anything else is
     * recorded with its message). A step FAIL never crashes the run.
     *
     * One signature for all three lanes: the session harness's blocks use
     * the [StepContext] receiver's `fail(...)` (carrying details into the
     * report), while the flows/dialog lanes' blocks keep using plain
     * `error(...)`/`check(...)` — bodies written without a receiver compile
     * unchanged.
     */
    suspend fun step(
        name: String,
        block: suspend StepContext.() -> Map<String, String>,
    ): Boolean {
        val atMs = System.currentTimeMillis()
        val result = try {
            val details = StepContext().block()
            StepResult(name, pass = true, atMs, elapsedSince(atMs), details, error = null)
        } catch (e: StepFailure) {
            StepResult(name, pass = false, atMs, elapsedSince(atMs), e.details, e.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            StepResult(name, pass = false, atMs, elapsedSince(atMs), emptyMap(), "$e")
        }
        println(
            "[JellyPlay][$logTag] step ${result.name}: " +
                (if (result.pass) "PASS" else "FAIL") +
                (result.error?.let { " — $it" } ?: "") +
                " (${result.durationMs}ms)",
        )
        synchronized(lock) { steps += result }
        return result.pass
    }

    /** Per-step receiver: `fail(...)` aborts the step as a FAIL, with optional report details. */
    class StepContext {
        fun fail(message: String, details: Map<String, String> = emptyMap()): Nothing =
            throw StepFailure(message, details)
    }

    /**
     * Poll [poll] until it returns true or [timeoutMs] elapses; one final
     * poll runs after the deadline so a just-ready condition is not missed
     * (all three lanes' original shape).
     *
     * ONE signature serves both poll styles: a plain `() -> Boolean` lambda
     * passes unchanged where `suspend () -> Boolean` is expected (Kotlin
     * adapts it), so the session harness's non-suspend polls and the flows
     * harness's suspending polls (e.g. awaitTarget's intra-poll settle
     * delay) share this chassis. The old per-lane poll cadence (200 ms
     * session / 150 ms flows / 100 ms dialog) is preserved per lane via
     * [pollIntervalMs] — deliberately kept as tuned wall-clock timing, not
     * unified.
     */
    suspend fun awaitUntil(timeoutMs: Long, poll: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0)
        while (System.currentTimeMillis() < deadline) {
            if (poll()) return true
            delay(pollIntervalMs)
        }
        return poll()
    }

    // ── report ──────────────────────────────────────────────────────────────

    private fun writeReport() {
        val (stepList, fatalErr) = synchronized(lock) { steps.toList() to fatal }
        val overallPass = stepList.isNotEmpty() && stepList.all { it.pass } && fatalErr == null
        lastOverallPass = overallPass
        val json = SessionHarnessReport(
            startedAtMs = startedAtMs,
            finishedAtMs = System.currentTimeMillis(),
            overallPass = overallPass,
            fatal = fatalErr?.toString(),
            machine = machineFacts(),
            steps = stepList,
            harness = harnessName,
        ).toJson()
        Files.createDirectories(logsDir)
        Files.writeString(logsDir.resolve(reportFileName), json)
        println(
            "[JellyPlay][$logTag] report written: $logsDir${File.separatorChar}$reportFileName",
        )
    }

    private fun elapsedSince(atMs: Long): Long = System.currentTimeMillis() - atMs
}

/**
 * The one Robot evidence chassis behind the desktop E2E lanes (extracted
 * from DesktopSessionHarness's private helpers, which are the superset;
 * the flows lane's copies were slimmed-down twins of these). Owns:
 * the cached [Robot] ([robotOrNull]), the AWT focus/key diagnostics
 * ([armFocusDiagnostics]), key injection ([injectKey]) and window
 * screenshots — both the session lane's FAIL-the-step flavor ([screenshot])
 * and the flows lane's skip-and-log best-effort flavor
 * ([screenshotWindow]). Every failure is a step FAIL (or a skipped
 * best-effort capture), never a process crash.
 *
 * All `t=+…ms` stamps are taken against the runner's [startedAtMs] so flap
 * cycles correlate against the step timeline and the bringWindowToFront
 * marks (the focus-thief lesson below makes them checkable).
 */
internal class HarnessRobot(
    private val logTag: String,
    private val startedAtMs: Long,
    private val screenshotDir: Path,
    private val mainWindow: () -> ComposeWindow?,
) {
    private var robot: Robot? = null

    fun robotOrNull(): Robot? {
        robot?.let { return it }
        val r = runCatching { Robot().apply { isAutoWaitForIdle = false } }.onFailure {
            System.err.println("[JellyPlay][$logTag] Robot unavailable: $it")
        }.getOrNull()
        robot = r
        return r
    }

    /**
     *  focus diagnostics: log every AWT focus-owner / focused-window
     * change (class + owning-window identity) and every key event's AWT
     * dispatch target, so a failing OVERLAY_SPACE run shows whether the
     * Robot-injected keys landed on the Compose component at all (e.g. the
     *  mpv SwingPanel Canvas stealing AWT focus) and how the owner
     * moved between the grab effect's attempts and the injection.
     */
    fun armFocusDiagnostics() {
        val ourWindow = mainWindow()
        runCatching {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener { evt ->
                    when (evt.propertyName) {
                        "focusOwner", "permanentFocusOwner", "focusedWindow" ->
                            println(
                                // every line is stamped with the
                                // elapsed-ms since the run started, so flap
                                // cycles correlate against the step timeline
                                // and the bringWindowToFront marks below
                                // (the log had no timestamps, so
                                // the flap's driver could only be guessed).
                                "[JellyPlay][$logTag][awt-focus] t=+" +
                                    (System.currentTimeMillis() - startedAtMs) + "ms " +
                                    "${evt.propertyName}: " +
                                    "${evt.oldValue.awtDescribe(ourWindow)} -> " +
                                    "${evt.newValue.awtDescribe(ourWindow)}",
                            )
                    }
                }
        }.onFailure { System.err.println("[JellyPlay][$logTag] KFM listener failed: $it") }
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
                { event ->
                    if (event is java.awt.event.KeyEvent) {
                        val id = when (event.id) {
                            java.awt.event.KeyEvent.KEY_PRESSED -> "PRESSED"
                            java.awt.event.KeyEvent.KEY_RELEASED -> "RELEASED"
                            java.awt.event.KeyEvent.KEY_TYPED -> "TYPED"
                            else -> event.id.toString()
                        }
                        println(
                            "[JellyPlay][$logTag][awt-key] $id " +
                                "code=${event.keyCode} src=" +
                                (event.source as? java.awt.Component)?.awtDescribe(ourWindow),
                        )
                    }
                },
                java.awt.AWTEvent.KEY_EVENT_MASK,
            )
        }.onFailure { System.err.println("[JellyPlay][$logTag] AWT key listener failed: $it") }
    }

    private fun Any?.awtDescribe(ourWindow: java.awt.Window?): String {
        val c = this as? java.awt.Component ?: return toString()
        val ownerWindow = javax.swing.SwingUtilities.getWindowAncestor(c)
        return "${c.javaClass.name}@${Integer.toHexString(System.identityHashCode(c))} " +
            "inWindow=${ownerWindow?.javaClass?.simpleName} " +
            "isOurComposeWindow=${ownerWindow != null && ownerWindow === ourWindow}"
    }

    /**
     * Inject [keyCode] through the Robot after [bringWindowToFront] +
     * a focus-state snapshot. Returns false when the Robot or the main
     * window is unavailable or AWT refuses the injection (a step FAIL for
     * the caller); rethrows cancellation.
     */
    suspend fun injectKey(keyCode: Int, reason: String): Boolean {
        val r = robotOrNull() ?: return false
        val window = mainWindow() ?: return false
        return runCatchingRethrowingCancellation {
            bringWindowToFront(window, "injectKey($keyCode) $reason")
            // Snapshot the AWT focus state at injection time —
            // Robot delivers to the OS-focused window whose AWT focus owner
            // receives the key; this line pairs with the [awt-key] events
            // that follow.
            val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
            println(
                "[JellyPlay][$logTag][awt-focus] t=+" +
                    (System.currentTimeMillis() - startedAtMs) + "ms " +
                    "injectKey=$keyCode focusOwner=" +
                    "${kfm.focusOwner.awtDescribe(window)} focusedWindow=" +
                    "${kfm.focusedWindow.awtDescribe(window)}",
            )
            r.keyPress(keyCode)
            delay(60)
            r.keyRelease(keyCode)
            delay(200)
            true
        }.onFailure {
            System.err.println("[JellyPlay][$logTag] key injection failed: $it")
        }.getOrDefault(false)
    }

    /**
     * Captures the main window's screen rect to `<screenshotDir>/<name>.png`,
     * returning the step-details map. Throws [StepFailure] when the Robot or
     * window is unavailable or the capture fails — the session lane's
     * screenshots ARE steps, so evidence failure FAILs the step; rethrows
     * cancellation (the `runCatchingRethrowingCancellation` house pattern,
     * as [injectKey]/[screenshotWindow]).
     */
    suspend fun screenshot(name: String, reason: String = name): Map<String, String> {
        val r = robotOrNull() ?: throw StepFailure("Robot unavailable")
        val window = mainWindow() ?: throw StepFailure("window unavailable")
        return runCatchingRethrowingCancellation {
            bringWindowToFront(window, "screenshot($reason)")
            delay(250)
            val bounds = window.bounds
            val image = r.createScreenCapture(bounds)
            Files.createDirectories(screenshotDir)
            val file = File(screenshotDir.toFile(), "$name.png")
            ImageIO.write(image, "png", file)
            mapOf("file" to file.absolutePath, "bounds" to bounds.toString())
        }.getOrElse { e ->
            if (e is StepFailure) throw e
            System.err.println("[JellyPlay][$logTag] screenshot '$name' failed: $e")
            throw StepFailure("screenshot '$name' failed: ${e.message}")
        }
    }

    /**
     * Best-effort capture of an explicit [window] (the flows lane's
     * evidence policy: a screenshot ride-along must never fail a click
     * step — missing Robot/window or a failed capture is logged and
     * skipped; [window] may be a non-main AWT window, e.g. a modal sheet).
     * Logged with the t=+…ms stamp the flows lane's diag lines use.
     */
    suspend fun screenshotWindow(name: String, window: java.awt.Window?) {
        val r = robotOrNull() ?: run { stamped("screenshot '$name' skipped: no Robot"); return }
        if (window == null) {
            stamped("screenshot '$name' skipped: no window")
            return
        }
        runCatchingRethrowingCancellation {
            window.toFront()
            delay(250)
            val image: BufferedImage = r.createScreenCapture(window.bounds)
            Files.createDirectories(screenshotDir)
            ImageIO.write(image, "png", File(screenshotDir.toFile(), "$name.png"))
            stamped("screenshot: $name.png (${window.bounds})")
        }.onFailure { stamped("screenshot '$name' failed: $it") }
    }

    /** The flows lane's diag line format: `[JellyPlay][tag] t=+Xms <message>`. */
    private fun stamped(message: String) {
        println(
            "[JellyPlay][$logTag] t=+${System.currentTimeMillis() - startedAtMs}ms $message",
        )
    }

    /**
     *  focus-thief fix: the old version called
     * `window.requestFocus()` on EVERY screenshot and key injection, and
     * each call produced exactly the AWT flap cycle the 14D diagnostics
     * caught (`SkiaLayer → null → ComposeWindow → null → SkiaLayer` — a
     * window-level focus request clears the owner, briefly promotes the
     * window itself, then settles back on the SkiaLayer). The harness was
     * thus CHURNING the app's focus on every evidence step and every
     * injection, widening the focus-less gaps that killed injected keys.
     * Now `requestFocus()` fires only when the AWT actually reports our
     * window unfocused (an external theft — restoring focus is then the
     * point), and the already-focused case is a no-op. A run's remaining
     * flap cycles that carry no `bringWindowToFront` mark within ~300 ms
     * are therefore externally driven (OS/mpv), which the `t=+…ms` stamps
     * make checkable.
     *
     * `jellyplay.harness.noWindowToFront=true` skips both calls entirely
     * (the thief experiment knob; default off — a genuinely
     * buried window still needs toFront for clean screenshots).
     */
    private suspend fun bringWindowToFront(window: ComposeWindow, reason: String) {
        if (System.getProperty(DesktopSessionHarness.PROP_NO_WINDOW_TO_FRONT)
            ?.equals("true", ignoreCase = true) == true
        ) {
            println(
                "[JellyPlay][$logTag][awt-focus] t=+" +
                    (System.currentTimeMillis() - startedAtMs) +
                    "ms bringWindowToFront($reason): SKIPPED (noWindowToFront experiment)",
            )
        } else {
            runCatchingRethrowingCancellation {
                window.toFront()
                val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                if (kfm.focusedWindow === window && kfm.focusOwner != null) {
                    println(
                        "[JellyPlay][$logTag][awt-focus] t=+" +
                            (System.currentTimeMillis() - startedAtMs) +
                            "ms bringWindowToFront($reason): window already focused — " +
                            "requestFocus skipped (flap churn guard)",
                    )
                } else {
                    println(
                        "[JellyPlay][$logTag][awt-focus] t=+" +
                            (System.currentTimeMillis() - startedAtMs) +
                            "ms bringWindowToFront($reason): window lacks focus " +
                            "(focusedWindow=${kfm.focusedWindow.awtDescribe(window)} " +
                            "focusOwner=${kfm.focusOwner.awtDescribe(window)}) — requesting focus",
                    )
                    window.requestFocus()
                }
            }
        }
        delay(200)
    }
}
