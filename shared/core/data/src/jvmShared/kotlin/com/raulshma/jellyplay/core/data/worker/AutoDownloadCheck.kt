package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.sortedByPlaybackOrder
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.download.ArrAutoArmSync
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.model.resolveAgainst
import kotlinx.coroutines.flow.first

/**
 * The auto-download check choreography: one pass that finds new episodes of
 * ARMED series (ADR-0015) and hands them to [DownloadIntake.startSeries] per
 * season, gated per series on its effective mode (resolved through
 * [resolveAgainst]: the per-series policy row — an explicit override or a
 * FOLLOW_GLOBAL arm — against the global `autoDownloadMode` preference;
 * NEVER series are skipped) and respecting the WiFi-only / storage-limit
 * constraints enforced inside [DownloadRepository].
 *
 * The pass's universe is coverage ∪ arms (ADR-0015 decision 4): the
 * disk-derived `downloadCoverage().seriesIds` unioned with the
 * `series_download_policies` arm rows, so a series whose downloads were all
 * reclaimed by watched-deletion hygiene is still checked every pass (gh-225:
 * the weekly series that stopped downloading once its watched episodes were
 * auto-deleted). [DownloadCoverage] itself stays disk-derived — its other
 * consumers read "what's on disk".
 *
 * Candidates are hygiene-filtered in EVERY mode (ADR-0015 decision 6): played
 * episodes are never candidates (an armed, fully-watched, fully-deleted
 * series would otherwise re-download its history; the lookahead ladder always
 * implied this and legacy take-all now matches), and virtual/unaired episodes
 * are never candidates (client-side guard, decoupled from the
 * `show_missing_episodes` display pref).
 *
 * The pass is bounded by the retention policy (the `auto_download_*` prefs on
 * [DownloadsStore]):
 *  - **Sweep first** — the keep-days retention sweep
 *    ([DownloadRepository.sweepExpiredAutoDownloads]) runs at the start of
 *    every pass so the pipeline stays self-cleaning (watched-only; unwatched
 *    downloads are never reclaimed).
 *  - **arr auto-arm** — the [ArrAutoArmSync] stage runs right after the sweep
 *    and before the universe derivation (gh-225 follow-up): gated on the
 *    `autoArmFromArr` pref + the Direct *arr flag + a configured Sonarr, it
 *    arms Sonarr-monitored series the Jellyfin library contains (add-only,
 *    insert-if-absent — an explicit NEVER tombstone is never re-armed), so the
 *    union below picks them up the same pass. Failures skip the stage, never
 *    the pass; arming never consumes the maxPerPass budget.
 *  - **Lookahead** — instead of every not-yet-downloaded episode of a season,
 *    the pass enqueues only the [DownloadsSlice.autoDownloadLookahead] episodes
 *    that follow the season's highest **downloaded or watched** episode in
 *    playback order (watched = the server played flag the catalogue snapshot's
 *    episodes carry — the same userData surface `PlayedStateSync` reconciles
 *    against). A season with no downloaded and no watched episode falls back
 *    to the season head (the first lookahead real episodes), so an arr- or
 *    picker-armed series the user never engaged with starts at episode 1.
 *    `0` restores the legacy take-them-all behavior.
 *  - **Max-per-pass** — a global enqueue budget across every series in the
 *    pass ([DownloadsSlice.autoDownloadMaxPerPass], 0 = unlimited); once hit,
 *    the pass stops and the remaining series are picked up next pass.
 *  - **Per-server allow-list** — a non-empty `autoDownloadServers` set no-ops
 *    the whole pass unless [ServerIdentityStore.activeServerId] is in it.
 *  - **Per-series policy** (gh-181; ADR-0015) — each series resolves its
 *    effective mode as its `series_download_policies` row (an explicit sheet
 *    override, or a FOLLOW_GLOBAL arm resolved against the global preference)
 *    via [resolveAgainst]. NEVER skips the series; ASK computes the same
 *    candidates the INSTANT half would enqueue but records them as pending
 *    suggestions
 *    ([DownloadRepository.recordAutoDownloadSuggestions]) for the user to
 *    confirm on the Downloads screen — never enqueues.
 *
 * Previously this lived twice: verbatim in the legacy Android
 * `core.data.worker.AutoDownloadWorker.doWork`, and ported verbatim in
 * `DesktopAutoDownloadScheduler.runCheck` (whose own KDoc admitted the
 * copy). The move is a body move (same gate, same single-query series index,
 * same per-season intake, same transient-failure escalation) so both
 * platforms check through ONE code path; the adapters own only the
 * scheduling shells — Android's WorkManager periodic/enqueueNow pair and
 * the desktop's in-process 6 h loop (see each adapter's KDoc).
 *
 * Failures are observable: a per-series catalogue load failure does not
 * abort the pass (partial success is preserved; that series is skipped), but
 * it escalates the outcome to [Outcome.RetriesPending] so the caller comes
 * back, and to [Outcome.Exhausted] once the [MAX_RETRIES] budget is spent.
 * Previously the Android worker returned `Result.success()` unconditionally,
 * leaving a persistently broken server path invisible to operators.
 *
 * Seasons + episodes come from [EpisodeCatalogue.loadSeriesEpisodes] — a
 * single consolidated snapshot per series instead of a separate `getSeasons`
 * + per-season `getEpisodes` fan-out. This path runs online-only, so
 * `offline` defaults to `false`.
 */
