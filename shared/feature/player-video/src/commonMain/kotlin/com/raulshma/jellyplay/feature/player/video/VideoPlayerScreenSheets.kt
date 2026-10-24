package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.feature.player.video.components.AVSyncSheet
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoQueueSheet
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackModeSheet
import com.raulshma.jellyplay.feature.player.video.components.QualityPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.RememberPreferenceToggle
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerSheet
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleHubTab
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayPlayerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoFilterSheet
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_audio_description
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_audio_language
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_remember_version
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_version

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
//
// The sheet-module migration is staged (the Detail section precedent): the
// migrated branches (wave 1: Hdr, Render) delegate to their
// [PlayerSheetModule]s in PlayerSheetModule.kt — the registry
// ([PlayerSheetRegistry]) owns the sheet enumeration the former manual
// ALL_SHEETS list carried — and the remaining branches stay hand-written
// here, migrating as touched.

/**
 * Input bundle for [PlayerSheetRouter] (the
 * [com.raulshma.jellyplay.feature.player.video.components.PlayerEffectsControls]
 * idiom): the 16 non-state values the router reads — the sheet-change /
 * dismiss funnels, the sheet-local flow subscriptions, the seek handoff, the
 * SubtitleHub per-open intents (reset-first flag + landing tab) and the
 * Render sheet's router-scoped "save for this series" toggle — riding one
 * `@Immutable` carrier remembered on its value fields at the call wall; its
 * lambdas capture only stable handles (the screen's rememberSaveable
 * delegates, the document pickers), so the bundle instance stays equal across
 * recompositions until an intent value actually flips.
 */
