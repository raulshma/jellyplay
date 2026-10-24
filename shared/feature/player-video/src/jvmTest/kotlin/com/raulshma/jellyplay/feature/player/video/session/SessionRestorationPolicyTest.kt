package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import com.raulshma.jellyplay.feature.player.video.STALE_POSITION_THRESHOLD_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the cold-start session-restoration table ([SessionRestorationPolicy.kt])
 * and the server-row → snapshot mapping ([latestResumeVideoSnapshot]) — the
 * full mode × snapshot × launch-context grid plus the 1 h staleness boundary,
 * all over explicit instants (no wall clock).
 */
class SessionRestorationPolicyTest {

    private val now = 1_800_000_000_000L
    private val freshStamp = now - 5 * 60_000L // 5 min ago
    private val staleStamp = now - STALE_POSITION_THRESHOLD_MS - 1L

    private fun snapshot(
        itemId: String = "item-1",
        positionMs: Long = 600_000L,
        timestampMs: Long? = freshStamp,
    ) = SessionRestorationSnapshot(
        itemId = itemId,
        title = "Episode 1",
        positionMs = positionMs,
        timestampMs = timestampMs,
    )

    private fun decide(
        mode: SessionRestoreMode,
        snapshot: SessionRestorationSnapshot?,
        launchContext: LaunchContext,
    ): SessionRestorationDecision = decideSessionRestoration(mode, snapshot, launchContext, now)

    // ── Rule 1: OFF → None always ────────────────────────────────────────

    @Test
    fun off_discardsEvenAFreshSnapshot() {
        assertEquals(SessionRestorationDecision.None, decide(SessionRestoreMode.OFF, snapshot(), LaunchContext.ColdStart))
    }

    @Test
    fun off_withNothingToRestore_isNone() {
        assertEquals(SessionRestorationDecision.None, decide(SessionRestoreMode.OFF, null, LaunchContext.ColdStart))
    }

    // ── Rule 2: an explicit destination is never hijacked ────────────────

