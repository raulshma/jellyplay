package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.feature.player.video.engine.EngineError

/**
 * The VOD session's fine-grained load lifecycle: replaces the former coarse
 * Idle/Loading/Ready/Failed state machine with progressive stage tracking,
 * monotonic timestamps, and stage latency metrics.
 *
 * All loading stages inherit from [Loading] for backward compatibility with
 * existing pattern matches.
 */
@Immutable
sealed interface LoadPhase {
    /** No load has been armed (fresh session, or after [PlayerSessionManager.release]). */
    data object Idle : LoadPhase

    /**
     * Active load in progress. Subtypes provide granular stage tracking,
     * start timestamps, and elapsed latency.
     */
    sealed interface Loading : LoadPhase {
        val startedAtMs: Long
        val stageStartedAtMs: Long
        val stageName: String
        val elapsedMs: Long get() = monotonicNowMillis() - startedAtMs
        val stageElapsedMs: Long get() = monotonicNowMillis() - stageStartedAtMs

        /** Stage 1: SyncPlay queue reconcile, prefs projection, offline resume resolution */
        data class Preparing(
            override val startedAtMs: Long = monotonicNowMillis(),
            override val stageStartedAtMs: Long = startedAtMs,
        ) : Loading {
            override val stageName: String get() = "Preparing"
        }

        /** Stage 2: Fetching item metadata & media details from server/cache */
        data class FetchingMetadata(
            override val startedAtMs: Long,
            override val stageStartedAtMs: Long = monotonicNowMillis(),
            val itemId: String,
        ) : Loading {
            override val stageName: String get() = "FetchingMetadata"
        }

        /** Stage 3: Resolving stream info / PlaybackInfo (Direct Play vs Transcode decision) */
        data class ResolvingStream(
            override val startedAtMs: Long,
            override val stageStartedAtMs: Long = monotonicNowMillis(),
            val itemId: String,
            val mediaSourceId: String?,
            val isWarmHit: Boolean = false,
        ) : Loading {
            override val stageName: String get() = "ResolvingStream"
        }

        /** Stage 4: Creating/adopting engine, configuring hardware decoders & audio sink */
        data class InitializingEngine(
            override val startedAtMs: Long,
            override val stageStartedAtMs: Long = monotonicNowMillis(),
            val playerType: PlayerType,
            val playMethod: PlayMethod,
            val streamUrl: String,
        ) : Loading {
            override val stageName: String get() = "InitializingEngine"
        }

        /** Stage 5: MediaEngine loaded and connecting; priming initial buffer before playback */
        data class PrimingBuffer(
            override val startedAtMs: Long,
            override val stageStartedAtMs: Long = monotonicNowMillis(),
            val playerType: PlayerType,
            val playMethod: PlayMethod,
            val bufferedDurationMs: Long = 0L,
            val targetBufferDurationMs: Long = 0L,
            val estimatedBandwidthBps: Long? = null,
        ) : Loading {
            override val stageName: String get() = "PrimingBuffer"
        }

        /** Backward-compatible companion object for legacy calls and coarse pattern matches */
        companion object : Loading {
            override val startedAtMs: Long = 0L
            override val stageStartedAtMs: Long = 0L
            override val stageName: String = "Loading"
        }
    }

    /**
     * Settled playback state: media is primed and playing.
     * Provides full latency breakdown for analytics and diagnostics.
     */
    sealed interface Ready : LoadPhase {
        val playMethod: PlayMethod
        val playerType: PlayerType
        val totalLoadDurationMs: Long
        val stageLatenciesMs: Map<String, Long>

        data class Settled(
            override val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
            override val playerType: PlayerType = PlayerType.EXO_PLAYER,
            override val totalLoadDurationMs: Long = 0L,
            override val stageLatenciesMs: Map<String, Long> = emptyMap(),
        ) : Ready

        // Backward compatibility delegate: `LoadPhase.Ready` matches the companion object
        companion object : Ready by Settled()
    }

    /** The load failed; [message] is the user-facing reason (also sent on the message bus). */
    data class Failed(
        val message: String,
        val failedStage: String? = null,
        val totalElapsedMs: Long = 0L,
        val error: EngineError? = null,
    ) : LoadPhase
}

/** Convenience helper matching [PlayerSessionState.isReady]. */
val LoadPhase.isReady: Boolean get() = this is LoadPhase.Ready

/** Convenience helper indicating active load. */
val LoadPhase.isLoading: Boolean get() = this is LoadPhase.Loading
