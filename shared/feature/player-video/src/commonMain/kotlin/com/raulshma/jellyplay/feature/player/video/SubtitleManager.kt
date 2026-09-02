package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderIds
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.model.subtitle.externalSubtitleIndices
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import com.raulshma.jellyplay.feature.player.video.state.providerSubtitleEngineId
import com.raulshma.jellyplay.feature.player.video.state.providerSubtitleRowKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What [SubtitleManager] hands back after refreshing media detail following an
 * in-player subtitle download/upload — a named value object so the callback
 * call sites read instead of relying on positional `(MediaDetail, Boolean,
 * Int?)` flags.
 *
 * - [detail] — the re-fetched item detail.
 * - [attachToEngine] — true when the new server-side stream still needs to be
 *   side-loaded into the live engine (server download / upload paths); false
 *   when the caller already side-loaded a local copy itself (the provider path
 *   saves + attaches before uploading, so attaching again would duplicate the
 *   track).
 * - [newSubtitleStreamIndex] — the server `MediaStream.index` of the
 *   just-downloaded subtitle, when known — arms the ViewModel's one-shot
 *   auto-select of the new track.
 */
data class MediaDetailRefresh(
    val detail: MediaDetail,
    val attachToEngine: Boolean = true,
    val newSubtitleStreamIndex: Int? = null,
)

/**
 * Owns the in-player "Subtitle Manager" workflow: downloading server-default
 * subtitles, searching OpenSubtitles (via the server), uploading local files,
 * side-loading local files, and loading the upload/search language cultures.
 *
 * Extracted verbatim from [VideoPlayerViewModel], continuing
 * the collaborator-extraction pattern established by [TrackSelectionHelper] and
 * [TrickplayManager]). The ViewModel keeps thin public delegating functions of
 * the same names so the screen call sites are unchanged; this class performs
 * the actual work against the repositories and owns the subtitle-workflow slice
 * [SubtitleState] (download/search state) as its single home, exposed as a
 * read-only [StateFlow].
 *
 * **Item-switch semantics: the workflow state does NOT persist across
 * episodes.** [resetForItem] clears the search/download state (and cancels
 * in-flight jobs so a stale post-download refresh can't write status back into
 * the freshly-cleared state for the new item); the ViewModel's
 * `releaseInternals()` calls it — the explicit form of the implicit reset the
 * former UiState rebuild performed (none of these fields were whitelisted).
 *
 * After a successful download/upload the media detail is re-fetched and handed
 * to [onMediaDetailRefreshed], which the VM implements to re-apply the detail
 * and refresh the shared `currentMediaSource` / `mediaStreams` /
 * `detectedAspectRatio` fields (those are session state owned by the ViewModel;
 * access here is a narrow [getMediaStreams] read). For server downloads the
 * newly appeared stream's index rides along so the VM can make the fresh
 * subtitle the active track without a manual picker hunt.
 *
 * File reads (`queryFileSizeBytes` / `readAndEncode`) live here because they
 * only need the [SubtitleContentGateway] seam; they are private to this
 * class. (Wave 8C: moved to commonMain — SAF/document Uri params are strings
 * at the API boundary, so the screens keep the string forms end to end.)
 */
