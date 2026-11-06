package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.PerformanceConfigResolver
import com.raulshma.jellyplay.core.model.performanceOverrides
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PerformanceOverrides
import com.raulshma.jellyplay.core.model.ResolvedPerformanceConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The Performance hub's combined UI state: the two owning stores' slices
 * (the appearance-side level + override rows, the playback-side buffer rows)
 * plus the folded posture ([PerformanceConfigResolver]) the tri-state rows'
 * "Auto (level → effective)" subtitles read. The fold is pure derived data —
 * recomputed per emission, never persisted (the resolver contract).
 */
@Immutable
data class PerformanceHubState(
    val appearance: AppearanceSlice = AppearanceSlice(),
    val playback: PlaybackSlice = PlaybackSlice(),
    /** The active level's folded posture (overrides included, reduce-motion folded). */
    val resolved: ResolvedPerformanceConfig =
        PerformanceConfigResolver.resolve(PerformanceLevel.STANDARD, reduceMotionEnabled = false, overrides = PerformanceOverrides()),
)

/**
 * The Performance hub's ViewModel: reads the appearance + playback lanes
 * (the stores inject directly — the hub's fields live on their slices, not on
 * any screen aggregate), folds the posture through
 * [PerformanceConfigResolver], and writes through the single
 * [SettingsSectionViewModel.edit] command
 * (`edit { scope -> scope.appearance.setX(...) }` /
 * `scope.playback.setX(...)`).
 */
class PerformanceSettingsViewModel(
    appearanceStore: AppearanceStore,
    playbackStore: PlaybackStore,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** The combined hub state — one recomposition lane for both owning stores. */
    val state: StateFlow<PerformanceHubState> =
        combine(appearanceStore.appearance, playbackStore.playback) { appearance, playback ->
            PerformanceHubState(
                appearance = appearance,
                playback = playback,
                resolved = PerformanceConfigResolver.resolve(
                    level = appearance.performanceLevel,
                    reduceMotionEnabled = appearance.reduceMotionEnabled,
                    overrides = appearance.performanceOverrides(),
                ),
            )
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), PerformanceHubState())
}
