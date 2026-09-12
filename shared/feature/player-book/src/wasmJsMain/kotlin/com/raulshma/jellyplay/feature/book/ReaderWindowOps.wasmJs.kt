package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.Composable

/**
 * The wasmJs actual of the reader window seam: the jvmMain actual's no-op
 * shape — a browser tab has no system bars to hide and no keep-screen-on
 * flag reachable from the composition for now.
 */
internal object WasmReaderWindowOps : ReaderWindowOps

/** The host-window operations for the current composition (see [ReaderWindowOps]). */
@Composable
internal actual fun rememberReaderWindowOps(): ReaderWindowOps = WasmReaderWindowOps
