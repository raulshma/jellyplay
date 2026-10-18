package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Test
import java.io.File

/**
 * Wiring ratchet for the SyncPlay -> cast -> local SEEK precedence: the
 * routing must exist exactly ONCE, in the session-owned router —
 * [TransportRouter.seek] (the [TransportRouter.play] companion) — with the
 * VM's `SeekTo` event arm and `seekByStep` both one-line forwards onto it,
 * and the screen's `doSeekTo` a plain event forward. The fold collapsed two
 * copies of the same precedence ladder (the screen's `doSeekTo` remember
 * lambda and `seekByStep`'s when-ladder) into the one funnel — VM-side first,
 * then moved onto the session's router beside its PiP consumer — so the seek
 * bar, gesture commit, D-pad commit, chapter sheet, cast dashboard and PiP
 * SKIP steps can never route differently. The source ratchets pin the wiring
 * shape:
 *
 *  1. the VM's `SeekTo` / `TransportPlay` / `SeekByStep` / `RestartPlayback`
 *     event arms are one-line forwards onto the session's `transport` router
 *     (never straight to a transport arm);
 *  2. the router's `step` computes its step target and delegates to `seek` —
 *     no second ladder copy in its body;
 *  3. the screen's `doSeekTo` is a single `onEvent(SeekTo)` forward with no
 *     transport arms left anywhere in the screen file.
 *
 * The ladder itself is asserted behaviorally (recording arms), never by
 * source text: with recording doubles for the arms, every rung's routing —
 * SyncPlay group seek while the session mirror is up, cast seek while the
 * connection flow is connected, local seek otherwise, in that order — and the
 * local arm's gating (the user-initiated segment clamp; the SyncPlay/cast
 * arms bypass it deliberately) are pinned by the rig below. If a reformat
 * trips a regex here, the assertion message names the contract to restore —
 * never delete an assertion to make a reformat pass.
 */
class SeekTransportRoutingWiringTest {

