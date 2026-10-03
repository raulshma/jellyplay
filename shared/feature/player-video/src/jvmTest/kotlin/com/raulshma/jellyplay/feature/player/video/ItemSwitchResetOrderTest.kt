package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the load-bearing orderings of the item-switch reset. The harness
 * suites (VideoPlayerResetEquivalenceTest) pin the reset's OUTCOMES
 * leaf-by-leaf; the two ORDER constraints below are observable neither
 * through the final state (the navigator's reset writes the same episode
 * slice the rebuild just wiped, so both orders converge) nor through
 * injectable seams (the episode continuation controller and the session
 * teardown halves are wiring-built, so the EngineAttachControllerTest
 * callLog idiom does not reach them) — they are pinned at the source, the
 * ControllerOwnershipTest.constructionOrderConvention_wiringPhaseOneBeforeArm
 * precedent:
 *
 * 1. the [keepAcrossItems] rebuild runs BEFORE
 *    `episodeContinuation.resetForItemSwitch()` — the rebuild wipes the
 *    whole state (episode slice included) and the navigator, the episode
 *    slice's single writer, re-derives that slice from the post-rebuild
 *    state (the declared contract on [keepAcrossItems]);
 * 2. the session-owned teardown half runs immediately before the VM half
 *    (`hooks.releaseInternalsVmPart`) on BOTH teardown paths, back-to-back
 *    on the same synchronous call chain — a dispatch hop between them would
 *    let an interleaved recomposition flash the outgoing item's rebuilt
 *    stale title (the declared contract on
 *    VideoPlayerViewModel.releaseInternalsVmPart).
 *
 * The remaining reset calls in `releaseInternalsVmPart` are mutually
 * independent and deliberately NOT pinned — reordering them must stay free.
 */
class ItemSwitchResetOrderTest {

    private fun moduleSource(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(
            dir != null,
            "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}",
        )
        return File(dir!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/$fileName")
            .readText(Charsets.UTF_8)
    }

    @Test
    fun releaseInternalsVmPart_rebuildsUiState_beforeTheNavigatorResetsTheEpisodeSlice() {
        val vm = moduleSource("VideoPlayerViewModel.kt")
        val body = vm
            .substringAfter("private fun releaseInternalsVmPart()")
            .substringBefore("\n    fun release()")

        val rebuild = body.indexOf("_uiState.update { it.keepAcrossItems() }")
        val navigatorReset = body.indexOf("episodeContinuation.resetForItemSwitch()")
        assertTrue(rebuild >= 0, "the keepAcrossItems rebuild is missing from releaseInternalsVmPart")
        assertTrue(navigatorReset >= 0, "the episode-slice reset is missing from releaseInternalsVmPart")
        assertTrue(
            rebuild < navigatorReset,
            "the keepAcrossItems rebuild must run BEFORE episodeContinuation.resetForItemSwitch: the " +
                "rebuild wipes the whole state (episode slice included) and the navigator — the slice's " +
                "single writer — re-derives it from the post-rebuild state; a reset that ran first would " +
                "write into the outgoing state only for the rebuild to clobber it",
        )
    }

    @Test
    fun releaseInternals_runsSessionHalf_thenVmHalf_backToBack_onBothTeardownPaths() {
        val session = moduleSource("PlaybackSession.kt")
        // Only whitespace may sit between the session half's call and the VM
        // half's hook: the pair is one synchronous call chain on both the
        // per-item re-initialization path and the full-release path.
        val adjacency = Regex("releaseInternalsSessionPart\\(\\)\\s*\\n\\s*hooks\\.releaseInternalsVmPart\\(\\)")
        val sites = adjacency.findAll(session).toList()
        assertEquals(
            2,
            sites.size,
            "expected the session half immediately before the VM half on BOTH teardown paths " +
                "(per-item re-initialization + full release); a dispatch hop between the halves lets an " +
                "interleaved recomposition flash the outgoing item's rebuilt stale title. Found: " +
                sites.map { it.value },
        )
    }

    /** Direct pin: the whitelist IS the constructor. */
    @Test
    fun keepAcrossItems_carriesTheWhitelistLeaves_andResetsTheRest() {
        val seed = VideoPlayerUiState()
        val before = seed.copy(
            preferredPlayerType = PlayerType.EXTERNAL,
            playbackSpeed = 2.0f,
            subtitleStyle = SubtitleStyle(fontSize = 40),
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
            uiPrefs = seed.uiPrefs.copy(controlsTimeoutMs = 12_345L, showVideoStats = true),
            gestures = seed.gestures.copy(seekDurationMs = 20_000L, isHoldSpeedActive = true),
            segmentState = seed.segmentState.copy(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
            videoFx = seed.videoFx.copy(tvZoomModePercent = 25f),
            episodes = seed.episodes.copy(currentSeasonId = "season-1", isLoadingEpisodes = true),
        )

        val after = before.keepAcrossItems()

        // Whitelist leaves carry.
        assertEquals(PlayerType.EXTERNAL, after.preferredPlayerType)
        assertEquals(12_345L, after.uiPrefs.controlsTimeoutMs)
        assertEquals(20_000L, after.gestures.seekDurationMs)
        assertEquals(
            mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            after.segmentState.segmentBehaviors,
        )
        assertEquals(25f, after.videoFx.tvZoomModePercent)
        assertEquals(SubtitleStyle(fontSize = 40), after.subtitleStyle)

        // Everything else resets: slice defaults, the per-item dialogue-boost
        // zeroing, and the episode slice (the navigator re-derives it through
        // its seam right after the rebuild).
        assertEquals(1.0f, after.playbackSpeed)
        assertFalse(after.uiPrefs.showVideoStats)
        assertFalse(after.gestures.isHoldSpeedActive)
        assertTrue(after.segmentState.segments.isEmpty())
        assertEquals(VideoFxState(tvZoomModePercent = 25f), after.videoFx)
        assertFalse(after.dialogueBoostEnabled)
        assertEquals(EffectStrength.NONE, after.dialogueBoostStrength)
        assertEquals(EpisodeBrowserState(), after.episodes)
    }
}
