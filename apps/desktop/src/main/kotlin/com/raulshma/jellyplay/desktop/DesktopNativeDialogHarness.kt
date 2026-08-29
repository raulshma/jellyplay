package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile
import com.raulshma.jellyplay.feature.settings.SettingsBackupIo
import com.raulshma.jellyplay.feature.settings.SettingsViewModel
import java.awt.FileDialog
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wave 22F native-dialog harness — the in-app, in-window verification of the
 * AWT [FileDialog] flows that wave 20 landed "manually-verified-only" (audit
 * finding F9: the item then silently fell off wave 21's remaining-surface
 * ledger). Driven entirely by `jellyplay.dialogpass.*` system properties
 * (injected through JAVA_TOOL_OPTIONS by tools/e2e/desktop-native-dialog-pass.sh,
 * the wave-13B [DesktopSessionHarness] pattern); when
 * `jellyplay.dialogpass.enabled` is unset, [runIfRequested] returns without
 * touching anything.
 *
 * What one enabled run proves, with NO server and NO navigation (the settings
 * backup flow is local-prefs-only — no sign-in is needed for the mechanics
 * under test):
 *
 *  1. DIALOG_EXPORT_SAVE — the REAL shared dialog helper [pickAwtFile] (the
 *     one `DesktopBackupFilePicker.launchCreateExport` calls, SAVE mode with
 *     the production "jellyplay-settings.json" prefill and the production
 *     "Export settings" title) is invoked ON THE EDT — Compose desktop's UI
 *     thread, exactly the production shape where the modal dialog blocks the
 *     click handler. A Robot driver thread (Dispatchers.IO) waits for the
 *     native dialog window, brings it to front, selects all in the filename
 *     box (Ctrl+A, clearing the prefill), types the full absolute path of a
 *     pre-created workspace location and presses Enter.
 *  2. EXPORT_VM_WRITES — the picked file's `file:` URI goes through the
 *     production callback body ([SettingsViewModel.exportSettings]); the step
 *     passes only when the VM reports "Settings exported successfully" AND
 *     the file exists on disk AND its JSON carries the v2 envelope
 *     (schemaVersion == [SettingsBackup.CURRENT_SCHEMA_VERSION], non-empty
 *     slices). The file is the observable, asserted from outside the dialog
 *     machinery.
 *  3. DIALOG_IMPORT_LOAD — the LOAD-mode dialog, same Robot mechanism,
 *     picking the file the export just wrote (the production round trip).
 *  4. IMPORT_VM_STAGE_CONFIRM — [SettingsViewModel.importSettings] stages a
 *     v2 pending import (not legacy, no version mismatch) and
 *     [SettingsViewModel.confirmImport] lands "Settings imported
 *     successfully" — the import half reads the same file back through the
 *     same dialog-produced URI.
 *  5. DIALOG_CANCEL_ESC — a LOAD dialog dismissed with ESC (native cancel):
 *     [pickAwtFile] returns null, so the production picker fires no callback
 *     and the VM's backup-restore state is untouched — the live twin of the
 *     `pickedAwtFile` cancel-shape unit test (wave 21D covered the mapping in
 *     isolation; this covers the real modal dismissal).
 *  6. Report — `<logs>/dialog-harness.json` (same step-ledger shape as the
 *     session harness, `harness:"desktop-native-dialog"`), a screenshot of
 *     the native dialog per step under the screenshot dir, then
 *     exitProcess(0). The auto-exit timer still collects a (partial, fatal)
 *     report if anything wedges, and the driver's safety net presses ESC on a
 *     dialog that outlives its attempts so the EDT blocked inside the modal
 *     loop resumes and the step FAILS instead of hanging until the deadline.
 *
 * Deliberate boundary (honest cut, not a gap): the harness calls [pickAwtFile]
 * + the production callback bodies directly instead of pointer-clicking the
 * BackupSettingsScreen rows. Robot mouse-pixel clicks into a scrollable
 * LazyColumn are the one ingredient the wave-13/14 lessons say not to bet on
 * (unstable coordinates, focus thieves, wrong-row risk — the third row is
 * Factory Reset), and no compose-test machinery exists inside a production
 * app to resolve a row's bounds. The row→picker→VM wiring those clicks cover
 * stays on the manual checklist in docs/e2e/desktop-native-dialogs.md;
 * everything behind the click (the modal SAVE/LOAD/cancel dialog mechanics,
 * the `file:` URI delivery, the JDK-stream IO and the VM round trip) is what
 * this harness gates.
 *
 * Properties:
 *  - `jellyplay.dialogpass.enabled`         — "true" arms the harness (required).
 *  - `jellyplay.dialogpass.workspace`       — writable scratch dir for the
 *    round-trip files (required; the runner script pre-creates it, space-free
 *    because JAVA_TOOL_OPTIONS splits on whitespace).
 *  - `jellyplay.dialogpass.autoExitSeconds` — hard-exit deadline, default 90.
 *  - `jellyplay.dialogpass.screenshotDir`   — default `<workspace>/shots`.
 */
