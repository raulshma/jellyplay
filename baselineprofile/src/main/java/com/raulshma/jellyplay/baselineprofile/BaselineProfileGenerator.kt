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
 * - The FEATURE passes ([generateNavigationAndFeedsProfile],
 *   [generateDetailsAndPlayerProfile]) run with `includeInStartupProfile =
 *   false`: their rules merge into `baseline-prof.txt` only, so those screens
 *   stay AOT-compiled for regular runs without claiming startup priority.
 *   Where the app offers a deep link (`android.intent.action.VIEW` on the
 *   `jellyplay://` scheme — the canonical destination table lives in
 *   DeepLinkGrammar), the pass launches it directly via an explicit intent;
 *   otherwise the gesture is best-effort and skips silently on devices where
 *   the surface isn't reachable (not signed in, onboarding, music/video mode,
 *   or TV form factor).
 *
 * Deliberately FEW passes: every `collect` block restarts the app, and each
 * cold launch of this app on the ciPixel8 GMD costs minutes of wall clock
 * (nine per-pass-cold-launch generations ran 37-44 min on-device). The
 * navigation pass therefore walks all top-level destinations inside ONE warm
 * process — profile recording is method-level, so classes executed via
 * onNewIntent navigation land in the profile exactly as they would from a
 * fresh launch; only the redundant cold-launch time is skipped.
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
     * Navigation + feeds pass: one warm-process circuit through every
     * top-level destination, replacing the nine per-screen cold-launch
     * passes this generator used to run (37-44 min on-device; see the class
     * doc). In order:
     *
     * - Home feed scroll — LazyColumn row composables, MediaImage + Coil
     *   decode pipeline, home ViewModel state machine.
     * - Deep-link circuit — library (with a grid scroll), search, settings,
     *   downloads (with a grid scroll). MainActivity is singleTask, so each
     *   link lands as onNewIntent + pending-route navigation in the same
     *   live process; backing out returns to the launcher and the next link
     *   warms the app again.
     * - Labeled surfaces with no deep-link host — Live TV (nav drawer /
     *   bottom bar) and the music-mode "Browse" tab, via guarded taps after
     *   a shell relaunch.
     *
     * Every step is best-effort: an unsigned fresh emulator simply consumes
     * the gestures and yields a narrower profile.
     */
    @Test
    fun generateNavigationAndFeedsProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Home feed rows.
            scrollHomeFeed()

            // Deep-link circuit through the top-level destinations.
            startDeepLinkAndWait(libraryDeepLink)
            scrollGrid()
            device.pressBack()
            device.waitForIdle()

            startDeepLinkAndWait(searchDeepLink)
            device.pressBack()
            device.waitForIdle()

            startDeepLinkAndWait(settingsDeepLink)
            device.pressBack()
            device.waitForIdle()

            startDeepLinkAndWait(downloadsDeepLink)
            scrollGrid()
            device.pressBack()
            device.waitForIdle()

            // Labeled surfaces: relaunch the shell so the nav entries are on
            // screen, then guarded-tap Live TV and the music-mode Browse tab.
            startActivityAndWait()
            device.waitForIdle()
            tapAndWait(liveTvLabel)
            tapAndWait(audioBrowseTabLabel)
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
 * [scrollHomeFeed], fewer iterations — grid screens are denser per swipe.
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
 * scrolling (that lives in the navigation pass's library leg). The label mirrors
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