    private fun moduleSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        val file = File(moduleRoot, relativePath)
        assertTrue(file.isFile, "source file not found at ${file.path}")
        return file.readText()
    }

    private val vmSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerViewModel.kt")
    }

    private val routerSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/TransportRouter.kt")
    }

    private val screenSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerScreen.kt")
    }

    private fun assertMatchesIn(source: String, regex: Regex, what: String) {
        assertTrue(regex.containsMatchIn(source), "seek routing drifted: $what")
    }

    @Test
    fun `the VM transport event arms are one-line forwards onto the session router`() {
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.SeekTo -> playbackSession\.transport\.seek\(event\.positionMs, userInitiated = true\)"""),
            "the SeekTo event must forward onto the session transport router (the ONE " +
                "precedence ladder), never straight to a transport arm",
        )
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.TransportPlay -> playbackSession\.transport\.play\(event\.play\)"""),
            "the TransportPlay event must forward onto the session transport router — the " +
                "play/pause press rides the same ladder as the seek",
        )
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.SeekByStep -> playbackSession\.transport\.step\(event\.direction\)"""),
            "the SeekByStep event must forward onto the session transport router's step funnel",
        )
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.RestartPlayback -> playbackSession\.transport\.restart\(\)"""),
            "the RestartPlayback event must forward onto the session transport router's local restart",
        )
    }

    @Test
    fun `the router seek is the only transport ladder`() {
        // The ladder arms are the injected per-arm effects; they must appear
        // exactly once — inside seek. A second copy is the divergence this
        // funnel exists to prevent.
        val ladderArms = listOf(
            Regex("""isInSyncPlaySession\(\) -> groupSeek\("""),
            Regex("""isCastConnected\(\) -> castSeek\("""),
        )
        for (arm in ladderArms) {
            assertEquals(
                1,
                arm.findAll(routerSource).count(),
                "the SyncPlay/cast seek arms must appear exactly once — inside the router's seek. " +
                    "A second copy is the divergence this funnel exists to prevent.",
            )
        }
    }

    @Test
    fun `step delegates to seek without its own ladder copy`() {
        val body = Regex("""fun step\(direction: Int\) \{[\s\S]*?\n    \}""")
            .find(routerSource)?.value
        assertTrue(body != null, "step body not found in the router")
        assertTrue(
            body.contains("seek("),
            "step must issue its step target through seek (the ONE ladder)",
        )
        assertTrue(
            body.contains("stepSeekTargetMs("),
            "step must keep computing its clamp math (stepSeekTargetMs) before routing",
        )
        assertFalse(
            body.contains("groupSeek(") || body.contains("castSeek("),
            "step must not carry its own SyncPlay/cast arms — route through seek instead",
        )
    }

    @Test
    fun `the screen doSeekTo is a plain event forward with no transport ladder`() {
        assertMatchesIn(
            screenSource,
            Regex(
                """val doSeekTo: \(Long\) -> Unit = remember \{[\s\S]*?""" +
                    """viewModel\.onEvent\(VideoPlayerUiEvent\.SeekTo\(ms\)\)[\s\S]*?\}""",
            ),
            "the screen's doSeekTo must forward the SeekTo event and let the session's " +
                "transport router own the transport precedence",
        )
        assertFalse(
            screenSource.contains("castSeekTo"),
            "the screen must not route casts seeks directly — no transport arms may remain " +
                "outside the session router",
        )
        assertFalse(
            screenSource.contains("syncPlay.seekTo"),
            "the screen must not route SyncPlay seeks directly — no transport arms may remain " +
                "outside the session router",
        )
    }

    /**
     * A minimal recording rig: the group/cast arms + the local arm's
     * persist effect append to [calls], so a single list asserts which
     * transport a press landed on (and which it never reached). The clamp
     * reads are inert (no segments, gate off) — the clamp math is pinned by
     * SegmentSeekClampTest and the source ratchets above; this rig exercises
     * the routing and the [TransportRouter.seekLocal] gating.
     */
    private fun routerWithCalls(
        calls: MutableList<String>,
        isInSyncPlaySession: Boolean = false,
        isCastConnected: Boolean = false,
    ) = TransportRouter(
        isInSyncPlaySession = { isInSyncPlaySession },
        isCastConnected = { isCastConnected },
        groupSeek = { calls += "groupSeek" },
        castSeek = { calls += "castSeek" },
        groupPlayPause = { calls += "groupPlayPause" },
        castPlay = { calls += "castPlay" },
        castPause = { calls += "castPause" },
        localPause = { calls += "localPause" },
        getEngine = { null },
        stepMs = { 10_000L },
        resumeSkipMs = { 0L },
        onUserInteraction = { calls += "onUserInteraction" },
        currentPositionMs = { 0L },
        durationMs = { 100_000L },
        segments = { emptyList() },
        segmentBehaviors = { emptyMap() },
        seekClampEnabled = { false },
        onSegmentSkippedNotice = { },
        persistSeek = { calls += "persistSeek" },
        writeDisplayedPosition = { },
    )

    @Test
    fun `seek while cast-connected never reaches the local engine seek`() {
        val calls = mutableListOf<String>()
        val router = routerWithCalls(calls, isCastConnected = true)

        router.seek(42_000L, userInitiated = true)

        assertEquals(listOf("castSeek"), calls, "a cast-connected seek must land on the cast " +
            "receiver arm ONLY — the local engine seek (its clamp + persist + display write " +
            "must never run)")
    }

    @Test
    fun `seek while in a syncplay session never reaches the local engine seek`() {
        val calls = mutableListOf<String>()
        val router = routerWithCalls(calls, isInSyncPlaySession = true)

        router.seek(42_000L, userInitiated = true)

        assertEquals(listOf("groupSeek"), calls, "a SyncPlay seek must land on the group arm " +
            "ONLY — the group owns the position, so the local clamp/persist/display write must " +
            "never run")
    }

    @Test
    fun `seek with syncplay and cast both up lands on the group arm`() {
        val calls = mutableListOf<String>()
        val router = routerWithCalls(calls, isInSyncPlaySession = true, isCastConnected = true)

        router.seek(42_000L, userInitiated = true)

        assertEquals(listOf("groupSeek"), calls, "with both remote transports live the group " +
            "rung must win — the SyncPlay-before-cast order means a press can never land on a " +
            "stale transport")
    }

    @Test
    fun `seek with no remote transport lands on the local arm`() {
        val calls = mutableListOf<String>()
        val router = routerWithCalls(calls)

        router.seek(42_000L, userInitiated = true)

        assertEquals(listOf("onUserInteraction", "persistSeek"), calls, "the local arm resets " +
            "the interaction clock, then persists + writes the display flow (no engine bound " +
            "here, so the engine seek no-ops)")
    }

    @Test
    fun `app-driven local seek does not reset the interaction clock`() {
        val calls = mutableListOf<String>()
        val router = routerWithCalls(calls)

        router.seekLocal(42_000L, userInitiated = false)

        assertEquals(listOf("persistSeek"), calls, "internal seeks (SyncPlay position sync, " +
            "resume-skip, auto-skip) must not touch the pass-out interaction clock")
    }
}