object DesktopNativeDialogHarness {

    const val PROP_ENABLED = "jellyplay.dialogpass.enabled"
    const val PROP_WORKSPACE = "jellyplay.dialogpass.workspace"
    const val PROP_AUTO_EXIT_SECONDS = "jellyplay.dialogpass.autoExitSeconds"
    const val PROP_SCREENSHOT_DIR = "jellyplay.dialogpass.screenshotDir"

    /** The production SAVE prefill (DesktopBackupFilePicker.launchCreateExport). */
    private const val PRODUCTION_PREFILL = "jellyplay-settings.json"

    private const val DEFAULT_AUTO_EXIT_SECONDS = 90

    /** True only when `jellyplay.dialogpass.enabled=true` — the zero-cost gate. */
    fun requested(): Boolean =
        System.getProperty(PROP_ENABLED)?.equals("true", ignoreCase = true) == true

    /**
     * Everything the harness needs; Koin-agnostic like
     * [DesktopSessionHarness.SessionHarnessDeps]. The VM is constructed by the
     * host (see [DesktopNativeDialogHarnessHost]) from the same Koin singles
     * the settings module's viewModel definition would inject — a harness-owned
     * instance, since no settings screen composes in this mode.
     */
    class DialogPassDeps(val settingsViewModel: SettingsViewModel)

    /**
     * Entry point from the host's LaunchedEffect (Main dispatcher = the AWT
     * EDT — the dialog calls rely on this). No-op unless [requested]; a full
     * run always ends in exitProcess(0) (report written best-effort on the
     * fatal/deadline path, [DesktopSessionHarness] pattern).
     */
    suspend fun runIfRequested(deps: DialogPassDeps) {
        if (!requested()) return
        try {
            Runner(deps).run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A harness crash is a harness FAIL, never an app crash report.
            System.err.println("[JellyPlay][dialogpass] fatal: $e")
            e.printStackTrace(System.err)
            Runner.currentFatalHandler?.invoke(e)
        }
    }

    private class Runner(private val deps: DialogPassDeps) {
        private val startedAtMs = System.currentTimeMillis()
        private val finished = AtomicBoolean(false)
        private val lock = Any()
        private val steps = ArrayList<StepResult>()
        private var fatal: Throwable? = null

        private val workspaceProp = System.getProperty(PROP_WORKSPACE)?.trim().orEmpty()
        private val autoExitSeconds = System.getProperty(PROP_AUTO_EXIT_SECONDS)?.toIntOrNull()
            ?: DEFAULT_AUTO_EXIT_SECONDS
        private val workspace: Path = Path.of(workspaceProp.ifEmpty { "." })
        private val screenshotDir: Path =
            System.getProperty(PROP_SCREENSHOT_DIR)?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?: workspace.resolve("shots")

        /** `<dataDir>/logs` — rerouted under jellyplay.perf.dataDir by the runner script. */
        private val logsDir: Path = DesktopPaths.resolve().logsDirNio

        private val vm: SettingsViewModel get() = deps.settingsViewModel

        init {
            currentFatalHandler = { e -> finishWithFatal(e) }
        }

