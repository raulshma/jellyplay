package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Snapshot of the user-tunable home-screen layout. Captured by "Home Layout
 * Presets" so a configuration can be saved, restored, shared and reset
 * independently of the rest of the app preferences.
 */
@Immutable
@Serializable
data class HomeLayoutConfig(
    val enabledHomeSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val mergeContinueWatchingAndNextUp: Boolean = false,
    val nextUpMaxDays: Int = 0,
    val nextUpRewatching: Boolean = false,
    val pinnedHomeSections: List<PinnedHomeSection> = emptyList(),
    val homeHeroEnabled: Boolean = true,
    val continueWatchingClickBehavior: ContinueWatchingClickBehavior = ContinueWatchingClickBehavior.DETAILS,
) {
    companion object {
        /** The factory-default home layout (also used by "Reset"). */
        val DEFAULT = HomeLayoutConfig()
    }
}

/**
 * A named, persisted home layout. [id] is a stable key for list rendering and
 * deletion; [createdAt] is epoch millis for display sorting.
 */
@Immutable
@Serializable
data class HomeLayoutPreset(
    val id: String,
    val name: String,
    val config: HomeLayoutConfig,
    val createdAt: Long = wallNowMillis(),
)
