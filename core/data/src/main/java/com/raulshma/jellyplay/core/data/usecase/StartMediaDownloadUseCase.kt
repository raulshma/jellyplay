package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.MediaDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartMediaDownloadUseCase @Inject constructor(
    private val downloadDelegate: DownloadDelegate,
) {
    suspend operator fun invoke(detail: MediaDetail): DownloadResult {
        val request = downloadDelegate.prepareDownloadRequest(detail) ?: return DownloadResult(
            downloadItem = null,
            error = "No media source available for download",
        )
        return downloadDelegate.executeDownload(request)
    }
}
