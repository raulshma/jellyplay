package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaStream
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
    private val playbackPreferenceResolver: ItemPlaybackPreferenceResolver,
    private val scope: CoroutineScope,
) {
    private var selectedSubtitleTrackIndex: Int? = null
    private var selectedAudioTrackIndex: Int? = null

    @Suppress("DEPRECATION")
    private var pendingSubtitleStreamIndex: Int? = null
    private var pendingAudioStreamIndex: Int? = null

    fun setPendingStreams(subtitleIndex: Int?, audioIndex: Int?) {
        pendingSubtitleStreamIndex = subtitleIndex
        pendingAudioStreamIndex = audioIndex
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
        val engine = getEngine() ?: return
        engine.selectTrack(TrackType.AUDIO, option.index)
        selectedAudioTrackIndex = if (option.index < 0) null else option.index
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
        val engine = getEngine() ?: return

        engine.selectTrack(TrackType.SUBTITLE, option.index)

        selectedSubtitleTrackIndex = if (option.index < 0) null else option.index

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

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            val sel = selectedAudioTrackIndex
            val hasSelectionMatch = audioOptions.any { it.index == sel }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = audioOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedAudioTrackIndex = engineAutoSelected.index
                    selectedAudioTrackIndex
                } else null
            }
            listOf(TrackOption(-1, "Default", null, resolvedSel == null)) + audioOptions.map { t ->
                val isSel = if (resolvedSel != null) resolvedSel == t.index else t.isSelected
                t.copy(isSelected = isSel)
            }
        }

        val engineSubOptions = rawSubTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }

        val subtitleTracks = if (engineSubOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackIndex
            val hasSelectionMatch = engineSubOptions.any { it.index == sel }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = engineSubOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedSubtitleTrackIndex = engineAutoSelected.index
                    selectedSubtitleTrackIndex
                } else null
            }
            listOf(TrackOption(-1, "Off", null, resolvedSel == null)) + engineSubOptions.map { t ->
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
        } else {
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
        } else {
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
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null
        playbackPreferenceResolver.clear()
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
    ): Int? {        val typedStreams = streams.filter { it.type == type }
        val trackLabel = trackOption.label
        val trackLanguage = trackOption.language

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
