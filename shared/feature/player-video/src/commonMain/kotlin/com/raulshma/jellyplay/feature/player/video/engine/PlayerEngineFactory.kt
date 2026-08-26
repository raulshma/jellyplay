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

    fun create(playerType: PlayerType): MediaEngine
}
