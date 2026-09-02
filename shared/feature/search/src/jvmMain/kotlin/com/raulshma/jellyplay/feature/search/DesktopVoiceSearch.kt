package com.raulshma.jellyplay.feature.search

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberVoiceSearchLauncher(
    prompt: String,
    onResult: (String?) -> Unit,
): (() -> Unit)? = null
