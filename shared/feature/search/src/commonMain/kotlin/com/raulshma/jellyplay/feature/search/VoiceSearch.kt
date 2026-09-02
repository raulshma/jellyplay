package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.Composable

/**
 * Speech-recognition seam. Returns a launcher that starts the platform voice
 * search, or null when no recognition activity exists — the mic affordance in
 * [SearchScreen] hides on null (Android gates on `resolveActivity`; desktop
 * has no engine yet).
 */
@Composable
internal expect fun rememberVoiceSearchLauncher(
    prompt: String,
    onResult: (String?) -> Unit,
): (() -> Unit)?
