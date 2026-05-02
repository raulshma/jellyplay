package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.raulshma.jellyplay.core.model.DownloadItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor() : ViewModel() {
    var downloads by mutableStateOf<List<DownloadItem>>(emptyList())
        private set
}
