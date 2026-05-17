package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

enum class AspectRatio(val displayName: String, val ratio: Float?) {
    AUTO("Auto", null),
    FIT("Fit", null),
    FILL("Fill", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_21_9("21:9", 21f / 9f),
    CROP("Crop", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    currentRatio: AspectRatio,
    detectedRatio: AspectRatio?,
    onSelect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Aspect Ratio",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (detectedRatio != null && detectedRatio != AspectRatio.FIT) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Detected: ${detectedRatio.displayName}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AspectRatio.entries.forEach { ratio ->
                    val isSelected = ratio == currentRatio
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelect(ratio)
                            onDismiss()
                        },
                        label = {
                            if (ratio == AspectRatio.AUTO && detectedRatio != null) {
                                Text("Auto (${detectedRatio.displayName})")
                            } else {
                                Text(
                                    ratio.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smoothPill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            enabled = true,
                            selected = isSelected,
                        ),
                    )
                }
            }
        }
    }
}
