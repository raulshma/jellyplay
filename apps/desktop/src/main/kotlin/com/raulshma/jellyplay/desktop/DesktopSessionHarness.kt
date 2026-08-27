package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.desktop.player.EngineActivityRecorder
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.delay

/**
 * Wave 13B real-server session harness — an E2E verification surface, NOT a
 * user code path. Driven entirely by `jellyplay.harness.*` system properties
 * (injected through JAVA_TOOL_OPTIONS by tools/e2e/desktop-session-pass.sh,
 * same pattern as the wave-12A perf harness); when
 * `jellyplay.harness.enabled` is unset, [runIfRequested] returns without
 * touching anything.
 *
 * What one enabled run proves, against a REAL Jellyfin server, inside the
 * REAL windowed app (the whole shared pipeline: VideoPlayerScreen →
 * VideoPlayerViewModel → DesktopMpvPlayerEngineFactory → MpvDesktopEngine):
 *
 *  1. LOGIN — AuthRepository.login(server, user, password) succeeds.
 *  2. PUSH_PLAYER — Route.VideoPlayer pushed onto the nav3 back stack;
 *     the player route composes.
 *  3. PLAYBACK — the engine created by the factory (observed through the
 *     [EngineActivityRecorder] Koin single, never by touching shared modules)
 *     reached isPlaying with a playhead advance ≥ 1 s within 90 s.
 *  4. Screenshots (java.awt.Robot over the window bounds) at signed-in home,
 *     player-open, mid-play and controls-overlay.
 *  5. OVERLAY_SPACE — the wave-9 open question "Esc-dismiss-vs-sheet popup
 *     ordering on desktop", probed through the one keyboard-reachable overlay:
 *     VideoPlayerScreen has NO keyboard-reachable bottom sheet (its non-TV
 *     handler covers space/arrows/F/M/J/L/Esc only; every PlayerSheet opens
 *     through pointer clicks), and its sheets render IN-WINDOW
 *     (PlayerModalBottomSheet/InWindowPlayerSheet — not a separate dialog
 *     window), so key ordering is decided by DesktopNavScaffold's
 *     onPreviewKeyEvent. SPACE must toggle play/pause (wave 14A made this a
 *     REGRESSION GATE, ESC_SEQUENCE-style: the step captures the isPlaying
 *     flip + playhead freeze from the [EngineActivityRecorder] samples into
 *     the report and FAILS when playback does not toggle — before wave 14A
 *     the player Box held no focus while the controls were visible, so SPACE
 *     died at the scaffold's null-focus fallback chain and this exact gate
 *     was the wave-13B focus finding). Then a single ESC — the verified
 *     ordering on this platform is that the scaffold's back handling wins
 *     (the route pops; the screen's own Esc branch would only hide the
 *     controls). ESC_SEQUENCE is a REGRESSION GATE on that ordering: a
 *     not-popping run records the finding (it would answer wave-9's question
 *     the other way) and FAILS the step, so the tool goes red rather than
 *     silently re-baselining.
 *  6. Report — `<logs>/session-harness.json` (steps, pass/fail, machine
 *     facts incl. the surface branch the factory used), then exitProcess(0).
 *
 * Wiring: DesktopAppRoot hosts [DesktopSessionHarnessHost] (a LaunchedEffect;
 * the harness needs no UI of its own); DesktopNavScaffold publishes its back
 * stack through [attachBackStackProvider] once composed (the harness performs
 * the login itself and waits for the scaffold to appear after
 * isAuthenticated flips). All deps arrive via [SessionHarnessDeps] — the
 * object is Koin-agnostic.
 *
 * Properties:
 *  - `jellyplay.harness.enabled`       — "true" arms the harness (required).
 *  - `jellyplay.harness.serverUrl`     — Jellyfin base URL (required).
 *  - `jellyplay.harness.username`      — (required).
 *  - `jellyplay.harness.password`      — (required).
 *  - `jellyplay.harness.itemId`        — movie item to play (required).
 *  - `jellyplay.harness.autoExitSeconds` — hard-exit deadline, default 120.
 *  - `jellyplay.harness.screenshotDir` — default `<dataDir>/harness-shots`.
 */
