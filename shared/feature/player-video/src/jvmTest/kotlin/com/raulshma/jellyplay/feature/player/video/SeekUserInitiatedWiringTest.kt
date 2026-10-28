package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

/**
 * Wiring guard for the skip-on-forward-seek gate: the clamp must
 * apply ONLY to user-initiated seeks. This pins the wiring at the source
 * (the [ControllerOwnershipTest] precedent). The seek funnel moved from the
 * ViewModel onto the session's router — the local arm body is
 * [TransportRouter.seekLocal] (its every read arrives as the ctor lambdas the
 * session wiring supplies), the resume-skip funnel is
 * [TransportRouter.applyResumeSkip] — and the
 * segment-skip dispatch arms (skipSegment / autoSkipSegment /
 * dispatchSegmentSkip / executeSegmentSkip) have since moved onto the session
 * beside it (the pure decision halves stay in SegmentSkipPolicy.kt), so the
 * guard is pinned across the three sources:
 *
 *  1. the router's `seekLocal` carries the `userInitiated: Boolean`
 *     parameter and only the user-initiated branch consults the clamp
 *     (`resolveForwardSeekSegmentClamp` + the `skipSegmentsOnSeek` gate);
 *  2. the VM's UI event arm forwards `userInitiated = true` (every UI entry
 *     point — seek bar, gesture commit, step buttons, restart — stays
 *     clamped), while every app-driven caller passes `userInitiated = false`
 *     — the auto-skip execution, the SyncPlay group position sync and the
 *     resume-skip (A-B repeat seeks the engine directly and can never route
 *     through the funnel);
 *  3. the auto-skip arm (`PlaybackSession.autoSkipSegment`, the
 *     position-tick path) raises the "Skipped …" notice and seeks
 *     non-user-initiated, while the overlay-button arm
 *     (`PlaybackSession.skipSegment`) stays user-initiated.
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

    private val routerSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/TransportRouter.kt")
    }

    private fun assertMatchesIn(source: String, regex: Regex, what: String) {
        assertTrue(regex.containsMatchIn(source), "seek wiring drifted: $what")
    }

    @Test
    fun `the router seekLocal carries the userInitiated parameter`() {
        assertMatchesIn(
            routerSource,
            Regex("""fun seekLocal\(positionMs: Long, userInitiated: Boolean\)"""),
            "the local seek arm must keep the user-initiated flag so the clamp can gate on it — " +
                "the VM's UI event arm forwards userInitiated = true so every UI entry point " +
                "(seek bar, gesture commit, step buttons, restart) stays clamped",
        )
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.SeekTo -> playbackSession\.transport\.seek\(event\.positionMs, userInitiated = true\)"""),
            "the SeekTo event arm must forward user-initiated so every UI entry point stays clamped",
        )
    }

    @Test
    fun `only the user-initiated branch consults the clamp`() {
        // The gate must sit INSIDE the userInitiated branch — internal seeks
        // never even resolve the clamp.
        assertMatchesIn(
            routerSource,
            Regex("""val effectiveTargetMs = if \(userInitiated\) \{\s*resolveForwardSeekSegmentClamp\("""),
            "the clamp resolution must live inside the userInitiated branch of the router's seekLocal",
        )
        assertMatchesIn(
            routerSource,
            Regex("""seekClampEnabled = \{ cachedAggregate\.videoPlayer\.skipSegmentsOnSeek \}"""),
            "the clamp must read the skip_segments_on_seek setting from the video-player store",
        )
    }

    @Test
    fun `auto-skip execution passes userInitiated = false`() {
        assertMatchesIn(
            sessionSource,
            Regex("""fun autoSkipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = false\)"""),
            "the position-tick auto-skip is app-driven — its seek must not be clamped",
        )
    }

    @Test
    fun `syncplay group position sync passes userInitiated = false`() {
        // The lambda lives on the session's SyncPlayBridge wiring; it routes
        // through the session's transport router (the local arm) either way.
        // Positional second arg (the flag on the arm is a lambda parameter —
        // named args are prohibited for function types).
        assertMatchesIn(
            sessionSource,
            Regex("""transport\.seekLocal\(positionTicks / 10_000, false\)"""),
            "SyncPlay's group-driven position sync is not a user seek — never clamp it",
        )
    }

    @Test
    fun `resume-skip passes userInitiated = false`() {
        assertMatchesIn(
            routerSource,
            Regex("""seekLocal\(\s*resumeSkipTargetMs\(currentPositionMs = engine\.currentPositionMs, skipMs = skipMs\),\s*false,"""),
            "the audio-focus/resume rewind is app-driven — never clamp it",
        )
    }

    @Test
    fun `segment-skip dispatch threads the flag through to the local seek arm`() {
        // executeSegmentSkip is the shared executor of the folded dispatch
        // arms; the flag must survive the hop into the local seek funnel
        // unchanged.
        assertMatchesIn(
            sessionSource,
            Regex("""is SegmentSkipTarget\.SeekToPosition ->\s*transport\.seekLocal\(target\.positionMs, userInitiated\)"""),
            "executeSegmentSkip must forward its userInitiated flag to the session's local seek arm",
        )
    }

    @Test
    fun `overlay-button skip stays user-initiated`() {
        assertMatchesIn(
            sessionSource,
            Regex("""fun skipSegment\(segment: MediaSegment\) \{\s*executeSegmentSkip\(.*?, userInitiated = true\)"""),
            "the overlay button press is a user action",
        )
    }

    @Test
    fun `the progress reporter routes auto-skips through the notice-raising arm`() {
        // The reporter's construction lives on the session since the
        // PlayerWiring → PlaybackSession move; since the end-of-media +
        // segment-skip fold moved in as well, the route is a session-internal
        // self-reference to the autoSkipSegment arm.
        assertMatchesIn(
            sessionSource,
            Regex("""onAutoSkip = \{ segment -> autoSkipSegment\(segment\) \}"""),
            "PlaybackProgressReporter's onAutoSkip must route to the session's autoSkipSegment arm " +
                "(notice + non-user seek), not the button arm",
        )
        assertMatchesIn(
            sessionSource,
            Regex("""fun autoSkipSegment[\s\S]*?showSkippedSegmentNotice\(segment\.type\)"""),
            "the auto-skip arm must raise the Skipped-segment notice",
        )
    }

    @Test
    fun `the clamp seek raises the Skipped-segment notice too`() {
        // [a] avoids the bell-character trap of \a — the raw string keeps
        // backslashes literal, so \a in a regex here would match BEL, not a dot.
        // The notice is session-owned (the auto-skip arm raises the same one);
        // the router's clamp fires it through the wiring's narrow lambda seam.
        assertMatchesIn(
            routerSource,
            Regex("""\?\.[a]lso \{ onSegmentSkippedNotice\(it\.segmentType\) \}"""),
            "a skip-on-seek clamp must raise the Skipped-segment notice with the clamped segment's type",
        )
    }
}
