package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.VideoPerformanceResolver
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.PerformanceDetection
import com.raulshma.jellyplay.core.model.PerformanceReason
import com.raulshma.jellyplay.core.model.ResolvedPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceMatrix
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Issue #186 test seam: a stable [VideoPerformanceResolver] stub whose
 * `resolve(requested)` returns [tier]'s matrix tuning (published into
 * `resolved` exactly like the real resolver does) and whose `warm()` is a
 * no-op — the suites that construct [PlayerSessionManager],
 * [SessionLoadPipeline] or the [PlaybackSession] graph inject it instead of
 * the DataStore-backed production single.
 *
 * NOTE: only the ONE-ARG `resolve(requested)` is stubbed — recording the
 * no-arg overload would evaluate its default argument
 * (`playbackStore.playback.value...`) against the stub's null private store
 * and NPE at recording time. The player-video call sites all pass the
 * profile explicitly (from the same aggregate snapshot the request builds
 * from), so the no-arg arm never runs under this stub.
 *
 * The default tier is HIGH — the no-caps no-op — so every construction
 * predating the performance profiles keeps its exact resolve-request values
 * (`applyProfileCeiling(base, base, HIGH-tuning, mode)` is the identity
 * fold). Pass LOW/MEDIUM to pin a tier under test; [requested] rides along
 * as the profile the stub "persisted".
 */
internal fun testVideoPerformanceResolver(
    tier: DevicePerformanceTier = DevicePerformanceTier.HIGH,
    requested: VideoPerformanceProfile = VideoPerformanceProfile.HIGH,
): VideoPerformanceResolver {
    val profile = ResolvedPerformanceProfile(
        requested = requested,
        tier = tier,
        tuning = VideoPerformanceMatrix.forTier(tier),
        detection = null,
    )
    return mockk {
        every { resolved } returns MutableStateFlow(profile)
        every { detection } returns MutableStateFlow(null)
        coEvery { resolve(any()) } returns profile
        coEvery { warm() } returns Unit
        coEvery { redetect() } returns PerformanceDetection(
            tier = tier,
            score = 0,
            reasons = listOf(PerformanceReason.MANUAL_REDETECT),
            fingerprint = "test",
            detectorVersion = 1,
            detectedAtEpochMs = 0L,
        )
    }
}
