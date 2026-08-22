package com.raulshma.jellyplay.core.ui.tv

import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext

/**
 * Plays the OS focus-navigation sound effect when a composable gains focus. Gated by [enabled]
 * (typically a user preference for accessibility). [enableMarqueeOnFocus] lives in
 * `shared/core/ui`.
 */
@Composable
fun Modifier.playSoundOnFocus(enabled: Boolean): Modifier {
    if (!enabled) return this
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    return this.onFocusChanged {
        if (it.isFocused) {
            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
        }
    }
}