        suspend fun run() {
            armAutoExit()
            println(
                "[JellyPlay][dialogpass] enabled: workspace=$workspace shots=$screenshotDir " +
                    "logs=$logsDir autoExit=${autoExitSeconds}s",
            )

            val exportTargetFile = workspace.resolve(PRODUCTION_PREFILL).toFile()

            val configOk = step("CONFIG") {
                check(workspaceProp.isNotEmpty()) { "missing $PROP_WORKSPACE" }
                check(!workspaceProp.contains(' ')) {
                    "workspace path contains a space; JAVA_TOOL_OPTIONS cannot carry it"
                }
                check(!java.awt.GraphicsEnvironment.isHeadless()) {
                    "headless AWT environment — the native-dialog pass needs a real display"
                }
                Files.createDirectories(workspace)
                check(Files.isDirectory(workspace)) { "workspace not creatable: $workspace" }
                check(!exportTargetFile.exists()) {
                    "export target already exists (stale workspace?): $exportTargetFile"
                }
                mapOf(
                    "workspace" to workspace.toString(),
                    "exportTarget" to exportTargetFile.toString(),
                    "prefill" to PRODUCTION_PREFILL,
                )
            }
            var fatalStop = !configOk

            // ── 1. SAVE dialog: Robot types the absolute export path + Enter ──
            val dialogSaveOk = !fatalStop && step("DIALOG_EXPORT_SAVE") {
                val picked = driveAndPick(
                    title = "Export settings",
                    save = true,
                    prefill = PRODUCTION_PREFILL,
                    pathToType = exportTargetFile.toString(),
                    cancel = false,
                    shotName = "dialog-export-save",
                )
                check(picked != null) { "SAVE dialog dismissed without a pick (path was typed)" }
                check(canonical(picked) == canonical(exportTargetFile)) {
                    "picked file $picked != typed path $exportTargetFile"
                }
                mapOf(
                    "picked" to (picked?.path ?: "null"),
                    "mode" to "SAVE",
                    "prefillClearedBy" to "CTRL_A + full absolute path typed",
                )
            }
            fatalStop = fatalStop || !dialogSaveOk

            // ── 2. the VM writes the file through the production callback ──
            val exportVmOk = !fatalStop && step("EXPORT_VM_WRITES") {
                val uri = exportTargetFile.toURI().toString()
                vm.exportSettings(uri)
                check(awaitUntil(15_000) { vm.backupRestoreStatus != null }) {
                    "backupRestoreStatus never settled after exportSettings"
                }
                check(vm.backupRestoreStatus == "Settings exported successfully") {
                    "unexpected export status: '${vm.backupRestoreStatus}'"
                }
                check(exportTargetFile.isFile) { "export file missing: $exportTargetFile" }
                val bytes = exportTargetFile.readBytes()
                check(bytes.isNotEmpty()) { "export file empty: $exportTargetFile" }
                val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                check(schemaVersion == SettingsBackup.CURRENT_SCHEMA_VERSION) {
                    "exported JSON schemaVersion=$schemaVersion, " +
                        "expected ${SettingsBackup.CURRENT_SCHEMA_VERSION}"
                }
                val slices = root["slices"]?.jsonObject
                check(!slices.isNullOrEmpty()) { "exported JSON has no non-empty slices" }
                mapOf(
                    "uri" to uri,
                    "vmStatus" to vm.backupRestoreStatus.orEmpty(),
                    "bytes" to bytes.size.toString(),
                    "schemaVersion" to schemaVersion.toString(),
                    "sliceKeys" to slices.keys.sorted().joinToString(","),
                )
            }
            fatalStop = fatalStop || !exportVmOk

            // ── 3. LOAD dialog: Robot picks the file the export wrote ──
            val dialogLoadOk = !fatalStop && step("DIALOG_IMPORT_LOAD") {
                val picked = driveAndPick(
                    title = "Import settings",
                    save = false,
                    prefill = null,
                    pathToType = exportTargetFile.toString(),
                    cancel = false,
                    shotName = "dialog-import-load",
                )
                check(picked != null) { "LOAD dialog dismissed without a pick (path was typed)" }
                check(canonical(picked) == canonical(exportTargetFile)) {
                    "picked file $picked != exported file $exportTargetFile"
                }
                mapOf(
                    "picked" to (picked?.path ?: "null"),
                    "mode" to "LOAD",
                )
            }
            fatalStop = fatalStop || !dialogLoadOk

            // ── 4. staging + confirm through the production VM ──
            val importVmOk = !fatalStop && step("IMPORT_VM_STAGE_CONFIRM") {
                val uri = exportTargetFile.toURI().toString()
                vm.importSettings(uri)
                check(awaitUntil(15_000) { vm.pendingImport != null }) {
                    "pendingImport never staged (status='${vm.backupRestoreStatus}')"
                }
                val pending = vm.pendingImport!!
                check(pending.uri == uri) { "staged uri '${pending.uri}' != dialog-picked '$uri'" }
                check(!pending.isLegacy) { "exported v2 backup classified legacy" }
                check(!pending.versionMismatch) {
                    "version mismatch on our own fresh export (schema ${pending.schemaVersion})"
                }
                vm.confirmImport(restoreSecuritySensitive = false)
                check(awaitUntil(15_000) { vm.pendingImport == null }) {
                    "pendingImport never consumed by confirmImport"
                }
                check(vm.backupRestoreStatus == "Settings imported successfully") {
                    "unexpected import status: '${vm.backupRestoreStatus}'"
                }
                mapOf(
                    "stagedSchemaVersion" to pending.schemaVersion.toString(),
                    "hasSecuritySensitive" to pending.hasSecuritySensitive.toString(),
                    "vmStatus" to vm.backupRestoreStatus.orEmpty(),
                )
            }
            fatalStop = fatalStop || !importVmOk

            // ── 5. the native cancel shape, live (ESC leaves VM untouched) ──
            if (!fatalStop) {
                step("DIALOG_CANCEL_ESC") {
                    val statusBefore = vm.backupRestoreStatus
                    val pendingBefore = vm.pendingImport
                    val picked = driveAndPick(
                        title = "Import settings",
                        save = false,
                        prefill = null,
                        pathToType = null,
                        cancel = true,
                        shotName = "dialog-cancel-esc",
                    )
                    check(picked == null) { "ESC left a pick behind: ${picked?.path}" }
                    check(vm.backupRestoreStatus == statusBefore && vm.pendingImport == pendingBefore) {
                        "VM state moved on a cancelled dialog (callback fired without a pick?)"
                    }
                    mapOf(
                        "picked" to "null",
                        "vmStateUnchanged" to "true",
                        "statusBefore" to (statusBefore ?: "null"),
                    )
                }
            }

            writeReportAndExit()
        }

