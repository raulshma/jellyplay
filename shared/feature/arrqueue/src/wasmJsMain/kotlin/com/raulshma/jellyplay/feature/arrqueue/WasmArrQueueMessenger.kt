package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Composable

// Web has no message-bus host wired yet (same degradation desktop accepts):
// the actual returns null and queue action messages drop silently.
@Composable
internal actual fun rememberArrQueueMessenger(): ArrQueueMessenger? = null
