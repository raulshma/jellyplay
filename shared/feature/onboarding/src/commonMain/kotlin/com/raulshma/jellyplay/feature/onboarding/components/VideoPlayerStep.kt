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
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_autoplay_next
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_default_orientation
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_gestures
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_gestures_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_player_engine
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_streaming_quality
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_video_title
import org.jetbrains.compose.resources.stringResource

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
            title = stringResource(Res.string.onboarding_video_title),
            subtitle = stringResource(Res.string.onboarding_video_subtitle),
            icon = Tabler.Outline.Video,
            onNext = {},
        ) {
            Text(
                text = stringResource(Res.string.onboarding_video_player_engine),
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
                text = stringResource(Res.string.onboarding_video_streaming_quality),
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
                title = stringResource(Res.string.onboarding_video_gestures),
                subtitle = stringResource(Res.string.onboarding_video_gestures_subtitle),
                checked = gesturesEnabled,
                onCheckedChange = onGesturesEnabledChange,
            )

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_video_autoplay_next),
                checked = autoplayNext,
                onCheckedChange = onAutoplayNextChange,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_video_default_orientation),
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
