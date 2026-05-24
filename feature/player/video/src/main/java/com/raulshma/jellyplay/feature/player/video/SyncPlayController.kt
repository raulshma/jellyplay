package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayChatMessage
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.UserPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

internal class SyncPlayController(
    private val syncPlayManager: SyncPlayManager,
    private val viewModel: ViewModel,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val getMediaEngine: () -> MediaEngine?,
    private val getCurrentItemId: () -> String? = { null },
    private val onLoadItem: ((String, Long) -> Unit)? = null,
    private val preferencesFlow: StateFlow<UserPreferences>,
) {
    private var commandJob: Job? = null
    private var currentPlaylistItemId: String? = null
    private var suppressNextPausedReadyReport = false
    private var _ignoreWait = MutableStateFlow(false)
    val ignoreWait: StateFlow<Boolean> = _ignoreWait

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _chatMessages = MutableStateFlow<List<SyncPlayChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<SyncPlayChatMessage>> = _chatMessages

    val isInSession: Boolean get() = syncPlayManager.isInSyncPlaySession

    private var lastCommand: SyncPlayCommand? = null
    private var scheduledCommandJob: Job? = null
    private var syncCorrectionEnabled = false
    private var syncCorrectionJob: Job? = null
    private var syncStateResetJob: Job? = null
    private var pendingItemLoad = false
    private var lastPlayCommandTimeMs = 0L

    private val minDelaySkipToSync = 400.0
    private val syncCorrectionInitialDelay = 500.0
    private val syncCorrectionInterval = 1000.0

    private val prefs: UserPreferences get() = preferencesFlow.value

    init {
        if (syncPlayManager.isInSyncPlaySession) {
            val group = syncPlayManager.currentGroup
            uiState.update { it.copy(
                isInSyncPlaySession = true,
                syncPlayGroupName = group?.groupName,
                syncPlayParticipantCount = group?.participantCount ?: 0,
                syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            ) }
        }
    }

    fun start() {
        startCommandListener()
    }

    fun joinGroup(groupId: String) {
        viewModel.viewModelScope.launch {
            syncPlayManager.joinGroup(groupId)
            val group = syncPlayManager.currentGroup
            uiState.update { it.copy(
                syncPlayGroupName = group?.groupName ?: groupId,
                isInSyncPlaySession = true,
                syncPlayParticipantCount = group?.participantCount ?: 0,
                syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
                syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
            ) }
            startCommandListener()
        }
    }

    fun leaveGroup() {
        viewModel.viewModelScope.launch {
            syncPlayManager.leaveGroup()
            uiState.update { it.copy(
                syncPlayGroupName = null,
                syncPlayParticipantCount = 0,
                isSyncPlaySynced = false,
                isInSyncPlaySession = false,
                isSyncPlaySyncing = false,
            ) }
            commandJob?.cancel()
            scheduledCommandJob?.cancel()
            syncCorrectionJob?.cancel()
            syncCorrectionEnabled = false
            suppressNextPausedReadyReport = false
            pendingItemLoad = false
            lastCommand = null
            lastPlayCommandTimeMs = 0L
        }
    }

    fun reset() {
        scheduledCommandJob?.cancel()
        syncCorrectionJob?.cancel()
        syncStateResetJob?.cancel()
        syncCorrectionEnabled = false
        suppressNextPausedReadyReport = false
        lastCommand = null
        pendingItemLoad = false
        lastPlayCommandTimeMs = 0L
        uiState.update { it.copy(isSyncPlaySyncing = false) }
    }

    fun reattachSession() {
        if (!syncPlayManager.isInSyncPlaySession) return
        val group = syncPlayManager.currentGroup
        uiState.update { it.copy(
            isInSyncPlaySession = true,
            syncPlayGroupName = group?.groupName,
            syncPlayParticipantCount = group?.participantCount ?: 0,
            syncPlayRepeatMode = group?.repeatMode ?: SyncPlayRepeatMode.REPEAT_NONE,
            syncPlayShuffleMode = group?.shuffleMode ?: SyncPlayShuffleMode.SORTED,
        ) }
    }

    fun setIgnoreWait(ignore: Boolean) {
        _ignoreWait.value = ignore
        viewModel.viewModelScope.launch {
            syncPlayManager.setIgnoreWait(ignore)
        }
    }

    fun sendStop() {
        viewModel.viewModelScope.launch { syncPlayManager.sendStop() }
    }

    fun sendNextItem(playlistItemId: String) {
        viewModel.viewModelScope.launch { syncPlayManager.sendNextItem(playlistItemId) }
    }

    fun sendPreviousItem(playlistItemId: String) {
        viewModel.viewModelScope.launch { syncPlayManager.sendPreviousItem(playlistItemId) }
    }

    fun sendChatMessage(text: String) {
        syncPlayManager.sendChatMessage(text)
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (!isInSession) return
        val engine = getMediaEngine() ?: return
        try {
            val posCheck = engine.currentPositionMs
        } catch (_: Exception) {
            return
        }
        val positionTicks = engine.currentPositionMs * 10_000
        val playlistItemId = currentPlaylistItemId ?: syncPlayManager.currentGroup?.playingPlaylistItemId?.takeIf { it.isNotBlank() }

        when (playbackState) {
            1 -> {
                Log.d(TAG, "STATE_IDLE: posTicks=$positionTicks, isPlaying=${engine.isPlaying.value}")
                stopSyncCorrection()
                viewModel.viewModelScope.launch {
                    syncPlayManager.reportBuffering(
                        positionTicks = positionTicks,
                        isPlaying = engine.isPlaying.value,
                        playlistItemId = playlistItemId,
                    )
                }
            }
            2 -> {
                val timeSincePlayCmd = System.currentTimeMillis() - lastPlayCommandTimeMs
                if (timeSincePlayCmd < 2000 && lastCommand is SyncPlayCommand.Play) {
                    Log.d(TAG, "STATE_BUFFERING: suppressed (Play command ${timeSincePlayCmd}ms ago)")
                    return
                }
                Log.d(TAG, "STATE_BUFFERING: posTicks=$positionTicks, isPlaying=${engine.isPlaying.value}")
                stopSyncCorrection()
                viewModel.viewModelScope.launch {
                    syncPlayManager.reportBuffering(
                        positionTicks = positionTicks,
                        isPlaying = engine.isPlaying.value,
                        playlistItemId = playlistItemId,
                    )
                }
            }
            3 -> {
                Log.d(TAG, "STATE_READY: posTicks=$positionTicks, isPlaying=${engine.isPlaying.value}")
                if (pendingItemLoad) {
                    pendingItemLoad = false
                    engine.pause()
                    suppressNextPausedReadyReport = true
                    Log.d(TAG, "STATE_READY (SyncPlay item load): pausing and reporting ready")
                    viewModel.viewModelScope.launch {
                        syncPlayManager.reportReady(
                            positionTicks = positionTicks,
                            isPlaying = false,
                            playlistItemId = playlistItemId,
                        )
                    }
                    uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                } else {
                    viewModel.viewModelScope.launch {
                        syncPlayManager.reportReady(
                            positionTicks = positionTicks,
                            isPlaying = engine.isPlaying.value,
                            playlistItemId = playlistItemId,
                        )
                    }
                }
            }
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isInSession) return
        if (getMediaEngine() == null) return
        if (isPlaying) {
            suppressNextPausedReadyReport = false
            return
        }
        if (suppressNextPausedReadyReport) {
            suppressNextPausedReadyReport = false
            return
        }
    }

    private fun startCommandListener() {
        if (commandJob?.isActive == true) return
        commandJob = viewModel.viewModelScope.launch {
            syncPlayManager.commands.collect { command ->
                processCommand(command)
            }
        }
    }

    private fun processCommand(command: SyncPlayCommand) {
        val engine = getMediaEngine()
        Log.d(TAG, "processCommand: $command")

        if (command is SyncPlayCommand.Play || command is SyncPlayCommand.Pause || command is SyncPlayCommand.Seek) {
            val cmdPlaylistItemId = when (command) {
                is SyncPlayCommand.Play -> command.playlistItemId
                is SyncPlayCommand.Pause -> command.playlistItemId
                is SyncPlayCommand.Seek -> command.playlistItemId
                else -> ""
            }
            val currentId = currentPlaylistItemId
            if (cmdPlaylistItemId.isNotBlank() && currentId != null && cmdPlaylistItemId != currentId) {
                Log.w(TAG, "Ignoring command for mismatched playlist item: cmd=$cmdPlaylistItemId current=$currentId")
                return
            }
        }

        when (command) {
            is SyncPlayCommand.Play -> {
                scheduledCommandJob?.cancel()
                lastCommand = command
                lastPlayCommandTimeMs = System.currentTimeMillis()
                currentPlaylistItemId = command.playlistItemId.takeIf { it.isNotBlank() }
                    ?: currentPlaylistItemId

                if (pendingItemLoad) {
                    Log.d(TAG, "Play command deferred: waiting for item to finish loading")
                    return
                }

                val posTicks = command.positionTicks
                val whenMs = command.whenMs
                val correctedPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                val durationMs = getMediaEngine()?.durationMs ?: 0L
                val correctedPosMs = if (durationMs > 0) {
                    (correctedPosTicks / 10_000).coerceIn(0, durationMs)
                } else {
                    (correctedPosTicks / 10_000).coerceAtLeast(0)
                }

                val waitMs = whenMs - syncPlayManager.remoteNow()
                Log.d(TAG, "Play command: posMs=${correctedPosMs}, waitMs=${waitMs}, remoteNow=${syncPlayManager.remoteNow()}, whenMs=$whenMs, rawPosTicks=$posTicks")

                uiState.update { it.copy(isSyncPlaySyncing = false, isSyncPlaySynced = false) }

                if (waitMs > 50) {
                    scheduledCommandJob = viewModel.viewModelScope.launch {
                        engine?.pause()
                        val preSeekTicks = syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                        val preSeekMs = if (durationMs > 0) (preSeekTicks / 10_000).coerceIn(0, durationMs) else (preSeekTicks / 10_000).coerceAtLeast(0)
                        engine?.seekTo(preSeekMs)

                        delay(waitMs)

                        val finalPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                        val finalPosMs = if (durationMs > 0) (finalPosTicks / 10_000).coerceIn(0, durationMs) else (finalPosTicks / 10_000).coerceAtLeast(0)
                        engine?.seekTo(finalPosMs)
                        engine?.play()
                        uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = true, isSyncPlaySyncing = false) }
                        enableSyncCorrection()
                        Log.d(TAG, "Scheduled Play executed at waitMs=$waitMs, posMs=$finalPosMs")
                    }
                } else {
                    if (engine?.isPlaying?.value == true && Math.abs(engine.currentPositionMs - correctedPosMs) < 500) {
                        Log.d(TAG, "Play command ignored: already playing and within 500ms")
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                        enableSyncCorrection()
                        return
                    }
                    engine?.seekTo(correctedPosMs)
                    engine?.play()
                    uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = true, isSyncPlaySyncing = false) }
                    enableSyncCorrection()
                }
            }
            is SyncPlayCommand.Pause -> {
                scheduledCommandJob?.cancel()
                lastCommand = command
                currentPlaylistItemId = command.playlistItemId.takeIf { it.isNotBlank() }
                    ?: currentPlaylistItemId
                stopSyncCorrection()

                val posTicks = command.positionTicks
                val whenMs = command.whenMs
                val correctedPosTicks = if (posTicks > 0) {
                    syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                } else {
                    val engineNow = getMediaEngine()?.currentPositionMs ?: 0L
                    engineNow * 10_000
                }
                val correctedPosMs = correctedPosTicks / 10_000

                val waitMs = whenMs - syncPlayManager.remoteNow()

                if (waitMs > 50) {
                    scheduledCommandJob = viewModel.viewModelScope.launch {
                        delay(waitMs)
                        engine?.seekTo(correctedPosMs)
                        engine?.pause()
                        uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = false) }
                        Log.d(TAG, "Scheduled Pause executed at waitMs=$waitMs, posMs=$correctedPosMs")
                    }
                } else {
                    engine?.seekTo(correctedPosMs)
                    engine?.pause()
                    uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = false) }
                }
            }
            is SyncPlayCommand.Seek -> {
                scheduledCommandJob?.cancel()
                lastCommand = command
                currentPlaylistItemId = command.playlistItemId.takeIf { it.isNotBlank() }
                    ?: currentPlaylistItemId

                val posTicks = command.positionTicks
                val correctedPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, command.whenMs)
                val correctedPosMs = correctedPosTicks / 10_000

                engine?.pause()
                engine?.seekTo(correctedPosMs)

                viewModel.viewModelScope.launch {
                    syncPlayManager.reportReady(
                        positionTicks = correctedPosTicks,
                        isPlaying = false,
                        playlistItemId = currentPlaylistItemId,
                    )
                }
                uiState.update { it.copy(isSyncPlaySynced = true) }
            }
            is SyncPlayCommand.Stop -> {
                scheduledCommandJob?.cancel()
                lastCommand = null
                stopSyncCorrection()
                engine?.pause()
                uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = false, isSyncPlaySyncing = false) }
            }
            is SyncPlayCommand.PlayQueueUpdate -> {
                if (command.playingPlaylistItemId.isNotBlank()) {
                    currentPlaylistItemId = command.playingPlaylistItemId
                }
                lastCommand = command
                val currentItemId = getCurrentItemId()

                when {
                    currentItemId == null || currentItemId != command.playingItemId -> {
                        pendingItemLoad = true
                        uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                        onLoadItem?.invoke(command.playingItemId, command.positionTicks)
                    }
                    engine == null -> {
                        pendingItemLoad = true
                        uiState.update { it.copy(isSyncPlaySyncing = true, isSyncPlaySynced = false) }
                    }
                    else -> {
                        val correctedPosTicks = syncPlayManager.estimateCurrentTicks(command.positionTicks, command.whenMs)
                        val correctedPosMs = correctedPosTicks / 10_000
                        val durationMs = engine.durationMs
                        if (durationMs > 0 && correctedPosMs > durationMs) {
                            Log.w(TAG, "PlayQueueUpdate: correctedPosMs=$correctedPosMs exceeds duration=$durationMs, clamping")
                        }
                        val safePosMs = if (durationMs > 0) correctedPosMs.coerceIn(0, durationMs) else correctedPosMs.coerceAtLeast(0)
                        val currentPosMs = engine.currentPositionMs
                        val diffMs = Math.abs(safePosMs - currentPosMs)
                        if (diffMs > 300) {
                            engine.seekTo(safePosMs)
                        }
                        if (command.isPlaying && !engine.isPlaying.value) {
                            engine.play()
                            enableSyncCorrection()
                        } else if (!command.isPlaying && engine.isPlaying.value) {
                            engine.pause()
                            stopSyncCorrection()
                        }
                    }
                }
                uiState.update { it.copy(isSyncPlaySynced = true) }
            }
            is SyncPlayCommand.StateUpdate -> {
                uiState.update { it.copy(isSyncPlaySynced = true) }
            }
            is SyncPlayCommand.GroupUpdate -> {
                if (command.groupName.isBlank() && command.participantCount == 0) {
                    uiState.update { it.copy(
                        syncPlayGroupName = null,
                        syncPlayParticipantCount = 0,
                        isSyncPlaySynced = false,
                        isInSyncPlaySession = false,
                        isSyncPlaySyncing = false,
                    ) }
                    stopSyncCorrection()
                } else {
                    uiState.update { it.copy(
                        syncPlayGroupName = command.groupName,
                        syncPlayParticipantCount = command.participantCount,
                        isInSyncPlaySession = true,
                    ) }
                }
            }
            is SyncPlayCommand.WaitForGroup -> {
                uiState.update { it.copy(isSyncPlaySynced = false, isSyncPlaySyncing = true) }
            }
            is SyncPlayCommand.Notification -> {
                _notifications.tryEmit(command.message)
            }
            is SyncPlayCommand.ChatMessage -> {
                val msg = SyncPlayChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = command.userId,
                    userName = command.userName,
                    text = command.text,
                )
                _chatMessages.update { (it + msg).takeLast(100) }
            }
        }

        val currentCachedGroup = syncPlayManager.currentGroup
        uiState.update { it.copy(
            syncPlayGroupName = currentCachedGroup?.groupName ?: it.syncPlayGroupName,
            syncPlayParticipantCount = currentCachedGroup?.participantCount ?: it.syncPlayParticipantCount,
            syncPlayRepeatMode = currentCachedGroup?.repeatMode ?: it.syncPlayRepeatMode,
            syncPlayShuffleMode = currentCachedGroup?.shuffleMode ?: it.syncPlayShuffleMode,
        ) }
    }

    private fun enableSyncCorrection() {
        if (syncCorrectionEnabled) return
        if (!prefs.syncPlaySyncCorrection) return
        syncCorrectionEnabled = true
        syncCorrectionJob?.cancel()
        syncCorrectionJob = viewModel.viewModelScope.launch {
            delay(syncCorrectionInitialDelay.toLong())
            if (!syncCorrectionEnabled) return@launch
            while (syncCorrectionEnabled) {
                delay(syncCorrectionInterval.toLong())
                performSyncCorrection()
            }
        }
        Log.d(TAG, "Sync correction enabled")
    }

    private fun stopSyncCorrection() {
        syncCorrectionEnabled = false
        syncCorrectionJob?.cancel()
        val engine = getMediaEngine()
        if (engine != null && engine.playbackSpeed != 1.0f) {
            engine.setPlaybackSpeed(1.0f)
        }
        Log.d(TAG, "Sync correction stopped")
    }

    private fun performSyncCorrection() {
        if (!syncCorrectionEnabled) return
        if (!prefs.syncPlaySyncCorrection) {
            stopSyncCorrection()
            return
        }
        if (!isInSession) return
        val engine = getMediaEngine() ?: return
        if (!engine.isPlaying.value) return

        val minDelaySpeedToSync = prefs.syncPlaySpeedToSyncMinDelayMs.toDouble()
        val maxDelaySpeedToSync = prefs.syncPlaySpeedToSyncMaxDelayMs.toDouble()
        val speedToSyncDuration = prefs.syncPlaySpeedToSyncDurationMs.toDouble()
        val useSpeedToSync = prefs.syncPlaySpeedToSyncEnabled

        val cmd = lastCommand ?: return
        val currentPosMs = engine.currentPositionMs
        val currentPosTicks = currentPosMs * 10_000

        val serverPositionTicks: Long = when (cmd) {
            is SyncPlayCommand.Play -> {
                syncPlayManager.estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
            }
            is SyncPlayCommand.PlayQueueUpdate -> {
                if (cmd.isPlaying) {
                    syncPlayManager.estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
                } else return
            }
            else -> return
        }

        val diffTicks = serverPositionTicks - currentPosTicks
        val diffMs = diffTicks / 10_000.0
        val absDiffMs = Math.abs(diffMs)

        if (absDiffMs < minDelaySpeedToSync) return

        if (absDiffMs >= minDelaySkipToSync) {
            val seekMs = serverPositionTicks / 10_000
            engine.seekTo(seekMs)
            uiState.update { it.copy(isSyncPlaySyncing = true) }
            syncStateResetJob?.cancel()
            syncStateResetJob = viewModel.viewModelScope.launch {
                delay(maxDelaySpeedToSync.toLong() / 2)
                uiState.update { it.copy(isSyncPlaySyncing = false) }
            }
            Log.d(TAG, "SkipToSync: diff=${diffMs}ms, seeking to ${seekMs}ms")
        } else if (useSpeedToSync && absDiffMs < maxDelaySpeedToSync) {
            val speed = (1.0 + diffMs / speedToSyncDuration).toFloat().coerceIn(0.8f, 1.5f)
            engine.setPlaybackSpeed(speed)
            uiState.update { it.copy(isSyncPlaySyncing = true) }
            syncStateResetJob?.cancel()
            syncStateResetJob = viewModel.viewModelScope.launch {
                delay(speedToSyncDuration.toLong())
                if (syncCorrectionEnabled) {
                    engine.setPlaybackSpeed(1.0f)
                    uiState.update { it.copy(isSyncPlaySyncing = false) }
                }
            }
            Log.d(TAG, "SpeedToSync: diff=${diffMs}ms, speed=$speed")
        }
    }

    companion object {
        private const val TAG = "SyncPlayController"
    }
}