    @Test
    fun ask_fresh_deeplinkLaunch_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.ASK, snapshot(), LaunchContext.DeepLink),
        )
    }

    @Test
    fun ask_fresh_externalPlaybackIntent_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.ASK, snapshot(), LaunchContext.ExternalPlaybackIntent),
        )
    }

    @Test
    fun ask_fresh_alreadyInPlayer_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.ASK, snapshot(), LaunchContext.AlreadyInPlayer),
        )
    }

    @Test
    fun auto_fresh_deeplinkLaunch_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.AUTO, snapshot(), LaunchContext.DeepLink),
        )
    }

    @Test
    fun auto_fresh_alreadyInPlayer_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.AUTO, snapshot(), LaunchContext.AlreadyInPlayer),
        )
    }

    // ── Rule 3: nothing to restore → None ────────────────────────────────

    @Test
    fun ask_withNothingToRestore_isNone() {
        assertEquals(SessionRestorationDecision.None, decide(SessionRestoreMode.ASK, null, LaunchContext.ColdStart))
    }

    @Test
    fun auto_withNothingToRestore_isNone() {
        assertEquals(SessionRestorationDecision.None, decide(SessionRestoreMode.AUTO, null, LaunchContext.ColdStart))
    }

    // ── Rule 4: the 1 h staleness mirror ─────────────────────────────────

    @Test
    fun ask_staleSnapshot_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.ASK, snapshot(timestampMs = staleStamp), LaunchContext.ColdStart),
        )
    }

    @Test
    fun auto_staleSnapshot_isNone() {
        assertEquals(
            SessionRestorationDecision.None,
            decide(SessionRestoreMode.AUTO, snapshot(timestampMs = staleStamp), LaunchContext.ColdStart),
        )
    }

    @Test
    fun ask_exactlyAtTheThreshold_isFresh() {
        // `now - stamp == threshold` is NOT "older than" — the boundary stays fresh.
        assertEquals(
            SessionRestorationDecision.Ask(snapshot(timestampMs = now - STALE_POSITION_THRESHOLD_MS)),
            decide(SessionRestoreMode.ASK, snapshot(timestampMs = now - STALE_POSITION_THRESHOLD_MS), LaunchContext.ColdStart),
        )
    }

    @Test
    fun ask_missingStamp_isTreatedFresh() {
        // Mirrors resolveResumeTicks: a zero/missing timestamp never blocks a restore.
        assertEquals(
            SessionRestorationDecision.Ask(snapshot(timestampMs = null)),
            decide(SessionRestoreMode.ASK, snapshot(timestampMs = null), LaunchContext.ColdStart),
        )
    }

    @Test
    fun ask_zeroStamp_isTreatedFresh() {
        assertEquals(
            SessionRestorationDecision.Ask(snapshot(timestampMs = 0L)),
            decide(SessionRestoreMode.ASK, snapshot(timestampMs = 0L), LaunchContext.ColdStart),
        )
    }

    // ── Rule 5: the ASK / AUTO arms ──────────────────────────────────────

    @Test
    fun ask_freshColdStart_asksWithTheSnapshot() {
        val s = snapshot()
        assertEquals(SessionRestorationDecision.Ask(s), decide(SessionRestoreMode.ASK, s, LaunchContext.ColdStart))
    }

    @Test
    fun auto_freshColdStart_autoRestoresWithTheSnapshot() {
        val s = snapshot()
        assertEquals(SessionRestorationDecision.AutoRestore(s), decide(SessionRestoreMode.AUTO, s, LaunchContext.ColdStart))
    }

    // ── the snapshot factory: server rows → candidate ────────────────────

    private fun row(
        id: String,
        type: MediaType,
        positionTicks: Long? = 600_000_000L,
        lastPlayed: String? = "2026-01-01T12:00:00Z",
    ) = MediaItem(
        id = id,
        name = "Row $id",
        mediaType = type,
        playbackPositionTicks = positionTicks,
        lastPlayedDate = lastPlayed,
    )

    @Test
    fun factory_picksTheMostRecentlyDatedVideoRow() {
        val chosen = latestResumeVideoSnapshot(
            listOf(
                row("older", MediaType.MOVIE, lastPlayed = "2026-01-01T12:00:00Z"),
                row("newer", MediaType.EPISODE, lastPlayed = "2026-01-02T12:00:00Z"),
            ),
        )
        assertEquals("newer", chosen?.itemId)
    }

    @Test
    fun factory_skipsSeriesRollupsBooksAndAudioRows() {
        val chosen = latestResumeVideoSnapshot(
            listOf(
                row("series", MediaType.SERIES),
                row("season", MediaType.SEASON),
                row("book", MediaType.BOOK),
                row("audio", MediaType.AUDIO),
                row("episode", MediaType.EPISODE),
            ),
        )
        assertEquals("episode", chosen?.itemId)
    }

    @Test
    fun factory_skipsRowsWithoutAResumePosition() {
        val chosen = latestResumeVideoSnapshot(
            listOf(
                row("watched", MediaType.MOVIE, positionTicks = 0L),
                row("mid", MediaType.MOVIE, positionTicks = 1L),
            ),
        )
        assertEquals("mid", chosen?.itemId)
    }

    @Test
    fun factory_emptyOrUnqualifiedRows_yieldNull() {
        assertNull(latestResumeVideoSnapshot(emptyList()))
        assertNull(latestResumeVideoSnapshot(listOf(row("series", MediaType.SERIES))))
        assertNull(latestResumeVideoSnapshot(listOf(row("movie", MediaType.MOVIE, positionTicks = null))))
    }

    @Test
    fun factory_mapsTicksToMilliseconds() {
        val chosen = latestResumeVideoSnapshot(listOf(row("m", MediaType.MOVIE, positionTicks = 90_000_000L)))
        assertEquals(9_000L, chosen?.positionMs)
    }

    @Test
    fun factory_parsesOffsetAwareStamps_andLeavesUnparseableNull() {
        val dated = latestResumeVideoSnapshot(listOf(row("a", MediaType.MOVIE, lastPlayed = "2026-01-01T12:00:00Z")))
        assertEquals(1_767_268_800_000L, dated?.timestampMs)

        val undated = latestResumeVideoSnapshot(listOf(row("b", MediaType.MOVIE, lastPlayed = "not-a-date")))
        assertNull(undated?.timestampMs)
        assertEquals("b", undated?.itemId)
    }

    @Test
    fun factory_undatedRowsSortAfterDatedOnes() {
        val chosen = latestResumeVideoSnapshot(
            listOf(
                row("undated", MediaType.MOVIE, lastPlayed = null),
                row("dated", MediaType.EPISODE, lastPlayed = "2020-01-01T00:00:00Z"),
            ),
        )
        assertEquals("dated", chosen?.itemId)
    }
}
