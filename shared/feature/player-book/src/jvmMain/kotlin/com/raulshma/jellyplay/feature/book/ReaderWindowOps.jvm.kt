package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberReaderWindowOps(): ReaderWindowOps = DesktopReaderWindowOps

/** A desktop window has no system bars and no keep-screen-on flag — no-op singleton. */
private object DesktopReaderWindowOps : ReaderWindowOps
