package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Creates the appropriate [PlayerEngine] implementation based on user preference.
 */
object PlayerEngineFactory {

    fun create(context: Context, playerType: PlayerType): PlayerEngine {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> ExoPlayerEngine(context)
            PlayerType.MPV -> MpvPlayerEngine(context)
            PlayerType.LIBVLC -> LibVlcPlayerEngine(context)
            PlayerType.EXTERNAL -> ExoPlayerEngine(context) // fallback — shouldn't reach here
        }
    }
}
