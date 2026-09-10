package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.ItemPlaybackPreference
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelFormatter
import com.raulshma.jellyplay.feature.player.video.engine.TrackLabelInfo
import com.raulshma.jellyplay.feature.player.video.state.ReadySubtitleHint
import com.raulshma.jellyplay.feature.player.video.state.TrackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The track-selection *hub*: builds the audio/subtitle picker rows from engine
 * + server stream data, drives the side-effecting consequences of a selection
 * (`engine.selectTrack`, latch the held-selection guard, persist the per-item
 * choice, mutate/hydrate the cross-episode memory), and consumes the
 * pending-navigation stream index.
 *
 * The pure *decision* — *which* track plays? — is delegated to
 * [trackSelectionPolicy]. That keeps this hub free of the precedence ladder
 * (pending → stored → scoring → type-specific → matcher → fallback), which now
 * has a first-class, unit-tested home. See [TrackSelectionPolicy].
 *
 * **State ownership:** this class is the single home of the track slice
 * [TrackState] (the picker lists + the per-item override and per-series
 * preference flags), exposed as a read-only [StateFlow]. The server
 * `mediaStreams` it enriches from are *session* state owned by the ViewModel —
 * access is a narrow [getMediaStreams] read, never a full-UiState handle.
 *
 * **Item-switch semantics: track state does NOT persist across episodes.**
 * [resetForItem] clears the whole [TrackState] — none of these fields were on
 * the former reset whitelist, so the implicit reset (fresh UiState) became this
 * explicit command. The internal cross-episode *memory* (remembered tracks)
 * intentionally survives a same-series episode change via [setPendingStreams].
 */
