package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
import com.raulshma.jellyplay.core.data.playback.FrameRateMatcher
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.player.TranscodeReasonsFormatter
import com.raulshma.jellyplay.core.ui.player.findActivity
import com.raulshma.jellyplay.feature.player.video.components.CastButton
import com.raulshma.jellyplay.feature.player.video.components.MpvSubtitleOverlay
import com.raulshma.jellyplay.feature.player.video.engine.AndroidSurfaceProvider
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android actuals for the commonMain [VideoPlayerScreen] platform seams
 * (wave 9A). Every body here is the screen's pre-split inline code, moved
 * verbatim — the transforms are call-shape only (Activity/Context acquired
 * inside the actuals, android.net.Uri stringified at the boundary).
 */

// ── Host window ────────────────────────────────────────────────────────────

/** Wraps the host [Activity]; a null activity (no window yet) no-ops every op. */
private class AndroidPlayerWindowOps(private val activity: Activity?) : PlayerWindowOps {

    private inline fun ifAlive(block: (Activity) -> Unit) {
        val act = activity ?: return
        if (!act.isDestroyed && !act.isFinishing) block(act)
    }

    private fun hideSystemBarsInternal(act: Activity) {
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override val isInPipMode: Boolean
        get() = activity?.isInPictureInPictureMode == true

    override fun hideSystemBars() {
        activity?.let { hideSystemBarsInternal(it) }
    }

    override fun showSystemBars() {
        ifAlive { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun setKeepScreenOn(enabled: Boolean) {
        ifAlive {
            if (enabled) {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun readWindowBrightness(): Float =
        activity?.window?.attributes?.screenBrightness ?: -1f

    override fun writeWindowBrightness(level: Float) {
        activity?.let { act ->
            val layout = act.window.attributes
            layout.screenBrightness = level
            act.window.attributes = layout
        }
    }

    override fun restoreWindowBrightness(restored: Float) {
        ifAlive { act ->
            val layout = act.window.attributes
            layout.screenBrightness =
                if (restored >= 0f) restored
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            act.window.attributes = layout
        }
    }

    override fun applyWindowBrightness(level: Float) {
        ifAlive { act ->
            val layout = act.window.attributes
            layout.screenBrightness = level
            act.window.attributes = layout
        }
    }

    override fun readMusicStreamVolume(): Pair<Int, Int> {
        val am = activity?.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager ?: return 0 to 0
        return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) to
            am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    }

    override fun setMusicStreamVolume(volume: Int) {
        val am = activity?.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager ?: return
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volume, 0)
    }

    override fun performConfirmHaptic() {
        activity?.let { act ->
            val view = act.window.decorView
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private fun lockConstant(mode: PlayerOrientationLock): Int = when (mode) {
        PlayerOrientationLock.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationLock.SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        PlayerOrientationLock.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        PlayerOrientationLock.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlayerOrientationLock.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientationLock.TV_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationLock.USER -> ActivityInfo.SCREEN_ORIENTATION_USER
        PlayerOrientationLock.UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun lockOrientation(mode: PlayerOrientationLock) {
        ifAlive { it.requestedOrientation = lockConstant(mode) }
    }

    override fun toggleOrientation(preferLockedLandscape: Boolean) {
        activity?.let { act ->
            val current = act.requestedOrientation
            val isPortrait = current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            // Resolve the configured default landscape mode so the toggle is symmetric:
            // portrait ↔ default-landscape, always returning to the user's preferred
            // landscape rather than drifting between LANDSCAPE and SENSOR_LANDSCAPE.
            val defaultLandscape = if (preferLockedLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            act.requestedOrientation = if (isPortrait) defaultLandscape
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun matchFrameRate(
        frameRate: Float?,
        targetWidth: Int?,
        targetHeight: Int?,
        mode: RefreshRateMode,
    ) {
        ifAlive {
            FrameRateMatcher.matchFrameRate(
                activity = it,
                frameRate = frameRate,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                mode = mode,
            )
        }
    }

    override fun restoreFrameRateMode() {
        ifAlive { FrameRateMatcher.restoreOriginalMode(it) }
    }

    override fun restoreOnPlayerExit(orientation: PlayerOrientationLock) {
        ifAlive {
            it.requestedOrientation = lockConstant(orientation)
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore OS-default brightness when leaving the player; otherwise a
            // gesture-set level persists on the host window after the screen exits.
            val layout = it.window.attributes
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            it.window.attributes = layout
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        restoreFrameRateMode()
    }
}

@Composable
internal actual fun rememberPlayerWindowOps(): PlayerWindowOps {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { AndroidPlayerWindowOps(activity) }
}

/**
 * STREAM_MUSIC volume nudge for the hardware-keyboard shortcuts — the
 * screen's pre-split `adjustStreamMusicVolume` verbatim.
 */
@Composable
internal actual fun rememberStreamVolumeAdjuster(): (up: Boolean) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { up: Boolean ->
            val am = context.getSystemService(android.content.Context.AUDIO_SERVICE)
                as? android.media.AudioManager
            if (am != null) {
                val direction = if (up) {
                    android.media.AudioManager.ADJUST_RAISE
                } else {
                    android.media.AudioManager.ADJUST_LOWER
                }
                am.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    direction,
                    android.media.AudioManager.FLAG_SHOW_UI,
                )
            }
        }
    }
}

@Composable
internal actual fun rememberHasHardwareKeyboard(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
            context.resources.configuration.hardKeyboardHidden !=
            android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES
    }
}

@Composable
internal actual fun rememberIsPortraitOrientation(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

@Composable
internal actual fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    return remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
}

// ── Key codes ──────────────────────────────────────────────────────────────

internal actual val KeyEvent.playerKeyCode: Int
    get() = nativeKeyEvent.keyCode

internal actual object PlayerKeyCodes {
    actual val KEYCODE_SPACE: Int = android.view.KeyEvent.KEYCODE_SPACE
    actual val KEYCODE_MEDIA_PLAY: Int = android.view.KeyEvent.KEYCODE_MEDIA_PLAY
    actual val KEYCODE_MEDIA_PAUSE: Int = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
    actual val KEYCODE_MEDIA_PLAY_PAUSE: Int = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    actual val KEYCODE_DPAD_RIGHT: Int = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    actual val KEYCODE_MEDIA_FAST_FORWARD: Int = android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    actual val KEYCODE_L: Int = android.view.KeyEvent.KEYCODE_L
    actual val KEYCODE_DPAD_LEFT: Int = android.view.KeyEvent.KEYCODE_DPAD_LEFT
    actual val KEYCODE_MEDIA_REWIND: Int = android.view.KeyEvent.KEYCODE_MEDIA_REWIND
    actual val KEYCODE_J: Int = android.view.KeyEvent.KEYCODE_J
    actual val KEYCODE_DPAD_UP: Int = android.view.KeyEvent.KEYCODE_DPAD_UP
    actual val KEYCODE_VOLUME_UP: Int = android.view.KeyEvent.KEYCODE_VOLUME_UP
    actual val KEYCODE_DPAD_DOWN: Int = android.view.KeyEvent.KEYCODE_DPAD_DOWN
    actual val KEYCODE_VOLUME_DOWN: Int = android.view.KeyEvent.KEYCODE_VOLUME_DOWN
    actual val KEYCODE_F: Int = android.view.KeyEvent.KEYCODE_F
    actual val KEYCODE_F1: Int = android.view.KeyEvent.KEYCODE_F1
    actual val KEYCODE_F2: Int = android.view.KeyEvent.KEYCODE_F2
    actual val KEYCODE_F3: Int = android.view.KeyEvent.KEYCODE_F3
    actual val KEYCODE_F4: Int = android.view.KeyEvent.KEYCODE_F4
    actual val KEYCODE_M: Int = android.view.KeyEvent.KEYCODE_M
    actual val KEYCODE_ESCAPE: Int = android.view.KeyEvent.KEYCODE_ESCAPE
    actual val KEYCODE_BACK: Int = android.view.KeyEvent.KEYCODE_BACK
}

// ── Document picker ────────────────────────────────────────────────────────

@Composable
internal actual fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onResult: (String?) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        onResult(uri?.toString())
    }
    return remember(launcher) { { launcher.launch(mimeTypes) } }
}

internal actual fun pickedDocumentDisplayName(uriString: String): String? =
    // Decoded last segment — HEAD's `Uri.lastPathSegment` semantics, preserved
    // so percent-encoded SAF names never reach the subtitle track label.
    runCatching { android.net.Uri.parse(uriString).lastPathSegment }
        .getOrNull()
        ?.substringAfterLast('/')

// ── Bitmaps ────────────────────────────────────────────────────────────────

actual typealias PlatformBitmap = android.graphics.Bitmap

actual fun PlatformBitmap.asPlatformImageBitmap(): ImageBitmap = asImageBitmap()

// ── Cast chrome ────────────────────────────────────────────────────────────

internal actual val VideoPlayerViewModel.platformCastManager: Any?
    get() = androidCastManager

@Composable
internal actual fun rememberCastDisconnect(viewModel: VideoPlayerViewModel): () -> Unit {
    val context = LocalContext.current
    return remember(context, viewModel) {
        { viewModel.androidCast.disconnect(context) }
    }
}

internal actual fun CoroutineScope.launchPlatformCastSessionEvents(viewModel: VideoPlayerViewModel) {
    launch {
        viewModel.androidCast.castSessionEvents.collect { event ->
            when (event) {
                is CastSessionEvent.Connected -> viewModel.cast.castToDevice()
                is CastSessionEvent.Disconnected -> viewModel.cast.onCastDisconnected()
            }
        }
    }
}

@Composable
internal actual fun PlatformCastButton(castManager: Any?) {
    if (castManager is com.raulshma.jellyplay.core.data.cast.CastManager) {
        CastButton(castManager = castManager)
    }
}

// ── Engine surfaces ────────────────────────────────────────────────────────

@Composable
internal actual fun EngineVideoSurface(
    engine: MediaEngine,
    effectiveZoom: Float,
    onSurfaceCreated: (Any?) -> Unit,
    onSurfaceUpdate: () -> Unit,
    onBoundsChanged: (Int, Int, Int, Int) -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            val view = (engine as? AndroidSurfaceProvider)?.createSurfaceView(ctx)
                // Non-View-surface engine (desktop-style, impossible on Android
                // today): empty surface, audio-only playback continues.
                ?: android.view.View(ctx)
            onSurfaceCreated(view)
            view
        },
        update = { _ ->
            onSurfaceUpdate()
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = effectiveZoom
                scaleY = effectiveZoom
            }
            // Track the surface's window bounds so the Activity can
            // supply a source-rect hint for a seamless PiP enter
            // animation (the screen gates the PiP-mode case).
            .onGloballyPositioned { coords ->
                val r = coords.boundsInWindow()
                onBoundsChanged(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            },
    )
}

@Composable
internal actual fun NativePinnedSubtitleHost(engine: MediaEngine) {
    AndroidView(
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }.also { host -> (engine as? AndroidSurfaceProvider)?.setExternalSubtitleHost(host) }
        },
        onRelease = { (engine as? AndroidSurfaceProvider)?.setExternalSubtitleHost(null) },
        // Sibling of the zoomed video — explicitly NOT in a
        // graphicsLayer, so it stays pinned to the screen.
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal actual fun ZoomedSubtitleOverlayHost(
    cue: CharSequence?,
    style: SubtitleStyle,
    viewModel: VideoPlayerViewModel,
) {
    MpvSubtitleOverlay(
        cue = cue,
        style = style,
        fontProvider = viewModel.androidFontProvider,
    )
}

internal actual fun requestVideoFrameCapture(
    surfaceView: Any?,
    titleHint: String,
    onMessage: (String) -> Unit,
) {
    ScreenshotSaver.capture(
        surfaceView = surfaceView as android.view.View,
        titleHint = titleHint,
    ) { result ->
        onMessage(
            when (result) {
                is ScreenshotSaver.Result.Saved ->
                    "Frame saved to Pictures/JellyPlay (${result.width}×${result.height})"
                is ScreenshotSaver.Result.Failed ->
                    "Capture failed: ${result.reason}"
            }
        )
    }
}

// ── Transcode reasons ──────────────────────────────────────────────────────

actual typealias PlatformTranscodeReason =
    com.raulshma.jellyplay.core.ui.player.FormattedTranscodeReason

@Composable
internal actual fun rememberFormattedTranscodeReasons(
    rawReasons: List<String>,
): List<PlatformTranscodeReason> {
    val context = LocalContext.current
    return remember(context, rawReasons) {
        TranscodeReasonsFormatter.format(context, rawReasons)
    }
}