        // ── the dialog drive: Robot driver thread + EDT-blocking pick ────────

        /**
         * Runs the production pick on the EDT (blocking the UI thread inside
         * the modal dialog, exactly like a real click handler) while a driver
         * coroutine on Dispatchers.IO finds the native dialog window, focuses
         * it and types. Returns what [pickAwtFile] answered. The driver's
         * failure (dialog never appeared, attempts exhausted) propagates from
         * the join below — a step FAIL, never a process crash.
         */
        private suspend fun driveAndPick(
            title: String,
            save: Boolean,
            prefill: String?,
            pathToType: String?,
            cancel: Boolean,
            shotName: String,
        ): File? = coroutineScope {
            val driver = async(Dispatchers.IO) {
                DialogDriver(screenshotDir, startedAtMs).drive(
                    pathToType = pathToType,
                    cancel = cancel,
                    shotName = shotName,
                )
            }
            val picked = withContext(Dispatchers.Main) {
                pickAwtFile(title = title, save = save, prefillFileName = prefill)
            }
            // The EDT call returning first is the normal order; awaiting the
            // driver here still surfaces its failure for THIS step.
            driver.await()
            picked
        }

        private fun canonical(f: File?): String? =
            runCatching { f?.canonicalFile?.path }.getOrNull()

        /**
         * The Robot half. Runs OFF the EDT (the EDT is inside the modal
         * loop). Waits for a showing [FileDialog], brings it to front, and
         * either types the full absolute path + Enter (cancel=false) or
         * presses ESC (cancel=true). Bounded attempts per the wave-14 retry
         * lesson; a dialog that outlives every attempt gets a final ESC so
         * the blocked EDT resumes and the step fails instead of wedging until
         * auto-exit. Every action prints a t=+ms-stamped diag line (wave-14
         * lesson: timestamps on diag lines).
         */
        private class DialogDriver(
            private val screenshotDir: Path,
            private val startedAtMs: Long,
        ) {
            private val robot: Robot? = runCatching { Robot() }.onFailure {
                diag("Robot unavailable: $it")
            }.getOrNull()

            suspend fun drive(pathToType: String?, cancel: Boolean, shotName: String) {
                val r = robot ?: error("Robot unavailable — cannot drive the native dialog")
                val dialog = awaitFileDialog(20_000)
                    ?: error("no showing FileDialog appeared within 20s")
                diag("dialog appeared: title='${dialog.title}' bounds=${dialog.bounds}")
                delay(500) // native settle: focus lands after the peer shows

                for (attempt in 1..MAX_ATTEMPTS) {
                    focusDialog(dialog)
                    if (!dialog.isShowing) return
                    capture(dialog, "$shotName-attempt$attempt")
                    if (cancel) {
                        diag("attempt $attempt: pressing ESC (cancel run)")
                        tap(r, KeyEvent.VK_ESCAPE)
                    } else {
                        val path = pathToType.orEmpty()
                        diag("attempt $attempt: Ctrl+A + typing ${path.length} chars + Enter")
                        selectAll(r)
                        for (c in path) typeChar(r, c)
                        tap(r, KeyEvent.VK_ENTER)
                    }
                    if (awaitHidden(dialog, 5_000)) {
                        diag("dialog dismissed after attempt $attempt")
                        return
                    }
                    diag("dialog still up 5s after attempt $attempt")
                }
                // Safety net: never leave the EDT wedged in the modal loop.
                diag("dialog survived $MAX_ATTEMPTS attempts — pressing ESC to unblock")
                tap(r, KeyEvent.VK_ESCAPE)
                awaitHidden(dialog, 5_000)
                error("dialog did not dismiss after $MAX_ATTEMPTS attempts")
            }

            private suspend fun awaitFileDialog(timeoutMs: Long): FileDialog? {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val d = Window.getWindows()
                        .filterIsInstance<FileDialog>()
                        .firstOrNull { it.isShowing }
                    if (d != null) return d
                    delay(100)
                }
                return null
            }

