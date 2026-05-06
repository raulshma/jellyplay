package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import androidx.media3.exoplayer.ExoPlayer

internal class SyncPlayController(
    private val syncPlayManager: SyncPlayManager,
    private val viewModel: ViewModel,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val getExoPlayer: () -> ExoPlayer?,
    private val getPlayerEngine: () -> PlayerEngine?,
) {
    private var commandJob: Job? = null

    val isInSession: Boolean get() = syncPlayManager.isInSyncPlaySession

    fun joinGroup(groupId: String) {
        viewModel.viewModelScope.launch {
            syncPlayManager.joinGroup(groupId)
            uiState.update { it.copy(syncPlayGroupName = groupId) }
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
            ) }
            commandJob?.cancel()
        }
    }

    fun reset() {
        commandJob?.cancel()
        syncPlayManager.reset()
    }

    private fun startCommandListener() {
        commandJob?.cancel()
        commandJob = viewModel.viewModelScope.launch {
            syncPlayManager.commands.collect { command ->
                when (command) {
                    is SyncPlayCommand.Play -> {
                        val posMs = command.positionTicks / 10_000
                        getPlayerEngine()?.let { engine ->
                            engine.seekTo(posMs)
                            engine.play()
                        } ?: getExoPlayer()?.let { player ->
                            player.seekTo(posMs)
                            player.play()
                        }
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                    }
                    is SyncPlayCommand.Pause -> {
                        val posMs = command.positionTicks / 10_000
                        getPlayerEngine()?.let { engine ->
                            engine.seekTo(posMs)
                            engine.pause()
                        } ?: getExoPlayer()?.let { player ->
                            player.seekTo(posMs)
                            player.pause()
                        }
                        uiState.update { it.copy(isSyncPlaySynced = true) }
                    }
                    is SyncPlayCommand.Seek -> {
                        val posMs = command.positionTicks / 10_000
                        getPlayerEngine()?.seekTo(posMs) ?: getExoPlayer()?.seekTo(posMs)
                    }
                    is SyncPlayCommand.PrepareSession -> {
                        val posMs = command.positionTicks / 10_000
                        if (!command.isPlaying) {
                            getPlayerEngine()?.let { engine ->
                                engine.seekTo(posMs)
                                engine.pause()
                            } ?: getExoPlayer()?.let { player ->
                                player.seekTo(posMs)
                                player.pause()
                            }
                        }
                        viewModel.viewModelScope.launch { syncPlayManager.reportReady() }
                        uiState.update { it.copy(isSyncPlaySynced = false) }
                    }
                    is SyncPlayCommand.GroupUpdate -> {
                        uiState.update { it.copy(
                            syncPlayGroupName = command.groupName,
                            syncPlayParticipantCount = command.participantCount,
                        ) }
                    }
                    is SyncPlayCommand.WaitForGroup -> {
                        uiState.update { it.copy(isSyncPlaySynced = false) }
                    }
                }
            }
        }
    }
}
