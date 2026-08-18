package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Maps a [PlayerType] to a concrete [MediaEngine].
 *
 * Process-wide [Singleton]: owns the shared [DefaultBandwidthMeter] so adaptive
 * bitrate learning carries across streams. Previously a plain `object` (kept
 * that way deliberately so the shared meter stayed testable); it is now a
 * Hilt-provided [Singleton] for consistency with the rest of the playback DI
 * layer — the [resetBandwidthMeter] escape hatch is retained for test
 * isolation and an optional user-triggered "reset playback diagnostics" action.
 */
@Singleton
class PlayerEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("streaming") private val streamingOkHttpClient: OkHttpClient,
    private val fontProvider: FontProvider,
    // Nullable + defaulted so direct constructions (tests) compile unchanged;
    // Hilt injects the real singleton (it passes every constructor parameter,
    // so the default only applies to Kotlin callers that omit it).
    private val videoStreamCache: VideoStreamCache? = null,
) {

    @Volatile
    private var sharedBandwidthMeter: DefaultBandwidthMeter? = null

    /**
     * The process-wide [DefaultBandwidthMeter], lazily created from the
     * injected application [Context].
     */
    fun getSharedBandwidthMeter(): DefaultBandwidthMeter {
        return sharedBandwidthMeter ?: synchronized(this) {
            sharedBandwidthMeter ?: DefaultBandwidthMeter.Builder(context).build().also {
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
     * a feature, not a leak. However, a single pathological stream's
     * observations would otherwise pollute every subsequent item. Resetting
     * between unrelated test cases (or on a user-triggered "reset playback
     * diagnostics" action) restores a clean baseline without re-instantiating
     * the factory itself.
     */
    fun resetBandwidthMeter() {
        synchronized(this) {
            sharedBandwidthMeter = null
        }
    }

    fun create(playerType: PlayerType): MediaEngine {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> ExoPlayerEngine(context, streamingOkHttpClient, getSharedBandwidthMeter(), fontProvider, videoStreamCache)
            PlayerType.MPV -> MpvPlayerEngine(context, fontProvider)
            PlayerType.LIBVLC -> LibVlcPlayerEngine(context, fontProvider)
            // External playback is launched in a third-party app; progress is
            // reported out-of-band. A NoOpEngine expresses that intent far more
            // clearly than aliasing to a fully wired ExoPlayerEngine.
            PlayerType.EXTERNAL -> NoOpEngine()
        }
    }
}
