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

            // Best-effort navigation passes over the remaining surfaces.
            // Each is guarded so a missing node (onboarding, not signed
            // in, music-mode, or TV form factor) can never fail profile
            // generation — the profile just stays as narrow as the
            // device allows.
            openSearchTab()
            openSettings()
            openDetailsCard()
            openAudioSection()

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

/**
 * Taps a node by text or content-desc if present, waits for the
 * destination to settle, then backs out. Returns without touching
 * anything when the node isn't on screen.
 */
private fun MacrobenchmarkScope.tapAndWait(label: String) {
    val node = device.findObject(By.text(label))
        ?: device.findObject(By.desc(label))
        ?: return
    try {
        node.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    } catch (_: Exception) {
        // Node vanished mid-gesture or navigation failed — skip the pass.
    }
}

/**
 * Opens the Search tab (phone bottom-nav label mirrors
 * [com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES])
 * so SearchScreen + the text-field focus pipeline are warmed.
 */
private fun MacrobenchmarkScope.openSearchTab() {
    tapAndWait(searchTabLabel)
}

/**
 * Opens Settings via its toolbar/drawer entry (content-desc or visible
 * label) so SettingsScreen's LazyColumn and preference rows are warmed.
 */
private fun MacrobenchmarkScope.openSettings() {
    tapAndWait(settingsLabel)
}

/**
 * Best-effort Details pass: taps the center of the screen inside the
 * library grid, which opens a media details card when the device is
 * signed in and the grid has content. A no-op tap elsewhere.
 */
private fun MacrobenchmarkScope.openDetailsCard() {
    try {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    } catch (_: Exception) {
        // Tap landed on nothing or navigation failed — skip the pass.
    }
}

/**
 * Best-effort Audio pass: the music-mode bottom-nav "Browse" tab
 * (mirrors MUSIC_TOP_LEVEL_ROUTES) is the only statically reachable
 * audio surface. Skips silently on video-mode devices.
 */
private fun MacrobenchmarkScope.openAudioSection() {
    tapAndWait(audioBrowseTabLabel)
}

private const val searchTabLabel = "Search"
private const val settingsLabel = "Settings"
private const val audioBrowseTabLabel = "Browse"
