package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.window.DialogProperties

internal actual fun imeDialogProperties(): DialogProperties =
    DialogProperties(decorFitsSystemWindows = false)
