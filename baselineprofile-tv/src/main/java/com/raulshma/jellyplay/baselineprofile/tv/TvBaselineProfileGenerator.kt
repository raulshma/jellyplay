package com.raulshma.jellyplay.baselineprofile.tv

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TvBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.raulshma.jellyplay.tv",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Warm up the home feed: swipe through the row list so the TV
            // row composables, MediaImage + Coil decode pipeline, and the
            // home ViewModel state machine are AOT-compiled. The swipes are
            // best-effort and a no-op on an empty/auth screen.
            scrollHomeFeed()

            // Walk the TV navigation drawer (TvNavigationDrawer): Home is the
            // launch destination, then open the Library and Settings rail
            // entries so those destinations and their layouts are warmed.
            // Skips silently on an auth/onboarding screen where the drawer
            // is not composed.
            openDrawerDestination(libraryDrawerLabel)
            openDrawerDestination(settingsDrawerLabel)

            // Let everything settle before the profile snapshot is taken. A
            // single back press at a top-level destination only arms the
            // exit-confirmation toast, so the app stays in the foreground.
            device.pressBack()
            device.waitForIdle()
        }
    }
}

/**
 * Best-effort vertical swipes on whatever scrollable surface is on screen.
 * Works regardless of whether the app is signed in: an auth screen just
 * consumes the gesture. Each swipe is `steps=20` (~smooth) which gives the
 * gesture pipeline + LazyScrollable container a realistic workout.
 */
private fun MacrobenchmarkScope.scrollHomeFeed() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(HOME_SCROLL_ITERATIONS) {
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, scrollSteps)
        device.waitForIdle()
    }
}

/**
 * Taps a TvNavigationDrawer rail entry. The drawer rows expose their label as
 * the icon content description in both the collapsed (72dp icon rail) and
 * expanded states, so a direct tap works without driving D-pad focus first.
 * The labels match [com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES]
 * and R.string.nav_settings on the en-US image. Skips silently when not found
 * so the profile stays deterministic on devices that aren't signed in.
 */
private fun MacrobenchmarkScope.openDrawerDestination(label: String) {
    val destination = device.findObject(By.desc(label)) ?: return
    destination.click()
    device.waitForIdle()
    // Scroll the destination screen so its layout + card composables + the
    // Coil prefetcher are exercised.
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(DESTINATION_SCROLL_ITERATIONS) {
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, scrollSteps)
        device.waitForIdle()
    }
}

private const val libraryDrawerLabel = "Library"
private const val settingsDrawerLabel = "Settings"
private const val scrollSteps = 20
private const val HOME_SCROLL_ITERATIONS = 3
private const val DESTINATION_SCROLL_ITERATIONS = 2
