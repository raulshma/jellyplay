package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * Video filter / aspect / zoom settings.
 */
@Immutable
data class VideoFxState(
    val videoEffects: VideoEffectsConfig = VideoEffectsConfig(),
    val aspectRatio: AspectRatio = AspectRatio.AUTO,
    val detectedAspectRatio: AspectRatio? = null,
    val tvZoomModePercent: Float = 0f,
)
