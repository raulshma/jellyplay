package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.Composable

/**
 * The wasmJs actual of the voice-search seam: null (the desktop actual's
 * shape) — the browser has no speech recognition wired for now, so the
 * mic affordance in [SearchScreen] hides on null like on desktop.
 */
@Composable
internal actual fun rememberVoiceSearchLauncher(
    prompt: String,
    onResult: (String?) -> Unit,
): (() -> Unit)? = null
