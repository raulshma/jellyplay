package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Test
import java.io.File

/**
 * Wiring ratchet for the SyncPlay -> cast -> local SEEK precedence: the
 * routing must exist exactly ONCE, in the ViewModel's transport funnel —
 * [VideoPlayerViewModel.routedSeek], the [routedPlay] companion — with the
 * `SeekTo` event arm and `seekByStep` both delegating to it, and the
 * screen's `doSeekTo` a plain event forward. The fold collapsed two copies
 * of the same precedence ladder (the screen's `doSeekTo` remember lambda and
 * `seekByStep`'s when-ladder) into the one VM-side funnel, so the seek bar,
 * gesture commit, D-pad commit, chapter sheet, cast dashboard and PiP SKIP
 * steps can never route differently. [VideoPlayerViewModel] is too heavy to
 * construct in jvmTest (a 30+ dependency constructor), so — the
 * [SeekUserInitiatedWiringTest] precedent — this pins the wiring at the
 * source:
 *
 *  1. the `SeekTo` event arm routes through `routedSeek` (never straight to
 *     the local `seekTo`);
 *  2. `routedSeek` carries the full precedence IN ORDER — SyncPlay group
 *     seek while `_uiState`'s session mirror is up, cast seek while the
 *     controller's connection flow is connected, local seek otherwise (the
 *     local arm keeps the user-initiated segment clamp; the SyncPlay/cast
 *     arms bypass it deliberately);
 *  3. `seekByStep` computes its step target and delegates to `routedSeek` —
 *     no second ladder copy in its body;
 *  4. the screen's `doSeekTo` is a single `onEvent(SeekTo)` forward with no
 *     transport arms left anywhere in the screen file.
 *
 * If a reformat trips a regex here, the assertion message names the contract
 * to restore — never delete an assertion to make a reformat pass.
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

    private val screenSource: String by lazy {
        moduleSource("src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/VideoPlayerScreen.kt")
    }

    private fun assertMatchesIn(source: String, regex: Regex, what: String) {
        assertTrue(regex.containsMatchIn(source), "seek routing drifted: $what")
    }

    @Test
    fun `the SeekTo event arm routes through the transport funnel`() {
        assertMatchesIn(
            vmSource,
            Regex("""is VideoPlayerUiEvent\.SeekTo -> routedSeek\(event\.positionMs\)"""),
            "the SeekTo event must route through routedSeek (the transport funnel), never " +
                "straight to the local seekTo — the funnel arm is what keeps the event, the " +
                "step buttons and the screen on one precedence ladder",
        )
    }

    @Test
    fun `routedSeek carries the full syncPlay cast local precedence in order`() {
        assertMatchesIn(
            vmSource,
            Regex(
                """private fun routedSeek\(positionMs: Long[^)]*\) \{\s*""" +
                    """when \{\s*""" +
                    """_uiState\.value\.isInSyncPlaySession -> (playbackSession\.)?syncPlay\.seekTo\(positionMs\)\s*""" +
                    """(playbackSession\.)?cast\.isConnectedFlow\.value -> (playbackSession\.)?cast\.castSeekTo\(positionMs\)\s*""" +
                    """else -> seekTo\(positionMs\)\s*""" +
                    """\}""",
            ),
            "routedSeek must keep the A1 order — SyncPlay group seek (uiState session mirror) " +
                "first, cast seek (live connection flow) second, local engine seek last — so a " +
                "seek can never land on a stale transport",
        )
    }

    @Test
    fun `routedSeek is the only transport ladder in the ViewModel`() {
        // The controllers moved into the session at the C6 collapse, so the
        // ladder arms read them through the qualified playbackSession handle.
        val ladderArms = listOf(
            Regex("""isInSyncPlaySession\s*->\s*(playbackSession\.)?syncPlay\.seekTo\("""),
            Regex("""(playbackSession\.)?cast\.isConnectedFlow\.value\s*->\s*(playbackSession\.)?cast\.castSeekTo\("""),
        )
        for (arm in ladderArms) {
            assertEquals(
                1,
                arm.findAll(vmSource).count(),
                "the SyncPlay/cast seek arms must appear exactly once — inside routedSeek. " +
                    "A second copy is the divergence this funnel exists to prevent.",
            )
        }
    }

    @Test
    fun `seekByStep delegates to routedSeek without its own ladder copy`() {
        val body = Regex("""private fun seekByStep\(direction: Int\) \{[\s\S]*?\n    \}""")
            .find(vmSource)?.value
        assertTrue(body != null, "seekByStep body not found in the ViewModel")
        assertTrue(
            body.contains("routedSeek("),
            "seekByStep must issue its step target through routedSeek (the ONE ladder)",
        )
        assertTrue(
            body.contains("stepSeekTargetMs("),
            "seekByStep must keep computing its clamp math (stepSeekTargetMs) before routing",
        )
        assertFalse(
            body.contains("syncPlay.seekTo") || body.contains("cast.castSeekTo"),
            "seekByStep must not carry its own SyncPlay/cast arms — route through routedSeek instead",
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
            "the screen's doSeekTo must forward the SeekTo event and let the ViewModel's " +
                "routedSeek own the transport precedence",
        )
        assertFalse(
            screenSource.contains("castSeekTo"),
            "the screen must not route casts seeks directly — no transport arms may remain " +
                "outside the ViewModel funnel",
        )
        assertFalse(
            screenSource.contains("syncPlay.seekTo"),
            "the screen must not route SyncPlay seeks directly — no transport arms may remain " +
                "outside the ViewModel funnel",
        )
    }
}
