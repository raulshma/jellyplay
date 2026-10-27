package com.raulshma.jellyplay.core.data.download

import android.content.Context
import com.raulshma.jellyplay.shared.core.data.R
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore

// DownloadIntake (the interface) + DownloadRequestResult live in shared/core/data
// commonMain — same package, re-exported here via the module's api(...) dependency.
// So does the shared body (DownloadIntakeBody, jvmShared) — the I1 hand-twin
// fold. This file keeps only the Android construction: the localized no-source
// error.

class DownloadIntakeImpl(
    context: Context,
    delegate: DownloadDelegate,
    downloadRepository: DownloadRepository,
    policyStore: SeriesDownloadPolicyStore,
    detailFeed: DetailFeed,
    downloadsStore: DownloadsStore,
) : DownloadIntakeBody(
    delegate = delegate,
    downloadRepository = downloadRepository,
    policyStore = policyStore,
    detailFeed = detailFeed,
    downloadsStore = downloadsStore,
    noSourceError = { context.getString(R.string.data_no_media_source_download) },
)
