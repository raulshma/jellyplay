package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore

/**
 * Desktop actual of the [DownloadIntake] seam (V3 downloads conveyor): a bare
 * constructor over the shared jvmShared body ([DownloadIntakeBody] — the I1
 * fold of the former line-for-line twins). The one platform delta is the
 * no-source error string: desktop emits a pre-resolved base-locale literal
 * below, byte-matching Android's `R.string.data_no_media_source_download`
 * base locale. This is an accepted desktop locale delta, not a pending item:
 * the intake's consumers (details/player-audio/music message seams) all carry
 * pre-resolved strings and core:data has no compose-resource access (no
 * resources source set exists in this module — the literal should ride
 * composeResources if one ever lands, same as
 * [com.raulshma.jellyplay.core.data.repository.DesktopAdminStatisticsLabels]),
 * so localizing one desktop-only sentinel would require sentinel-matching
 * translation plumbing across those seams.
 *
 * Series batches go through DownloadRepository.downloadSeries; on desktop
 * that path is fully live — MediaRepositoryAccess is real (Koin owns
 * MediaRepositoryImpl on desktop too, see desktopDataModule), so series
 * downloads and the auto-download loop work end-to-end. Single-item downloads
 * likewise work end-to-end — for episodes, any missing series metadata
 * degrades to the minimal parent-row fallback (see saveOfflineMediaItem's
 * runCatching).
 */
class DesktopDownloadIntake(
    delegate: DownloadDelegate,
    downloadRepository: DownloadRepository,
    mediaRepository: MediaRepository,
    downloadsStore: DownloadsStore,
) : DownloadIntakeBody(
    delegate = delegate,
    downloadRepository = downloadRepository,
    mediaRepository = mediaRepository,
    downloadsStore = downloadsStore,
    // Same copy as the Android R.string.data_no_media_source_download.
    noSourceError = { "No media source available for download" },
)
