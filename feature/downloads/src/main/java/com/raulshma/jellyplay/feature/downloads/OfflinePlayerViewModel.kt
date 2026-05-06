package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.ViewModel
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    val preferencesStore: UserPreferencesStore,
) : ViewModel()
