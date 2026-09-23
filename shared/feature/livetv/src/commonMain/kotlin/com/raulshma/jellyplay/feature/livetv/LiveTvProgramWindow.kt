package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.model.LiveTvProgram
import kotlin.time.Instant

/**
 * The Live-TV feature's ONE now/next fold over a channel's program list.
 * player-live's [com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel]
 * was the last hand-rolled scan — a strict `Instant.parse` ladder, an inline
 * `start <= now < finish` predicate and a direct `Clock.System.now()` read —
 * and now reads this vocabulary instead (the LiveNowWindow convergence).
 * Pure commonMain, Compose-free, NO clock reads: the caller supplies `now`
 * through its own injected clock seam ([nowInstant] for the Live-TV
 * ViewModels).
 */
object LiveTvProgramWindow {

    /**
     * Picks the program on air at [now] and the next one to start, from
     * [programs] IN LIST ORDER (the server's ordering is the tie-break —
     * `firstOrNull`, never a re-sort; an empty list or a list with no airing
     * and no future program yields nulls on the respective side).
     *
     *  - current: the first program satisfying [isAiringAt]'s half-open
     *    `[start, end)` window, timestamps parsed through the canonical
     *    lenient [toInstantOrNull] ladder (offset-less strings read as UTC —
     *    the C10 vocabulary the former strict `Instant.parse` scan rejected).
     *  - next: the first program whose start parses and is strictly after
     *    `now`, skipping the current program's id (vacuous when current is
     *    null — a current program's start can never also be `> now`).
     *
     * Behavior note versus the player-live scan this replaced, honestly: that
     * scan required BOTH timestamps to parse before a program could be
     * current; the canonical [isAiringAt] semantics treat a null bound as
     * unconstrained, so a started program with a missing/unparseable end (or
     * an unparseable start with a live end) is now eligible — the shared
     * lenient rule channel detail already pins, applied to one more caller.
     * The `next` leg's semantics are unchanged (a program with no parseable
     * start can never be next).
     */
    fun currentAndNext(
        programs: List<LiveTvProgram>,
        now: Instant,
    ): Pair<LiveTvProgram?, LiveTvProgram?> {
        val current = programs.firstOrNull { isAiringAt(it, now) }
        val next = programs.firstOrNull { program ->
            val start = program.startDate?.toInstantOrNull()
            program.id != current?.id && start != null && start > now
        }
        return current to next
    }
}
