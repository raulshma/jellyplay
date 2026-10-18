package com.raulshma.jellyplay.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup macrobenchmarks for the phone app: TimeToInitialDisplay /
 * TimeToFullDisplay via [StartupTimingMetric] across the three launch
 * temperatures.
 *
 * Full-display timing (TimeToFullDisplay) requires the app to call
 * `reportFullyDrawn()` — MainActivity does so exactly once when the install
 * splash gate releases. Without that call the full-display measurement is
 * absent and only TTID is reported.
 *
 * The benchmarks run with [CompilationMode.None] (no compilation overrides),
 * so numbers reflect whatever AOT/baseline-profile state the installed APK
 * carries. The package name matches the generator's target (`:app`);
 * startActivityAndWait launches its default launcher activity.
 *
 * For "is the baseline profile actually in effect?" see
 * [BaselineProfileAssertion]; for scroll/jank over the main surfaces see
 * [ScrollBenchmark].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Full process creation — the install-splash-to-Home path. */
    @Test
    fun coldStartup() {
        startupBenchmark(StartupMode.COLD)
    }

    /** Process alive but activity recreated (configuration change, back-out). */
    @Test
    fun warmStartup() {
        startupBenchmark(StartupMode.WARM)
    }

    /** Process and activity alive; brought back from background. */
    @Test
    fun hotStartup() {
        startupBenchmark(StartupMode.HOT)
    }

    private fun startupBenchmark(startupMode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = STARTUP_ITERATIONS,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}

private const val STARTUP_ITERATIONS = 5
