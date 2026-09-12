package com.raulshma.jellyplay.feature.livetv

import androidx.compose.runtime.Composable

/**
 * Web actual of the [LiveTvMessenger] seam: null, like the desktop actual —
 * the browser shell has no UserMessageBus host yet, so the channel-detail
 * record/cancel feedback drops (messages are already resolved [String]s at
 * the call site; the record outcome still reaches the user through the
 * screens' own dialog/toast surfaces).
 */
@Composable
internal actual fun rememberLiveTvMessenger(): LiveTvMessenger? = null
