package com.raulshma.jellyplay.feature.player.live

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState

@Immutable
data class LiveTvPlayerUiState(
    val isLoadingChannels: Boolean = true,
    val channels: List<LiveTvChannel> = emptyList(),
    val currentIndex: Int = 0,
    val currentChannel: LiveTvChannel? = null,
    val currentProgram: LiveTvProgram? = null,
    val nextProgram: LiveTvProgram? = null,
    val isBuffering: Boolean = true,
    val isPlaying: Boolean = false,
    val isAtLiveEdge: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val engineState: LiveEngineState = LiveEngineState.IDLE,
    val errorMessage: String? = null,
    val isSwitchingChannel: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val isMuted: Boolean = false,
) {
    val hasNext: Boolean get() = currentIndex < channels.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
}
