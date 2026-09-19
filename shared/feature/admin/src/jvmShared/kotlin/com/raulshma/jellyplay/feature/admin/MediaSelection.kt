package com.raulshma.jellyplay.feature.admin

import com.raulshma.jellyplay.core.model.MediaItemStub

/**
 * Returns only the scan results whose [MediaItemStub.itemId] is in [ids] — i.e.
 * the items the user has selected for deletion.
 *
 * Extracted as a shared pure function so the delete-confirmation sheets in
 * `stalemedia` and `watchedremoval` can memoize the result (via `remember`)
 * instead of recomputing a fresh filtered list — and re-sorting the underlying
 * `scanResults` computed getter — on every recomposition while the sheet is open.
 */
internal fun List<MediaItemStub>.filterSelectedForDeletion(
    ids: Set<String>,
): List<MediaItemStub> = filter { it.itemId in ids }
