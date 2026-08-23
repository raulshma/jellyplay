package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet

/**
 * Shared shell for the per-filter selection sheets. Renders a
 * title and a scrollable content area inside a [TvSafeSheet] — phone
 * ModalBottomSheet / TV full-screen Dialog with D-pad focus — the same
 * shell the app's other sheets use.
 *
 * Per-filter sheets apply immediately on selection (no Apply button), matching
 * the chip→sheet→toggle flow, so this shell only takes an [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSelectionSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // SheetHeader carries the standard title treatment (titleLarge /
            // SemiBold, leading-icon slot) and its own horizontal padding, so the
            // body no longer pads horizontally here — content() keeps its own.
            SheetHeader(title = title)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                content()
            }
        }
    }
}
