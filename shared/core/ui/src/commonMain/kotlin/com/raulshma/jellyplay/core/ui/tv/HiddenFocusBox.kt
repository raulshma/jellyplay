package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * A 1dp-tall focusable "trap" that fires [onFocus] when focus lands on it. Used to catch the D-pad
 * after closing a dropdown/overlay/dialog and trigger state restoration — for example, snapping
 * focus back to the previously-focused grid card after a context menu closes.
 *
 * The composable is
 * invisible but still focusable, so a `moveFocus()` call from elsewhere lands here and triggers
 * the callback.
 */
@Composable
fun HiddenFocusBox(
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: () -> Unit,
) = Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .focusRequester(focusRequester)
        .onFocusChanged { if (it.isFocused) onFocus.invoke() }
        .focusable(),
)
