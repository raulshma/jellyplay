package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable

/**
 * Web actual of the downloads messenger expect: returns null — messages drop
 * (the desktop actual's shape). The browser has no global snackbar host
 * wired for downloads, and the screen renders its own error state anyway.
 */
@Composable
internal actual fun rememberDownloadsMessenger(): DownloadsMessenger? = null
