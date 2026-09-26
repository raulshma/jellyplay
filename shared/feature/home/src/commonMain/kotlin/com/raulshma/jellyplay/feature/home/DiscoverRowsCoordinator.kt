package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Deep module extracted from [HomeRefresher]: the discover-row dice-roll
 * machinery — the roll registry, the roll jobs, and the state patches a roll
 * applies — behind one small interface ([roll], [drainRolls],
 * [cancelForIdentityChange]). Previously this lived inline on the refresher
 * as ~160 lines interleaved with the fetch choreography; every home change
 * had to re-learn the generation invariant by reading the whole refresher.
 * The refresher keeps WHAT/WHEN (the fetch schedule, the drain CALL SITE);
 * this coordinator owns the ordering rule that makes a roll survive a
 * concurrently in-flight fetch.
 *
 * Writes go through the refresher's SINGLE [HomeRefreshState] store (the
 * [state] reference handed in at construction is the refresher's own
 * `MutableStateFlow`) — the coordinator is extracted machinery, not a second
 * writer: [HomeRefreshState.sections] stays single-store, and the VM keeps
 * folding one state object.
 *
 * The repository owns the cache half of the roll (invalidate → fetch → seed;
 * see `MediaRepository.rerollDiscoverRow`, the protocol's single owner);
 * this class owns the FEATURE half: patching the on-screen row in place and
 * ordering its writes against a fetch's own sections write.
 *
 * THE GENERATION INVARIANT — the one ordering rule this class exists to
 * state: a fetch re-applies every roll registered before the fetch's DRAIN
 * POINT ([drainRolls]); a roll registered after the drain point applies
 * itself. The refresher calls [drainRolls] as the last statement before the
 * fetch's single sections write, with no suspension between drain and write,
 * so every suspension a fetch can park on (the main sections await, the
 * book-fraction decode) sits strictly BEFORE it: a roll landing anywhere
 * mid-fetch registers into a not-yet-drained registry and is re-applied by
 * the write that follows, while a roll landing after the write is not that
 * fetch's to cover — registration happens-before the roll's own in-place
 * patch, so it applies itself, and its entry simply waits for the NEXT
 * fetch's drain (re-applying it is idempotent). [registerRolledRowGeneration]
 * stamps [rollGeneration] on every entry to give that happens-before edge a
 * name; the stamps are ordered, never compared — the registry drains whole,
 * no filtering by stamp.
 *
 * Vocabulary: "generation", not "epoch", in the feature layer — the lower
 * layers keep their store-local epoch guards (the repository's
 * discoverRollEpoch, the network layer's discoverRowEpoch; see the roll
 * protocol on `MediaRepository.rerollDiscoverRow` for how the three layers
 * compose). Identity transitions clear this registry wholesale
 * ([cancelForIdentityChange]) — clearing alone is sufficient because the
 * generation's job is ordering WITHIN one identity, and an identity change
 * voids ordering wholesale; [rollGeneration] is deliberately not reset.
 *
 * Plain map on purpose: written by [roll]'s job and consumed by [drainRolls],
 * both on the refresher scope's main-confined dispatcher with no suspension
 * between the drain and the clear.
 */
internal class DiscoverRowsCoordinator(
    /** The refresher's scope: roll jobs must die with the VM, beside the refresh jobs. */
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    /** The refresher's own state store — handed in so the roll patches and flag writes land in the single UiState fold. */
    private val state: MutableStateFlow<HomeRefreshState>,
) {

    internal companion object {
        /** Logcat/console tag for the dice roll's degraded-outcome logs. */
        private const val TAG = "DiscoverRowsCoordinator"

        /**
         * Minimum time the rolling flag stays up per roll — the dice spin's
         * display floor. A sub-100ms local-server roll must still show
         * perceptible feedback or the affordance reads as dead.
         */
        private const val ROLL_MIN_SPIN_MS = 700L

        /** Test-visible mirror of [ROLL_MIN_SPIN_MS] (private const can't be read from tests). */
        internal const val ROLL_MIN_SPIN_FOR_TEST = ROLL_MIN_SPIN_MS
    }

    /**
     * The roll-vs-fetch registry: dice rolls that landed while a full refresh
     * was ALREADY in flight, row id → (rolled items, generation stamp). The
     * repository's epoch guards keep its caches roll-clean, but the raced
     * fetch's ALREADY-RESOLVED result still carries the row's pre-roll
     * payloads, and its single sections write would transiently revert the
     * on-screen roll; the write re-applies these entries instead (see
     * [drainRolls]) and drains them — later fetches serve the seeded cache
     * and need no guard. This uiState-vs-fetch-fold race is why the registry
     * survives the repository-owned reroll: it orders FEATURE state, not
     * caches.
     */
    private val rolledRowGenerations = LinkedHashMap<String, RolledRowGeneration>()

    /**
     * Monotonic source of the stamps in [rolledRowGenerations]. Plain var on
     * purpose — bumped only by [registerRolledRowGeneration] on the scope's
     * main-confined dispatcher, the single-writer idiom of this class's
     * cross-job fields.
     */
    private var rollGeneration = 0L

    /**
     * In-flight dice-roll jobs (row id → job), so the identity transitions can
     * cancel them alongside the refresher's refresh/discover jobs: a roll that
     * raced a sign-out or user-switch belongs to the PREVIOUS identity —
     * letting it land would patch the new identity's freshly painted sections,
     * and its [rolledRowGenerations] entry would make the next fetch re-apply
     * the previous user's rolled items. Removed by each roll's finally
     * (including a cancelled one, via [NonCancellable] clearing through the
     * flag reset).
     */
    private val rollJobs = LinkedHashMap<String, Job>()

    /**
     * The dice affordance for one RANDOM-sorted Jellyfin discover row. The
     * repository's [MediaRepository.rerollDiscoverRow] owns the whole cache
     * choreography (drop the pre-roll payloads, fetch fresh, commit the rolled
     * set where the next home fetch reads it); this side does only what the
     * repository cannot see: patch the row's items in place — no full refresh,
     * no spinner, sibling rows untouched — guarded by the roll-generation
     * registry against a fetch already in flight, and keep the spin state
     * honest. Failures degrade silently (row keeps its current items; the
     * rolling flag still clears). One in-flight roll per row — a tap while
     * that row's dice is already animating
     * ([HomeRefreshState.rollingDiscoverRowIds]) is ignored.
     */
    fun roll(row: DiscoverRowConfig, onResult: (Boolean) -> Unit = {}) {
        // Check-and-set inside _state.update's CAS loop so a concurrent state
        // writer can't slip a second roll for the same row between the check
        // and the set (double fetch + a patch racing its own finally-clear).
        var accepted = false
        state.update { current ->
            if (row.id in current.rollingDiscoverRowIds) {
                current
            } else {
                accepted = true
                current.copy(rollingDiscoverRowIds = current.rollingDiscoverRowIds + row.id)
            }
        }
        if (!accepted) return
        val rollStartedAt = TimeSource.Monotonic.markNow()
        Log.d(TAG, "roll ${row.id}: accepted, flag up")
        rollJobs[row.id] = scope.launch {
            // The roll's outcome for the caller: true when the row's items
            // were swapped, false when the fetch failed/returned nothing
            // (the row keeps its current items — reported, not silent).
            // Declared outside try so the finally's diagnostic log can read it.
            var rolled = false
            try {
                runCatchingRethrowingCancellation {
                    val result = mediaRepository.rerollDiscoverRow(row)
                    val items = result.getOrNull().orEmpty()
                    if (items.isEmpty()) {
                        // A silent skip here reads as a dead button on the
                        // screen — log the cause so the failure is diagnosable.
                        result.exceptionOrNull()?.let { e ->
                            Log.w(TAG, "Discover row roll failed for ${row.id}: ${e.message}", e)
                        }
                        return@runCatchingRethrowingCancellation
                    }
                    rolled = true
                    // Register BEFORE the state patch below (generation
                    // invariant on [rolledRowGenerations]): a fetch already in
                    // flight captured the pre-roll payloads and lands its own
                    // sections write after this one — the stamped entry makes
                    // that write re-apply the rolled items instead of
                    // reverting the roll.
                    val generation = registerRolledRowGeneration(row.id, items)
                    val rowSectionId = HomeSectionType.DISCOVER.descriptor.idFor(row.id)
                    Log.d(
                        TAG,
                        "roll ${row.id}: gen=$generation, patched ${items.size} items, first=${items.firstOrNull()?.id} " +
                            "(on-screen first=${state.value.sections
                                .firstOrNull { it.id == rowSectionId && it.type == HomeSectionType.DISCOVER }
                                ?.items?.firstOrNull()?.id})",
                    )
                    state.update { s ->
                        s.copy(sections = patchRolledRows(s.sections, mapOf(rowSectionId to items)))
                    }
                }
                onResult(rolled)
            } finally {
                // Keep the flag up for a minimum window: a fast local server
                // answers in tens of milliseconds, and a spin that flashes for
                // one frame reads as no feedback at all (the on-device report
                // behind the dice feature). NonCancellable so a cancelled roll
                // (stop()/user switch) still clears the flag — a stuck flag
                // would disable the dice forever.
                withContext(NonCancellable) {
                    val remainingMs = ROLL_MIN_SPIN_MS - rollStartedAt.elapsedNow().inWholeMilliseconds
                    Log.d(TAG, "roll ${row.id}: done in ${ROLL_MIN_SPIN_MS - remainingMs}ms, rolled=$rolled, holding flag ${remainingMs}ms more")
                    if (remainingMs > 0) delay(remainingMs)
                }
                Log.d(TAG, "roll ${row.id}: flag cleared")
                rollJobs.remove(row.id)
                state.update { it.copy(rollingDiscoverRowIds = it.rollingDiscoverRowIds - row.id) }
            }
        }
    }

    /**
     * Identity-transition drain of the dice-roll machinery (see [rollJobs]):
     * cancel the in-flight rolls — their finally clears the rolling flags via
     * [NonCancellable] — and clear the roll-generation registry so the
     * incoming identity's first fetch doesn't re-apply the previous user's
     * rolled items (see [rolledRowGenerations]: clearing is sufficient — an
     * identity change voids the ordering the generations track). The
     * network-layer caches they seeded are cleared wholesale by the identity
     * transition itself.
     */
    fun cancelForIdentityChange() {
        rollJobs.values.forEach { it.cancel() }
        rollJobs.clear()
        rolledRowGenerations.clear()
    }

    /**
     * Registration half of the generation invariant (see
     * [rolledRowGenerations]): stamps the next [rollGeneration] onto the
     * entry and returns the stamp for log correlation. Called by [roll]
     * strictly before the roll's own in-place state patch.
     */
    private fun registerRolledRowGeneration(rowId: String, items: List<MediaItem>): Long {
        val generation = ++rollGeneration
        rolledRowGenerations[rowId] = RolledRowGeneration(items, generation)
        return generation
    }

    /**
     * Drain half of the generation invariant (see [rolledRowGenerations]):
     * overlays every registered roll over the fetch's sections and drains the
     * registry — the fetch's own last word on sections, so a roll that
     * completed mid-fetch keeps its freshly-rolled items on screen. Called by
     * the refresher as the last statement before the fetch's single sections
     * write, with NO suspension after it (synchronous by construction — the
     * invariant's no-suspension window is structural, not conventional).
     * Entries whose row the fetch doesn't carry (disabled / absent) are
     * dropped with the drain: the roll had no section to patch either.
     */
    fun drainRolls(sections: List<HomeSection>): List<HomeSection> {
        if (rolledRowGenerations.isEmpty()) return sections
        Log.d(
            TAG,
            "fetch re-applying rolled rows ${rolledRowGenerations.keys} " +
                "(generations ${rolledRowGenerations.values.joinToString { it.generation.toString() }})",
        )
        val rolledBySectionId = rolledRowGenerations
            .mapKeys { (rowId, _) -> HomeSectionType.DISCOVER.descriptor.idFor(rowId) }
            .mapValues { (_, entry) -> entry.items }
        rolledRowGenerations.clear()
        return patchRolledRows(sections, rolledBySectionId)
    }

    /**
     * The one in-place row-swap shape, shared by [roll]'s own write and
     * [drainRolls]'s fetch-overlaid write so the two writers can't drift on
     * what identifies a rolled row: a DISCOVER-typed section whose id is the
     * DISCOVER descriptor's `idFor` of the rolled row's id. Sections without
     * an entry pass through untouched.
     */
    private fun patchRolledRows(
        sections: List<HomeSection>,
        rolledItemsBySectionId: Map<String, List<MediaItem>>,
    ): List<HomeSection> = sections.map { section ->
        if (section.type != HomeSectionType.DISCOVER) {
            section
        } else {
            rolledItemsBySectionId[section.id]?.let { section.copy(items = it) } ?: section
        }
    }
}

/**
 * One [DiscoverRowsCoordinator] registry entry: the rolled items plus the
 * monotonic generation stamp of the registration that produced them (see the
 * generation invariant on [DiscoverRowsCoordinator]).
 */
private data class RolledRowGeneration(
    val items: List<MediaItem>,
    val generation: Long,
)
