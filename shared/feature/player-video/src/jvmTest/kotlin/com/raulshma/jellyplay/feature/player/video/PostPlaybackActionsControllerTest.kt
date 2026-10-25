package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the #14 offer-delete-after-playback decision table
 * ([decidePostPlaybackDeleteOffer]: media type × prefs × incognito × offline
 * × permission × already-offered × item-bound) and the
 * [PostPlaybackActionsController] lifecycle around it: the offer arms only on
 * a NATURAL end, latches per item + session (never twice), a passing
 * evaluation HOLDS the end-of-media close (released by "Keep" or the
 * permission-read release; "Delete" closes outright), Keep dismisses for
 * good, Delete runs the server delete + cache invalidation and closes the
 * player, and a failed delete dismisses + surfaces without closing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostPlaybackActionsControllerTest {

    private val dispatcher = StandardTestDispatcher()

    /** The controller's Eagerly-shared scope — cancelled in teardown (the UpNextControllerTest pattern). */
    private val controllerScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val canDelete = MutableStateFlow(true)

    // Fakes + sinks.
    private var mediaType: MediaType? = MediaType.MOVIE
    private var itemId: String? = "item-1"
    private var itemTitle = "The Movie"
    private var seriesIdHint: String? = null
    private var incognito = false
    private var offline = false
    private var offerMovies = true
    private var offerEpisodes = false
    private var deleteResult: Result<Unit> = Result.success(Unit)
    private var closeRequests = 0
    private var closeAfterKeepRequests = 0

    private val deletedIds = mutableListOf<String>()
    private val invalidatedIds = mutableListOf<Pair<String, String?>>()
    private val failedNotices = mutableListOf<String>()

    private fun sources() = PostPlaybackDeleteSources(
        canDeleteContent = canDelete,
        deleteItem = { id ->
            deletedIds.add(id)
            deleteResult
        },
        invalidateAfterDelete = { id, hint -> invalidatedIds.add(id to hint) },
    )

    private fun controller(sources: PostPlaybackDeleteSources? = sources()) =
        PostPlaybackActionsController(
            scope = controllerScope,
            sources = sources,
            getMediaKind = { mediaType },
            getItemId = { itemId },
            getItemTitle = { itemTitle },
            getSeriesIdHint = { seriesIdHint },
            isIncognito = { incognito },
            isOfflinePlayback = { offline },
            offerForMovies = { offerMovies },
            offerForEpisodes = { offerEpisodes },
            closePlayer = { closeRequests++ },
            closeAfterKeep = { closeAfterKeepRequests++ },
            notifyDeleteFailed = { title -> failedNotices.add(title) },
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        controllerScope.cancel()
        Dispatchers.resetMain()
    }

    // ── the decision table ─────────────────────────────────────────────────

    private fun inputs(
        mediaType: MediaType? = MediaType.MOVIE,
        offerMovies: Boolean = true,
        offerEpisodes: Boolean = false,
        incognito: Boolean = false,
        offline: Boolean = false,
        canDelete: Boolean = true,
        alreadyOffered: Boolean = false,
        itemId: String? = "item-1",
    ) = PostPlaybackOfferInputs(
        itemId = itemId,
        mediaType = mediaType,
        offerMovies = offerMovies,
        offerEpisodes = offerEpisodes,
        incognito = incognito,
        offline = offline,
        canDelete = canDelete,
        alreadyOffered = alreadyOffered,
    )

    @Test
    fun `decision - movie offered under the movie pref`() {
        assertTrue(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.MOVIE, offerMovies = true)))
    }

    @Test
    fun `decision - movie pref off suppresses`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.MOVIE, offerMovies = false)))
    }

    @Test
    fun `decision - episode offered only under the episode pref`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.EPISODE, offerEpisodes = false)))
        assertTrue(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.EPISODE, offerEpisodes = true)))
    }

    @Test
    fun `decision - episode pref does not offer movies and vice versa`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.MOVIE, offerMovies = false, offerEpisodes = true)))
        assertFalse(decidePostPlaybackDeleteOffer(inputs(mediaType = MediaType.EPISODE, offerMovies = true, offerEpisodes = false)))
    }

    @Test
    fun `decision - non-leaf media types never offer`() {
        for (type in listOf(MediaType.SERIES, MediaType.SEASON, MediaType.ALBUM, null)) {
            assertFalse(decidePostPlaybackDeleteOffer(inputs(mediaType = type, offerMovies = true, offerEpisodes = true)))
        }
    }

    @Test
    fun `decision - incognito suppresses`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(incognito = true)))
    }

    @Test
    fun `decision - offline playback suppresses`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(offline = true)))
    }

    @Test
    fun `decision - missing delete permission suppresses`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(canDelete = false)))
    }

    @Test
    fun `decision - already offered suppresses and so does an unbound item`() {
        assertFalse(decidePostPlaybackDeleteOffer(inputs(alreadyOffered = true)))
        assertFalse(decidePostPlaybackDeleteOffer(inputs(itemId = null)))
    }

    // ── the controller lifecycle ───────────────────────────────────────────

    @Test
    fun `natural end arms the offer for a pref-matching movie`() = runTest(dispatcher) {
        val c = controller()

        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        val offer = c.deleteOffer.value
        assertTrue(offer != null, "the offer arms")
        assertEquals("item-1", offer!!.itemId)
        assertEquals("The Movie", offer.title)
    }

    @Test
    fun `keep dismisses and the latch blocks a second offer for the same item`() = runTest(dispatcher) {
        val c = controller()
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        c.keepItem()
        assertNull(c.deleteOffer.value)

        // A later natural end of the SAME item cannot resurrect the offer.
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()
        assertNull(c.deleteOffer.value, "never twice for the same item + session")
    }

    @Test
    fun `a new item may offer again`() = runTest(dispatcher) {
        val c = controller()
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()
        c.keepItem()

        itemId = "item-2"
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        assertEquals("item-2", c.deleteOffer.value?.itemId, "the latch is per item")
    }

    @Test
    fun `suppressed sessions never arm the offer`() = runTest(dispatcher) {
        val c = controller()

        incognito = true
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()
        assertNull(c.deleteOffer.value, "incognito never offers")

        incognito = false
        offline = true
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()
        assertNull(c.deleteOffer.value, "offline playback never offers")

        offline = false
        offerMovies = false
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()
        assertNull(c.deleteOffer.value, "the pref gates the offer")
    }

    @Test
    fun `missing delete permission suppresses the armed precheck`() = runTest(dispatcher) {
        val c = controller()
        canDelete.value = false

        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        assertNull(c.deleteOffer.value, "the permission read gates the offer")
    }

    @Test
    fun `delete runs the server delete path with cache invalidation and closes the player`() =
        runTest(dispatcher) {
            seriesIdHint = "series-1"
            val c = controller()
            c.onPlaybackEndedNaturally()
            advanceUntilIdle()

            c.deleteOfferedItem()
            advanceUntilIdle()

            assertEquals(listOf("item-1"), deletedIds, "the existing MediaInfoApiClient.deleteItem path runs")
            assertEquals(
                listOf<Pair<String, String?>>("item-1" to "series-1"),
                invalidatedIds,
                "the cache invalidation carries the series hint",
            )
            assertEquals(1, closeRequests, "the just-deleted item's player closes")
            assertNull(c.deleteOffer.value, "the dialog is dismissed")
            assertTrue(failedNotices.isEmpty())

            // The latch holds: no re-offer for the deleted item.
            c.onPlaybackEndedNaturally()
            advanceUntilIdle()
            assertNull(c.deleteOffer.value)
        }

    @Test
    fun `failed delete dismisses the dialog surfaces the failure and stays open`() = runTest(dispatcher) {
        deleteResult = Result.failure(IllegalStateException("boom"))
        val c = controller()
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        c.deleteOfferedItem()
        advanceUntilIdle()

        assertEquals(0, closeRequests, "the player stays open on a failed delete")
        assertNull(c.deleteOffer.value, "the dialog dismisses")
        assertEquals(listOf("The Movie"), failedNotices, "the failure is surfaced")
    }

    // ── the end-of-media close hold ────────────────────────────────────────

    @Test
    fun `a natural end that arms the offer holds the close`() = runTest(dispatcher) {
        val c = controller()

        val holds = c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        assertTrue(holds, "the armed offer owns the end-of-media close (the dialog lives in the player screen)")
        assertTrue(c.deleteOffer.value != null)
        assertEquals(0, closeAfterKeepRequests, "the hold is only released by an answer or a release")
    }

    @Test
    fun `a suppressed end never holds the close`() = runTest(dispatcher) {
        val c = controller()

        // Each arm gets a fresh item id: the latch registers on EVALUATION
        // (before the gates), so a reused id would only pin the latch.
        incognito = true
        assertFalse(c.onPlaybackEndedNaturally(), "incognito: the caller's normal close proceeds")

        itemId = "item-2"
        incognito = false
        offerMovies = false
        assertFalse(c.onPlaybackEndedNaturally(), "the pref gate: the caller's normal close proceeds")

        itemId = "item-3"
        offerMovies = true
        mediaType = MediaType.SERIES
        assertFalse(c.onPlaybackEndedNaturally(), "a non-leaf type: the caller's normal close proceeds")

        itemId = "item-4"
        mediaType = MediaType.MOVIE
        assertTrue(c.onPlaybackEndedNaturally())
        advanceUntilIdle()
        c.keepItem()
        itemId = null
        assertFalse(c.onPlaybackEndedNaturally(), "an unbound item: the caller's normal close proceeds")
        itemId = "item-4"
        assertFalse(c.onPlaybackEndedNaturally(), "the latch: never twice for the same item + session")
    }

    @Test
    fun `keep releases the hold exactly once and a keep with nothing pending fires nothing`() =
        runTest(dispatcher) {
            val c = controller()

            // No pending offer yet: the keep is a no-op.
            c.keepItem()
            assertEquals(0, closeAfterKeepRequests)

            c.onPlaybackEndedNaturally()
            advanceUntilIdle()
            c.keepItem()
            assertEquals(1, closeAfterKeepRequests, "the deferred end-of-media close fires on Keep")

            // The latch holds — a later keep cannot double-fire.
            c.keepItem()
            assertEquals(1, closeAfterKeepRequests)
        }

    @Test
    fun `a failed permission read releases the hold without raising`() = runTest(dispatcher) {
        val c = controller()
        canDelete.value = false

        val holds = c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        assertTrue(holds, "the precheck held the close before the async permission read")
        assertNull(c.deleteOffer.value, "no offer without the permission")
        assertEquals(1, closeAfterKeepRequests, "the hold releases — the natural close resumes")
    }

    // ── twin end events (Issue #10: the natural end dispatches twice) ─────

    @Test
    fun `a twin end event of an armed item keeps holding the close until resolved`() = runTest(dispatcher) {
        val c = controller()

        // The reporter's end-of-stream safety net fires first and arms.
        assertTrue(c.onPlaybackEndedNaturally(), "first end arms and holds")
        advanceUntilIdle()

        // The engine's genuine ENDED lands ~500 ms later: the twin must not
        // release the hold the first event placed (the player used to close
        // out from under the just-armed dialog here).
        assertTrue(c.onPlaybackEndedNaturally(), "the twin end of the SAME armed item still holds")
        advanceUntilIdle()
        assertEquals(0, closeAfterKeepRequests, "the hold outlives the twin event")
        assertTrue(c.deleteOffer.value != null, "the offer survives the twin event")

        // Resolution ends the ownership: a post-resolution twin proceeds.
        c.keepItem()
        assertEquals(1, closeAfterKeepRequests, "Keep releases the hold exactly once")
        assertFalse(c.onPlaybackEndedNaturally(), "a twin after resolution proceeds with the normal close")
    }

    @Test
    fun `a twin end event of a suppressed item never holds`() = runTest(dispatcher) {
        val c = controller()

        incognito = true
        assertFalse(c.onPlaybackEndedNaturally(), "suppressed: no hold")
        assertFalse(c.onPlaybackEndedNaturally(), "the twin of a suppressed end also never holds")
    }

    @Test
    fun `a failed permission read ends the armed ownership for late twins`() = runTest(dispatcher) {
        val c = controller()
        canDelete.value = false

        assertTrue(c.onPlaybackEndedNaturally(), "the precheck holds before the permission read")
        advanceUntilIdle()
        assertEquals(1, closeAfterKeepRequests, "the hold released")

        assertFalse(c.onPlaybackEndedNaturally(), "a twin after the released hold proceeds normally")
    }

    @Test
    fun `no delete sources - the feature is inert`() = runTest(dispatcher) {
        val c = controller(sources = null)

        assertFalse(c.onPlaybackEndedNaturally(), "no seams, no hold")
        c.onPlaybackEndedNaturally()
        advanceUntilIdle()

        assertNull(c.deleteOffer.value, "no seams, no offer")
    }

    @Test
    fun `commands without a pending offer are no-ops`() = runTest(dispatcher) {
        val c = controller()

        c.keepItem()
        c.deleteOfferedItem()
        advanceUntilIdle()

        assertTrue(deletedIds.isEmpty(), "nothing to delete")
        assertEquals(0, closeRequests)
        assertEquals(0, closeAfterKeepRequests)
    }
}
