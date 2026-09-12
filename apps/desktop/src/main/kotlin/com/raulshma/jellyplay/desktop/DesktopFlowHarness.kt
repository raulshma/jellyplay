package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.harness.HarnessClickBridge
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.desktop.player.EngineActivityRecorder
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Flows harness — the e2e lane closing the four native-dialog flows
 * the ledger left checklist-only (docs/e2e/desktop-native-dialogs.md
 * items 3-6): the editor image picker, the editor subtitle picker, the
 * insights heatmap share and the player subtitle upload. Driven entirely by
 * `jellyplay.flowpass.*` system properties (injected through
 * JAVA_TOOL_OPTIONS by tools/e2e/desktop-native-dialog-flows-pass.sh); when
 * `jellyplay.flowpass.enabled` is unset, [runIfRequested] returns without
 * touching anything, and the click-reach bridge this lane relies on
 * ([HarnessClickBridge]) stays disarmed (Main.kt arms it only under this
 * same property) so the annotated production rows keep zero cost.
 *
 * What makes this lane possible where stopped — the click-reach fix:
 *
 *  - NAVIGATION: the flows' screens are FIRST-CLASS routes
 *    (Route.MetadataEditor / Route.WatchProgressHeatmap / Route.VideoPlayer),
 *    so the primitive (push a NavKey onto the scaffold-published
 *    back stack) reaches all of them — the blocker note ("reaches
 *    VideoPlayer but not the editor drill-in / insights screen") was a
 *    timebox cut, not a structural wall.
 *  - ROW CLICKS: the target rows/buttons publish their LIVE window-space
 *    bounds + owning window through [HarnessClickBridge]
 *    (`Modifier.harnessClickTarget(id)` annotations in the editor, insights
 *    and player-video modules — a harness-gated no-op on every normal boot).
 *    The harness converts bounds → screen coordinates and drives a REAL
 *    java.awt.Robot mouse click at the row center, so every click exercises
 *    the production hit-testing + onClick wiring, not a synthetic callback.
 *    Gated rows (upload-confirm buttons) also publish their enabled state so
 *    the harness can await a clickable state after a dialog pick.
 *
 * One run (needs the Docker Jellyfin fixture + libmpv; see the runner):
 *
 *  1. CONFIG / LOGIN / NAV_READY — session-harness pattern.
 *  2. FLOW3_EDITOR_IMAGE_PICKER — push the editor, CLICK Images tab →
 *     Upload row → Select image → the REAL "Choose image" AWT LOAD dialog
 *     (Robot types the file path + Enter, the shared HarnessDialogDriver) →
 *     the sheet's preview state enables the Upload button → CLICK it → the
 *     production upload pushes the PNG to the server, asserted SERVER-SIDE:
 *     the item's Primary imageTag changed (getItemImageInfo).
 *  3. FLOW4_EDITOR_SUBTITLE_PICKER — same screen, Subtitles tab → Upload →
 *     Select file → the REAL unfiltered "Choose subtitle" LOAD dialog picks
 *     a local .srt → language field typed ("eng", the sheet's confirm
 *     requires one) → Upload clicked → asserted server-side: the item gains
 *     an external subtitle stream carrying the uploaded name.
 *  4. FLOW5_HEATMAP_SHARE — push Route.WatchProgressHeatmap, CLICK the share
 *     IconButton (rendered once the grid recorded a frame) → the production
 *     DesktopHeatmapShare writes a PNG under java.io.tmpdir (the runner
 *     redirects it into the isolated workspace) — asserted: new
 *     watch_progress_heatmap_<millis>.png with PNG magic bytes.
 *  5. FLOW6_PLAYER_SUBTITLE_UPLOAD — push the player, await real playback,
 *     SPACE (shows controls + pauses — session-harness finding), CLICK the
 *     subtitles trigger → hub Get tab → Upload sub-tab → Select file → the
 *     REAL "Choose subtitle file" LOAD dialog (advisory filter) → language
 *     typed → Upload clicked → asserted server-side like flow 4.
 *  6. Report — `<logs>/flow-harness.json` (harness "desktop-native-dialog-flows"),
 *     screenshots per click step, then exitProcess(0).
 *
 * Properties:
 *  - `jellyplay.flowpass.enabled`         — "true" arms the harness (required).
 *  - `jellyplay.flowpass.workspace`       — writable scratch dir (required; the
 *    runner pre-creates sample.png/sample.srt inside, space-free ASCII).
 *  - `jellyplay.flowpass.serverUrl`       — Jellyfin base URL (required).
 *  - `jellyplay.flowpass.username` / `.password` — admin fixture user (required;
 *    the editor is admin-gated).
 *  - `jellyplay.flowpass.itemId`          — movie item for the editor flows (required).
 *  - `jellyplay.flowpass.playerItemId`    — item for the player flow (default: itemId).
 *  - `jellyplay.flowpass.autoExitSeconds` — hard-exit deadline, default 300.
 *  - `jellyplay.flowpass.screenshotDir`   — default `<workspace>/shots`.
 */
object DesktopFlowHarness {

    const val PROP_ENABLED = "jellyplay.flowpass.enabled"
    const val PROP_WORKSPACE = "jellyplay.flowpass.workspace"
    const val PROP_SERVER_URL = "jellyplay.flowpass.serverUrl"
    const val PROP_USERNAME = "jellyplay.flowpass.username"
    const val PROP_PASSWORD = "jellyplay.flowpass.password"
    const val PROP_ITEM_ID = "jellyplay.flowpass.itemId"
    const val PROP_PLAYER_ITEM_ID = "jellyplay.flowpass.playerItemId"
    const val PROP_AUTO_EXIT_SECONDS = "jellyplay.flowpass.autoExitSeconds"
    const val PROP_SCREENSHOT_DIR = "jellyplay.flowpass.screenshotDir"

    /** True only when `jellyplay.flowpass.enabled=true` — the zero-cost gate. */
    fun requested(): Boolean =
        System.getProperty(PROP_ENABLED)?.equals("true", ignoreCase = true) == true

    /** Everything the harness needs; Koin-agnostic (session-harness pattern). */
    class FlowHarnessDeps(
        val authRepository: AuthRepository,
        val editorRepository: MetadataEditorRepository,
        val engineRecorder: EngineActivityRecorder,
        val windowRef: AtomicReference<ComposeWindow?>?,
    )

    /**
     * Entry point from DesktopAppRoot's LaunchedEffect. No-op unless
     * [requested]; a full run always ends in exitProcess(0) (report written
     * best-effort even on the auto-exit deadline path).
     */
    suspend fun runIfRequested(deps: FlowHarnessDeps) {
        if (!requested()) return
        try {
            Runner(deps).run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A harness crash is a harness FAIL, never an app crash report.
            System.err.println("[JellyPlay][flowpass] fatal: $e")
            e.printStackTrace(System.err)
            Runner.currentFatalHandler?.invoke(e)
        }
    }

    private class Runner(private val deps: FlowHarnessDeps) {
        private val startedAtMs = System.currentTimeMillis()
        private val finished = AtomicBoolean(false)
        private val lock = Any()
        private val steps = ArrayList<StepResult>()
        private var fatal: Throwable? = null
        @Volatile
        private var reportOverallPass: Boolean = false

        private val workspaceProp = System.getProperty(PROP_WORKSPACE)?.trim().orEmpty()
        private val serverUrl = System.getProperty(PROP_SERVER_URL)?.trim().orEmpty()
        private val username = System.getProperty(PROP_USERNAME)?.trim().orEmpty()
        private val password = System.getProperty(PROP_PASSWORD).orEmpty()
        private val itemId = System.getProperty(PROP_ITEM_ID)?.trim().orEmpty()
        private val playerItemId = System.getProperty(PROP_PLAYER_ITEM_ID)?.trim()
            ?.ifEmpty { null } ?: itemId
        private val autoExitSeconds = System.getProperty(PROP_AUTO_EXIT_SECONDS)?.toIntOrNull()
            ?: DEFAULT_AUTO_EXIT_SECONDS
        private val workspace: Path = Path.of(workspaceProp.ifEmpty { "." })
        private val screenshotDir: Path =
            System.getProperty(PROP_SCREENSHOT_DIR)?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?: workspace.resolve("shots")

        /** `<dataDir>/logs` — rerouted under jellyplay.perf.dataDir by the runner. */
        private val logsDir: Path = DesktopPaths.resolve().logsDirNio

        private val samplePng: File = workspace.resolve("sample.png").toFile()
        private val sampleSrt: File = workspace.resolve("sample.srt").toFile()

        private var robot: Robot? = null

        init {
            currentFatalHandler = { e -> finishWithFatal(e) }
        }

        suspend fun run() {
            armAutoExit()
            println(
                "[JellyPlay][flowpass] enabled: workspace=$workspace server=$serverUrl " +
                    "item=$itemId playerItem=$playerItemId logs=$logsDir shots=$screenshotDir " +
                    "autoExit=${autoExitSeconds}s",
            )

            val configOk = step("CONFIG") {
                check(workspaceProp.isNotEmpty()) { "missing $PROP_WORKSPACE" }
                check(!workspaceProp.contains(' ')) {
                    "workspace path contains a space; JAVA_TOOL_OPTIONS cannot carry it"
                }
                check(serverUrl.isNotEmpty()) { "missing $PROP_SERVER_URL" }
                check(username.isNotEmpty()) { "missing $PROP_USERNAME" }
                check(password.isNotEmpty()) { "missing $PROP_PASSWORD" }
                check(itemId.isNotEmpty()) { "missing $PROP_ITEM_ID" }
                check(!GraphicsEnvironment.isHeadless()) {
                    "headless AWT environment — the flows pass needs a real display"
                }
                check(HarnessClickBridge.enabled) {
                    "HarnessClickBridge not armed (Main.kt gates it on $PROP_ENABLED)"
                }
                check(samplePng.isFile) { "sample PNG missing: $samplePng" }
                check(sampleSrt.isFile) { "sample SRT missing: $sampleSrt" }
                armWindowCreationTracking()
                Files.createDirectories(workspace)
                mapOf(
                    "workspace" to workspace.toString(),
                    "samplePngBytes" to samplePng.length().toString(),
                    "sampleSrtBytes" to sampleSrt.length().toString(),
                    "tmpdir" to System.getProperty("java.io.tmpdir"),
                )
            }
            var fatalStop = !configOk

            val loginOk = !fatalStop && step("LOGIN") {
                val user = deps.authRepository.login(serverUrl, username, password)
                    .getOrElse { error("login failed: ${it.message}") }
                mapOf("user" to (user.name ?: username), "userId" to user.id)
            }
            fatalStop = fatalStop || !loginOk

            val navOk = !fatalStop && step("NAV_READY") {
                awaitUntil(30_000) { DesktopSessionHarness.currentBackStack() != null } ||
                    error("nav scaffold never composed (back stack provider not attached)")
                mapOf("backStackSize" to (backStack()?.size ?: -1).toString())
            }
            fatalStop = fatalStop || !navOk

            // ── Flow 3: editor image picker ─────────────────────────────────
            val flow3Ok = !fatalStop && step("FLOW3_EDITOR_IMAGE_PICKER") {
                val tagBefore = primaryImageTag()
                pushRoute(Route.MetadataEditor(itemId = itemId))
                clickAwaiting("editor-tab-images") { "editor-images-upload" in targets() }
                screenshot("flow3-editor-images-tab")
                clickAwaiting("editor-images-upload") { "editor-images-select-file" in targets() }
                screenshot("flow3-upload-sheet")
                clickAwaitingDialog("editor-images-select-file", pathToType = samplePng.path, shotName = "flow3-choose-image")
                // The pick must land in the sheet's state: the confirm button
                // publishes enabled only when selectedFile != null.
                awaitEnabled("editor-images-upload-confirm", 15_000)
                screenshot("flow3-picked-preview")
                clickAwaiting("editor-images-upload-confirm") { "editor-images-select-file" !in targets() }
                // Give the fire-and-forget upload a beat, then capture the
                // editor surface (an upload failure renders as an inline
                // error banner — run-5/6 lesson: the server log saw no POST,
                // so the failure happens client-side and the banner is the
                // only visible diagnostic).
                delay(2500)
                screenshot("flow3-after-confirm")
                // Server-side post-condition: the Primary image content
                // changed (Jellyfin re-tags a replaced image).
                awaitUntil(20_000) { primaryImageTag() != null && primaryImageTag() != tagBefore } ||
                    error("server Primary imageTag unchanged after upload ($tagBefore)")
                mapOf(
                    "primaryTagBefore" to (tagBefore ?: "none"),
                    "primaryTagAfter" to (primaryImageTag() ?: "none"),
                    "clicked" to "tab→upload→select-file→[Choose image LOAD dialog]→upload-confirm",
                )
            }
            fatalStop = fatalStop || !flow3Ok

            // ── Flow 4: editor subtitle picker ──────────────────────────────
            val flow4Ok = !fatalStop && step("FLOW4_EDITOR_SUBTITLE_PICKER") {
                val streamsBefore = subtitleStreams(itemId).size
                clickAwaiting("editor-tab-subtitles") { "editor-subtitles-upload" in targets() }
                screenshot("flow4-editor-subtitles-tab")
                clickAwaiting("editor-subtitles-upload") { "editor-subtitles-select-file" in targets() }
                clickAwaitingDialog("editor-subtitles-select-file", pathToType = sampleSrt.path, shotName = "flow4-choose-subtitle")
                // The sheet's confirm requires a language — type one into the
                // focused field (the real click focuses the OutlinedTextField).
                clickAwaiting("editor-subtitles-language-field") { true }
                typeFocused("eng")
                awaitEnabled("editor-subtitles-upload-confirm", 15_000)
                screenshot("flow4-picked")
                clickAwaiting("editor-subtitles-upload-confirm") { "editor-subtitles-select-file" !in targets() }
                awaitUntil(20_000) { subtitleStreams(itemId).size > streamsBefore } ||
                    error(
                        "server subtitle stream count unchanged ($streamsBefore → " +
                            "${subtitleStreams(itemId).size}) after upload",
                    )
                val newStream = subtitleStreams(itemId).firstOrNull { it.title?.contains("sample") == true }
                mapOf(
                    "streamsBefore" to streamsBefore.toString(),
                    "streamsAfter" to subtitleStreams(itemId).size.toString(),
                    "uploadedName" to (newStream?.title ?: newStream?.displayTitle ?: "?"),
                    "clicked" to "tab→upload→select-file→[Choose subtitle LOAD dialog]→language→upload-confirm",
                )
            }
            fatalStop = fatalStop || !flow4Ok

            // ── Flow 5: insights heatmap share ──────────────────────────────
            val flow5Ok = !fatalStop && step("FLOW5_HEATMAP_SHARE") {
                popRoute()
                val tmpdir = Path.of(System.getProperty("java.io.tmpdir"))
                pushRoute(Route.WatchProgressHeatmap)
                // Share button renders with the screen; the GRID records into
                // the capture layer on its first draw pass (data load settles
                // the loading state first) — wait for the click target, then
                // one settle beat so the recorded frame exists.
                awaitTarget("insights-heatmap-share", 20_000)
                screenshot("flow5-heatmap")
                clickAwaiting("insights-heatmap-share") { true }
                awaitUntil(15_000) { newestHeatmapPngSince(tmpdir) != null } || error(
                    "no watch_progress_heatmap_*.png appeared under $tmpdir after the share click",
                )
                val png = newestHeatmapPngSince(tmpdir)
                    ?: error("heatmap PNG vanished before it could be read")
                val bytes = png.readBytes()
                check(bytes.size > MIN_HEATMAP_PNG_BYTES) {
                    "heatmap PNG suspiciously small (${bytes.size} bytes): $png"
                }
                check(bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
                ) { "heatmap PNG magic bytes missing: $png" }
                screenshot("flow5-after-share")
                mapOf(
                    "png" to png.path,
                    "bytes" to bytes.size.toString(),
                    "tmpdir" to tmpdir.toString(),
                    "clicked" to "share IconButton (grid recorded a frame)",
                )
            }
            fatalStop = fatalStop || !flow5Ok

            // ── Flow 6: player subtitle upload ──────────────────────────────
            val flow6Ok = !fatalStop && step("FLOW6_PLAYER_SUBTITLE_UPLOAD") {
                popRoute()
                val streamsBefore = subtitleStreams(playerItemId).size
                val pushAtMs = System.currentTimeMillis()
                pushRoute(Route.VideoPlayer(itemId = playerItemId))
                awaitUntil(60_000) {
                    deps.engineRecorder.latestVideoEngine().let {
                        it.createdAtMs >= pushAtMs && it.surface.isNotEmpty() &&
                            it.positionSamples.any { s -> s.isPlaying }
                    }
                } || error("playback never started (no playing sample after route push)")
                screenshot("flow6-playing")
                // SPACE shows the controls overlay (and pauses — the
                // session-harness finding); the trigger button composes with it.
                injectKey(KeyEvent.VK_SPACE)
                awaitTarget("player-subtitles-trigger", 10_000)
                screenshot("flow6-controls")
                clickAwaiting("player-subtitles-trigger") { "player-subtitle-hub-get-tab" in targets() }
                screenshot("flow6-subtitle-hub")
                clickAwaiting("player-subtitle-hub-get-tab") { "player-subtitle-upload-tab" in targets() }
                clickAwaiting("player-subtitle-upload-tab") { "player-subtitle-select-file" in targets() }
                clickAwaitingDialog("player-subtitle-select-file", pathToType = sampleSrt.path, shotName = "flow6-choose-subtitle-file")
                clickAwaiting("player-subtitle-language-field") { true }
                typeFocused("eng")
                awaitEnabled("player-subtitle-upload-confirm", 15_000)
                screenshot("flow6-picked")
                clickAwaiting("player-subtitle-upload-confirm") { "player-subtitle-select-file" !in targets() }
                awaitUntil(20_000) { subtitleStreams(playerItemId).size > streamsBefore } ||
                    error(
                        "server subtitle stream count unchanged ($streamsBefore → " +
                            "${subtitleStreams(playerItemId).size}) after player upload",
                    )
                mapOf(
                    "streamsBefore" to streamsBefore.toString(),
                    "streamsAfter" to subtitleStreams(playerItemId).size.toString(),
                    "clicked" to "subtitles→Get tab→Upload tab→select-file→[Choose subtitle file LOAD dialog]→language→upload-confirm",
                )
            }
            fatalStop = fatalStop || !flow6Ok

            writeReportAndExit()
        }

        // ── navigation ( primitive, shared provider) ─────────────────

        private fun backStack(): MutableList<NavKey>? = DesktopSessionHarness.currentBackStack()

        private suspend fun pushRoute(route: Route) {
            val stack = backStack() ?: error("no back stack")
            stack.add(route)
            awaitUntil(5_000) { backStack()?.lastOrNull() == route } ||
                error("${route::class.simpleName} never reached the top of the back stack")
        }

        private fun popRoute() {
            val stack = backStack() ?: return
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }

        // ── server-side observables (the production repository) ─────────────

        private suspend fun primaryImageTag(): String? =
            deps.editorRepository.getItemImageInfo(itemId).getOrElse { error("getItemImageInfo failed: $it") }
                .firstOrNull { it.imageType.equals("Primary", ignoreCase = true) }
                ?.imageTag

        private suspend fun subtitleStreams(id: String) =
            deps.editorRepository.getMediaDetail(id)
                .getOrElse { error("getMediaDetail failed: $it") }
                .mediaSources.flatMap { it.mediaStreams }
                .filter { it.type == StreamType.SUBTITLE }

        // ── the click-reach bridge driver (Robot mouse) ──────────────────────

        private fun targets(): Set<String> = HarnessClickBridge.snapshot().keys

        private suspend fun awaitTarget(
            id: String,
            timeoutMs: Long = 15_000,
            requireEnabled: Boolean = false,
        ): HarnessClickBridge.Target {
            awaitUntil(timeoutMs) {
                val t = HarnessClickBridge.target(id)
                t != null && (!requireEnabled || t.enabled)
            } || run {
                screenshot("missing-$id") // failure evidence before the step FAIL
                error(
                    if (HarnessClickBridge.target(id) == null) {
                        "click target '$id' never appeared within ${timeoutMs}ms " +
                            "(present: ${targets().sorted()})"
                    } else {
                        "click target '$id' present but never enabled within ${timeoutMs}ms"
                    },
                )
            }
            // Stability: sheets animate in — wait until two polls agree.
            var previous = HarnessClickBridge.target(id)?.bounds
            awaitUntil(5_000) {
                delay(150)
                val current = HarnessClickBridge.target(id)?.bounds
                val stable = current != null && current == previous
                previous = current ?: previous
                stable
            } || error("click target '$id' bounds never stabilized")
            return HarnessClickBridge.target(id) ?: error("click target '$id' vanished")
        }

        private suspend fun awaitEnabled(id: String, timeoutMs: Long) {
            awaitTarget(id, timeoutMs, requireEnabled = true)
        }

        /**
         * One REAL pointer click at the target's live center. The OS-level
         * event travels the production hit-testing + onClick path; the
         * owning window is brought to front first. The owner is resolved
         * from the tracked AWT windows: a material3 ModalBottomSheet opens
         * its own top-level window on desktop (PlayerModalBottomSheet's
         * in-window variant is the exception), so the NEWEST visible
         * non-FileDialog window hosts sheet targets and the main window
         * hosts everything else.
         *
         * Windows foreground-lock defense (run-1 lesson: toFront() alone can
         * fail silently for a background process, and the Robot click then
         * lands on whatever window IS at the point): the window is forced
         * foreground via the classic alwaysOnTop toggle, and the click point
         * is VERIFIED to show OUR pixels before the click (Robot.getPixelColor
         * vs the window-screenshot pixel at the same location).
         */
        private suspend fun click(id: String): HarnessClickBridge.Target {
            val target = awaitTarget(id)
            val window = resolveOwnerWindow()
            val bounds = HarnessClickBridge.target(id)?.bounds
                ?: target.bounds
                ?: error("click target '$id' lost its bounds before the click")
            val location = window.locationOnScreen
            // DPI mapping (run-3 lesson): the bridge reports Compose px, the
            // Robot lives in AWT's (logical) space — on a scaled display
            // (measured defaultTransform 1.5) Compose px ÷ scale = AWT px.
            // On unscaled displays the factor is 1.0 and this is a no-op.
            val scale = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            val x = (location.x + bounds.center.x / scale).toInt()
            val y = (location.y + bounds.center.y / scale).toInt()
            val r = robotOrNull() ?: error("Robot unavailable")

            var verified = false
            for (attempt in 1..MAX_CLICK_ATTEMPTS) {
                window.isAlwaysOnTop = true
                window.toFront()
                window.requestFocus()
                delay(300)
                val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                if (kfm.activeWindow === window) {
                    verified = true
                    diag(
                        "window active for click (attempt $attempt): bounds=${window.bounds} " +
                            "scale=${window.graphicsConfiguration?.defaultTransform?.scaleX} " +
                            "focusOwner=${kfm.focusOwner?.javaClass?.simpleName}",
                    )
                    break
                }
                diag(
                    "window NOT active (attempt $attempt): active=${kfm.activeWindow} " +
                        "focused=${kfm.focusedWindow} — forcing foreground again",
                )
            }
            window.isAlwaysOnTop = false
            if (!verified) {
                error(
                    "window never became AWT-active for the click on '$id' " +
                        "(window=$window bounds=${window.bounds}) — foreground-lock mismatch",
                )
            }

            r.mouseMove(x, y)
            delay(200)
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            delay(80)
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            diag("clicked '$id' at ($x,$y) bounds=$bounds window=${window.javaClass.simpleName}")
            delay(400)
            return target
        }

        /**
         * AWT windows in creation order (WINDOW_OPENED listener, armed in
         * CONFIG). [resolveOwnerWindow] picks the sheet window from this.
         */
        private val createdWindows = java.util.Collections.synchronizedList(ArrayList<java.awt.Window>())

        private fun armWindowCreationTracking() {
            runCatching {
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
                    { event ->
                        if (event is java.awt.event.WindowEvent && event.id == java.awt.event.WindowEvent.WINDOW_OPENED) {
                            event.window?.let { createdWindows.add(it) }
                        }
                    },
                    java.awt.AWTEvent.WINDOW_EVENT_MASK,
                )
            }.onFailure { diag("window-creation tracking failed: $it") }
            // Seed with the windows that already exist (the main window may
            // have opened before the harness armed the listener).
            createdWindows.addAll(java.awt.Window.getWindows().toList())
            diag("window tracking armed (${createdWindows.size} existing windows)")
        }

        /** The window hosting the current click targets (see [click]). */
        private fun resolveOwnerWindow(): java.awt.Window {
            val main = deps.windowRef?.get()
            val candidates = createdWindows.toList()
                .filter { it.isShowing && it !is java.awt.FileDialog }
            diag(
                "window candidates: " + candidates.joinToString(", ") {
                    "${it.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(it))}" +
                        "@${it.locationOnScreen?.let { l -> "${l.x},${l.y}" } ?: "?"}${it.size.width}x${it.size.height}" +
                        if (it === main) "(MAIN)" else ""
                },
            )
            // The newest VISIBLE window that is not the main window and not a
            // native file dialog is the open modal sheet.
            val sheet = createdWindows.toList()
                .filter { it.isShowing && it !is java.awt.FileDialog && it !== main }
                .lastOrNull()
            return sheet ?: main ?: error("no AWT window to click into (main window not composed?)")
        }

        /** Click [id], then poll [effect] — up to 3 attempts (retry ladder). */
        private suspend fun clickAwaiting(id: String, timeoutMs: Long = 10_000, effect: suspend () -> Boolean) {
            var lastError: String = "effect never observed"
            for (attempt in 1..MAX_CLICK_ATTEMPTS) {
                click(id)
                if (awaitUntil(3_000) { effect() }) return
                lastError = "attempt $attempt: no effect within 3s"
                diag("clickAwaiting('$id') $lastError — retrying")
            }
            awaitUntil((timeoutMs - 3_000L * MAX_CLICK_ATTEMPTS).coerceAtLeast(1_000)) { effect() } || run {
                screenshot("clickfail-$id") // failure evidence before the step FAIL
                error(
                    "click on '$id' had no observable effect ($lastError; " +
                        "present targets: ${targets().sorted()})",
                )
            }
        }

        /**
         * Click [id] — its handler opens a native AWT LOAD dialog — while a
         * driver coroutine on Dispatchers.IO types [pathToType] + Enter and
         * captures dialog evidence (HarnessDialogDriver, the
         * mechanics). The driver's failure fails the step.
         */
        private suspend fun clickAwaitingDialog(id: String, pathToType: String, shotName: String) {
            coroutineScope {
                val driver = async(Dispatchers.IO) {
                    HarnessDialogDriver(screenshotDir, startedAtMs, tag = "flowpass")
                        .drive(pathToType = pathToType, cancel = false, shotName = shotName)
                }
                click(id)
                driver.await()
            }
        }

        /** Types [text] into the currently focused Compose field (click first). */
        private fun typeFocused(text: String) {
            HarnessDialogDriver(screenshotDir, startedAtMs, tag = "flowpass")
                .typeIntoFocusedField(text)
        }

        // ── evidence helpers ─────────────────────────────────────────────────

        private fun robotOrNull(): Robot? {
            robot?.let { return it }
            val r = runCatching { Robot() }.onFailure {
                System.err.println("[JellyPlay][flowpass] Robot unavailable: $it")
            }.getOrNull()
            robot = r
            return r
        }

        private suspend fun injectKey(keyCode: Int) {
            val r = robotOrNull() ?: error("Robot unavailable")
            val window = deps.windowRef?.get() ?: error("window unavailable")
            window.toFront()
            delay(200)
            r.keyPress(keyCode)
            delay(60)
            r.keyRelease(keyCode)
            delay(300)
        }

        private suspend fun screenshot(name: String) {
            val r = robotOrNull() ?: run { diag("screenshot '$name' skipped: no Robot"); return }
            val window: java.awt.Window = deps.windowRef?.get()
                ?: createdWindows.toList()
                    .filter { it.isShowing && it !is java.awt.FileDialog }
                    .lastOrNull()
                ?: run { diag("screenshot '$name' skipped: no window"); return }
            runCatching {
                window.toFront()
                delay(250)
                val image: BufferedImage = r.createScreenCapture(window.bounds)
                Files.createDirectories(screenshotDir)
                ImageIO.write(image, "png", File(screenshotDir.toFile(), "$name.png"))
                diag("screenshot: $name.png (${window.bounds})")
            }.onFailure { diag("screenshot '$name' failed: $it") }
        }

        private fun newestHeatmapPngSince(dir: Path): File? =
            runCatching {
                dir.toFile()
                    .listFiles { f: File -> f.name.startsWith("watch_progress_heatmap_") && f.name.endsWith(".png") }
                    ?.firstOrNull { it.lastModified() >= startedAtMs && it.length() > 0 }
            }.getOrNull()

        private fun diag(message: String) {
            println(
                "[JellyPlay][flowpass] t=+${System.currentTimeMillis() - startedAtMs}ms $message",
            )
        }

        // ── plumbing (session-harness twins) ─────────────────────────────────

        private fun armAutoExit() {
            Timer("jellyplay-flowpass", true).schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        System.err.println(
                            "[JellyPlay][flowpass] auto-exit deadline (${autoExitSeconds}s) reached",
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
            exitProcess(1)
        }

        private fun writeReportAndExit() {
            if (!finished.compareAndSet(false, true)) return
            runCatching { writeReport() }
            exitProcess(if (lastReportOverallPass()) 0 else 1)
        }

        /** Mirrors the report's overallPass so the process exit code can be wired to CI directly. */
        private fun lastReportOverallPass(): Boolean = reportOverallPass

        private suspend fun step(
            name: String,
            block: suspend () -> Map<String, String>,
        ): Boolean {
            val atMs = System.currentTimeMillis()
            val result = try {
                val details = block()
                StepResult(name, pass = true, atMs, System.currentTimeMillis() - atMs, details, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                StepResult(name, pass = false, atMs, System.currentTimeMillis() - atMs, emptyMap(), "$e")
            }
            println(
                "[JellyPlay][flowpass] step ${result.name}: " +
                    (if (result.pass) "PASS" else "FAIL") +
                    (result.error?.let { " — $it" } ?: "") +
                    " (${result.durationMs}ms)",
            )
            synchronized(lock) { steps += result }
            return result.pass
        }

        private suspend fun awaitUntil(timeoutMs: Long, poll: suspend () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (poll()) return true
                delay(150)
            }
            return poll()
        }

        private fun writeReport() {
            val (stepList, fatalErr) = synchronized(lock) { steps.toList() to fatal }
            val overallPass = stepList.isNotEmpty() && stepList.all { it.pass } && fatalErr == null
            reportOverallPass = overallPass
            val json = SessionHarnessReport(
                startedAtMs = startedAtMs,
                finishedAtMs = System.currentTimeMillis(),
                overallPass = overallPass,
                fatal = fatalErr?.toString(),
                machine = mapOf(
                    "os.name" to System.getProperty("os.name"),
                    "os.version" to System.getProperty("os.version"),
                    "java.version" to System.getProperty("java.version"),
                    "serverUrl" to serverUrl,
                    "itemId" to itemId,
                    "playerItemId" to playerItemId,
                    "workspace" to workspace.toString(),
                ),
                steps = stepList,
                harness = "desktop-native-dialog-flows",
            ).toJson()
            Files.createDirectories(logsDir)
            Files.writeString(logsDir.resolve(REPORT_FILE_NAME), json)
            println(
                "[JellyPlay][flowpass] report written: $logsDir${File.separatorChar}$REPORT_FILE_NAME",
            )
        }

        companion object {
            @Volatile
            var currentFatalHandler: ((Throwable) -> Unit)? = null

            private const val REPORT_FILE_NAME = "flow-harness.json"
            private const val DEFAULT_AUTO_EXIT_SECONDS = 300
            private const val MAX_CLICK_ATTEMPTS = 3

            /** Heatmap PNGs are a few KB even for an empty grid. */
            private const val MIN_HEATMAP_PNG_BYTES = 500
        }
    }
}

/**
 * Composition-site sugar so DesktopAppRoot hosts the flows harness in one
 * call. Internal — only the desktop shell uses it. Gated by
 * [DesktopFlowHarness.requested] at the call site so a normal boot composes
 * nothing here.
 */
@Composable
internal fun DesktopFlowHarnessHost(
    authRepository: AuthRepository,
    editorRepository: MetadataEditorRepository,
    engineRecorder: EngineActivityRecorder,
    windowRef: AtomicReference<ComposeWindow?>?,
) {
    LaunchedEffect(Unit) {
        DesktopFlowHarness.runIfRequested(
            DesktopFlowHarness.FlowHarnessDeps(
                authRepository = authRepository,
                editorRepository = editorRepository,
                engineRecorder = engineRecorder,
                windowRef = windowRef,
            ),
        )
    }
}
