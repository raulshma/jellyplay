package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Wiring ratchet for the skip-on-forward-seek gate: the clamp must
 * apply ONLY to user-initiated seeks. [VideoPlayerViewModel] is too heavy to
 * construct in jvmTest (a 30+ dependency constructor), so — the
 * [ControllerOwnershipTest] precedent — this pins the wiring at the source.
 * The seek funnel and its internal callers live in [VideoPlayerViewModel];
 * the segment-skip dispatch arms (skipSegment / autoSkipSegment /
 * executeSegmentSkip) live in [SegmentDispatchController] since the
 * extraction (the facts snapshot + the VM wiring stay VM-side), so the two
 * sources are pinned together:
 *
 *  1. `seekTo` carries the `userInitiated: Boolean = true` parameter and only
 *     the user-initiated branch consults the clamp
 *     (`resolveForwardSeekSegmentClamp` + the `skipSegmentsOnSeek` gate);
 *  2. every app-driven internal caller passes `userInitiated = false` — the
 *     auto-skip execution, the SyncPlay group position sync and the
 *     resume-skip (A-B repeat seeks the engine directly and can never route
 *     through the funnel);
 *  3. the auto-skip arm (`SegmentDispatchController.autoSkipSegment`) raises
 *     the "Skipped …" notice (through the VM-wired `showSkippedNotice` seam)
 *     and seeks non-user-initiated, while the overlay-button arm
 *     (`SegmentDispatchController.skipSegment`) stays user-initiated.
 *
 * If a reformat trips a regex here, the assertion message names the contract
 * to restore — never delete an assertion to make a reformat pass.
 */
class SeekUserInitiatedWiringTest {

    private fun moduleSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        val file = File(moduleRoot!!, relativePath)
        assertTrue(file.isFile, "source file not found at ${file.path}")
        return file.readText()
    }

    private val vmSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerViewModel.kt")
    }

    private val dispatchSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/SegmentDispatchController.kt")
    }

    private fun assertMatchesIn(source: String, regex: Regex, what: String) {
        assertTrue(regex.containsMatchIn(source), "seek wiring drifted: $what")
    }

    @Test
    fun `seekTo carries the userInitiated parameter defaulting to true`() {
        assertMatchesIn(
            vmSource,
            Regex("""fun seekTo\(positionMs: Long, userInitiated: Boolean = true\)"""),
            "seekTo must keep the user-initiated default so every UI entry point (seek bar, " +
                "gesture commit, step buttons, restart) stays clamped",
        )
    }

    @Test
    fun `only the user-initiated branch consults the clamp`() {
        // The gate must sit INSIDE the userInitiated branch — internal seeks
        // never even resolve the clamp.
        assertMatchesIn(
            vmSource,
            Regex("""val effectiveTargetMs = if \(userInitiated\) \{\s*resolveForwardSeekSegmentClamp\("""),
            "the clamp resolution must live inside the userInitiated branch of seekTo",
        )
        assertMatchesIn(
            vmSource,
            Regex("""enabled = cachedAggregate\.videoPlayer\.skipSegmentsOnSeek"""),
            "the clamp must read the skip_segments_on_seek setting from the video-player store",
        )
    }

    @Test
    fun `auto-skip execution passes userInitiated = false`() {
        assertMatchesIn(
            dispatchSource,
            Regex("""fun autoSkipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = false\)"""),
            "the position-tick auto-skip is app-driven — its seek must not be clamped",
        )
    }

    @Test
    fun `syncplay group position sync passes userInitiated = false`() {
        assertMatchesIn(
            vmSource,
            Regex("""seekTo\(positionTicks / 10_000, userInitiated = false\)"""),
            "SyncPlay's group-driven position sync is not a user seek — never clamp it",
        )
    }

    @Test
    fun `resume-skip passes userInitiated = false`() {
        assertMatchesIn(
            vmSource,
            Regex("""seekTo\(\s*resumeSkipTargetMs\(currentPositionMs = engine\.currentPositionMs, skipMs = skipMs\),\s*userInitiated = false,"""),
            "the audio-focus/resume rewind is app-driven — never clamp it",
        )
    }

    @Test
    fun `segment-skip dispatch threads the flag through to seekTo`() {
        // The controller's seekTo is the VM-wired constructor lambda; the
        // flag must survive that hop, and the VM wiring must pass it through.
        assertMatchesIn(
            dispatchSource,
            Regex("""is SegmentSkipTarget\.SeekToPosition -> seekTo\(target\.positionMs, userInitiated\)"""),
            "executeSegmentSkip must forward its userInitiated flag to the seekTo seam",
        )
        assertMatchesIn(
            vmSource,
            Regex("""seekTo = \{ positionMs, userInitiated -> seekTo\(positionMs, userInitiated\) \}"""),
            "the VM's seekTo wiring must thread the controller's userInitiated flag into the funnel unchanged",
        )
    }

    @Test
    fun `overlay-button skip stays user-initiated`() {
        assertMatchesIn(
            dispatchSource,
            Regex("""fun skipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = true\)"""),
            "the overlay button press is a user action",
        )
    }

    @Test
    fun `the progress reporter routes auto-skips through the notice-raising arm`() {
        assertMatchesIn(
            vmSource,
            Regex("""onAutoSkip = \{ segment -> segmentDispatch\.autoSkipSegment\(segment\) \}"""),
            "PlaybackProgressReporter's onAutoSkip must route to the dispatch controller's autoSkipSegment " +
                "(notice + non-user seek), not the button arm",
        )
        assertMatchesIn(
            dispatchSource,
            Regex("""fun autoSkipSegment[\s\S]*?showSkippedNotice\(segment\.type\)"""),
            "the auto-skip arm must raise the Skipped-segment notice through its seam",
        )
        assertMatchesIn(
            vmSource,
            Regex("""showSkippedNotice = \{ segmentType -> showSkippedSegmentNotice\(segmentType\) \}"""),
            "the VM wiring must back the controller's notice seam with the real Skipped-segment notice",
        )
    }

    @Test
    fun `the clamp seek raises the Skipped-segment notice too`() {
        // [a] avoids the bell-character trap of \a — the raw string keeps
        // backslashes literal, so \a in a regex here would match BEL, not a dot.
        assertMatchesIn(
            vmSource,
            Regex("""\?\.[a]lso \{ showSkippedSegmentNotice\(it\.segmentType\) \}"""),
            "a skip-on-seek clamp must raise the Skipped-segment notice with the clamped segment's type",
        )
    }
}
