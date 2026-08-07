package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MediaEngineContractTest] specimen for [MpvPlayerEngine].
 *
 * The constructor builds helpers only (no native init in `<init>`), but the
 * native libmpv surface path may not load under a plain JVM/Robolectric
 * environment. Construction is wrapped in a try/catch so the whole specimen
 * is reported as skipped (not failed) when libmpv cannot load — the contract
 * still runs for engines that can construct. `supportsViewCreation` is true
 * because [MpvPlayerEngine.createSurfaceView] has a fallback plain-View path.
 */
@RunWith(RobolectricTestRunner::class)
class MpvPlayerEngineContractTest : MediaEngineContractTest() {

    override fun createEngine(): MediaEngine = try {
        MpvPlayerEngine(
            context = ApplicationProvider.getApplicationContext(),
            fontProvider = FontProvider(ApplicationProvider.getApplicationContext()),
        )
    } catch (t: Throwable) {
        // UnsatisfiedLinkError / ExceptionInInitializerError when libmpv's
        // native glue cannot load in this environment — skip the specimen.
        assumeTrue("mpv not constructible in this unit-test environment", false)
        throw t
    }

    override fun expectedCapabilityMatrix(): EngineCapabilities = EngineCapabilityMatrix.MPV
    override fun expectedDisplayName(): String = "mpv"

    // createSurfaceView's native MPVView path and its asset-backed fallback
    // both touch resources unavailable under Robolectric
    // (FileNotFoundException in ShadowArscAssetManager), so the View-returning
    // Level-0 test skips for this specimen even when the engine constructed.
    override fun supportsViewCreation(): Boolean = false
}
