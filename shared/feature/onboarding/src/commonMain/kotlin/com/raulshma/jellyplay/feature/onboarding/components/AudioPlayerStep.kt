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
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_autoplay_next
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_crossfade
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_default_speed
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_gapless
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_gapless_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_normalization
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_normalization_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_off
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_audio_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioPlayerStep(
    defaultSpeed: Float,
    gaplessEnabled: Boolean,
    crossfadeDurationMs: Long,
    normalizationEnabled: Boolean,
    autoplayNext: Boolean,
    onDefaultSpeedChange: (Float) -> Unit,
    onGaplessEnabledChange: (Boolean) -> Unit,
    onCrossfadeDurationChange: (Long) -> Unit,
    onNormalizationEnabledChange: (Boolean) -> Unit,
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
            title = stringResource(Res.string.onboarding_audio_title),
            subtitle = stringResource(Res.string.onboarding_audio_subtitle),
            icon = Tabler.Outline.Headphones,
            onNext = {},
        ) {
            Text(
                text = stringResource(Res.string.onboarding_audio_default_speed),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                    val selected = speed == defaultSpeed
                    OnboardingOptionCard(
                        label = "${speed}x",
                        selected = selected,
                        onClick = { onDefaultSpeedChange(speed) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_audio_gapless),
                subtitle = stringResource(Res.string.onboarding_audio_gapless_subtitle),
                checked = gaplessEnabled,
                onCheckedChange = onGaplessEnabledChange,
            )

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_audio_autoplay_next),
                checked = autoplayNext,
                onCheckedChange = onAutoplayNextChange,
            )

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_audio_normalization),
                subtitle = stringResource(Res.string.onboarding_audio_normalization_subtitle),
                checked = normalizationEnabled,
                onCheckedChange = onNormalizationEnabledChange,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_audio_crossfade),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(0L, 2000L, 5000L, 8000L).forEach { duration ->
                    val selected = duration == crossfadeDurationMs
                    OnboardingOptionCard(
                        label = if (duration == 0L) stringResource(Res.string.onboarding_audio_off) else "${duration / 1000}s",
                        selected = selected,
                        onClick = { onCrossfadeDurationChange(duration) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
