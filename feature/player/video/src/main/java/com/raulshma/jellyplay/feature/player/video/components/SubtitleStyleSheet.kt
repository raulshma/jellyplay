package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.TextSize
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities

/**
 * Subtitle settings bottom sheet for the video player. The editable controls
 * live in the shared [SubtitleStyleControls] (also consumed by the standalone
 * subtitle tester) — this composable only owns the sheet chrome (title,
 * "Open tester" action) and forwards style changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleSheet(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
    capabilities: EngineCapabilities = EngineCapabilities(),
    onPickFont: () -> Unit = {},
    onOpenTester: () -> Unit = {},
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.player_video_subtitle_settings),
                icon = Tabler.Outline.TextSize,
                trailing = {
                    TextButton(onClick = onOpenTester) {
                        Text(stringResource(R.string.player_video_open_tester))
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            SubtitleStyleControls(
                currentStyle = currentStyle,
                onStyleChange = onStyleChange,
                capabilities = capabilities,
                onPickFont = onPickFont,
                showOverrideToggle = true,
                onReset = { onStyleChange(SubtitleStyle(applyCustomStyle = true)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
