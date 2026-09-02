package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.window.FrameWindowScope
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.platform.pickAwtFile
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import java.io.File
import java.text.DateFormat
import java.util.Locale
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope

/**
 * Desktop (jvmMain) actuals for the commonMain [VideoPlayerScreen] platform
 * seams (wave 9A). A desktop window has no system bars, no Activity
 * orientation lock and no OS brightness override, so the window-ops actual is
 * the interface's no-op default; the input/format facts have desktop-honest
 * values (hardware keyboard always present, 24-hour clock from the JDK's
 * default time format, landscape-style layout).
 */

// ── Host window ────────────────────────────────────────────────────────────

private object DesktopPlayerWindowOps : PlayerWindowOps

@Composable
internal actual fun rememberPlayerWindowOps(): PlayerWindowOps =
    remember { DesktopPlayerWindowOps }

/** No system volume stream on desktop — hardware volume keys are the OS's. */
@Composable
internal actual fun rememberStreamVolumeAdjuster(): (up: Boolean) -> Unit =
    remember { { _: Boolean -> } }

@Composable
internal actual fun rememberHasHardwareKeyboard(): Boolean = true

/**
 * Desktop grabs keyboard focus onto the player Box as soon as the
 * hardware-keyboard layer composes (wave 14A; see the commonMain expect for
 * the full dispatch-chain rationale — nothing else on the desktop shell holds
 * Compose focus while the fullscreen player is up, so the layer must own it).
 */
internal actual fun grabsKeyboardFocusWithControlsVisible(): Boolean = true

/**
 * Wave 14D focus diagnostic: stdout only while the desktop session harness is
 * armed (`jellyplay.harness.enabled=true` — DesktopSessionHarness's zero-cost
 * gate); silent on every normal desktop boot.
 */
internal actual fun harnessFocusDiag(message: String) {
    if (System.getProperty("jellyplay.harness.enabled")?.equals("true", ignoreCase = true) == true) {
        // Wave 14E: wall-clock stamp on every line so the Compose-side story
        // correlates exactly with the harness's `t=+…ms` AWT focus lines
        // (harness prints its own epoch ms too).
        println(
            "[JellyPlay][harness][focus-diag] now=${System.currentTimeMillis()} $message",
        )
    }
}

/**
 * Wave 14E deterministic key delivery: delegate to the desktop shell bridge
 * (same module, see [DesktopPlayerKeyBridge] for the full rationale).
 */
internal actual fun installPlayerKeySink(sink: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) {
    DesktopPlayerKeyBridge.install(sink)
}

internal actual fun uninstallPlayerKeySink(expected: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) {
    DesktopPlayerKeyBridge.uninstall(expected)
}

/** Resizable desktop player window — treated as the landscape-style layout. */
@Composable
internal actual fun rememberIsPortraitOrientation(): Boolean = false

@Composable
internal actual fun rememberIs24HourFormat(): Boolean =
    remember {
        // JDK heuristic: format 13:00 with the default SHORT time pattern —
        // a 24-hour locale renders "13:…" while 12-hour locales render "1:… PM".
        val formatted = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            .format(java.util.Date(13L * 60L * 60L * 1000L))
        !formatted.contains("PM") && !formatted.contains("AM")
    }

// ── Key codes ──────────────────────────────────────────────────────────────

internal actual val KeyEvent.playerKeyCode: Int
    get() = when (key) {
        Key.Spacebar -> PlayerKeyCodes.KEYCODE_SPACE
        Key.MediaPlay -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY
        Key.MediaPause -> PlayerKeyCodes.KEYCODE_MEDIA_PAUSE
        Key.MediaPlayPause -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE
        Key.DirectionRight -> PlayerKeyCodes.KEYCODE_DPAD_RIGHT
        Key.MediaFastForward -> PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD
        Key.L -> PlayerKeyCodes.KEYCODE_L
        Key.DirectionLeft -> PlayerKeyCodes.KEYCODE_DPAD_LEFT
        Key.MediaRewind -> PlayerKeyCodes.KEYCODE_MEDIA_REWIND
        Key.J -> PlayerKeyCodes.KEYCODE_J
        Key.DirectionUp -> PlayerKeyCodes.KEYCODE_DPAD_UP
        Key.VolumeUp -> PlayerKeyCodes.KEYCODE_VOLUME_UP
        Key.DirectionDown -> PlayerKeyCodes.KEYCODE_DPAD_DOWN
        Key.VolumeDown -> PlayerKeyCodes.KEYCODE_VOLUME_DOWN
        Key.F -> PlayerKeyCodes.KEYCODE_F
        Key.F1 -> PlayerKeyCodes.KEYCODE_F1
        Key.F2 -> PlayerKeyCodes.KEYCODE_F2
        Key.F3 -> PlayerKeyCodes.KEYCODE_F3
        Key.F4 -> PlayerKeyCodes.KEYCODE_F4
        Key.M -> PlayerKeyCodes.KEYCODE_M
        Key.Escape -> PlayerKeyCodes.KEYCODE_ESCAPE
        else -> 0 // KEYCODE_UNKNOWN: unmatched keys fall through every when-branch.
    }

