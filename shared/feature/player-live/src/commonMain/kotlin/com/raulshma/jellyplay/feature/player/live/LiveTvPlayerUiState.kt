package com.raulshma.jellyplay.feature.player.live

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.core.ui.message.UiMessage

@Immutable
data class LiveTvPlayerUiState(
    val isLoadingChannels: Boolean = true,
    val channels: List<LiveTvChannel> = emptyList(),
    val currentIndex: Int = 0,
    val currentChannel: LiveTvChannel? = null,
    val currentProgram: LiveTvProgram? = null,
    val nextProgram: LiveTvProgram? = null,
    val isPlaying: Boolean = false,
    val isAtLiveEdge: Boolean = true,
    val engineState: LiveEngineState = LiveEngineState.IDLE,
    /**
     * Player-facing error, still unresolved (the commonMain VM has no
     * Context): a localized [UiMessage.Resource] or an already-final
     * [UiMessage.Raw] engine string. The screen collapses it with
     * `asText` where [components.LiveErrorBanner] renders.
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
    /**
     * The persisted input mapping (the same `videoInputBindings` store the
     * VOD player projects): the live D-pad surface resolves its arm gates
     * through it (the GLOSSARY "Input dispatch module" — live riding the
     * map), so a disabled/NONE D-pad row silences the live arm and a rebound
     * row routes where live can express it. Defaults to the catalog defaults
     * — an untouched map keeps live's fixed arm behavior exactly.
     */
    val inputMap: PlayerInputMap = PlayerInputDefaults.defaultMap(),
) {
    val hasNext: Boolean get() = currentIndex < channels.lastIndex
    val hasPrevious: Boolean get() = currentIndex > 0

    /**
     * The buffering chrome, DERIVED over the engine phase + the error slot —
     * there is no stored flag (see [liveBufferingChrome] for the decision and
     * its migration/precedence record). The screens read this unchanged
     * (`LivePlayerScreen`'s banner gate passes it to
     * [com.raulshma.jellyplay.feature.player.live.components.LiveErrorBanner]'s
     * spinner arm); the value can no longer disagree with [engineState]
     * because it is a pure function of the phase and [errorMessage].
     */
    val isBuffering: Boolean
        get() = liveBufferingChrome(
            engineState = engineState,
            hasError = errorMessage != null,
        )
}

/**
 * The live player's buffering-chrome decision — the pure fold
 * [LiveTvPlayerUiState.isBuffering] projects, exhaustive over
 * [LiveEngineState] so a state added to the enum breaks this site at compile
 * time instead of silently mis-projecting.
 *
 * MIGRATED (the recorded Tier 1 `LiveTvPlayerUiState` boolean drift):
 * `isBuffering` was a stored Boolean defaulting `true`, written at exactly
 * ONE fold arm (`EngineStateChanged`: `BUFFERING || IDLE`) and independently
 * cleared by four more (`ChannelLoadFailed`, `TuneFailed`,
 * `BufferingWatchdogTimedOut`, `TranscodeFallbackFailed`) plus raised by
 * `setLiveStreamOption`'s optimistic fold — none of which touch
 * [LiveEngineState], so `engineState=BUFFERING && isBuffering=false`
 * (post-watchdog) and `engineState=READY && isBuffering=true` (mid
 * stream-option switch) were both reachable while the screens read the
 * Boolean. The stored flag is deleted; the chrome is a pure function of the
 * phase plus an EXPLICIT error precedence, pinned by
 * `LiveTvPlayerUiStateTest`:
 *
 *  - **Error precedence**: an error message wins over the buffering faces —
 *    a stalled tuner whose watchdog timed out still sits in
 *    [LiveEngineState.BUFFERING] (the session never rewrites the engine's
 *    state), and the spinner must lift so the error banner renders the
 *    retryable error instead ("the stuck rebuffer spinner must lift with the
 *    error" — the pre-migration pins' wording, preserved). Every phase with
 *    `hasError = true` therefore reads `false`.
 *  - [LiveEngineState.IDLE] and [LiveEngineState.BUFFERING] spin while no
 *    error stands: IDLE is the pre-load first frame and the post-`stop`
 *    reset ("IDLE renders the buffering spinner" — the pinned legacy
 *    semantics of the one derivation arm), BUFFERING is a load/rebuffer in
 *    flight.
 *  - [LiveEngineState.READY], [LiveEngineState.ENDED] and
 *    [LiveEngineState.ERROR] never spin.
 *
 * DECLARED FIX (the migration's only observable behavior change): the
 * stored flag let `setLiveStreamOption`'s optimistic fold raise
 * `isBuffering = true` while the engine still reported READY/ERROR/ENDED
 * from the previous stream — the `engineState=READY && isBuffering=true`
 * drift combo, rendered as a rebuffer spinner laid over the live picture
 * (or over the previous error) before the reload's first
 * `EngineStateChanged` lands. That combination is now unrepresentable: the
 * spinner returns exactly when the reload flips the engine to
 * BUFFERING/IDLE, like every other buffering face. The post-watchdog combo
 * (`BUFFERING && isBuffering=false`) becomes the error-precedence rule
 * above with a byte-identical observable.
 */
internal fun liveBufferingChrome(
    engineState: LiveEngineState,
    hasError: Boolean,
): Boolean = when (engineState) {
    LiveEngineState.IDLE,
    LiveEngineState.BUFFERING,
    -> !hasError
    LiveEngineState.READY,
    LiveEngineState.ENDED,
    LiveEngineState.ERROR,
    -> false
}
