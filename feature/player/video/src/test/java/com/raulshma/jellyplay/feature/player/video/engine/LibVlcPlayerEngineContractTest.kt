package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MediaEngineContractTest] specimen for [LibVlcPlayerEngine].
 *
 * The constructor stores its arguments only — the `LibVLC(...)` JNI init is
 * lazy (in `load`), so construction is safe. Construction is still wrapped in a
 * try/catch so the specimen is skipped (not failed) if the libVLC JNI glue
 * cannot load. `supportsViewCreation` is false because the surface path touches
 * `LibVLC` JNI (229-230), which we do not exercise here.
 */
@RunWith(RobolectricTestRunner::class)
class LibVlcPlayerEngineContractTest : MediaEngineContractTest() {

    override fun createEngine(): MediaEngine = try {
        LibVlcPlayerEngine(
            context = ApplicationProvider.getApplicationContext(),
            fontProvider = FontProvider(ApplicationProvider.getApplicationContext()),
        )
    } catch (t: Throwable) {
        // UnsatisfiedLinkError / ExceptionInInitializerError when libVLC's
        // JNI glue cannot load in this environment — skip the specimen.
        assumeTrue("libVLC not constructible in this unit-test environment", false)
        throw t
    }

    override fun expectedCapabilityMatrix(): EngineCapabilities = EngineCapabilityMatrix.LIBVLC
    override fun expectedDisplayName(): String = "LibVLC"
}
