package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.HomeLayoutRestoreFetch
import com.raulshma.jellyplay.core.data.repository.HomeLayoutSyncController
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import kotlinx.coroutines.flow.StateFlow

class HomeSettingsViewModel(
    projections: PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val homeLayoutSync: HomeLayoutSyncController,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Home-screen slice — recomposes this screen only on home-discovery writes. */
    val preferences: StateFlow<HomeScreenPreferences> = projections.homeScreenPreferences

    /**
     * The decoded server offer awaiting the restore confirmation dialog — set
     * by [fetchServerLayout] when the server holds a decodable layout, cleared
     * on confirm / dismiss. The dialog's apply runs through
     * `edit { homeDiscovery.applyLayout(offer.config) }`: the SAME command the
     * local preset-restore path uses.
     */
    var pendingRestoreOffer by composeState<HomeLayoutRestoreFetch.Offer?>(null)
        private set

    /**
     * H20 manual upload — pushes the current layout to the server through the
     * sync controller (the same codec the autopush uses). Works regardless of
     * the autopush pref; the result reports success or the transport failure.
     */
    fun uploadLayoutToServer(onResult: (Result<Unit>) -> Unit) {
        launch {
            val result = homeLayoutSync.uploadNow()
            onResult(result)
        }
    }

    /**
     * H20 manual restore, step 1 — fetch + leniently decode the server's saved
     * layout. On an [HomeLayoutRestoreFetch.Offer] the VM retains it in
     * [pendingRestoreOffer] for the confirmation dialog; the caller messages
     * the Empty / Undecodable / failure outcomes.
     */
    fun fetchServerLayout(onResult: (Result<HomeLayoutRestoreFetch>) -> Unit) {
        launch {
            val result = homeLayoutSync.fetchServerLayout()
            pendingRestoreOffer = result.getOrNull() as? HomeLayoutRestoreFetch.Offer
            onResult(result)
        }
    }

    fun clearPendingRestoreOffer() {
        pendingRestoreOffer = null
    }
}
