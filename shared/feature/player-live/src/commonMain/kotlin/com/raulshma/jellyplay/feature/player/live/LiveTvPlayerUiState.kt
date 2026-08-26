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
    val engineState: LiveEngineState = LiveEngineState.IDLE,
    /**
     * Player-facing error, still unresolved (the commonMain VM has no
     * Context): a localized [LivePlayerMessage.Resource] or an already-final
     * [LivePlayerMessage.Raw] engine string. The screen collapses it with
     * [LivePlayerMessage.asText] where [components.LiveErrorBanner] renders.
     */
    val errorMessage: LivePlayerMessage? = null,
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
    /** Server-reported transcode reasons for the current stream (raw tokens);
     *  empty when direct streaming. Appended to the error overlay's detail
     *  section so "why is this transcoding" is answerable on Live TV too. */
    val transcodeReasons: List<String> = emptyList(),
    /** Controls auto-hide delay sourced from the user's `videoControlsTimeoutMs`
     *  preference (mirrors the VOD player). Doubled on TV in the screen. */
    val controlsTimeoutMs: Long = 5_000L,
) {
    val hasNext: Boolean get() = currentIndex < channels.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0
}
