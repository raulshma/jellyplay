package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.StallCause

/**
 * An actionable remediation option presented to the user during slow
 * playback or buffering stalls. Zero silent drops — user must confirm.
 */
@Immutable
sealed interface RecoveryAction {
    /** Steps down streaming quality to a specific [targetQuality] tier. */
    data class SwitchQuality(
        val targetQuality: StreamingQuality,
        val height: Int,
        val bitrateBps: Int,
    ) : RecoveryAction

    /** Switches active playback engine (e.g. ExoPlayer -> MPV on decoder stall). */
    data class SwitchEngine(
        val targetEngine: PlayerType,
    ) : RecoveryAction

    /** Boosts player buffer size to standard large window (e.g. 30s/45s). */
    data object BoostBufferSize : RecoveryAction

    /** Dismisses the prompt and keeps current settings. */
    data object Dismiss : RecoveryAction
}

/**
 * High-level actionable recovery proposal presented via [PlaybackRecoveryBanner].
 */
@Immutable
data class ActionableRecovery(
    val id: String,
    val cause: StallCause,
    val title: String,
    val message: String,
    val primaryAction: RecoveryAction,
    val secondaryAction: RecoveryAction = RecoveryAction.Dismiss,
)

/**
 * Advice line for the "Stats for Nerds" performance block.
 */
enum class StatsAdvice {
    TRY_AUTO_OR_LOW,
    TRY_LOW_OR_SWITCH_ENGINE,
    LOWER_QUALITY_CHECK_WIFI,
}

/**
 * Sliding window tracker for buffering stalls to implement anti-thrashing
 * rebuffer threshold escalation (3s -> 6s -> 12s).
 */
class StallTracker(
    private val windowMs: Long = SLIDING_WINDOW_MS,
    private val clock: () -> Long = { com.raulshma.jellyplay.core.model.monotonicNowMillis() },
) {
    private val stallTimestamps = mutableListOf<Long>()

    /**
     * Records a stall occurrence at [timestampMs] (or now) and returns the
     * total number of stalls observed within the sliding window.
     */
    fun recordStall(timestampMs: Long = clock()): Int {
        stallTimestamps.removeAll { timestampMs - it > windowMs }
        stallTimestamps.add(timestampMs)
        return stallTimestamps.size
    }

    /**
     * Calculates the active rebuffer threshold in milliseconds:
     * - Stall 1: 3,000ms
     * - Stall 2: 6,000ms
     * - Stall 3+: 12,000ms
     */
    fun currentRebufferThresholdMs(nowMs: Long = clock()): Long {
        stallTimestamps.removeAll { nowMs - it > windowMs }
        val count = stallTimestamps.size
        return when {
            count >= 3 -> ESCALATED_REBUFFER_THRESHOLD_MS_3
            count == 2 -> ESCALATED_REBUFFER_THRESHOLD_MS_2
            else -> DEFAULT_REBUFFER_THRESHOLD_MS
        }
    }

    /** Returns current number of stalls within sliding window. */
    fun stallCount(nowMs: Long = clock()): Int {
        stallTimestamps.removeAll { nowMs - it > windowMs }
        return stallTimestamps.size
    }

    /** Resets stall history (e.g. on item change or smooth playback sustained >60s). */
    fun reset() {
        stallTimestamps.clear()
    }

    companion object {
        const val SLIDING_WINDOW_MS = 60_000L
        const val DEFAULT_REBUFFER_THRESHOLD_MS = 3_000L
        const val ESCALATED_REBUFFER_THRESHOLD_MS_2 = 6_000L
        const val ESCALATED_REBUFFER_THRESHOLD_MS_3 = 12_000L
    }
}

/**
 * Pure diagnostic policy evaluating playback health, stall causes, and
 * actionable remediation. Zero platform or UI framework dependencies.
 */
object PlaybackDiagnosticAdvisor {

    const val STALL_DURATION_ACTIONABLE_MS = 3_500L
    const val DROP_RATE_HIGH_PERCENT = 2f
    const val DROPPED_FRAMES_HIGH_ABSOLUTE = 250

    /**
     * Determines whether frame drop rate is considered severe.
     */
    fun isDropRateHigh(droppedFrames: Long, totalVideoFrames: Long): Boolean =
        if (totalVideoFrames > 0) {
            val total = droppedFrames + totalVideoFrames
            droppedFrames.toFloat() / total.toFloat() * 100f >= DROP_RATE_HIGH_PERCENT
        } else {
            droppedFrames >= DROPPED_FRAMES_HIGH_ABSOLUTE
        }

