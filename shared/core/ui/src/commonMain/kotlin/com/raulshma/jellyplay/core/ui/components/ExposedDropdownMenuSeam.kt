package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Cross-train seam for [ExposedDropdownMenuBoxScope]'s dropdown menu.
 *
 * androidx material3 1.5.0-beta02 (the version the app ships — see
 * KmpLibraryComposePlugin's material3 alignment constraint) moved
 * `ExposedDropdownMenu` from a scope member to a top-level extension and
 * HID the member; the JB compose-multiplatform material3 the desktop target
 * resolves only has the scope member, so no top-level import exists there.
 * A hidden member with the same name blocks same-package extension
 * resolution on beta02 (the extension is not found without an explicit
 * import, and the explicit import does not exist on JB) — commonMain cannot
 * spell either form directly. The expect/actual pair resolves per platform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
expect fun ExposedDropdownMenuBoxScope.jellyPlayExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
)
