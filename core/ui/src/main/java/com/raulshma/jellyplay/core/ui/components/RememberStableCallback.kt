package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Memoizes a callback keyed on [keys] so list-item composables stay skippable
 * across parent state emissions — the `remember(id) { { onClick(item) } }`
 * grid idiom without the double-brace noise. Key on everything the callback
 * reads (typically the item id, plus any fields it forwards); while the keys
 * hold, the child sees the same callback instance.
 */
@Composable
fun rememberStableCallback(vararg keys: Any?, callback: () -> Unit): () -> Unit =
    remember(*keys) { callback }
