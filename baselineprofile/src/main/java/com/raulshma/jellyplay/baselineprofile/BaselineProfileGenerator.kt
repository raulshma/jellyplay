package com.raulshma.jellyplay.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline-profile generation for the phone app, split into two kinds of
 * passes — each pass its own @Test so the Gradle-managed-device generation
 * task runs and merges them independently.
 *
 * - ONE startup pass ([generateStartupShellProfile]) runs with
 *   `includeInStartupProfile = true`, so ONLY the classes it executes land in
 *   the generated `startup-prof.txt`: boot (install splash → shell host →
 *   first composed frame) plus a single first-navigation hop onto a top-level
 *   tab. Keeping this pass narrow is what keeps `startup-prof.txt` tight —
 *   feature-screen warm-up must never ride inside it.
 *
 * - FEATURE passes (every other @Test) run with `includeInStartupProfile =
 *   false`: their rules merge into `baseline-prof.txt` only, so those screens
 *   stay AOT-compiled for regular runs without claiming startup priority.
 *   Where the app offers a deep link (`android.intent.action.VIEW` on the
 *   `jellyplay://` scheme — the canonical destination table lives in
 *   DeepLinkGrammar), the pass launches it directly via an explicit intent;
 *   otherwise the gesture is best-effort and skips silently on devices where
 *   the surface isn't reachable (not signed in, onboarding, music/video mode,
 *   or TV form factor).
 *
 * No pass can fail generation: all navigation is guarded, so an unsigned
 * fresh emulator (the CI GMD) simply yields a narrower profile.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    // ------------------------------------------------------------------
    // STARTUP pass — the only one feeding startup-prof.txt.
    // Boot + first navigation, nothing else.
    // ------------------------------------------------------------------

    /**
     * Shell startup profile: launch (splash gate → shell host → Home's first
     * frame) and one bottom-nav tab hop. No scrolling, no feature-screen
     * warm-up — those live in the feature passes below so the startup profile
     * stays as small as the true cold-start path allows. Best-effort: on an
     * unsigned device the tab isn't there and the pass is launch-only.
     */
    @Test
    fun generateStartupShellProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // One first-navigation hop so the bottom-nav shell plumbing
            // (route switch + tab bar recomposition) is in the startup
            // profile alongside boot.
            openLibraryTabTapOnly()

            device.pressBack()
            device.waitForIdle()
        }
    }

    // ------------------------------------------------------------------
    // FEATURE passes — baseline-prof.txt only (includeInStartupProfile =
    // false). Deterministic deep links where the app provides them,
    // guarded gestures otherwise.
    // ------------------------------------------------------------------

    /**
     * Home feed scroll: warms the LazyColumn row composables, MediaImage +
     * Coil decode pipeline, and the home ViewModel state machine. Best-effort
     * — an empty/onboarding screen just consumes the gestures.
     */
    @Test
    fun generateHomeFeedProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            scrollHomeFeed()
        }
    }

    /**
     * Library grid: deep links straight to the Library top-level destination
     * (`jellyplay://library` — deterministic, no tab-label lookup) and
     * scrolls it so LibraryScreen + MediaCard + the adaptive grid layout +
     * Coil prefetcher are exercised.
     */
    @Test
    fun generateLibraryGridProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startDeepLinkAndWait(libraryDeepLink)
            scrollGrid()
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Search screen via `jellyplay://search`, so SearchScreen + the
     * text-field focus pipeline are warmed without depending on tab-label
     * lookups.
     */
    @Test
    fun generateSearchProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startDeepLinkAndWait(searchDeepLink)
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Settings screen via `jellyplay://settings`, so SettingsScreen's
     * LazyColumn and preference rows are warmed.
     */
    @Test
    fun generateSettingsProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startDeepLinkAndWait(settingsDeepLink)
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Downloads screen via `jellyplay://downloads` (deterministic) plus a
     * scroll so the downloads list rows are exercised when the device has
     * content.
     */
    @Test
    fun generateDownloadsProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startDeepLinkAndWait(downloadsDeepLink)
            scrollGrid()
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Details → player pass. Deep links to the details destination
     * (`jellyplay://media/{id}` — deterministic navigation: the intent parse +
     * pending-route fold + details route classes execute regardless of
     * session state) using a well-formed probe item id, because the CI GMD is
     * unsigned and has no real library to browse. The id is arbitrary: the
     * details screen simply fails to resolve it.
     *
     * Actual playback CANNOT be reached deterministically here — PlayerActivity
     * is not exported, and its only entry is a details-screen Play/Resume
     * press backed by a signed-in server with media. So the press is
     * best-effort: when a Play/Resume button is on screen the pass taps it and
     * lets the player route settle, which is what carries PlayerActivity +
     * the video engine classes into the profile; otherwise it skips silently.
     */
    @Test
    fun generateDetailsAndPlayerProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startDeepLinkAndWait(mediaDetailDeepLink(PROBE_DETAILS_ITEM_ID))
            device.waitForIdle()
            tapPlayButton()
            // Back out of playback (if it started) and the details screen.
            try {
                device.pressBack()
                device.waitForIdle()
            } catch (_: Exception) {
                // Player already exited — nothing to unwind.
            }
        }
    }

    /**
     * Live TV pass: best-effort tap on the "Live TV" entry (nav drawer /
     * bottom bar label — there is no deep-link host for live TV). Skips
     * silently when the entry isn't visible (unsigned, live TV disabled, or
     * offline-hidden).
     */
    @Test
    fun generateLiveTvProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            tapAndWait(liveTvLabel)
            device.pressHome()
        }
    }

    /**
     * Audio browse pass: the music-mode bottom-nav "Browse" tab (mirrors
     * MUSIC_TOP_LEVEL_ROUTES) is the only statically reachable audio surface.
     * Skips silently on video-mode devices.
     */
    @Test
    fun generateAudioBrowseProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            tapAndWait(audioBrowseTabLabel)
            device.pressHome()
        }
    }
}

