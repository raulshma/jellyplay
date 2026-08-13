package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.catalogue.sortedByPlaybackOrder
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DownloadAttachment
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.LocalSeriesAggregate
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import com.raulshma.jellyplay.core.model.toMediaDetail
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production adapter for [MediaDetailProvider]. Owns the remote/local source
 * decision and the source-dependent read graph: the projected [MediaDetail],
 * seasons/episodes (always via [EpisodeCatalogue] — the online/offline fork is
 * not recreated one level down), album children, local external subtitles,
 * local presentation artwork, the reactive download/sync attachment, and the
 * capability set derived once per snapshot.
 *
 * Reactivity: a per-item [Session] combines a content resolution (re-resolved
 * on refresh or a relevant connectivity change) with reactive Room attachments
 * (download progress, sync state, local row) so attachment updates re-emit
 * without re-fetching content. Content sections are stable across attachment
 * ticks (same references, same [MediaDetailSnapshot.contentGeneration]) so a
 * consumer's optimistic UI mutations between resolutions are not clobbered.
 *
 * Manual/auto offline mode never issues a server request; a remote failure with
 * a local row falls back in place; reconnect after a fallback retries remote
 * resolution. `NetworkStatus.Local` still permits the remote attempt because
 * `OfflineModeManager` keeps [OfflineMode.ONLINE] on a LAN.
 */
