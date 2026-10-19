package com.raulshma.jellyplay.feature.player.video.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The composition binding for the chrome module's [PlayerChromeController]
 * (the same-package split-package precedent: this file is player-video's
 * half of the contract's chrome package, like the engine package). Owns the
 * visibility STATE the controller binds — [persistVisible] backs it with
 * `rememberSaveable` (the VOD screen's config-change restore contract,
 * preserved verbatim) or plain `remember` (the live screen's) — and every
 * decision about it lives in the controller.
 *
 * The reader lambdas must be stable-capture (the reader-lambda rule): they
 * are baked into the remembered controller, so each must read through a
 * state delegate / stable handle, never a per-recomposition value.
 *
 * Public because the LIVE player's screen binds the same controller (the
 * PlayerTvFocusGrabEffect public-for-live precedent).
 */
@Composable
fun rememberPlayerChromeController(
    isTv: Boolean,
    controlsTimeoutMs: () -> Long,
    isSheetOpen: () -> Boolean,
    persistVisible: Boolean = false,
    isSeeking: () -> Boolean = { false },
    isOverflowMenuOpen: () -> Boolean = { false },
    controlsHasFocus: () -> Boolean = { false },
    hideOsdOnPause: () -> Boolean = { false },
    onVisibilityChange: (Boolean) -> Unit = {},
): PlayerChromeController {
    var visible by if (persistVisible) {
        rememberSaveable { mutableStateOf(true) }
    } else {
        remember { mutableStateOf(true) }
    }
    val scope = rememberCoroutineScope()
    return remember(scope) {
        PlayerChromeController(
            scope = scope,
            isVisibleNow = { visible },
            applyVisible = { visible = it },
            isTv = { isTv },
            controlsTimeoutMs = controlsTimeoutMs,
            isSheetOpen = isSheetOpen,
            isSeeking = isSeeking,
            isOverflowMenuOpen = isOverflowMenuOpen,
            controlsHasFocus = controlsHasFocus,
            hideOsdOnPause = hideOsdOnPause,
            onVisibilityChange = onVisibilityChange,
        )
    }
}
