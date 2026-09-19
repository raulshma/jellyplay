package com.raulshma.jellyplay.feature.book

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal actual fun rememberReaderWindowOps(): ReaderWindowOps {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    return remember(activity, view) { AndroidReaderWindowOps(activity, view) }
}

private class AndroidReaderWindowOps(
    private val activity: Activity?,
    private val view: View,
) : ReaderWindowOps {

    override fun enter() {
        val window = activity?.window ?: return
        // No setDecorFitsSystemWindows flip: the app is edge-to-edge
        // app-wide (MainActivity), and the reader must not own that bit —
        // restoring a different value on exit would break other screens.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun exit() {
        val window = activity?.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, view)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
