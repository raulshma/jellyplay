package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import coil3.PlatformContext

// No desktop dominant-color classifier yet: cards render the fallback tint
// until one lands, matching the designsystem desktop artwork stance.
internal actual suspend fun extractDominantColor(context: PlatformContext, imageUrl: String): Color? = null
