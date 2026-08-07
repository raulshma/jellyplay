package com.raulshma.jellyplay.feature.player.live

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod

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
    /** Full technical detail for the last error (stacktrace-grade), shown in
     *  an expandable section of the error overlay. */
    val errorDetail: String? = null,
    val isSwitchingChannel: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val lastChannelId: String? = null,
    val isMuted: Boolean = false,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    /** Delivery method in use for the current stream (Direct Stream /
     *  Transcode), surfaced as a badge in the player chrome. Null until the
     *  first stream resolves. */
    val playMethod: LivePlayMethod? = null,
) {
    val hasNext: Boolean get() = currentIndex < channels.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
}
