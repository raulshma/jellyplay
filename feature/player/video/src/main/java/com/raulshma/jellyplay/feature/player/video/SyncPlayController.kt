package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayChatMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine

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
    private var _ignoreWait = MutableStateFlow(false)
    val ignoreWait: kotlinx.coroutines.flow.StateFlow<Boolean> = _ignoreWait

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _chatMessages = kotlinx.coroutines.flow.MutableStateFlow<List<SyncPlayChatMessage>>(emptyList())
    val chatMessages: kotlinx.coroutines.flow.StateFlow<List<SyncPlayChatMessage>> = _chatMessages

    val isInSession: Boolean get() = syncPlayManager.isInSyncPlaySession

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
        }
    }

    fun reset() {
        commandJob?.cancel()
        syncPlayManager.reset()
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
        val isPlaying = engine.isPlaying

        when (playbackState) {
            2 -> {
                viewModel.viewModelScope.launch {
                    syncPlayManager.reportBuffering(
                        positionTicks = positionTicks,
                        isPlaying = isPlaying,
                        playlistItemId = currentPlaylistItemId,
                    )
                }
            }
            3 -> {
                viewModel.viewModelScope.launch {
                    syncPlayManager.reportReady(
                        positionTicks = positionTicks,
                        isPlaying = isPlaying,
                        playlistItemId = currentPlaylistItemId,
                    )
                }
            }
        }
    }

    private fun startCommandListener() {
        commandJob?.cancel()
        commandJob = viewModel.viewModelScope.launch {
            syncPlayManager.commands.collect { command ->
                when (command) {
                    is SyncPlayCommand.Play -> {
                        val posMs = command.positionTicks / 10_000
                        val engine = getPlayerEngine()
                        engine?.seekTo(posMs)
                        engine?.play()
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                    }
                    is SyncPlayCommand.Pause -> {
                        val posMs = command.positionTicks / 10_000
                        val engine = getPlayerEngine()
                        engine?.seekTo(posMs)
                        engine?.pause()
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                    }
                    is SyncPlayCommand.Seek -> {
                        val posMs = command.positionTicks / 10_000
                        getPlayerEngine()?.seekTo(posMs)
                    }
                    is SyncPlayCommand.Stop -> {
                        getPlayerEngine()?.pause()
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                    }
                    is SyncPlayCommand.PlayQueueUpdate -> {
                        currentPlaylistItemId = command.playingItemId
                        val engine = getPlayerEngine()
                        val currentItemId = getCurrentItemId()
                        if (engine == null || currentItemId == null || currentItemId != command.playingItemId) {
                            onLoadItem?.invoke(command.playingItemId, command.positionTicks)
                        } else {
                            val posMs = command.positionTicks / 10_000
                            engine.seekTo(posMs)
                            if (command.isPlaying) {
                                engine.play()
                            } else {
                                engine.pause()
                            }
                        }
                        uiState.update { it.copy(
                            isSyncPlaySynced = true,
                            syncPlayGroupName = syncPlayManager.currentGroup?.groupName,
                        ) }
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
                        } else {
                            uiState.update { it.copy(
                                syncPlayGroupName = command.groupName,
                                syncPlayParticipantCount = command.participantCount,
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
                        _chatMessages.update { it + msg }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "SyncPlayController"
    }
}
