package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.ActiveDownloadCount
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The download-actions family of the dataJvmModule split — the true
 * download-actions half of the former mixed section (see [dataJvmModule]
 * for the construction-owner rules). Binding bodies moved verbatim from
 * the pre-split single-module layout.
 */
internal val dataDownloadActionsModule: Module = module {
    // ── download-actions seams (promoted core:data interfaces) ───────────
    // The feature-facing download reads, all declared in core:data
    // commonMain and implemented DIRECTLY by this module's engine singles
    // (DownloadRepositoryImpl implements TrackDownloadStatusWindow +
    // ActiveDownloadCount + SeriesEpisodeDownloads + DownloadQueue;
    // OfflineSyncManager implements OfflineResync — the former jvmShared /
    // feature:downloads verbatim-forward adapters are deleted) — features
    // never grow their own wall-crossing template.
    //  - TrackDownloadStatusWindow: the audio player's and the album
    //    screen's row window over the DownloadRepository single (its
    //    downloadsFor IS the single getDownloadsByMediaItemIdsFlow IN-query
    //    — the N per-id-flow divergence of the deleted adapter is reverted);
    //  - ActiveDownloadCount: the music-home transfer badge;
    //  - SeriesEpisodeDownloads: the home series-download sheet's
    //    episode-id read;
    //  - DownloadQueue: the downloads screen's queue reads and transfer
    //    controls;
    //  - OfflineResync: the downloads screen's check-for-updates / resync.
    single<TrackDownloadStatusWindow> { get<DownloadRepositoryImpl>() }
    single<ActiveDownloadCount> { get<DownloadRepositoryImpl>() }
    single<SeriesEpisodeDownloads> { get<DownloadRepositoryImpl>() }
    single<DownloadQueue> { get<DownloadRepositoryImpl>() }
    single<OfflineResync> { get<OfflineSyncManager>() }
}
