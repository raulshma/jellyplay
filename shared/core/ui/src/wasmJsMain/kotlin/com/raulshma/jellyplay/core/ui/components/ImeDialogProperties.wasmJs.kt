package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.window.DialogProperties

/** Mirrors the jvmMain default shape: no IME-specific window tuning on web. */
internal actual fun imeDialogProperties(): DialogProperties = DialogProperties()
