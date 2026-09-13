package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Web seam over core:data's jvmShared `MediaDownloadActions`
 * — the two quick-action surfaces the home ViewModel consumes (the shared
 * downloaded-ids flow that flips a card's DOWNLOAD slot to REMOVE_DOWNLOAD,
 * and the delete routing behind the offline home's quick-action menu). The
 * class's constructor closure reaches the JVM download pipeline, so
 * commonMain cannot name it. QuickDownloadActions template: the interface
 * carries exactly the host-facing surface, the jvmShared actual delegates to
 * the process-wide `MediaDownloadActions` single (same shared downloadedIds
 * flow — android/desktop behavior unchanged), and the wasmJs actual is an
 * honest no-op.
 *
 * Web behavior: the browser has no local download pipeline, so the wasm
 * actual reports [isSupported] = false — hosts should hide/disable the
 * download CTAs — while [downloadedIds] stays empty and [removeDownload] is
 * inert.
 */
interface HomeDownloadActions {

    /** Whether this platform has a download pipeline; gates download CTAs. */
    val isSupported: Boolean

    /**
     * Ids whose quick actions flip to "Remove download" — completed downloads
     * ∪ series ids (the union contract on DownloadRepository).
     */
    val downloadedIds: StateFlow<Set<String>>

    /** Remove [item]'s local download (artifacts + offline rows). Fire-and-forget. */
    fun removeDownload(item: MediaItem)
}

/**
 * Web seam over core:data's jvmShared `DownloadRepository` —
 * the single read the series download sheet makes (which episodes of a
 * series are already downloaded, so the sheet pre-checks its rows). The
 * repository's constructor closure is the JVM download engine, so commonMain
 * cannot name the class. Same template: jvmShared adapter over the real
 * single, honest empty read on web (nothing was ever downloaded in this
 * browser — never fabricated ids).
 */
interface SeriesEpisodeDownloads {

    /** The ids of [seriesId]'s episodes with a completed local download. */
    suspend fun downloadedEpisodeIds(seriesId: String): Set<String>
}
