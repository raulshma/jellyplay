package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.library.R

/**
 * Shared shell for the per-filter selection sheets. Renders a
 * title and a scrollable content area inside a [ModalBottomSheet] (phone) or a
 * centered [Dialog] (TV) — the same phone/TV split [LibraryFilterSheet] uses.
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
    val isTv = LocalTvMode.current
    val isLight = LocalIsLightTheme.current
    val sheetContainerColor = if (isLight) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            content()
        }
    }

    if (isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .fillMaxWidth(0.6f)
                    .clip(ShapeCache.smooth24),
                color = sheetContainerColor,
                tonalElevation = 6.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize()) { body() }
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = sheetContainerColor,
            tonalElevation = 0.dp,
        ) {
            body()
        }
    }
}
