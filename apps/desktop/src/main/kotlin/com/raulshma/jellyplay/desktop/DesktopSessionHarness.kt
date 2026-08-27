package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.desktop.player.EngineActivityRecorder
import com.raulshma.jellyplay.desktop.player.EngineActivitySnapshot
import com.raulshma.jellyplay.feature.player.video.DesktopPlayerKeyBridge
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

    /**
     * Wave 14E thief-experiment knob: `true` makes every screenshot/injection
     * skip toFront()+requestFocus() entirely, so a run's AWT flap cycles can
     * be attributed (all cycles persisting without any bringWindowToFront
     * mark are externally driven). Default off — normal runs use the
     * conditional requestFocus churn guard instead.
     */
    const val PROP_NO_WINDOW_TO_FRONT = "jellyplay.harness.noWindowToFront"

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
            armFocusDiagnostics()
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
                // Wave 14E: the 12 s harness clip can hit EOF while login /
                // screenshots burn wall time under machine load; the screen
                // then auto-pops the route (closePlayer → onBack) BEFORE the
                // SPACE probe — the failed merged-tree run injected SPACE and
                // ESC against a player that was gone or going. Re-push first
                // so this step always probes the REAL mechanism (key → player
                // handler) and never a popped stack.
                ensurePlayerRouteTop()
                // Pre-condition: the CURRENT engine must have sampled as
                // PLAYING before the key lands, so a flip is attributable to
                // SPACE (a re-push above created a fresh engine).
                awaitUntil(45_000) {
                    deps.engineRecorder.latestVideoEngine().positionSamples.any { it.isPlaying }
                } || fail("no playing sample recorded before SPACE — playback liveness unproven")
                var spaceAtMs = 0L
                var toggled = false
                val attemptNotes = ArrayList<String>()
                var attempts = 0
                while (true) {
                    attempts += 1
                    // Wave 14E retry: snapshot the bridge's delivery counter
                    // around each injection. An unchanged count after the
                    // probe window PROVES the key never reached the player's
                    // handler (a flap gap swallowed it) — retry. A moved count
                    // with no playback toggle is a genuine handler-ran-did-
                    // not-toggle failure and stops immediately.
                    val beforeCount = DesktopPlayerKeyBridge.deliveryCount()
                    spaceAtMs = System.currentTimeMillis()
                    injectKey(KeyEvent.VK_SPACE, "OVERLAY_SPACE attempt $attempts") ||
                        fail("SPACE injection failed (Robot)")
                    toggled = awaitUntil(SPACE_TOGGLE_PROBE_MS) {
                        deps.engineRecorder.latestVideoEngine().pausedSince(spaceAtMs)
                    }
                    val reached = DesktopPlayerKeyBridge.deliveryCount() > beforeCount
                    attemptNotes += "attempt=$attempts reached=$reached toggled=$toggled"
                    println(
                        "[JellyPlay][harness] OVERLAY_SPACE ${attemptNotes.last()} " +
                            "(deliveryCount=$beforeCount→${DesktopPlayerKeyBridge.deliveryCount()})",
                    )
                    if (toggled) break
                    if (reached) {
                        fail(
                            "SPACE reached the player's key handler (after $attempts injection(s)) " +
                                "but playback did not toggle within ${SPACE_TOGGLE_PROBE_MS / 1000}s " +
                                "(playheadAdvanceSinceSpaceMs=" +
                                "${deps.engineRecorder.latestVideoEngine().advanceSinceMs(spaceAtMs)})",
                            mapOf("injections" to attemptNotes.joinToString("; ")),
                        )
                    }
                    if (attempts >= SPACE_MAX_INJECTION_ATTEMPTS) {
                        fail(
                            "SPACE never reached the player's key handler " +
                                "(delivery count unchanged after $attempts injections)",
                            mapOf("injections" to attemptNotes.joinToString("; ")),
                        )
                    }
                    delay(SPACE_RETRY_SPACING_MS)
                }
                spaceReachedPlayer = toggled
                delay(900) // let the controls overlay settle, as before the screenshot
                screenshot("player-controls-overlay", "overlay-space")
                val advance = deps.engineRecorder.latestVideoEngine().advanceSinceMs(spaceAtMs)
                val details = mapOf(
                    "spaceReachedPlayer" to toggled.toString(),
                    "injectionAttempts" to attempts.toString(),
                    "injections" to attemptNotes.joinToString("; "),
                    "playheadAdvanceSinceSpaceMs" to advance.toString(),
                    "note" to "SPACE pauses + shows controls when the player key handler gets it " +
                        "(wave 14A regression gate: a run whose playback does not toggle FAILS). " +
                        "Wave 14E: keys reach the handler through the shell's deterministic " +
                        "DesktopPlayerKeyBridge forward even in focus gaps; the delivery " +
                        "counter gates the per-attempt retry.",
                )
                if (!toggled) {
                    fail(
                        "SPACE did not toggle playback within ${SPACE_TOGGLE_PROBE_MS / 1000}s " +
                            "of injection (playheadAdvanceSinceSpaceMs=$advance) — the key never " +
                            "reached VideoPlayerScreen's key handler (focus gap regression)",
                        details,
                    )
                } else {
                    details
                }
            }

            step("ESC_SEQUENCE") {
                // Wave 14E: tolerate the 12 s clip's EOF auto-pop (the screen's
                // closePlayer flow pops the route without any injected key).
                // Without this the step's regression assertion could compare
                // against a stack that had ALREADY returned to 1 (the failed
                // merged-tree run), reporting ESC as not-popping when the ESC
                // machinery was never actually exercised.
                val repush = ensurePlayerRouteTop()
                val stack = currentBackStack() ?: fail("no back stack")
                val before = stack.size
                injectKey(KeyEvent.VK_ESCAPE, "ESC_SEQUENCE") || fail("ESC injection failed (Robot)")
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
                    "playerRouteRepushed" to (repush["repushed"] ?: "?"),
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

        /**
         * Wave 14D focus diagnostics: log every AWT focus-owner / focused-window
         * change (class + owning-window identity) and every key event's AWT
         * dispatch target, so a failing OVERLAY_SPACE run shows whether the
         * Robot-injected keys landed on the Compose component at all (e.g. the
         * wave-14B mpv SwingPanel Canvas stealing AWT focus) and how the owner
         * moved between the grab effect's attempts and the injection.
         */
        private fun armFocusDiagnostics() {
            val ourWindow = deps.windowRef?.get()
            runCatching {
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addPropertyChangeListener { evt ->
                        when (evt.propertyName) {
                            "focusOwner", "permanentFocusOwner", "focusedWindow" ->
                                println(
                                    // Wave 14E: every line is stamped with the
                                    // elapsed-ms since the run started, so flap
                                    // cycles correlate against the step timeline
                                    // and the bringWindowToFront marks below
                                    // (the wave-14D log had no timestamps, so
                                    // the flap's driver could only be guessed).
                                    "[JellyPlay][harness][awt-focus] t=+" +
                                        (System.currentTimeMillis() - startedAtMs) + "ms " +
                                        "${evt.propertyName}: " +
                                        "${evt.oldValue.awtDescribe(ourWindow)} -> " +
                                        "${evt.newValue.awtDescribe(ourWindow)}",
                                )
                        }
                    }
            }.onFailure { System.err.println("[JellyPlay][harness] KFM listener failed: $it") }
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
                                "[JellyPlay][harness][awt-key] $id " +
                                    "code=${event.keyCode} src=" +
                                    (event.source as? java.awt.Component)?.awtDescribe(ourWindow),
                            )
                        }
                    },
                    java.awt.AWTEvent.KEY_EVENT_MASK,
                )
            }.onFailure { System.err.println("[JellyPlay][harness] AWT key listener failed: $it") }
        }

        private fun Any?.awtDescribe(ourWindow: java.awt.Window?): String {
            val c = this as? java.awt.Component ?: return toString()
            val ownerWindow = javax.swing.SwingUtilities.getWindowAncestor(c)
            return "${c.javaClass.name}@${Integer.toHexString(System.identityHashCode(c))} " +
                "inWindow=${ownerWindow?.javaClass?.simpleName} " +
                "isOurComposeWindow=${ownerWindow != null && ownerWindow === ourWindow}"
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

        /**
         * Wave 14E: guarantee the video player route is the top of the current
         * tab's back stack, re-pushing Route.VideoPlayer when it is not. The
         * 12 s harness clip auto-pops the route at EOF (the screen's
         * closePlayer flow → onBack), so a step that runs late under machine
         * load would otherwise inject keys at a popped stack and report a
         * fake regression. Returns the step detail entry recording whether a
         * re-push happened.
         */
        private suspend fun ensurePlayerRouteTop(): Map<String, String> {
            val stack = currentBackStack() ?: throw StepFailure("no back stack")
            if (stack.lastOrNull() is Route.VideoPlayer) {
                return mapOf("repushed" to "false")
            }
            val sizeAfterAutoPop = stack.size
            stack.add(Route.VideoPlayer(itemId = itemId))
            awaitUntil(5_000) { currentBackStack()?.lastOrNull() is Route.VideoPlayer } ||
                throw StepFailure("re-pushed VideoPlayer route never reached the top of the back stack")
            println(
                "[JellyPlay][harness] player route re-pushed (EOF auto-pop had already " +
                    "returned the stack to $sizeAfterAutoPop)",
            )
            return mapOf("repushed" to "true", "stackSizeAfterAutoPop" to sizeAfterAutoPop.toString())
        }

        // ── evidence helpers (Robot; every failure is a step FAIL, not a crash) ──

        private fun robotOrNull(): Robot? {
            robot?.let { return it }
            val r = runCatching { Robot().apply { isAutoWaitForIdle = false } }.onFailure {
                System.err.println("[JellyPlay][harness] Robot unavailable: $it")
            }.getOrNull()
            robot = r
            return r
        }

        private suspend fun injectKey(keyCode: Int, reason: String): Boolean {
            val r = robotOrNull() ?: return false
            val window = deps.windowRef?.get() ?: return false
            return runCatching {
                bringWindowToFront(window, "injectKey($keyCode) $reason")
                // Wave 14D: snapshot the AWT focus state at injection time —
                // Robot delivers to the OS-focused window whose AWT focus owner
                // receives the key; this line pairs with the [awt-key] events
                // that follow.
                val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                println(
                    "[JellyPlay][harness][awt-focus] t=+" +
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
                System.err.println("[JellyPlay][harness] key injection failed: $it")
            }.getOrDefault(false)
        }

        /** Captures the window's screen rect to `<screenshotDir>/<name>.png`. */
        private suspend fun screenshot(name: String, reason: String = name): Map<String, String> {
            val r = robotOrNull() ?: throw StepFailure("Robot unavailable")
            val window = deps.windowRef?.get() ?: throw StepFailure("window unavailable")
            try {
                bringWindowToFront(window, "screenshot($reason)")
                delay(250)
                val bounds = window.bounds
                val image = r.createScreenCapture(bounds)
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

        /**
         * Wave 14E focus-thief fix: the old version called
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
         * (the wave-14E thief experiment knob; default off — a genuinely
         * buried window still needs toFront for clean screenshots).
         */
        private suspend fun bringWindowToFront(window: ComposeWindow, reason: String) {
            if (System.getProperty(PROP_NO_WINDOW_TO_FRONT)?.equals("true", ignoreCase = true) == true) {
                println(
                    "[JellyPlay][harness][awt-focus] t=+" +
                        (System.currentTimeMillis() - startedAtMs) +
                        "ms bringWindowToFront($reason): SKIPPED (noWindowToFront experiment)",
                )
            } else {
                runCatching {
                    window.toFront()
                    val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    if (kfm.focusedWindow === window && kfm.focusOwner != null) {
                        println(
                            "[JellyPlay][harness][awt-focus] t=+" +
                                (System.currentTimeMillis() - startedAtMs) +
                                "ms bringWindowToFront($reason): window already focused — " +
                                "requestFocus skipped (flap churn guard)",
                        )
                    } else {
                        println(
                            "[JellyPlay][harness][awt-focus] t=+" +
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
     * Wave 14E: how long each SPACE injection attempt waits for the sampled
     * isPlaying flip. The recorder samples on a ~500 ms cadence, so a real
     * toggle clears within ~1 s; 3 s leaves margin for a busy UI thread while
     * keeping the whole retry ladder (up to [SPACE_MAX_INJECTION_ATTEMPTS]
     * attempts + spacing) well inside the auto-exit deadline.
     */
    private const val SPACE_TOGGLE_PROBE_MS = 3_000L

    /**
     * Wave 14E: maximum SPACE injection attempts per OVERLAY_SPACE step. A
     * single unlucky focus flap must not fail the gate (the retry ladder);
     * a key that provably REACHED the handler still fails immediately.
     */
    private const val SPACE_MAX_INJECTION_ATTEMPTS = 3

    /** Wave 14E: spacing between SPACE injection attempts. */
    private const val SPACE_RETRY_SPACING_MS = 1_000L
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
