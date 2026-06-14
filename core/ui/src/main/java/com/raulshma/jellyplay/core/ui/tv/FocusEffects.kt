package com.raulshma.jellyplay.core.ui.tv

import android.media.AudioManager
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext

/**
 * Plays the OS focus-navigation sound effect when a composable gains focus. Gated by [enabled]
 * (typically a user preference for accessibility).
 *
 * A composable modifier (not a `composed { }` extension) so it can be applied directly:
 * `Modifier.playSoundOnFocus(enabled = prefs.tvFocusSounds)`.
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

/**
 * Marquee delay helper — waits [delayMillis] after focus lands before enabling
 * `Modifier.basicMarquee()`. Quick D-pad scrolling doesn't trigger a mess of marquee animations;
 * lingering on an item does.
 *
 * Typical usage on a card title:
 * ```
 * Text(title, modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused))
 * ```
 */
@Composable
fun Modifier.enableMarqueeOnFocus(
    focused: Boolean,
    delayMillis: Long = 500L,
): Modifier = composed {
    var marqueeEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(delayMillis)
            marqueeEnabled = true
        } else {
            marqueeEnabled = false
        }
    }
    if (marqueeEnabled) {
        this.basicMarquee()
    } else {
        this
    }
}
