package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayChatMessage
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
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import java.util.concurrent.ConcurrentHashMap

internal class SyncPlayController(
    private val syncPlayManager: SyncPlayManager,
    private val viewModel: ViewModel,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val getPlayerEngine: () -> PlayerEngine?,
    private val getCurrentItemId: () -> String? = { null },
    private val onLoadItem: ((String, Long) -> Unit)? = null,
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

    private val minDelaySpeedToSync = 60.0
    private val maxDelaySpeedToSync = 3000.0
    private val speedToSyncDuration = 1000.0
    private val minDelaySkipToSync = 400.0

    init {
        if (syncPlayManager.isInSyncPlaySession) {
            uiState.update { it.copy(
                isInSyncPlaySession = true,
                syncPlayGroupName = syncPlayManager.currentGroup?.groupName,
                syncPlayParticipantCount = syncPlayManager.currentGroup?.participantCount ?: 0,
            ) }
            startCommandListener()
        }
    }

    fun joinGroup(groupId: String) {
        viewModel.viewModelScope.launch {
            syncPlayManager.joinGroup(groupId)
            uiState.update { it.copy(
                syncPlayGroupName = syncPlayManager.currentGroup?.groupName ?: groupId,
                isInSyncPlaySession = true,
                syncPlayParticipantCount = syncPlayManager.currentGroup?.participantCount ?: 0,
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
            ) }
            commandJob?.cancel()
            scheduledCommandJob?.cancel()
            syncCorrectionJob?.cancel()
            syncCorrectionEnabled = false
            suppressNextPausedReadyReport = false
            lastCommand = null
        }
    }

    fun reset() {
        commandJob?.cancel()
        scheduledCommandJob?.cancel()
        syncCorrectionJob?.cancel()
        syncCorrectionEnabled = false
        suppressNextPausedReadyReport = false
        lastCommand = null
    }

    fun reattachSession() {
        if (!syncPlayManager.isInSyncPlaySession) return
        uiState.update { it.copy(
            isInSyncPlaySession = true,
            syncPlayGroupName = syncPlayManager.currentGroup?.groupName,
            syncPlayParticipantCount = syncPlayManager.currentGroup?.participantCount ?: 0,
        ) }
        startCommandListener()
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
        val engine = getPlayerEngine() ?: return
        val positionTicks = engine.currentPositionMs * 10_000

        when (playbackState) {
            2 -> {
                stopSyncCorrection()
                viewModel.viewModelScope.launch {
                    syncPlayManager.reportBuffering(
                        positionTicks = positionTicks,
                        isPlaying = engine.isPlaying,
                        playlistItemId = currentPlaylistItemId ?: syncPlayManager.currentGroup?.playingPlaylistItemId?.takeIf { it.isNotBlank() },
                    )
                }
            }
            3 -> {
                viewModel.viewModelScope.launch {
                    if (syncPlayManager.currentGroup?.isPlaying == true && engine.isPlaying) {
                        suppressNextPausedReadyReport = true
                        engine.pause()
                    }

                    syncPlayManager.reportReady(
                        positionTicks = positionTicks,
                        isPlaying = false,
                        playlistItemId = currentPlaylistItemId ?: syncPlayManager.currentGroup?.playingPlaylistItemId?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isInSession) return
        if (getPlayerEngine() == null) return
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
        commandJob?.cancel()
        commandJob = viewModel.viewModelScope.launch {
            syncPlayManager.commands.collect { command ->
                processCommand(command)
            }
        }
    }

    private fun processCommand(command: SyncPlayCommand) {
        val engine = getPlayerEngine()
        when (command) {
            is SyncPlayCommand.Play -> {
                scheduledCommandJob?.cancel()
                lastCommand = command
                currentPlaylistItemId = command.playlistItemId.takeIf { it.isNotBlank() }
                    ?: currentPlaylistItemId

                val posTicks = command.positionTicks
                val whenMs = command.whenMs
                val correctedPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                val correctedPosMs = correctedPosTicks / 10_000

                val waitMs = whenMs - syncPlayManager.estimateCurrentTicks(0, whenMs) / 10_000

                if (waitMs > 50) {
                    scheduledCommandJob = viewModel.viewModelScope.launch {
                        if (engine?.isPlaying != true) {
                            engine?.seekTo(correctedPosMs)
                        }
                        delay(waitMs)
                        val finalPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, whenMs)
                        engine?.seekTo(finalPosTicks / 10_000)
                        engine?.play()
                        uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = true) }
                        enableSyncCorrection()
                    }
                } else {
                    if (engine?.isPlaying == true && Math.abs(engine.currentPositionMs - correctedPosMs) < 500) {
                        return
                    }
                    engine?.seekTo(correctedPosMs)
                    engine?.play()
                    uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = true) }
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
                val correctedPosTicks = if (posTicks > 0) {
                    syncPlayManager.estimateCurrentTicks(posTicks, command.whenMs)
                } else {
                    val engineNow = getPlayerEngine()?.currentPositionMs ?: 0L
                    engineNow * 10_000
                }
                val correctedPosMs = correctedPosTicks / 10_000

                engine?.seekTo(correctedPosMs)
                engine?.pause()
                uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = false) }
            }
            is SyncPlayCommand.Seek -> {
                scheduledCommandJob?.cancel()
                lastCommand = command
                currentPlaylistItemId = command.playlistItemId.takeIf { it.isNotBlank() }
                    ?: currentPlaylistItemId

                val posTicks = command.positionTicks
                val correctedPosTicks = syncPlayManager.estimateCurrentTicks(posTicks, command.whenMs)
                val correctedPosMs = correctedPosTicks / 10_000

                engine?.seekTo(correctedPosMs)
                engine?.play()

                viewModel.viewModelScope.launch {
                    delay(200)
                    engine?.pause()
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
                uiState.update { it.copy(isSyncPlaySynced = true, isPlaying = false) }
            }
            is SyncPlayCommand.PlayQueueUpdate -> {
                currentPlaylistItemId = command.playingPlaylistItemId
                lastCommand = command
                val currentItemId = getCurrentItemId()
                val posMs = command.positionTicks / 10_000
                val correctedPosTicks = syncPlayManager.estimateCurrentTicks(command.positionTicks, command.whenMs)
                val correctedPosMs = correctedPosTicks / 10_000

                if (engine == null || currentItemId == null || currentItemId != command.playingItemId) {
                    onLoadItem?.invoke(command.playingItemId, correctedPosTicks)
                } else {
                    engine.seekTo(correctedPosMs)
                    if (command.isPlaying) {
                        engine.play()
                        enableSyncCorrection()
                    } else {
                        engine.pause()
                        stopSyncCorrection()
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
                uiState.update { it.copy(isSyncPlaySynced = false) }
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
        ) }
    }

    private fun enableSyncCorrection() {
        if (syncCorrectionEnabled) return
        syncCorrectionEnabled = true
        syncCorrectionJob?.cancel()
        syncCorrectionJob = viewModel.viewModelScope.launch {
            delay((maxDelaySpeedToSync / 2).toLong())
            if (!syncCorrectionEnabled) return@launch
            while (syncCorrectionEnabled) {
                delay(2000)
                performSyncCorrection()
            }
        }
    }

    private fun stopSyncCorrection() {
        syncCorrectionEnabled = false
        syncCorrectionJob?.cancel()
        val engine = getPlayerEngine()
        if (engine != null && engine.playbackSpeed != 1.0f) {
            engine.setPlaybackSpeed(1.0f)
        }
    }

    private fun performSyncCorrection() {
        if (!syncCorrectionEnabled) return
        if (!isInSession) return
        val engine = getPlayerEngine() ?: return
        if (!engine.isPlaying) return

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
            Log.d(TAG, "SkipToSync: diff=${diffMs}ms, seeking to ${seekMs}ms")
        } else if (absDiffMs < maxDelaySpeedToSync) {
            val speed = (1.0 + diffMs / speedToSyncDuration).toFloat().coerceIn(0.8f, 1.5f)
            engine.setPlaybackSpeed(speed)
            viewModel.viewModelScope.launch {
                delay(speedToSyncDuration.toLong())
                if (syncCorrectionEnabled) {
                    engine.setPlaybackSpeed(1.0f)
                }
            }
            Log.d(TAG, "SpeedToSync: diff=${diffMs}ms, speed=$speed")
        }
    }

    companion object {
        private const val TAG = "SyncPlayController"
    }
}