object DesktopSessionHarness {

    const val PROP_ENABLED = "jellyplay.harness.enabled"
    const val PROP_SERVER_URL = "jellyplay.harness.serverUrl"
    const val PROP_USERNAME = "jellyplay.harness.username"
    const val PROP_PASSWORD = "jellyplay.harness.password"
    const val PROP_ITEM_ID = "jellyplay.harness.itemId"
    const val PROP_AUTO_EXIT_SECONDS = "jellyplay.harness.autoExitSeconds"
    const val PROP_SCREENSHOT_DIR = "jellyplay.harness.screenshotDir"

    /** True only when `jellyplay.harness.enabled=true` — the zero-cost gate. */
    fun requested(): Boolean =
        System.getProperty(PROP_ENABLED)?.equals("true", ignoreCase = true) == true

    /**
     * Back stack of the CURRENT top-level tab, published by
     * DesktopNavScaffold. Provider form (not a captured list) so pops are
     * read from the live tab even if the tab ever changed.
     */
    @Volatile
    private var backStackProvider: (() -> MutableList<NavKey>?)? = null

    /** Called from DesktopNavScaffold's composition (idempotent). */
    fun attachBackStackProvider(provider: () -> MutableList<NavKey>?) {
        backStackProvider = provider
    }

    /** Everything the harness needs from the app; keeps the object Koin-free. */
    class SessionHarnessDeps(
        val authRepository: AuthRepository,
        val windowRef: AtomicReference<ComposeWindow?>?,
        val engineRecorder: EngineActivityRecorder,
    )

    /**
     * Entry point from DesktopAppRoot's LaunchedEffect. No-op unless
     * [requested]; a full run always ends in exitProcess(0) (report written
     * best-effort even on the auto-exit deadline path).
     */
    suspend fun runIfRequested(deps: SessionHarnessDeps) {
        if (!requested()) return
        try {
            SessionRunner(deps).run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A harness crash is a harness FAIL, never an app crash report.
            System.err.println("[JellyPlay][harness] fatal: $e")
            e.printStackTrace(System.err)
            SessionRunner.currentFatalHandler?.invoke(e)
        }
    }

    /**
     * One session's step machine + report writer. Owns the auto-exit timer so
     * a hung run still leaves a (partial) report and exits 0 — the
     * DesktopStartupPerf auto-exit pattern.
     */
    private class SessionRunner(private val deps: SessionHarnessDeps) {
        private val startedAtMs = System.currentTimeMillis()
        private val finished = AtomicBoolean(false)
        private val lock = Any()
        private val steps = ArrayList<StepResult>()
        private var fatal: Throwable? = null

        private val serverUrl = System.getProperty(PROP_SERVER_URL)?.trim().orEmpty()
        private val username = System.getProperty(PROP_USERNAME)?.trim().orEmpty()
        private val password = System.getProperty(PROP_PASSWORD).orEmpty()
        private val itemId = System.getProperty(PROP_ITEM_ID)?.trim().orEmpty()
        private val autoExitSeconds = System.getProperty(PROP_AUTO_EXIT_SECONDS)?.toIntOrNull()
            ?: DEFAULT_AUTO_EXIT_SECONDS
        private val paths = DesktopPaths.resolve()
        private val logsDir: Path = paths.logsDirNio
        private val screenshotDir: Path = System.getProperty(PROP_SCREENSHOT_DIR)?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: paths.dataDirNio.resolve("harness-shots")

        private var robot: Robot? = null

        init {
            // The active runner's crash/deadline handler (a static hook is the
            // simplest way for runIfRequested's catch to reach this instance).
            currentFatalHandler = { e -> finishWithFatal(e) }
        }

