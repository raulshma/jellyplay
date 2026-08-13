package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRequest(
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val mediaSourceId: String,
    val downloadUrl: String,
    val imageUrl: String,
    val imageBlurHash: String?,
    val trickplayInfo: com.raulshma.jellyplay.core.model.TrickplayInfo? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    // Full server detail for the item being downloaded. Persisted into the
    // offline store so the redesigned offline detail screens can show cast,
    // studios, ratings, overview, etc. — instead of the bare stub used before.
    val detail: MediaDetail? = null,
    // Original container format from the Jellyfin MediaSource ("mkv", "mp4",
    // "ts", ...). Used to derive the on-disk file extension and to attach the
    // correct MIME type to the player engine at playback time.
    val container: String? = null,
)

data class DownloadResult(
    val downloadItem: DownloadItem?,
    val error: String?,
)

@Singleton
class DownloadDelegate @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    // Narrowed to the write surface only — pause/resume/cancel, series-batch
    // orchestration, and status queries live on DownloadRepository but the
    // per-item recipe never touches them. Depending on OfflineDownloadWriter
    // (not the 25-method DownloadRepository) keeps this module's seam honest
    // and lets the recipe be tested with a fake writer.
    private val writer: OfflineDownloadWriter,
    private val playbackRepository: PlaybackRepository,
) {

    /**
     * Builds a [DownloadRequest] from [detail]. [maxBitrate] (bits per second)
     * is applied to the stream URL so the server transcodes to the user's
     * chosen download quality; pass null for original quality.
     */
    suspend fun prepareDownloadRequest(
        detail: MediaDetail,
        maxBitrate: Int? = null,
    ): DownloadRequest? {
        val item = detail.item
        val source = detail.mediaSources.firstOrNull() ?: return null
        val streamUrl = playbackRepository.getStreamUrl(
            itemId = item.id,
            mediaSourceId = source.id,
            maxBitrate = maxBitrate,
        )
        if (streamUrl.isBlank()) return null
        val imageUrl = playbackRepository.getImageUrl(item.id, maxWidth = 300)
        val mediaType = when (item.mediaType) {
            MediaType.AUDIO, MediaType.MUSIC -> MediaType.AUDIO.name
            else -> item.mediaType.name
        }
        return DownloadRequest(
            mediaItemId = item.id,
            name = item.name,
            mediaType = mediaType,
            mediaSourceId = source.id,
            downloadUrl = streamUrl,
            imageUrl = imageUrl,
            imageBlurHash = item.blurHashes.primary,
            trickplayInfo = source.trickplayInfo,
            mediaStreams = source.mediaStreams,
            detail = detail,
            container = source.container,
        )
    }

    /**
     * The single per-item download recipe — prepare + execute in one call.
     *
     * **Why this exists.** Single-item intake (`DownloadIntake.start`) and the
     * per-episode loop inside `DownloadRepositoryImpl.downloadSeries` both need
     * the same "build a request, run it" sequence. Previously each inlined
     * `prepareDownloadRequest` + `executeDownload`, kept in sync only by
     * comments — a future change to one (a new pre-step, a retry) would silently
     * miss the other. Folding the recipe here means both paths call the same
     * code; the only thing they disagree on (the budget hint) is a parameter.
     *
     * Returns null when [prepareDownloadRequest] yields no request (no media
     * source / blank URL) so callers can decide whether that's an error
     * (single-item intake reports it) or a skip (series loop filters it out).
     */
    suspend fun startOne(
        detail: MediaDetail,
        maxBitrate: Int? = null,
        precomputedCurrentBytes: Long? = null,
    ): DownloadResult? {
        val request = prepareDownloadRequest(detail, maxBitrate) ?: return null
        return executeDownload(request, precomputedCurrentBytes)
    }

    suspend fun executeDownload(
        request: DownloadRequest,
        /**
         * Pre-fetched `SUM(downloadedBytes)` to skip the per-call budget query.
         * Series batches pass the value computed once up-front (see
         * `DownloadRepositoryImpl.downloadSeries`); single-item callers leave
         * this null and let `startDownload` query the DAO normally.
         */
        precomputedCurrentBytes: Long? = null,
    ): DownloadResult {
        // For episodes, propagate the parent series/season ids so the downloads
        // row is linked to its series. Without these, deleteOfflineSeries
        // (WHERE seriesId = :seriesId) finds no rows and leaves episode files +
        // download rows orphaned behind a deleted series.
        val detailItem = request.detail?.item
        val isEpisode = detailItem?.mediaType == MediaType.EPISODE
        val result = writer.startDownload(
            mediaItemId = request.mediaItemId,
            name = request.name,
            mediaType = request.mediaType,
            mediaSourceId = request.mediaSourceId,
            downloadUrl = request.downloadUrl,
            imageUrl = request.imageUrl,
            imageBlurHash = request.imageBlurHash,
            container = request.container,
            // detailItem is guaranteed non-null when isEpisode is true.
            seriesId = if (isEpisode && detailItem != null) detailItem.seriesId else null,
            seasonId = if (isEpisode && detailItem != null) detailItem.seasonId else null,
            seriesName = if (isEpisode && detailItem != null) detailItem.seriesName else null,
            seasonName = if (isEpisode && detailItem != null) detailItem.seasonName else null,
            episodeNumber = if (isEpisode && detailItem != null) detailItem.episodeNumber else null,
            seasonNumber = if (isEpisode && detailItem != null) detailItem.seasonNumber else null,
            precomputedCurrentBytes = precomputedCurrentBytes,
        )
        return result.fold(
            onSuccess = { downloadItem ->
                if (downloadItem.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                    writer.enqueueDownload(downloadItem.id)
                    try {
                        // Download poster + backdrop to local files so they render
                        // offline; fall back to the remote URL if a download fails.
                        // Filenames are keyed by mediaItemId so items sharing the
                        // flat downloads dir don't overwrite each other.
                        //
                        // Episodes persist NO backdrop of their own: Jellyfin
                        // usually has no Backdrop image for an episode, so the
                        // download 404s and the persisted path falls back to a
                        // remote URL that only renders offline when Coil's cache
                        // happens to hold it. The online detail screen resolves
                        // episode heroes to the SERIES backdrop instead, so the
                        // offline row leaves backdropPath null and
                        // OfflineRepositoryImpl.getOfflineDetail substitutes the
                        // series' local backdrop at load time (the same result
                        // with a deterministic local file behind it).
                        val parentDir = java.io.File(downloadItem.downloadPath).parentFile
                        val localPoster = if (parentDir != null) {
                            writer.downloadOfflineImage(
                                request.mediaItemId, "Primary", 300, parentDir,
                                com.raulshma.jellyplay.core.data.repository.DownloadArtifacts.posterFile(request.mediaItemId),
                            ) ?: request.imageUrl
                        } else {
                            request.imageUrl
                        }
                        val localBackdrop = if (!isEpisode && parentDir != null) {
                            val backdropUrl = playbackRepository.getBackdropUrl(request.mediaItemId, maxWidth = 1280)
                            writer.downloadOfflineImage(
                                request.mediaItemId, "Backdrop", 1280, parentDir,
                                com.raulshma.jellyplay.core.data.repository.DownloadArtifacts.backdropFile(request.mediaItemId),
                            ) ?: backdropUrl
                        } else {
                            null
                        }
                        // Persist full metadata when the originating MediaDetail
                        // is available (overview, cast, studios, ratings, …);
                        // otherwise fall back to the minimal item path so we
                        // never leave the download without an offline row.
                        val detail = request.detail
                        // Download up to 10 cast/person images to disk (keyed by
                        // personId) so the offline detail cast row renders without
                        // network even after Coil's memory cache evicts the entries
                        // (memory pressure, app restart, or rows downloaded before
                        // cast preloading existed). Best-effort: failures are
                        // swallowed per-image and never block the download. The
                        // shared flat downloads dir + personId-keyed filenames mean
                        // the same image satisfies every item referencing that id.
                        if (detail != null && parentDir != null) {
                            detail.people
                                .filter { it.hasCastImage() }
                                .take(CAST_IMAGE_MAX_COUNT)
                                .forEach { person ->
                                    try {
                                        writer.downloadOfflineImage(
                                            person.id, "Primary", CAST_IMAGE_WIDTH, parentDir,
                                            com.raulshma.jellyplay.core.data.repository.DownloadArtifacts.personImageFile(person.id),
                                        )
                                    } catch (_: Exception) {
                                        // Best-effort: a failed cast image must not
                                        // abort the download or other metadata writes.
                                    }
                                }
                        }
                        if (detail != null) {
                            writer.saveOfflineMediaDetail(
                                detail,
                                localPoster,
                                localBackdrop,
                            )
                        } else {
                            val minimalItem = com.raulshma.jellyplay.core.model.MediaItem(
                                id = request.mediaItemId,
                                name = request.name,
                                mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE,
                            )
                            writer.saveOfflineMediaItem(
                                minimalItem,
                                localPoster,
                                localBackdrop,
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("DownloadDelegate", "Failed to persist offline images for ${request.mediaItemId}", e)
                    }
                    request.trickplayInfo?.let { info ->
                        try {
                            if (!writer.downloadTrickplayData(request.mediaItemId, info, downloadItem.downloadPath)) {
                                android.util.Log.d("DownloadDelegate", "Trickplay sync failed for ${request.mediaItemId}")
                            }
                        } catch (_: Exception) {}
                    }
                    // Bundle external subtitles + intro/outro segments for offline use.
                    if (request.mediaStreams.isNotEmpty()) {
                        try {
                            if (!writer.downloadExternalSubtitles(
                                    request.mediaItemId,
                                    request.mediaSourceId,
                                    request.mediaStreams,
                                    downloadItem.downloadPath,
                                )
                            ) {
                                android.util.Log.d("DownloadDelegate", "Subtitles sync failed for ${request.mediaItemId}")
                            }
                        } catch (_: Exception) {}
                    }
                    try {
                        if (!writer.downloadMediaSegments(request.mediaItemId, downloadItem.downloadPath)) {
                            android.util.Log.d("DownloadDelegate", "Segments sync failed for ${request.mediaItemId}")
                        }
                    } catch (_: Exception) {}
                }
                DownloadResult(downloadItem = downloadItem, error = null)
            },
            onFailure = { error ->
                DownloadResult(downloadItem = null, error = error.message ?: "Download failed")
            },
        )
    }

    private companion object {
        // Max number of cast/person images persisted to disk per item. Caps the
        // per-download image-fetch cost; the cast row rarely needs more than the
        // top-billed handful, and the rest fall back to the remote URL/blurhash.
        const val CAST_IMAGE_MAX_COUNT = 10

        // Pixel width requested for each cast portrait. Cast thumbnails render
        // small, so 200px is ample and keeps disk + bandwidth low (matches the
        // width the online cast row requests via ImageUrlProvider).
        const val CAST_IMAGE_WIDTH = 200
    }
}