/**
 * Launches a `jellyplay://` deep link through the app's own VIEW intent
 * filter and waits for the destination to settle. MainActivity is
 * singleTask, so this lands as onNewIntent + pending-route navigation when
 * the process is alive — both paths are profile-worthy.
 */
private fun MacrobenchmarkScope.startDeepLinkAndWait(uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(TARGET_PACKAGE)
    startActivityAndWait(intent)
    device.waitForIdle()
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
 * Scrolls the current screen's grid/list surface. Same gesture shape as
 * [scrollHomeFeed]; kept separate so each pass's intent reads clearly.
 */
private fun MacrobenchmarkScope.scrollGrid() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(LIBRARY_SCROLL_ITERATIONS) {
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, scrollSteps)
        device.waitForIdle()
    }
}

/**
 * Taps the phone bottom-nav "Library" tab if present — tap ONLY, no
 * scrolling (that lives in the library feature pass). The label mirrors
 * [com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES]. Skips
 * silently when not found (unsigned, onboarding, music-mode, TV form factor).
 */
private fun MacrobenchmarkScope.openLibraryTabTapOnly() {
    val libraryTab = device.findObject(By.text(libraryTabLabel)) ?: return
    try {
        libraryTab.click()
        device.waitForIdle()
    } catch (_: Exception) {
        // Node vanished mid-gesture — skip the hop.
    }
}

private const val libraryTabLabel = "Library"
internal const val scrollSteps = 20
private const val HOME_SCROLL_ITERATIONS = 3
private const val LIBRARY_SCROLL_ITERATIONS = 2

/**
 * Taps a node by text or content-desc if present, waits for the destination
 * to settle, then backs out. Returns without touching anything when the node
 * isn't on screen.
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
 * Best-effort Play/Resume press on whatever details screen is showing.
 * Both labels exist because MediaDetailActionButtons renders "Resume" when
 * the item has in-progress position, "Play" otherwise (and "Replay" when
 * fully played — treated as Play here). No-op when neither is on screen.
 */
private fun MacrobenchmarkScope.tapPlayButton() {
    val play = device.findObject(By.text(playLabel))
        ?: device.findObject(By.text(resumeLabel))
        ?: device.findObject(By.desc(playLabel))
        ?: device.findObject(By.desc(resumeLabel))
        ?: return
    try {
        play.click()
        device.waitForIdle()
    } catch (_: Exception) {
        // Button vanished mid-gesture or playback failed to start — skip.
    }
}

private const val playLabel = "Play"
private const val resumeLabel = "Resume"

/** Live TV nav entry label (NavDestinationRegistry: Route.LiveTv → "Live TV"). */
private const val liveTvLabel = "Live TV"

private const val audioBrowseTabLabel = "Browse"

internal const val TARGET_PACKAGE = "com.raulshma.jellyplay"

// Deep-link URIs, mirroring DeepLinkGrammar's builders (the grammar is in a
// KMP common source set; the producer module re-states the wire format here
// rather than taking a cross-tree source dependency).
internal const val libraryDeepLink = "jellyplay://library"
private const val searchDeepLink = "jellyplay://search"
private const val settingsDeepLink = "jellyplay://settings"
private const val downloadsDeepLink = "jellyplay://downloads"

/**
 * Well-formed-but-unresolved probe id for the details deep link: 32-hex,
 * the Jellyfin item-id shape, so the route parses cleanly and the details
 * destination composes (then fails to resolve) without touching real data.
 */
private const val PROBE_DETAILS_ITEM_ID = "0102030405060708090a0b0c0d0e0f10"

private fun mediaDetailDeepLink(itemId: String) = "jellyplay://media/$itemId"
