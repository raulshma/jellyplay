package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Maps a [PlayerType] to a concrete [MediaEngine] (wave 8C seam): the member
 * the commonMain session cluster ([PlayerSessionManager][com.raulshma.jellyplay.feature.player.video.PlayerSessionManager])
 * calls. The androidMain class formerly named `PlayerEngineFactory` was
 * renamed [AndroidPlayerEngineFactory][com.raulshma.jellyplay.feature.player.video.engine.AndroidPlayerEngineFactory]
 * and implements this interface (it keeps owning the process-wide media3
 * DefaultBandwidthMeter and the Context/OkHttp/font wiring). The jvmMain
 * actual returns a no-op engine — desktop playback engines are queued work.
 */
interface PlayerEngineFactory {

    /**
     * Suspend so implementors may await preconditions before construction
     * (the desktop factory polls for the surface HWND; mpv's `wid` is
     * ctor-time) without blocking the caller's dispatcher — the session
     * pipeline calls this on the main-adjacent context that also drives the
     * UI.
     */
    suspend fun create(playerType: PlayerType): MediaEngine
}
