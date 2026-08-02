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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.feature.onboarding.R

@Composable
fun HomeLayoutStep(
    homeMode: HomeMode,
    navBarShowLabels: Boolean,
    enabledHomeSectionTypes: Set<HomeSectionType>,
    onHomeModeChange: (HomeMode) -> Unit,
    onNavBarShowLabelsChange: (Boolean) -> Unit,
    onEnabledHomeSectionTypesChange: (Set<HomeSectionType>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = stringResource(R.string.onboarding_home_layout_title),
            subtitle = stringResource(R.string.onboarding_home_layout_subtitle),
            icon = Tabler.Outline.LayoutGrid,
            onNext = {},
        ) {
            Text(
                text = stringResource(R.string.onboarding_home_layout_home_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HomeMode.entries.forEach { mode ->
                    val selected = mode == homeMode
                    OnboardingOptionCard(
                        label = mode.name,
                        icon = when (mode) {
                            HomeMode.VIDEO -> Tabler.Outline.Video
                            HomeMode.MUSIC -> Tabler.Outline.Music
                        },
                        selected = selected,
                        onClick = { onHomeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OnboardingToggleRow(
                title = stringResource(R.string.onboarding_home_layout_nav_labels),
                subtitle = stringResource(R.string.onboarding_home_layout_nav_labels_subtitle),
                checked = navBarShowLabels,
                onCheckedChange = onNavBarShowLabelsChange,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_home_layout_home_sections),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeSectionType.entries.forEach { section ->
                    OnboardingToggleRow(
                        title = section.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                        checked = section in enabledHomeSectionTypes,
                        onCheckedChange = { enabled ->
                            val newSet = if (enabled) enabledHomeSectionTypes + section
                            else enabledHomeSectionTypes - section
                            onEnabledHomeSectionTypesChange(newSet)
                        },
                    )
                }
            }
        }
    }
}
