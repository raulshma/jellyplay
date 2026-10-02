package com.raulshma.jellyplay.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.ui.components.BackExitConfirmation
import com.raulshma.jellyplay.core.ui.navigation.Navigator

/**
 * The phone layout's back-exit confirmation wiring, extracted from
 * `MainContent`'s non-full-screen branch: the remembered
 * [BackExitConfirmation] decision object, the last-press timestamp, and the
 * [BackHandler] that maps its decision onto pop / prompt-toast /
 * moveTaskToBack. The full-screen player is excluded (it owns its own
 * BackHandler) — this host is only composed in the phone branch.
 */
@Composable
internal fun BackExitHost(
    navigator: Navigator,
) {
    val context = LocalContext.current
    // Wire the system/gesture back button to in-app navigation so back
    // from a deep screen returns to the tab root. At a tab root, mirror
    // the TV path: prompt with a toast and only exit on a second press
    // inside the shared BackExitConfirmation window. The full-screen
    // player is excluded — it owns its own BackHandler.
    val backExitConfirmation = remember { BackExitConfirmation() }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        when (val decision = backExitConfirmation.onBack(
            nowMs = System.currentTimeMillis(),
            lastAtMs = lastBackPressTime,
            atExitPoint = navigator.isAtTabRoot(),
        )) {
            BackExitConfirmation.Decision.Pop -> navigator.goBack()
            is BackExitConfirmation.Decision.Prompt -> {
                lastBackPressTime = decision.nowMs
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.press_back_again_to_exit),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            BackExitConfirmation.Decision.Exit -> {
                lastBackPressTime = 0L
                (context as? android.app.Activity)?.moveTaskToBack(true)
            }
        }
    }
}
