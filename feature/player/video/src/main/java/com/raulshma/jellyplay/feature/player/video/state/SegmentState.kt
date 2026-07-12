package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior

/**
 * Raw segment/chapter data fed to [com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator].
 * The derived overlay (active segment, intro/credits, up-next) lives in
 * [com.raulshma.jellyplay.feature.player.video.SegmentOverlayState] on the ViewModel.
 */
@Immutable
data class SegmentState(
    val segments: List<MediaSegment> = emptyList(),
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
)
