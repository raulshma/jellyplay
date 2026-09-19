package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.Composable

/**
 * Host-window seam for the reader screen (module-internal twin of the video
 * player's PlayerWindowOps, scoped to what a reader needs): keep-screen-on
 * while the reader is up, and Android immersive system-bar hiding with
 * swipe-to-reveal. The jvmMain actual is a no-op — a desktop window has no
 * system bars and no keep-screen-on flag.
 */
internal interface ReaderWindowOps {
    /** FLAG_KEEP_SCREEN_ON + hide system bars (Android); no-op on desktop. */
    fun enter() {}

    /** Restore the system bars + clear keep-screen-on on reader exit (Android); no-op on desktop. */
    fun exit() {}
}

/** The host-window operations for the current composition (see [ReaderWindowOps]). */
@Composable
internal expect fun rememberReaderWindowOps(): ReaderWindowOps
