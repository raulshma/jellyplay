package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
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
    private val onReloadForStreamChange: (audioStreamIndex: Int?, subtitleStreamIndex: Int?) -> Unit,
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

    fun setPendingStreams(subtitleIndex: Int?, audioIndex: Int?) {
        pendingSubtitleStreamIndex = subtitleIndex
        pendingAudioStreamIndex = audioIndex
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
        if (option.index >= SERVER_TRACK_INDEX_BASE) {
            // A synthetic server track is a picker affordance, not an engine
            // track. The auto-resolution ladder (pending/stored/preference)
            // hits this branch before the new engine has published any audio
            // tracks — acting there would latch the held-selection guard
            // against a track that never exists, permanently blocking the
            // re-resolution that should fire when the real track arrives.
            // Only a deliberate user pick acts; the auto path stays
            // unlatched so the next availableTracks emission re-resolves.
            if (!isUserOverride) return
            val streamIndex = option.index - SERVER_TRACK_INDEX_BASE
            selectedAudioTrackIndex = option.index
            audioSelectionHeld = true
            _state.update { state ->
                state.copy(audioTracks = state.audioTracks.map { track ->
                    track.copy(isSelected = track.index == option.index)
                })
            }
            // Persist before the reload: the engine re-creation invalidates
            // engine-positional indices, so the stored server-stream index is
            // the only key the post-reload ladder can restore the pick from.
            persistStreamSelectionFromPlayer(
                audioTrackOption = option,
                subtitleTrackOption = null,
            )
            onReloadForStreamChange(streamIndex, null)
            return
        }
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
        if (option.index >= SERVER_TRACK_INDEX_BASE) {
            // Same auto-path rule as selectAudioTrack's server-track branch:
            // the ladder resolves here while the side-loaded engine track
            // hasn't materialized yet (it appears a beat after the engine
            // re-creates). Latching the synthetic index would wedge the
            // selection — nothing maps it back to the engine — so only a
            // user pick acts; the auto path stays unlatched and retries once
            // the real side-loaded track is published.
            if (!isUserOverride) return
            val streamIndex = option.index - SERVER_TRACK_INDEX_BASE
            selectedSubtitleTrackIndex = option.index
            subtitleSelectionHeld = true
            _state.update { state ->
                state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                    track.copy(isSelected = track.index == option.index)
                })
            }
            // Persist before the reload so the post-reload ladder can restore
            // the pick on the re-side-loaded track (resolved via the
            // "external:{index}" id contract — see TrackSelectionPolicy).
            persistStreamSelectionFromPlayer(
                audioTrackOption = null,
                subtitleTrackOption = option,
            )
            onReloadForStreamChange(null, streamIndex)
            return
        }
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
        val mergedSubOptions = if (getPlayMethod() != PlayMethod.DIRECT_PLAY) {
            mergeServerStreams(enrichedSubOptions, streams, StreamType.SUBTITLE)
        } else {
            enrichedSubOptions
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

        val pendingAudio = pendingAudioStreamIndex
        if (pendingAudio != null) {
            pendingAudioStreamIndex = null
            if (pendingAudio == -1) {
                audioTracks.firstOrNull { it.index < 0 }?.let { selectAudioTrack(it, isUserOverride = false) }
            } else {
                val targetStream = streams.firstOrNull {
                    it.type == StreamType.AUDIO && it.index == pendingAudio
                }
                // pendingAudio is a server/Jellyfin stream index, so match it
                // against the engine track's container stream index (mpv ff-index)
                // — NOT the engine track id, which is unrelated to the server
                // index. Fall back to label for engines/tracks without one.
                trackSelectionPolicy.resolveByStreamIndex(audioTracks, pendingAudio, targetStream)
                    ?.let { selectAudioTrack(it, isUserOverride = false) }
            }
        } else if (!audioSelectionHeld) {
            // Re-resolve stored/per-item/series/global preference — but only when
            // no selection has been applied for this item yet. Once a track is
            // selected (auto or manual) we leave it alone; the re-assert block
            // above keeps it sticky across track-list republishes. Without this
            // guard, every availableTracks emission re-ran this block, and on
            // offline playback (empty mediaStreams) resolveMediaStreamIndex
            // returned null → the next emission dropped audio back to default.
            val itemId = getCurrentItemId()
            if (itemId != null) {
                val engine = engineStore.playerEngine.value
                val sub = subtitleStore.subtitle.value
                val stored = engine.mediaStreamSelections[itemId]
                val audioIdx = stored?.audioStreamIndex
                val prefAudioLang = sub.preferredAudioLanguage ?: "eng"
                if (audioIdx != null) {
                    if (audioIdx == -1) {
                        audioTracks.firstOrNull { it.index < 0 }?.let { selectAudioTrack(it, isUserOverride = false) }
                    } else {
                        val targetStream = streams.firstOrNull {
                            it.type == StreamType.AUDIO && it.index == audioIdx
                        }
                        // Prefer container stream index (mpv ff-index == stored
                        // server index); fall back to label for engines/side-loaded
                        // tracks that don't expose one. Offline (no server streams)
                        // falls through to the engine positional index.
                        val resolved = trackSelectionPolicy.resolveByStreamIndex(audioTracks, audioIdx, targetStream)
                        if (resolved != null) {
                            selectAudioTrack(resolved, isUserOverride = false)
                        } else if (streams.isEmpty()) {
                            audioTracks.firstOrNull { it.index == audioIdx }
                                ?.let { selectAudioTrack(it, isUserOverride = false) }
                        }
                    }
                } else {
                    // Per-item then per-series language rule overrides the
                    // global preferred audio language when set.
                    val resolvedAudioLang = playbackPreferenceResolver.resolved.value?.audioLanguage
                        ?: prefAudioLang
                    // The full precedence ladder — G5 scoring pre-pass →
                    // audio-description preference → language match — lives in
                    // TrackSelectionPolicy now. Returns null when no match
                    // exists; we then select the Default placeholder.
                    hydrateRememberedAudioTrack()
                    val match = trackSelectionPolicy.resolveAudio(
                        AudioResolutionArgs(
                            tracks = audioTracks,
                            streams = streams,
                            resolvedLang = resolvedAudioLang,
                            preferAudioDescription = sub.preferAudioDescription,
                            remembered = rememberedAudioTrack,
                        ),
                    )
                    if (match != null) {
                        selectAudioTrack(match, isUserOverride = false)
                    } else {
                        audioTracks.firstOrNull { it.index < 0 }?.let { selectAudioTrack(it, isUserOverride = false) }
                    }
                }
            }
        }

        val pending = pendingSubtitleStreamIndex
        if (pending != null) {
            pendingSubtitleStreamIndex = null
            if (pending == -1) {
                subtitleTracks.firstOrNull { it.index < 0 }?.let { selectSubtitleTrack(it, isUserOverride = false) }
            } else {
                val subStreams = getMediaStreams()
                val targetStream = subStreams.firstOrNull { it.type == StreamType.SUBTITLE && it.index == pending }
                if (targetStream != null) {
                    // Prefer the container stream index (mpv ff-index == the
                    // server's MediaStream.index) — robust against blank/dup/
                    // translated titles. Fall back to label only for engines or
                    // side-loaded tracks that don't expose a stream index.
                    val match = trackSelectionPolicy.resolveByStreamIndex(subtitleTracks, pending, targetStream)
                    if (match != null) {
                        selectSubtitleTrack(match, isUserOverride = false)
                    }
                } else if (subStreams.isEmpty()) {
                    // Offline: server mediaStreams is empty, so the pending
                    // index is the original server stream index stamped onto the
                    // side-loaded subtitle as id == "offline:${pending}". Resolve
                    // by that id; if the engine hasn't propagated it (e.g. the
                    // sub hasn't side-loaded yet) the next availableTracks
                    // emission re-runs the restore path and picks it up.
                    trackSelectionPolicy.resolveByOfflineSubtitleId(subtitleTracks, pending)
                        ?.let { selectSubtitleTrack(it, isUserOverride = false) }
                }
            }
        } else if (!subtitleSelectionHeld) {
            // Re-resolve stored/preference only while no selection is held for
            // this item. The classic "subtitle flashes then resets" bug on
            // offline playback: user picks a sub → persistStreamSelectionFromPlayer
            // stores null (resolveMediaStreamIndex fails on empty mediaStreams)
            // → the next availableTracks emission re-entered this block → no
            // stored index, language-preference match against empty streams
            // failed → fell through to selectSubtitleTrack(Off), wiping the
            // override the user just set. Guarding it keeps the held selection.
            val itemId = getCurrentItemId()
            if (itemId != null) {
                val engine = engineStore.playerEngine.value
                val sub = subtitleStore.subtitle.value
                val stored = engine.mediaStreamSelections[itemId]
                val subIdx = stored?.subtitleStreamIndex
                val prefSubLang = sub.preferredSubtitleLanguage ?: "eng"
                if (subIdx != null) {
                    if (subIdx == -1) {
                        subtitleTracks.firstOrNull { it.index < 0 }?.let { selectSubtitleTrack(it, isUserOverride = false) }
                    } else {
                        val targetStream = streams.firstOrNull {
                            it.type == StreamType.SUBTITLE && it.index == subIdx
                        }
                        // Prefer the container stream index (mpv ff-index), which
                        // equals the stored server index; fall back to label for
                        // engines/side-loaded tracks without one. Offline (no
                        // server streams) resolves the side-loaded subtitle by
                        // its `"offline:${index}"` id (both ExoPlayer and mpv
                        // propagate it into TrackOption.id), then falls back to
                        // a positional-index match for legacy tracks without it.
                        val resolved = trackSelectionPolicy.resolveByStreamIndex(subtitleTracks, subIdx, targetStream)
                        if (resolved != null) {
                            selectSubtitleTrack(resolved, isUserOverride = false)
                        } else if (streams.isEmpty()) {
                            trackSelectionPolicy.resolveByOfflineSubtitleId(subtitleTracks, subIdx)
                                ?.let { selectSubtitleTrack(it, isUserOverride = false) }
                                ?: subtitleTracks.firstOrNull { it.index == subIdx }
                                    ?.let { selectSubtitleTrack(it, isUserOverride = false) }
                        }
                    }
                } else {
                    // Per-item then per-series preference overrides the global
                    // preferred subtitle language when set.
                    val resolvedPref = playbackPreferenceResolver.resolved.value
                    // An explicit "subtitles off" intent (item scope over series)
                    // short-circuits the matcher: force Off and skip the language
                    // ladder entirely, mirroring how subIdx == -1 works above for
                    // the per-item stream-index override.
                    if (resolvedPref?.subtitleDisabled == true) {
                        subtitleTracks.firstOrNull { it.index < 0 }
                            ?.let { selectSubtitleTrack(it, isUserOverride = false) }
                    } else {
                        val resolvedSubLang = resolvedPref?.subtitleLanguage
                            ?: prefSubLang
                        val forcedOnly = sub.subtitlesForcedOnly
                        // The full precedence ladder — G5 scoring pre-pass (non-forced
                        // only) → forced-only stream pick → tiered SubtitleTrackMatcher
                        // → null — lives in TrackSelectionPolicy now. Returns null when
                        // no same-language track exists; we then select Off.
                        if (!forcedOnly) {
                            hydrateRememberedSubtitleTrack()
                        }
                        val match = trackSelectionPolicy.resolveSubtitle(
                            SubtitleResolutionArgs(
                                tracks = subtitleTracks,
                                streams = streams,
                                lang = resolvedSubLang,
                                forcedOnly = forcedOnly,
                                forced = resolvedPref?.subtitleForced,
                                hearingImpaired = resolvedPref?.subtitleHearingImpaired,
                                remembered = if (forcedOnly) null else rememberedSubtitleTrack,
                            ),
                        )
                        if (match != null) {
                            selectSubtitleTrack(match, isUserOverride = false)
                        } else {
                            subtitleTracks.firstOrNull { it.index < 0 }?.let { selectSubtitleTrack(it, isUserOverride = false) }
                        }
                    }
                }
            }
        }
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
