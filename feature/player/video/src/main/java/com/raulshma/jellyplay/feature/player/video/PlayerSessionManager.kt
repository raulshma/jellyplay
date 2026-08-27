package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import android.util.Log
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.TranscodeReasonsRefresher
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.isSideLoadableEmbeddedSubtitle
import com.raulshma.jellyplay.core.model.toMediaDetail
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session state for the VOD player: resolved item metadata, stream info, and
 * the play-method badge fields surfaced by the player chrome.
 */
data class PlayerSessionState(
    val currentItemId: String? = null,
    val mediaDetail: MediaDetail? = null,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val title: String = "",
    val subtitle: String = "",
    val playMethodString: String = "Direct Play",
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    /** Server-reported transcode reasons for the current stream; empty when
     *  direct playing (or when the client forced its own fallback URL). */
    val transcodeReasons: List<String> = emptyList(),
    val isDirectPlayForced: Boolean = false,
    /**
     * The server-issued play session id returned by the `PlaybackInfo`
     * endpoint. Used for progress reporting so the server can associate
     * reports with (possibly transcoded) streams. `null` for offline
     * playback or when the client falls back to a static direct URL.
     */
    val playSessionId: String? = null,
    val isReady: Boolean = false,
    val offlineTrickplayDir: java.io.File? = null,
    val streamUrl: String? = null,
    /**
     * True when the current item is playing from a local download (resolved
     * via [PlaybackSource.Offline]). The ViewModel uses this to branch
     * episode/season resolution toward the offline store so next-episode
     * discovery and autoplay work without a server round-trip.
     */
    val isOffline: Boolean = false,
)

class PlayerSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val playerLifecycleManager: PlayerLifecycleManager,
    private val pipController: com.raulshma.jellyplay.core.data.playback.PipController,
    private val adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
    private val playerEngineFactory: PlayerEngineFactory,
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
    /**
     * Fail-fast gate for online resolution (#146): without it a dead network
     * made every load stage block on full OkHttp timeouts before silently
     * bailing, which read as "the Next button did nothing".
     */
    private val offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager,
    private val userMessageBus: com.raulshma.jellyplay.core.ui.feedback.UserMessageBus,
) {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _engine = MutableStateFlow<MediaEngine?>(null)
    val engineFlow: StateFlow<MediaEngine?> = _engine.asStateFlow()
    val engine: MediaEngine? get() = _engine.value

    private var lastPlaybackRequest: PlaybackRequest? = null

    /**
     * Owns the in-flight offline sidecar-subtitle catch-up fetch; cancelled on
     * every load and on release so a late landing can never attach into a
     * session it no longer belongs to. The item-id guard inside the job stays
     * as belt-and-braces (same-item reload).
     */
    private var sidecarCatchUpJob: Job? = null

    /** Owns the in-flight transcode-reason lookup; cancelled/replaced per resolution. */
    private val transcodeReasonsRefresher =
        TranscodeReasonsRefresher(scope, playbackRepository::fetchActiveTranscodeReasons)

    /**
     * Populates [PlayerSessionState.transcodeReasons] from the server's live
     * session (`TranscodingInfo`) when the resolution chose [PlayMethod.TRANSCODE],
     * and clears it otherwise. The server registers the transcoding session a
     * beat after playback starts, so the fetch waits, then retries once —
     * reasons are diagnostics and a miss simply leaves the list empty.
     */
    private fun scheduleTranscodeReasonsRefresh(itemId: String?, playMethod: PlayMethod) {
        transcodeReasonsRefresher.refresh(
            itemId,
            isTranscode = playMethod == PlayMethod.TRANSCODE,
            isCurrent = { _sessionState.value.currentItemId == itemId },
            clear = { _sessionState.update { it.copy(transcodeReasons = emptyList()) } },
            onReasons = { reasons ->
                _sessionState.update { it.copy(transcodeReasons = reasons) }
            },
        )
    }

    /**
     * The external (side-loaded) subtitle sources for the current session, or
     * null when no media is loaded. Exposed so features like the subtitle-sync
     * preview can resolve and parse the active track's bytes. The backing
     * [PlaybackRequest] is immutable — external subs are replaced wholesale
     * (see [addExternalSubtitle]).
     */
    val currentExternalSubtitles: List<SubtitleSource>?
        get() = lastPlaybackRequest?.externalSubtitles

    /**
     * The request headers (auth etc., e.g. Jellyfin `X-Emby-Token`) for the
     * current session, or null when no media is loaded. Used to authenticate
     * HTTP-fetched external subtitle bytes (see [SubtitlePreviewRepository]).
     */
    val currentPlaybackHeaders: Map<String, String>?
        get() = lastPlaybackRequest?.headers
    private var lastPlayerType: PlayerType? = null

    /**
     * Builds the secondary line shown beneath the episode title in the player
     * chrome. For a series episode this renders the show name followed by the
     * season/episode marker in `SXXEXX` form (e.g. "The Show · S01E05"). When
     * neither a series name nor season/episode data is available, falls back
     * to a trimmed overview — preserving the historical behaviour for movies
     * and other non-episodic items.
     */
    private fun buildEpisodeSubtitle(
        seriesName: String?,
        overview: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): String = buildString {
        val hasSeries = !seriesName.isNullOrBlank()
        if (hasSeries) append(seriesName)
        if (seasonNumber != null && episodeNumber != null) {
            if (isNotEmpty()) append(" \u00B7 ")
            append("S${seasonNumber}E${episodeNumber}")
        }
        if (isEmpty()) append(overview?.take(60) ?: "")
    }

    fun bindReclaimedEngine(engine: MediaEngine, itemId: String, detail: MediaDetail) {
        // Same stale-fetch hazard as loadMedia: a reclaimed engine for the
        // same item must not inherit the previous session's in-flight fetch.
        transcodeReasonsRefresher.cancel()
        _engine.value = engine
        playerLifecycleManager.activeCallbacks = engine
        pipController.requestAutoEnterPip(engine.capabilities.supportsPip)
        _sessionState.update {
            it.copy(
                currentItemId = itemId,
                mediaDetail = detail,
                title = detail.item.name,
                subtitle = buildEpisodeSubtitle(
                    seriesName = detail.item.seriesName,
                    overview = detail.item.overview,
                    seasonNumber = detail.item.seasonNumber,
                    episodeNumber = detail.item.episodeNumber,
                ),
                transcodeReasons = emptyList(),
                isReady = true
            )
        }
    }

    /**
     * Legacy entry point — preserved for binary compatibility with existing
     * call-sites ([VideoPlayerViewModel.initializeInternal],
     * [VideoPlayerViewModel.loadCinemaIntro]). Delegates to
     * [loadMedia] with a [PlaybackSource.Auto].
     */
    suspend fun loadMedia(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        loadMedia(PlaybackSource.Auto(itemId, mediaSourceId), startPositionTicks)
    }

    /**
     * Unified media loader. Dispatches to [loadOffline] or [loadOnline] based
     * on the resolved [PlaybackSource]. The [PlaybackSource.Auto] variant is
     * resolved via [PlaybackSource.Auto.resolve] using the downloads DB,
     * matching the historical auto-detection behaviour exactly.
     *
     * The completed-download predicate is owned by
     * [PlaybackSourceResolver.resolveUsableDownload] (the shared core routine
     * every call-site converges on); here it doubles as the Auto resolution
     * input and the metadata source for [loadOffline]. `Auto.resolve` then
     * maps a non-null download → Offline, null → Online.
     */
    suspend fun loadMedia(source: PlaybackSource, startPositionTicks: Long) {
        val itemId = source.itemId
        // Kill any in-flight reasons fetch up front: reloading the *same*
        // item (watch-again, offline switch) would let the old fetch's
        // isCurrent guard pass and land the previous session's reasons in
        // the fresh session before the resolve path re-arms or clears them.
        transcodeReasonsRefresher.cancel()
        sidecarCatchUpJob?.cancel()
        _sessionState.update { it.copy(currentItemId = itemId, isReady = false) }

        // The download lookup is needed both for Auto resolution and for
        // metadata enrichment during offline playback, so it is performed
        // unconditionally — identical to the pre-refactor behaviour. The
        // shared resolver owns the COMPLETED + file-exists predicate.
        val download = playbackSourceResolver.resolveUsableDownload(itemId)

        val resolved = when (source) {
            is PlaybackSource.Auto -> source.resolve(download)
            is PlaybackSource.Offline -> source
            is PlaybackSource.Online -> source
        }

        // Offline gate: an Online-resolved item cannot play without the server.
        // Fail fast with feedback instead of dead-airing through every network
        // stage's timeout (#146). Offline (download) sources are unaffected —
        // that is exactly the path offline mode exists for.
        if (resolved is PlaybackSource.Online && offlineModeManager.isOffline) {
            failLoad(context.getString(R.string.player_video_error_offline_stream))
            return
        }

        when (resolved) {
            is PlaybackSource.Offline -> loadOffline(
                itemId = itemId,
                download = download,
                downloadPath = resolved.downloadPath,
                startPositionTicks = startPositionTicks,
            )
            is PlaybackSource.Online -> loadOnline(
                itemId = itemId,
                mediaSourceId = resolved.mediaSourceId,
                startPositionTicks = startPositionTicks,
            )
            is PlaybackSource.Auto -> error("PlaybackSource.Auto must be resolved before dispatch")
        }
    }

    /**
     * Fail a load: publish the not-ready state and surface the reason to the
     * user. Every [loadMedia] failure path converges here so the state write
     * and the feedback emission cannot drift apart (#146).
     */
    private fun failLoad(message: String) {
        _sessionState.update { it.copy(title = message, isReady = false) }
        userMessageBus.error(message)
    }

    private suspend fun loadOffline(
        itemId: String,
        download: com.raulshma.jellyplay.core.model.DownloadItem?,
        downloadPath: String,
        startPositionTicks: Long,
    ) {
        val localFile = java.io.File(downloadPath).takeIf { it.exists() }
        if (localFile == null) {
            // The file vanished after resolution — surface an error rather
            // than silently falling back online (callers that want fallback
            // should use [PlaybackSource.Auto]).
            _sessionState.update { it.copy(title = context.getString(R.string.player_video_error_offline_file_missing), isReady = false) }
            return
        }

        val offlineItem = offlineRepository.getOfflineItem(itemId)
        val title = offlineItem?.name ?: download?.name ?: itemId
        val subtitle = buildEpisodeSubtitle(
            seriesName = offlineItem?.seriesName,
            overview = offlineItem?.overview,
            seasonNumber = offlineItem?.seasonNumber,
            episodeNumber = offlineItem?.episodeNumber,
        )
        val url = Uri.fromFile(localFile).toString()

        var runTimeTicks = offlineItem?.runTimeTicks
        if (runTimeTicks == null || runTimeTicks <= 0L) {
            val extractedDurationMs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(localFile.absolutePath)
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    durationStr?.toLongOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            if (extractedDurationMs != null && extractedDurationMs > 0) {
                runTimeTicks = extractedDurationMs * 10_000
            }
        }

        // Resolve the real container so ExoPlayer gets the right MIME type and
        // selects the correct extractor. Precedence:
        //   1. Container persisted at download time (new downloads).
        //   2. Magic-byte sniffing fallback (legacy downloads whose on-disk
        //      extension was hardcoded to .mp4 even when the bytes are MKV/TS/...).
        // Without this, MKV-in-.mp4 hangs ExoPlayer silently in STATE_BUFFERING
        // with no error dialog (only MPV plays, because libavformat sniffs content).
        val containerHint = download?.container
            ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.raulshma.jellyplay.feature.player.video.engine.ContainerSniffer.sniff(localFile)
            }
        val mimeHint = containerHint?.let {
            com.raulshma.jellyplay.feature.player.video.engine.ContainerMimeMapper.mapToMime(it)
        }

        _sessionState.update {
            it.copy(
                title = title,
                subtitle = subtitle,
                playMethodString = "Offline",
                playMethod = PlayMethod.DIRECT_PLAY,
                transcodeReasons = emptyList(),
                streamUrl = url,
            )
        }

        // Await the hydrated aggregate (not the .value point read, which is the
        // empty default on cold start and would lose the saved per-item subtitle
        // delay). loadOnline/loadOffline are suspend, so .first() on the raw
        // flow blocks until the real DataStore values arrive.
        val agg = aggregateStore.aggregateRaw.first()
        val playerType = agg.playback.preferredPlayer

        // Build the session detail through the canonical offline adapter
        // (toMediaDetail) so every persisted field — chapters, provider ids,
        // external urls, cast, taglines, critic rating — flows exactly as it
        // does online. Falls back to a bare-detail MediaDetail when only the
        // download row (no offline_media metadata) is present. runTimeTicks is
        // overridden with the locally-extracted value so seek/duration math
        // matches the actual file. mediaSources stays empty on both paths
        // (toMediaDetail maps it null and the bare default is emptyList)
        // because the offline track-restore ladder depends on it (see
        // refreshMediaDetail).
        val detail = (offlineItem?.toMediaDetail() ?: MediaDetail(
            item = MediaItem(
                id = itemId,
                name = title,
                mediaType = download?.mediaType ?: MediaType.UNKNOWN,
            ),
        )).let { base ->
            base.copy(item = base.item.copy(runTimeTicks = runTimeTicks))
        }

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true, mediaDetail = detail, isOffline = true) }
            return
        }

        initializeEngine(playerType, detail, null, url, startPositionTicks, agg, mimeType = mimeHint)

        // Attach external subtitles bundled with the download (offline subs).
        loadOfflineSubtitles(itemId, downloadPath)

        // Re-attach provider-sourced subtitles (OpenSubtitles/Wyzie) persisted
        // for this item in the streaming-subtitle store. `SubtitleManager`
        // always writes provider downloads there regardless of online/offline
        // playback, so offline (downloaded) media must restore them too —
        // otherwise a subtitle downloaded once vanishes on reopen. No server
        // stream list exists offline, so no deletion reconciliation runs.
        loadStreamingSubtitles(itemId, currentStreams = null)

        // Best-effort catch-up for sidecar subtitles added on the server AFTER
        // the download was made (#144): the bundle is a download-time snapshot,
        // so a newly added .srt never reached it, and offline sessions (empty
        // server streams, engine-tracks-only picker) would never show it. When
        // the server is reachable, re-fetch the detail in the background and
        // side-load every external stream not already attached — bundled subs
        // dedupe by their `offline:{index}` id inside attachNewSubtitleStreams.
        // Deliberately background: offline playback must start instantly, and
        // the tracks pop in when the fetch lands. This never publishes server
        // streams into session state — the offline track-restore ladder depends
        // on `mediaStreams` staying empty (see refreshMediaDetail).
        if (!offlineModeManager.isOffline) {
            sidecarCatchUpJob = scope.launch {
                val detail = mediaRepository.getMediaDetail(itemId, force = true).getOrNull()
                // The session may have moved on (item switch, release) while the
                // fetch was in flight — attachNewSubtitleStreams re-checks the
                // item id, but skip the work entirely when it is no longer ours.
                if (detail != null && _sessionState.value.currentItemId == itemId) {
                    attachNewSubtitleStreams(detail)
                }
            }
        }

        val trickplayDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
            .getLocalTrickplayDir(downloadPath, itemId)

        _sessionState.update { it.copy(
            isReady = true,
            mediaDetail = detail,
            offlineTrickplayDir = trickplayDir,
            isOffline = true,
        ) }
    }

    private suspend fun loadOnline(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ) {
        val detailResult = mediaRepository.getMediaDetail(itemId)
        val detail = detailResult.getOrElse {
            // Surface the failure — the old silent bail left the user staring
            // at a "loading" veil that had lifted over nothing (#146).
            failLoad(context.getString(R.string.player_video_error_loading_media))
            return
        }

        val source = if (mediaSourceId != null) {
            detail.mediaSources.find { it.id == mediaSourceId }
        } else {
            detail.mediaSources.firstOrNull()
        }
        val streams = source?.mediaStreams ?: emptyList()

        // Await the hydrated aggregate (not the .value point read, which is the
        // empty default on cold start and would lose the saved per-item subtitle
        // delay). loadOnline/loadOffline are suspend, so .first() on the raw
        // flow blocks until the real DataStore values arrive.
        val agg = aggregateStore.aggregateRaw.first()
        val playerType = agg.playback.preferredPlayer
        val sourceId = source?.id ?: ""

        // Consult the PlaybackInfo endpoint so the server decides Direct
        // Play / Direct Stream / Transcode based on the device profile and
        // the user's PlaybackMode. Falls back to a static direct URL when
        // the server cannot resolve a playable method (preserving the
        // historical behaviour for non-conforming sources).
        val maxBitrate = adaptiveBitrateManager.resolveEffectiveMaxBitrate()
        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = startPositionTicks,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = maxBitrate,
            mode = agg.playback.playbackMode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, startPositionTicks, source?.liveStreamId)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                title = detail.item.name,
                subtitle = buildEpisodeSubtitle(
                    seriesName = detail.item.seriesName,
                    overview = detail.item.overview,
                    seasonNumber = detail.item.seasonNumber,
                    episodeNumber = detail.item.episodeNumber,
                ),
                mediaDetail = detail,
                currentMediaSource = source,
                mediaStreams = streams,
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                isDirectPlayForced = agg.playback.playbackMode == PlaybackMode.FORCE_DIRECT_PLAY,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }
        scheduleTranscodeReasonsRefresh(itemId, playMethod)

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true) }
            return
        }

        initializeEngine(playerType, detail, source, url, startPositionTicks, agg, playMethod)

        // Re-attach subtitles previously saved from an external provider
        // (OpenSubtitles/Wyzie) for this streaming item. These persist across
        // replays on-device, so a subtitle downloaded once (even offline) is
        // available on the next playback without a server round-trip.
        loadStreamingSubtitles(itemId, streams)

        _sessionState.update { it.copy(isReady = true) }
    }

    /**
     * Side-loads subtitles previously persisted by `SubtitleManager` into the
     * durable streaming-subtitle store. Mirrors [loadOfflineSubtitles] but for
     * streaming (non-downloaded) items — keyed by `itemId`, not a media-file
     * path. Files missing on disk are silently skipped.
     *
     * Entries that recorded a [SavedSubtitle.serverStreamIndex] are reconciled
     * against [currentStreams]: when the user deleted the subtitle from the
     * metadata editor, its server stream is gone — and side-loading the local
     * copy would resurrect the deleted track on every playback. Null disables
     * reconciliation (offline playback has no server state to reconcile
     * against). Legacy entries (no recorded index) and device-only downloads
     * (upload failed, so no index was ever recorded) always load: the durable
     * local copy is their only copy.
     */
    private suspend fun loadStreamingSubtitles(itemId: String, currentStreams: List<MediaStream>?) {
        val saved = streamingSubtitleStore.loadAll(itemId)
        val currentIndexes = currentStreams
            ?.filter { it.type == StreamType.SUBTITLE }
            ?.map { it.index }
            ?.toSet()
        for (entry in saved) {
            val recordedIndex = entry.serverStreamIndex
            if (recordedIndex != null && currentIndexes != null && recordedIndex !in currentIndexes) {
                Log.d(
                    "SubtitleUse",
                    "loadStreamingSubtitles: skipping ${entry.provider}:${entry.providerSubtitleId} " +
                        "(server stream $recordedIndex deleted)",
                )
                continue
            }
            val file = streamingSubtitleStore.fileFor(itemId, entry)
            if (!file.exists()) continue
            addExternalSubtitle(
                SubtitleSource(
                    url = Uri.fromFile(file).toString(),
                    label = entry.language ?: entry.fileName,
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = false,
                    isForced = entry.isForced,
                    id = "streaming:${entry.provider}:${entry.providerSubtitleId}",
                ),
            )
        }
    }

    private fun initializeEngine(
        playerType: PlayerType,
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        agg: VideoPlayerAggregate,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mimeType: String? = null,
    ) {
        // Reuse the live engine when the player type is unchanged (the common
        // binge-watch/autoplay case): every engine's load() resets per-item
        // state, and ExoPlayerEngine fast-paths an unchanged-config load with
        // setMediaItem+prepare instead of a full teardown/rebuild — keeping the
        // media-session wiring and audio-effect chain attached and avoiding a
        // rebuffer at every episode boundary. First creation, an engine
        // switch, or a released engine takes the original release+create path.
        val existingEngine = _engine.value
        val eng = if (existingEngine != null && playerType == lastPlayerType) {
            existingEngine
        } else {
            // Release the outgoing engine and null the reference before creating
            // the replacement, so a failure in create()/setup can never leave the
            // field pointing at an already-released engine.
            try {
                _engine.value?.release()
            } finally {
                _engine.value = null
            }
            playerEngineFactory.create(playerType).also { created ->
                _engine.value = created
            }
        }
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        pipController.requestAutoEnterPip(eng.capabilities.supportsPip)

        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = _sessionState.value.mediaStreams,
            itemId = detail.item.id,
            engineSpecific = resolveEngineConfig(playerType, agg),
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(agg.videoPlayer.videoDefaultSpeed)

        val externalSubtitles = buildExternalSubtitles(detail, source, playMethod)

        val artworkUri = playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
        
        val headers = mutableMapOf<String, String>()
        val serverUrl = playbackRepository.getServerUrl()
        val token = playbackRepository.getAccessToken()
        if (!token.isNullOrBlank()) {
            headers["X-Emby-Token"] = token
        }

        val request = PlaybackRequest(
            uri = url,
            title = detail.item.name,
            startPositionMs = startPositionTicks / 10_000,
            artworkUri = artworkUri,
            externalSubtitles = externalSubtitles,
            headers = headers,
            preferredAudioLanguage = agg.subtitle.preferredAudioLanguage,
            preferredSubtitleLanguage = agg.subtitle.preferredSubtitleLanguage,
            maxVideoBitrate = if (agg.playback.playbackMode == PlaybackMode.AUTO)
                adaptiveBitrateManager.resolveEffectiveMaxBitrate()?.toInt()
                else null,
            serverUrl = serverUrl,
            authToken = token,
            playMethod = playMethod,
            minBufferMs = agg.videoPlayer.videoPreloadBufferSize.minBufferMs,
            maxBufferMs = agg.videoPlayer.videoPreloadBufferSize.maxBufferMs,
            normalizationGain = detail.item.normalizationGain,
            mimeType = mimeType,
            serverDurationMs = (detail.item.runTimeTicks ?: 0L) / 10_000,
        )

        lastPlaybackRequest = request
        eng.load(request)
    }

    private fun resolveEngineConfig(
        playerType: PlayerType,
        agg: VideoPlayerAggregate,
    ): EngineSpecificConfig? = when (playerType) {
        PlayerType.MPV -> agg.engine.mpvConfig
        PlayerType.LIBVLC -> agg.engine.libVlcConfig
        PlayerType.EXO_PLAYER -> agg.engine.exoPlayerConfig
        PlayerType.EXTERNAL -> null
    }

    /**
     * Re-resolves playback for the current item under [mode] and reloads the
     * engine with the resulting URL at [currentPositionMs]. Used when the
     * user toggles [PlaybackMode] or [com.raulshma.jellyplay.core.model.StreamingQuality]
     * mid-playback so a switch to/from transcode swaps the underlying stream
     * without restarting the item.
     *
     * [selection] carries the currently-selected server streams into the
     * re-POST: the server bakes a single audio track into the transcoded
     * manifest and burns in image subs, so dropping the indices would silently
     * reset those choices to the server defaults. Text subs are side-loaded
     * regardless (see [buildExternalSubtitles]) — for them the indices are
     * echoed for the server's DefaultSubtitleStreamIndex bookkeeping; the
     * client-side selection is restored by the track ladder.
     *
     * Returns the resolved [ResolvedPlayback] (or `null` on failure) so the
     * caller can react — e.g. fall back to transcode when a forced direct
     * play request yields no playable method.
     */
    suspend fun reloadPlayback(
        mode: PlaybackMode,
        quality: com.raulshma.jellyplay.core.model.StreamingQuality,
        currentPositionMs: Long,
        selection: MediaStreamSelection? = null,
    ): ResolvedPlayback? {
        val itemId = _sessionState.value.currentItemId ?: return null
        val sourceId = _sessionState.value.currentMediaSource?.id ?: ""
        val agg = aggregateStore.aggregate.value
        val playerType = lastPlayerType ?: agg.playback.preferredPlayer
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(quality)

        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = currentPositionMs * 10_000,
            audioStreamIndex = selection?.audioStreamIndex,
            subtitleStreamIndex = selection?.subtitleStreamIndex,
            maxStreamingBitrateBits = maxBitrate,
            mode = mode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, currentPositionMs * 10_000)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                isDirectPlayForced = mode == PlaybackMode.FORCE_DIRECT_PLAY,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }
        scheduleTranscodeReasonsRefresh(itemId, playMethod)

        // Swap the engine onto the freshly resolved URL. The engine-level
        // bitrate cap is only meaningful for AUTO (server-side cap drives
        // transcode; direct play is uncapped). Rebuild the side-loaded
        // subtitle set for the new play method so the subtitle picker stays
        // populated when switching to/from a transcode.
        val engineMaxBitrate = if (mode == PlaybackMode.AUTO) maxBitrate?.toInt() else null
        val state = _sessionState.value
        val rebuiltSubtitles = state.mediaDetail?.let { detail ->
            buildExternalSubtitles(detail, state.currentMediaSource, playMethod)
        } ?: emptyList()
        reloadWithEngine(
            playerType = playerType,
            currentPositionMs = currentPositionMs,
            playbackSpeed = agg.videoPlayer.videoDefaultSpeed,
            maxVideoBitrate = engineMaxBitrate,
            uriOverride = url,
            externalSubtitlesOverride = rebuiltSubtitles,
            playMethodOverride = playMethod,
        )
        return resolved
    }

    /**
     * Reload playback for the current item at [currentPositionMs] with a new
     * audio/subtitle stream [selection]. Used when the user selects a
     * server-origin audio/subtitle track during transcoded playback
     * — mpv cannot switch audio in-place on an HLS manifest, and embedded subs
     * aren't delivered in the transcode, so the server must re-issue the
     * stream with the chosen index baked in (standard Jellyfin PlaybackInfo
     * re-POST).
     *
     * The current playback mode / quality / max-bitrate are preserved from the
     * existing session; only the stream indices change. Subtitles are rebuilt
     * via [buildExternalSubtitles] so the side-loaded set matches the new
     * play method.
     */
    suspend fun reloadForStreamChange(
        selection: MediaStreamSelection,
        currentPositionMs: Long,
    ): ResolvedPlayback? {
        val itemId = _sessionState.value.currentItemId ?: return null
        val sourceId = _sessionState.value.currentMediaSource?.id ?: ""
        val agg = aggregateStore.aggregate.value
        val playerType = lastPlayerType ?: agg.playback.preferredPlayer
        val maxBitrate = adaptiveBitrateManager.resolveEffectiveMaxBitrate()
        val mode = agg.playback.playbackMode

        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = currentPositionMs * 10_000,
            audioStreamIndex = selection.audioStreamIndex,
            subtitleStreamIndex = selection.subtitleStreamIndex,
            maxStreamingBitrateBits = maxBitrate,
            mode = mode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, currentPositionMs * 10_000)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }
        scheduleTranscodeReasonsRefresh(itemId, playMethod)

        val engineMaxBitrate = if (mode == PlaybackMode.AUTO) maxBitrate?.toInt() else null
        val state = _sessionState.value
        val rebuiltSubtitles = state.mediaDetail?.let { detail ->
            buildExternalSubtitles(detail, state.currentMediaSource, playMethod)
        } ?: emptyList()
        reloadWithEngine(
            playerType = playerType,
            currentPositionMs = currentPositionMs,
            playbackSpeed = agg.videoPlayer.videoDefaultSpeed,
            maxVideoBitrate = engineMaxBitrate,
            uriOverride = url,
            externalSubtitlesOverride = rebuiltSubtitles,
            playMethodOverride = playMethod,
        )
        return resolved
    }

    suspend fun reloadWithEngine(
        playerType: PlayerType,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        maxVideoBitrate: Int? = null,
        uriOverride: String? = null,
        externalSubtitlesOverride: List<SubtitleSource>? = null,
        playMethodOverride: PlayMethod? = null,
    ) {
        val last = lastPlaybackRequest ?: return
        val agg = aggregateStore.aggregate.value

        try {
            _engine.value?.release()
        } finally {
            _engine.value = null
        }

        val eng = playerEngineFactory.create(playerType)
        _engine.value = eng
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        pipController.requestAutoEnterPip(eng.capabilities.supportsPip)

        val state = _sessionState.value
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = state.mediaStreams,
            itemId = state.currentItemId,
            engineSpecific = resolveEngineConfig(playerType, agg),
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(playbackSpeed)

        val request = last.copy(
            startPositionMs = currentPositionMs,
            maxVideoBitrate = maxVideoBitrate,
            uri = uriOverride ?: last.uri,
            externalSubtitles = externalSubtitlesOverride ?: last.externalSubtitles,
            // A URL swap (mode/quality/stream-index reload) re-resolves the
            // play method with it; without this the stale Direct-Play method
            // would survive a switch to a transcode URL.
            playMethod = playMethodOverride ?: last.playMethod,
        )
        lastPlaybackRequest = request
        eng.load(request)
    }

    fun addExternalSubtitle(source: SubtitleSource) {
        val last = lastPlaybackRequest ?: return
        val updatedSubtitles = last.externalSubtitles + source
        lastPlaybackRequest = last.copy(externalSubtitles = updatedSubtitles)
        _engine.value?.addExternalSubtitle(source)
    }

    /**
     * The [MediaSource] of [detail] corresponding to the session's playing
     * source, matched by id so multi-version items keep the playing version's
     * streams (falling back to the CURRENT session source, never to a
     * same-position different-version stream). With [fallbackToFirst], a
     * session without a source (offline) resolves to the detail's first
     * source; without it the result stays null so offline sessions keep their
     * source-less state. Single home for the "which of this detail's sources
     * is ours?" walk used by refresh/attach paths and the ViewModel fold.
     */
    fun matchedMediaSource(detail: MediaDetail, fallbackToFirst: Boolean): MediaSource? {
        val current = _sessionState.value.currentMediaSource
        return when {
            current != null -> detail.mediaSources.firstOrNull { it.id == current.id } ?: current
            fallbackToFirst -> detail.mediaSources.firstOrNull()
            else -> null
        }
    }

    /**
     * Applies a refreshed [MediaDetail] after a mid-session metadata change
     * (subtitle download/upload). Merges into session state FIRST so mode/
     * quality/stream-index reloads rebuild the side-loaded subtitle set from
     * the refreshed detail instead of the pre-change snapshot, and the session
     * collector re-publishes the refreshed streams instead of reverting them;
     * then optionally side-loads newly attached subtitle streams into the live
     * engine ([attachToEngine] = false when the caller already side-loaded a
     * local copy itself — attaching again would duplicate the track). One
     * entry point so this ordering contract lives here, not in callers.
     */
    fun applyRefreshedDetail(detail: MediaDetail, attachToEngine: Boolean) {
        refreshMediaDetail(detail)
        if (attachToEngine) attachNewSubtitleStreams(detail)
    }

    /**
     * Merges a re-fetched [MediaDetail] into the session state after a
     * mid-session metadata change (subtitle download/upload). Without this,
     * [reloadPlayback] / [reloadForStreamChange] rebuild the side-loaded
     * subtitle set from the stale pre-change [PlayerSessionState.mediaDetail],
     * silently dropping a just-downloaded subtitle on the next mode/quality/
     * stream-index switch, and the ViewModel's session collector re-publishes
     * the stale stream list over the refreshed one.
     *
     * Offline sessions keep their null source and empty stream list — their
     * side-loaded subs are keyed by the offline id contract, and the
     * track-restore ladder depends on `mediaStreams` staying empty offline.
     */
    fun refreshMediaDetail(detail: MediaDetail) {
        val itemId = _sessionState.value.currentItemId ?: return
        if (detail.item.id != itemId) return
        val source = matchedMediaSource(detail, fallbackToFirst = false)
        val streams = source?.mediaStreams ?: emptyList()
        _sessionState.update {
            it.copy(
                mediaDetail = detail,
                currentMediaSource = source,
                mediaStreams = streams,
            )
        }
    }

    /**
     * Side-loads subtitle streams that appeared in [detail] but are not yet
     * attached to the current session — the missing step after an in-player
     * subtitle download/upload. The engine was loaded with the pre-change
     * subtitle set, and on Direct Play the picker is built purely from engine
     * tracks, so a server-attached stream stays invisible until the engine
     * itself learns about it. Re-runs [buildExternalSubtitles]' per-stream
     * gates so the mid-session set matches a fresh load, then attaches only
     * genuinely new entries (by `external:{index}` / `offline:{index}` id) to
     * avoid duplicating already side-loaded streams.
     */
    fun attachNewSubtitleStreams(detail: MediaDetail) {
        if (lastPlaybackRequest == null) return
        val state = _sessionState.value
        val itemId = state.currentItemId ?: return
        if (detail.item.id != itemId) return
        val source = matchedMediaSource(detail, fallbackToFirst = true) ?: return
        val existingIds = lastPlaybackRequest?.externalSubtitles?.map { it.id }?.toSet().orEmpty()
        val built = buildExternalSubtitles(detail, source, state.playMethod)
            .filter { sub ->
                if (sub.id in existingIds) return@filter false
                // An offline-bundled sidecar with the same stream index is the
                // same subtitle — don't re-attach it under the external id.
                val index = externalSubtitleTrackStreamIndex(sub.id)
                index == null || offlineSubtitleTrackId(index) !in existingIds
            }
        Log.d(
            "SubtitleUse",
            "attachNewSubtitleStreams: playMethod=${state.playMethod}, existing=${existingIds.size}, " +
                "attaching=${built.map { "${it.id} '${it.label.take(24)}'" }}",
        )
        built.forEach { addExternalSubtitle(it) }
    }

    /**
     * Builds the side-loaded [SubtitleSource] list for the engine.
     *
     * Subtitles that already carry a server [MediaStream.deliveryUrl] (the
     * PlaybackInfo response populates this for externally-delivered subs,
     * including image subs when the PGS-direct-play profile opts in) use that
     * URL. Otherwise, only **text** subs ([isSideLoadableEmbeddedSubtitle]) are
     * considered for side-loading — the Jellyfin subtitle endpoint cannot serve
     * image formats (PGS/VOBSUB/DVB), so those are skipped (left to burn-in on
     * transcode, or container demux on direct play).
     *
     * For the text subs that survive the codec gate, side-loading is
     * method-dependent: external subs are always side-loaded; embedded text
     * subs are side-loaded only when NOT direct-playing (transcoded HLS does
     * not reliably expose them in-manifest). On DIRECT_PLAY every engine
     * (ExoPlayer, LibVLC, MPV) demuxes embedded text subs from the container
     * natively — confirmed for MPV via logcat, which lists the demuxed tracks
     * — so side-loading them too would duplicate each track and could render
     * the selected sub twice. We therefore never side-load embedded subs
     * alongside container demuxing.
     */
    private fun buildExternalSubtitles(
        detail: MediaDetail,
        source: MediaSource?,
        playMethod: PlayMethod,
    ): List<SubtitleSource> {
        val streams = source?.mediaStreams ?: return emptyList()
        return streams.filter { it.type == StreamType.SUBTITLE }.mapNotNull { stream ->
            val subUrl = when {
                // Server-issued delivery URL (e.g. an external PGS sub the
                // server can serve verbatim when PGS direct play is opted in).
                !stream.deliveryUrl.isNullOrBlank() ->
                    playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                // The Jellyfin subtitle endpoint only serves text formats — it
                // cannot synthesize image subs (PGS/VOBSUB/DVB), so only
                // side-load text-side-loadable streams. Image subs are left to
                // the server's burn-in (transcode) or the player's container
                // demux (direct play on MPV).
                !isSideLoadableEmbeddedSubtitle(stream.codec) -> {
                    Log.d("SubtitleUse", "buildExternalSubtitles: skipping non-sideloadable codec=${stream.codec} index=${stream.index}")
                    return@mapNotNull null
                }
                // See the KDoc above for the DIRECT_PLAY rationale: embedded
                // text subs are side-loaded only when not direct-playing.
                stream.isExternal ||
                    playMethod != PlayMethod.DIRECT_PLAY ->
                    playbackRepository.buildSubtitleDeliveryUrl(
                        detail.item.id, source.id, stream.index, stream.codec,
                    )
                else -> {
                    Log.d("SubtitleUse", "buildExternalSubtitles: skipping embedded sub on direct play index=${stream.index}")
                    return@mapNotNull null
                }
            }
            if (subUrl.isBlank()) {
                Log.d("SubtitleUse", "buildExternalSubtitles: blank url for index=${stream.index}")
                return@mapNotNull null
            }

            SubtitleSource(
                url = subUrl,
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown",
                language = stream.language,
                mimeType = null, // Mapped by the engine using codec or extension.
                codec = stream.codec,
                isDefault = stream.isDefault,
                isForced = stream.isForced,
                id = externalSubtitleTrackId(stream.index),
            )
        }
    }

    private suspend fun loadOfflineSubtitles(itemId: String, downloadPath: String) {
        val manifest = downloadRepository.loadLocalSubtitleManifest(downloadPath, itemId) ?: return
        if (manifest.subtitles.isEmpty()) return
        val parentDir = java.io.File(downloadPath).parentFile ?: return
        // Try item-scoped directory first, fall back to legacy un-scoped.
        val scopedDir = java.io.File(parentDir, "subtitles_$itemId")
        val subtitlesDir = if (scopedDir.exists()) scopedDir else java.io.File(parentDir, "subtitles")
        for (entry in manifest.subtitles) {
            val file = java.io.File(subtitlesDir, entry.fileName)
            if (!file.exists()) continue
            addExternalSubtitle(
                SubtitleSource(
                    url = Uri.fromFile(file).toString(),
                    label = entry.displayTitle ?: entry.title ?: entry.language ?: "Subtitle ${entry.index}",
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = entry.isDefault,
                    isForced = entry.isForced,
                    id = offlineSubtitleTrackId(entry.index),
                )
            )
        }
    }

    fun release() {
        transcodeReasonsRefresher.cancel()
        sidecarCatchUpJob?.cancel()
        _engine.value?.release()
        _engine.value = null
        lastPlaybackRequest = null
        lastPlayerType = null
        _sessionState.update { PlayerSessionState() }
    }

    fun detachEngine() {
        _engine.value = null
    }
}

