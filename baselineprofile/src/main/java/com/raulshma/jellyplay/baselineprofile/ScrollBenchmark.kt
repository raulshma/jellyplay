package com.raulshma.jellyplay.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll (jank) macrobenchmarks for the phone app: [FrameTimingMetric]
 * (frameDurationCpuMs / frameOverrunMs / frameCount) over the two
 * highest-traffic scrolling surfaces — the Home feed and the Library grid.
 *
 * Navigation happens in the setup block so measurement captures scroll
 * frames only, and compilation uses [CompilationMode.Partial] with
 * [BaselineProfileMode.Require] so numbers reflect the baseline-profile AOT
 * state a release install carries (a run against a profile-less APK fails
 * fast instead of producing misleading numbers).
 *
 * The fling gestures are best-effort: on a device without a signed-in server
 * (e.g. a fresh CI emulator) the surfaces are onboarding/auth screens with
 * nothing to scroll, so frameCount reports zero. Meaningful frame data needs
 * an install signed in against a server with library content.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Fling the Home feed LazyColumn. */
    @Test
    fun scrollHomeFeed() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
            },
        ) {
            flingCurrentSurface()
        }
    }

    /**
     * Fling the Library grid, reached via the deterministic `jellyplay://library`
     * deep link rather than a tab-label lookup.
     */
    @Test
    fun scrollLibraryGrid() {
        val libraryIntent = Intent(Intent.ACTION_VIEW, Uri.parse(libraryDeepLink))
            .setPackage(TARGET_PACKAGE)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait(libraryIntent)
                device.waitForIdle()
            },
        ) {
            flingCurrentSurface()
        }
    }
}

/**
 * Vertical flings on whatever scrollable surface is on screen — the same
 * gesture shape the profile generator uses, so both exercise identical
 * scroll paths.
 */
private fun MacrobenchmarkScope.flingCurrentSurface() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(SCROLL_FLING_ITERATIONS) {
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, scrollSteps)
        device.waitForIdle()
    }
}

private const val SCROLL_ITERATIONS = 5
private const val SCROLL_FLING_ITERATIONS = 3