        suspend fun run() {
            armAutoExit()
            println(
                "[JellyPlay][harness] enabled: server=$serverUrl item=$itemId " +
                    "logs=$logsDir shots=$screenshotDir autoExit=${autoExitSeconds}s",
            )

            val configOk = step("CONFIG") {
                check(serverUrl.isNotEmpty()) { "missing $PROP_SERVER_URL" }
                check(username.isNotEmpty()) { "missing $PROP_USERNAME" }
                check(password.isNotEmpty()) { "missing $PROP_PASSWORD" }
                check(itemId.isNotEmpty()) { "missing $PROP_ITEM_ID" }
                mapOf("serverUrl" to serverUrl, "itemId" to itemId)
            }

            var fatalStop = !configOk

            val loginOk = !fatalStop && step("LOGIN") {
                val user = deps.authRepository.login(serverUrl, username, password)
                    .getOrElse { fail("login failed: ${it.message}") }
                mapOf("user" to (user.name ?: username), "userId" to user.id)
            }
            fatalStop = fatalStop || !loginOk

            val navOk = !fatalStop && step("NAV_READY") {
                awaitUntil(30_000) { backStackProvider != null } ||
                    fail("nav scaffold never composed (back stack provider not attached)")
                mapOf("backStackSize" to (currentBackStack()?.size ?: -1).toString())
            }
            fatalStop = fatalStop || !navOk

            if (!fatalStop) step("SCREENSHOT_HOME") { screenshot("signed-in-home") }

            val pushOk = !fatalStop && step("PUSH_PLAYER") {
                val stack = currentBackStack() ?: fail("no back stack")
                stack.add(Route.VideoPlayer(itemId = itemId))
                awaitUntil(5_000) { stack.lastOrNull() is Route.VideoPlayer } ||
                    fail("VideoPlayer route never reached the top of the back stack")
                mapOf("backStackSize" to stack.size.toString())
            }
            fatalStop = fatalStop || !pushOk

            val pushAtMs = System.currentTimeMillis()
            var engine = EngineActivitySnapshot.NONE
            val engineOk = !fatalStop && step("ENGINE_CREATED") {
                awaitUntil(45_000) {
                    engine = deps.engineRecorder.latestVideoEngine()
                    engine.createdAtMs >= pushAtMs && engine.surface.isNotEmpty()
                } || fail("no engine recorded by the factory after route push")
                mapOf(
                    "surface" to engine.surface,
                    "engine" to engine.displayName,
                    "createdAtMs" to engine.createdAtMs.toString(),
                )
            }
            fatalStop = fatalStop || !engineOk

            if (!fatalStop) step("SCREENSHOT_PLAYER_OPEN") { screenshot("player-open") }

            val playbackOk = !fatalStop && step("PLAYBACK") {
                awaitUntil((90_000 - elapsedSince(pushAtMs)).coerceAtLeast(1_000)) {
                    engine = deps.engineRecorder.latestVideoEngine()
                    engine.playbackVerified()
                } || fail(
                    "playback not verified within 90s of push " +
                        "(isPlayingObserved=${engine.isPlayingObserved} " +
                        "playingAdvanceMs=${engine.playingAdvanceMs()} " +
                        "transitions=${engine.transitions.joinToString { it.toState }})",
                )
                mapOf(
                    "surface" to engine.surface,
                    "engine" to engine.displayName,
                    "isPlayingObserved" to engine.isPlayingObserved.toString(),
                    "playingAdvanceMs" to engine.playingAdvanceMs().toString(),
                    "transitions" to engine.transitions.joinToString(",") { it.toState },
                )
            }
            fatalStop = fatalStop || !playbackOk

            if (!fatalStop) step("SCREENSHOT_MID_PLAY") { screenshot("mid-play") }

            // Wave-9 question: no keyboard-reachable sheet exists (see KDoc);
            // the SPACE-reachable controls overlay is the ordering probe.
            // Wave 14A: the SPACE leg became a REGRESSION GATE (fail when
            // playback does not toggle) — pre-fix runs recorded it as a
            // "focus finding" because the player Box held no focus while the
            // controls were visible, and the key died at the scaffold's
            // null-focus fallback chain.
            step("SHEET_TRIGGER_SCAN") {
                mapOf(
                    "keyboardReachableSheet" to "none",
                    "finding" to
                        "VideoPlayerScreen's non-TV key handler covers space/arrows/F/M/J/L/" +
                        "Esc only; every PlayerSheet opens via pointer clicks. Sheets render " +
                        "in-window (PlayerModalBottomSheet), so Esc ordering is decided by " +
                        "DesktopNavScaffold.onPreviewKeyEvent — probed via the SPACE overlay.",
                )
            }

            var spaceReachedPlayer = false
            val overlayOk = !fatalStop && step("OVERLAY_SPACE") {
                // Pre-condition: the engine must have sampled as PLAYING
                // before the key lands, so a flip is attributable to SPACE.
                deps.engineRecorder.latestVideoEngine().positionSamples.any { it.isPlaying } ||
                    fail("no playing sample recorded before SPACE — playback liveness unproven")
                val spaceAtMs = System.currentTimeMillis()
                injectKey(KeyEvent.VK_SPACE) || fail("SPACE injection failed (Robot)")
                val toggled = awaitUntil(SPACE_TOGGLE_TIMEOUT_MS) {
                    deps.engineRecorder.latestVideoEngine().pausedSince(spaceAtMs)
                }
                spaceReachedPlayer = toggled
                delay(900) // let the controls overlay settle, as before the screenshot
                screenshot("player-controls-overlay")
                val advance = deps.engineRecorder.latestVideoEngine().advanceSinceMs(spaceAtMs)
                val details = mapOf(
                    "spaceReachedPlayer" to toggled.toString(),
                    "playheadAdvanceSinceSpaceMs" to advance.toString(),
                    "note" to "SPACE pauses + shows controls when the player key handler gets it " +
                        "(wave 14A regression gate: a run whose playback does not toggle FAILS)",
                )
                if (!toggled) {
                    fail(
                        "SPACE did not toggle playback within ${SPACE_TOGGLE_TIMEOUT_MS / 1000}s " +
                            "of injection (playheadAdvanceSinceSpaceMs=$advance) — the key never " +
                            "reached VideoPlayerScreen's key handler (focus gap regression)",
                        details,
                    )
                } else {
                    details
                }
            }

            step("ESC_SEQUENCE") {
                val stack = currentBackStack() ?: fail("no back stack")
                val before = stack.size
                injectKey(KeyEvent.VK_ESCAPE) || fail("ESC injection failed (Robot)")
                val popped = awaitUntil(10_000) { stack.size < before }
                val overlayNote = if (spaceReachedPlayer) {
                    "with the SPACE-shown controls overlay on screen — the shell's back " +
                        "handling consumed Esc before VideoPlayerScreen's own Esc branch " +
                        "could dismiss the overlay. Popup ordering: shell back > overlay/sheet."
                } else {
                    "the SPACE probe did NOT reach the player's key handler (playhead kept " +
                        "advancing, so no overlay was showing when Esc landed — a focus " +
                        "finding in itself). Ordering still unambiguous: DesktopNavScaffold's " +
                        "onPreviewKeyEvent consumed Esc and popped the route; nothing on the " +
                        "player screen intercepted it."
                }
                val details = mapOf(
                    "backStackSizeBefore" to before.toString(),
                    "backStackSizeAfter" to stack.size.toString(),
                    "playerPopped" to popped.toString(),
                    "escCount" to "1",
                    "overlayVisibleWhenEscInjected" to spaceReachedPlayer.toString(),
                    "finding" to if (popped) {
                        "Single ESC popped the player route (stack $before → ${stack.size}); $overlayNote"
                    } else {
                        "ESC did NOT pop the player route within 10s (stack still $before) — " +
                            "the overlay/sheet layering consumed it; shell back handling did " +
                            "not win. This answers wave-9's ordering question the OTHER way."
                    },
                )
                if (!popped) fail("ESC did not pop the player route", details) else details
            }

            writeReportAndExit()
        }

