package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.onboarding.R

@Composable
fun PerformanceStep(
    performanceMode: Boolean,
    onPerformanceModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = stringResource(R.string.onboarding_performance_title),
            subtitle = stringResource(R.string.onboarding_performance_subtitle),
            icon = Tabler.Outline.Bolt,
            onNext = {},
        ) {
            Text(
                text = "Performance Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Reduces animations, transitions, and image quality for smoother performance on lower-end devices. You can always change this later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            OnboardingToggleRow(
                title = stringResource(R.string.onboarding_performance_enable),
                subtitle = stringResource(R.string.onboarding_performance_enable_subtitle),
                checked = performanceMode,
                onCheckedChange = onPerformanceModeChange,
            )
        }
    }
}
