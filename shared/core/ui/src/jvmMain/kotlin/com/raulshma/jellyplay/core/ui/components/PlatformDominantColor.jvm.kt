package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import coil3.PlatformContext

// Desktop dominant-color classifier lands with the shell polish pass (plan
// §Phase V1b/V2); cards render the fallback tint until then, matching the
// designsystem desktop artwork stance.
internal actual suspend fun extractDominantColor(context: PlatformContext, imageUrl: String): Color? = null