class AutoDownloadCheck(
    private val downloadsStore: DownloadsStore,
    private val downloadRepository: DownloadRepository,
    private val policyStore: SeriesDownloadPolicyStore,
    private val downloadIntake: DownloadIntake,
    private val episodeCatalogue: EpisodeCatalogue,
    /**
     * The arr auto-arm stage (gh-225 follow-up): Sonarr-monitored series that
     * exist in the Jellyfin library get armed before the universe derivation,
     * so they join THIS pass. Its failures never fail the pass (stage-skipped,
     * below) and it enqueues nothing (no maxPerPass consumption).
     */
    private val arrAutoArmSync: ArrAutoArmSync,
    /** Active-server identity for the per-user allow-list gate. */
    private val serverIdentityStore: ServerIdentityStore,
    /**
     * Cancellation seam (the ScanWorkerHelper pattern): folds the Android
     * worker's `isStopped` and the desktop loop's `isActive` into one check,
     * polled before each series and each season so a stopped caller stops
     * mid-pass instead of queueing downloads for a dead shell.
     */
    private val isStopped: () -> Boolean = { false },
    /** Clock for the Ask-mode suggestions' `suggestedAt` stamp (fake-clock testable). */
    private val timeSource: TimeSource = SystemTimeSource(),
) {

    /**
     * Runs one check pass. [attempt] is the retry-budget input (the Android
     * adapter passes WorkManager's `runAttemptCount`; the desktop ladder
     * counts its own in-process passes from 0).
     */
    suspend fun checkOnce(attempt: Int): Outcome {
        // No global gate: a global NEVER with per-series overrides must still
        // reach those series (the schedulers keep the pass alive via
        // hasSeriesPolicies); series resolution below skips NEVER ones.
        // Disk-backed one-shot read, not the eagerly-shared [DownloadsStore.downloads]
        // projection (the same reasoning as the scheduler's keep-alive gate in
        // [DownloadsStore.readDownloadsOrNull]): the projection has no happens-before
        // edge against a pass launched right after a write, so a just-enabled
        // `autoArmFromArr` (or mode/lookahead change) could be observed at its
        // pre-write value and the pass would skip arming. The raw cold read
        // suspends until the persisted snapshot arrives.
        //
        // Failure-surfacing on purpose ([DownloadsStore.readDownloadsOrNull],
        // not the degrading [DownloadsStore.readDownloads]): the degrading read
        // answers the all-defaults slice on a failed read, whose EMPTY
        // `autoDownloadServers` allow-list reads as "every server allowed" —
        // the gate below would then run the sweep and intake for a server the
        // user excluded (the persisted restriction silently lifted by an IO
        // failure). A failed read must fail the pass instead: null rides the
        // retry budget (the same escalation as the catalogue failures below),
        // and the next pass re-reads disk.
        val prefs = runCatchingRethrowingCancellation { downloadsStore.readDownloadsOrNull() }
            .getOrNull()
        if (prefs == null) {
            // The wrap folds any contract-violating throw into the same
            // fail-closed arm (the scheduler gate's runCatching idiom);
            // readDownloadsOrNull's contract is null-on-failure.
            Log.w(TAG, "AutoDownload pass skipped: reading the persisted downloads slice failed")
            return retryOutcome(attempt)
        }

        // Per-server allow-list: empty = all servers; a non-empty set that does
        // not contain the active server no-ops the whole pass (the download
        // stack itself is server-scoped, so there is nothing to sweep or
        // enqueue "for another server" here).
        if (prefs.autoDownloadServers.isNotEmpty()) {
            // Disk-backed one-shot read (see ServerIdentityStore.readIdentity)
            // — the activeServerId projection derives from the eagerly-seeded
            // identity StateFlow, whose NoSession seed is instantly readable
            // during the DataStore-read window (the pass would skip despite a
            // persisted selection).
            val activeServerId = serverIdentityStore.readIdentity().serverId
            if (activeServerId == null || activeServerId !in prefs.autoDownloadServers) {
                Log.i(TAG, "AutoDownload skipped: active server is not allow-listed")
                return Outcome.Complete()
            }
        }

        // Retention sweep first — the pass self-cleans before it enqueues.
        // The sweep is failure-swallowing by contract (a failed pass returns
        // EMPTY and the next periodic pass retries); it must never block the
        // enqueue half. runCatchingRethrowingCancellation keeps the caller's
        // cancellation propagating (a swallowed CancellationException here
        // would let a stopped worker keep enqueueing past the stop).
        runCatchingRethrowingCancellation { downloadRepository.sweepExpiredAutoDownloads() }
            .onFailure { Log.w(TAG, "Retention sweep failed", it) }

        // The arr auto-arm stage runs between the sweep and the universe
        // derivation so newly armed series join THIS pass's universe below
        // (the same sweep-first-then-widen ordering as the retention half).
        // Best-effort like the sweep: any stage failure (arr down, Jellyfin
        // enumeration failing) is swallowed — arr being down must never break
        // auto-download — and the stage is a policy-row writer only, so it
        // never consumes the INSTANT maxPerPass budget (arming enqueues
        // nothing; the enqueues happen in this pass's own loop).
        runCatchingRethrowingCancellation { arrAutoArmSync.sync(prefs) }
            .onFailure { Log.w(TAG, "arr auto-arm stage failed; stage skipped", it) }

        // The universe union (ADR-0015 decision 4): the disk-derived coverage
        // series half (the folded getDownloadedSeriesIds read — same DAO
        // query, snapped off the shared coverage flow) plus every armed
        // series' id, so armed-but-empty series stay checked (gh-225).
        // distinct() matters: implicit arming gives nearly every downloaded
        // series an arm row too, so the halves overlap heavily — a duplicate
        // visit would re-run the whole series choreography and double-count
        // its enqueues against maxPerPass.
        val seriesIds =
            downloadRepository.downloadCoverage().first().seriesIds + policyStore.armedSeriesIds()
        if (seriesIds.isEmpty()) return Outcome.Complete()
        val universe = LinkedHashSet(seriesIds)

        // Fetch every series' downloaded episode ids in a single 2-column query
        // (grouped by seriesId) instead of issuing one full-row query per series
        // inside the loop below. A 100-episode series previously decoded ~100
        // rows × 23 columns per iteration; this reads 2 columns, once.
        val downloadedEpisodeIdsBySeries = downloadRepository.getDownloadedEpisodeIdsBySeries()

        val globalMode = prefs.autoDownloadMode
        // Per-series policy rows (gh-181/ADR-0015) resolved in one query; the
        // map carries explicit overrides AND FOLLOW_GLOBAL arms — both resolve
        // through [resolveAgainst] (missing id = un-armed coverage series =
        // the global mode).
        val policies = policyStore.seriesPolicies(universe.toList())

        val lookahead = prefs.autoDownloadLookahead
        val maxPerPass = prefs.autoDownloadMaxPerPass
        var enqueuedInPass = 0
        val askSuggestions = mutableListOf<AutoDownloadSuggestion>()

        var hadTransientFailure = false
        for (seriesId in universe) {
            if (isStopped()) break
            // Budget spent: the remaining series (and their seasons) are picked
            // up by the next pass instead of blowing past the cap mid-pass.
            if (maxPerPass > 0 && enqueuedInPass >= maxPerPass) break
            val mode = policies[seriesId].resolveAgainst(globalMode)
            if (mode == AutoDownloadMode.NEVER) continue
            val alreadyDownloaded = downloadedEpisodeIdsBySeries[seriesId].orEmpty()
            // One consolidated load per series: seasons + every season's episodes
            // in a single snapshot, replacing the prior getSeasons + per-season
            // getEpisodes fan-out. A catalogue failure is a transient error —
            // this series is skipped but the run continues and the outcome
            // escalates to RetriesPending below.
            val snapshot = episodeCatalogue.loadSeriesEpisodes(seriesId).getOrElse {
                hadTransientFailure = true
                continue
            }
            for (season in snapshot.seasons) {
                if (isStopped()) break
                // The enqueue budget bounds the INSTANT half only — Ask-mode
                // prompts write table rows, not transfers, so they never
                // consume download budget.
                if (mode == AutoDownloadMode.INSTANT && maxPerPass > 0 && enqueuedInPass >= maxPerPass) break
                val seasonEpisodes = snapshot.seasonEpisodes(season.id)
                val newEpisodeIds = if (lookahead > 0) {
                    lookaheadWindow(seasonEpisodes, alreadyDownloaded, lookahead)
                } else {
                    // Legacy behavior: every not-yet-downloaded episode — now
                    // with the ADR-0015 decision-6 hygiene filter (played
                    // episodes are never candidates in ANY mode; virtual/
                    // unaired never), so an armed fully-watched series does
                    // not re-download its history.
                    seasonEpisodes
                        .filter { it.id !in alreadyDownloaded && !it.isPlayed && !it.isVirtual }
                        .map { it.id }
                }
                if (newEpisodeIds.isEmpty()) continue
                when (mode) {
                    AutoDownloadMode.INSTANT -> {
                        val budgeted = if (maxPerPass > 0) {
                            newEpisodeIds.take(maxPerPass - enqueuedInPass)
                        } else {
                            newEpisodeIds
                        }
                        downloadIntake.startSeries(
                            seriesId = seriesId,
                            episodeIds = mapOf(season.id to budgeted),
                        )
                        enqueuedInPass += budgeted.size
                    }

                    AutoDownloadMode.ASK -> {
                        // Same candidates the INSTANT half would have enqueued,
                        // recorded as pending prompts instead. The Downloads
                        // screen's suggestion card is where the user answers;
                        // the Android adapter additionally raises a summary
                        // notification when [Outcome.Complete.newSuggestions]
                        // reports fresh rows.
                        val episodeById = seasonEpisodes.associateBy { it.id }
                        newEpisodeIds.forEach { episodeId ->
                            episodeById[episodeId]?.let { episode ->
                                askSuggestions += AutoDownloadSuggestion(
                                    episodeId = episode.id,
                                    seriesId = seriesId,
                                    seriesName = episode.seriesName.orEmpty(),
                                    seasonId = season.id,
                                    seasonName = episode.seasonName ?: season.name,
                                    seasonNumber = episode.seasonNumber ?: 0,
                                    episodeNumber = episode.indexNumber ?: 0,
                                    name = episode.name,
                                    suggestedAt = timeSource.nowEpochMillis(),
                                )
                            }
                        }
                    }

                    AutoDownloadMode.NEVER -> Unit

                    // Unreachable: [resolveAgainst] never yields the sentinel
                    // (it folds into the global mode above). The arm keeps the
                    // when exhaustive over the enum.
                    AutoDownloadMode.FOLLOW_GLOBAL -> Unit
                }
            }
        }

        val newSuggestions = if (askSuggestions.isEmpty()) {
            0
        } else {
            policyStore.recordAutoDownloadSuggestions(askSuggestions)
        }

        if (!hadTransientFailure) return Outcome.Complete(newSuggestions)
        return retryOutcome(attempt)
    }

    /**
     * The ONE transient-failure escalation fold (the [WorkerRetryVerdict]
     * budget applied to [attempt]) — shared by the persisted-slice read's
     * fail-closed arm above and the per-series catalogue failures, so a new
     * transient source maps onto the same Retry/Exhausted ladder instead of
     * re-folding the verdict.
     */
    private fun retryOutcome(attempt: Int): Outcome =
        when (WorkerRetryVerdict.of(attempt, MAX_RETRIES)) {
            WorkerRetryVerdict.Retry -> Outcome.RetriesPending
            WorkerRetryVerdict.Exhausted -> {
                Log.w(TAG, "AutoDownload exhausted $MAX_RETRIES retries")
                Outcome.Exhausted
            }
        }

    /**
     * The lookahead window for one season: the [lookahead] episodes that
     * follow the season's highest downloaded-or-watched episode in playback
     * order. The anchor is positional (an index in the ordered list, not an
     * episode-number arithmetic), so gapped numbering and a mid-season start
     * (an anchor that is not the first episode) both resolve naturally. A
     * season with no downloaded and no watched episode has no anchor and
     * falls back to the season head — the first [lookahead] real episodes
     * (ADR-0016's amendment: an arr- or picker-armed series the user never
     * engaged with must start at episode 1, not enqueue nothing). For an
     * armed-then-hygiene-emptied series the anchor sits on the last watched
     * episode alone (the ADR-0015 resume mechanics).
     *
     * The anchor computation counts PLAYED episodes deliberately (filtering
     * them here would kill the resume anchor); the ADR-0015 decision-6
     * hygiene filter (played/virtual are never candidates) applies to the
     * window AFTER the anchor, before the take — so [lookahead] real episodes
     * are chosen, not skipped ones.
     */
    private fun lookaheadWindow(
        seasonEpisodes: List<MediaItem>,
        alreadyDownloaded: Set<String>,
        lookahead: Int,
    ): List<String> {
        val ordered = seasonEpisodes.sortedByPlaybackOrder()
        val anchorIndex = ordered.indexOfLast { it.id in alreadyDownloaded || it.isPlayed }
        return ordered
            .drop(anchorIndex + 1)
            .filter { !it.isPlayed && !it.isVirtual }
            .take(lookahead)
            .map { it.id }
    }

    /**
     * Outcome of one [checkOnce] pass — what the platform adapter decides on.
     * The retry-budget comparison is applied HERE (one place owns
     * [MAX_RETRIES]); the adapters only map the arms.
     */
    sealed interface Outcome {

        /**
         * The gate was off, there was nothing downloaded to check, or every
         * catalogue load landed. [newSuggestions] is how many Ask-mode
         * prompts were newly recorded this pass (0 on every INSTANT-only
         * pass) — the Android adapter's notification trigger. Equality is
         * structural, so `assertEquals(Outcome.Complete, result)` keeps
         * holding for passes that recorded nothing.
         */
        data class Complete(val newSuggestions: Int = 0) : Outcome

        /**
         * At least one series' catalogue load failed while still under
         * [MAX_RETRIES] — the caller should come back for another pass.
         * (Android mapping: `Result.retry()`; desktop: the in-process
         * backoff-spaced retry pass.)
         */
        data object RetriesPending : Outcome

        /**
         * At least one series' catalogue load failed and the [MAX_RETRIES]
         * budget is spent — no further passes. (Android mapping:
         * `Result.failure()`; desktop: the ladder gives up until the next
         * periodic pass.)
         */
        data object Exhausted : Outcome
    }

    companion object {
        private const val TAG = "AutoDownloadCheck"

        /**
         * Retry budget for transient catalogue-load failures before the check
         * escalates to [Outcome.Exhausted]. The ONE declaration behind the
         * Android worker's retry()/failure() escalation (was
         * `AutoDownloadWorker.MAX_RETRIES`) and the desktop ladder's give-up
         * (was `DesktopAutoDownloadScheduler.MAX_RETRIES`).
         */
        const val MAX_RETRIES = 3
    }
}