    /**
     * Pure fold for Stats for Nerds advice (promoted from VideoStatsOverlay).
     */
    fun performanceStatsAdviceFor(
        droppedFrames: Long,
        totalVideoFrames: Long,
        isDirectPlay: Boolean,
        estimatedBandwidthBps: Long,
        streamBitrateBps: Int,
    ): StatsAdvice? = when {
        isDropRateHigh(droppedFrames, totalVideoFrames) && isDirectPlay -> StatsAdvice.TRY_AUTO_OR_LOW
        isDropRateHigh(droppedFrames, totalVideoFrames) -> StatsAdvice.TRY_LOW_OR_SWITCH_ENGINE
        estimatedBandwidthBps > 0 && streamBitrateBps > 0 && estimatedBandwidthBps < streamBitrateBps ->
            StatsAdvice.LOWER_QUALITY_CHECK_WIFI
        else -> null
    }

    /**
     * Evaluates stall cause and telemetry to propose an [ActionableRecovery] banner.
     * Returns null if no action is warranted or stall duration is below threshold.
     *
     * The decoder-overload arm is judged on [recentDroppedFrames]/[recentTotalFrames]
     * — the deltas since the last evaluation — rather than the lifetime
     * [droppedFrames]/[totalVideoFrames] totals, so historical drops cannot
     * keep triggering the engine switch and override the NETWORK_STARVATION
     * arm. Lifetime totals remain the basis for the stats overlay advice
     * ([performanceStatsAdviceFor]).
     */
    fun evaluateRecovery(
        stallCause: StallCause,
        stallDurationMs: Long,
        stallsInWindow: Int,
        isDirectPlay: Boolean,
        currentEngine: PlayerType,
        streamBitrateBps: Int,
        estimatedBandwidthBps: Long,
        transcodeSpeedRatio: Double? = null,
        droppedFrames: Long = 0L,
        totalVideoFrames: Long = 0L,
        recentDroppedFrames: Long = 0L,
        recentTotalFrames: Long = 0L,
    ): ActionableRecovery? {
        val isStallProlonged = stallDurationMs >= STALL_DURATION_ACTIONABLE_MS || stallsInWindow >= 2

        if (stallCause == StallCause.DECODER_OVERLOAD || isDropRateHigh(recentDroppedFrames, recentTotalFrames)) {
            return when (currentEngine) {
                PlayerType.EXO_PLAYER -> ActionableRecovery(
                    id = "decoder_switch_mpv",
                    cause = StallCause.DECODER_OVERLOAD,
                    title = "Hardware Decoder Overload",
                    message = "ExoPlayer is dropping frames on this stream. Switch to MPV Player for advanced software decoding?",
                    primaryAction = RecoveryAction.SwitchEngine(PlayerType.MPV),
                )
                PlayerType.MPV -> ActionableRecovery(
                    id = "decoder_switch_exo",
                    cause = StallCause.DECODER_OVERLOAD,
                    title = "Decoder Struggling",
                    message = "MPV is dropping frames on this stream. Switch to ExoPlayer for hardware acceleration?",
                    primaryAction = RecoveryAction.SwitchEngine(PlayerType.EXO_PLAYER),
                )
                else -> null
            }
        }

        if (!isStallProlonged && stallsInWindow < 3) {
            return null
        }

        return when {
            stallCause == StallCause.NETWORK_STARVATION -> {
                // The step-down is validated against the stream's actual
                // bitrate and the measured bandwidth: a fixed tier could sit
                // ABOVE the current bitrate (no relief) or above the measured
                // bandwidth (still starving). No qualifying tier → the
                // quality-reduction action is omitted entirely.
                val stepDown = validatedStepDown(streamBitrateBps, estimatedBandwidthBps) ?: return null
                val height = stepDown.height
                if (isDirectPlay) {
                    ActionableRecovery(
                        id = "network_step_down_${height}p",
                        cause = StallCause.NETWORK_STARVATION,
                        title = "Slow Connection Detected",
                        message = "Direct Play stream bitrate (${formatBitrate(streamBitrateBps)}) exceeds network speed. Step down to ${stepDown.quality.displayName} for smooth playback?",
                        primaryAction = RecoveryAction.SwitchQuality(
                            targetQuality = stepDown.quality,
                            height = height,
                            bitrateBps = stepDown.bitrateBps.toInt(),
                        ),
                    )
                } else {
                    ActionableRecovery(
                        id = "network_step_down_${height}p",
                        cause = StallCause.NETWORK_STARVATION,
                        title = "Slow Connection Detected",
                        message = "Network bandwidth (${formatBandwidth(estimatedBandwidthBps)}) is struggling with current stream. Lower quality to ${stepDown.quality.displayName}?",
                        primaryAction = RecoveryAction.SwitchQuality(
                            targetQuality = stepDown.quality,
                            height = height,
                            bitrateBps = stepDown.bitrateBps.toInt(),
                        ),
                    )
                }
            }
            stallCause == StallCause.SERVER_TRANSCODE_BOTTLENECK -> {
                if (currentEngine == PlayerType.EXO_PLAYER) {
                    ActionableRecovery(
                        id = "transcode_try_mpv",
                        cause = StallCause.SERVER_TRANSCODE_BOTTLENECK,
                        title = "Server Transcode Struggling",
                        message = "Server cannot transcode fast enough. Switch to MPV Player to attempt Direct Play on device?",
                        primaryAction = RecoveryAction.SwitchEngine(PlayerType.MPV),
                    )
                } else {
                    // Same validated step-down as the starvation arms.
                    val stepDown = validatedStepDown(streamBitrateBps, estimatedBandwidthBps) ?: return null
                    val height = stepDown.height
                    ActionableRecovery(
                        id = "transcode_step_down_${height}p",
                        cause = StallCause.SERVER_TRANSCODE_BOTTLENECK,
                        title = "Server Transcode Struggling",
                        message = "Server transcode speed is below realtime. Lower quality to ${stepDown.quality.displayName} to reduce server load?",
                        primaryAction = RecoveryAction.SwitchQuality(
                            targetQuality = stepDown.quality,
                            height = height,
                            bitrateBps = stepDown.bitrateBps.toInt(),
                        ),
                    )
                }
            }
            stallsInWindow >= 3 -> {
                ActionableRecovery(
                    id = "frequent_stalls_boost_buffer",
                    cause = StallCause.BUFFER_UNDERRUN,
                    title = "Frequent Buffering Detected",
                    message = "Multiple playback rebuffers occurred. Boost player buffer size to 30 seconds for stable playback?",
                    primaryAction = RecoveryAction.BoostBufferSize,
                )
            }
            else -> null
        }
    }

