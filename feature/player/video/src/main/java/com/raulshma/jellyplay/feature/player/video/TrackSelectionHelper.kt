package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Immutable
data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

internal class TrackSelectionHelper(
    private val preferencesStore: UserPreferencesStore,
    private val getEngine: () -> MediaEngine?,
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val getCurrentItemId: () -> String?,
    private val getCurrentSeriesId: () -> String?,
    private val getPlayMethod: () -> PlayMethod,
    private val onReloadForStreamChange: (audioStreamIndex: Int?, subtitleStreamIndex: Int?) -> Unit,
    private val playbackPreferenceResolver: ItemPlaybackPreferenceResolver,
    private val scope: CoroutineScope,
) {
    private var selectedSubtitleTrackIndex: Int? = null
    private var selectedAudioTrackIndex: Int? = null

    // A selection (auto or manual) is "held" once applied via selectAudioTrack /
    // selectSubtitleTrack. While held, updateTracksFromEngine does NOT re-run the
    // stored/pending/preference resolution that can otherwise flip it — that
    // re-resolution was the cause of the "subtitle flashes then resets" bug on
    // offline playback (empty mediaStreams → resolveMediaStreamIndex returned
    // null → the next availableTracks emission re-resolved and dropped to Off).
    // Cleared on a fresh item load (setPendingStreams) and explicit reset.
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
    }

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
            val streamIndex = option.index - SERVER_TRACK_INDEX_BASE
            selectedAudioTrackIndex = option.index
            audioSelectionHeld = true
            updateUiState { state ->
                state.copy(audioTracks = state.audioTracks.map { track ->
                    track.copy(isSelected = track.index == option.index)
                })
            }
            if (isUserOverride) {
                onReloadForStreamChange(streamIndex, null)
            }
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
        updateUiState { state ->
            val isDefault = option.index < 0
            state.copy(audioTracks = state.audioTracks.map { track ->
                val matches = track.index == option.index
                val isDefaultTrack = track.index < 0
                track.copy(isSelected = if (isDefault) isDefaultTrack else matches)
            })
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
            val streamIndex = option.index - SERVER_TRACK_INDEX_BASE
            selectedSubtitleTrackIndex = option.index
            subtitleSelectionHeld = true
            updateUiState { state ->
                state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                    track.copy(isSelected = track.index == option.index)
                })
            }
            if (isUserOverride) {
                onReloadForStreamChange(null, streamIndex)
            }
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

        updateUiState { state ->
            val isOff = option.index < 0
            state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                val matches = track.index == option.index
                val isOffTrack = track.index < 0
                track.copy(isSelected = if (isOff) isOffTrack else matches)
            })
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
        val streams = getUiState().mediaStreams
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
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }

        // For transcoded / direct-stream playback the server bakes a single
        // audio track into the HLS manifest, so mpv's track-list surfaces only
        // that one — the picker would otherwise hide every other audio stream.
        // Supplement with server-side mediaStreams (deduped by label against
        // the engine tracks) so the user can see and switch to any audio track.
        // Selecting a server-origin track triggers a session reload (see
        // selectAudioTrack) since mpv cannot switch audio in-place on a
        // transcode.
        val mergedAudioOptions = if (getPlayMethod() != PlayMethod.DIRECT_PLAY) {
            mergeServerStreams(audioOptions, streams, StreamType.AUDIO)
        } else {
            audioOptions
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
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }

        // Same merge for subtitles: side-loaded external subs may take a moment
        // to resolve in mpv's track-list, and embedded subs aren't in the
        // transcode manifest at all. Surface all server subtitle streams so the
        // picker is populated immediately; selecting one side-loads it.
        val mergedSubOptions = if (getPlayMethod() != PlayMethod.DIRECT_PLAY) {
            mergeServerStreams(engineSubOptions, streams, StreamType.SUBTITLE)
        } else {
            engineSubOptions
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

        updateUiState { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }

        val pendingAudio = pendingAudioStreamIndex
        if (pendingAudio != null) {
            pendingAudioStreamIndex = null
            if (pendingAudio == -1) {
                audioTracks.firstOrNull { it.index < 0 }?.let { selectAudioTrack(it, isUserOverride = false) }
            } else {
                val targetStream = streams.firstOrNull {
                    it.type == StreamType.AUDIO && it.index == pendingAudio
                }
                val matchByIndex = audioTracks.firstOrNull { it.index >= 0 && it.index == pendingAudio }
                val matchByLabel = if (matchByIndex == null && targetStream != null) {
                    val targetLabel = targetStream.displayTitle ?: targetStream.title ?: targetStream.language
                    audioTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }
                } else null
                (matchByIndex ?: matchByLabel)?.let { selectAudioTrack(it, isUserOverride = false) }
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
                val currentPrefs = preferencesStore.preferences.value
                val stored = currentPrefs.mediaStreamSelections[itemId]
                val audioIdx = stored?.audioStreamIndex
                val prefAudioLang = currentPrefs.preferredAudioLanguage ?: "eng"
                if (audioIdx != null) {
                    if (audioIdx == -1) {
                        audioTracks.firstOrNull { it.index < 0 }?.let { selectAudioTrack(it, isUserOverride = false) }
                    } else {
                        val targetStream = streams.firstOrNull {
                            it.type == StreamType.AUDIO && it.index == audioIdx
                        }
                        val targetLabel = targetStream?.displayTitle ?: targetStream?.title ?: targetStream?.language
                        if (targetLabel != null) {
                            audioTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }?.let { selectAudioTrack(it, isUserOverride = false) }
                        } else if (streams.isEmpty()) {
                            // Offline restore: stored index is the engine
                            // positional index (no server streams to map a label).
                            audioTracks.firstOrNull { it.index == audioIdx }?.let { selectAudioTrack(it, isUserOverride = false) }
                        }
                    }
                } else {
                    // Per-item then per-series language rule overrides the
                    // global preferred audio language when set.
                    val resolvedAudioLang = playbackPreferenceResolver.resolved.value?.audioLanguage
                        ?: prefAudioLang
                    val match = pickPreferredAudioTrack(
                        audioTracks = audioTracks,
                        streams = streams,
                        prefAudioLang = resolvedAudioLang,
                        preferAudioDescription = currentPrefs.preferAudioDescription,
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
                val subStreams = getUiState().mediaStreams
                val targetStream = subStreams.firstOrNull { it.type == StreamType.SUBTITLE && it.index == pending }
                if (targetStream != null) {
                    val targetLabel = targetStream.displayTitle ?: targetStream.title ?: targetStream.language
                    val match = subtitleTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }
                    if (match != null) {
                        selectSubtitleTrack(match, isUserOverride = false)
                    }
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
                val currentPrefs = preferencesStore.preferences.value
                val stored = currentPrefs.mediaStreamSelections[itemId]
                val subIdx = stored?.subtitleStreamIndex
                val prefSubLang = currentPrefs.preferredSubtitleLanguage ?: "eng"
                if (subIdx != null) {
                    if (subIdx == -1) {
                        subtitleTracks.firstOrNull { it.index < 0 }?.let { selectSubtitleTrack(it, isUserOverride = false) }
                    } else {
                        val targetStream = streams.firstOrNull {
                            it.type == StreamType.SUBTITLE && it.index == subIdx
                        }
                        val targetLabel = targetStream?.displayTitle ?: targetStream?.title ?: targetStream?.language
                        if (targetLabel != null) {
                            subtitleTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }?.let { selectSubtitleTrack(it, isUserOverride = false) }
                        } else if (streams.isEmpty()) {
                            // Offline restore: no server streams to map a label
                            // from, so the stored index is an engine positional
                            // index (see resolveMediaStreamIndex). Match it
                            // directly so a prior offline subtitle selection
                            // survives a session reload.
                            subtitleTracks.firstOrNull { it.index == subIdx }?.let { selectSubtitleTrack(it, isUserOverride = false) }
                        }
                    }
                } else {
                    // Per-item then per-series language rule overrides the
                    // global preferred subtitle language when set.
                    val resolvedSubLang = playbackPreferenceResolver.resolved.value?.subtitleLanguage
                        ?: prefSubLang
                    val forcedOnly = currentPrefs.subtitlesForcedOnly
                    val match = if (forcedOnly) {
                        val forcedStream = streams
                            .firstOrNull { it.type == StreamType.SUBTITLE && it.isForced && isLanguageMatch(it.language, resolvedSubLang) }
                            ?: streams.firstOrNull { it.type == StreamType.SUBTITLE && it.isForced }
                        if (forcedStream != null) {
                            val forcedLabel = forcedStream.displayTitle ?: forcedStream.title ?: forcedStream.language
                            subtitleTracks.firstOrNull { it.index >= 0 && it.label == forcedLabel }
                        } else {
                            null
                        }
                    } else {
                        subtitleTracks.firstOrNull { it.index >= 0 && isLanguageMatch(it.language, resolvedSubLang) }
                    }
                    if (match != null) {
                        selectSubtitleTrack(match, isUserOverride = false)
                    } else {
                        subtitleTracks.firstOrNull { it.index < 0 }?.let { selectSubtitleTrack(it, isUserOverride = false) }
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
            val currentSelection = preferencesStore.preferences.value.mediaStreamSelections[itemId]
            preferencesStore.setMediaStreamSelection(
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
            val currentSelection = preferencesStore.preferences.value.mediaStreamSelections[itemId]
            preferencesStore.setMediaStreamSelection(
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
            val label = stream.displayTitle ?: stream.title ?: stream.language ?: continue
            if (label.lowercase() in engineLabels) continue
            val syntheticIndex = SERVER_TRACK_INDEX_BASE + stream.index
            merged.add(
                TrackOption(
                    index = syntheticIndex,
                    label = label,
                    language = stream.language,
                    isSelected = false,
                )
            )
        }
        return merged
    }

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
        val streams = getUiState().mediaStreams
        val currentSelection = preferencesStore.preferences.value.mediaStreamSelections[itemId]
        val audioStreamIndex = if (audioTrackOption != null) {
            if (audioTrackOption.index < 0) -1
            else resolveMediaStreamIndex(streams, StreamType.AUDIO, audioTrackOption)
        } else {
            currentSelection?.audioStreamIndex
        }
        val subtitleStreamIndex = if (subtitleTrackOption != null) {
            if (subtitleTrackOption.index < 0) -1
            else resolveMediaStreamIndex(streams, StreamType.SUBTITLE, subtitleTrackOption)
        } else {
            currentSelection?.subtitleStreamIndex
        }
        scope.launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
        }
    }

    private fun resolveMediaStreamIndex(
        streams: List<MediaStream>,
        type: StreamType,
        trackOption: TrackOption,
    ): Int? {
        val typedStreams = streams.filter { it.type == type }
        val trackLabel = trackOption.label
        val trackLanguage = trackOption.language

        // Offline playback carries no server mediaStreams (detail.mediaSources is
        // empty), so there is nothing to match against here. Persist the engine
        // track's positional index directly — it's the only stable handle for
        // offline sidecar subs, and restore-on-reload re-matches by label in
        // updateTracksFromEngine. Previously this returned null offline, so the
        // stored per-item selection silently became null and never restored.
        if (typedStreams.isEmpty()) return trackOption.index

        val exactMatch = typedStreams.firstOrNull {
            it.displayTitle == trackLabel || it.title == trackLabel || it.language == trackLabel
        }
        if (exactMatch != null) return exactMatch.index

        if (trackLanguage != null) {
            val languageMatches = typedStreams.filter { isLanguageMatch(it.language, trackLanguage) }
            if (languageMatches.isNotEmpty()) {
                if (languageMatches.size == 1) return languageMatches[0].index
                val bestMatch = languageMatches.firstOrNull { stream ->
                    val streamTitle = stream.displayTitle ?: stream.title ?: ""
                    streamTitle.isNotBlank() && (trackLabel.contains(streamTitle, ignoreCase = true) || streamTitle.contains(trackLabel, ignoreCase = true))
                } ?: languageMatches.firstOrNull { it.isDefault } ?: languageMatches.first()
                return bestMatch.index
            }
        }

        return typedStreams.firstOrNull { it.index >= 0 }?.index
    }

    /**
     * Picks the best audio [TrackOption] for automatic (no stored override)
     * selection. When [preferAudioDescription] is enabled, descriptive tracks
     * (matched via title/label keywords) are preferred over the default
     * language match so visually-impaired users get narration by default.
     */
    private fun pickPreferredAudioTrack(
        audioTracks: List<TrackOption>,
        streams: List<MediaStream>,
        prefAudioLang: String,
        preferAudioDescription: Boolean,
    ): TrackOption? {
        val selectable = audioTracks.filter { it.index >= 0 }
        if (selectable.isEmpty()) return null
        if (preferAudioDescription) {
            val descriptiveStreamIdx = streams
                .firstOrNull { it.type == StreamType.AUDIO && isAudioDescriptionStream(it) }
                ?.index
            if (descriptiveStreamIdx != null) {
                // Engine track indices are positional (0..n) while mediaStream
                // indices come from the server. Match by label as a bridge.
                val targetStream = streams.firstOrNull { it.index == descriptiveStreamIdx }
                val targetLabel = targetStream?.displayTitle ?: targetStream?.title
                val match = selectable.firstOrNull { opt ->
                    opt.label == targetLabel || (targetLabel != null && opt.label.contains(targetLabel, ignoreCase = true))
                } ?: selectable.firstOrNull { opt -> isAudioDescriptionLabel(opt.label) }
                if (match != null) return match
            }
        }
        return selectable.firstOrNull { isLanguageMatch(it.language, prefAudioLang) }
    }

    /** Heuristics for detecting audio-description tracks from titles/labels. */
    private fun isAudioDescriptionStream(stream: MediaStream): Boolean {
        val title = stream.displayTitle ?: stream.title ?: return false
        return isAudioDescriptionLabel(title)
    }

    private fun isAudioDescriptionLabel(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("description") ||
            lower.contains("descriptive") ||
            lower.contains("narration") ||
            lower.contains(" dvs") ||
            lower.endsWith(" ad")
    }
}
