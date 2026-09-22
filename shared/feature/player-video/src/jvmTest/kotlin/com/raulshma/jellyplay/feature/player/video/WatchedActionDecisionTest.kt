package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The exhaustive matrix for the mark-and-exit overflow actions' decision
 * ladder ([PlayerScreenPolicies.decideWatchedActions]). Pins the
 * incognito arms (offline-local mark for "watched", no mark at all for
 * "unwatched") and the SyncPlay advance rule (the navigator's group-queue
 * routing applies even without a locally-resolved sibling) without
 * constructing the ViewModel.
 */
class WatchedActionDecisionTest {

    private fun decide(
        hasNext: Boolean = false,
        incognito: Boolean = false,
        isInSyncPlay: Boolean = false,
    ) = decideWatchedActions(
        hasNext = hasNext,
        incognito = incognito,
        isInSyncPlay = isInSyncPlay,
    )

    // ── The mark path (watched arm) ─────────────────────────────────────────

    @Test
    fun watchedMark_onlineGoesThroughTheServerMutation() {
        // The same UserDataMutator.setPlayed path the watched-threshold
        // callback uses — SeenMediaRepository is deliberately not consulted.
        assertEquals(WatchedMarkPath.SERVER, decide().watchedMarkPath)
        assertEquals(WatchedMarkPath.SERVER, decide(hasNext = true).watchedMarkPath)
        assertEquals(WatchedMarkPath.SERVER, decide(isInSyncPlay = true).watchedMarkPath)
    }

    @Test
    fun watchedMark_incognitoStaysLocalOnly() {
        // OfflinePlaybackFacade.recordPlayed — never the server, never an
        // outbox row (the threshold callback's incognito invariant).
        assertEquals(WatchedMarkPath.OFFLINE_LOCAL, decide(incognito = true).watchedMarkPath)
        assertEquals(WatchedMarkPath.OFFLINE_LOCAL, decide(incognito = true, hasNext = true).watchedMarkPath)
    }

    // ── The watched action's advance-vs-close branch ────────────────────────

    @Test
    fun watched_withNextEpisode_advances() {
        assertTrue(decide(hasNext = true).watchedAdvancesToNext)
    }

    @Test
    fun watched_withoutNextEpisode_closesThePlayer() {
        assertFalse(decide(hasNext = false).watchedAdvancesToNext)
    }

    @Test
    fun watched_inSyncPlay_advancesEvenWithoutALocalSibling() {
        // The group queue may hold the next item when the local adjacency has
        // not resolved it: playNextEpisode() routes through the group either
        // way, so a SyncPlay session always tries the advance verb.
        assertTrue(decide(hasNext = false, isInSyncPlay = true).watchedAdvancesToNext)
        assertTrue(decide(hasNext = true, isInSyncPlay = true).watchedAdvancesToNext)
    }

    @Test
    fun watched_incognitoAdvancesExactlyLikeNormal() {
        // Incognito changes WHERE the mark goes, never the advance branch.
        assertEquals(
            decide(hasNext = true).watchedAdvancesToNext,
            decide(hasNext = true, incognito = true).watchedAdvancesToNext,
        )
        assertEquals(
            decide(hasNext = false).watchedAdvancesToNext,
            decide(hasNext = false, incognito = true).watchedAdvancesToNext,
        )
    }

    // ── The unwatched mark (the quit arm) ───────────────────────────────────

    @Test
    fun unwatched_onlineMarksUnplayed() {
        assertTrue(decide().unwatchedMarkApplied)
        assertTrue(decide(hasNext = true).unwatchedMarkApplied)
        assertTrue(decide(isInSyncPlay = true).unwatchedMarkApplied)
    }

    @Test
    fun unwatched_incognitoIsANoOpMark() {
        // Incognito exists to leave no watch state — including clearing one.
        // The player still exits (the VM closes regardless of this flag).
        assertFalse(decide(incognito = true).unwatchedMarkApplied)
        assertFalse(decide(incognito = true, isInSyncPlay = true).unwatchedMarkApplied)
    }

    // ── The full four-combo headline matrix (hasNext × incognito) ───────────

    @Test
    fun headlineMatrix_hasNextTimesIncognito() {
        val expected = listOf(
            // hasNext, incognito → (markPath, advances, unwatchedApplied)
            listOf(WatchedMarkPath.SERVER, true, true),        // online, next
            listOf(WatchedMarkPath.SERVER, false, true),       // online, no next
            listOf(WatchedMarkPath.OFFLINE_LOCAL, true, false), // incognito, next
            listOf(WatchedMarkPath.OFFLINE_LOCAL, false, false), // incognito, no next
        )
        val actual = listOf(
            decide(hasNext = true, incognito = false),
            decide(hasNext = false, incognito = false),
            decide(hasNext = true, incognito = true),
            decide(hasNext = false, incognito = true),
        ).map { listOf(it.watchedMarkPath, it.watchedAdvancesToNext, it.unwatchedMarkApplied) }
        assertEquals(expected, actual)
    }
}