internal class SubtitleManager(
    /** Content-URI IO seam (wave 8C): androidMain impl reads via ContentResolver. */
    private val contentGateway: SubtitleContentGateway,
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val subtitleProviderRepository: SubtitleProviderRepository,
    private val streamingSubtitleStore: StreamingSubtitleStore,
    /** User-feedback seam (wave 8C): androidMain bridge posts to the legacy UserMessageBus. */
    private val userMessageBus: PlayerVideoMessageBus,
    private val scope: CoroutineScope,
    private val addExternalSubtitle: (SubtitleSource) -> Unit,
    private val getMediaStreams: () -> List<MediaStream>,
    private val getCurrentItemId: () -> String?,
    /**
     * Id of the media source currently playing, when known. Post-download
     * stream attribution is scoped to this source so multi-version items
     * cannot attribute another version's same-language stream.
     */
    private val getCurrentSourceId: () -> String? = { null },
    /**
     * Hands the refreshed detail to the VM after a download/upload — see
     * [MediaDetailRefresh] for the per-field contract.
     */
    private val onMediaDetailRefreshed: (MediaDetailRefresh) -> Unit,
    /**
     * In-memory [MediaDetail] snapshot held by the VM (populated at playback
     * start). Preferred over a fresh [mediaRepository.getMediaDetail] round-trip
     * so subtitle search keeps working when the Jellyfin server is unreachable
     * — the external providers (Wyzie/OpenSubtitles) only need the item's
     * TMDB/IMDb/title, all of which are present on this snapshot. See
     * [searchAllProviders].
     */
    private val getCurrentMediaDetail: () -> MediaDetail?,
    /**
     * True when the side-loaded subtitle described by [hint] is resolvable in
     * the live track picker (engine track published, or a synthetic server row
     * for its stream index). The VM wires this to
     * [TrackSelectionHelper.findSubtitleOptionFor] so "downloaded" can mean
     * "usable", not merely "saved on the server" — every engine drops a
     * side-load silently (unmappable codec, failed fetch), and marking the row
     * usable before the track lands produced a "Use" button that did nothing
     * (#144). Defaults to true so the row never blocks on the seam.
     */
    private val isSubtitleTrackAttached: (ReadySubtitleHint) -> Boolean = { true },
    /**
     * True while the app is in offline mode (manual or auto). The server-default
     * remote-subtitle list is a pure server read: offline it can never succeed,
     * and the retry chain's late failure surfaced as a global "Could not load
     * subtitle list" toast long after the hub was closed — often only after the
     * player itself (the track list the user actually saw is engine-local state
     * and unaffected). Offline, [loadRemoteSubtitles] skips the fetch silently;
     * the Get tab just has no server-default rows, like an empty result.
     */
    private val isOffline: () -> Boolean = { false },
) {

    private val _state = MutableStateFlow(SubtitleState())
    val state: StateFlow<SubtitleState> = _state.asStateFlow()

    /**
     * In-flight remote-search job. A slow "en" query landing after a
     * fast "fr" query used to overwrite the latter's results, surfacing
     * stale results for the wrong language. Cancel the prior search before
     * starting a new one.
     */
    private var searchJob: Job? = null

    /**
     * In-flight [loadRemoteSubtitles] fetch. Tracked so a reset (sheet reopen /
     * item switch / release) cancels it — otherwise the retry chain's late
     * failure would toast after the hub or even the player was gone.
     */
    private var remoteSubtitlesJob: Job? = null

    /**
     * In-flight download jobs keyed by subtitle id. The former single global
     * download flag closed the panel on pick and gave no per-subtitle feedback;
     * the sheet now stays open and tracks each download independently, so a
     * retry (or a second subtitle) can't clobber a prior in-flight job.
     * Re-tapping the same subtitle cancels only its own prior job — mirroring
     * [searchJob]'s cancellation guard, scoped per id.
     *
     * Cancelled jobs must not touch UI state: cancellation is intentional, not a
     * failure (see [downloadSubtitle]).
     */
    private val downloadJobs = mutableMapOf<String, Job>()

    fun loadRemoteSubtitles() {
        val itemId = getCurrentItemId() ?: return
        // Offline the server read can never succeed; skipping silently avoids
        // burning the retry chain on a read with no chance of success (see
        // [isOffline]). Any stale error from a prior online attempt clears too.
        if (isOffline()) {
            _state.update {
                it.copy(remoteSubtitles = emptyList(), isLoadingRemoteSubtitles = false, remoteSubtitlesError = null)
            }
            return
        }
        remoteSubtitlesJob?.cancel()
        _state.update { it.copy(isLoadingRemoteSubtitles = true, remoteSubtitlesError = null) }
        remoteSubtitlesJob = scope.launch {
            playbackRepository.getRemoteSubtitles(itemId).fold(
                onSuccess = { subs ->
                    _state.update { it.copy(remoteSubtitles = subs, isLoadingRemoteSubtitles = false) }
                },
                onFailure = { e ->
                    // Inline on the Get tab — see [SubtitleState.remoteSubtitlesError].
                    _state.update {
                        it.copy(
                            remoteSubtitles = emptyList(),
                            isLoadingRemoteSubtitles = false,
                            remoteSubtitlesError = e.message ?: "unknown error",
                        )
                    }
                },
            )
        }
    }

    fun downloadSubtitle(subtitleInfo: RemoteSubtitleInfo) {
        val itemId = getCurrentItemId() ?: return
        val subtitleId = subtitleInfo.id
        // Cancel only this subtitle's prior in-flight job — a retry (or a second
        // pick of the same id) supersedes the old job without disturbing other
        // concurrent downloads.
        downloadJobs.remove(subtitleId)?.cancel()
        // Snapshot the subtitle-stream indices present *before* the download so
        // the post-download poll can require a genuinely new stream rather than
        // matching an existing same-language subtitle (false positive).
        val preDownloadStreamIndices = currentSubtitleStreamIndices()
        markDownloadStatus(subtitleId, SubtitleDownloadState.DOWNLOADING)
        downloadJobs[subtitleId] = scope.launch {
            // Seed the snapshot from the server when the live one is empty —
            // offline (downloaded-media) sessions carry no server streams in UI
            // state. Must happen BEFORE the POST: afterwards the new stream may
            // already be visible and would be indistinguishable from a
            // pre-existing one. The download itself requires the server, so
            // this round-trip is always possible when it matters.
            val effectiveSnapshot = preDownloadStreamIndices.ifEmpty {
                seedPreDownloadStreamIndices(itemId)
            }
            // NOTE: downloadSubtitle() returns Result<Unit>, so we fold that
            // directly — `runCatching { repo.x() }` would swallow a returned
            // Result.failure into onSuccess (the failure is wrapped as the
            // success value), silently masking a failed download. This matches
            // the repository API contract (Result, not throwing).
            val result = playbackRepository.downloadSubtitle(itemId, subtitleId)
            // If superseded (a newer downloadSubtitle() for this id cancelled
            // us), do not touch UI state — the newer job owns it now and has
            // already reset the status to DOWNLOADING on entry.
            ensureActive()
            result.fold(
                onSuccess = {
                    waitForSubtitleToAppear(itemId, subtitleInfo, effectiveSnapshot)
                },
                onFailure = { e ->
                    val msg = e.message ?: "Download failed"
                    userMessageBus.error("Subtitle download failed: $msg")
                    markDownloadStatus(subtitleId, SubtitleDownloadState.FAILED, msg)
                },
            )
        }
    }

    /**
     * Polls the (cache-busted) media detail until the freshly downloaded
     * subtitle surfaces as a new `MediaStream`, then hands the refreshed detail
     * to [onMediaDetailRefreshed] (which re-applies source/track state), records
     * the new stream's hints for the "Use" action, and marks the row DOWNLOADED.
     * Returns the new stream's index, or null if it never surfaced.
     *
     * Jellyfin queues remote-subtitle downloads server-side: the
     * `POST .../Remote/Subtitles/{id}` call returns once the file is saved, but
     * the new stream is not guaranteed to appear in the item's media info on
     * the very next read. Without this poll the row would flip to "done" while
     * the subtitle is still invisible to the player — so we wait.
     *
     * The wait is two-phase. The fast phase (~9 s) covers the common case; if
     * it expires the row flips to DELAYED ("taking a while") **but the poll
     * keeps running on a slower cadence** — previously we gave up entirely
     * here, which left the session without the refreshed detail/track list and
     * forced the user to exit and reopen the player to see the subtitle even
     * though the download had actually succeeded server-side. A late arrival
     * in the slow phase runs exactly the same success path as a fast one
     * (refresh + select + DOWNLOADED + toast).
     *
     * The forced [getMediaDetail] read is load-bearing:
     * [getMediaDetail] is a single-flight cached read with a TTL, so without
     * the force flag the poll would keep returning the stale pre-download
     * detail and never see the new stream.
     */
    private suspend fun waitForSubtitleToAppear(
        itemId: String,
        subtitleInfo: RemoteSubtitleInfo,
        preDownloadStreamIndices: Set<Int>,
    ): Int? {
        val subtitleId = subtitleInfo.id
        pollUntilAppeared(
            itemId,
            subtitleInfo,
            preDownloadStreamIndices,
            attempts = SUBTITLE_APPEAR_MAX_ATTEMPTS,
            delayMs = SUBTITLE_APPEAR_POLL_DELAY_MS,
            delayBeforePoll = false,
        )?.let { return it }
        // Budget elapsed without the stream surfacing. The server may still be
        // processing, so this is a soft state, not a hard failure — but unlike
        // a terminal failure we keep watching on a slower cadence below so a
        // late-arriving stream completes the flow without user intervention.
        userMessageBus.info("Subtitle is taking a while to appear on the server")
        markDownloadStatus(subtitleId, SubtitleDownloadState.DELAYED)
        return pollUntilAppeared(
            itemId,
            subtitleInfo,
            preDownloadStreamIndices,
            attempts = SUBTITLE_APPEAR_LATE_MAX_ATTEMPTS,
            delayMs = SUBTITLE_APPEAR_LATE_POLL_DELAY_MS,
            // The DELAYED notice already spent time; sleep before polling.
            delayBeforePoll = true,
        )
    }

    /**
     * One phase of [waitForSubtitleToAppear]: up to [attempts] cache-busted
     * polls [delayMs] apart (no sleep after the last), returning the new
     * stream's index or null when it never surfaced. The fast phase polls
     * immediately and sleeps between attempts ([delayBeforePoll] = false); the
     * slow phase sleeps first.
     */
    private suspend fun pollUntilAppeared(
        itemId: String,
        subtitleInfo: RemoteSubtitleInfo,
        preDownloadStreamIndices: Set<Int>,
        attempts: Int,
        delayMs: Long,
        delayBeforePoll: Boolean,
    ): Int? {
        repeat(attempts) { attempt ->
            if (delayBeforePoll && attempt < attempts - 1) {
                delay(delayMs)
            }
            currentCoroutineContext().ensureActive()
            pollForSubtitle(itemId, subtitleInfo, preDownloadStreamIndices)?.let { return it }
            if (!delayBeforePoll && attempt < attempts - 1) {
                delay(delayMs)
            }
        }
        return null
    }

    /**
     * One cache-busted poll attempt of the media detail. Returns the newly
     * appeared subtitle stream's index and completes the success side effects
     * (detail refresh callback, ready-hints, toast, DOWNLOADED status), or null
     * when the stream has not surfaced yet.
     */
    private suspend fun pollForSubtitle(
        itemId: String,
        subtitleInfo: RemoteSubtitleInfo,
        preDownloadStreamIndices: Set<Int>,
    ): Int? {
        val subtitleId = subtitleInfo.id
        // Force the read so a stale single-flight cache hit can't mask the
        // new stream the server has just attached.
        val detail = mediaRepository.getMediaDetail(itemId, force = true).getOrNull()
        currentCoroutineContext().ensureActive()
        // A retry of an id we already downloaded once: its stream index is in
        // the snapshot now (the server already had it when the snapshot was
        // taken), so the "genuinely new index" rule would never match it.
        // Accept the recorded index as attributable too.
        val priorStreamIndex = _state.value.readySubtitles[subtitleId]?.serverStreamIndex
        val appeared = detail?.let {
            findAppearedSubtitleStream(it, subtitleInfo, preDownloadStreamIndices, priorStreamIndex)
        }
        if (detail == null || appeared == null) return null
        val hint = ReadySubtitleHint(
            trackId = externalSubtitleTrackId(appeared.index),
            serverStreamIndex = appeared.index,
        )
        onMediaDetailRefreshed(
            MediaDetailRefresh(
                detail = detail,
                attachToEngine = true,
                newSubtitleStreamIndex = appeared.index,
            ),
        )
        recordReadySubtitle(subtitleId, hint)
        verifyThenMark(
            statusKey = subtitleId,
            hint = hint,
            successMessage = "Subtitle downloaded",
            // Distinct from the provider path's notice: here the subtitle is
            // durably on the server; only the local attach failed.
            failureNotice = "Subtitle saved to the server but could not be attached to playback",
        )
        return appeared.index
    }

    /** Records the "Use"-action hints for a row that became usable. */
    private fun recordReadySubtitle(rowKey: String, hint: ReadySubtitleHint) {
        _state.update { it.copy(readySubtitles = it.readySubtitles + (rowKey to hint)) }
    }

    /**
     * The subtitle stream in [detail] attributable to the just-downloaded
     * [subtitleInfo], or null. Prefers a stream whose index is *not* in
     * [preDownloadStreamIndices] (i.e. genuinely new), to avoid a false
     * positive on an existing same-language subtitle; among candidates the
     * highest index wins (the server appends). Falls back to a plain language
     * match when no snapshot was captured (defensive). [priorStreamIndex] —
     * when this is a retry of an id downloaded earlier in this session, its
     * recorded stream index is attributable even though the snapshot now
     * contains it. The language is compared case-insensitively against the
     * remote info's three-letter ISO name (server streams use the same form),
     * then the human-readable name.
     */
    private fun findAppearedSubtitleStream(
        detail: MediaDetail,
        subtitleInfo: RemoteSubtitleInfo,
        preDownloadStreamIndices: Set<Int>,
        priorStreamIndex: Int? = null,
    ): MediaStream? {
        // Attribute the new stream to the PLAYING source when known —
        // flat-mapping every media source could match a same-language stream
        // from another version whose index collides.
        val playingSourceId = getCurrentSourceId()
        val sources = if (playingSourceId != null) {
            detail.mediaSources.filter { it.id == playingSourceId }.ifEmpty { detail.mediaSources }
        } else {
            detail.mediaSources
        }
        val subtitleStreams = sources.flatMap { it.mediaStreams }
            .filter { it.type == StreamType.SUBTITLE }
        if (subtitleStreams.isEmpty()) return null
        val targetLang = subtitleInfo.threeLetterISOLanguageName.ifBlank { subtitleInfo.language.orEmpty() }
        val byLanguage = if (targetLang.isBlank()) {
            subtitleStreams
        } else {
            subtitleStreams.filter { stream ->
                stream.language?.equals(targetLang, ignoreCase = true) == true
            }
        }
        // If a snapshot of pre-download indices exists, require a genuinely new
        // index whose language matches — otherwise an existing same-language
        // subtitle would read as a false positive the instant the download call
        // returns. A retry's recorded index stays attributable (see above).
        val candidates = if (preDownloadStreamIndices.isNotEmpty()) {
            byLanguage.filter { it.index !in preDownloadStreamIndices || it.index == priorStreamIndex }
        } else {
            // No snapshot (e.g. the pre-download seeding failed): a plain
            // language match is the best we can do.
            byLanguage
        }
        return candidates.maxByOrNull { it.index }
    }

    /**
     * Fetches the server's subtitle-stream indices for [itemId] so
     * [downloadSubtitle] can snapshot the pre-download state on sessions whose
     * UI-state stream list is empty (offline/downloaded media). The download
     * itself requires the server, so this is only unreachable when the whole
     * flow is; an empty set then degrades [findAppearedSubtitleStream] to the
     * plain language match, as before.
     */
    private suspend fun seedPreDownloadStreamIndices(itemId: String): Set<Int> =
        mediaRepository.getMediaDetail(itemId, force = true).getOrNull()
            ?.mediaSources
            ?.flatMap { it.mediaStreams }
            ?.filter { it.type == StreamType.SUBTITLE }
            ?.map { it.index }
            ?.toSet()
            ?: emptySet()

    /**
     * Waits (bounded) for the side-loaded subtitle described by [hint] to
     * become resolvable in the live track picker. The engine side-load is
     * fire-and-forget and its track-list republish is asynchronous, so
     * attachment is observed through [isSubtitleTrackAttached], not assumed.
     */
    private suspend fun waitForTrackAttachment(hint: ReadySubtitleHint): Boolean {
        repeat(TRACK_ATTACH_MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(TRACK_ATTACH_POLL_DELAY_MS)
            currentCoroutineContext().ensureActive()
            if (isSubtitleTrackAttached(hint)) return true
        }
        return false
    }

    /**
     * Verify-then-mark tail shared by every side-load download path (#144):
     * the engine side-load → track-list republish round-trip is asynchronous,
     * and every engine drops a side-load silently when it fails (unmappable
     * codec, engine-side fetch error), so marking a row usable before the
     * track lands produced a "Use" button that did nothing. Waits (bounded)
     * for [hint] to become resolvable via [isSubtitleTrackAttached]; on
     * success surfaces [successMessage] and records [successState]
     * ([successError]); on miss surfaces [failureNotice] and marks
     * [statusKey] FAILED with [ATTACH_FAILED_MESSAGE].
     */
    private suspend fun verifyThenMark(
        statusKey: String,
        hint: ReadySubtitleHint,
        successMessage: String,
        failureNotice: String,
        successState: SubtitleDownloadState = SubtitleDownloadState.DOWNLOADED,
        successError: String? = null,
    ) {
        if (waitForTrackAttachment(hint)) {
            userMessageBus.info(successMessage)
            markDownloadStatus(statusKey, successState, successError)
        } else {
            userMessageBus.error(failureNotice)
            markDownloadStatus(statusKey, SubtitleDownloadState.FAILED, ATTACH_FAILED_MESSAGE)
        }
    }

    /** Current subtitle-stream indices across all media sources, for the snapshot. */
    private fun currentSubtitleStreamIndices(): Set<Int> =
        getMediaStreams().filter { it.type == StreamType.SUBTITLE }.map { it.index }.toSet()

    /**
     * Sets a download status on the owned state. [statusKey] is the subtitle
     * id for server downloads, or the composite "provider:id" key
     * ([providerSubtitleRowKey]) for external-provider downloads — both live
     * in [SubtitleState.downloadingSubtitles] under one key space.
     */
    private fun markDownloadStatus(statusKey: String, state: SubtitleDownloadState, errorMessage: String? = null) {
        _state.update {
            it.copy(
                downloadingSubtitles = it.downloadingSubtitles + (statusKey to SubtitleDownloadStatus(statusKey, state, errorMessage)),
            )
        }
    }

    /** Removes [subtitleId]'s status entry (e.g. on a fresh retry reset). */
    private fun clearDownloadStatus(subtitleId: String) {
        _state.update {
            it.copy(downloadingSubtitles = it.downloadingSubtitles - subtitleId)
        }
    }

    fun addLocalSubtitle(uri: String, fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val codec = when (ext) {
            "srt" -> "srt"
            "ass", "ssa" -> "ass"
            "vtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            else -> null
        }

        val label = fileName.substringBeforeLast('.').ifBlank { "Local subtitle" }
        val source = SubtitleSource(
            url = uri.toString(),
            label = label,
            language = null,
            mimeType = null,
            codec = codec,
            isDefault = false,
            isForced = false,
            id = "local:${System.currentTimeMillis()}",
        )
        addExternalSubtitle(source)
        userMessageBus.info("Local subtitle loaded")
    }

    /**
     * Resets the Subtitle Manager's search/cultures state. Called when the sheet
     * opens so results from a previous item don't leak into a new one. Cultures
     * are reloaded on demand since they may be item-scoped on some servers.
     * (Sheet-open semantics: the server-default list and provider config stay.)
     */
    fun resetSubtitleManagerState() {
        // Cancel any in-flight downloads so a stale post-download refresh can't
        // write status back into the freshly-cleared sheet state for a new item.
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        searchJob?.cancel()
        providerSearchJob?.cancel()
        remoteSubtitlesJob?.cancel()
        _state.update {
            it.copy(
                searchedSubtitles = emptyList(),
                hasSearchedSubtitles = false,
                isSearchingSubtitles = false,
                subtitleSearchError = null,
                subtitleCultures = emptyList(),
                downloadingSubtitles = emptyMap(),
                providerSearchResults = emptyList(),
                providerSearchErrors = emptyMap(),
                readySubtitles = emptyMap(),
                remoteSubtitlesError = null,
            )
        }
    }

    /**
     * Item-switch reset: restores the default [SubtitleState] wholesale — the
     * explicit form of the implicit reset the former UiState rebuild performed
     * (none of these fields were whitelisted, so every one of them reset).
     * Cancels the in-flight jobs first: a stale post-download refresh must not
     * write status back into the freshly-cleared state for the new item.
     */
    fun resetForItem() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        searchJob?.cancel()
        providerSearchJob?.cancel()
        remoteSubtitlesJob?.cancel()
        _state.value = SubtitleState()
    }

    /**
     * Loads the language cultures the server understands for subtitle
     * upload/search selection. Idempotent: a no-op once cultures are already
     * populated for the current item (e.g. across tab switches / reopens).
     */
    fun loadSubtitleCultures() {
        if (_state.value.subtitleCultures.isNotEmpty()) return
        val itemId = getCurrentItemId() ?: return
        scope.launch {
            playbackRepository.getSubtitleCultures(itemId).fold(
                onSuccess = { cultures -> _state.update { it.copy(subtitleCultures = cultures) } },
                onFailure = {
                    // An empty cultures list just yields an empty dropdown;
                    // users can still type a code manually, so no error toast.
                    _state.update { it.copy(subtitleCultures = emptyList()) }
                },
            )
        }
    }

    /**
     * Language-scoped remote subtitle search (OpenSubtitles via the server).
     * Results populate the Search tab and are kept separate from the Download
     * tab's server-default list. A failure surfaces as
     * [SubtitleState.subtitleSearchError] (distinct from an empty result) so
     * the UI can invite retry rather than implying "no subtitles exist".
     */
    fun searchRemoteSubtitles(language: String) {
        val itemId = getCurrentItemId() ?: return
        searchJob?.cancel()
        _state.update {
            it.copy(isSearchingSubtitles = true, hasSearchedSubtitles = false, searchedSubtitles = emptyList(), subtitleSearchError = null)
        }
        searchJob = scope.launch {
            playbackRepository.searchRemoteSubtitles(itemId, language).fold(
                onSuccess = { subs ->
                    _state.update {
                        it.copy(searchedSubtitles = subs, isSearchingSubtitles = false, hasSearchedSubtitles = true, subtitleSearchError = null)
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isSearchingSubtitles = false,
                            hasSearchedSubtitles = false,
                            subtitleSearchError = e.message ?: "Search failed",
                        )
                    }
                },
            )
        }
    }

    // region Multi-provider search (Jellyfin + Wyzie + OpenSubtitles)

    /**
     * Loads the user's configured providers into state so the Search tab can
     * decide whether to show provider filter chips. Called when the
     * SubtitleManager sheet opens. Jellyfin is always present.
     */
    fun loadConfiguredProviders() {
        scope.launch {
            val configured = subtitleProviderRepository.configuredProviders().first()
            _state.update { it.copy(configuredSubtitleProviders = configured) }
        }
    }

    /**
     * Concurrent cross-provider search: Jellyfin (server-scoped by itemId +
     * language) + every configured external provider (Wyzie/OpenSubtitles,
     * searched by TMDB/IMDb/title from the item's provider ids). Results are
     * merged into [SubtitleState.providerSearchResults] with per-provider
     * error chips in [SubtitleState.providerSearchErrors] — one bad key
     * never blanks the others.
     *
     * A separate job from [searchJob] so the two search paths (legacy
     * Jellyfin-only vs. multi-provider) can't cancel each other.
     */
    private var providerSearchJob: Job? = null

    fun searchAllProviders(language: String) {
        val itemId = getCurrentItemId() ?: return
        providerSearchJob?.cancel()
        _state.update {
            it.copy(
                isSearchingSubtitles = true,
                hasSearchedSubtitles = false,
                providerSearchResults = emptyList(),
                providerSearchErrors = emptyMap(),
            )
        }
        providerSearchJob = scope.launch {
            // Prefer the in-memory detail snapshot held by the VM (loaded at
            // playback start); only hit the network when it is missing. This
            // keeps external-provider search working when the Jellyfin server is
            // unreachable — the snapshot carries the TMDB/IMDb/title the
            // external providers need, and [SubtitleProviderRepositoryImpl.searchAllStreaming]
            // already isolates a Jellyfin failure (returns empty rather than
            // aborting) so a server outage only surfaces as an empty Jellyfin
            // section, not a blank result. The previous logic bailed out here
            // with "Could not load item details", needlessly blocking Wyzie and
            // OpenSubtitles.
            val detail = getCurrentMediaDetail()
                ?: mediaRepository.getMediaDetail(itemId).getOrNull()
            val query = detail?.let { SubtitleProviderIds.buildQuery(it) }
                // No detail at all (e.g. playback started without one and the
                // fetch failed): search external providers by title only. With
                // no ids/title the providers return empty, but we still reach
                // them so a misconfigured-but-reachable Jellyfin never masks a
                // reachable Wyzie/OpenSubtitles.
                ?: SubtitleQuery(query = null)

            // Centralized Jellyfin + external merge + sort. Streaming: each
            // provider's results/errors land in state the instant it resolves, so
            // a slow/retrying provider can no longer gate its siblings.
            val merged = subtitleProviderRepository.searchAllStreaming(
                query.copy(languages = listOf(language)),
                itemId,
                language,
            ) { partial ->
                _state.update {
                    it.copy(
                        providerSearchResults = partial.results,
                        providerSearchErrors = partial.errors,
                    )
                }
            }
            _state.update {
                it.copy(
                    isSearchingSubtitles = false,
                    hasSearchedSubtitles = true,
                    providerSearchResults = merged.results,
                    providerSearchErrors = merged.errors,
                )
            }
        }
    }

    /**
     * Downloads a subtitle from a [SubtitleSearchResult]. Routes by provider:
     *
     * - [SubtitleProviderKind.JELLYFIN]: delegates to the existing server-side
     *   [downloadSubtitle] path (POST + media-detail poll) using the preserved
     *   [RemoteSubtitleInfo] in [SubtitleSearchResult.jellyfinInfo].
     * - External providers: fetches the bytes via the repository, side-loads
     *   them into the live engine for immediate use, **and uploads them to the
     *   Jellyfin server** so the subtitle persists as a `MediaStream` (survives
     *   player close/reopen). The upload mirrors the editor's provider path and
     *   the Jellyfin download path; without it the bytes lived only in a
     *   disposable cache file plus a one-shot side-load, so the subtitle
     *   vanished the next time the media was played. After the upload lands the
     *   detail cache is invalidated and re-fetched so the new stream surfaces in
     *   `mediaStreams` (and thus in `buildExternalSubtitles` on the next
     *   playback) and in the track picker right away. Per-id status mirrors the
     *   Jellyfin download flow.
     */
    fun downloadProviderSubtitle(result: SubtitleSearchResult) {
        val itemId = getCurrentItemId() ?: return
        val statusKey = providerSubtitleRowKey(result.provider, result.id)
        when (result.provider) {
            SubtitleProviderKind.JELLYFIN -> {
                val jellyfinInfo = result.jellyfinInfo ?: return
                downloadSubtitle(jellyfinInfo)
            }
            else -> {
                downloadJobs.remove(statusKey)?.cancel()
                markDownloadStatus(statusKey, SubtitleDownloadState.DOWNLOADING)
                downloadJobs[statusKey] = scope.launch {
                    val fileResult = subtitleProviderRepository.downloadExternal(result)
                    ensureActive()
                    fileResult.fold(
                        onSuccess = { file ->
                            val codec = codecForFormat(file.format)
                            // Persist durably (filesDir, survives replay) so the
                            // subtitle is usable on-device even when the server is
                            // unreachable. The returned SavedSubtitle resolves to
                            // the on-disk durable file we side-load below — the
                            // previous disposable cache copy is no longer needed.
                            val saved = streamingSubtitleStore.save(
                                itemId = itemId,
                                provider = result.provider,
                                providerSubtitleId = result.id,
                                fileName = file.fileName,
                                language = file.language ?: result.language,
                                codec = codec ?: file.format,
                                isForced = result.isForced,
                                isHearingImpaired = result.isHearingImpaired,
                                bytes = file.bytes,
                            )
                            val durableFile = streamingSubtitleStore.fileFor(itemId, saved)
                            ensureActive()
                            // Side-load the durable copy immediately so the subtitle
                            // is usable in this playback session without waiting for
                            // the server round-trip.
                            val source = SubtitleSource(
                                url = durableFile.toURI().toString(),
                                label = result.displayName,
                                language = result.language,
                                mimeType = mimeForCodec(codec),
                                codec = codec,
                                isDefault = false,
                                isForced = result.isForced,
                                id = providerSubtitleEngineId(statusKey),
                            )
                            addExternalSubtitle(source)
                            // The side-loaded track carries source.id, so the
                            // sheet's "Use" action can resolve it exactly.
                            val hint = ReadySubtitleHint(trackId = source.id)
                            recordReadySubtitle(statusKey, hint)

                            // Verify-then-mark via [verifyThenMark], same
                            // contract as the Jellyfin remote path. The hint
                            // is trackId-only, so it observes the engine's own
                            // track — not a synthetic server row.
                            val attachFailureNotice =
                                "Subtitle downloaded but could not be attached to playback"

                            // Best-effort: upload to the Jellyfin server so the
                            // subtitle is also persisted as a MediaStream (and thus
                            // available across devices / future sessions without the
                            // local durable copy). Failure here is NOT fatal — the
                            // durable on-device copy still backs the side-load and
                            // survives replay via the streaming-subtitle store.
                            // KMP seam (wave 8C): java.util.Base64 replaces
                            // android.util.Base64.NO_WRAP — identical output.
                            val base64 = java.util.Base64.getEncoder().encodeToString(file.bytes)
                            val preUploadExternalIndices = getMediaStreams().externalSubtitleIndices()
                            val uploadResult = playbackRepository.uploadSubtitle(
                                itemId = itemId,
                                data = base64,
                                fileName = file.fileName,
                                language = file.language ?: result.language,
                                isForced = result.isForced,
                                isHearingImpaired = result.isHearingImpaired,
                            )
                            ensureActive()
                            uploadResult.fold(
                                onSuccess = {
                                    // Force a re-fetch so the freshly attached
                                    // stream lands in the cached media detail
                                    // (and thus in buildExternalSubtitles on the
                                    // next playback) and in the track picker
                                    // now — getMediaDetail is a single-flight
                                    // cached read, so without the force flag it
                                    // would keep returning the pre-upload
                                    // snapshot.
                                    mediaRepository.getMediaDetail(itemId, force = true).getOrNull()?.let { detail ->
                                        // attachToEngine = false: the provider
                                        // download already side-loaded its local
                                        // copy above; attaching the server echo
                                        // would list the same subtitle twice.
                                        onMediaDetailRefreshed(MediaDetailRefresh(detail, attachToEngine = false))
                                        detail.mediaSources.firstOrNull()?.mediaStreams?.let { streams ->
                                            streamingSubtitleStore.attributeUploadedSubtitle(
                                                itemId = itemId,
                                                saved = saved,
                                                streamsAfterUpload = streams,
                                                preUploadExternalIndices = preUploadExternalIndices,
                                            )
                                        }
                                    }
                                    verifyThenMark(
                                        statusKey = statusKey,
                                        hint = hint,
                                        successMessage = "Subtitle added",
                                        failureNotice = attachFailureNotice,
                                    )
                                },
                                onFailure = { e ->
                                    // Server unreachable / upload failed — the subtitle
                                    // is still usable on-device. Surface a softer
                                    // device-only status rather than a hard failure.
                                    val msg = e.message ?: "Server unavailable"
                                    verifyThenMark(
                                        statusKey = statusKey,
                                        hint = hint,
                                        successMessage = "Saved to device only: $msg",
                                        failureNotice = attachFailureNotice,
                                        successState = SubtitleDownloadState.DOWNLOADED_DEVICE_ONLY,
                                        successError = msg,
                                    )
                                },
                            )
                        },
                        onFailure = { e ->
                            val msg = e.message ?: "Download failed"
                            userMessageBus.error("Subtitle download failed: $msg")
                            markDownloadStatus(statusKey, SubtitleDownloadState.FAILED, msg)
                        },
                    )
                }
            }
        }
    }

    private fun codecForFormat(format: String?): String? = when (format?.lowercase()) {
        "srt", "subrip" -> "srt"
        "ass", "ssa" -> "ass"
        "vtt", "webvtt" -> "vtt"
        "ttml", "dfxp" -> "ttml"
        else -> null
    }

    private fun mimeForCodec(codec: String?): String? = when (codec) {
        "srt" -> "application/x-subrip"
        "ass" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        "ttml" -> "application/ttml+xml"
        else -> null
    }

    /**
     * Seeds [SubtitleState.defaultSearchLanguage] from the user's preferred
     * subtitle language (ISO 639-2/3). The former projection lived in
     * [SettingsProjector]; it moves here because the field's home moved.
     * Guarded so an unrelated preference emission does not re-emit the flow.
     */
    fun seedDefaultSearchLanguage(language: String) {
        if (_state.value.defaultSearchLanguage != language) {
            _state.update { it.copy(defaultSearchLanguage = language) }
        }
    }

    // endregion

    /**
     * Uploads a local subtitle file to the current item, then reloads the
     * media detail so the new stream appears in the track list — mirroring
     * [downloadSubtitle]'s refresh and the editor's upload path.
     *
     * The file size is checked up front against [MAX_SUBTITLE_UPLOAD_BYTES] (via
     * OpenableColumns.SIZE) so an oversized pick is rejected before the whole
     * file — and its ~1.33× Base64 expansion — are loaded into memory. This
     * matters on low-RAM TV devices where a stray large pick could OOM.
     */
    fun uploadSubtitle(uri: String, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) {
        val itemId = getCurrentItemId() ?: return
        _state.update { it.copy(isUploadingSubtitle = true) }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val size = queryFileSizeBytes(uri)
                    if (size in 1..MAX_SUBTITLE_UPLOAD_BYTES) {
                        // Expected path: a real subtitle file well under the cap.
                        readAndEncode(uri)
                    } else if (size > MAX_SUBTITLE_UPLOAD_BYTES) {
                        throw java.io.IOException("Subtitle file is too large (${size / 1024} KB). Limit is ${MAX_SUBTITLE_UPLOAD_BYTES / 1024} KB.")
                    } else {
                        // SIZE unknown (some providers return 0/null) — read the file
                        // but reject it if it is genuinely empty. A 0-byte pick is
                        // never a usable subtitle and would surface as a confusing
                        // server error after Base64-encoding an empty string.
                        val bytes = readBytes(uri)
                        if (bytes.isEmpty()) {
                            throw java.io.IOException("Selected subtitle file is empty")
                        }
                        java.util.Base64.getEncoder().encodeToString(bytes)
                    }
                }
            }.mapCatching { base64 ->
                playbackRepository.uploadSubtitle(itemId, base64, fileName, language, isForced, isHearingImpaired).getOrThrow()
            }
            _state.update { it.copy(isUploadingSubtitle = false) }
            result.onSuccess {
                // Refresh media detail so the uploaded track surfaces in the
                // subtitle track list — same approach as downloadSubtitle().
                // Forced: getMediaDetail is a single-flight cached read, and
                // without the force flag the cache keeps serving the
                // pre-upload snapshot (see waitForSubtitleToAppear).
                mediaRepository.getMediaDetail(itemId, force = true).getOrNull()?.let { detail ->
                    onMediaDetailRefreshed(MediaDetailRefresh(detail))
                }
                userMessageBus.info("Subtitle uploaded")
            }.onFailure { e ->
                userMessageBus.error(e.message ?: "Failed to upload subtitle")
            }
        }
    }

    /**
     * Subtitle upload size cap (2 MB). Real subtitle files are tens of KB at
     * most; this comfortably rejects a mis-selected large asset while not
     * blocking any legitimate subtitle. See [uploadSubtitle].
     */
    private val MAX_SUBTITLE_UPLOAD_BYTES = 2L * 1024 * 1024

    /**
     * How many times to re-fetch (cache-busted) media detail while waiting for a
     * freshly downloaded subtitle stream to surface in the fast phase. See
     * [waitForSubtitleToAppear].
     */
    private val SUBTITLE_APPEAR_MAX_ATTEMPTS = 6

    /**
     * Delay between media-detail polls during the fast phase. Jellyfin queues
     * the download server-side, so the new stream typically surfaces within the
     * first couple of retries. See [waitForSubtitleToAppear].
     */
    private val SUBTITLE_APPEAR_POLL_DELAY_MS = 1500L

    /**
     * Slow-phase poll attempts after DELAYED. A server that finishes the
     * subtitle download late still completes the full success path (refresh +
     * select + DOWNLOADED) without the user having to leave playback. See
     * [waitForSubtitleToAppear].
     */
    private val SUBTITLE_APPEAR_LATE_MAX_ATTEMPTS = 8

    /** Delay between slow-phase media-detail polls. See [waitForSubtitleToAppear]. */
    private val SUBTITLE_APPEAR_LATE_POLL_DELAY_MS = 5000L

    /**
     * Bounded wait for the engine to publish a just-side-loaded subtitle track
     * before the row is marked usable. Covers the async side-load →
     * track-list republish round-trip (ExoPlayer re-prepare, mpv's 500 ms
     * delayed track refresh); a side-load that fails never lands, and the
     * row reports FAILED instead of a dead "Use". See [waitForTrackAttachment].
     */
    private val TRACK_ATTACH_MAX_ATTEMPTS = 12

    /** Delay between track-attachment polls. See [waitForTrackAttachment]. */
    private val TRACK_ATTACH_POLL_DELAY_MS = 500L

    /**
     * Row-level FAILED message when a side-loaded track never became
     * resolvable in the picker (#144 verify-then-mark). Shared by both
     * download paths — see [verifyThenMark].
     */
    private val ATTACH_FAILED_MESSAGE = "Subtitle could not be attached to playback"

    /** Returns the byte size of [uri] via OpenableColumns.SIZE, or 0 if unknown. */
    private fun queryFileSizeBytes(uri: String): Long = contentGateway.queryFileSizeBytes(uri)

    /** Reads [uri] fully and Base64-encodes it (NO_WRAP). Throws on read failure. */
    private fun readAndEncode(uri: String): String {
        val bytes = readBytes(uri)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    /** Reads [uri] fully into a byte array. Throws on read failure. */
    private fun readBytes(uri: String): ByteArray = contentGateway.readBytes(uri)
}
