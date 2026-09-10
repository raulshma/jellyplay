package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins [HomeDialogSession]'s event CASCADES and dismiss-clear ORDERING — the
 * choreography that used to live as untested LaunchedEffect/onDismiss bodies
 * in HomeScreen.
 */
class HomeDialogSessionTest {

    /** Fake onEvent: records every fired event in order for sequence assertions. */
    private fun recordingSession(events: MutableList<HomeUiEvent>): HomeDialogSession =
        HomeDialogSession { event -> events += event }

    private fun outboxEntry(itemId: String) = PlaybackOutboxEntry(
        id = "outbox-$itemId",
        itemId = itemId,
        eventType = PlaybackOutboxEventType.PROGRESS,
        sessionId = "session-1",
        positionTicks = 0L,
        isPaused = false,
        playMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId = null,
        recordedAt = 0L,
        createdAt = 0L,
    )

    @Test
    fun openSeerrRequest_tv_firesServiceDetailsThenTvSeasons() {
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.openSeerrRequest(SeerrSearchItem(id = 42, mediaType = "tv"))

        assertEquals(
            listOf(
                HomeUiEvent.LoadSeerrServiceDetails("tv"),
                HomeUiEvent.LoadTvSeasons(42),
            ),
            events,
        )
    }

    @Test
    fun openSeerrRequest_tvMixedCase_stillLoadsSeasons_andForwardsTypeVerbatim() {
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.openSeerrRequest(SeerrSearchItem(id = 7, mediaType = "TV"))

        assertEquals(
            listOf(
                // The mediaType reaches the service-details load EXACTLY as the
                // item carried it; only the season gate is case-insensitive.
                HomeUiEvent.LoadSeerrServiceDetails("TV"),
                HomeUiEvent.LoadTvSeasons(7),
            ),
            events,
        )
    }

    @Test
    fun openSeerrRequest_movie_firesServiceDetailsOnly() {
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.openSeerrRequest(SeerrSearchItem(id = 9, mediaType = "movie"))

        assertEquals(listOf<HomeUiEvent>(HomeUiEvent.LoadSeerrServiceDetails("movie")), events)
    }

    @Test
    fun dismissSeerrRequest_clearsItemThenResult_inThatOrder() {
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.dismissSeerrRequest()

        assertEquals(
            listOf(HomeUiEvent.SelectSeerrRequestItem(null), HomeUiEvent.ClearRequestResult),
            events,
        )
    }

    @Test
    fun syncSheetOpened_mapsEntriesToItemIds_preservingOrder() {
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.syncSheetOpened(
            listOf(outboxEntry("c"), outboxEntry("a"), outboxEntry("b")),
        )

        assertEquals(
            listOf<HomeUiEvent>(HomeUiEvent.EnsurePendingItemDetails(listOf("c", "a", "b"))),
            events,
        )
    }

    @Test
    fun syncSheetOpened_emptyEntries_stillFiresWithEmptyIds() {
        // Today's behavior: the screen's while-open effect fires unconditionally
        // (the sheet-open `if` is the gate, and it stays there), so an open sheet
        // with nothing queued resolves an empty id set. The session preserves that.
        val events = mutableListOf<HomeUiEvent>()
        val session = recordingSession(events)

        session.syncSheetOpened(emptyList())

        assertEquals(
            listOf<HomeUiEvent>(HomeUiEvent.EnsurePendingItemDetails(emptyList())),
            events,
        )
    }
}
