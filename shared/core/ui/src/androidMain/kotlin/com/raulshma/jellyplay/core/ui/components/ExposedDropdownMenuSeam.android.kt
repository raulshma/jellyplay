package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified

/** Android resolves the androidx beta02 extension; desktop the JB member — see the expect KDoc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ExposedDropdownMenuBoxScope.jellyPlayExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (containerColor.isUnspecified) {
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            content = content,
        )
    } else {
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = containerColor,
            content = content,
        )
    }
}