            private fun focusDialog(dialog: FileDialog) {
                runCatching {
                    SwingUtilities.invokeAndWait { dialog.toFront() }
                }.onFailure { diag("toFront via invokeAndWait failed: $it") }
                val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                diag(
                    "after toFront: focusedWindow=" +
                        "${kfm.focusedWindow?.javaClass?.simpleName} " +
                        "targetIsFocused=${kfm.focusedWindow === dialog}",
                )
            }

            private suspend fun awaitHidden(dialog: FileDialog, timeoutMs: Long): Boolean {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (!dialog.isShowing) return true
                    delay(100)
                }
                return !dialog.isShowing
            }

            private fun capture(dialog: FileDialog, name: String) {
                val r = robot ?: return
                runCatching {
                    // The WFileDialogPeer does not reflect its native bounds
                    // back into the AWT object (measured 0x0 while showing),
                    // so fall back to the full default screen when the dialog
                    // rect is empty — the native dialog is front-and-center
                    // over our window in a modal run either way.
                    val bounds = dialog.bounds.takeIf { it.width > 0 && it.height > 0 }
                        ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .defaultScreenDevice.defaultConfiguration.bounds
                    val img: BufferedImage = r.createScreenCapture(bounds)
                    Files.createDirectories(screenshotDir)
                    val file = File(screenshotDir.toFile(), "$name.png")
                    ImageIO.write(img, "png", file)
                    diag("screenshot: ${file.absolutePath} ($bounds)")
                }.onFailure { diag("screenshot '$name' failed: $it") }
            }

            private fun tap(r: Robot, keyCode: Int) {
                r.keyPress(keyCode)
                r.delay(60)
                r.keyRelease(keyCode)
                r.delay(120)
            }

            private fun selectAll(r: Robot) {
                r.keyPress(KeyEvent.VK_CONTROL)
                tap(r, KeyEvent.VK_A)
                r.keyRelease(KeyEvent.VK_CONTROL)
                r.delay(120)
            }

            /** One char into the focused field; fails the driver on unmappable chars. */
            private fun typeChar(r: Robot, c: Char) {
                val (code, shift) = charKey(c)
                    ?: throw IllegalStateException("unmappable path char '$c' (keep the workspace path space-free ASCII)")
                if (shift) r.keyPress(KeyEvent.VK_SHIFT)
                r.keyPress(code)
                r.delay(40)
                r.keyRelease(code)
                if (shift) r.keyRelease(KeyEvent.VK_SHIFT)
                r.delay(40)
            }

            /** ASCII map for space-free Windows paths. */
            private fun charKey(c: Char): Pair<Int, Boolean>? = when (c) {
                in 'a'..'z' -> (KeyEvent.VK_A + (c - 'a')) to false
                in 'A'..'Z' -> (KeyEvent.VK_A + (c - 'A')) to true
                in '0'..'9' -> (KeyEvent.VK_0 + (c - '0')) to false
                '.' -> KeyEvent.VK_PERIOD to false
                '-' -> KeyEvent.VK_MINUS to false
                '_' -> KeyEvent.VK_MINUS to true
                '/' -> KeyEvent.VK_SLASH to false
                '\\' -> KeyEvent.VK_BACK_SLASH to false
                ':' -> KeyEvent.VK_SEMICOLON to true
                ';' -> KeyEvent.VK_SEMICOLON to false
                ' ' -> KeyEvent.VK_SPACE to false
                else -> null
            }

