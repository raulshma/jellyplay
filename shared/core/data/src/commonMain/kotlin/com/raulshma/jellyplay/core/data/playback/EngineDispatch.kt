package com.raulshma.jellyplay.core.data.playback

/**
 * The narrow engine-command port [AudioQueueStateCore] drives a playback
 * engine through — the queue chassis's ONLY way to touch an engine. The
 * desktop adapter (`DesktopAudioQueueManager`, core:data jvmMain)
 * implements it over its nullable audio-only `MediaEngine`, so every
 * command keeps its historical engine-null tolerance (`engine?.…`):
 * a dispatch against no live engine is a no-op, exactly like the
 * pre-extraction inlined calls.
 *
 * The port is deliberately narrower than `MediaEngine`: the chassis needs
 * load, transport, seek and speed writes ONLY. Everything else an engine
 * offers (observers, config pushes, release) stays adapter-owned — the
 * adapter wires engine lifecycle, event observers (which feed the core's
 * `onEngine*` callbacks), effects pushes and teardown around the core.
 *
 *  - [isLive] — the chassis's engine-liveness gate. The twin managers'
 *    `exoPlayer ?: return` / `engine ?: return` sites (skip-previous,
 *    shuffle reorder, play-from-queue same-index, undo restore, the
 *    transition tail) are chassis DECISIONS, so the liveness read belongs
 *    on the port rather than in a callback.
 *  - [prepare] — resolve + load [item] at [startPositionMs]. Resolution is
 *    adapter territory (the desktop's per-item resolver + next-item
 *    prefetch cache + per-track ReplayGain context all live behind this
 *    call), which is why the port takes the QUEUE ITEM and a position, not
 *    an engine request.
 *  - [stop] — the empty-queue park (remove-current draining the queue,
 *    clearQueue): Android's clear-media-items idle and the desktop's
 *    `engine.stop()` are the same chassis decision, so stop is a port
 *    command alongside play/pause.
 *
 * Main-thread confined by contract (the [AudioQueueManager] thread
 * contract); the adapter enforces it at its public entries.
 */
interface EngineDispatch {

    /** True while an engine instance is live (the twins' null-player gate). */
    val isLive: Boolean

    /** Resolve [item] and load it into the engine starting at [startPositionMs]. */
    fun prepare(item: AudioQueueItem, startPositionMs: Long)

    fun play()

    /** Adapter-implemented gating preserved: a no-op against an idle engine. */
    fun pause()

    /** Park the engine idle without releasing it (queue emptied / cleared). */
    fun stop()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)
}