internal class TrackSelectionHelper(
    private val engineStore: PlayerEngineStore,
    private val subtitleStore: SubtitleLanguageStore,
    private val trackSelectionPolicy: TrackSelectionPolicy = TrackSelectionPolicy(),
    private val getEngine: () -> MediaEngine?,
    private val getMediaStreams: () -> List<MediaStream>,
    private val getCurrentItemId: () -> String?,
    private val getCurrentSeriesId: () -> String?,
    private val getPlayMethod: () -> PlayMethod,
    private val onReloadForStreamChange: (selection: MediaStreamSelection) -> Unit,
    private val playbackPreferenceResolver: ItemPlaybackPreferenceResolver,
    // Persists the remembered track to the series-scope preference row (G5).
    // Default no-op so unit tests that don't exercise persistence compile unchanged.
    private val persistRememberedTrack: (TrackType, RememberedTrack?) -> Unit = { _, _ -> },
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()

    private var selectedSubtitleTrackIndex: Int? = null
    private var selectedAudioTrackIndex: Int? = null

    // Last track of each type the user (or auto-resolution) selected, remembered
    // so the NEXT episode can score its tracks against it (cross-episode track
    // scoring). Cleared when the
    // series changes so a different show's layout doesn't bleed in. Used only as
    // a pre-pass before the language-rule fallback; the language rule remains the
    // safety net when scoring finds no confident (≥3) match.
    //
    // [com.raulshma.jellyplay.core.model.RememberedTrack] per type. The index is
    // the track's position within its language group at selection time, so the
    // scorer's layout-stability signal (+1) can actually fire (it was being fed
    // -1 before, which dead-coded it). Persisted to the series-scope preference
    // row via [persistRememberedTrack] so it survives an app restart.
    private var rememberedAudioTrack: RememberedTrack? = null
    private var rememberedSubtitleTrack: RememberedTrack? = null

    // A selection (auto or manual) is "held" once applied via selectAudioTrack /
    // selectSubtitleTrack. While held, updateTracksFromEngine does NOT re-run the
    // stored/pending/preference resolution that can otherwise flip it — that
    // re-resolution was the cause of the "subtitle flashes then resets" bug on
    // offline playback (empty mediaStreams → resolveMediaStreamIndex returned
    // null → the next availableTracks emission re-resolved and dropped to Off).
    // Cleared on a fresh item load (setPendingStreams), an explicit reset, and
    // an engine recreation (onEngineRecreated — engine indices don't survive
    // the swap, but the stored server-stream keys do).
    private var audioSelectionHeld = false
    private var subtitleSelectionHeld = false

    @Suppress("DEPRECATION")
    private var pendingSubtitleStreamIndex: Int? = null
    private var pendingAudioStreamIndex: Int? = null

    /**
     * One-shot "select the freshly downloaded subtitle once it shows up" intent.
     * After an in-player download the side-load into the engine is asynchronous
     * (ExoPlayer re-prepares the media item; mpv refreshes its track list on a
     * delay), so at the moment the download completes the picker list usually
     * does not contain the new track yet. This pending intent survives across
     * [updateTracksFromEngine] runs and is consumed the first run whose rebuilt
     * list resolves it, bounded by [PENDING_SERVER_SUBTITLE_MAX_ATTEMPTS] runs
     * so a side-load that never lands cannot hijack later selections. Cleared on
     * item switch, reset and engine recreation (a new engine renumbers tracks).
     */
    private var pendingServerSubtitleHint: ReadySubtitleHint? = null
    private var pendingServerSubtitleAttempts = 0

    fun setPendingStreams(selection: MediaStreamSelection?) {
        pendingAudioStreamIndex = selection?.audioStreamIndex
        pendingSubtitleStreamIndex = selection?.subtitleStreamIndex
        // New item: a selection has not yet been applied for it.
        audioSelectionHeld = false
        subtitleSelectionHeld = false
        // G5: forget the cross-episode track memory when the series changes, so
        // a different show's track layout doesn't bleed into the new one. Same
        // series (next episode) keeps the memory — that is the whole point.
        val series = getCurrentSeriesId()
        if (series != trackedSeriesId) {
            trackedSeriesId = series
            rememberedAudioTrack = null
            rememberedSubtitleTrack = null
        }
    }

    /** Series id the remembered track selections belong to. */
    private var trackedSeriesId: String? = null

    /**
     * Re-resolves the per-item / per-series language preference for the current
     * item/series. Call when a new item loads or after a preference is
     * saved/deleted, then [updateTracksFromEngine] to reapply. The read is
     * async; [updateTracksFromEngine] reads the cached value.
     */
    fun refreshPlaybackPreferences() {
        playbackPreferenceResolver.refresh()
    }

    fun selectAudioTrack(option: TrackOption, isUserOverride: Boolean = true) {
        // Server-origin audio track (transcode/direct-stream): mpv cannot
        // switch audio in-place on a transcoded HLS manifest. Re-resolve
        // playback with the new audioStreamIndex and reload the engine at the
        // current position. The picker refreshes once the new stream loads.
        if (selectServerTrack(option, TrackType.AUDIO, isUserOverride)) return
        val engine = getEngine() ?: return
        engine.selectTrack(TrackType.AUDIO, option.index)
        selectedAudioTrackIndex = if (option.index < 0) null else option.index
        // Latch the held state only for a real track (>= 0) or an explicit user
        // override (e.g. the user deliberately turned it off). An auto fallback
        // to "Default"/"Off" while no tracks exist yet must stay unlatched so a
        // later track emission (e.g. offline sidecar subs attached post-load)
        // can still resolve the language/preference selection.
        audioSelectionHeld = isUserOverride || option.index >= 0
        _state.update { state ->
            val isDefault = option.index < 0
            state.copy(audioTracks = state.audioTracks.map { track ->
                val matches = track.index == option.index
                val isDefaultTrack = track.index < 0
                track.copy(isSelected = if (isDefault) isDefaultTrack else matches)
            })
        }
        // Remember a real (>= 0) audio selection so the next episode can score
        // against it (G5). "Off"/Default selections don't carry forward intent.
        if (option.index >= 0 && option.index < SERVER_TRACK_INDEX_BASE) {
            val tracks = _state.value.audioTracks
            val remembered = RememberedTrack(
                label = option.label,
                language = option.language,
                indexWithinLanguage = engineIndexWithinLanguage(tracks, option.index),
            )
            rememberedAudioTrack = remembered
            if (isUserOverride) {
                persistRememberedTrack(TrackType.AUDIO, remembered)
            }
        }
        if (isUserOverride) {
            persistStreamSelectionFromPlayer(
                audioTrackOption = option,
                subtitleTrackOption = null,
            )
        }
    }

    fun selectSubtitleTrack(option: TrackOption, isUserOverride: Boolean = true) {
        // Server-origin subtitle (transcode): the sub isn't in the HLS manifest
        // and mpv hasn't side-loaded it yet. Re-resolve playback with the new
        // subtitleStreamIndex so the server delivers it (and side-loads it via
        // buildExternalSubtitles on the reloaded engine).
        if (selectServerTrack(option, TrackType.SUBTITLE, isUserOverride)) return
        val engine = getEngine() ?: return

        engine.selectTrack(TrackType.SUBTITLE, option.index)

        selectedSubtitleTrackIndex = if (option.index < 0) null else option.index
        // Latch only for a real track or an explicit user override. An auto
        // fallback to "Off" while tracks haven't loaded yet must remain
        // unlatched so a subsequent emission (offline sidecar subs arrive after
        // the first track list) can still apply the auto/preference selection.
        subtitleSelectionHeld = isUserOverride || option.index >= 0

        _state.update { state ->
            val isOff = option.index < 0
            state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                val matches = track.index == option.index
                val isOffTrack = track.index < 0
                track.copy(isSelected = if (isOff) isOffTrack else matches)
            })
        }
        // Remember a real subtitle selection for cross-episode scoring (G5).
        if (option.index >= 0 && option.index < SERVER_TRACK_INDEX_BASE) {
            val tracks = _state.value.subtitleTracks
            val remembered = RememberedTrack(
                label = option.label,
                language = option.language,
                indexWithinLanguage = engineIndexWithinLanguage(tracks, option.index),
            )
            rememberedSubtitleTrack = remembered
            if (isUserOverride) {
                persistRememberedTrack(TrackType.SUBTITLE, remembered)
            }
        }
        if (isUserOverride) {
            persistStreamSelectionFromPlayer(
                audioTrackOption = null,
                subtitleTrackOption = option,
            )
        }
    }

    /**
     * Shared body of the server-origin branches ([SERVER_TRACK_INDEX_BASE] and
     * up) in [selectAudioTrack] / [selectSubtitleTrack]. A synthetic server
     * track is a picker affordance, not an engine track: the auto-resolution
     * ladder (pending/stored/preference) hits this branch before the
     * replacement engine has published the real track (it materializes a beat
     * after the engine re-creates), so acting there would latch the
     * held-selection guard against a track that never exists — nothing maps it
     * back to the engine — permanently blocking re-resolution. Only a
     * deliberate user pick acts; the auto path stays unlatched so the next
     * availableTracks emission re-resolves.
     *
     * Returns true when [option] is server-origin (handled, or deliberately
     * ignored on the auto path); false when it is an engine track and the
     * caller must continue with the in-place selection.
     */
    private fun selectServerTrack(
        option: TrackOption,
        type: TrackType,
        isUserOverride: Boolean,
    ): Boolean {
        if (option.index < SERVER_TRACK_INDEX_BASE) return false
        if (!isUserOverride) return true
        val streamIndex = option.index - SERVER_TRACK_INDEX_BASE
        if (type == TrackType.AUDIO) {
            selectedAudioTrackIndex = option.index
            audioSelectionHeld = true
        } else {
            selectedSubtitleTrackIndex = option.index
            subtitleSelectionHeld = true
        }
        _state.update { state ->
            val tracks = if (type == TrackType.AUDIO) state.audioTracks else state.subtitleTracks
            val updated = tracks.map { track ->
                track.copy(isSelected = track.index == option.index)
            }
            if (type == TrackType.AUDIO) state.copy(audioTracks = updated)
            else state.copy(subtitleTracks = updated)
        }
        // Persist before the reload: the engine re-creation invalidates
        // engine-positional indices, so the stored server-stream index is the
        // only key the post-reload ladder can restore the pick from (subs via
        // the "external:{index}" id contract — see TrackSelectionPolicy).
        persistStreamSelectionFromPlayer(
            audioTrackOption = option.takeIf { type == TrackType.AUDIO },
            subtitleTrackOption = option.takeIf { type == TrackType.SUBTITLE },
        )
        onReloadForStreamChange(
            if (type == TrackType.AUDIO) MediaStreamSelection(audioStreamIndex = streamIndex)
            else MediaStreamSelection(subtitleStreamIndex = streamIndex),
        )
        return true
    }

    /**
     * Requests that the downloaded subtitle described by [hint] — its
     * side-loaded engine track id, plus optionally the server `MediaStream.index`
     * for the synthetic transcode picker rows — becomes the active subtitle as
     * soon as it is resolvable — immediately when possible, otherwise on the
     * next [updateTracksFromEngine] run whose list carries it. Used after an
     * in-player subtitle download so the user does not have to open the track
     * picker and hunt for the new row.
     */
    fun requestSubtitleSelection(hint: ReadySubtitleHint) {
        pendingServerSubtitleHint = hint
        pendingServerSubtitleAttempts = PENDING_SERVER_SUBTITLE_MAX_ATTEMPTS
        tryConsumePendingServerSubtitleSelection()
    }

    /**
     * Resolves a downloaded-subtitle hint to a picker row. Match order: exact
     * side-loaded id → container stream index → synthetic transcode row
     * ([SERVER_TRACK_INDEX_BASE] + stream index). Returns null when the track
     * has not surfaced yet.
     *
     * [allowSyntheticRow] gates the third match. The synthetic row is a
     * picker affordance whose selection triggers a full session reload
     * (see [selectServerTrack]) — right for an explicit user pick, wrong for
     * the auto-armed post-download intent, which must apply in place once the
     * side-load lands (or not at all) rather than surprise-reload playback.
     */
    fun findSubtitleOptionFor(hint: ReadySubtitleHint, allowSyntheticRow: Boolean = true): TrackOption? {
        val tracks = _state.value.subtitleTracks
        tracks.firstOrNull { it.id == hint.trackId }?.let { return it }
        val serverStreamIndex = hint.serverStreamIndex ?: return null
        tracks.firstOrNull { it.streamIndex == serverStreamIndex }?.let { return it }
        if (!allowSyntheticRow) return null
        return tracks.firstOrNull {
            it.index >= SERVER_TRACK_INDEX_BASE &&
                it.index - SERVER_TRACK_INDEX_BASE == serverStreamIndex
        }
    }

    private fun tryConsumePendingServerSubtitleSelection() {
        val hint = pendingServerSubtitleHint ?: return
        // Synthetic rows excluded: the auto intent waits for the real
        // side-loaded track (or expires); it must never trigger the reload a
        // synthetic-row selection causes.
        val option = findSubtitleOptionFor(hint, allowSyntheticRow = false)
        if (option != null) {
            clearPendingServerSubtitleSelection()
            selectSubtitleTrack(option, isUserOverride = true)
            return
        }
        // Not resolvable yet (side-load still in flight): burn one attempt. The
        // next availableTracks emission re-runs the consume via
        // updateTracksFromEngine until the cap drops the intent.
        pendingServerSubtitleAttempts--
        if (pendingServerSubtitleAttempts <= 0) {
            clearPendingServerSubtitleSelection()
        }
    }

    private fun clearPendingServerSubtitleSelection() {
        pendingServerSubtitleHint = null
        pendingServerSubtitleAttempts = 0
    }

    /**
     * The per-type deltas of the twin restore ladders in
     * [updateTracksFromEngine]. The shared choreography — pending server index
     * (-1 = placeholder, else resolve by server stream index) → held-selection
     * guard → stored per-item index (-1 = placeholder, else resolve by stream
     * index + an offline fallback) → the language/preference ladder (null =
     * placeholder) — runs ONCE in [TrackSelectionHelper.runRestoreLadder];
     * everything that genuinely differs between audio and subtitles is a field
     * here:
     *
     *  - [resolvePendingMatch] — audio always resolves through
     *    [TrackSelectionPolicy.resolveByStreamIndex] (its null-targetStream
     *    label fallback included); subtitles guard on a non-null target stream
     *    and, with no server streams left (offline), resolve the pending index
     *    by the `"offline:${index}"` side-load id instead.
     *  - [resolveStoredOfflineFallback] — what a stored index falls back to
     *    when the stream-index resolution missed and no server streams remain
     *    (offline): audio the engine positional index; subtitles the offline
     *    id first, then the positional index.
     *  - [resolvePreferenceMatch] — audio's [TrackSelectionPolicy.resolveAudio]
     *    (audio-description preference, unconditional remembered-memory
     *    hydration) vs subtitles' [TrackSelectionPolicy.resolveSubtitle] (the
     *    subtitleDisabled short-circuit, forcedOnly gating of the memory).
     *    Returning null selects the type's placeholder in both ladders.
     */
    private class TrackRestoreLadder(
        /** Server [MediaStream] type the target streams are looked up by. */
        val streamType: StreamType,
        val pendingIndex: () -> Int?,
        val clearPendingIndex: () -> Unit,
        val isSelectionHeld: () -> Boolean,
        val storedIndexIn: (MediaStreamSelection?) -> Int?,
        /** Applies an auto (non-user) selection: engine select + held latch + persist. */
        val select: (TrackOption) -> Unit,
        val resolvePendingMatch: (tracks: List<TrackOption>, streamIndex: Int, streams: List<MediaStream>) -> TrackOption?,
        val resolveStoredOfflineFallback: (tracks: List<TrackOption>, streamIndex: Int) -> TrackOption?,
        val resolvePreferenceMatch: (tracks: List<TrackOption>, streams: List<MediaStream>, sub: SubtitleSlice) -> TrackOption?,
    )

    // The two per-type delta instances. Declared as vals whose lambdas defer
    // every read (pending indices, held flags, remembered memory, resolver
    // value) to invocation time.
    private val audioRestoreLadder = TrackRestoreLadder(
        streamType = StreamType.AUDIO,
        pendingIndex = { pendingAudioStreamIndex },
        clearPendingIndex = { pendingAudioStreamIndex = null },
        isSelectionHeld = { audioSelectionHeld },
        storedIndexIn = { it?.audioStreamIndex },
        select = { selectAudioTrack(it, isUserOverride = false) },
        resolvePendingMatch = { tracks, streamIndex, streams ->
            val targetStream = streams.firstOrNull {
                it.type == StreamType.AUDIO && it.index == streamIndex
            }
            // The pending index is a server/Jellyfin stream index, so match it
            // against the engine track's container stream index (mpv ff-index)
            // — NOT the engine track id, which is unrelated to the server
            // index. Fall back to label for engines/tracks without one.
            trackSelectionPolicy.resolveByStreamIndex(tracks, streamIndex, targetStream)
        },
        resolveStoredOfflineFallback = { tracks, streamIndex ->
            // Offline (no server streams): the engine positional index is the
            // only remaining handle.
            tracks.firstOrNull { it.index == streamIndex }
        },
        resolvePreferenceMatch = { tracks, streams, sub ->
            // Per-item then per-series language rule overrides the
            // global preferred audio language when set.
            val resolvedLang = playbackPreferenceResolver.resolved.value?.audioLanguage
                ?: sub.preferredAudioLanguage ?: "eng"
            // The full precedence ladder — G5 scoring pre-pass →
            // audio-description preference → language match — lives in
            // TrackSelectionPolicy now. Returns null when no match
            // exists; we then select the Default placeholder.
            hydrateRememberedAudioTrack()
            trackSelectionPolicy.resolveAudio(
                AudioResolutionArgs(
                    tracks = tracks,
                    streams = streams,
                    resolvedLang = resolvedLang,
                    preferAudioDescription = sub.preferAudioDescription,
                    remembered = rememberedAudioTrack,
                ),
            )
        },
    )

    private val subtitleRestoreLadder = TrackRestoreLadder(
        streamType = StreamType.SUBTITLE,
        pendingIndex = { pendingSubtitleStreamIndex },
        clearPendingIndex = { pendingSubtitleStreamIndex = null },
        isSelectionHeld = { subtitleSelectionHeld },
        storedIndexIn = { it?.subtitleStreamIndex },
        select = { selectSubtitleTrack(it, isUserOverride = false) },
        resolvePendingMatch = { tracks, streamIndex, streams ->
            val targetStream = streams.firstOrNull {
                it.type == StreamType.SUBTITLE && it.index == streamIndex
            }
            if (targetStream != null) {
                // Prefer the container stream index (mpv ff-index == the
                // server's MediaStream.index) — robust against blank/dup/
                // translated titles. Fall back to label only for engines or
                // side-loaded tracks that don't expose a stream index.
                trackSelectionPolicy.resolveByStreamIndex(tracks, streamIndex, targetStream)
            } else if (streams.isEmpty()) {
                // Offline: server mediaStreams is empty, so the pending
                // index is the original server stream index stamped onto the
                // side-loaded subtitle as id == "offline:${index}". Resolve
                // by that id; if the engine hasn't propagated it (e.g. the
                // sub hasn't side-loaded yet) the next availableTracks
                // emission re-runs the restore path and picks it up.
                trackSelectionPolicy.resolveByOfflineSubtitleId(tracks, streamIndex)
            } else {
                null
            }
        },
        resolveStoredOfflineFallback = { tracks, streamIndex ->
            // Offline (no server streams) resolves the side-loaded subtitle by
            // its `"offline:${index}"` id (both ExoPlayer and mpv propagate it
            // into TrackOption.id), then falls back to a positional-index
            // match for legacy tracks without it.
            trackSelectionPolicy.resolveByOfflineSubtitleId(tracks, streamIndex)
                ?: tracks.firstOrNull { it.index == streamIndex }
        },
        resolvePreferenceMatch = { tracks, streams, sub ->
            val resolvedPref = playbackPreferenceResolver.resolved.value
            // An explicit "subtitles off" intent (item scope over series)
            // short-circuits the matcher: force Off and skip the language
            // ladder entirely, mirroring how a stored -1 works for the
            // per-item stream-index override. Returning null selects Off.
            if (resolvedPref?.subtitleDisabled == true) {
                null
            } else {
                // Per-item then per-series preference overrides the global
                // preferred subtitle language when set.
                val resolvedLang = resolvedPref?.subtitleLanguage
                    ?: sub.preferredSubtitleLanguage ?: "eng"
                val forcedOnly = sub.subtitlesForcedOnly
                // The full precedence ladder — G5 scoring pre-pass (non-forced
                // only) → forced-only stream pick → tiered SubtitleTrackMatcher
                // → null — lives in TrackSelectionPolicy now. Returns null when
                // no same-language track exists; we then select Off.
                if (!forcedOnly) {
                    hydrateRememberedSubtitleTrack()
                }
                trackSelectionPolicy.resolveSubtitle(
                    SubtitleResolutionArgs(
                        tracks = tracks,
                        streams = streams,
                        lang = resolvedLang,
                        forcedOnly = forcedOnly,
                        forced = resolvedPref?.subtitleForced,
                        hearingImpaired = resolvedPref?.subtitleHearingImpaired,
                        remembered = if (forcedOnly) null else rememberedSubtitleTrack,
                    ),
                )
            }
        },
    )

    /**
     * The one restore choreography both ladders run against their rebuilt
     * picker rows. Stage order — pending server index → held-selection guard →
     * stored per-item index → language/preference ladder — is behaviour-pinned
     * by TrackSelectionHelperTest; the per-type deltas live on [TrackRestoreLadder].
     */
    private fun runRestoreLadder(
        ladder: TrackRestoreLadder,
        tracks: List<TrackOption>,
        streams: List<MediaStream>,
    ) {
        val pending = ladder.pendingIndex()
        if (pending != null) {
            ladder.clearPendingIndex()
            if (pending == -1) {
                tracks.firstOrNull { it.index < 0 }?.let(ladder.select)
            } else {
                ladder.resolvePendingMatch(tracks, pending, streams)?.let(ladder.select)
            }
        } else if (!ladder.isSelectionHeld()) {
            // Re-resolve stored/per-item/series/global preference — but only
            // when no selection has been applied for this item yet. Once a
            // track is selected (auto or manual) we leave it alone; the
            // re-assert block at the top of updateTracksFromEngine keeps it
            // sticky across track-list republishes. Without this guard, every
            // availableTracks emission re-ran the resolution, and on offline
            // playback (empty mediaStreams) the stored-index lookup returned
            // null → the next emission dropped the selection back to
            // Default/Off — the classic "subtitle flashes then resets" bug.
            val itemId = getCurrentItemId() ?: return
            val stored = engineStore.playerEngine.value.mediaStreamSelections[itemId]
            val sub = subtitleStore.subtitle.value
            val storedIndex = ladder.storedIndexIn(stored)
            if (storedIndex != null) {
                if (storedIndex == -1) {
                    tracks.firstOrNull { it.index < 0 }?.let(ladder.select)
                } else {
                    val targetStream = streams.firstOrNull {
                        it.type == ladder.streamType && it.index == storedIndex
                    }
                    // Prefer container stream index (mpv ff-index == stored
                    // server index); fall back to label for engines/side-loaded
                    // tracks that don't expose one. When nothing matched and no
                    // server streams remain (offline), the ladder's offline
                    // fallback applies.
                    val match = trackSelectionPolicy.resolveByStreamIndex(tracks, storedIndex, targetStream)
                        ?: if (streams.isEmpty()) ladder.resolveStoredOfflineFallback(tracks, storedIndex) else null
                    match?.let(ladder.select)
                }
            } else {
                ladder.resolvePreferenceMatch(tracks, streams, sub)?.let(ladder.select)
                    ?: tracks.firstOrNull { it.index < 0 }?.let(ladder.select)
            }
        }
    }

    fun updateTracksFromEngine() {
        val engine = getEngine() ?: return
        val streams = getMediaStreams()
        val rawTracks = engine.availableTracks.value

        // If a previously-selected track has lost its "selected" flag (e.g. the
        // engine re-published its track list after a bitrate/mode change), re-
        // assert the selection — but do NOT return early. Returning here used to
        // skip the track-list rebuild below and the pending/preference selection
        // logic, leaving the picker UI stale and (if no follow-up availableTracks
        // emission arrived) failing to apply the navigation/per-item/series/global
        // language preference at all for that load.
        val rawAudioTracks = rawTracks.filter { it.type == TrackType.AUDIO }
        val prevAudioSel = selectedAudioTrackIndex
        if (prevAudioSel != null) {
            val targetTrack = rawAudioTracks.find { it.index == prevAudioSel }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(TrackType.AUDIO, targetTrack.index)
            }
        }

        val rawSubTracks = rawTracks.filter { it.type == TrackType.SUBTITLE }
        val prevSubSel = selectedSubtitleTrackIndex
        if (prevSubSel != null) {
            val targetTrack = rawSubTracks.find { it.index == prevSubSel }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(TrackType.SUBTITLE, targetTrack.index)
            }
        }

        val audioOptions = rawAudioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, t.streamIndex, t.badges, id = t.id)
        }
        // Upgrade each engine audio track's label/badges with the richer server
        // MediaStream data when a match is found (by stream index, then language
        // + positional order). This fixes Direct-Play, where engine labels are
        // crude (e.g. ExoPlayer's "English · application/x-media3-cues") and the
        // server stream list was previously ignored entirely. Falls through
        // unchanged for offline playback (no server streams).
        val enrichedAudioOptions = enrichFromServer(audioOptions, streams, StreamType.AUDIO)

        // For transcoded / direct-stream playback the server bakes a single
        // audio track into the HLS manifest, so mpv's track-list surfaces only
        // that one — the picker would otherwise hide every other audio stream.
        // Supplement with server-side mediaStreams (deduped by label against
        // the engine tracks) so the user can see and switch to any audio track.
        // Selecting a server-origin track triggers a session reload (see
        // selectAudioTrack) since mpv cannot switch audio in-place on a
        // transcode.
        val mergedAudioOptions = if (getPlayMethod() != PlayMethod.DIRECT_PLAY) {
            mergeServerStreams(enrichedAudioOptions, streams, StreamType.AUDIO)
        } else {
            enrichedAudioOptions
        }

        val audioTracks = if (mergedAudioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            val sel = selectedAudioTrackIndex
            val hasSelectionMatch = mergedAudioOptions.any { it.index == sel }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = mergedAudioOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedAudioTrackIndex = engineAutoSelected.index
                    selectedAudioTrackIndex
                } else null
            }
            listOf(TrackOption(-1, "Default", null, resolvedSel == null)) + mergedAudioOptions.map { t ->
                val isSel = if (resolvedSel != null) resolvedSel == t.index else t.isSelected
                t.copy(isSelected = isSel)
            }
        }

        val engineSubOptions = rawSubTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, t.streamIndex, t.badges, id = t.id)
        }
        // Same server enrichment as audio — Direct-Play subtitle tracks get
        // their real title/codec/flags from the server MediaStream instead of
        // ExoPlayer's synthetic mime ("application/x-media3-cues"), so
        // duplicate-language subs become distinguishable.
        val enrichedSubOptions = enrichFromServer(engineSubOptions, streams, StreamType.SUBTITLE)

        // Same merge for subtitles: side-loaded external subs may take a moment
        // to resolve in mpv's track-list, and embedded subs aren't in the
        // transcode manifest at all. Surface all server subtitle streams so the
        // picker is populated immediately; selecting one side-loads it.
        val mergedSubOptions = when {
            getPlayMethod() != PlayMethod.DIRECT_PLAY ->
                mergeServerStreams(enrichedSubOptions, streams, StreamType.SUBTITLE)
            // Direct-play safety net (#144): the picker is engine-tracks-only on
            // direct play, so a silently-failed side-load chain (unmappable
            // codec, dropped engine fetch) empties the sheet even though the
            // server lists the subtitle. When the engine published NO subtitle
            // tracks at all, surface the server's EXTERNAL subs as synthetic
            // rows — selecting one runs the selectServerTrack reload, which
            // re-resolves playback and re-side-loads. Embedded streams stay
            // excluded: buildExternalSubtitles skips them on direct play
            // (container demux owns them), so their rows would be dead.
            enrichedSubOptions.isEmpty() && streams.any { it.type == StreamType.SUBTITLE && it.isExternal } ->
                mergeServerStreams(emptyList(), streams.filter { it.isExternal }, StreamType.SUBTITLE)
            else -> enrichedSubOptions
        }

        val subtitleTracks = if (mergedSubOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackIndex
            val hasSelectionMatch = mergedSubOptions.any { it.index == sel }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = mergedSubOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedSubtitleTrackIndex = engineAutoSelected.index
                    selectedSubtitleTrackIndex
                } else null
            }
            listOf(TrackOption(-1, "Off", null, resolvedSel == null)) + mergedSubOptions.map { t ->
                val isSel = if (resolvedSel != null) resolvedSel == t.index else t.isSelected
                t.copy(isSelected = isSel)
            }
        }

        _state.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }

        // The two restore ladders — pending server index → held-selection
        // guard → stored per-item index → per-item/series preference — are ONE
        // choreography run twice, parameterised by the per-type deltas carried
        // by the ladder instances (see [TrackRestoreLadder]).
        runRestoreLadder(audioRestoreLadder, audioTracks, streams)
        runRestoreLadder(subtitleRestoreLadder, subtitleTracks, streams)

        // A pending post-download selection consumes the rebuilt list last so
        // it wins over the auto-resolution ladder above.
        tryConsumePendingServerSubtitleSelection()
    }

    fun resetAudioSelection() {
        val itemId = getCurrentItemId() ?: return
        selectedAudioTrackIndex = null
        audioSelectionHeld = false
        scope.launch {
            val currentSelection = engineStore.playerEngine.value.mediaStreamSelections[itemId]
            engineStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = null,
                subtitleStreamIndex = currentSelection?.subtitleStreamIndex
            )
            updateTracksFromEngine()
        }
    }

    fun resetSubtitleSelection() {
        val itemId = getCurrentItemId() ?: return
        selectedSubtitleTrackIndex = null
        subtitleSelectionHeld = false
        scope.launch {
            val currentSelection = engineStore.playerEngine.value.mediaStreamSelections[itemId]
            engineStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = currentSelection?.audioStreamIndex,
                subtitleStreamIndex = null
            )
            updateTracksFromEngine()
        }
    }

    fun reset() {
        selectedSubtitleTrackIndex = null
        selectedAudioTrackIndex = null
        subtitleSelectionHeld = false
        audioSelectionHeld = false
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null
        playbackPreferenceResolver.clear()
        // Forget cross-episode track memory too — reset is a teardown/explicit
        // reset, not a series continuation, so the remembered selection no longer
        // reflects the user's intent for the next resolution.
        rememberedAudioTrack = null
        rememberedSubtitleTrack = null
        trackedSeriesId = null
        clearPendingServerSubtitleSelection()
    }

    /**
     * Item-switch reset: the whole [TrackState] is per-item (picker lists +
     * override/preference flags) and must not bleed into the next episode.
     * Called by the ViewModel's `releaseInternals()` next to [reset] — this is
     * the explicit form of the implicit reset the former UiState rebuild did
     * (none of these fields were on the reset whitelist).
     */
    fun resetForItem() {
        _state.value = TrackState()
        clearPendingServerSubtitleSelection()
    }

    /**
     * Drops engine-positional selection state after a same-item engine
     * recreation ([com.raulshma.jellyplay.feature.player.video.PlayerSessionManager.reloadWithEngine]
     * — mode/quality/stream-index reload, engine switch, error retry). The
     * replacement engine renumbers tracks and re-side-loads subtitles, so a
     * held selection — or its stale engine/synthetic index — can no longer
     * match anything; leaving it latched blocks the stored/pending/preference
     * re-resolution in [updateTracksFromEngine] and silently drops the
     * selection for the rest of the session (the force-transcode subtitle
     * regression). Only engine-scoped state is cleared: the *stable* keys
     * survive — the stored per-item server-stream indices and the
     * cross-episode remembered-track memory — so the first post-reload track
     * emission re-applies them. Mirrors what [setPendingStreams] does for a
     * fresh item, minus the pending seeding (the reload paths that know the
     * target indices arm [setPendingStreams] themselves).
     */
    fun onEngineRecreated() {
        selectedAudioTrackIndex = null
        selectedSubtitleTrackIndex = null
        audioSelectionHeld = false
        subtitleSelectionHeld = false
        clearPendingServerSubtitleSelection()
    }

    /**
     * Reflects the stored per-item audio/subtitle stream selection (from the
     * session state or the aggregate preferences snapshot) into the override
     * flags. Called by the ViewModel's session collector and aggregate
     * collector — the two sites that formerly wrote these flags directly into
     * the UiState. Guarded so a redundant call does not re-emit the flow.
     */
    fun onStoredSelectionChanged(stored: MediaStreamSelection?) {
        val newAudio = stored?.audioStreamIndex != null
        val newSub = stored?.subtitleStreamIndex != null
        val s = _state.value
        if (s.hasAudioOverride != newAudio || s.hasSubtitleOverride != newSub) {
            _state.update { it.copy(hasAudioOverride = newAudio, hasSubtitleOverride = newSub) }
        }
    }

    /**
     * Reflects the resolved per-item/series playback preference into the
     * series-preference flags (the track sheets' "remember for this series"
     * toggle rows). Called by the ViewModel's resolver collector — the site
     * that formerly wrote these flags directly into the UiState.
     */
    fun onSeriesPreferenceResolved(pref: ItemPlaybackPreference?) {
        val isSeries = pref?.scope == com.raulshma.jellyplay.core.model.PlaybackPrefScope.SERIES
        val newAudio = isSeries && pref.audioLanguage != null
        val newSub = isSeries && (pref.subtitleLanguage != null || pref.subtitleDisabled == true)
        val newSubOff = isSeries && pref.subtitleDisabled == true
        val newBoost = isSeries && pref.dialogueBoostStrength != null
        val s = _state.value
        if (s.hasSeriesAudioPref != newAudio || s.hasSeriesSubtitlePref != newSub ||
            s.hasSeriesSubtitleOffPref != newSubOff || s.hasSeriesDialogueBoostPref != newBoost
        ) {
            _state.update {
                it.copy(
                    hasSeriesAudioPref = newAudio,
                    hasSeriesSubtitlePref = newSub,
                    hasSeriesSubtitleOffPref = newSubOff,
                    hasSeriesDialogueBoostPref = newBoost,
                )
            }
        }
    }

    /**
     * Merge server-side [MediaStream] entries into the engine-derived track
     * options for transcoded/direct-stream playback. The HLS manifest only
     * carries one audio track (and may omit embedded subs), so the engine
     * track-list alone hides the rest. Each server stream not already
     * represented (by label) in [engineOptions] is appended with a synthetic
     * index ([SERVER_TRACK_INDEX_BASE] + stream.index) so selection can detect
     * it as server-origin and trigger a session reload or side-load.
     *
     * The currently-active stream is marked selected so the picker reflects
     * reality: for audio the active track is the one baked into the manifest
     * (matched by label against engine tracks); for subtitles we defer to the
     * engine's selection flag.
     */
    private fun mergeServerStreams(
        engineOptions: List<TrackOption>,
        streams: List<MediaStream>,
        type: StreamType,
    ): List<TrackOption> {
        if (streams.isEmpty()) return engineOptions
        val engineLabels = engineOptions.map { it.label.lowercase() }.toSet()
        val merged = engineOptions.toMutableList()
        for (stream in streams.filter { it.type == type }) {
            val info = TrackLabelInfo(
                title = stream.title,
                language = stream.language,
                codec = stream.codec,
                channels = stream.channels,
                isForced = stream.isForced,
                isDefault = stream.isDefault,
            )
            val label = stream.displayTitle?.takeIf { it.isNotBlank() }
                ?: TrackLabelFormatter.primary(info)
            if (label.lowercase() in engineLabels) continue
            val syntheticIndex = SERVER_TRACK_INDEX_BASE + stream.index
            merged.add(
                TrackOption(
                    index = syntheticIndex,
                    label = label,
                    language = stream.language,
                    isSelected = false,
                    badges = TrackLabelFormatter.badges(info),
                )
            )
        }
        return merged
    }

    /**
     * Upgrades each engine track's [TrackOption.label] and [TrackOption.badges]
     * with the richer Jellyfin server [MediaStream] data when a match is found.
     *
     * This runs for **all** play methods (including Direct-Play), which is the
     * key fix: engine-built labels (especially ExoPlayer's
     * `Language · application/x-media3-cues`) are crude and collapse
     * same-language tracks into identical rows. The server stream carries the
     * real title, codec, and forced/default/hearing-impaired flags, so matching
     * them yields `Signs & Songs - English - text/x-ssa` + a `FORCED` badge.
     *
     * Matching priority (see [TrackEnrichmentResolver.enrich]):
     *  1. Container stream index (mpv `ff-index` == server `index`) — robust.
     *  2. Language match, positional order within that language — best-effort
     *     for engines that expose no container index (ExoPlayer). Each server
     *     stream is consumed once, so two "eng" subs resolve to distinct tracks.
     *  3. Title/label exact match.
     *
     * Tracks with no server match pass through unchanged.
     */
    private fun enrichFromServer(
        engineOptions: List<TrackOption>,
        streams: List<MediaStream>,
        type: StreamType,
    ): List<TrackOption> = TrackEnrichmentResolver.enrich(engineOptions, streams, type)

    companion object {
        /**
         * Synthetic index base for server-origin track options. A track option
         * whose index is >= this value refers to [MediaStream.index] (subtract
         * the base to recover it) and is not backed by an engine track; it
         * requires a session reload (audio) or side-load (subtitle) on select.
         */
        internal const val SERVER_TRACK_INDEX_BASE = 100_000

        /**
         * How many [updateTracksFromEngine] runs a pending post-download
         * selection survives before giving up. The side-load → track-list
         * republish round-trip takes one or two emissions; the cap only exists
         * so a side-load that never lands (network failure, engine without the
         * codec) cannot hijack every later selection.
         */
        internal const val PENDING_SERVER_SUBTITLE_MAX_ATTEMPTS = 10
    }

    private fun persistStreamSelectionFromPlayer(
        audioTrackOption: TrackOption?,
        subtitleTrackOption: TrackOption?,
    ) {
        val itemId = getCurrentItemId() ?: return
        val streams = getMediaStreams()
        val currentSelection = engineStore.playerEngine.value.mediaStreamSelections[itemId]
        val audioStreamIndex = if (audioTrackOption != null) {
            if (audioTrackOption.index < 0) -1
            else trackSelectionPolicy.resolveMediaStreamIndex(streams, StreamType.AUDIO, audioTrackOption)
        } else {
            currentSelection?.audioStreamIndex
        }
        val subtitleStreamIndex = if (subtitleTrackOption != null) {
            if (subtitleTrackOption.index < 0) -1
            else trackSelectionPolicy.resolveMediaStreamIndex(streams, StreamType.SUBTITLE, subtitleTrackOption)
        } else {
            currentSelection?.subtitleStreamIndex
        }
        scope.launch {
            engineStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
        }
    }

    /**
     * Seeds in-process remembered-audio memory from the persisted series-scope
     * preference (G5). The resolver reads the DAO asynchronously; this runs on
     * the hot path so once the resolver has published a value the memory is
     * hydrated (once) and the scoring pre-pass can use it without a restart.
     */
    private fun hydrateRememberedAudioTrack() {
        if (rememberedAudioTrack != null) return
        rememberedAudioTrack = playbackPreferenceResolver.resolved.value?.rememberedAudioTrack
    }

    /** [hydrateRememberedAudioTrack] for subtitles. */
    private fun hydrateRememberedSubtitleTrack() {
        if (rememberedSubtitleTrack != null) return
        rememberedSubtitleTrack = playbackPreferenceResolver.resolved.value?.rememberedSubtitleTrack
    }

    /**
     * Position among same-language tracks of the track whose engine [index]
     * matches, or -1 if unknown. Used when remembering a selection — the caller
     * knows the real engine track index, not its array position. (The scoring
     * read-side equivalent lives in [TrackSelectionPolicy].)
     */
    private fun engineIndexWithinLanguage(tracks: List<TrackOption>, index: Int): Int {
        val target = tracks.firstOrNull { it.index == index } ?: return -1
        val lang = target.language?.lowercase()?.trim().orEmpty()
        if (lang.isEmpty()) return -1
        val sameLang = tracks.filter { it.language?.lowercase()?.trim() == lang }
        return sameLang.indexOf(target)
    }
}