        /** Deadline timer: write whatever exists, exit 0 (perf-harness twin). */
        private fun armAutoExit() {
            Timer("jellyplay-harness", true).schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        System.err.println(
                            "[JellyPlay][harness] auto-exit deadline (${autoExitSeconds}s) reached",
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
            exitProcess(0)
        }

        private fun writeReportAndExit() {
            if (!finished.compareAndSet(false, true)) return
            runCatching { writeReport() }
            exitProcess(0)
        }

        // ── steps ───────────────────────────────────────────────────────────

        /**
         * Runs [block]; PASS when it returns a details map, FAIL when it
         * throws (a [StepFailure] carries optional details; anything else is
         * recorded with its message). A step FAIL never crashes the run.
         */
        private suspend fun step(
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
                "[JellyPlay][harness] step ${result.name}: " +
                    (if (result.pass) "PASS" else "FAIL") +
                    (result.error?.let { " — $it" } ?: "") +
                    " (${result.durationMs}ms)",
            )
            synchronized(lock) { steps += result }
            return result.pass
        }

        /** Thrown by `fail()` — a step FAIL, never a process crash. */
        private class StepFailure(
            message: String,
            val details: Map<String, String> = emptyMap(),
        ) : RuntimeException(message)

        private inner class StepContext {
            fun fail(message: String, details: Map<String, String> = emptyMap()): Nothing =
                throw StepFailure(message, details)
        }

