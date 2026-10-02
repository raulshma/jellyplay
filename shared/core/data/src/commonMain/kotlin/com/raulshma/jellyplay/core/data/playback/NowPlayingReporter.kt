package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-wide now-playing seam: ONE `StateFlow<NowPlayingMeta?>` plus ONE
 * typed event stream that shell-level consumers (desktop Discord Rich
 * Presence, playback-event shell hooks) observe instead of reaching into any
 * player's internals. Producers publish explicitly — the video side from
 * `PlayerSessionManager` (load-ready transitions + the ViewModel's
 * release/ended paths), the desktop audio side from `DesktopAudioQueueManager`
 * (a mirror over the chassis [NowPlayingTracker] flows + the engine ENDED
 * edge) — so a consumer needs no player-specific dependency and Android's
 * media3 stack needs no publisher at all (the seam is inert until a platform
 * adopts it).
 *
 * Event semantics (pinned by `NowPlayingReporterTest`):
 *  - [NowPlayingEvent.Started] fires when [publish] lands a meta whose
 *    (kind, itemId) pair differs from the previous one (or there was none).
 *    Re-publishes of the SAME item — position refreshes, play/pause mirrors,
 *    subtitle re-attaches — update the state silently.
 *  - [NowPlayingEvent.Ended] fires on [markEnded]: a genuine end-of-stream
 *    (video EOF, audio engine ENDED before the auto-advance). The meta STAYS
 *    published — an auto-advance replaces it with the next item's
 *    [NowPlayingEvent.Started]; nothing follows when nothing is next.
 *  - [NowPlayingEvent.Stopped] fires on [clear] — playback abandoned without
 *    an end-of-stream (player closed, audio released). A clear of an empty
 *    seam emits nothing.
 *  - [NowPlayingEvent.IdleEntered]/[IdleLeft] ride the same stream for the
 *    desktop shell hooks: transitions of the shell's idle monitor
 *    (`DesktopIdleMonitor`), published by the desktop shell's services holder.
 *    They carry no meta — `meta` is null — and are unrelated to the
 *    now-playing state machine.
 */
class NowPlayingReporter {

    /** What kind of content the reported item is — the WATCHING/LISTENING split. */
    enum class Kind { VIDEO, MUSIC }

    /**
     * One now-playing snapshot. [positionMs] is the position AT PUBLISH TIME
     * (the shell hook `{position_ms}` placeholder's input); consumers that
     * need live positions collect the owning player's own flows. [durationMs]
     * is null when the item length is unknown (live streams, unprobed files).
     */
    data class NowPlayingMeta(
        val itemId: String,
        val title: String,
        /** Secondary line — the episode/series line for video, the artist for music. */
        val subtitle: String,
        val kind: Kind,
        val positionMs: Long,
        val durationMs: Long?,
    )

    /** The typed playback events — see the class KDoc for the exact semantics. */
    sealed interface NowPlayingEvent {
        data class Started(val meta: NowPlayingMeta) : NowPlayingEvent
        data class Ended(val meta: NowPlayingMeta) : NowPlayingEvent
        data class Stopped(val meta: NowPlayingMeta) : NowPlayingEvent
        data object IdleEntered : NowPlayingEvent
        data object IdleLeft : NowPlayingEvent
    }

    private val _nowPlaying = MutableStateFlow<NowPlayingMeta?>(null)
    val nowPlaying: StateFlow<NowPlayingMeta?> = _nowPlaying.asStateFlow()

    /** `tryEmit`-only — a shell hook consumer must never suspend a producer. */
    private val _events = MutableSharedFlow<NowPlayingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<NowPlayingEvent> = _events.asSharedFlow()

    /**
     * Publishes (or refreshes) the current item. A (kind, itemId) change —
     * or a first publish over an empty seam — emits [NowPlayingEvent.Started];
     * same-item refreshes only replace the state.
     */
    fun publish(meta: NowPlayingMeta) {
        val previous = _nowPlaying.value
        _nowPlaying.value = meta
        if (previous == null || previous.kind != meta.kind || previous.itemId != meta.itemId) {
            _events.tryEmit(NowPlayingEvent.Started(meta))
        }
    }

    /** Reports a genuine end-of-stream for the current item; the meta stays published. */
    fun markEnded() {
        _nowPlaying.value?.let { _events.tryEmit(NowPlayingEvent.Ended(it)) }
    }

    /** Reports playback abandoned without an end-of-stream; the seam returns to empty. */
    fun clear() {
        val previous = _nowPlaying.value ?: return
        _nowPlaying.value = null
        _events.tryEmit(NowPlayingEvent.Stopped(previous))
    }

    /** The desktop shell's idle-monitor transition publisher (shell hooks' idle arms). */
    fun reportIdle(idle: Boolean) {
        _events.tryEmit(if (idle) NowPlayingEvent.IdleEntered else NowPlayingEvent.IdleLeft)
    }
}
