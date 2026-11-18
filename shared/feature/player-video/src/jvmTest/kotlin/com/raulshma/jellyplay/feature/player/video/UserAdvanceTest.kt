package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.VideoQueueItem
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The manual advance verb's behavioral suite ([PlaybackSession.userAdvance],
 * the [UserAdvance] variants): the six user-driven "move to another item"
 * arms, folded out of the ViewModel's when-body into ONE session
 * choreography. Per variant this pins
 *
 *  1. the shared invariant — [AutoPlayController.onUserInteraction] runs
 *     (the still-watching engagement streak resets) BEFORE the variant's own
 *     dispatch;
 *  2. the variant's routing, observed where it lands: FreshOpen through the
 *     full open funnel's mapped [LoadRequest] (the pipeline mock records it),
 *     Episode through the picker funnel's request, Previous through the
 *     sibling resolution, Next and QueueItem through the queue-first advance,
 *     WatchedAndSkip through the mark + close;
 *  3. the binge-streak ownership — ONLY FreshOpen resets UpNext's unattended
 *     streak explicitly; the other variants ride their UpNext entry points'
 *     own resets (the Next arm's reset stays INSIDE UpNext — a source pin
 *     below keeps it that way).
 *
 * Conventions match [PlaybackSessionLifecycleTest]: the session's injected
 * scope is unconfined on the test scheduler (synchronous launches), the
 * pipeline is the recording double, the whole construction graph comes from
 * [PlaybackSessionTestGraph].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserAdvanceTest {

    private lateinit var graph: PlaybackSessionTestGraph
    private lateinit var session: PlaybackSession
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var hooks: RecordingHooks
    private val startedRequests = mutableListOf<LoadRequest>()

    private fun TestScope.buildSession(
        currentItemId: String? = null,
        playSessionId: String? = null,
    ) {
        hooks = RecordingHooks()
        graph = PlaybackSessionTestGraph(
            testScope = this,
            hooks = hooks,
            initialItem = currentItemId,
            initialPlaySessionId = playSessionId,
        )
        session = graph.session
        sessionStateFlow = graph.sessionStateFlow
        every { graph.pipeline.start(any(), any()) } answers {
            startedRequests += secondArg<LoadRequest>()
            hooks.calls += "startPipeline"
            Job()
        }
    }

    @AfterTest
    fun tearDown() {
        // The owner's teardown path: cancel the session's internally-built
        // release scope, then the session scope (cancel-after-release).
        if (this::graph.isInitialized) {
            session.releaseScope.cancel()
            graph.sessionScope.cancel()
        }
    }

    // ── 1. The shared invariant: onUserInteraction on every variant ─────────

    @Test
    fun userAdvance_resetsTheStillWatchingStreak_onEveryVariant() = runTest {
        buildSession()
        val variants = listOf<UserAdvance>(
            UserAdvance.FreshOpen(itemId = "item-a", mediaSourceId = null, startPositionTicks = 0L, subtitleStreamIndex = null, audioStreamIndex = null, forceStream = false),
            UserAdvance.Episode(episodeId = "ep-a", startPositionTicks = 0L),
            UserAdvance.Previous,
            UserAdvance.Next,
            UserAdvance.WatchedAndSkip,
            UserAdvance.QueueItem(index = 0),
        )
        for (variant in variants) {
            // Grow the unattended engagement streak past an armed threshold:
            // the prompt verdict is TRUE, and the advance must reset it.
            session.autoplayController.setStillWatchingThreshold(1)
            session.autoplayController.recordAutoAdvance()
            assertTrue(
                session.autoplayController.needsStillWatchingCheck(),
                "precondition: the streak reached the armed threshold ($variant)",
            )

            session.userAdvance(variant)

            assertFalse(
                session.autoplayController.needsStillWatchingCheck(),
                "a user-driven advance resets the still-watching engagement streak ($variant)",
            )
        }
    }

    // ── 2. Per-variant routing ──────────────────────────────────────────────

    @Test
    fun freshOpen_advancesThroughTheFullOpenFunnel_withTheMappedRequest() = runTest {
        buildSession()

        session.userAdvance(
            UserAdvance.FreshOpen(
                itemId = "item-9",
                mediaSourceId = "ms-9",
                startPositionTicks = 30_000_000L,
                subtitleStreamIndex = 2,
                audioStreamIndex = 1,
                forceStream = true,
            ),
        )

        // The pipeline receives the open funnel's LoadRequest — the six
        // arguments mapped verbatim (cinema allowed, no forced override).
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "item-9",
                    mediaSourceId = "ms-9",
                    startPositionTicks = 30_000_000L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = 2,
                    audioStreamIndex = 1,
                    forceStream = true,
                ),
            ),
            startedRequests.toList(),
        )
    }

    @Test
    fun freshOpen_additionallyResetsTheBingeStreak() = runTest {
        buildSession()
        armBingeLimit()

        // A prior AUTO advance grew the binge streak past the cap…
        session.upNext.advanceUnattended()
        testScheduler.runCurrent()
        assertTrue(session.upNext.isAutoplayLimitReached.value, "precondition: the cap is reached")

        // …and a fresh player open restarts it (the one variant that resets
        // it explicitly beside the load).
        session.userAdvance(
            UserAdvance.FreshOpen(itemId = "item-9", mediaSourceId = null, startPositionTicks = 0L, subtitleStreamIndex = null, audioStreamIndex = null, forceStream = false),
        )
        testScheduler.runCurrent()

        assertFalse(
            session.upNext.isAutoplayLimitReached.value,
            "a user-driven player open resets the autoplay-limit binge streak (#17)",
        )
    }

    @Test
    fun freshOpen_resetsTheEngagementStreak_BEFORETheOpenFunnelRuns() = runTest {
        buildSession()
        session.autoplayController.setStillWatchingThreshold(1)
        session.autoplayController.recordAutoAdvance()
        assertTrue(
            session.autoplayController.needsStillWatchingCheck(),
            "precondition: the streak reached the armed threshold",
        )

        // Capture the engagement state the instant the open funnel starts:
        // the reset must already have landed there, i.e. the engagement reset
        // is ordered BEFORE the variant's verb (the initialize funnel), not
        // merely somewhere inside the arm.
        var engagementAtFunnelStart: Boolean? = null
        every { graph.pipeline.start(any(), any()) } answers {
            engagementAtFunnelStart = session.autoplayController.needsStillWatchingCheck()
            startedRequests += secondArg<LoadRequest>()
            hooks.calls += "startPipeline"
            Job()
        }

        session.userAdvance(
            UserAdvance.FreshOpen(itemId = "item-9", mediaSourceId = null, startPositionTicks = 0L, subtitleStreamIndex = null, audioStreamIndex = null, forceStream = false),
        )
        testScheduler.runCurrent()

        assertNotNull(engagementAtFunnelStart, "the open funnel started")
        assertFalse(
            engagementAtFunnelStart,
            "the still-watching engagement reset is ordered before the open funnel",
        )
    }

    @Test
    fun episode_advancesThroughThePickerFunnel_withTheMappedRequest() = runTest {
        buildSession()

        session.userAdvance(UserAdvance.Episode(episodeId = "ep-9", startPositionTicks = 5_000_000L))

        // playEpisode routes the picker funnel: a plain load request for the
        // picked episode (no media-source pin, resume from the given ticks).
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "ep-9",
                    mediaSourceId = null,
                    startPositionTicks = 5_000_000L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                ),
            ),
            startedRequests.toList(),
        )
    }

    @Test
    fun previous_advancesToThePreviousSibling_fromItsSavedPosition() = runTest {
        buildSession(currentItemId = "ep-2")
        seedEpisodeAnchor()

        session.userAdvance(UserAdvance.Previous)
        testScheduler.runCurrent()

        // The sibling resolution lands the previous episode's plain load —
        // resumed from its saved position (the picker funnel's semantics).
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "ep-1",
                    mediaSourceId = null,
                    startPositionTicks = 7_000_000L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                ),
            ),
            startedRequests.toList(),
        )
    }

    @Test
    fun next_routesTheManualArm_throughTheQueueHead() = runTest {
        buildSession()
        armBingeLimit()
        session.videoQueue.enqueueLast(VideoQueueItem(itemId = "q-head", title = "Queued"))

        // A prior AUTO advance grew the binge streak past the cap…
        session.upNext.advanceUnattended()
        testScheduler.runCurrent()
        assertTrue(session.upNext.isAutoplayLimitReached.value, "precondition: the cap is reached")

        session.userAdvance(UserAdvance.Next)
        testScheduler.runCurrent()

        // The manual arm plays the queue head (queue-first advance)…
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "q-head",
                    mediaSourceId = null,
                    startPositionTicks = 0L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                ),
            ),
            startedRequests.toList(),
        )
        // …and its UpNext-side reset restarted the binge streak — the reset
        // stays INSIDE UpNext (no duplicate on the session verb; the source
        // pin below keeps it that way).
        assertFalse(session.upNext.isAutoplayLimitReached.value, "the manual play-next restarts the streak")
    }

    @Test
    fun watchedAndSkip_marksPlayed_thenClosesWhenNoNext() = runTest {
        buildSession(currentItemId = "item-1", playSessionId = "server-1")
        armBingeLimit()

        session.userAdvance(UserAdvance.WatchedAndSkip)
        testScheduler.runCurrent()

        // No episode sibling and no queue head: the mark lands through the
        // same mutator the watched-threshold arm uses, then the player closes.
        coVerify(exactly = 1) { graph.userDataMutator.setPlayed("item-1", played = true) }
        assertEquals(Unit, graph.handles.closePlayer.tryReceive().getOrNull())
        assertTrue(startedRequests.isEmpty(), "no advance load behind the close")
        // The deliberate-advance reset is UpNext-side (markWatchedAndSkip).
        assertFalse(session.upNext.isAutoplayLimitReached.value, "the deliberate advance restarts the streak")
    }

    @Test
    fun queueItem_playsThePickedRow() = runTest {
        buildSession()
        armBingeLimit()
        session.videoQueue.enqueueLast(VideoQueueItem(itemId = "q-1", title = "First"))
        session.videoQueue.enqueueLast(VideoQueueItem(itemId = "q-2", title = "Second"))

        session.userAdvance(UserAdvance.QueueItem(index = 1))
        testScheduler.runCurrent()

        // The picked ROW plays (not the head) — the manual queue-row funnel.
        assertEquals(
            listOf(
                LoadRequest(
                    itemId = "q-2",
                    mediaSourceId = null,
                    startPositionTicks = 0L,
                    allowCinemaMode = true,
                    subtitleStreamIndex = null,
                    audioStreamIndex = null,
                ),
            ),
            startedRequests.toList(),
        )
    }

    // ── 3. The binge-streak ownership pin (source-level) ────────────────────

    @Test
    fun userAdvance_bingeStreakReset_livesOnlyInFreshOpen() {
        // The ordering/dead-code class of pins: the ItemSwitchResetOrderTest
        // precedent. The session verb must reset the binge streak EXACTLY
        // once — in the FreshOpen arm; the other variants rely on their
        // UpNext entry points' own resets, and a duplicated reset on the
        // verb would hide a future UpNext-side regression instead of
        // failing a pin.
        val source = moduleSource("PlaybackSession.kt")
        val body = source
            .substringAfter("fun userAdvance(variant: UserAdvance)")
            .substringBefore("fun initialize(request: LoadRequest)")
        val resets = Regex("resetAutoAdvanceStreak").findAll(body).toList()
        assertEquals(1, resets.size, "userAdvance resets the binge streak in exactly one place")
        val freshOpenArm = body.substringBefore("is UserAdvance.Episode")
        assertTrue(
            freshOpenArm.contains("resetAutoAdvanceStreak"),
            "the binge-streak reset must live in the FreshOpen arm (the full open funnel), not the shared prefix",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Caps the autoplay episode limit at 1 — the binge-streak observability seed. */
    private fun armBingeLimit() {
        graph.aggregateFlow.value = VideoPlayerAggregate(
            videoPlayer = VideoPlayerSlice(autoplayEpisodeLimit = 1),
        )
    }

    /**
     * Seeds the previous-sibling resolution the Previous variant resolves
     * through: a series/season anchor on the playing item plus the catalogue's
     * two-episode season (ep-1 carries a saved position).
     */
    private fun seedEpisodeAnchor() {
        session.mediaDetail = MediaDetail(
            item = MediaItem(
                id = "ep-2",
                name = "Episode Two",
                mediaType = MediaType.EPISODE,
                seriesId = "series-1",
                seasonId = "season-1",
            ),
        )
        coEvery {
            graph.episodeCatalogue.loadSeasonEpisodes("series-1", "season-1", false)
        } returns Result.success(
            listOf(
                MediaItem(
                    id = "ep-1",
                    name = "Episode One",
                    mediaType = MediaType.EPISODE,
                    seriesId = "series-1",
                    seasonId = "season-1",
                    playbackPositionTicks = 7_000_000L,
                ),
                MediaItem(
                    id = "ep-2",
                    name = "Episode Two",
                    mediaType = MediaType.EPISODE,
                    seriesId = "series-1",
                    seasonId = "season-1",
                ),
            ),
        )
    }

    private fun moduleSource(fileName: String): String {
        var dir = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !java.io.File(dir, "src/commonMain/kotlin").isDirectory) dir = dir.parentFile
        assertTrue(dir != null, "could not locate src/commonMain/kotlin")
        return java.io.File(dir!!, "src/commonMain/kotlin/com/raulshma/jellyplay/feature/player/video/$fileName")
            .readText(Charsets.UTF_8)
    }

    /** Records the VM-bound hook invocations the session makes (the LifecycleTest shape). */
    private class RecordingHooks : SessionLifecycleHooks {
        val calls = mutableListOf<String>()

        override fun rearmTransports() { calls += "rearmTransports" }

        override fun resetForNewItem(selection: com.raulshma.jellyplay.core.model.MediaStreamSelection) {
            calls += "resetForNewItem"
        }

        override fun routeToRemotePlaySession(request: LoadRequest): Boolean {
            calls += "routeToRemotePlaySession"
            return false
        }

        override fun tryReclaimMiniPlayer(itemId: String): MediaEngine? {
            calls += "tryReclaimMiniPlayer"
            return null
        }

        override fun onMiniPlayerReclaimed() { calls += "onMiniPlayerReclaimed" }

        override fun hydrateReclaimedItem(itemId: String, detail: MediaDetail) {
            calls += "hydrateReclaimedItem"
        }

        override fun releaseMiniPlayerState() { calls += "releaseMiniPlayerState" }

        override fun releaseInternalsVmPart() { calls += "releaseInternalsVmPart" }

        override fun clearTrickplay() { calls += "clearTrickplay" }

        override fun reattachSyncPlay() { calls += "reattachSyncPlay" }

        override fun wasInSyncPlay(): Boolean {
            calls += "wasInSyncPlay"
            return false
        }
    }
}