@Singleton
class UnifiedMediaDetailProviderImpl @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val offlineRepository: OfflineRepository,
    private val downloadRepository: DownloadRepository,
    private val episodeCatalogue: EpisodeCatalogue,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val offlineModeManager: OfflineModeManager,
    private val localStreamProbe: LocalStreamProbe,
    @ApplicationScope private val appScope: CoroutineScope,
) : MediaDetailProvider {

    private val sessions = ConcurrentHashMap<String, Session>()
    private val sessionsMutex = Mutex()

    override fun observe(itemId: String): Flow<DetailLoadState> = flow {
        val session = acquireSession(itemId)
        try {
            emitAll(session.snapshots)
        } finally {
            // NonCancellable so a collector cancelled mid-flight (e.g. the VM
            // reusing its scope across navigations) cannot strand the session —
            // refCount must always decrement + destroy() when it hits zero.
            withContext(NonCancellable) { releaseSession(itemId) }
        }
    }

    override suspend fun refresh(itemId: String) {
        // revalidate = false: refresh() forces its own re-resolve below (and
        // awaits it), so the reuse-path revalidation would only duplicate it.
        val session = acquireSession(itemId, revalidate = false)
        try {
            session.refresh()
        } finally {
            releaseSession(itemId)
        }
    }

    override suspend fun applyOptimisticSeasonRewrite(
        itemId: String,
        seasonId: String,
        transform: (List<MediaItem>) -> List<MediaItem>,
    ) {
        val session = sessions[itemId] ?: return
        val seriesIdToInvalidate = session.rewriteSeason(seasonId, transform) ?: return
        // Drop the catalogue cache for re-entry freshness, outside the session
        // resolve lock so a concurrent resolution isn't blocked on the (fast)
        // cache drop. The active session already holds the optimistic snapshot
        // in its content flow, so consumers see the flip via [observe].
        episodeCatalogue.invalidateSeries(seriesIdToInvalidate)
    }

    override suspend fun applyOptimisticItemState(
        itemId: String,
        isFavorite: Boolean?,
        isPlayed: Boolean?,
    ) {
        val session = sessions[itemId]
            ?: sessions.values.firstOrNull { it.containsItem(itemId) }
        session?.rewriteItemState(itemId, isFavorite, isPlayed)
    }

    override suspend fun expandSeason(itemId: String, seasonId: String): List<MediaItem> {
        val session = sessions[itemId] ?: return emptyList()
        return session.expandSeason(seasonId)
    }

    override suspend fun canonicalEpisodeIds(seriesId: String): List<String> {
        // Serve from an active session whose content is loaded for this series
        // (avoids a catalogue round-trip when the series is on screen but the
        // consumer's local snapshot is cold/stale); otherwise cold-load.
        val cached = sessions.values.firstNotNullOfOrNull { session ->
            (session.content.value as? ContentResolution.Resolved)
                ?.takeIf { it.detail.item.seriesIdForDetail == seriesId }
                ?.sortedEpisodes
                ?.takeIf { it.isNotEmpty() }
                ?.map { it.id }
        }
        if (cached != null) return cached
        return episodeCatalogue.loadSeriesEpisodes(seriesId)
            .getOrNull()
            ?.allEpisodeIds
            ?: emptyList()
    }

    override fun invalidate(seriesId: String) {
        episodeCatalogue.invalidateSeries(seriesId)
    }

    /**
     * Resolves the [Session] for [itemId], creating + starting one on first use.
     * On reuse, [revalidate] (default true) bumps the refresh tick so the running
     * [Session.start] collector force-re-resolves instead of replaying the cached
     * snapshot — the DetailViewModel is Activity-scoped (Navigation3 has no
     * per-entry ViewModelStore), so a back→home→detail trip reuses this session,
     * and without revalidation every re-entry would serve stale state. Pass
     * [revalidate] = false when the caller drives its own re-resolve (see
     * [refresh]).
     */
    private suspend fun acquireSession(itemId: String, revalidate: Boolean = true): Session =
        sessionsMutex.withLock {
            val existing = sessions[itemId]
            if (existing != null) {
                existing.acquire()
                if (revalidate) existing.requestRevalidate()
                existing
            } else {
                val session = Session(itemId)
                sessions[itemId] = session
                session.start()
                session.acquire() // count the caller holding this session
                session
            }
        }

    private suspend fun releaseSession(itemId: String) {
        val toRemove = sessionsMutex.withLock {
            val session = sessions[itemId] ?: return
            if (session.release()) {
                sessions.remove(itemId)
                session
            } else {
                null
            }
        }
        toRemove?.destroy()
    }

    private inner class Session(val itemId: String) {
        private val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext[Job]))
        private val resolveMutex = Mutex()
        private val generation = AtomicLong(0L)
        private val refreshTick = MutableStateFlow(0L)
        private val refCount = AtomicLong(0L)
        @Volatile private var started = false
        @Volatile private var lastTick = -1L
        // Probe cache: (file lastModified millis → streams). Re-probe only when
        // the file changes; stable across re-resolves (refresh/expand) so opening
        // the detail screen probes once per file version per session. Not private
        // so the Session extension functions (publishLocal/probeStreamInfo) can
        // touch it — Session itself is a private inner class, so this stays scoped.
        @Volatile var probedStreamsCache: Pair<Long, List<MediaStream>>? = null

        val content: MutableStateFlow<ContentResolution> = MutableStateFlow(ContentResolution.Initial)

        /** Reactive download/sync/local-row attachment for this item. */
        private val attachments: Flow<Attachment> = combine(
            offlineModeManager.offlineMode,
            offlineRepository.getOfflineDetail(itemId),
            downloadRepository.getDownloadByMediaItemIdFlow(itemId),
            offlineRepository.getOfflineSyncState(itemId),
        ) { mode, localItem, download, syncState ->
            Attachment(mode, localItem, download, syncState)
        }.distinctUntilChanged()

        /** Snapshot stream: Loading until first content resolution, then Loaded/Error. */
        val snapshots: Flow<DetailLoadState> = combine(content, attachments) { c, a ->
            buildState(c, a)
        }.distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

        fun acquire() {
            refCount.incrementAndGet()
        }

        fun release(): Boolean {
            val remaining = refCount.decrementAndGet()
            return remaining <= 0
        }

        fun containsItem(targetItemId: String): Boolean {
            val current = content.value as? ContentResolution.Resolved ?: return false
            return current.detail.item.id == targetItemId ||
                current.episodesBySeason.values.any { episodes ->
                    episodes.any { episode -> episode.id == targetItemId }
                }
        }

        fun start() {
            if (started) return
            started = true
            scope.launch {
                combine(refreshTick, offlineModeManager.offlineMode) { tick, mode -> tick to mode }
                    .distinctUntilChanged()
                    .collect { (tick, mode) ->
                        val force = tick != lastTick
                        lastTick = tick
                        resolveFor(mode, force)
                    }
            }
            scope.launch { watchDownloadCompletion() }
        }

        fun destroy() {
            scope.cancel()
        }

        /**
         * Bumps the refresh tick so the running [start] collector runs its next
         * resolve with force=true (drops the detail cache + re-fetches). Returns
         * the new generation. Shared by [requestRevalidate] (fire-and-forget,
         * used on session re-entry) and [refresh] (which additionally awaits the
         * new generation).
         */
        private fun bumpRefreshTick(): Long {
            val gen = generation.incrementAndGet()
            refreshTick.value = gen
            return gen
        }

        /**
         * Fire-and-forget content re-resolve used on session re-entry — see
         * [acquireSession]. Non-suspending: callers (observe) are not delayed;
         * they pick up the fresh emission through [snapshots].
         */
        fun requestRevalidate() {
            bumpRefreshTick()
        }

        /**
         * Forces a content re-resolution and waits for the new generation to land.
         */
        suspend fun refresh() {
            val gen = bumpRefreshTick()
            // Wait for the resolver to publish a resolution for this generation.
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                content.first { it.contentGen >= gen }
            }
        }

        /**
         * Optimistic in-place rewrite of one season's episodes, serialized
         * against [resolveMutex] so it cannot race a concurrent content
         * re-resolution. Mirrors the rewrite through the catalogue to reuse
         * [EpisodeCatalogueSnapshot.withSeasonEpisodes]'s canonical re-sort, then
         * projects the rebuilt sections into this session's content with a
         * bumped content generation. The content flow re-emits, so the consumer
         * adopts the rewritten content sections via its normal reducer path.
         *
         * Returns the series id (so the caller drops the catalogue cache for
         * re-entry freshness), or null if there was nothing to rewrite: session
         * not resolved, no parent series, or the season is absent/empty.
         */
        suspend fun rewriteSeason(
            seasonId: String,
            transform: (List<MediaItem>) -> List<MediaItem>,
        ): String? = resolveMutex.withLock {
            val current = content.value
            if (current !is ContentResolution.Resolved) return@withLock null
            val seriesId = current.detail.item.seriesIdForDetail ?: return@withLock null
            val currentEpisodes = current.episodesBySeason[seasonId]
            if (currentEpisodes.isNullOrEmpty()) return@withLock null
            // updateSeasonEpisodes returns the rebuilt snapshot (sortedEpisodes
            // included); the cache write is undone by the caller's invalidate,
            // so this is effectively using it as a pure rebuild over the
            // canonical re-sort owned by EpisodeCatalogueSnapshot.
            val rebuilt = episodeCatalogue.updateSeasonEpisodes(seriesId, seasonId, transform)
                ?: return@withLock null
            // Optimistically reconcile the series-header watched state from the
            // rebuilt episode set so the header (not just the episode cards)
            // reflects the flip on the current screen. Only when every season is
            // loaded, so a partial fetch can't push a misleading unplayed count.
            // A subsequent re-entry re-resolve (see acquireSession) authoritatively
            // reconciles it regardless.
            val allSeasonsLoaded = rebuilt.seasons.isNotEmpty() &&
                rebuilt.seasons.all { it.id in rebuilt.fetchedSeasonIds }
            val updatedDetail = if (allSeasonsLoaded && rebuilt.sortedEpisodes.isNotEmpty()) {
                val unplayed = rebuilt.sortedEpisodes.count { !it.isPlayed }
                current.detail.copy(
                    item = current.detail.item.copy(
                        isPlayed = unplayed == 0,
                        unplayedItemCount = unplayed,
                    ),
                )
            } else {
                current.detail
            }
            val targetGen = generation.incrementAndGet()
            content.value = current.copy(
                contentGen = targetGen,
                detail = updatedDetail,
                seasons = rebuilt.seasons,
                episodesBySeason = rebuilt.episodesBySeason,
                fetchedSeasonIds = rebuilt.fetchedSeasonIds,
                sortedEpisodes = rebuilt.sortedEpisodes,
            )
            seriesId
        }

        /**
         * Rewrites the targeted detail item or episode projections shown in the
         * same screen. A series played flip cascades to its episodes. Keeping
         * this in the provider means a re-emitted snapshot cannot overwrite the
         * ViewModel's optimistic state with the pre-action content.
         */
        suspend fun rewriteItemState(
            itemId: String,
            isFavorite: Boolean?,
            isPlayed: Boolean?,
        ) = resolveMutex.withLock {
            val current = content.value as? ContentResolution.Resolved ?: return@withLock
            if (isFavorite == null && isPlayed == null) return@withLock

            val currentItem = current.detail.item
            val isDetailItem = currentItem.id == itemId
            val hasEpisode = current.episodesBySeason.values.any { episodes ->
                episodes.any { episode -> episode.id == itemId }
            }
            if (!isDetailItem && !hasEpisode) return@withLock

            val updatedItem = currentItem.copy(
                isFavorite = if (isDetailItem) isFavorite ?: currentItem.isFavorite else currentItem.isFavorite,
                isPlayed = if (isDetailItem) isPlayed ?: currentItem.isPlayed else currentItem.isPlayed,
                playbackPositionTicks = if (isDetailItem && isPlayed != null) 0L
                else currentItem.playbackPositionTicks,
            )
            val updateEpisode: (MediaItem) -> MediaItem = { episode ->
                if (isPlayed == null || (!isDetailItem && episode.id != itemId)) {
                    if (isFavorite != null && episode.id == itemId) {
                        episode.copy(isFavorite = isFavorite)
                    } else {
                        episode
                    }
                } else {
                    episode.copy(
                        isPlayed = isPlayed,
                        playbackPositionTicks = 0L,
                        isFavorite = if (episode.id == itemId && isFavorite != null) {
                            isFavorite
                        } else {
                            episode.isFavorite
                        },
                    )
                }
            }
            val cascadePlayedState = isDetailItem && isPlayed != null && currentItem.mediaType == MediaType.SERIES
            // Guard above guarantees isPlayed/isFavorite are not both null, so the
            // per-episode rewrite always runs for the present projection(s).
            val updatedEpisodesBySeason = current.episodesBySeason.mapValues { (_, episodes) ->
                episodes.map { episode ->
                    if (cascadePlayedState) {
                        episode.copy(isPlayed = isPlayed, playbackPositionTicks = 0L)
                    } else {
                        updateEpisode(episode)
                    }
                }
            }
            val updatedSortedEpisodes = current.sortedEpisodes.map { episode ->
                if (cascadePlayedState) {
                    episode.copy(isPlayed = isPlayed, playbackPositionTicks = 0L)
                } else {
                    updateEpisode(episode)
                }
            }

            content.value = current.copy(
                contentGen = generation.incrementAndGet(),
                detail = if (isDetailItem) current.detail.copy(item = updatedItem) else current.detail,
                episodesBySeason = updatedEpisodesBySeason,
                sortedEpisodes = updatedSortedEpisodes,
            )
        }

        /**
         * On-demand per-season expand. Fetches via the shared catalogue (serving
         * from its cached snapshot when present, else fetching the one season),
         * merges the result into this session's content with a bumped generation
         * (re-emits through [snapshots]), and returns the episodes for callers
         * that need them synchronously. Idempotent: a re-expand of a season
         * already present at the same size is a no-op.
         */
        suspend fun expandSeason(seasonId: String): List<MediaItem> {
            val seriesId = (content.value as? ContentResolution.Resolved)
                ?.detail?.item?.seriesIdForDetail ?: return emptyList()
            val episodes = episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId)
                .getOrDefault(emptyList())
            if (episodes.isEmpty()) return emptyList()
            resolveMutex.withLock {
                val current = content.value
                if (current !is ContentResolution.Resolved) return@withLock
                // Skip if already present and marked fetched (idempotent re-expand).
                if (seasonId in current.fetchedSeasonIds &&
                    current.episodesBySeason[seasonId]?.size == episodes.size
                ) {
                    return@withLock
                }
                val mergedMap = current.episodesBySeason + (seasonId to episodes)
                val mergedSorted = mergedMap.values.flatten().distinctBy { it.id }.sortedByPlaybackOrder()
                val targetGen = generation.incrementAndGet()
                content.value = current.copy(
                    contentGen = targetGen,
                    episodesBySeason = mergedMap,
                    fetchedSeasonIds = current.fetchedSeasonIds + seasonId,
                    sortedEpisodes = mergedSorted,
                )
            }
            return episodes
        }

        private suspend fun resolveFor(mode: OfflineMode, force: Boolean) {
            resolveMutex.withLock {
                val targetGen = generation.get()
                val current = content.value
                if (mode == OfflineMode.ONLINE) {
                    val alreadyRemote = current is ContentResolution.Resolved &&
                        current.origin == DetailOrigin.REMOTE &&
                        current.contentGen == targetGen &&
                        !force
                    when {
                        force -> resolveRemote(targetGen, force = true)
                        alreadyRemote -> Unit // keep the remote resolution through blips
                        else -> resolveRemote(targetGen, force = false)
                    }
                } else {
                    resolveLocal(targetGen, DetailOrigin.LOCAL_OFFLINE_MODE)
                }
            }
        }

        /**
         * Re-checks the on-disk completed-file predicate when a download flips to
         * COMPLETED, so local-playback/deletion capabilities advertise without a
         * full content re-resolution.
         */
        private suspend fun watchDownloadCompletion() {
            var lastStatus: DownloadStatus? = null
            downloadRepository.getDownloadByMediaItemIdFlow(itemId).collect { download ->
                val status = download?.status
                if (status == DownloadStatus.COMPLETED && lastStatus != DownloadStatus.COMPLETED) {
                    val usable = playbackSourceResolver.resolveUsableDownload(itemId) != null
                    // Serialize with resolves so the confirmed-file flag cannot
                    // race a concurrent content re-resolution.
                    resolveMutex.withLock {
                        val current = content.value
                        if (current is ContentResolution.Resolved) {
                            content.value = current.copy(confirmedUsable = usable)
                        }
                    }
                }
                lastStatus = status
            }
        }
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private suspend fun Session.resolveRemote(targetGen: Long, force: Boolean) {
        if (force) {
            mediaRepository.invalidateDetailCache(itemId)
        }
        val result = mediaRepository.getMediaDetail(itemId)
        result
            .onSuccess { detail ->
                if (force) invalidateByType(detail)
                val usable = playbackSourceResolver.resolveUsableDownload(itemId) != null
                val seriesData = loadSeriesData(detail, offline = false)
                val album = loadAlbumTracks(detail, offline = false)
                content.value = ContentResolution.Resolved(
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
            }
            .onFailure { err ->
                val local = offlineRepository.getOfflineDetail(itemId).first()
                if (local != null) {
                    publishLocal(targetGen, DetailOrigin.LOCAL_REMOTE_FAILURE, local)
                } else {
                    val accessDenied = (err as? ApiException)?.isAccessDenied == true
                    content.value = ContentResolution.Failed(
                        contentGen = targetGen,
                        error = DetailLoadError(
                            message = err.message ?: "Failed to load details",
                            isAccessDenied = accessDenied,
                        ),
                    )
                }
            }
    }

    private suspend fun Session.resolveLocal(targetGen: Long, origin: DetailOrigin) {
        val local = offlineRepository.getOfflineDetail(itemId).first()
        if (local != null) {
            publishLocal(targetGen, origin, local)
        } else {
            content.value = ContentResolution.Failed(
                contentGen = targetGen,
                error = DetailLoadError(
                    message = "Unavailable offline",
                    isUnavailableOffline = true,
                ),
            )
        }
    }

    /**
     * Probes the downloaded file's audio/video tracks, memoized per file
     * `lastModified` so re-resolves (refresh, expand) don't re-probe an
     * unchanged file. Returns `emptyList()` when there is no path or the probe
     * fails — the caller then skips synthesizing a media source.
     */
    private suspend fun Session.probeStreamInfo(downloadPath: String?): List<MediaStream> {
        if (downloadPath.isNullOrEmpty()) return emptyList()
        val mtime = runCatching { java.io.File(downloadPath).lastModified() }.getOrDefault(-1L)
        probedStreamsCache?.let { (cachedMtime, cached) ->
            if (cachedMtime == mtime && mtime >= 0L) return cached
        }
        val streams = localStreamProbe.probe(downloadPath)
        if (mtime >= 0L) probedStreamsCache = mtime to streams
        return streams
    }

    private suspend fun Session.publishLocal(
        targetGen: Long,
        origin: DetailOrigin,
        local: OfflineMediaItem,
    ) {
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
            )
        } else {
            null
        }
        content.value = ContentResolution.Resolved(
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
     * Flattens every episode across a local series's seasons (one-shot Room
     * read). Used to derive both the aggregate header and the per-episode
     * artwork map from a single pass over the offline rows — the catalogue's
     * [MediaItem] projection drops `posterPath` / `totalSizeBytes`.
     */
    private suspend fun localSeriesEpisodes(seriesId: String): List<OfflineMediaItem> =
        offlineRepository.getSeasonsForSeries(seriesId).first().flatMap { season ->
            offlineRepository.getEpisodesForSeason(season.id).first()
        }

    private fun invalidateByType(detail: MediaDetail) {
        when (detail.item.mediaType) {
            MediaType.SERIES -> {
                episodeCatalogue.invalidateSeries(detail.item.id)
                mediaRepository.invalidateDetailCache(detail.item.id)
            }
            MediaType.EPISODE -> detail.item.seriesId?.let { seriesId ->
                episodeCatalogue.invalidateSeries(seriesId)
            }
            MediaType.ALBUM -> mediaRepository.invalidateUserDataCaches(detail.item.id)
            MediaType.COLLECTION -> mediaRepository.invalidateCollectionItemsCache(detail.item.id)
            else -> Unit // detail cache already invalidated by the caller
        }
    }

    // ------------------------------------------------------------------
    // Snapshot construction
    // ------------------------------------------------------------------

    private fun buildState(content: ContentResolution, attachment: Attachment): DetailLoadState = when (content) {
        ContentResolution.Initial -> DetailLoadState.Loading
        is ContentResolution.Failed -> DetailLoadState.Error(content.error)
        is ContentResolution.Resolved -> DetailLoadState.Loaded(buildSnapshot(content, attachment))
    }

    private fun buildSnapshot(
        content: ContentResolution.Resolved,
        attachment: Attachment,
    ): MediaDetailSnapshot {
        val mode = attachment.mode
        val connectivity = if (mode == OfflineMode.ONLINE) RemoteConnectivity.AVAILABLE else RemoteConnectivity.BLOCKED
        val download = buildAttachment(attachment, content.confirmedUsable)
        val isRemote = content.origin == DetailOrigin.REMOTE
        val isCompletedLocal = download?.isCompleted == true
        val capabilities = DetailCapabilities(
            remoteDiscovery = isRemote,
            remoteStreamSelection = isRemote,
            localSubtitleSelection = content.localSubtitles.isNotEmpty(),
            localStreamInfo = !isRemote &&
                content.detail.mediaSources.firstOrNull()?.mediaStreams?.isNotEmpty() == true,
            personNavigation = isRemote,
            studioNavigation = isRemote && content.detail.studios.isNotEmpty(),
            smartPlay = isRemote,
            remoteWorkAllowed = connectivity == RemoteConnectivity.AVAILABLE,
            localDownloadManagement = isCompletedLocal,
        )
        val context = DetailContext(
            origin = content.origin,
            connectivity = connectivity,
            download = download,
            syncState = attachment.syncState,
            seriesAggregate = content.seriesAggregate,
        )
        return MediaDetailSnapshot(
            detail = content.detail,
            context = context,
            capabilities = capabilities,
            assets = content.assets,
            seasons = content.seasons,
            episodesBySeason = content.episodesBySeason,
            fetchedSeasonIds = content.fetchedSeasonIds,
            sortedEpisodes = content.sortedEpisodes,
            albumTracks = content.albumTracks,
            localSubtitles = content.localSubtitles,
            contentGeneration = content.contentGen,
        )
    }

    private fun buildAttachment(attachment: Attachment, confirmedUsable: Boolean): DownloadAttachment? {
        val download = attachment.download ?: return null
        val local = attachment.localItem
        val status = download.status
        val totalSize = if (download.totalSizeBytes > 0) download.totalSizeBytes else local?.totalSizeBytes ?: 0L
        return DownloadAttachment(
            status = status,
            downloadedBytes = download.downloadedBytes,
            totalSizeBytes = totalSize,
            mediaSourceId = download.mediaSourceId,
            container = download.container,
            downloadPath = download.downloadPath,
            createdAtEpochMillis = local?.createdAt ?: 0L,
            isCompletedFilePresent = confirmedUsable && status == DownloadStatus.COMPLETED,
        )
    }

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

    private data class Attachment(
        val mode: OfflineMode,
        val localItem: OfflineMediaItem?,
        val download: DownloadItem?,
        val syncState: OfflineSyncState?,
    )

    private sealed interface ContentResolution {
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val REFRESH_TIMEOUT_MS = 15_000L
        // Synthesized MediaSource id for the probed local file's track inventory.
        const val LOCAL_SOURCE_ID = "local"
    }
}