internal actual object PlayerKeyCodes {
    actual val KEYCODE_SPACE: Int = 1
    actual val KEYCODE_MEDIA_PLAY: Int = 2
    actual val KEYCODE_MEDIA_PAUSE: Int = 3
    actual val KEYCODE_MEDIA_PLAY_PAUSE: Int = 4
    actual val KEYCODE_DPAD_RIGHT: Int = 5
    actual val KEYCODE_MEDIA_FAST_FORWARD: Int = 6
    actual val KEYCODE_L: Int = 7
    actual val KEYCODE_DPAD_LEFT: Int = 8
    actual val KEYCODE_MEDIA_REWIND: Int = 9
    actual val KEYCODE_J: Int = 10
    actual val KEYCODE_DPAD_UP: Int = 11
    actual val KEYCODE_VOLUME_UP: Int = 12
    actual val KEYCODE_DPAD_DOWN: Int = 13
    actual val KEYCODE_VOLUME_DOWN: Int = 14
    actual val KEYCODE_F: Int = 15
    actual val KEYCODE_F1: Int = 16
    actual val KEYCODE_F2: Int = 17
    actual val KEYCODE_F3: Int = 18
    actual val KEYCODE_F4: Int = 19
    actual val KEYCODE_M: Int = 20
    actual val KEYCODE_ESCAPE: Int = 21
    actual val KEYCODE_BACK: Int = 22
}

// ── Document picker ────────────────────────────────────────────────────────

@Composable
internal actual fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onResult: (String?) -> Unit,
): () -> Unit =
    remember(mimeTypes, onResult) {
        {
            // Shared AWT dialog (modal). mimeTypes is advisory only — the
            // subtitle flow's list maps to subtitle/font file names, which
            // the SAF contract filtered on Android; the desktop dialog lets
            // the user pick any file and the screen's own name checks apply.
            onResult(pickAwtFile(title = "Choose file")?.toURI()?.toString())
        }
    }

internal actual fun pickedDocumentDisplayName(uriString: String): String? =
    runCatching { java.net.URI(uriString).path }
        .getOrNull()
        ?.substringAfterLast('/')

// ── Bitmaps ────────────────────────────────────────────────────────────────

actual typealias PlatformBitmap = java.awt.image.BufferedImage

actual fun PlatformBitmap.asPlatformImageBitmap(): ImageBitmap = toComposeImageBitmap()

// ── Cast chrome ────────────────────────────────────────────────────────────

internal actual val VideoPlayerViewModel.platformCastManager: Any?
    get() = null

@Composable
internal actual fun rememberCastDisconnect(viewModel: VideoPlayerViewModel): () -> Unit =
    remember(viewModel) { { } }

internal actual fun CoroutineScope.launchPlatformCastSessionEvents(viewModel: VideoPlayerViewModel) {
    // No desktop cast stack — nothing to collect.
}

@Composable
internal actual fun PlatformCastButton(castManager: Any?) {
    // Never composed: platformCastManager is null on desktop, so the controls
    // keep the cast slot hidden.
}

// ── Engine surfaces ────────────────────────────────────────────────────────

// EngineVideoSurface lives in DesktopVideoSurface.jvm.kt (SwingPanel host).

