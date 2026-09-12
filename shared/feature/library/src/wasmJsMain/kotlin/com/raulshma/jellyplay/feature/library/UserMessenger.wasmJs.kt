package com.raulshma.jellyplay.feature.library

import androidx.compose.runtime.Composable

/**
 * The wasmJs actual of the photo-viewer feedback seam: null (the desktop
 * actual's shape) — the browser has no global message host wired for now,
 * so save/share result messages drop like on desktop.
 */
@Composable
internal actual fun rememberUserMessenger(): UserMessenger? = null
