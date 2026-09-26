package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Wiring ratchet for the skip-on-forward-seek gate: the clamp must
 * apply ONLY to user-initiated seeks. [VideoPlayerViewModel] is too heavy to
 * construct in jvmTest (a 30+ dependency constructor), so — the
 * [ControllerOwnershipTest] precedent — this pins the wiring at the source:
 *
 *  1. `seekTo` carries the `userInitiated: Boolean = true` parameter and only
 *     the user-initiated branch consults the clamp
 *     (`resolveForwardSeekSegmentClamp` + the `skipSegmentsOnSeek` gate);
 *  2. every app-driven internal caller passes `userInitiated = false` — the
 *     auto-skip execution, the SyncPlay group position sync and the
 *     resume-skip (A-B repeat seeks the engine directly and can never route
 *     through the funnel);
 *  3. the auto-skip arm (`autoSkipSegment`) raises the "Skipped …" notice and
 *     seeks non-user-initiated, while the overlay-button arm
 *     (`skipSegment`) stays user-initiated.
 *
 * If a reformat trips a regex here, the assertion message names the contract
 * to restore — never delete an assertion to make a reformat pass.
 */
class SeekUserInitiatedWiringTest {

    private val source: String by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        val vmFile = File(moduleRoot!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerViewModel.kt")
        assertTrue(vmFile.isFile, "VideoPlayerViewModel.kt not found at ${vmFile.path}")
        vmFile.readText()
    }

    private fun assertMatches(regex: Regex, what: String) {
        assertTrue(regex.containsMatchIn(source), "seek wiring drifted: $what")
    }

    @Test
    fun `seekTo carries the userInitiated parameter defaulting to true`() {
        assertMatches(
            Regex("""fun seekTo\(positionMs: Long, userInitiated: Boolean = true\)"""),
            "seekTo must keep the user-initiated default so every UI entry point (seek bar, " +
                "gesture commit, step buttons, restart) stays clamped",
        )
    }

    @Test
    fun `only the user-initiated branch consults the clamp`() {
        // The gate must sit INSIDE the userInitiated branch — internal seeks
        // never even resolve the clamp.
        assertMatches(
            Regex("""val effectiveTargetMs = if \(userInitiated\) \{\s*resolveForwardSeekSegmentClamp\("""),
            "the clamp resolution must live inside the userInitiated branch of seekTo",
        )
        assertMatches(
            Regex("""enabled = cachedAggregate\.videoPlayer\.skipSegmentsOnSeek"""),
            "the clamp must read the skip_segments_on_seek setting from the video-player store",
        )
    }

    @Test
    fun `auto-skip execution passes userInitiated = false`() {
        assertMatches(
            Regex("""private fun autoSkipSegment\(segment: com\.raulshma\.jellyplay\.core\.model\.MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = false\)"""),
            "the position-tick auto-skip is app-driven — its seek must not be clamped",
        )
    }

    @Test
    fun `syncplay group position sync passes userInitiated = false`() {
        assertMatches(
            Regex("""seekTo\(positionTicks / 10_000, userInitiated = false\)"""),
            "SyncPlay's group-driven position sync is not a user seek — never clamp it",
        )
    }

    @Test
    fun `resume-skip passes userInitiated = false`() {
        assertMatches(
            Regex("""seekTo\(\s*resumeSkipTargetMs\(currentPositionMs = engine\.currentPositionMs, skipMs = skipMs\),\s*userInitiated = false,"""),
            "the audio-focus/resume rewind is app-driven — never clamp it",
        )
    }

    @Test
    fun `segment-skip dispatch threads the flag through to seekTo`() {
        assertMatches(
            Regex("""is SegmentSkipTarget\.SeekToPosition -> seekTo\(target\.positionMs, userInitiated\)"""),
            "executeSegmentSkip must forward its userInitiated flag to seekTo",
        )
    }

    @Test
    fun `overlay-button skip stays user-initiated`() {
        assertMatches(
            Regex("""fun skipSegment\(segment: com\.raulshma\.jellyplay\.core\.model\.MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = true\)"""),
            "the overlay button press is a user action",
        )
    }

    @Test
    fun `the progress reporter routes auto-skips through the notice-raising arm`() {
        assertMatches(
            Regex("""onAutoSkip = \{ segment -> autoSkipSegment\(segment\) \}"""),
            "PlaybackProgressReporter's onAutoSkip must route to autoSkipSegment (notice + non-user seek), not the button arm",
        )
        assertMatches(
            Regex("""private fun autoSkipSegment[\s\S]*?showSkippedSegmentNotice\(segment\.type\)"""),
            "the auto-skip arm must raise the Skipped-segment notice",
        )
    }

    @Test
    fun `the clamp seek raises the Skipped-segment notice too`() {
        // [a] avoids the bell-character trap of \a — the raw string keeps
        // backslashes literal, so \a in a regex here would match BEL, not a dot.
        assertMatches(
            Regex("""\?\.[a]lso \{ showSkippedSegmentNotice\(it\.segmentType\) \}"""),
            "a skip-on-seek clamp must raise the Skipped-segment notice with the clamped segment's type",
        )
    }
}
