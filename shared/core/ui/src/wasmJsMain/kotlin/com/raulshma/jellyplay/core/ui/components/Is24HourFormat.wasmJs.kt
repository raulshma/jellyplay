package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** `hour12` may be absent on older engines; probe presence first. */
private fun hour12Defined(): Boolean =
    js("typeof Intl.DateTimeFormat().resolvedOptions().hour12 !== 'undefined'")

private fun hour12Resolved(): Boolean =
    js("Intl.DateTimeFormat().resolvedOptions().hour12 === true")

@Composable
internal actual fun rememberIs24HourFormat(): Boolean =
    remember { if (hour12Defined()) hour12Resolved() else false }
