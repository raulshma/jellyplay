package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.LocalSeriesAggregate
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.model.toMediaDetail
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * The content-resolution collaborator of [UnifiedMediaDetailProviderImpl]:
 * owns the remote/local resolution ladder — the offline/reconnect dispatch
 * decision, the remote fetch (detail + series + album fan-out), the local
 * publication (probed streams, subtitles, offline assets/aggregate) and the
 * per-file stream-probe memo. Extracted verbatim from the provider's Session
 * extension functions so the session stays a lifecycle/rewrite machine
 * (refcount, scopes, attachment/snapshot combining, optimistic rewrites) and
 * the WHAT of resolution lives here.
 *
 * Constructed ONE PER SESSION (inside the provider's `Session`): the
 * [probedStreamsCache] memo's scope is the session — "probes once per file
 * version per session" — so a shared provider-level instance would leak a
 * stale memo across sessions. The collaborators are the provider's own ctor
 * dependencies, handed through unchanged.
 *
 * Threading contract: [resolve] is called under the session's `resolveMutex`
 * (the session still owns the lock), so a resolution cannot race the session's
 * optimistic rewrites — the same serialization the pre-extraction bodies ran
 * under.
 */
internal class DetailContentResolver(
    private val itemId: String,
    private val mediaRepository: MediaRepository,
    /** The per-type "which caches does this detail affect" dispatch (plan 08). */
    private val cacheInvalidation: MediaRepositoryCacheInvalidation,
    private val offlineRepository: OfflineRepository,
    private val downloadRepository: DownloadRepository,
    private val episodeCatalogue: EpisodeCatalogue,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val localStreamProbe: LocalStreamProbe,
) {

    // Probe cache: (file lastModified millis → streams). Re-probe only when
    // the file changes; stable across re-resolves (refresh/expand) so opening
    // the detail screen probes once per file version per session. The
    // per-session resolver instance (not a provider-level one) is what keeps
    // that scope — the memo must not outlive its session.
    @Volatile private var probedStreamsCache: Pair<Long, List<MediaStream>>? = null

    /**
     * The offline/reconnect dispatch decision (the former `Session.resolveFor`
     * body, verbatim): online keeps a current remote resolution through blips
     * and otherwise re-resolves remote ([force] = drop-and-refetch); any other
     * mode resolves local. Returns the new resolution to publish into the
     * session's content flow, or null when the current one should be KEPT
     * (the `alreadyRemote` branch — a blip must not blank an open detail).
     */
    suspend fun resolve(
        mode: OfflineMode,
        force: Boolean,
        targetGen: Long,
        current: ContentResolution,
    ): ContentResolution? {
        if (mode == OfflineMode.ONLINE) {
            val alreadyRemote = current is ContentResolution.Resolved &&
                current.origin == DetailOrigin.REMOTE &&
                current.contentGen == targetGen &&
                !force
            return when {
                force -> resolveRemote(targetGen, force = true)
                alreadyRemote -> null // keep the remote resolution through blips
                else -> resolveRemote(targetGen, force = false)
            }
        }
        return resolveLocal(targetGen, DetailOrigin.LOCAL_OFFLINE_MODE)
    }

    private suspend fun resolveRemote(targetGen: Long, force: Boolean): ContentResolution =
        // Force read: the repository drops the item's cached detail before the
        // fetch (plan 08's freshness lever), then the per-type dispatch below
        // drops the type-scoped caches (series catalogue, album tracks,
        // collection items) the snapshot derivation reads right after.
        coroutineScope {
            // resolveUsableDownload needs only itemId — start it before the
            // detail fetch so the Room read overlaps the network round-trip.
            val usableDeferred = async { playbackSourceResolver.resolveUsableDownload(itemId) != null }
            val result = mediaRepository.getMediaDetail(itemId, force = force)
            result.fold(
                onSuccess = { detail ->
                    if (force) cacheInvalidation.invalidateFor(detail)
                    val seriesDeferred = async { loadSeriesData(detail, offline = false) }
                    val albumDeferred = async { loadAlbumTracks(detail, offline = false) }
                    val usable = usableDeferred.await()
                    val seriesData = seriesDeferred.await()
                    val album = albumDeferred.await()
                    ContentResolution.Resolved(
                        contentGen = targetGen,
                        origin = DetailOrigin.REMOTE,
                        detail = detail,
                        seasons = seriesData.seasons,
                        episodesBySeason = seriesData.episodesBySeason,
                        fetchedSeasonIds = seriesData.fetchedSeasonIds,
                        sortedEpisodes = seriesData.sortedEpisodes,
                        albumTracks = album,
                        localSubtitles = emptyList(),
                        assets = DetailAssets(),
                        seriesAggregate = null,
                        confirmedUsable = usable,
                    )
                },
                onFailure = { err ->
                    val local = offlineRepository.getOfflineDetail(itemId).first()
                    if (local != null) {
                        publishLocal(targetGen, DetailOrigin.LOCAL_REMOTE_FAILURE, local)
                    } else {
                        val accessDenied = (err as? ApiException)?.isAccessDenied == true
                        ContentResolution.Failed(
                            contentGen = targetGen,
                            error = DetailLoadError(
                                message = err.message ?: "Failed to load details",
                                isAccessDenied = accessDenied,
                            ),
                        )
                    }
                },
            )
        }

    private suspend fun resolveLocal(targetGen: Long, origin: DetailOrigin): ContentResolution {
        val local = offlineRepository.getOfflineDetail(itemId).first()
        if (local != null) {
            return publishLocal(targetGen, origin, local)
        }
        return ContentResolution.Failed(
            contentGen = targetGen,
            error = DetailLoadError(
                message = "Unavailable offline",
                isUnavailableOffline = true,
            ),
        )
    }

    /**
     * Pure file-stat read — non-suspend on purpose (the ratchet keeps bare
     * runCatching out of suspend bodies); `-1L` mirrors File.lastModified's
     * own error contract for a failed stat.
     */
    private fun fileMtime(path: String): Long =
        runCatching { java.io.File(path).lastModified() }.getOrDefault(-1L)

    /**
     * Probes the downloaded file's audio/video tracks, memoized per file
     * `lastModified` so re-resolves (refresh, expand) don't re-probe an
     * unchanged file. Returns `emptyList()` when there is no path or the probe
     * fails — the caller then skips synthesizing a media source.
     */
    private suspend fun probeStreamInfo(downloadPath: String?): List<MediaStream> {
        if (downloadPath.isNullOrEmpty()) return emptyList()
        val mtime = fileMtime(downloadPath)
        probedStreamsCache?.let { (cachedMtime, cached) ->
            if (cachedMtime == mtime && mtime >= 0L) return cached
        }
        val streams = localStreamProbe.probe(downloadPath)
        if (mtime >= 0L) probedStreamsCache = mtime to streams
        return streams
    }

    private suspend fun publishLocal(
        targetGen: Long,
        origin: DetailOrigin,
        local: OfflineMediaItem,
    ): ContentResolution {
        val detail = local.toMediaDetail()
        // Probe the actual downloaded file for its real audio/video tracks.
        // Server metadata is unreliable here: a transcoded download bakes a
        // different track set than the source. Only the file is authoritative,
        // and the probe is the same ground truth the player uses at playback.
        val probedStreams = probeStreamInfo(local.downloadPath)
        val detailWithStreams = if (probedStreams.isEmpty()) {
            detail
        } else {
            detail.copy(mediaSources = listOf(
                MediaSource(
                    id = LOCAL_SOURCE_ID,
                    name = "Local",
                    mediaStreams = probedStreams,
                ),
            ))
        }
        val usable = playbackSourceResolver.resolveUsableDownload(itemId) != null
        val seriesData = loadSeriesData(detail, offline = true)
        val album = loadAlbumTracks(detail, offline = true)
        val subtitles = loadLocalSubtitles(itemId, local.downloadPath)
        // One-shot read of the local series' episodes: the catalogue's
        // [MediaItem] projection drops `posterPath` and `totalSizeBytes`
        // (storage concerns), so the aggregate header AND the per-episode
        // artwork map are derived from the raw offline rows in a single pass.
        val seriesEpisodes: List<OfflineMediaItem> = detail.item.seriesIdForDetail
            ?.let { localSeriesEpisodes(it) }
            .orEmpty()
        val assets = DetailAssets(
            posterPath = local.posterPath,
            backdropPath = local.backdropPath,
            castImages = local.cast
                .mapNotNull { p -> p.localImagePath?.let { p.id to it } }
                .toMap(),
            episodeImages = seriesEpisodes
                .mapNotNull { e -> e.posterPath?.let { e.id to it } }
                .toMap(),
        )
        val aggregate = if (detail.item.mediaType == MediaType.SERIES) {
            LocalSeriesAggregate(
                downloadedEpisodeCount = seriesEpisodes.size,
                totalSizeBytes = seriesEpisodes.sumOf { it.totalSizeBytes },
                episodeSizeBytes = seriesEpisodes.associate { it.id to it.totalSizeBytes },
            )
        } else {
            null
        }
        return ContentResolution.Resolved(
            contentGen = targetGen,
            origin = origin,
            detail = detailWithStreams,
            seasons = seriesData.seasons,
            episodesBySeason = seriesData.episodesBySeason,
            fetchedSeasonIds = seriesData.fetchedSeasonIds,
            sortedEpisodes = seriesData.sortedEpisodes,
            albumTracks = album,
            localSubtitles = subtitles,
            assets = assets,
            seriesAggregate = aggregate,
            confirmedUsable = usable,
        )
    }

    /**
     * Loads seasons/episodes through the shared [EpisodeCatalogue] regardless of
     * source — the anti-fork point. For an episode, loads its parent series so
     * the seasons UI has context.
     */
    private suspend fun loadSeriesData(
        detail: MediaDetail,
        offline: Boolean,
    ): SeriesData {
        val item = detail.item
        val seriesId = item.seriesIdForDetail ?: return SeriesData.EMPTY
        val snapshot: EpisodeCatalogueSnapshot = episodeCatalogue
            .loadSeriesEpisodes(seriesId, offline = offline)
            .getOrNull()
            ?: EpisodeCatalogueSnapshot.empty(seriesId)
        return SeriesData(
            seasons = snapshot.seasons,
            episodesBySeason = snapshot.episodesBySeason,
            fetchedSeasonIds = snapshot.fetchedSeasonIds,
            sortedEpisodes = snapshot.sortedEpisodes,
        )
    }

    private suspend fun loadAlbumTracks(detail: MediaDetail, offline: Boolean): List<MediaItem> {
        if (detail.item.mediaType != MediaType.ALBUM) return emptyList()
        return if (offline) {
            offlineRepository.getChildren(detail.item.id).first().map { it.toMediaItem() }
        } else {
            mediaRepository.getAlbumTracks(detail.item.id).getOrDefault(emptyList())
        }
    }

    private suspend fun loadLocalSubtitles(itemId: String, downloadPath: String?): List<LocalSubtitleOption> {
        if (downloadPath == null) return emptyList()
        val manifest = downloadRepository.loadLocalSubtitleManifest(downloadPath, itemId) ?: return emptyList()
        // The persisted manifest drops the SDH flag and carries no audio inventory;
        // expose only manifest-backed external subtitle entries.
        return manifest.subtitles.map { entry ->
            LocalSubtitleOption(
                index = entry.index,
                fileName = entry.fileName,
                displayTitle = entry.displayTitle ?: entry.title,
                language = entry.language,
                isDefault = entry.isDefault,
                isForced = entry.isForced,
            )
        }
    }

    /**
     * Flattens every episode across a local series's seasons (single one-shot
     * Room read, season/index ordered). Used to derive both the aggregate
     * header and the per-episode artwork map from a single pass over the
     * offline rows — the catalogue's [MediaItem] projection drops
     * `posterPath` / `totalSizeBytes`.
     */
    private suspend fun localSeriesEpisodes(seriesId: String): List<OfflineMediaItem> =
        offlineRepository.getEpisodesForSeries(seriesId)

    private data class SeriesData(
        val seasons: List<MediaItem>,
        val episodesBySeason: Map<String, List<MediaItem>>,
        val fetchedSeasonIds: Set<String>,
        val sortedEpisodes: List<MediaItem>,
    ) {
        companion object {
            val EMPTY = SeriesData(emptyList(), emptyMap(), emptySet(), emptyList())
        }
    }

    private companion object {
        // Synthesized MediaSource id for the probed local file's track inventory.
        const val LOCAL_SOURCE_ID = "local"
    }
}

/**
 * The session's content state machine value — produced by [DetailContentResolver],
 * held/published by the provider's `Session` (which also rewrites [Resolved]
 * optimistically). Moved verbatim from its former home nested inside
 * [UnifiedMediaDetailProviderImpl]; top-level (module-internal) so both the
 * provider and its resolver collaborator share the one type.
 */
internal sealed interface ContentResolution {
    val contentGen: Long

    data object Initial : ContentResolution {
        override val contentGen: Long get() = -1L
    }

    data class Resolved(
        override val contentGen: Long,
        val origin: DetailOrigin,
        val detail: MediaDetail,
        val seasons: List<MediaItem>,
        val episodesBySeason: Map<String, List<MediaItem>>,
        val fetchedSeasonIds: Set<String>,
        val sortedEpisodes: List<MediaItem>,
        val albumTracks: List<MediaItem>,
        val localSubtitles: List<LocalSubtitleOption>,
        val assets: DetailAssets,
        val seriesAggregate: LocalSeriesAggregate?,
        val confirmedUsable: Boolean,
    ) : ContentResolution

    data class Failed(
        override val contentGen: Long,
        val error: DetailLoadError,
    ) : ContentResolution
}
