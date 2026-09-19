package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins for the ExoPlayer reuse-vs-rebuild predicate
 * ([shouldReuseExistingPlayer] over [LoadRebuildInputs], commonMain): every
 * construction input must participate in the equality set, because a reused
 * player keeps its baked-in renderers factory / buffers / DRM hook /
 * data-source chain — one missed delta silently leaks the old build onto the
 * new item. The androidMain engine itself is not unit-testable; this is the
 * pin that the decision table cannot drift.
 */
class ExoPlayerReusePolicyTest {

    private val drmA = EngineDrmSessionManagerProvider { null }
    private val drmB = EngineDrmSessionManagerProvider { null }

    private val baseline = LoadRebuildInputs(
        decoderMode = DecoderMode.HW_PREFERRED,
        exoCfg = ExoPlayerEngineConfig(),
        minBufferMs = 50_000,
        maxBufferMs = 90_000,
        serverUrl = "https://jellyfin/media",
        authToken = "token",
        headers = mapOf("Authorization" to "MediaBrowser token"),
        assSession = false,
        pauseOnAudioFocusLoss = true,
        drmProvider = null,
        streamCacheEligible = true,
    )

    private fun delta(mutate: LoadRebuildInputs.() -> LoadRebuildInputs): LoadRebuildInputs =
        baseline.mutate()

    @Test
    fun identicalInputs_withExistingPlayer_reuse() {
        assertTrue(
            shouldReuseExistingPlayer(
                existingPlayerPresent = true,
                lastInputs = baseline,
                newInputs = baseline.copy(),
            ),
        )
    }

    @Test
    fun noExistingPlayer_alwaysRebuilds_evenWithIdenticalInputs() {
        assertFalse(
            shouldReuseExistingPlayer(
                existingPlayerPresent = false,
                lastInputs = baseline,
                newInputs = baseline.copy(),
            ),
        )
    }

    @Test
    fun noLastBuild_alwaysRebuilds() {
        assertFalse(
            shouldReuseExistingPlayer(
                existingPlayerPresent = true,
                lastInputs = null,
                newInputs = baseline,
            ),
        )
    }

    @Test
    fun everySingleFieldDelta_forcesRebuild() {
        val deltas = listOf(
            delta { copy(decoderMode = DecoderMode.SW_ONLY) },
            delta { copy(exoCfg = exoCfg.copy(enableDecoderFallback = false)) },
            delta { copy(exoCfg = exoCfg.copy(skipSilence = true)) },
            delta { copy(minBufferMs = minBufferMs + 1) },
            delta { copy(maxBufferMs = maxBufferMs + 1) },
            delta { copy(serverUrl = "https://other/media") },
            delta { copy(serverUrl = null) },
            delta { copy(authToken = "other") },
            delta { copy(authToken = null) },
            delta { copy(headers = headers + ("X-Extra" to "v")) },
            delta { copy(assSession = true) },
            delta { copy(pauseOnAudioFocusLoss = false) },
            delta { copy(streamCacheEligible = false) },
        )
        deltas.forEach { newInputs ->
            assertFalse(
                shouldReuseExistingPlayer(
                    existingPlayerPresent = true,
                    lastInputs = baseline,
                    newInputs = newInputs,
                ),
                "delta must rebuild: $newInputs",
            )
        }
    }

    @Test
    fun drmProvider_comparedByIdentity_notByProduct() {
        // Providers have no equals: the same instance means the same DRM
        // hook (reuse), a fresh instance of an equivalent provider forces
        // the rebuild path — pinned so nobody "fixes" this to structural.
        assertTrue(
            shouldReuseExistingPlayer(
                existingPlayerPresent = true,
                lastInputs = baseline.copy(drmProvider = drmA),
                newInputs = baseline.copy(drmProvider = drmA),
            ),
        )
        assertFalse(
            shouldReuseExistingPlayer(
                existingPlayerPresent = true,
                lastInputs = baseline.copy(drmProvider = drmA),
                newInputs = baseline.copy(drmProvider = drmB),
            ),
        )
        assertFalse(
            shouldReuseExistingPlayer(
                existingPlayerPresent = true,
                lastInputs = baseline.copy(drmProvider = drmA),
                newInputs = baseline.copy(drmProvider = null),
            ),
        )
    }

    @Test
    fun streamCacheEligibilityFlip_forcesRebuild_bothDirections() {
        // The cache wrapper is baked into the MediaSourceFactory: leaking
        // the cached chain onto a non-cacheable item (or vice versa) is a
        // correctness bug, not a perf nit.
        assertFalse(
            shouldReuseExistingPlayer(true, baseline, baseline.copy(streamCacheEligible = false)),
        )
        assertFalse(
            shouldReuseExistingPlayer(
                true,
                baseline.copy(streamCacheEligible = false),
                baseline,
            ),
        )
    }
}
