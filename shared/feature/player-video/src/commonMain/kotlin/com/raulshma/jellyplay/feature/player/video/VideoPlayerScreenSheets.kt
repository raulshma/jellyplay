package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.feature.player.video.components.AVSyncSheet
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackModeSheet
import com.raulshma.jellyplay.feature.player.video.components.QualityPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.RememberPreferenceToggle
import com.raulshma.jellyplay.feature.player.video.components.RenderSheet
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerSheet
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubSheet
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayPlayerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoFilterSheet
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_audio_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitle_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_subtitles_off

// ── Section host: modal-sheet router ─────────────────────────────────────
// Owns [PlayerSheetRouter] — every PlayerSheet branch (speed, audio tracks,
// subtitle hub, chapters, playback info, aspect ratio, A/V sync, decoder,
// episodes, SyncPlay, quality, playback mode, sleep timer, video filters,
// mpv render) — plus the narrow flow-subscribing binders that keep
// high-frequency position/remaining-time collection sheet-local. Moved
// verbatim from VideoPlayerScreen.kt as a composition-only section-host
// split: declarations are unchanged except `private` → `internal` where the
// root file calls the symbol. See the section-host map on
// VideoPlayerScreen.kt.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheetRouter(
    currentSheet: PlayerSheet,
    onSheetChange: (PlayerSheet) -> Unit,
    dismissSheet: () -> Unit,
    uiState: VideoPlayerUiState,
    currentPositionFlow: StateFlow<Long>,
    sleepTimerRemainingFlow: StateFlow<Long>,
    doSeekTo: (Long) -> Unit,
    viewModel: VideoPlayerViewModel,
    itemId: String,
    syncPlayIgnoreWait: Boolean,
    onLoadLocalSubtitle: () -> Unit,
    onPickFont: () -> Unit,
    onOpenSubtitleTester: () -> Unit,
    onOpenSubtitleDelayOverlay: () -> Unit,
    /** The pending reset-first intent for this SubtitleHub open — see [onSubtitleHubResetConsumed]. */
    subtitleHubResetFirst: Boolean,
    /** Clears the consumed [subtitleHubResetFirst] flag so it stays single-shot. */
    onSubtitleHubResetConsumed: () -> Unit,
) {
    // the Render sheet's "save for this series" toggle — sheet-local
    // UI state, defaults off (session-only edits).
    var saveRenderForSeries by remember { mutableStateOf(false) }
    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(it)) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Audio -> {
            // Track slice: collected inside the branch — only this
            // picker consumes it while the sheet is open.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            TrackPickerSheet(
                title = stringResource(Res.string.player_audio),
                tracks = trackState.audioTracks,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SelectAudioTrack(it)) },
                onReset = if (trackState.hasAudioOverride) { { viewModel.onEvent(VideoPlayerUiEvent.ResetAudioTrack) } } else null,
                onDismiss = dismissSheet,
                footer = if (uiState.media.seriesId != null) {
                    {
                        // Per-series audio-language preference toggle. Saving
                        // remembers the currently-selected track's language for
                        // every episode of this series; toggling off forgets it.
                        RememberPreferenceToggle(
                            label = stringResource(Res.string.player_video_remember_audio_language),
                            checked = trackState.hasSeriesAudioPref,
                            onToggle = { remember ->
                                viewModel.onEvent(
                                    VideoPlayerUiEvent.SetSeriesAudioLanguagePreference(
                                        seriesAudioPreferenceIntent(trackState.audioTracks, remember),
                                    )
                                )
                            },
                        )
                    }
                } else null,
            )
        }
        is PlayerSheet.SubtitleHub -> {
            // The sheet's SINGLE hub-open load trigger ([SubtitleManager.openSubtitleHub]):
            // both entry clicks (Tracks tab / overflow) only route here, so an
            // open costs one remote-subtitles request — the former hand-copied
            // click cascades double-fetched (click loads, then this effect
            // cancelled and re-fetched). Timing change: the fetch
            // starts at sheet composition rather than at click (the hub's
            // loading spinner already covers the in-flight window). The reset
            // intent is per-entry: the overflow entry opens with a cleared
            // search/cultures slice (stale results must not leak across
            // items), the Tracks tab deliberately keeps prior state — the
            // flag rides from the click and is consumed single-shot. A
            // config-change sheet restore re-enters with the flag reset to
            // false, i.e. the same no-reset re-load this effect always did.
            LaunchedEffect(Unit) {
                viewModel.subtitles.openSubtitleHub(resetFirst = subtitleHubResetFirst)
                onSubtitleHubResetConsumed()
            }
            // Track + subtitle-workflow slices: collected inside the
            // branch — only this hub consumes them while the sheet is open.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val subtitleState by viewModel.subtitles.state.collectAsStateWithLifecycle()
            SubtitleHubSheet(
                initialTab = com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab.TRACKS,
                onDismiss = dismissSheet,
                // Tracks tab
                subtitleTracks = trackState.subtitleTracks,
                onSelectSubtitleTrack = { viewModel.onEvent(VideoPlayerUiEvent.SelectSubtitleTrack(it)) },
                onResetSubtitleTrack = if (trackState.hasSubtitleOverride) {
                    { viewModel.onEvent(VideoPlayerUiEvent.ResetSubtitleTrack) }
                } else null,
                tracksFooter = if (uiState.media.seriesId != null) {
                    {
                        // Per-series subtitle preference toggle. With a real track
                        // selected it saves that track's language + role so every
                        // episode restores the right same-language track; with the
                        // "Off" row selected it saves a "subtitles off" intent so
                        // every episode loads with subs off. Toggling off forgets
                        // whichever intent was saved. The intent derivation lives
                        // in [seriesSubtitlePreferenceIntent].
                        val label = if (seriesSubtitlePrefersOffLabel(trackState.subtitleTracks, trackState.hasSeriesSubtitleOffPref)) {
                            stringResource(Res.string.player_video_remember_subtitles_off)
                        } else {
                            stringResource(Res.string.player_video_remember_subtitle_language)
                        }
                        RememberPreferenceToggle(
                            label = label,
                            checked = trackState.hasSeriesSubtitlePref,
                            onToggle = { remember ->
                                when (val intent = seriesSubtitlePreferenceIntent(trackState.subtitleTracks, remember)) {
                                    is SeriesSubtitlePrefIntent.Off ->
                                        viewModel.onEvent(VideoPlayerUiEvent.SetSeriesSubtitleDisabled(intent.disabled))
                                    is SeriesSubtitlePrefIntent.Track ->
                                        viewModel.onEvent(
                                            VideoPlayerUiEvent.SetSeriesSubtitlePreference(
                                                language = intent.language,
                                                forced = intent.forced,
                                                hearingImpaired = intent.hearingImpaired,
                                            )
                                        )
                                    SeriesSubtitlePrefIntent.Forget ->
                                        viewModel.onEvent(VideoPlayerUiEvent.SetSeriesSubtitlePreference(language = null))
                                }
                            },
                        )
                    }
                } else null,
                // Style tab
                subtitleStyle = uiState.subtitleStyle,
                onStyleChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleStyle(it)) },
                onSubtitleDelayChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSubtitleDelay(it)) },
                onPickFont = onPickFont,
                onOpenTester = onOpenSubtitleTester,
                capabilities = uiState.engineCapabilities,
                // Get tab
                downloadSubtitles = subtitleState.remoteSubtitles,
                isDownloading = subtitleState.isLoadingRemoteSubtitles,
                remoteSubtitlesError = subtitleState.remoteSubtitlesError,
                onDownload = { viewModel.subtitles.downloadSubtitle(it) },
                onLoadLocalFile = onLoadLocalSubtitle,
                searchResults = subtitleState.searchedSubtitles,
                isSearching = subtitleState.isSearchingSubtitles,
                hasSearched = subtitleState.hasSearchedSubtitles,
                searchError = subtitleState.subtitleSearchError,
                cultures = subtitleState.subtitleCultures,
                defaultLanguage = subtitleState.defaultSearchLanguage,
                onSearch = { viewModel.subtitles.searchRemoteSubtitles(it) },
                onDownloadSearched = { viewModel.subtitles.downloadSubtitle(it) },
                providerSearchResults = subtitleState.providerSearchResults,
                providerSearchErrors = subtitleState.providerSearchErrors,
                configuredProviders = subtitleState.configuredSubtitleProviders,
                onSearchAllProviders = { viewModel.subtitles.searchAllProviders(it) },
                onDownloadProviderSubtitle = { viewModel.subtitles.downloadProviderSubtitle(it) },
                downloadingSubtitles = subtitleState.downloadingSubtitles,
                // "Use" activates the downloaded subtitle as the current track
                // (resolved from the manager's ready hints); on success the hub
                // shows the Tracks tab with the new selection, otherwise it
                // stays on Get while the pending selection auto-applies.
                onUseSubtitle = { rowKey -> viewModel.useDownloadedSubtitle(rowKey) },
                isUploading = subtitleState.isUploadingSubtitle,
                onUpload = { uriStr, fileName, language, isForced, isHearingImpaired ->
                    // KMP seam: the sheets hand the picked SAF
                    // document as its string form; SubtitleManager consumes it.
                    viewModel.subtitles.uploadSubtitle(
                        uriStr,
                        fileName,
                        language,
                        isForced,
                        isHearingImpaired,
                    )
                    onSheetChange(PlayerSheet.None)
                },
                // Delay tab
                currentSubtitleDelayMs = uiState.subtitleStyle.offsetMs,
                onOpenDelayOverlay = onOpenSubtitleDelayOverlay,
            )
        }
        is PlayerSheet.Chapter -> {
            // Collect position only while the chapter sheet is open, so
            // the router itself stays a low-frequency scope when no sheet (or
            // a non-chapter sheet) is shown.
            ChapterPickerBinder(
                chapters = uiState.chapters,
                currentPositionFlow = currentPositionFlow,
                onSelect = { positionTicks ->
                    doSeekTo(positionTicks / 10_000)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackInfo -> {
            // Track + effects slices: collected inside the branch.
            val trackState by viewModel.trackState.collectAsStateWithLifecycle()
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            PlayerModalBottomSheet(
                onDismissRequest = dismissSheet,
                sheetState = rememberModalBottomSheetState(),
            ) {
                PlaybackInfoOverlay(
                    mediaSource = uiState.media.currentMediaSource,
                    mediaStreams = uiState.media.mediaStreams,
                    playMethod = uiState.media.playMethod,
                    isConnectionMetered = uiState.isConnectionMetered,
                    hdrType = uiState.hdrType,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = effectsState.decoderMode.name,
                    aspectRatio = uiState.videoFx.aspectRatio.name,
                    nightModeEnabled = effectsState.nightModeEnabled,
                    nightModeStrength = effectsState.nightModeStrength,
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                    dialogueBoostStrength = uiState.dialogueBoostStrength,
                    audioPassthrough = effectsState.audioPassthrough,
                    audioTracks = trackState.audioTracks,
                    subtitleTracks = trackState.subtitleTracks,
                    playbackSpeed = uiState.playbackSpeed,
                    audioDelayMs = effectsState.audioDelayMs,
                    subtitleDelayMs = uiState.subtitleStyle.offsetMs,
                    playerError = uiState.playerError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                )
            }
        }
        is PlayerSheet.AspectRatio -> {
            AspectRatioSheet(
                currentRatio = uiState.videoFx.aspectRatio,
                detectedRatio = uiState.videoFx.detectedAspectRatio,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetAspectRatio(it)) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.AVSync -> {
            // Effects slice: collected inside the branch.
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            AVSyncSheet(
                currentAudioDelayMs = effectsState.audioDelayMs,
                onAudioDelayChange = { viewModel.effects.setAudioDelay(it) },
                onDismiss = dismissSheet,
                audioDelaySupported = uiState.engineCapabilities.supportsAudioDelay,
            )
        }
        is PlayerSheet.Decoder -> {
            // Effects slice: collected inside the branch.
            val effectsState by viewModel.effects.state.collectAsStateWithLifecycle()
            DecoderPickerSheet(
                currentMode = effectsState.decoderMode,
                onSelect = { viewModel.effects.setDecoderMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Episodes -> {
            EpisodePickerSheet(
                seasons = uiState.episodes.seriesSeasons,
                episodes = uiState.episodes.seasonEpisodes,
                currentSeasonId = uiState.episodes.currentSeasonId,
                currentEpisodeId = itemId,
                isLoading = uiState.episodes.isLoadingEpisodes,
                onSeasonSelect = { viewModel.onEvent(VideoPlayerUiEvent.LoadSeasonEpisodes(it)) },
                onEpisodeSelect = { episode ->
                    viewModel.onEvent(VideoPlayerUiEvent.PlayEpisode(episode.id, episode.playbackPositionTicks ?: 0L))
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
                getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            )
        }
        is PlayerSheet.SyncPlay -> {
            // SyncPlay group-display slice: collected inside the branch.
            val syncPlayState by viewModel.syncPlay.state.collectAsStateWithLifecycle()
            SyncPlayPlayerSheet(
                groupName = syncPlayState.syncPlayGroupName ?: "Group",
                participantCount = syncPlayState.syncPlayParticipantCount,
                isSynced = syncPlayState.isSyncPlaySynced,
                isPlaying = uiState.isPlaying,
                ignoreWait = syncPlayIgnoreWait,
                repeatMode = syncPlayState.syncPlayRepeatMode,
                shuffleMode = syncPlayState.syncPlayShuffleMode,
                onRepeatModeChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSyncPlayRepeatMode(it)) },
                onShuffleModeChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSyncPlayShuffleMode(it)) },
                onTogglePlayPause = { viewModel.syncPlay.togglePlayPause() },
                onStop = { viewModel.syncPlay.sendStop() },
                onLeave = {
                    viewModel.syncPlay.leaveGroup()
                    onSheetChange(PlayerSheet.None)
                },
                onIgnoreWaitChange = { viewModel.syncPlay.setIgnoreWait(it) },
                 onDismiss = dismissSheet,
             )
         }
        is PlayerSheet.Quality -> {
            QualityPickerSheet(
                currentQuality = uiState.uiPrefs.streamingQuality,
                adaptiveBitrateEnabled = uiState.uiPrefs.adaptiveBitrateEnabled,
                onToggleAdaptiveBitrate = { viewModel.onEvent(VideoPlayerUiEvent.SetAdaptiveBitrateEnabled(it)) },
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetStreamingQuality(it)) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackMode -> {
            PlaybackModeSheet(
                currentMode = uiState.uiPrefs.playbackMode,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackMode(it)) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SleepTimer -> {
            // Sleep-timer slice: collected inside the branch.
            val sleepTimerState by viewModel.sleepTimer.state.collectAsStateWithLifecycle()
            SleepTimerSheetBinder(
                isActive = sleepTimerState.sleepTimerActive,
                isEndOfEpisodeMode = sleepTimerState.sleepTimerEndOfEpisode,
                lastUsedDurationMs = sleepTimerState.sleepTimerLastUsedDurationMs,
                sleepTimerRemainingFlow = sleepTimerRemainingFlow,
                onSelectDuration = { viewModel.sleepTimer.startSleepTimer(it) },
                onSelectEndOfEpisode = { viewModel.sleepTimer.startSleepTimerEndOfEpisode() },
                onCancel = { viewModel.sleepTimer.cancelSleepTimer() },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.VideoFilter -> {
            VideoFilterSheet(
                currentEffects = uiState.videoFx.videoEffects,
                onEffectsChange = { viewModel.onEvent(VideoPlayerUiEvent.SetVideoEffects(it)) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Render -> {
            // mpv render surface. The session render state is the
            // sheet's source of truth; picks apply to the running engine
            // immediately and (with the toggle on) persist to the series row.
            val sessionRender = viewModel.sessionRender
            val global = viewModel.globalMpvConfig
            val effective = sessionRender.effectiveMpvConfig(global)
            val active = sessionRender.override
            RenderSheet(
                shaderPack = active?.shaderPack ?: global.shaderPack,
                toneMapping = active?.toneMapping ?: global.toneMapping,
                // The effective quality (the session lens may hold a pick the
                // DataStore round-trip hasn't mirrored into the aggregate yet).
                renderQuality = effective.renderQuality,
                hasStoredOverride = active != null,
                canSaveForSeries = uiState.media.seriesId != null || itemId != null,
                saveForSeries = saveRenderForSeries,
                onShaderPackSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetRenderShaderPack(it, saveRenderForSeries)) },
                onToneMappingSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetRenderToneMapping(it, saveRenderForSeries)) },
                onRenderQualitySelect = { viewModel.onEvent(VideoPlayerUiEvent.SetRenderQuality(it)) },
                onSaveForSeriesChange = { saveRenderForSeries = it },
                onInherit = {
                    viewModel.onEvent(VideoPlayerUiEvent.ClearRenderOverride)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
            )
        }
        PlayerSheet.None -> { }
    }
}

/**
 * Narrow binder that subscribes to [currentPositionFlow] only while the
  * chapter picker sheet is open, so the screen root and the sheet router
 * are not invalidated at 4 Hz on every position tick.
 */
@Composable
private fun ChapterPickerBinder(
    chapters: List<com.raulshma.jellyplay.core.model.ChapterInfo>,
    currentPositionFlow: StateFlow<Long>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentPositionMs by currentPositionFlow.collectAsStateWithLifecycle()
    ChapterPickerSheet(
        chapters = chapters,
        currentPositionMs = currentPositionMs,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/**
 * Narrow binder that subscribes to [sleepTimerRemainingFlow] only while the
 * sleep-timer sheet is open, so the sheet router and screen root are not
 * invalidated on every 5 s tick (or the 100 ms fade-out burst).
 */
@Composable
private fun SleepTimerSheetBinder(
    isActive: Boolean,
    isEndOfEpisodeMode: Boolean,
    lastUsedDurationMs: Long,
    sleepTimerRemainingFlow: StateFlow<Long>,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remainingMs by sleepTimerRemainingFlow.collectAsStateWithLifecycle()
    SleepTimerSheet(
        isActive = isActive,
        isEndOfEpisodeMode = isEndOfEpisodeMode,
        remainingMs = remainingMs,
        lastUsedDurationMs = lastUsedDurationMs,
        onSelectDuration = onSelectDuration,
        onSelectEndOfEpisode = onSelectEndOfEpisode,
        onCancel = onCancel,
        onDismiss = onDismiss,
    )
}