/**
 * Desktop engines that can capture the currently-displayed video frame as a
 * platform bitmap (wave 17B). Desktop has no PixelCopy equivalent for the
 * mpv-embedded child window — capture goes through the ENGINE instead (mpv's
 * `screenshot-to-file`), so the desktop `requestVideoFrameCapture` actual
 * downcasts its engine parameter to this interface, the exact dispatch
 * pattern [EngineVideoSurface]'s desktop actual uses for
 * [SoftwareFrameVideoSurface].
 *
 * Lives in jvmMain (not commonMain) because its contract is JVM-native
 * ([java.awt.image.BufferedImage] — the desktop PlatformBitmap); the Android
 * target never sees it, so the commonMain seam keeps its signature and the
 * Android PixelCopy actual stays untouched. Implemented by apps/desktop's
 * `MpvDesktopEngine`.
 */
interface DesktopFrameCaptureEngine {

    /**
     * Captures the current video frame (subtitles composited), or null when
     * there is nothing to capture or the capture fails — implementations
     * degrade, never throw.
     */
    fun captureVideoFrame(): java.awt.image.BufferedImage?
}

@Composable
internal actual fun NativePinnedSubtitleHost(engine: MediaEngine) {
    // No NATIVE_PINNED engine on desktop (mpv is COMPOSE_CUE).
}

@Composable
internal actual fun ZoomedSubtitleOverlayHost(
    cue: CharSequence?,
    style: SubtitleStyle,
    viewModel: VideoPlayerViewModel,
) {
    // Pointer pinch-zoom never engages on desktop, so the zoomed overlay is
    // unreachable; mpv's native libass path keeps rendering at zoom == 1.
}

internal actual fun requestVideoFrameCapture(
    surfaceView: Any?,
    engine: MediaEngine?,
    titleHint: String,
    onMessage: (String) -> Unit,
) {
    // The desktop never reads surfaceView: the AWT Canvas is mpv's output
    // window and has no read-back — capture is an engine (mpv command) path.
    // This also covers the software-render surface, which publishes no
    // platform surface object at all.
    val capturable = engine as? DesktopFrameCaptureEngine
    if (capturable == null) {
        onMessage("Capture failed: engine cannot capture frames")
        return
    }
    // mpv command + PNG encode/decode off the UI thread (Android's
    // ScreenshotSaver does its file I/O on a background thread too); the
    // caller's onMessage only launches a snackbar coroutine, thread-safe.
    thread(name = "jellyplay-frame-capture", isDaemon = true) {
        val image = runCatching { capturable.captureVideoFrame() }.getOrNull()
        if (image == null) {
            onMessage("Capture failed: no frame available")
            return@thread
        }
        val message = runCatching { saveCapture(image, titleHint) }
            .getOrElse { "Capture failed: ${it.message ?: it.javaClass.simpleName}" }
        onMessage(message)
    }
}

/**
 * Persists [image] under `~/Pictures/JellyPlay` (the desktop analogue of
 * Android's MediaStore folder), falling back to the temp dir when the
 * Pictures tree cannot be made. The timestamped name keeps rapid double
 * captures from colliding.
 */
private fun saveCapture(image: java.awt.image.BufferedImage, titleHint: String): String {
    val pictures = File(System.getProperty("user.home"), "Pictures/JellyPlay")
    val dir = if (pictures.isDirectory || pictures.mkdirs()) pictures
    else File(System.getProperty("java.io.tmpdir"))
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss.SSS", Locale.ROOT).format(java.util.Date())
    val safeTitle = titleHint.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60)
    val name = listOfNotNull("JellyPlay", safeTitle.takeIf { it.isNotBlank() }, stamp)
        .joinToString(" ") + ".png"
    val out = File(dir, name)
    javax.imageio.ImageIO.write(image, "png", out)
    return "Frame saved to ${out.absolutePath} (${image.width}×${image.height})"
}

// ── Transcode reasons ──────────────────────────────────────────────────────

actual class PlatformTranscodeReason(
    actual val raw: String,
    actual val explanation: String,
    actual val hint: String?,
) {
    actual val renderedText: String
        get() = if (hint != null) "$explanation\n$hint" else explanation
}

@Composable
internal actual fun rememberFormattedTranscodeReasons(
    rawReasons: List<String>,
): List<PlatformTranscodeReason> =
    // Desktop raw-token echo — the same fallback text Android's formatter
    // produces for tokens it has no string table entry for.
    remember(rawReasons) {
        rawReasons.map { PlatformTranscodeReason(raw = it, explanation = it, hint = null) }
    }
