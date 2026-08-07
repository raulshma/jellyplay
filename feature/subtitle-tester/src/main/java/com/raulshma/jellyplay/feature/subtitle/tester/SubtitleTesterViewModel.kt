package com.raulshma.jellyplay.feature.subtitle.tester

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.subtitle.tester.preview.PlaybackRequestFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubtitleTesterViewModel @Inject constructor(
    private val engineFactory: PlayerEngineFactory,
    private val subtitleLanguageStore: com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore,
    private val fontProvider: FontProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val playbackRequestFactory = PlaybackRequestFactory(context)

    private val _uiState = MutableStateFlow(SubtitleTesterUiState())
    val uiState: StateFlow<SubtitleTesterUiState> = _uiState.asStateFlow()

    private val _activeEngine = MutableStateFlow<MediaEngine?>(null)
    val activeEngine: StateFlow<MediaEngine?> = _activeEngine.asStateFlow()

    private var engine: MediaEngine? = null

    // Loop job: the bundled host clip is only a few seconds long. Without
    // looping the player hits ENDED and stops rendering subtitles, leaving a
    // frozen frame with no cues. This relaunches the clip on ENDED so the
    // preview keeps showing live subtitle styling. All supported engines
    // (ExoPlayer / mpv / libVLC) surface ENDED via [MediaEngine.playbackState]
    // and implement seekTo/play, so this is engine-agnostic.
    private var loopJob: Job? = null

    // Seed-once guard: avoid re-seeding working copies after the first pref
    // emission (e.g. when an apply/external edit flows back through the StateFlow).
    private var seeded = false

    init {
        // Seed working copies from current prefs once, then load the preview engine.
        viewModelScope.launch {
            subtitleLanguageStore.subtitle.collect { prefs ->
                if (!seeded) {
                    seeded = true
                    // Force applyCustomStyle = true on the working copies: the tester
                    // hides the override toggle, so every edit must take effect on the
                    // preview. The originals stay verbatim for accurate dirty checks.
                    val sdr = prefs.subtitleStyle.copy(applyCustomStyle = true)
                    val hdr = prefs.hdrSubtitleStyle.copy(applyCustomStyle = true)
                    _uiState.update {
                        it.copy(
                            workingSdrStyle = sdr,
                            originalSdrStyle = prefs.subtitleStyle,
                            workingHdrStyle = hdr,
                            originalHdrStyle = prefs.hdrSubtitleStyle,
                            hdrSubtitleEnabled = prefs.hdrSubtitleStyleEnabled,
                        )
                    }
                    ensureEngineLoaded()
                }
            }
        }
    }

    fun updateStyle(style: SubtitleStyle) {
        _uiState.update {
            if (it.mode == SubtitleStyleMode.HDR) {
                it.copy(workingHdrStyle = style)
            } else {
                it.copy(workingSdrStyle = style)
            }
        }
        pushConfigToEngine()
    }

    /**
     * Copies a SAF-picked font into the shared font cache and stamps the
     * resolved file path + family name onto the active mode's working style.
     * Mirrors [com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel.installUserFont];
     * the SAF uri is not persisted (only the copied local file survives).
     */
    fun installUserFont(uri: android.net.Uri) {
        viewModelScope.launch {
            val installed = fontProvider.installUserFont(uri) ?: return@launch
            updateStyle(
                activeStyleCopy().copy(
                    fontFamilyPath = installed.file.absolutePath,
                    fontFamilyName = installed.familyName,
                ),
            )
        }
    }

    private fun activeStyleCopy(): SubtitleStyle = _uiState.value.activeWorkingStyle

    fun setMode(mode: SubtitleStyleMode) {
        _uiState.update { it.copy(mode = mode) }
        pushConfigToEngine()
    }

    fun setHdrSubtitleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hdrSubtitleEnabled = enabled) }
    }

    fun switchEngine(type: PlayerType) {
        _uiState.update { it.copy(isApplying = true, previewEngine = type) }
        viewModelScope.launch {
            releaseEngine()
            ensureEngineLoaded()
        }
    }

    fun switchPreset(id: String) {
        _uiState.update { it.copy(samplePresetId = id, isApplying = true) }
        // Rebuild the engine (release + reload) rather than calling load() on
        // the existing instance: ExoPlayerEngine.load() starts with release(),
        // which nulls the bound PlayerView, and the UI only re-attaches a
        // surface when _activeEngine re-emits a new instance. Reloading in
        // place would leave a dead PlayerView showing nothing.
        viewModelScope.launch {
            releaseEngine()
            ensureEngineLoaded()
        }
    }

    fun reset() {
        _uiState.update {
            it.copy(
                workingSdrStyle = it.originalSdrStyle,
                workingHdrStyle = it.originalHdrStyle,
            )
        }
        pushConfigToEngine()
    }

    fun applyAndExit(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            // Force applyCustomStyle = true so the saved pref takes effect.
            subtitleLanguageStore.setSubtitleStyle(state.workingSdrStyle.copy(applyCustomStyle = true))
            subtitleLanguageStore.setHdrSubtitleStyle(state.workingHdrStyle.copy(applyCustomStyle = true))
            subtitleLanguageStore.setHdrSubtitleStyleEnabled(state.hdrSubtitleEnabled)
            onComplete()
        }
    }

    private fun ensureEngineLoaded() {
        val state = _uiState.value
        val newEngine = engineFactory.create(state.previewEngine)
        engine = newEngine
        val preset = SampleSubtitlePresets.byId(state.samplePresetId)
        val useAssTrack = state.activeWorkingStyle.assOverride == AssOverrideMode.FORCE
        val request = playbackRequestFactory.forPreview(preset, useAssTrack)
        newEngine.load(request)
        _activeEngine.value = newEngine
        // The host clip is short (a few seconds); loop it so subtitle cues keep
        // rendering instead of freezing on ENDED. See [loopJob] comment.
        loopJob = viewModelScope.launch {
            newEngine.playbackState
                .filter { it == EnginePlaybackState.ENDED }
                .collect {
                    newEngine.seekTo(0)
                    newEngine.play()
                }
        }
        pushConfigToEngine()
        _uiState.update { it.copy(isApplying = false) }
    }

    private fun pushConfigToEngine() {
        val state = _uiState.value
        val resolved = state.activeWorkingStyle.copy(applyCustomStyle = true)
        val config = EngineConfig(subtitleStyle = resolved)
        engine?.updateConfig(config)
    }

    private fun releaseEngine() {
        loopJob?.cancel()
        loopJob = null
        _activeEngine.value = null
        engine?.release()
        engine = null
    }

    override fun onCleared() {
        releaseEngine()
        super.onCleared()
    }
}
