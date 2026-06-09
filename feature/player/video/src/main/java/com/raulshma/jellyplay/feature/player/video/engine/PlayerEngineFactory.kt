package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.raulshma.jellyplay.core.model.PlayerType

object PlayerEngineFactory {

    @Volatile
    private var sharedBandwidthMeter: DefaultBandwidthMeter? = null

    fun getSharedBandwidthMeter(context: Context): DefaultBandwidthMeter {
        return sharedBandwidthMeter ?: synchronized(this) {
            sharedBandwidthMeter ?: DefaultBandwidthMeter.Builder(context.applicationContext).build().also {
                sharedBandwidthMeter = it
            }
        }
    }

    fun create(context: Context, playerType: PlayerType): MediaEngine {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> ExoPlayerEngine(context, getSharedBandwidthMeter(context))
            PlayerType.MPV -> MpvPlayerEngine(context)
            PlayerType.LIBVLC -> LibVlcPlayerEngine(context)
            PlayerType.EXTERNAL -> ExoPlayerEngine(context, getSharedBandwidthMeter(context))
        }
    }
}
