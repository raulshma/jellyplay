package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus

/**
 * The exit-transition retention scan behind
 * [DownloadsViewModel.progressById] — one pure fold of the keyed side map
 * (state-in → state-out), so the ViewModel's combine chain stays a one-liner
 * and the invariant has a single pinned home.
 *
 * The invariant: the live query drops a row the moment its status leaves the
 * in-flight set, which can land a frame BEFORE the structural list re-emits
 * with the row's new status — without retention the still-DOWNLOADING row
 * would flash back to its stale structural bytes for that frame. The fold
 * keeps the last live value for every row the structural list still shows as
 * DOWNLOADING and forgets a row once its structural status moves on (or it
 * leaves the list) — exactly one frame of retention, then forgotten.
 *
 * Mechanics: [retained] is the previous accumulator, [live] the fresh
 * active-download query, [structural] the status-projection list. Entries are
 * first pruned to the structural DOWNLOADING ids, then [live] overlays
 * unconditionally — so a live entry for a non-DOWNLOADING row (e.g. QUEUED
 * with early bytes) still passes through, and a re-appearing id's fresh bytes
 * replace its retained ones.
 */
internal fun retainedDownloadProgress(
    retained: Map<String, DownloadProgress>,
    live: Map<String, DownloadProgress>,
    structural: List<DownloadItem>,
): Map<String, DownloadProgress> {
    val stillDownloading = structural
        .asSequence()
        .filter { it.status == DownloadStatus.DOWNLOADING }
        .mapTo(HashSet()) { it.id }
    val merged = HashMap(retained)
    merged.keys.retainAll(stillDownloading)
    merged.putAll(live)
    return merged
}
