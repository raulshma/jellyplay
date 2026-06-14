package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.feature.player.video.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val isTv = LocalJellyPlayUi.current.isTv

    if (isTv) {
        TvSafeSheet(
            onDismissRequest = onDismissRequest,
        ) {
            content()
        }
    } else {
        val colorScheme = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val context = LocalContext.current

        DisposableEffect(Unit) {
            onDispose {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                context.findActivity()?.let { act ->
                    val window = act.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                onDismissRequest()
            },
            modifier = modifier,
            sheetState = sheetState,
            containerColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            val view = LocalView.current
            DisposableEffect(view) {
                val checkAndHide = {
                    val window = (view.parent as? DialogWindowProvider)?.window
                    window?.let {
                        val controller = WindowCompat.getInsetsController(it, it.decorView)
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (view.isAttachedToWindow) {
                    checkAndHide()
                }
                val listener = object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        checkAndHide()
                    }
                    override fun onViewDetachedFromWindow(v: android.view.View) {}
                }
                view.addOnAttachStateChangeListener(listener)
                onDispose {
                    view.removeOnAttachStateChangeListener(listener)
                }
            }
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    content = content,
                )
            }
        }
    }
}
