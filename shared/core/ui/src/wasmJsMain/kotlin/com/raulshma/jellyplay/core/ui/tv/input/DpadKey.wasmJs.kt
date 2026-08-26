package com.raulshma.jellyplay.core.ui.tv.input

import androidx.compose.ui.input.key.KeyEvent

// Web has no D-pad key source: the mapping never fires, so every handler
// registered through onDpadKey/onDpadKeyEvent stays inert (the dpadKeyHandler
// modifier itself is already gated by LocalTvMode).
actual fun KeyEvent.toDpadKeyEvent(): DpadKeyEvent? = null
