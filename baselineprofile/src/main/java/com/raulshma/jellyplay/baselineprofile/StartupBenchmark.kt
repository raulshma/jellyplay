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
 * Cold-startup macrobenchmark for the phone app. Measures TimeToInitialDisplay
 * / TimeToFullDisplay via [StartupTimingMetric] with no compilation overrides
 * ([CompilationMode.None]), so numbers reflect whatever AOT/base-profile state
 * the installed APK carries. The package name matches the generator's target
 * (`:app`); startActivityAndWait launches its default launcher activity.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "com.raulshma.jellyplay",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
