package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadArm
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.resolveAgainst

/**
 * One armed series rendered for the Downloads screen's armed list (ADR-0015):
 * a series whose arm keeps it in the auto-download check's universe even at
 * zero downloaded rows — which is exactly why it needs its own surface (the
 * normal downloads list cannot show it).
 *
 * [displayName] falls back to the series id when the arm's cached
 * [SeriesDownloadArm.seriesName] is blank — a sheet-written row can be
 * nameless until its first episode download backfills the name. [resolvedMode]
 * is the raw row value resolved against the global preference through
 * [resolveAgainst] (THE one resolution home — a second hand-fold of the
 * FOLLOW_GLOBAL branch is a defect); [followsGlobal] keeps the raw sentinel's
 * story so the row can SAY "follows global" instead of presenting the
 * resolved mode as if it were a stored override. Explicit NEVER rows stay
 * listed too (the check visits and skips them — hiding them would make the
 * arm undiscoverable and its resolution unexplainable).
 *
 * [reArmable] keys on the RAW ROW mode == NEVER (the disarm tombstone the
 * un-arm verb now writes), never on [resolvedMode] — a FOLLOW_GLOBAL row
 * under a global NEVER also RESOLVES to Never but is an ARMED row (the check
 * follows the global preference the moment it flips back), so its button must
 * stay "Stop auto-download". Re-arm writes [AutoDownloadMode.FOLLOW_GLOBAL]
 * via [DownloadsSeriesPolicyActions.reArmSeries] — [armSeries] would no-op on
 * the existing row.
 */
@Immutable
data class ArmedSeriesRow(
    val seriesId: String,
    val displayName: String,
    val followsGlobal: Boolean,
    val resolvedMode: AutoDownloadMode,
    val reArmable: Boolean,
)

/**
 * The pure [SeriesDownloadArm] → [ArmedSeriesRow] projection beside the VM
 * (the DownloadsViewModelOwnershipTest carve-out rule): name fallback,
 * FOLLOW_GLOBAL resolution, and the deterministic display order (name,
 * case-insensitive, series id as the tiebreaker so two same-named series
 * can't flip rows between emissions). A small table — no polling, no network:
 * the name comes from the arm row itself.
 */
fun armedSeriesRows(
    arms: List<SeriesDownloadArm>,
    globalMode: AutoDownloadMode,
): List<ArmedSeriesRow> = arms
    .map { arm ->
        ArmedSeriesRow(
            seriesId = arm.seriesId,
            displayName = arm.seriesName.takeIf(String::isNotBlank) ?: arm.seriesId,
            followsGlobal = arm.mode == AutoDownloadMode.FOLLOW_GLOBAL,
            resolvedMode = arm.mode.resolveAgainst(globalMode),
            reArmable = arm.mode == AutoDownloadMode.NEVER,
        )
    }
    .sortedWith(compareBy({ it.displayName.lowercase() }, { it.seriesId }))
