package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadPhaseTest {

    @Test
    fun testIdleState() {
        val idle = LoadPhase.Idle
        assertFalse(idle.isReady)
        assertFalse(idle.isLoading)
    }

    @Test
    fun testProgressiveLoadingStages() {
        val t0 = 1000L
        val preparing = LoadPhase.Loading.Preparing(t0, t0)
        assertTrue(preparing.isLoading)
        assertFalse(preparing.isReady)
        assertEquals("Preparing", preparing.stageName)
        assertEquals(t0, preparing.startedAtMs)

        val fetching = LoadPhase.Loading.FetchingMetadata(
            startedAtMs = t0,
            stageStartedAtMs = t0 + 100,
            itemId = "item-1"
        )
        assertTrue(fetching.isLoading)
        assertFalse(fetching.isReady)
        assertEquals("FetchingMetadata", fetching.stageName)
        assertEquals("item-1", fetching.itemId)

        val resolving = LoadPhase.Loading.ResolvingStream(
            startedAtMs = t0,
            stageStartedAtMs = t0 + 250,
            itemId = "item-1",
            mediaSourceId = "src-1",
            isWarmHit = false
        )
        assertTrue(resolving.isLoading)
        assertFalse(resolving.isReady)
        assertEquals("ResolvingStream", resolving.stageName)

        val initializing = LoadPhase.Loading.InitializingEngine(
            startedAtMs = t0,
            stageStartedAtMs = t0 + 500,
            playerType = PlayerType.EXO_PLAYER,
            playMethod = PlayMethod.DIRECT_PLAY,
            streamUrl = "http://stream.m3u8"
        )
        assertTrue(initializing.isLoading)
        assertFalse(initializing.isReady)
        assertEquals("InitializingEngine", initializing.stageName)

        val priming = LoadPhase.Loading.PrimingBuffer(
            startedAtMs = t0,
            stageStartedAtMs = t0 + 750,
            playerType = PlayerType.EXO_PLAYER,
            playMethod = PlayMethod.DIRECT_PLAY,
            bufferedDurationMs = 2000L,
            targetBufferDurationMs = 5000L
        )
        assertTrue(priming.isLoading)
        assertFalse(priming.isReady)
        assertEquals("PrimingBuffer", priming.stageName)
    }

    @Test
    fun testBackwardCompatibilityWithLoadingCompanion() {
        val legacyLoading: LoadPhase = LoadPhase.Loading
        assertTrue(legacyLoading is LoadPhase.Loading)
        assertTrue(legacyLoading.isLoading)
        assertFalse(legacyLoading.isReady)
        assertEquals("Loading", (legacyLoading as LoadPhase.Loading).stageName)

        val progressiveStage: LoadPhase = LoadPhase.Loading.Preparing(5000L)
        assertTrue(progressiveStage is LoadPhase.Loading)
        assertTrue(progressiveStage.isLoading)
    }

    @Test
    fun testBackwardCompatibilityWithReadyCompanion() {
        val legacyReady: LoadPhase = LoadPhase.Ready
        assertTrue(legacyReady is LoadPhase.Ready)
        assertTrue(legacyReady.isReady)
        assertFalse(legacyReady.isLoading)

        val customReady: LoadPhase = LoadPhase.Ready.Settled(
            playMethod = PlayMethod.DIRECT_PLAY,
            playerType = PlayerType.EXO_PLAYER,
            totalLoadDurationMs = 850L,
            stageLatenciesMs = mapOf("Preparing" to 50L, "PrimingBuffer" to 300L)
        )
        assertTrue(customReady is LoadPhase.Ready)
        assertTrue(customReady.isReady)
        assertFalse(customReady.isLoading)
        val settled = customReady as LoadPhase.Ready.Settled
        assertEquals(850L, settled.totalLoadDurationMs)
        assertEquals(50L, settled.stageLatenciesMs["Preparing"])
    }

    @Test
    fun testPatternMatchingBranches() {
        fun classify(phase: LoadPhase): String = when (phase) {
            LoadPhase.Idle -> "idle"
            is LoadPhase.Loading -> "loading:${phase.stageName}"
            is LoadPhase.Ready -> "ready:${phase.totalLoadDurationMs}ms"
            is LoadPhase.Failed -> "failed:${phase.message}"
        }

        assertEquals("idle", classify(LoadPhase.Idle))
        assertEquals("loading:Preparing", classify(LoadPhase.Loading.Preparing(10L)))
        assertEquals("loading:ResolvingStream", classify(LoadPhase.Loading.ResolvingStream(10L, 20L, "i", "s")))
        assertEquals("ready:0ms", classify(LoadPhase.Ready))
        assertEquals("ready:400ms", classify(LoadPhase.Ready.Settled(totalLoadDurationMs = 400L)))
        assertEquals("failed:Network timeout", classify(LoadPhase.Failed("Network timeout")))
    }

    @Test
    fun testFailedStateCarriesDiagnosticContext() {
        val failed = LoadPhase.Failed(
            message = "Stream resolution timed out",
            failedStage = "ResolvingStream",
            totalElapsedMs = 3170L
        )

        assertFalse(failed.isReady)
        assertFalse(failed.isLoading)
        assertEquals("Stream resolution timed out", failed.message)
        assertEquals("ResolvingStream", failed.failedStage)
        assertEquals(3170L, failed.totalElapsedMs)
    }
}