    /** One tier of the step-down ladder: the quality pick and its bitrate ceiling. */
    private data class QualityTier(val quality: StreamingQuality, val bitrateBps: Long) {
        val height: Int get() = quality.maxHeight ?: 0
    }

    /**
     * The step-down ladder, highest tier first, with each tier's bitrate
     * ceiling (mirroring the adaptive-bitrate caps — 4K has no ceiling in
     * that ladder, so it is no step-down candidate here either).
     */
    private val STEP_DOWN_LADDER = listOf(
        QualityTier(StreamingQuality.FHD_1080P, 8_000_000L),
        QualityTier(StreamingQuality.HD_720P, 3_000_000L),
        QualityTier(StreamingQuality.SD_480P, 1_500_000L),
        QualityTier(StreamingQuality.LOW_360P, 500_000L),
    )

    /**
     * The highest supported tier whose bitrate sits below [currentBitrateBps]
     * AND within [estimatedBandwidthBps] when a live estimate exists — the
     * validated step-down target. Null when no lower tier qualifies (unknown
     * current bitrate, or every lower tier above the measured bandwidth):
     * the caller omits the quality-reduction action instead of suggesting
     * one that cannot relieve the stall.
     */
    private fun validatedStepDown(currentBitrateBps: Int, estimatedBandwidthBps: Long): QualityTier? =
        STEP_DOWN_LADDER.firstOrNull { tier ->
            tier.bitrateBps < currentBitrateBps &&
                (estimatedBandwidthBps <= 0L || tier.bitrateBps <= estimatedBandwidthBps)
        }

    private fun formatBitrate(bps: Int): String = when {
        bps >= 1_000_000 -> "${(bps / 1_000_000.0).formatFixedLocal(1)} Mbps"
        bps >= 1_000 -> "${(bps / 1_000.0).formatFixedLocal(0)} kbps"
        else -> "$bps bps"
    }

    private fun formatBandwidth(bps: Long): String = when {
        bps >= 1_000_000 -> "${(bps / 1_000_000.0).formatFixedLocal(1)} Mbps"
        bps >= 1_000 -> "${(bps / 1_000.0).formatFixedLocal(0)} kbps"
        else -> "$bps bps"
    }

    private fun Double.formatFixedLocal(digits: Int): String {
        val factor = when (digits) {
            1 -> 10.0
            2 -> 100.0
            else -> 1.0
        }
        val rounded = kotlin.math.round(this * factor) / factor
        return if (digits == 0) "${rounded.toLong()}" else "$rounded"
    }
}
