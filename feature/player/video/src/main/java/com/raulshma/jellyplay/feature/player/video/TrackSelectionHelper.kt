package com.raulshma.jellyplay.feature.player.video

import androidx.media3.common.TrackGroup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val trackGroup: TrackGroup? = null,
)

internal class TrackSelectionHelper(
    private val preferencesStore: UserPreferencesStore,
    private val getEngine: () -> MediaEngine?,
    private val getUiState: () -> VideoPlayerUiState,
    private val updateUiState: ((VideoPlayerUiState) -> VideoPlayerUiState) -> Unit,
    private val getCurrentItemId: () -> String?,
    private val scope: CoroutineScope,
) {
    private var selectedSubtitleTrackId: Pair<Int, Any?>? = null
    private var selectedAudioTrackId: Pair<Int, Any?>? = null

    @Suppress("DEPRECATION")
    private var pendingSubtitleStreamIndex: Int? = null
    private var pendingAudioStreamIndex: Int? = null

    fun setPendingStreams(subtitleIndex: Int?, audioIndex: Int?) {
        pendingSubtitleStreamIndex = subtitleIndex
        pendingAudioStreamIndex = audioIndex
    }

    fun selectAudioTrack(option: TrackOption, isUserOverride: Boolean = true) {
        val engine = getEngine() ?: return
        engine.selectTrack(TrackType.AUDIO, option.index, option.trackGroup)
        if (option.index < 0) {
            selectedAudioTrackId = null
        } else {
            selectedAudioTrackId = option.index to option.trackGroup
        }
        updateUiState { state ->
            val isDefault = option.index < 0
            state.copy(audioTracks = state.audioTracks.map { track ->
                val matches = track.index == option.index && track.trackGroup == option.trackGroup
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

        engine.selectTrack(TrackType.SUBTITLE, option.index, option.trackGroup)

        if (option.index < 0) {
            selectedSubtitleTrackId = null
        } else {
            selectedSubtitleTrackId = option.index to option.trackGroup
        }

        updateUiState { state ->
            val isOff = option.index < 0
            state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                val matches = track.index == option.index && track.trackGroup == option.trackGroup
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

        val rawAudioTracks = rawTracks.filter { it.type == TrackType.AUDIO }
        val prevAudioSel = selectedAudioTrackId
        if (prevAudioSel != null) {
            val targetTrack = rawAudioTracks.find { it.index == prevAudioSel.first && it.trackGroup == prevAudioSel.second }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(TrackType.AUDIO, targetTrack.index, targetTrack.trackGroup)
                return
            }
        }

        val rawSubTracks = rawTracks.filter { it.type == TrackType.SUBTITLE }
        val prevSubSel = selectedSubtitleTrackId
        if (prevSubSel != null) {
            val targetTrack = rawSubTracks.find { it.index == prevSubSel.first && it.trackGroup == prevSubSel.second }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(TrackType.SUBTITLE, targetTrack.index, targetTrack.trackGroup)
                return
            }
        }

        val audioOptions = rawAudioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? TrackGroup)
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            val sel = selectedAudioTrackId
            val hasSelectionMatch = audioOptions.any { sel != null && sel.first == it.index && sel.second == it.trackGroup }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = audioOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedAudioTrackId = engineAutoSelected.index to engineAutoSelected.trackGroup
                    selectedAudioTrackId
                } else null
            }
            listOf(TrackOption(-1, "Default", null, resolvedSel == null)) + audioOptions.map { t ->
                val isSel = if (resolvedSel != null) {
                    resolvedSel.first == t.index && resolvedSel.second == t.trackGroup
                } else {
                    t.isSelected
                }
                t.copy(isSelected = isSel)
            }
        }

        val engineSubOptions = rawSubTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? TrackGroup)
        }

        val subtitleTracks = if (engineSubOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackId
            val hasSelectionMatch = engineSubOptions.any { sel != null && sel.first == it.index && sel.second == it.trackGroup }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = engineSubOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedSubtitleTrackId = engineAutoSelected.index to engineAutoSelected.trackGroup
                    selectedSubtitleTrackId
                } else null
            }
            listOf(TrackOption(-1, "Off", null, resolvedSel == null)) + engineSubOptions.map { t ->
                val isSel = if (resolvedSel != null) {
                    resolvedSel.first == t.index && resolvedSel.second == t.trackGroup
                } else {
                    t.isSelected
                }
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
                    val match = audioTracks.firstOrNull { it.index >= 0 && isLanguageMatch(it.language, prefAudioLang) }
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
                    val forcedOnly = currentPrefs.subtitlesForcedOnly
                    val match = if (forcedOnly) {
                        val forcedStream = streams
                            .firstOrNull { it.type == StreamType.SUBTITLE && it.isForced && isLanguageMatch(it.language, prefSubLang) }
                            ?: streams.firstOrNull { it.type == StreamType.SUBTITLE && it.isForced }
                        if (forcedStream != null) {
                            val forcedLabel = forcedStream.displayTitle ?: forcedStream.title ?: forcedStream.language
                            subtitleTracks.firstOrNull { it.index >= 0 && it.label == forcedLabel }
                        } else {
                            null
                        }
                    } else {
                        subtitleTracks.firstOrNull { it.index >= 0 && isLanguageMatch(it.language, prefSubLang) }
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
        selectedAudioTrackId = null
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
        selectedSubtitleTrackId = null
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
        selectedSubtitleTrackId = null
        selectedAudioTrackId = null
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null
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
}
