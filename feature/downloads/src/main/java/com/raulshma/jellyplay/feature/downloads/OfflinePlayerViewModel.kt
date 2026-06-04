package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    val preferencesStore: UserPreferencesStore,
) : JellyPlayViewModel()
