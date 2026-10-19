package com.raulshma.jellyplay.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
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
 * Baseline-profile assertion for the phone app: fails the instrumentation
 * run when the installed APK ships no usable baseline profile.
 *
 * The pinned androidx.benchmark (1.5.0-alpha07) has no dedicated assertion
 * rule, so this uses the documented macrobenchmark idiom instead: a cold
 * startup loop compiled with [CompilationMode.Partial] +
 * [BaselineProfileMode.Require]. BaselineProfileMode.Require makes the
 * benchmark throw at setup when the target app has no installable baseline
 * profile (nothing in the APK's assets/dex metadata, or no ProfileInstaller
 * running it), so a missing/stale committed profile fails the lane instead of
 * silently regressing startup.
 *
 * Run this against a release/nonMinifiedRelease build produced with the
 * committed profiles (app/src/phoneRelease/generated) — a debug build carries
 * no profile and the assertion correctly rejects it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileAssertion {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun baselineProfileIsUsed() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            startupMode = StartupMode.COLD,
            iterations = ASSERTION_ITERATIONS,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}

private const val ASSERTION_ITERATIONS = 10
