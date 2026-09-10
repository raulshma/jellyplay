package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cue-preview state for the subtitle-sync sheet. Null [cues] + [SubtitlePreviewSource.NONE]
 * means no preview is available (image subs, unsupported engines).
 */
@Immutable
data class SubtitlePreviewState(
    /** Parsed cue list for the active subtitle track (see [SubtitlePreviewSource]). */
    val cues: List<TimedCue>? = null,
    /** Which source populated [cues] — external full track vs. embedded played range. */
    val source: SubtitlePreviewSource = SubtitlePreviewSource.NONE,
    /** Whether the AV-sync sheet (the only consumer of [cues]) is open. */
    val sheetVisible: Boolean = false,
)

/**
 * Owns the subtitle cue-preview cluster for the AV-sync sheet (G10): resolving
 * the active external subtitle source, loading its parsed cues, accumulating
 * the engine's embedded-subtitle cues as the fallback, and the sheet-visibility
 * gate that keeps the high-frequency cue pump from churning state while the
 * sheet is closed.
 *
 * Extracted from [VideoPlayerViewModel], continuing the collaborator pattern
 * established by [SleepTimerController] / [SubtitleManager]. This class is the
 * single home of the preview trio that used to live as flat
 * [VideoPlayerUiState] fields (`subtitlePreviewCues` /
 * `subtitlePreviewSource` / `previewSheetVisible`); it exposes them as one
 * [SubtitlePreviewState] [StateFlow] instead.
 *
 * All engine/session access is via constructor lambdas so this class stays
 * ViewModel-agnostic; the load seam mirrors
 * [com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository.loadCues]
 * so tests substitute a plain suspend lambda.
 */
internal class SubtitlePreviewController(
    private val scope: CoroutineScope,
    /** Parses an external [SubtitleSource] into cues; null = not text-parseable. */
    private val loadCues: suspend (SubtitleSource, Map<String, String>) -> List<TimedCue>?,
    /** Drops the repository's memoized cue cache (see `SubtitlePreviewRepository.clearCache`). */
    private val clearCuesCache: () -> Unit,
    /** The session's current side-loaded external subtitle sources, or null. */
    private val getExternalSubtitles: () -> List<SubtitleSource>?,
    /** Auth headers for server-served HTTP subtitle URLs, or null. */
    private val getPlaybackHeaders: () -> Map<String, String>?,
    /** The currently selected subtitle [TrackOption], if one with an engine index is selected. */
    private val getSelectedSubtitleTrack: () -> TrackOption?,
    /** The engine's current embedded cue list (non-empty only), or null. */
    private val getEngineCues: () -> List<TimedCue>?,
) {

    private val _state = MutableStateFlow(SubtitlePreviewState())
    val state: StateFlow<SubtitlePreviewState> = _state.asStateFlow()

    /** In-flight external cue load; cancelled on track change / reset so a stale result can't overwrite a newer one. */
    private var loadJob: Job? = null

    /**
     * Loads the parsed cue list for the active external subtitle track so the
     * AV-sync sheet's cue-preview can render prev/active/next lines. Resolves
     * the active track by intersecting the selected subtitle [TrackOption] with
     * the session's external subtitles: exact id match first (ExoPlayer
     * side-loaded tracks carry the source id), label match as fallback. Clears
     * the preview when the active track has no parseable external source
     * (embedded/image subs during DIRECT_PLAY) — the engine-accumulated
     * [EMBEDDED][SubtitlePreviewSource.EMBEDDED] cues then take over.
     */
    fun onTrackSelectionChanged() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val externalSubs = getExternalSubtitles() ?: emptyList()
            if (externalSubs.isEmpty()) {
                // No external source: let the engine-accumulated cues (embedded
                // subs) take over by clearing the external-source precedence.
                _state.update { it.copy(cues = null, source = SubtitlePreviewSource.NONE) }
                return@launch
            }
            val selected = getSelectedSubtitleTrack()
            val source = resolveActivePreviewSource(externalSubs, selected)
            if (source == null) {
                // The selected track is embedded/image or unknown — never guess a
                // different external track, that would preview the wrong subtitle.
                _state.update { it.copy(cues = null, source = SubtitlePreviewSource.NONE) }
                return@launch
            }
            val cues = loadCues(source, getPlaybackHeaders() ?: emptyMap())
            _state.update {
                it.copy(
                    cues = cues,
                    source = if (cues != null) SubtitlePreviewSource.EXTERNAL else SubtitlePreviewSource.NONE,
                )
            }
        }
    }

    /**
     * Embedded-cue pump entry, fed from the engine's `currentCues` collector.
     * Only wins when no external text source is active — external gives the
     * full track in both offset directions; engine accumulation covers the
     * played range only. Gated on [SubtitlePreviewState.sheetVisible]:
     * ExoPlayer fires onCues several times a second, and nothing renders the
     * cues while the sheet is closed, so copying state on every tick then is
     * pure overhead. EXTERNAL source is populated on-demand by
     * [onTrackSelectionChanged] and is exempt.
     */
    fun onEngineCues(engineCues: List<TimedCue>) {
        val s = _state.value
        if (s.source != SubtitlePreviewSource.EXTERNAL && s.sheetVisible) {
            val cues = engineCues.takeIf { it.isNotEmpty() }
            if (s.cues == cues && cues != null) return
            _state.update {
                it.copy(
                    cues = cues,
                    source = if (cues != null) SubtitlePreviewSource.EMBEDDED else SubtitlePreviewSource.NONE,
                )
            }
        }
    }

    /**
     * Toggles [SubtitlePreviewState.sheetVisible]. Called by the screen as the
     * AV-sync sheet opens/dismisses. On open, immediately re-syncs the embedded
     * cue preview from the engine's current cue list so the preview isn't blank
     * until the next onCues tick (the embedded cue pump is gated on this flag,
     * so without re-syncing the first render after open is stale).
     */
    fun setSheetVisible(visible: Boolean) {
        _state.update { it.copy(sheetVisible = visible) }
        if (visible && _state.value.source != SubtitlePreviewSource.EXTERNAL) {
            val engineCues = getEngineCues()
            _state.update {
                it.copy(
                    cues = engineCues,
                    source = if (engineCues != null) SubtitlePreviewSource.EMBEDDED else SubtitlePreviewSource.NONE,
                )
            }
        }
    }

    /** Clears the cue preview and drops the repository cache (e.g. when the active subtitle track changes). */
    fun clearCues() {
        loadJob?.cancel()
        loadJob = null
        clearCuesCache()
        _state.update { it.copy(cues = null, source = SubtitlePreviewSource.NONE) }
    }

    /** Item-switch reset: preview cleared, cache dropped, sheet flag back down. */
    fun resetForItem() {
        clearCues()
        _state.update { it.copy(sheetVisible = false) }
    }

    private fun resolveActivePreviewSource(
        externalSubs: List<SubtitleSource>,
        selected: TrackOption?,
    ): SubtitleSource? {
        if (selected == null) return null
        val byId = selected.id?.let { id -> externalSubs.firstOrNull { it.id == id } }
        if (byId != null) return byId
        return externalSubs.firstOrNull { it.label == selected.label }
    }
}
