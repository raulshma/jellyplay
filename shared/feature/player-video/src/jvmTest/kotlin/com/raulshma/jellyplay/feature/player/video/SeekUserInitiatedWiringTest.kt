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
 * dispatchSegmentSkip / executeSegmentSkip) were extracted into
 * SegmentDispatchController and have since been folded back into the same VM
 * (the pure decision halves stay in SegmentSkipPolicy.kt), so everything is
 * pinned in one source:
 *
 *  1. `seekTo` carries the `userInitiated: Boolean = true` parameter and only
 *     the user-initiated branch consults the clamp
 *     (`resolveForwardSeekSegmentClamp` + the `skipSegmentsOnSeek` gate);
 *  2. every app-driven internal caller passes `userInitiated = false` — the
 *     auto-skip execution, the SyncPlay group position sync and the
 *     resume-skip (A-B repeat seeks the engine directly and can never route
 *     through the funnel);
 *  3. the auto-skip arm (`VideoPlayerViewModel.autoSkipSegment`, the
 *     position-tick path) raises the "Skipped …" notice and seeks
 *     non-user-initiated, while the overlay-button arm
 *     (`VideoPlayerViewModel.skipSegment`) stays user-initiated.
 *
 * Since the [VideoPlayerViewModel] wiring moved into [PlayerWiring] (the
 * two-phase composition builder) and then into [PlaybackSession] (the C6
 * collapse made the session its own composition root), three anchors live in
 * the session's source instead of the VM's: the SyncPlay position-sync
 * lambda, the reporter's auto-skip routing and the aggregate-backed clamp
 * gate. The contract is unchanged — only the file the regex scans adapted.
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

    private val sessionSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/PlaybackSession.kt")
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
            Regex("""enabled = (wiring\.|playbackSession\.)?cachedAggregate\.videoPlayer\.skipSegmentsOnSeek"""),
            "the clamp must read the skip_segments_on_seek setting from the video-player store",
        )
    }

    @Test
    fun `auto-skip execution passes userInitiated = false`() {
        assertMatchesIn(
            vmSource,
            Regex("""fun autoSkipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = false\)"""),
            "the position-tick auto-skip is app-driven — its seek must not be clamped",
        )
    }

    @Test
    fun `syncplay group position sync passes userInitiated = false`() {
        // The lambda lives on the session's SyncPlayBridge wiring since the
        // PlayerWiring → PlaybackSession move; it routes through the VM's
        // seekTo funnel either way. Positional second arg (the flag on the
        // host lambdas is a lambda parameter — named args are prohibited for
        // function types).
        assertMatchesIn(
            sessionSource,
            Regex("""host\.seekTo\(positionTicks / 10_000, false\)"""),
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
        // executeSegmentSkip is the shared executor of the folded dispatch
        // arms; the flag must survive the hop into the seek funnel unchanged.
        assertMatchesIn(
            vmSource,
            Regex("""is SegmentSkipTarget\.SeekToPosition -> seekTo\(target\.positionMs, userInitiated\)"""),
            "executeSegmentSkip must forward its userInitiated flag to the seekTo funnel",
        )
    }

    @Test
    fun `overlay-button skip stays user-initiated`() {
        assertMatchesIn(
            vmSource,
            Regex("""fun skipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = true\)"""),
            "the overlay button press is a user action",
        )
    }

    @Test
    fun `the progress reporter routes auto-skips through the notice-raising arm`() {
        // The reporter's construction lives on the session since the
        // PlayerWiring → PlaybackSession move; the route still ends at the
        // VM's autoSkipSegment arm through the host seam.
        assertMatchesIn(
            sessionSource,
            Regex("""onAutoSkip = \{ segment -> host\.autoSkipSegment\(segment\) \}"""),
            "PlaybackProgressReporter's onAutoSkip must route to the VM's autoSkipSegment arm " +
                "(notice + non-user seek), not the button arm",
        )
        assertMatchesIn(
            vmSource,
            Regex("""fun autoSkipSegment[\s\S]*?showSkippedSegmentNotice\(segment\.type\)"""),
            "the auto-skip arm must raise the Skipped-segment notice",
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
