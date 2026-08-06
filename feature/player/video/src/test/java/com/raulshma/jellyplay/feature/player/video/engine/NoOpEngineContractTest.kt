package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MediaEngineContractTest] specimen for [NoOpEngine], the deliberate
 * placeholder for [com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL].
 *
 * NoOp is the lower bound of the contract: it implements the full surface but
 * every operation is inert. It passes every Level-0 invariant (construction +
 * "control calls do not throw"); Level-1 behavioral invariants auto-skip
 * because its internal state is not drivable from a unit test. The matrix
 * identity + displayName checks stay active and lock the EXTERNAL projection.
 */
@RunWith(RobolectricTestRunner::class)
class NoOpEngineContractTest : MediaEngineContractTest() {

    override fun createEngine(): MediaEngine = NoOpEngine()

    // NoOp advertises exactly the EXTERNAL capability matrix.
    override fun expectedCapabilityMatrix(): EngineCapabilities = EngineCapabilityMatrix.EXTERNAL
    override fun expectedDisplayName(): String = "External"

    // createSurfaceView returns a plain View — safe under Robolectric.
    override fun supportsViewCreation(): Boolean = true
}