@Immutable
internal data class SheetRouterInputs(
    val onSheetChange: (PlayerSheet) -> Unit,
    val dismissSheet: () -> Unit,
    val currentPositionFlow: StateFlow<Long>,
    val sleepTimerRemainingFlow: StateFlow<Long>,
    val doSeekTo: (Long) -> Unit,
    val itemId: String,
    val syncPlayIgnoreWait: Boolean,
    val onLoadLocalSubtitle: () -> Unit,
    val onPickFont: () -> Unit,
    val onOpenSubtitleTester: () -> Unit,
    val onOpenSubtitleDelayOverlay: () -> Unit,
    /** The pending reset-first intent for this SubtitleHub open — see [onSubtitleHubResetConsumed]. */
    val subtitleHubResetFirst: Boolean,
    /** Clears the consumed [subtitleHubResetFirst] flag so it stays single-shot. */
    val onSubtitleHubResetConsumed: () -> Unit,
    /** The tab this SubtitleHub open lands on (set by the entry that opened it). */
    val subtitleHubInitialTab: SubtitleHubTab,
    /**
     * The Render sheet's "save for this series" toggle — the sheet's
     * router-scoped sheet-local state (defaults off = session-only edits),
     * hoisted beside the bundle so it survives sheet close/reopen. Written
     * through [onSaveRenderForSeriesChange]; the bundle rebuilds on a flip
     * (the value is a remember key at the call wall), the same contract as
     * [subtitleHubResetFirst].
     */
    val saveRenderForSeries: Boolean,
    /** Writes [saveRenderForSeries] (the Render sheet's toggle callback). */
    val onSaveRenderForSeriesChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheetRouter(
    currentSheet: PlayerSheet,
    uiState: VideoPlayerUiState,
    inputs: SheetRouterInputs,
    viewModel: VideoPlayerViewModel,
) {
    // the sheets region's session slice: every branch reads the session
    // through it (the GLOSSARY "Session slice" seam) — no controller reaches.
    val sheets = viewModel.sessionSlices.sheets
    // The module slot's region params — only the migrated branches read it
    // (wave 1: Hdr, Render; wave 2: SubtitleHub); their slice collections
    // stay inside each module's Content exactly where the branches collected
    // them.
    val moduleScope = PlayerSheetScope(uiState, inputs, viewModel, sheets)
    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(it)) },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.Audio -> {
            // Track slice: collected inside the branch — only this
            // picker consumes it while the sheet is open.
            val trackState by sheets.trackState.collectAsStateWithLifecycle()
            TrackPickerSheet(
                title = stringResource(Res.string.player_audio),
                tracks = trackState.audioTracks,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SelectAudioTrack(it)) },
                onReset = if (trackState.hasAudioOverride) { { viewModel.onEvent(VideoPlayerUiEvent.ResetAudioTrack) } } else null,
                onDismiss = inputs.dismissSheet,
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
                        // Per-series audio-description pin: on = prefer AD
                        // narration for every episode; off = follow the global
                        // setting again. Independent of the language row above.
                        RememberPreferenceToggle(
                            label = stringResource(Res.string.player_video_remember_audio_description),
                            checked = trackState.hasSeriesAudioDescriptionPref,
                            onToggle = { remember ->
                                viewModel.onEvent(
                                    VideoPlayerUiEvent.SetSeriesAudioDescriptionPreference(remember)
                                )
                            },
                        )
                    }
                } else null,
            )
        }
        is PlayerSheet.SubtitleHub ->
            // Migrated (the sheet-module wave 2): the branch body — the single
            // hub-open load trigger, the four sheet-local slice collections
            // and the per-tab wiring — lives in SubtitleHubSheetModule.Content;
            // the hub's open intent (reset-first flag + landing tab) rides the
            // inputs bundle like the Render sheet's toggle.
            SubtitleHubSheetModule.Content(moduleScope)
        is PlayerSheet.Chapter -> {
            // Collect position only while the chapter sheet is open, so
            // the router itself stays a low-frequency scope when no sheet (or
            // a non-chapter sheet) is shown.
            val chapterImages = remember(uiState.chapters) {
                viewModel.chapterImageUrls(uiState.chapters)
            }
            ChapterPickerBinder(
                chapters = uiState.chapters,
                chapterImages = chapterImages,
                currentPositionFlow = inputs.currentPositionFlow,
                onSelect = { positionTicks ->
                    inputs.doSeekTo(positionTicks / 10_000)
                    inputs.onSheetChange(PlayerSheet.None)
                },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.PlaybackInfo -> {
            // Track + effects slices: collected inside the branch.
            val trackState by sheets.trackState.collectAsStateWithLifecycle()
            val effectsState by sheets.effectsState.collectAsStateWithLifecycle()
            PlayerModalBottomSheet(
                onDismissRequest = inputs.dismissSheet,
                sheetState = rememberModalBottomSheetState(),
            ) {
                PlaybackInfoOverlay(
                    mediaSource = uiState.media.currentMediaSource,
                    mediaStreams = uiState.media.mediaStreams,
                    playMethod = uiState.media.playMethod,
                    isOfflineSource = uiState.media.showDownloadedBadge,
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
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.AVSync -> {
            // Effects slice: collected inside the branch.
            val effectsState by sheets.effectsState.collectAsStateWithLifecycle()
            AVSyncSheet(
                currentAudioDelayMs = effectsState.audioDelayMs,
                onAudioDelayChange = { sheets.setAudioDelayLive(it) },
                onAudioDelayChangeFinished = { sheets.setAudioDelay(it) },
                onDismiss = inputs.dismissSheet,
                audioDelaySupported = uiState.engineCapabilities.supportsAudioDelay,
            )
        }
        is PlayerSheet.Decoder -> {
            // Effects slice: collected inside the branch.
            val effectsState by sheets.effectsState.collectAsStateWithLifecycle()
            DecoderPickerSheet(
                currentMode = effectsState.decoderMode,
                onSelect = { sheets.setDecoderMode(it) },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.Episodes -> {
            EpisodePickerSheet(
                seasons = uiState.episodes.seriesSeasons,
                episodes = uiState.episodes.seasonEpisodes,
                currentSeasonId = uiState.episodes.currentSeasonId,
                currentEpisodeId = inputs.itemId,
                isLoading = uiState.episodes.isLoadingEpisodes,
                onSeasonSelect = { viewModel.onEvent(VideoPlayerUiEvent.LoadSeasonEpisodes(it)) },
                onEpisodeSelect = { episode ->
                    viewModel.onEvent(VideoPlayerUiEvent.PlayEpisode(episode.id, episode.playbackPositionTicks ?: 0L))
                    inputs.onSheetChange(PlayerSheet.None)
                },
                onDismiss = inputs.dismissSheet,
                getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            )
        }
        is PlayerSheet.Queue -> {
            // Queue slice: collected inside the branch so the router stays
            // low-frequency when no sheet (or another sheet) is shown.
            val videoQueueItems by sheets.queueItems.collectAsStateWithLifecycle()
            VideoQueueSheet(
                queue = videoQueueItems,
                onSelect = { index ->
                    viewModel.onEvent(VideoPlayerUiEvent.PlayQueueItem(index))
                    inputs.onSheetChange(PlayerSheet.None)
                },
                onRemove = { viewModel.onEvent(VideoPlayerUiEvent.RemoveQueueItem(it)) },
                onMove = { from, to -> viewModel.onEvent(VideoPlayerUiEvent.MoveQueueItem(from, to)) },
                onClear = { viewModel.onEvent(VideoPlayerUiEvent.ClearQueue) },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.SyncPlay -> {
            // SyncPlay group-display slice: collected inside the branch.
            val syncPlayState by sheets.syncPlayState.collectAsStateWithLifecycle()
            SyncPlayPlayerSheet(
                groupName = syncPlayState.syncPlayGroupName ?: "Group",
                participantCount = syncPlayState.syncPlayParticipantCount,
                isSynced = syncPlayState.isSyncPlaySynced,
                isPlaying = uiState.isPlaying,
                ignoreWait = inputs.syncPlayIgnoreWait,
                repeatMode = syncPlayState.syncPlayRepeatMode,
                shuffleMode = syncPlayState.syncPlayShuffleMode,
                onRepeatModeChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSyncPlayRepeatMode(it)) },
                onShuffleModeChange = { viewModel.onEvent(VideoPlayerUiEvent.SetSyncPlayShuffleMode(it)) },
                onTogglePlayPause = { sheets.toggleSyncPlayTransport() },
                onStop = { sheets.sendSyncPlayStop() },
                onLeave = {
                    sheets.leaveSyncPlayGroup()
                    inputs.onSheetChange(PlayerSheet.None)
                },
                onIgnoreWaitChange = { sheets.setSyncPlayIgnoreWait(it) },
                 onDismiss = inputs.dismissSheet,
             )
         }
        is PlayerSheet.Version -> {
            // Version rows mirror the session state (collected via the media
            // slice): one row per media source, the current one marked.
            val trackState by sheets.trackState.collectAsStateWithLifecycle()
            TrackPickerSheet(
                title = stringResource(Res.string.player_video_version),
                tracks = buildVersionTrackOptions(
                    mediaSources = uiState.media.mediaSources,
                    currentSourceId = uiState.media.currentMediaSource?.id,
                ),
                onSelect = { option ->
                    option.id?.let { viewModel.onEvent(VideoPlayerUiEvent.SelectMediaSource(it)) }
                },
                onDismiss = inputs.dismissSheet,
                footer = {
                    // Per-item/series version memory (the audio sheet's
                    // "remember language" pattern): on = the CURRENT version
                    // is pinned for this item (movie) or series (episode);
                    // off = forget both scopes.
                    RememberPreferenceToggle(
                        label = stringResource(Res.string.player_video_remember_version),
                        checked = trackState.hasPreferredMediaSource,
                        onToggle = { remember ->
                            viewModel.onEvent(VideoPlayerUiEvent.SetPreferredMediaVersion(remember))
                        },
                    )
                },
            )
        }
        is PlayerSheet.Quality -> {
            QualityPickerSheet(
                currentQuality = uiState.uiPrefs.streamingQuality,
                adaptiveBitrateEnabled = uiState.uiPrefs.adaptiveBitrateEnabled,
                onToggleAdaptiveBitrate = { viewModel.onEvent(VideoPlayerUiEvent.SetAdaptiveBitrateEnabled(it)) },
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetStreamingQuality(it)) },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.PlaybackMode -> {
            // The combined options sheet stays open across picks: engine +
            // delivery + quality in one panel. The engine section hides for
            // cast/SyncPlay; quality hides for offline sessions too.
            val engine by sheets.engineFlow.collectAsStateWithLifecycle()
            val castConnected by viewModel.playbackSession.cast.isConnectedFlow.collectAsStateWithLifecycle()
            val engineName = engine?.displayName
            PlaybackModeSheet(
                currentMode = uiState.uiPrefs.playbackMode,
                currentPlayMethod = uiState.media.playMethod,
                onSelect = { viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackMode(it)) },
                onDismiss = inputs.dismissSheet,
                currentQuality = uiState.uiPrefs.streamingQuality,
                adaptiveBitrateEnabled = uiState.uiPrefs.adaptiveBitrateEnabled,
                onQualitySelect = { viewModel.onEvent(VideoPlayerUiEvent.SetStreamingQuality(it)) },
                onToggleAdaptiveBitrate = { viewModel.onEvent(VideoPlayerUiEvent.SetAdaptiveBitrateEnabled(it)) },
                // Forced direct play has no transcode quality to pick either —
                // the section disables until the delivery mode changes.
                qualityAvailable = uiState.uiPrefs.playbackMode != PlaybackMode.FORCE_DIRECT_PLAY &&
                    !uiState.media.showDownloadedBadge && !castConnected && !uiState.isInSyncPlaySession,
                currentEngineName = engineName,
                availablePlayers = if (castConnected || uiState.isInSyncPlaySession) {
                    emptyList()
                } else {
                    viewModel.playbackSession.availablePlayerTypes.filter { it.displayName != engineName }
                },
                onPlayerSelect = { viewModel.onEvent(VideoPlayerUiEvent.SwitchPlayerEngine(it)) },
            )
        }
        is PlayerSheet.SleepTimer -> {
            // Sleep-timer slice: collected inside the branch.
            val sleepTimerState by sheets.sleepTimerState.collectAsStateWithLifecycle()
            SleepTimerSheetBinder(
                isActive = sleepTimerState.sleepTimerActive,
                isEndOfEpisodeMode = sleepTimerState.sleepTimerEndOfEpisode,
                lastUsedDurationMs = sleepTimerState.sleepTimerLastUsedDurationMs,
                sleepTimerRemainingFlow = inputs.sleepTimerRemainingFlow,
                onSelectDuration = { sheets.startSleepTimer(it) },
                onSelectEndOfEpisode = { sheets.startSleepTimerEndOfEpisode() },
                onCancel = { sheets.cancelSleepTimer() },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.VideoFilter -> {
            VideoFilterSheet(
                currentEffects = uiState.videoFx.videoEffects,
                onEffectsChange = { viewModel.onEvent(VideoPlayerUiEvent.SetVideoEffects(it)) },
                onEffectsSettled = { viewModel.onEvent(VideoPlayerUiEvent.SetVideoEffectsSettled(it)) },
                onDismiss = inputs.dismissSheet,
            )
        }
        is PlayerSheet.Hdr ->
            // Migrated (the sheet-module pilot): the branch body — the
            // sheet-local optimistic mirrors and the stats collection
            // included — lives in HdrSheetModule.Content.
            HdrSheetModule.Content(moduleScope)
        is PlayerSheet.Render ->
            // Migrated (the sheet-module pilot): the branch body lives in
            // RenderSheetModule.Content; the "save for this series" toggle
            // rides the inputs bundle.
            RenderSheetModule.Content(moduleScope)
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
    chapterImages: List<String?>,
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
        chapterImages = chapterImages,
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
