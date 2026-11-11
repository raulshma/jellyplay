package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Pins the player's error state machine after the collapse of the former
 * three-field representation (message + retryable flag + dialog flag) into
 * the single [VideoPlayerUiState.playbackError] value ([PlaybackErrorUi]).
 * The point of the collapse: "silent error" (message without the dialog) and
 * "blank dialog" (dialog without a message) were representable before; with
 * one nullable value they are not — non-null IS the dialog-on-screen state.
 *
 * Three pins:
 *
 *  1. the SET path, non-watchdog arm: the session-event forwarder folds a
 *     `SessionEvent.ShowError` into one `PlaybackErrorUi(message, retryable)`
 *     write and leaves the buffering sibling untouched — behavioral, through
 *     the real coordinator → decision executor → event pipe → forwarder chain
 *     on [PlaybackSessionTestGraph] with the session ARMED (the forwarder is
 *     an `arm()` collector — production arms it in the VM's `init`; the
 *     lifecycle suites observe the event pipe without arming, only this fold
 *     needs it);
 *  2. the SET path, watchdog arm: the start-up watchdog's ShowError — the one
 *     carrying `clearBuffering` — lowers `isBuffering` in the SAME write that
 *     raises the error, exactly as the old triple-plus-sibling write did;
 *  3. the DISMISS path: the VM's `clearPlaybackErrorState` funnel is the
 *     single null write. The funnel is VM-private and the VM harness's load
 *     path is engine-free (no ShowError can be raised end-to-end there), so
 *     the funnel is pinned at the source — the
 *     ItemSwitchResetOrderTest/ControllerOwnershipTest precedent for
 *     constraints no injectable seam reaches — together with a sweep
 *     asserting the legacy field names survive NOWHERE in the module's main
 *     sources (the machine has one field, one set, one clear).
 *
 * The across-item reset of the error value (it is not a `keepAcrossItems`
 * whitelist leaf) is pinned in ItemSwitchResetOrderTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackErrorUiStateTest {

    private lateinit var graph: PlaybackSessionTestGraph
    private lateinit var session: PlaybackSession
    private lateinit var engine: FakeMediaEngine

    private fun TestScope.buildArmedSession() {
        graph = PlaybackSessionTestGraph(
            testScope = this,
            hooks = NoOpErrorHooks(),
            initialItem = null,
            initialPlaySessionId = null,
        )
        session = graph.session
        engine = graph.engine
        session.arm()
        testScheduler.runCurrent()
    }

    @AfterTest
    fun tearDown() {
        // Cancel the session's internally-built release scope AFTER its work
        // was verified, then the session scope — the owner's teardown path,
        // exactly as the PlaybackSessionLifecycleTest convention.
        if (this::graph.isInitialized) {
            session.releaseScope.cancel()
            graph.sessionScope.cancel()
        }
    }

    @Test
    fun sessionShowError_foldsMessageAndRetryVerdict_intoOnePlaybackErrorValue() = runTest {
        buildArmedSession()
        // Seed the buffering sibling so the test proves the NON-watchdog arm
        // leaves it exactly where it was (only the watchdog's ShowError
        // lowers it — the next test).
        graph.uiState.update { it.copy(isBuffering = true) }

        engine.errorEmissions.tryEmit(EngineError.Network(null))
        testScheduler.runCurrent()

        // ONE value carries everything the old triple carried: non-null IS
        // the dialog-on-screen state (the dialog gate reads exactly this),
        // with the structured retry verdict riding inside it.
        assertEquals(
            PlaybackErrorUi(message = EngineError.Network(null).message, retryable = true),
            graph.uiState.value.playbackError,
            "the ShowError fold writes the structured message + retry verdict as one value; " +
                "a silent error (message without dialog) is no longer representable",
        )
        assertTrue(
            graph.uiState.value.isBuffering,
            "a non-watchdog error must NOT touch the buffering sibling",
        )
    }

    @Test
    fun watchdogTimeout_showError_lowersBuffering_inTheSameWriteThatRaisesTheError() = runTest {
        buildArmedSession()
        graph.uiState.update { it.copy(isBuffering = true) }

        engine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000L) // EngineEventCoordinator's BUFFERING_TIMEOUT_MS
        testScheduler.runCurrent()

        assertEquals(
            PlaybackErrorUi(message = EngineError.Timeout().message, retryable = true),
            graph.uiState.value.playbackError,
            "the watchdog's ShowError raises the dialog state",
        )
        assertFalse(
            graph.uiState.value.isBuffering,
            "the start-up watchdog must lift the stuck buffering spinner in the same " +
                "write that raises the error (the clearBuffering arm)",
        )
    }

    @Test
    fun dismissalFunnel_isTheSingleNullWrite_andTheLegacyFieldNamesAreGone() {
        val vm = moduleSource("VideoPlayerViewModel.kt")
        val funnel = vm
            .substringAfter("private fun clearPlaybackErrorState()")
            .substringBefore("}")

        assertTrue(
            "it.copy(playbackError = null)" in funnel,
            "the dismissal funnel must be the single null write on the collapsed " +
                "playbackError value",
        )
        listOf("playerError", "showPlaybackErrorDialog", "playerErrorRetryable").forEach { legacy ->
            assertFalse(
                legacy in vm,
                "the legacy field name `$legacy` must not survive anywhere in the VM — " +
                    "the error state is the single playbackError value now",
            )
        }

        // And nowhere in the module's main sources either: one field, one set
        // (the session fold), one clear (the funnel above). The component-local
        // `errorMessage` inputs are NOT the state machine and stay free to
        // carry message strings.
        listOf(
            "PlaybackSession.kt",
            "VideoPlayerUiState.kt",
            "VideoPlayerScreen.kt",
            "VideoPlayerScreenSheets.kt",
            "VideoPlayerScreenInfo.kt",
            "VideoPlayerScreenOverlays.kt",
            "KeepAcrossItems.kt",
            "components/PlaybackInfoOverlay.kt",
        ).forEach { file ->
            val source = moduleSource(file)
            listOf("playerError", "showPlaybackErrorDialog", "playerErrorRetryable").forEach { legacy ->
                assertFalse(
                    legacy in source,
                    "$file still references the legacy error-state field name `$legacy` — " +
                        "the machine is the single playbackError value now",
                )
            }
        }
    }

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

    /** Silent hooks: this suite drives the error fold, none of the hook choreography. */
    private class NoOpErrorHooks : SessionLifecycleHooks {
        override fun rearmTransports() = Unit
        override fun resetForNewItem(selection: MediaStreamSelection) = Unit
        override fun routeToRemotePlaySession(request: LoadRequest): Boolean = false
        override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? = null
        override fun onMiniPlayerReclaimed() = Unit
        override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) = Unit
        override fun releaseMiniPlayerState() = Unit
        override fun releaseInternalsVmPart() = Unit
        override fun clearTrickplay() = Unit
        override fun reattachSyncPlay() = Unit
        override fun wasInSyncPlay(): Boolean = false
    }
}
