package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AppearanceSettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    /**
     * The appearance store — backs the Performance group's master-level quick
     * picker (the level lives on the store slice, not on this screen's
     * aggregate). Nullable-with-default keeps the direct-construction test
     * harnesses compiling; the Koin factory resolves the real single and the
     * flow always carries the live level there.
     */
    appearanceStore: AppearanceStore? = null,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Appearance-screen slice — recomposes this screen only on appearance-field writes. */
    val preferences: StateFlow<AppearanceScreenPreferences> = projections.appearanceScreenPreferences

    /** Navigation-customization slice, consumed by the embedded `NavigationCustomizationGroup`. */
    val navigationCustomizationPreferences: StateFlow<NavigationCustomizationPreferences> =
        projections.navigationCustomizationPreferences

    /**
     * The master performance level — the Performance group's quick picker
     * face ([AppearanceScreenPreferences.performanceMode] is only its
     * PERFORMANCE-derived projection, so it cannot distinguish
     * STANDARD/BALANCED). Falls back to a constant STANDARD where no store
     * is bound (direct-construction harnesses).
     */
    val performanceLevel: StateFlow<PerformanceLevel> =
        appearanceStore?.appearance
            ?.map { it.performanceLevel }
            ?.stateIn(scope, SharingStarted.WhileSubscribed(5_000), PerformanceLevel.STANDARD)
            ?: kotlinx.coroutines.flow.MutableStateFlow(PerformanceLevel.STANDARD)
}
