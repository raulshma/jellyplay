package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.Player

/**
 * Android-only capability seam: implemented by engines that drive playback
 * through a media3 [Player] (currently [ExoPlayerEngine]). Replaces the
 * retired type-erased `RemotePlayableEngine.underlyingPlayer` escape hatch —
 * the commonMain contract no longer carries an opaque handle, and the
 * `as?` narrowing happens exactly once, inside [asMedia3Player].
 *
 * Engines without a media3 player (mpv, libVLC, NoOp, every desktop engine)
 * do not implement this interface and resolve to `null` — mirroring the
 * `ZoomSafeSubtitleStrategy` precedent of declaring a capability on the seam
 * instead of type-testing concrete adapters. The null is meaningful, not a
 * dead end: gh-178's session controller fronts capability-null engines with
 * the [MediaEngineSessionPlayer] adapter, so the seam decides WHICH transport
 * a system MediaSession gets (native vs adapter), never WHETHER it gets one.
 */
interface Media3PlayerHost {

    /** The engine's live media3 player, or `null` when none is bound. */
    val media3Player: Player?
}

/**
 * Narrows [MediaEngine] to its media3 [Player] transport, or `null` when the
 * engine does not host one. The single cast site of the media3 seam — the
 * former per-callsite `engine.underlyingPlayer as? Player` pattern.
 */
fun MediaEngine.asMedia3Player(): Player? = (this as? Media3PlayerHost)?.media3Player
