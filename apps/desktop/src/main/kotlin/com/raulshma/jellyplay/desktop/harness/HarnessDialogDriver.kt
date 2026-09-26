package com.raulshma.jellyplay.desktop.harness

import java.awt.FileDialog
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay

/**
 * The shared Robot half of the native-dialog harnesses ('s
 * DesktopNativeDialogHarness.DialogDriver, extracted when the flows
 * lane needed the same mechanics). Runs OFF the EDT (the EDT is inside the
 * modal loop): waits for a showing [FileDialog], brings it to front, and
 * either types the full absolute path + Enter (cancel=false) or presses ESC
 * (cancel=true). Bounded attempts per the retry lesson; a dialog that
 * outlives every attempt gets a final ESC so the blocked EDT resumes and the
 * step fails instead of wedging until auto-exit. Every action prints a
 * t=+ms-stamped diag line (a past lesson: timestamps on diag lines).
 *
 * Also exposes the ASCII [typer] so flow harnesses can type short strings
 * into focused Compose fields (language inputs) with the same cadence.
 */
internal class HarnessDialogDriver(
    private val screenshotDir: Path,
    private val startedAtMs: Long,
    private val tag: String = "dialogpass",
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

    /**
     * Selects all in the focused field and types [text] at the dialog cadence
     * (for short Compose-field inputs, e.g. language codes). Requires the
     * field to hold AWT focus (click it first). No trailing Enter — the
     * target field's onValueChange commits per keystroke.
     */
    fun typeIntoFocusedField(text: String) {
        val r = robot ?: error("Robot unavailable — cannot type")
        selectAll(r)
        for (c in text) typeChar(r, c)
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
            "[JellyPlay][$tag] t=+${System.currentTimeMillis() - startedAtMs}ms $message",
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
