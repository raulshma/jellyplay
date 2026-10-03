package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The system-chrome preference aggregates: experimental feature flags and the
 * navigation-customization slice.
 */

/** Fields read by `ExperimentalSettingsScreen`. */
@Immutable
@Serializable
data class ExperimentalPreferences(
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
)

/** Fields read by `NavigationCustomizationGroup`. */
@Immutable
@Serializable
data class NavigationCustomizationPreferences(
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
    val hideBottomNavOnScroll: Boolean = true,
)
