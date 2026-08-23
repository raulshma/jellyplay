package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LiveTvProgram

/**
 * Single-source-of-truth UI state for the channel-detail screen.
 *
 * [currentProgram] drives the backdrop image and the now-playing hero progress
 * bar; [programs] is the "On Today" timeline (now → end of day, upcoming only).
 * Both are resolved once on load (no periodic refresh).
 */
@Immutable
data class ChannelDetailUiState(
    val channelId: String = "",
    val channelName: String = "",
    val channelNumber: String? = null,
    /** Channel primary image URL; empty when no image tag is present. */
    val channelLogoUrl: String = "",
    /** BlurHash for the channel's primary image; used as a backdrop placeholder. */
    val channelBlurHash: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The program currently airing (if any) — drives backdrop + hero progress. */
    val currentProgram: LiveTvProgram? = null,
    /** Today's upcoming programs, sorted by start time ascending. */
    val programs: List<LiveTvProgram> = emptyList(),
)
