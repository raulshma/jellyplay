package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.model.DownloadItem
import kotlinx.coroutines.flow.Flow

/**
 * Web seam over core:data's jvmShared `DownloadRepository` —
 * the audio player reads exactly two of its operations (the now-playing
 * track's download status flow and the delete command behind the
 * remove-download flip), so commonMain cannot name the class whose
 * constructor closure reaches the JVM download pipeline.
 * QuickDownloadActions template: the interface carries exactly the
 * host-facing surface, the jvmShared actual delegates to the process-wide
 * `DownloadRepository` single (same DI graph, android/desktop behavior
 * unchanged), and the wasmJs actual is an honest no-op.
 *
 * Web behavior: the browser has no local download pipeline, so the wasm
 * actual reports [isSupported] = false — the screen hides the download CTA
 * while [trackStatus] stays empty and [remove] is inert.
 */
interface AudioTrackDownloads {

    /** Whether this platform has a download pipeline; gates the download CTA. */
    val isSupported: Boolean

    /** Status of [itemId]'s download (empty flow when none — see DownloadRepository). */
    fun trackStatus(itemId: String): Flow<DownloadItem?>

    /** Removes [downloadId]'s local download (artifacts + offline rows). */
    suspend fun remove(downloadId: String): Result<Unit>
}
