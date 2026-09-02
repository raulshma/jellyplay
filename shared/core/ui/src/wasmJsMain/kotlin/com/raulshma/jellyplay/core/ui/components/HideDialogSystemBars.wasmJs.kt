package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable

/** Web dialogs have no system bars to hide (mirrors the jvmMain inertness). */
@Composable
internal actual fun HideDialogSystemBars() {
    // No-op on wasm.
}
