package com.raulshma.jellyplay.feature.music.feedback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop actual of the [MusicMessageBus] (wave 21B — replaces the
 * message-dropping no-op whose "no host yet" note outlived the desktop
 * shell's snackbar): a small buffering relay the desktop shell collects into
 * its snackbar host (DesktopAppRoot's DesktopNavScaffold — the twin of
 * Android bridging the same seam into the app-wide UserMessageBus).
 *
 * Shape, deliberately minimal (no new framework, no cross-module seams):
 *  - [messages] is a no-replay SharedFlow with a small drop-oldest buffer —
 *    this is a UI-feedback surface, not a log: messages emitted while no
 *    host is collecting (before the signed-in scaffold composes, e.g. a
 *    failure during the last signed-out moment) are dropped, and a burst
 *    beyond the buffer keeps the NEWEST messages.
 *  - [error]'s `tryEmit` never suspends and never fails — the shared
 *    ViewModels call it from release paths where delivery is best-effort.
 *
 * Public (not `internal` like most platform actuals) because the desktop
 * shell lives in a different module (apps/desktop) and collects the relay
 * through the Koin single; the commonMain [MusicMessageBus] stays the only
 * surface the shared code knows.
 */
class DesktopMusicMessageBus : MusicMessageBus {

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = MESSAGE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Error messages emitted through [error]; see class KDoc for the buffering contract. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    override fun error(message: String) {
        _messages.tryEmit(message)
    }

    private companion object {
        /** Generous vs. realistic bursts (one message per failed refresh); drop-oldest beyond. */
        const val MESSAGE_BUFFER_CAPACITY = 16
    }
}

fun desktopMusicMessageBusModule(): Module = module {
    single<MusicMessageBus> { DesktopMusicMessageBus() }
}
