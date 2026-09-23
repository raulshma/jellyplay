package com.raulshma.jellyplay.feature.player.live

import androidx.compose.runtime.Immutable

/**
 * One-shot screen events from the live player ViewModel — the VOD
 * `SessionEvent` shape scaled to this screen's two sinks:
 *  - [Message] — record/cancel feedback and other transient notices; the
 *    screen resolves [LivePlayerMessage.Resource] values with the collecting
 *    composition's locale (livetv's LiveTvUserMessage seam shape) and
 *    forwards through the app-wide user-message bus.
 *  - [ClosePlayer] — the PiP-dismiss choreography: the window is showing a
 *    dead stream, so the screen must leave instead of lingering behind the
 *    closing window (the VOD VM's `closePlayer` pattern).
 *
 * ONE intake ([LiveTvPlayerViewModel.events], the player-contract
 * EngineSessionShell's one-shot event pipe — tryEmit-only, so a
 * mid-teardown emit never suspends) carries both, replacing the former
 * `messages` + `closePlayer` member pair.
 */
@Immutable
sealed interface LivePlayerEvent {
    data class Message(val message: LivePlayerMessage) : LivePlayerEvent

    data object ClosePlayer : LivePlayerEvent
}