            private fun diag(message: String) {
                println(
                    "[JellyPlay][dialogpass] t=+${System.currentTimeMillis() - startedAtMs}ms $message",
                )
            }

            private companion object {
                const val MAX_ATTEMPTS = 3
            }
        }

        // ── plumbing (session-harness twins) ─────────────────────────────────

        /** Deadline timer: write whatever exists, exit 0 (perf-harness twin). */
        private fun armAutoExit() {
            Timer("jellyplay-dialogpass", true).schedule(
                object : java.util.TimerTask() {
                    override fun run() {
                        System.err.println(
                            "[JellyPlay][dialogpass] auto-exit deadline (${autoExitSeconds}s) reached",
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

        private suspend fun step(
            name: String,
            block: suspend () -> Map<String, String>,
        ): Boolean {
            val atMs = System.currentTimeMillis()
            val result = try {
                val details = block()
                StepResult(
                    name,
                    pass = true,
                    atMs,
                    System.currentTimeMillis() - atMs,
                    details,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                StepResult(
                    name,
                    pass = false,
                    atMs,
                    System.currentTimeMillis() - atMs,
                    emptyMap(),
                    "$e",
                )
            }
            println(
                "[JellyPlay][dialogpass] step ${result.name}: " +
                    (if (result.pass) "PASS" else "FAIL") +
                    (result.error?.let { " — $it" } ?: "") +
                    " (${result.durationMs}ms)",
            )
            synchronized(lock) { steps += result }
            return result.pass
        }

        private suspend fun awaitUntil(timeoutMs: Long, poll: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (poll()) return true
                delay(100)
            }
            return poll()
        }

        private fun writeReport() {
            val (stepList, fatalErr) = synchronized(lock) { steps.toList() to fatal }
            val json = SessionHarnessReport(
                startedAtMs = startedAtMs,
                finishedAtMs = System.currentTimeMillis(),
                overallPass = stepList.isNotEmpty() && stepList.all { it.pass } && fatalErr == null,
                fatal = fatalErr?.toString(),
                machine = mapOf(
                    "os.name" to System.getProperty("os.name"),
                    "os.version" to System.getProperty("os.version"),
                    "java.version" to System.getProperty("java.version"),
                    "workspace" to workspace.toString(),
                ),
                steps = stepList,
                harness = "desktop-native-dialog",
            ).toJson()
            Files.createDirectories(logsDir)
            Files.writeString(logsDir.resolve(REPORT_FILE_NAME), json)
            println(
                "[JellyPlay][dialogpass] report written: $logsDir${File.separatorChar}$REPORT_FILE_NAME",
            )
        }

        companion object {
            @Volatile
            var currentFatalHandler: ((Throwable) -> Unit)? = null

            private const val REPORT_FILE_NAME = "dialog-harness.json"
        }
    }
}

/**
 * Composition-site sugar so DesktopAppRoot hosts the dialog harness in one
 * call. Internal — only the desktop shell uses it. Gated by
 * [DesktopNativeDialogHarness.requested] at the call site so a normal boot
 * composes nothing here. The [SettingsViewModel] is built from the same Koin
 * singles the settings module's viewModel definition injects (a harness-owned
 * instance; no settings screen composes in this mode) — plain `get()` on a
 * viewModel definition is deliberately avoided so the harness does not depend
 * on viewModel-index resolution semantics.
 */
@Composable
internal fun DesktopNativeDialogHarnessHost() {
    val settingsBackupIo: SettingsBackupIo = org.koin.compose.koinInject()
    val preferencesStore: UserPreferencesStore = org.koin.compose.koinInject()
    val projections: PreferenceProjections = org.koin.compose.koinInject()
    val authRepository: AuthRepository = org.koin.compose.koinInject()
    val seerrRepository: SeerrRepository = org.koin.compose.koinInject()
    val adminRepository: AdminRepository = org.koin.compose.koinInject()
    val editor: PreferencesEditor = org.koin.compose.koinInject()
    val recentsStore: SettingsRecentsStore = org.koin.compose.koinInject()
    LaunchedEffect(Unit) {
        DesktopNativeDialogHarness.runIfRequested(
            DesktopNativeDialogHarness.DialogPassDeps(
                SettingsViewModel(
                    settingsBackupIo = settingsBackupIo,
                    preferencesStore = preferencesStore,
                    projections = projections,
                    authRepository = authRepository,
                    seerrRepository = seerrRepository,
                    adminRepository = adminRepository,
                    editor = editor,
                    recentsStore = recentsStore,
                ),
            ),
        )
    }
}