        private suspend fun awaitUntil(timeoutMs: Long, poll: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0)
            while (System.currentTimeMillis() < deadline) {
                if (poll()) return true
                delay(200)
            }
            return poll()
        }

        private fun currentBackStack(): MutableList<NavKey>? = backStackProvider?.invoke()

        // ── evidence helpers (Robot; every failure is a step FAIL, not a crash) ──

        private fun robotOrNull(): Robot? {
            robot?.let { return it }
            val r = runCatching { Robot().apply { isAutoWaitForIdle = false } }.onFailure {
                System.err.println("[JellyPlay][harness] Robot unavailable: $it")
            }.getOrNull()
            robot = r
            return r
        }

        private suspend fun injectKey(keyCode: Int): Boolean {
            val r = robotOrNull() ?: return false
            val window = deps.windowRef?.get() ?: return false
            return runCatching {
                bringWindowToFront(window)
                r.keyPress(keyCode)
                delay(60)
                r.keyRelease(keyCode)
                delay(200)
                true
            }.onFailure {
                System.err.println("[JellyPlay][harness] key injection failed: $it")
            }.getOrDefault(false)
        }

        /** Captures the window's screen rect to `<screenshotDir>/<name>.png`. */
        private suspend fun screenshot(name: String): Map<String, String> {
            val r = robotOrNull() ?: throw StepFailure("Robot unavailable")
            val window = deps.windowRef?.get() ?: throw StepFailure("window not available")
            try {
                bringWindowToFront(window)
                delay(250)
                val bounds: Rectangle = window.bounds
                val image: BufferedImage = r.createScreenCapture(bounds)
                Files.createDirectories(screenshotDir)
                val file = File(screenshotDir.toFile(), "$name.png")
                ImageIO.write(image, "png", file)
                return mapOf("file" to file.absolutePath, "bounds" to bounds.toString())
            } catch (e: StepFailure) {
                throw e
            } catch (e: Exception) {
                System.err.println("[JellyPlay][harness] screenshot '$name' failed: $e")
                throw StepFailure("screenshot '$name' failed: ${e.message}")
            }
        }

        private suspend fun bringWindowToFront(window: ComposeWindow) {
            runCatching {
                window.toFront()
                window.requestFocus()
            }
            delay(200)
        }

        private fun elapsedSince(atMs: Long): Long = System.currentTimeMillis() - atMs

        // ── report ──────────────────────────────────────────────────────────

        private fun machineFacts(): Map<String, String> {
            val engine = deps.engineRecorder.latestVideoEngine()
            return buildMap {
                put("os.name", System.getProperty("os.name"))
                put("os.version", System.getProperty("os.version"))
                put("java.version", System.getProperty("java.version"))
                put("surfaceMode", engine.surface.ifEmpty { "UNKNOWN" })
                put("engineDisplayName", engine.displayName.ifEmpty { "UNKNOWN" })
                put("serverUrl", serverUrl)
                put("itemId", itemId)
            }
        }

