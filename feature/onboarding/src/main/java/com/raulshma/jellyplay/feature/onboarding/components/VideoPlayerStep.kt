package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality

@Composable
fun VideoPlayerStep(
    preferredPlayer: PlayerType,
    streamingQuality: StreamingQuality,
    seekDurationMs: Long,
    gesturesEnabled: Boolean,
    defaultOrientation: OrientationMode,
    autoplayNext: Boolean,
    onPreferredPlayerChange: (PlayerType) -> Unit,
    onStreamingQualityChange: (StreamingQuality) -> Unit,
    onSeekDurationChange: (Long) -> Unit,
    onGesturesEnabledChange: (Boolean) -> Unit,
    onDefaultOrientationChange: (OrientationMode) -> Unit,
    onAutoplayNextChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = "Video Player",
            subtitle = "Configure your default video playback experience",
            icon = Tabler.Outline.Video,
            onNext = {},
        ) {
            Text(
                text = "Player Engine",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerType.entries.forEach { player ->
                    OnboardingOptionCard(
                        label = player.displayName,
                        selected = player == preferredPlayer,
                        onClick = { onPreferredPlayerChange(player) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Streaming Quality",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    StreamingQuality.AUTO,
                    StreamingQuality.HD_720P,
                    StreamingQuality.FHD_1080P,
                    StreamingQuality.UHD_4K,
                ).forEach { quality ->
                    val selected = quality == streamingQuality
                    OnboardingOptionCard(
                        label = quality.name.replace('_', ' '),
                        selected = selected,
                        onClick = { onStreamingQualityChange(quality) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OnboardingToggleRow(
                title = "Gestures",
                subtitle = "Swipe to seek, adjust brightness and volume",
                checked = gesturesEnabled,
                onCheckedChange = onGesturesEnabledChange,
            )

            OnboardingToggleRow(
                title = "Autoplay next episode",
                checked = autoplayNext,
                onCheckedChange = onAutoplayNextChange,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Default Orientation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    OrientationMode.SENSOR_LANDSCAPE,
                    OrientationMode.SENSOR_PORTRAIT,
                    OrientationMode.SENSOR,
                ).forEach { mode ->
                    val selected = mode == defaultOrientation
                    OnboardingOptionCard(
                        label = mode.displayName,
                        selected = selected,
                        onClick = { onDefaultOrientationChange(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
