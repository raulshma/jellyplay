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

    /**
     * Drops the process-wide [DefaultBandwidthMeter] so the next
     * [getSharedBandwidthMeter] call builds a fresh one.
     *
     * The meter is intentionally shared across streams: ABR adaptation learns
     * network conditions from every observation, so cross-stream retention is
     * a feature, not a leak. However, the previous design offered no escape
     * hatch — a single pathological stream's observations would pollute every
     * subsequent item, and the singleton was invisible to tests. Resetting
     * between unrelated test cases (or, optionally, on a user-triggered
     * "reset playback diagnostics" action) restores a clean baseline without
     * forcing a full Hilt migration of this `object`.
     */
    fun resetBandwidthMeter() {
        synchronized(this) {
            sharedBandwidthMeter = null
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