        private fun writeReport() {
            val (stepList, fatalErr) = synchronized(lock) { steps.toList() to fatal }
            val json = SessionHarnessReport(
                startedAtMs = startedAtMs,
                finishedAtMs = System.currentTimeMillis(),
                overallPass = stepList.isNotEmpty() && stepList.all { it.pass } && fatalErr == null,
                fatal = fatalErr?.toString(),
                machine = machineFacts(),
                steps = stepList,
            ).toJson()
            Files.createDirectories(logsDir)
            Files.writeString(logsDir.resolve(REPORT_FILE_NAME), json)
            println("[JellyPlay][harness] report written: $logsDir${File.separatorChar}$REPORT_FILE_NAME")
        }

        companion object {
            @Volatile
            var currentFatalHandler: ((Throwable) -> Unit)? = null
        }
    }

    private const val DEFAULT_AUTO_EXIT_SECONDS = 120
    private const val REPORT_FILE_NAME = "session-harness.json"

    /**
     * How long OVERLAY_SPACE waits for the sampled isPlaying flip after
     * injecting SPACE. The recorder samples on a ~500 ms cadence, so a real
     * toggle clears within ~1 s; 8 s leaves margin for a busy UI thread
     * without eating into the auto-exit deadline.
     */
    private const val SPACE_TOGGLE_TIMEOUT_MS = 8_000L
}

/** One recorded harness step. */
data class StepResult(
    val name: String,
    val pass: Boolean,
    val atMs: Long,
    val durationMs: Long,
    val details: Map<String, String>,
    val error: String?,
)

/** The whole run; toJson emits the `<logs>/session-harness.json` payload. */
data class SessionHarnessReport(
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val overallPass: Boolean,
    val fatal: String?,
    val machine: Map<String, String>,
    val steps: List<StepResult>,
) {
    /**
     * Hand-rolled JSON (the apps/desktop module does not apply the
     * serialization compiler plugin; DesktopStartupPerf set this precedent).
     * The unit tests pin the shape by parsing the output.
     */
    fun toJson(): String = buildString {
        append('{')
        append("\"harness\":\"desktop-session\"")
        append(",\"overallPass\":").append(overallPass)
        append(",\"startedAtMs\":").append(startedAtMs)
        append(",\"finishedAtMs\":").append(finishedAtMs)
        fatal?.let { append(",\"fatal\":").append(jsonString(it)) }
        append(",\"machine\":{")
        machine.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append(jsonString(k)).append(':').append(jsonString(v))
        }
        append('}')
        append(",\"steps\":[")
        steps.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("{\"name\":").append(jsonString(s.name))
            append(",\"pass\":").append(s.pass)
            append(",\"atMs\":").append(s.atMs)
            append(",\"durationMs\":").append(s.durationMs)
            append(",\"error\":").append(s.error?.let(::jsonString) ?: "null")
            append(",\"details\":{")
            s.details.entries.forEachIndexed { j, (k, v) ->
                if (j > 0) append(',')
                append(jsonString(k)).append(':').append(jsonString(v))
            }
            append("}}")
        }
        append(']')
        append('}')
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

/**
 * Composition-site sugar so DesktopAppRoot hosts the harness in one call.
 * Internal — only the desktop shell uses it. Gated by [DesktopSessionHarness.requested]
 * at the call site so a normal boot composes nothing here.
 */
@Composable
internal fun DesktopSessionHarnessHost(
    authRepository: AuthRepository,
    windowRef: AtomicReference<ComposeWindow?>?,
    engineRecorder: EngineActivityRecorder,
) {
    LaunchedEffect(windowRef) {
        DesktopSessionHarness.runIfRequested(
            DesktopSessionHarness.SessionHarnessDeps(
                authRepository = authRepository,
                windowRef = windowRef,
                engineRecorder = engineRecorder,
            ),
        )
    }
}
