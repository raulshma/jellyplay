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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_description
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_enable
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_enable_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_mode
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_performance_title
import org.jetbrains.compose.resources.stringResource

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
            title = stringResource(Res.string.onboarding_performance_title),
            subtitle = stringResource(Res.string.onboarding_performance_subtitle),
            icon = Tabler.Outline.Bolt,
            onNext = {},
        ) {
            Text(
                text = stringResource(Res.string.onboarding_performance_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.onboarding_performance_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            OnboardingToggleRow(
                title = stringResource(Res.string.onboarding_performance_enable),
                subtitle = stringResource(Res.string.onboarding_performance_enable_subtitle),
                checked = performanceMode,
                onCheckedChange = onPerformanceModeChange,
            )
        }
    }
}
