package com.raulshma.jellyplay.baselineprofile

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
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.raulshma.jellyplay",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Warm up the home feed: swipe through the LazyColumn so the
            // row composables, MediaImage + Coil decode pipeline, and the
            // home ViewModel state machine are AOT-compiled. The swipes
            // are best-effort and a no-op on an empty/onboarding screen.
            scrollHomeFeed()

            // Navigate into the Library tab (phone bottom-nav) and scroll
            // it so LibraryScreen + MediaCard + the adaptive grid layout
            // are warmed. Best-effort: skip silently if the tab isn't
            // visible (e.g. onboarding, music-mode, or TV form factor).
            openLibraryTab()

            // Return to home and let everything settle before the profile
            // snapshot is taken.
            device.pressBack()
            device.waitForIdle()
        }
    }
}

/**
 * Best-effort vertical swipes on whatever scrollable surface is on screen.
 * Works regardless of whether the app is logged in: an empty screen just
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
 * Taps the phone bottom-nav "Library" tab if present. The label matches
 * [com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES].
 * Skips silently when not found so the profile stays deterministic on
 * devices that aren't signed in or are running the TV flavor.
 */
private fun MacrobenchmarkScope.openLibraryTab() {
    val libraryTab = device.findObject(By.text(libraryTabLabel)) ?: return
    libraryTab.click()
    device.waitForIdle()
    // Scroll the library grid so the GridLayoutManager + card layout
    // composables + Coil prefetcher are exercised.
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(LIBRARY_SCROLL_ITERATIONS) {
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, scrollSteps)
        device.waitForIdle()
    }
}

private const val libraryTabLabel = "Library"
private const val scrollSteps = 20
private const val HOME_SCROLL_ITERATIONS = 3
private const val LIBRARY_SCROLL_ITERATIONS = 2
