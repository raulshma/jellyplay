package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers the `homeBackdropEnabled` setter wiring on
 * [AppearanceSettingsViewModel] — mirroring [LibraryLayoutViewModelTest]'s
 * mockk style — without touching the DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsViewModelHomeBackdropTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var store: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var editor: PreferencesEditor

    @Before
    fun setUp() {
        store = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { projections.appearanceScreenPreferences } returns
            MutableStateFlow(AppearanceScreenPreferences())
        every { projections.navigationCustomizationPreferences } returns
            MutableStateFlow(NavigationCustomizationPreferences())
    }

    @Test
    fun `setHomeBackdropEnabled delegates to editor`() {
        val viewModel = AppearanceSettingsViewModel(store, projections, appearanceStore, editor)

        viewModel.setHomeBackdropEnabled(false)

        verify { editor.setHomeBackdropEnabled(false) }
    }
}
