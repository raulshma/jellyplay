package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.extendedColors
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning
import com.raulshma.jellyplay.feature.player.video.PlatformTranscodeReason
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_av_sync
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bitrate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_channels
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_codec
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_decoder
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_decoder_mode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_downloaded_badge
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_duration
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_frame_rate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play_method
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_resolution
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_speed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_buffer_health
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_buffer_size
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_buffered
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_buffered_ranges
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_clock
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_color_depth
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_color_range
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_display_hdr
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_display_rate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_drop_rate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_dropped_frames
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_engine
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_est_bandwidth
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bypass_download
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bypass_force_direct
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bypass_live_tv
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_performance_detected_tier
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_for_nerds
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_hdr
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_hdr_output_active
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_network
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_performance
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_playback
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_player
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_playing
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_position
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_profile
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_effective_cap
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_no_profile_cap
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_caps_bypassed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_detection_reasons
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_try_auto_low
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_try_low_switch_engine
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_try_lower_quality
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_progress
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_quality
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_sample_rate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_session_id
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_spatial
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_spatial_superseded
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_state
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_stream_bitrate
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_total_frames
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_vo_delay
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_stats_vo_dropped_frames
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_video
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoStatsOverlay(
    // High-frequency streams collected here so only this overlay (shown
    // only while "Stats for Nerds" is enabled) recomposes on position ticks.
    statsFlow: StateFlow<EngineVideoStats>,
    onClose: () -> Unit = {},
    currentPositionFlow: StateFlow<Long>,
    /** range-level buffered surface — the readout row below. */
    bufferedRangesFlow: StateFlow<List<LongRange>> = MutableStateFlow(emptyList()),
    durationMs: Long,
    playbackSpeed: Float,
    isPlaying: Boolean,
    playbackState: String,
    playMethod: String,
    /** True when the session plays a stored download — pins the
     *  "Downloaded" badge beside the play-method row. */
    isOfflineSource: Boolean = false,
    streamingQuality: String,
    playerType: String,
    decoderMode: String,
    audioSessionId: Int,
    /** Server-reported transcode reasons, pre-formatted; empty when direct
     *  playing so the "why transcoding" block is hidden entirely. */
    transcodeReasons: List<PlatformTranscodeReason> = emptyList(),
    /** The performance-profile block (issue #186 §5.12): the active profile
     *  and the resolver-derived facts — see the Performance section below. */
    performanceProfile: VideoPerformanceProfile = VideoPerformanceProfile.AUTO,
    detectedPerformanceTier: DevicePerformanceTier? = null,
    /** The resolved tuning (the profile's ceiling); [VideoPerformanceTuning.NONE]
     *  = no resolution yet = the no-cap HIGH row. */
    performanceTuning: VideoPerformanceTuning = VideoPerformanceTuning.NONE,
    /** Why the caps cannot be enforced this session (§5.13's bypass table),
     *  pre-folded by the caller from session state. */
    capsBypassed: List<CapsBypassSource> = emptyList(),
    /** The AUTO detector's sticky run — the Detection-reasons row's source,
     *  collected here so only the visible overlay ticks with it. */
    detectionFlow: StateFlow<com.raulshma.jellyplay.core.model.PerformanceDetection?> = MutableStateFlow(null),
    modifier: Modifier = Modifier,
) {
    val stats by statsFlow.collectAsStateWithLifecycle()
    val currentPositionMs by currentPositionFlow.collectAsStateWithLifecycle()
    val bufferedRanges by bufferedRangesFlow.collectAsStateWithLifecycle()
    val detection by detectionFlow.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .clip(ShapeCache.smooth12)
            .background(playerScrimColor().copy(alpha = 0.78f))
            // Swallow taps on the panel so touching it neither summons nor
            // dismisses the player controls behind it.
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.player_video_stats_for_nerds),
                style = MaterialTheme.typography.titleSmall,
                color = playerOnScrim(),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Tabler.Outline.X, stringResource(Res.string.player_video_stats_close), tint = playerOnScrim())
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("video-stats-content"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatsSection(stringResource(Res.string.player_video_stats_playback)) {
                StatsRow(stringResource(Res.string.player_video_stats_state), if (isPlaying) stringResource(Res.string.player_video_stats_playing) else playbackState)
                StatsRow(stringResource(Res.string.player_video_stats_position), formatDurationMsLocal(currentPositionMs))
                StatsRow(stringResource(Res.string.player_video_duration), formatDurationMsLocal(durationMs))
                if (durationMs > 0) {
                    val progress = (currentPositionMs.toFloat() / durationMs.toFloat() * 100f)
                    StatsRow(stringResource(Res.string.player_video_stats_progress), "${formatFixed(progress.toDouble(), 1)}%")
                }
                StatsRow(stringResource(Res.string.player_video_speed), "${playbackSpeed}x")
                val bufferHealthMs = (stats.bufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
                StatsRow(stringResource(Res.string.player_video_stats_buffer_health), formatDurationMsLocal(bufferHealthMs))
                StatsRow(stringResource(Res.string.player_video_stats_buffered), formatDurationMsLocal(stats.bufferedPositionMs))
                // the multi-range readout — count of distinct cached
                // windows plus their total span, so a dropped window after a
                // forward seek is visible as "2 ranges" rather than one scalar.
                if (bufferedRanges.isNotEmpty()) {
                    val totalBufferedMs = bufferedRanges.sumOf { it.last - it.first }
                    StatsRow(
                        stringResource(Res.string.player_video_stats_buffered_ranges),
                        "${bufferedRanges.size} · ${formatDurationMsLocal(totalBufferedMs)}",
                    )
                }
                StatsRow(stringResource(Res.string.player_video_stats_clock), rememberCurrentTimeString())
            }

            if (stats.videoCodec != null || stats.videoResolution != null) {
                StatsSection(stringResource(Res.string.player_video_video)) {
                    // the HDR passthrough badge leads the video block —
                    // active (display switched to an HDR target) or, when the
                    // display stayed SDR, the one-line tone-mapping fallback
                    // notice. Both come from the engine's target-params probe.
                    if (stats.hdrOutputActive) {
                        StatsRow(
                            stringResource(Res.string.player_video_stats_hdr_output_active),
                            "● HDR",
                        )
                    }
                    stats.videoCodec?.let { StatsRow(stringResource(Res.string.player_video_codec), it.uppercase()) }
                    stats.videoDecoder?.let { StatsRow(stringResource(Res.string.player_video_decoder), it) }
                    stats.videoResolution?.let { StatsRow(stringResource(Res.string.player_video_resolution), it) }
                    stats.videoFrameRate?.let { StatsRow(stringResource(Res.string.player_video_frame_rate), "${formatFixed(it.toDouble(), 2)} fps") }
                    stats.videoBitrate?.let { StatsRow(stringResource(Res.string.player_video_bitrate), formatBitrate(it)) }
                    stats.videoHdrType?.let { StatsRow(stringResource(Res.string.player_video_stats_hdr), it) }
                    // the display's capability answers the content row above —
                    // tone-map decisions read as (content family, display family).
                    stats.displayHdrTypes?.let {
                        StatsRow(stringResource(Res.string.player_video_stats_display_hdr), it)
                    }
                    stats.videoColorRange?.let { StatsRow(stringResource(Res.string.player_video_stats_color_range), it) }
                    stats.videoColorDepth?.let { StatsRow(stringResource(Res.string.player_video_stats_color_depth), it) }
                    stats.hdrOutputNotice?.let { notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            color = playerOnScrim().copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (stats.audioCodec != null || stats.audioChannels != null || stats.audioSpatialState != null) {
                StatsSection(stringResource(Res.string.player_video_audio)) {
                    stats.audioCodec?.let { StatsRow(stringResource(Res.string.player_video_codec), it.uppercase()) }
                    stats.audioSampleRate?.let { StatsRow(stringResource(Res.string.player_video_stats_sample_rate), "${it} Hz") }
                    stats.audioChannels?.let { ch -> StatsRow(stringResource(Res.string.player_video_channels), formatChannels(ch)) }
                    stats.audioBitrate?.let { StatsRow(stringResource(Res.string.player_video_bitrate), formatBitrate(it)) }
                    // The platform spatializer's honest state (ExoPlayer, API 32+
                    // devices with the feature) — a report, not a control. The
                    // raw state name is technical by design (codec-style); the
                    // supersession note is localized prose, so it comes from
                    // resources rather than the policy module (commonMain pure
                    // Kotlin has no string access).
                    stats.audioSpatialState?.let { state ->
                        StatsRow(
                            stringResource(Res.string.player_video_stats_spatial),
                            if (stats.audioSpatialSuperseded) {
                                state.name + " " + stringResource(Res.string.player_video_stats_spatial_superseded)
                            } else {
                                state.name
                            },
                        )
                    }
                    if (audioSessionId != 0) {
                        StatsRow(stringResource(Res.string.player_video_stats_session_id), "$audioSessionId")
                    }
                }
            }

            StatsSection(stringResource(Res.string.player_video_stats_network)) {
                StatsRow(
                    stringResource(Res.string.player_video_play_method),
                    playMethod,
                    badge = if (isOfflineSource) stringResource(Res.string.player_video_downloaded_badge) else null,
                )
                StatsRow(stringResource(Res.string.player_video_stats_quality), streamingQuality)
                if (stats.estimatedBandwidthBps > 0) {
                    StatsRow(stringResource(Res.string.player_video_stats_est_bandwidth), formatBandwidth(stats.estimatedBandwidthBps))
                }
                StatsRow(stringResource(Res.string.player_video_stats_stream_bitrate), formatBitrate(
                    stats.videoBitrate ?: (stats.audioBitrate ?: 0)
                ))
                if (transcodeReasons.isNotEmpty()) {
                    transcodeReasons.forEach { reason ->
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = reason.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = playerOnScrim().copy(alpha = 0.9f),
                            )
                            reason.hint?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = playerOnScrim().copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }

            StatsSection(stringResource(Res.string.player_video_stats_performance)) {
                // ── The performance-profile block (issue #186 §5.12): the
                // profile, its effective ceiling, why the caps cannot enforce
                // this session, the detector's reasons, and the "what to try"
                // line. Profile caps only — the resolved per-request cap
                // source (Profile / Quality / Data saver / …) is not exposed
                // by the session yet, so the Effective-cap row states the
                // profile's own ceiling and the source row stays out rather
                // than guessing.
                StatsRow(
                    stringResource(Res.string.player_video_stats_profile),
                    performanceProfileLabel(performanceProfile, detectedPerformanceTier),
                )
                StatsRow(
                    stringResource(Res.string.player_video_stats_effective_cap),
                    performanceCapLabel(performanceTuning),
                )
                if (capsBypassed.isNotEmpty()) {
                    StatsRow(
                        stringResource(Res.string.player_video_stats_caps_bypassed),
                        capsBypassed.map { bypassLabel(it) }.joinToString(", "),
                    )
                }
                detection?.reasons?.takeIf { it.isNotEmpty() }?.let { reasons ->
                    StatsRow(
                        stringResource(Res.string.player_video_stats_detection_reasons),
                        reasons.joinToString(", ") { it.name },
                    )
                }
                // The §5.12 "what to try" line: the fold reads THIS section's
                // own rows (drop rate, stream bitrate vs estimated bandwidth)
                // and the play posture (empty transcode reasons = direct
                // play) — one advice line, highest-priority cause first.
                PerformanceStatsAdvice.adviceFor(
                    droppedFrames = stats.droppedFrames,
                    totalVideoFrames = stats.totalVideoFrames,
                    isDirectPlay = transcodeReasons.isEmpty(),
                    estimatedBandwidthBps = stats.estimatedBandwidthBps,
                    streamBitrateBps = stats.videoBitrate ?: stats.audioBitrate ?: 0,
                )?.let { advice ->
                    Text(
                        text = adviceLabel(advice),
                        style = MaterialTheme.typography.bodySmall,
                        color = playerOnScrim().copy(alpha = 0.75f),
                    )
                }
                StatsRow(stringResource(Res.string.player_video_stats_dropped_frames), "${stats.droppedFrames}")
                // Only show total rendered frames when the engine actually
                // reports them (ExoPlayer via DecoderCounters, MPV via
                // displayed-frame-count); otherwise the row is hidden rather
                // than stuck on a misleading 0.
                if (stats.totalVideoFrames > 0) {
                    StatsRow(stringResource(Res.string.player_video_stats_total_frames), "${stats.totalVideoFrames}")
                    val dropPct = if (stats.totalVideoFrames > 0) {
                        stats.droppedFrames.toFloat() /
                            (stats.droppedFrames + stats.totalVideoFrames) * 100f
                    } else 0f
                    StatsRow(stringResource(Res.string.player_video_stats_drop_rate), "${formatFixed(dropPct.toDouble(), 2)}%")
                }
                if (stats.bufferSizeBytes > 0) {
                    StatsRow(stringResource(Res.string.player_video_stats_buffer_size), formatBytes(stats.bufferSizeBytes))
                }
                // Sync / display diagnostics — null-safe: rows hidden when absent.
                stats.voFrameDropCount?.let { StatsRow(stringResource(Res.string.player_video_stats_vo_dropped_frames), "$it") }
                stats.avsyncMs?.let { StatsRow(stringResource(Res.string.player_video_av_sync), "${formatFixed(it.toDouble(), 1)} ms") }
                stats.voDelayedMs?.let { StatsRow(stringResource(Res.string.player_video_stats_vo_delay), "${formatFixed(it.toDouble(), 1)} ms") }
                stats.displayFps?.let { StatsRow(stringResource(Res.string.player_video_stats_display_rate), "${formatFixed(it.toDouble(), 2)} Hz") }
            }

            StatsSection(stringResource(Res.string.player_video_stats_engine)) {
                StatsRow(stringResource(Res.string.player_video_stats_player), playerType)
                StatsRow(stringResource(Res.string.player_video_decoder_mode), decoderMode)
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
            color = MaterialTheme.extendedColors.statsOverlayText,
        )
        content()
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = playerOnScrim().copy(alpha = 0.85f),
            modifier = Modifier.weight(0.44f),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(0.56f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
                color = playerOnScrim(),
                textAlign = TextAlign.End,
            )
            // Small trailing pill (e.g. the "Downloaded" source badge) —
            // rendered only when the caller supplies one.
            if (badge != null) {
                Text(
                    text = badge,
                    modifier = Modifier.clip(ShapeCache.smoothPill)
                        .background(playerOnScrim().copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = playerOnScrim(),
                )
            }
        }
    }
}

private fun formatDurationMsLocal(ms: Long): String = com.raulshma.jellyplay.core.ui.components.formatDurationMs(ms)

// ── Performance-profile block (issue #186 §5.12) ─────────────────────────────

/**
 * Why the performance caps cannot be enforced for the current session
 * (§5.13's bypass table) — pre-folded by the caller from session state
 * (forced direct-play mode, the downloaded badge, the current source's live
 * stream id). Labels resolve to resources in the overlay.
 */
enum class CapsBypassSource { FORCE_DIRECT_PLAY, DOWNLOAD, LIVE_TV }

@Composable
private fun performanceProfileLabel(
    profile: VideoPerformanceProfile,
    detectedTier: DevicePerformanceTier?,
): String = when (profile) {
    // §5.12's `Auto (Detected: Low)` shape; a still-running first detection
    // shows the bare profile — the Effective-cap row already communicates the
    // MEDIUM seed the resolver falls back to.
    VideoPerformanceProfile.AUTO -> detectedTier
        ?.let { "${profile.displayName} (${stringResource(Res.string.player_video_performance_detected_tier, it.displayName)})" }
        ?: profile.displayName
    else -> profile.displayName
}

/** `1080p / 8.0 Mbps` or `No profile cap` (HIGH / pre-resolution NONE). */
@Composable
private fun performanceCapLabel(tuning: VideoPerformanceTuning): String {
    val height = tuning.maxVideoHeight
    val bitrate = tuning.maxVideoBitrate
    if (height == null && bitrate == null) {
        return stringResource(Res.string.player_video_stats_no_profile_cap)
    }
    val heightPart = height?.let { "${it}p" }
    val bitratePart = bitrate?.let { "${formatFixed(it / 1_000_000.0, 1)} Mbps" }
    return listOfNotNull(heightPart, bitratePart).joinToString(" / ")
}

@Composable
private fun bypassLabel(source: CapsBypassSource): String = when (source) {
    CapsBypassSource.FORCE_DIRECT_PLAY -> stringResource(Res.string.player_video_bypass_force_direct)
    CapsBypassSource.DOWNLOAD -> stringResource(Res.string.player_video_bypass_download)
    CapsBypassSource.LIVE_TV -> stringResource(Res.string.player_video_bypass_live_tv)
}

@Composable
private fun adviceLabel(advice: PerformanceStatsAdvice.Advice): String = when (advice) {
    PerformanceStatsAdvice.Advice.TRY_AUTO_OR_LOW ->
        stringResource(Res.string.player_video_stats_try_auto_low)
    PerformanceStatsAdvice.Advice.TRY_LOW_OR_SWITCH_ENGINE ->
        stringResource(Res.string.player_video_stats_try_low_switch_engine)
    PerformanceStatsAdvice.Advice.LOWER_QUALITY_CHECK_WIFI ->
        stringResource(Res.string.player_video_stats_try_lower_quality)
}

/**
 * The §5.12 "what to try" fold — pure and JVM-tested (the diagnostics' only
 * decision logic; the overlay renders its verdict):
 *
 *  1. high dropped frames + direct play → try AUTO or LOW (the profile cap
 *     transcodes down to the tier's ceiling);
 *  2. high dropped frames + transcode → try LOW; if it persists, switch the
 *     player engine;
 *  3. estimated bandwidth below the stream bitrate → lower quality / check
 *     Wi-Fi.
 *
 * "High" drop posture: a ≥2% drop rate when the engine reports frame totals,
 * otherwise a ≥[DROPPED_FRAMES_HIGH_ABSOLUTE] absolute count (an engine
 * without totals cannot rate — the absolute fallback keeps the advice
 * reachable there).
 */
internal object PerformanceStatsAdvice {

    /** The advice verdicts, mapped to localized lines by the overlay. */
    enum class Advice {
        TRY_AUTO_OR_LOW,
        TRY_LOW_OR_SWITCH_ENGINE,
        LOWER_QUALITY_CHECK_WIFI,
    }

    /** Drop-rate threshold (percent) when frame totals are reported. */
    const val DROP_RATE_HIGH_PERCENT = 2f

    /** Absolute dropped-frame fallback when no frame totals are reported. */
    const val DROPPED_FRAMES_HIGH_ABSOLUTE = 250

    fun isDropRateHigh(droppedFrames: Long, totalVideoFrames: Long): Boolean =
        if (totalVideoFrames > 0) {
            val total = droppedFrames + totalVideoFrames
            droppedFrames.toFloat() / total.toFloat() * 100f >= DROP_RATE_HIGH_PERCENT
        } else {
            droppedFrames >= DROPPED_FRAMES_HIGH_ABSOLUTE
        }

    fun adviceFor(
        droppedFrames: Long,
        totalVideoFrames: Long,
        isDirectPlay: Boolean,
        estimatedBandwidthBps: Long,
        streamBitrateBps: Int,
    ): Advice? = when {
        isDropRateHigh(droppedFrames, totalVideoFrames) && isDirectPlay -> Advice.TRY_AUTO_OR_LOW
        isDropRateHigh(droppedFrames, totalVideoFrames) -> Advice.TRY_LOW_OR_SWITCH_ENGINE
        estimatedBandwidthBps > 0 && streamBitrateBps > 0 && estimatedBandwidthBps < streamBitrateBps ->
            Advice.LOWER_QUALITY_CHECK_WIFI
        else -> null
    }
}

private fun formatBitrate(bps: Int): String = when {
    bps >= 1_000_000 -> "${formatFixed(bps / 1_000_000.0, 1)} Mbps"
    bps >= 1_000 -> "${Math.round(bps / 1_000.0)} kbps"
    else -> "$bps bps"
}

private fun formatBandwidth(bps: Long): String = when {
    bps >= 1_000_000 -> "${formatFixed(bps / 1_000_000.0, 1)} Mbps"
    bps >= 1_000 -> "${Math.round(bps / 1_000.0)} kbps"
    else -> "$bps bps"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${formatFixed(bytes / 1_048_576.0, 1)} MB"
    bytes >= 1_024 -> "${Math.round(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

private fun formatChannels(ch: Int): String = when (ch) {
    1 -> "Mono (1)"
    2 -> "Stereo (2)"
    6 -> "5.1 (6)"
    8 -> "7.1 (8)"
    else -> "$ch ch"
}

@Composable
private fun rememberCurrentTimeString(): String {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    // Reuse a single Date instance and mutate its underlying time field each
    // tick instead of allocating a fresh Date() every second.
    val date = remember { Date() }
    var time by remember { mutableStateOf(formatter.format(date)) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            date.time = System.currentTimeMillis()
            time = formatter.format(date)
        }
    }
    return time
}
