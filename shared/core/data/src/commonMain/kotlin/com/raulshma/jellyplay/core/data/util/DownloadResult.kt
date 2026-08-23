package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.model.DownloadItem

data class DownloadResult(
    val downloadItem: DownloadItem?,
    val error: String?,
)
