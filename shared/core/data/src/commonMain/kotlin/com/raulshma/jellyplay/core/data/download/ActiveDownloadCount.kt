package com.raulshma.jellyplay.core.data.download

import kotlinx.coroutines.flow.Flow

/**
 * The live count of in-flight (PENDING/QUEUED/DOWNLOADING) downloads — the
 * music home screen's transfer badge. The one read of the former
 * feature-local MusicTrackDownloads seam that did not fold onto
 * [TrackDownloadStatusWindow]: a badge is process-scoped (it counts the WHOLE
 * pipeline), not an id-scoped row window, so it is neither a
 * `downloadsFor(ids)` shape nor a remove command.
 *
 * The natural source — core:data's jvmShared `DownloadRepository`
 * ([`getActiveDownloadCount`][com.raulshma.jellyplay.core.data.repository.DownloadRepository.getActiveDownloadCount])
 * — is invisible to feature commonMain (its constructor closure is the JVM
 * download engine), which is exactly why this interface exists: the
 * wall-crossing seam is declared, implemented and bound by core:data on both
 * platforms rather than growing another feature-local template. Since the
 * promoted-interface pass the JVM actual is the repository itself —
 * jvmShared `DownloadRepositoryImpl` implements this interface directly and
 * dataJvmModule binds it over the repository single.
 */
interface ActiveDownloadCount {

    /** Live count of in-flight downloads (the music-home badge). */
    fun activeDownloadCount(): Flow<Int>
}
