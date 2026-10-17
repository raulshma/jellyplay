package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.bookProgressFraction
import kotlinx.coroutines.sync.Semaphore

/**
 * The one Continue-Reading fraction decode, shared by the online refresher
 * and the offline gate: per item, a best-effort TOC-cache page-count lookup
 * (a paged book's real page count lives only in the local cache — the server
 * item carries ticks, never the count) feeding
 * [bookProgressFraction] — exact page fractions where the count is known,
 * the percent fallback otherwise. A cache miss or throw degrades that one
 * item (percent fallback or skipped when there is no position to decode),
 * never the row.
 *
 * The per-item lookups fan out over [DEFAULT_FANOUT_PARALLELISM] permits
 * (they are suspending Room reads that used to run sequentially on the
 * fetch's critical path, ahead of every sections write). The decode still
 * completes before the caller's write, and the outcome is byte-identical to
 * the sequential loop: [mapConcurrent] — not the catching variant — because
 * a null page count (miss, zero count, failed read) is a VALUE here, the
 * percent fallback's input, never a drop.
 */
internal suspend fun BookTocCacheRepository.decodeBookProgressFractions(
    items: List<MediaItem>,
): Map<String, Float> {
    val pageCounts = Semaphore(DEFAULT_FANOUT_PARALLELISM).mapConcurrent(items) { item ->
        item to runCatchingRethrowingCancellation {
            getToc(item.id)?.pageCount?.takeIf { it > 0 }
        }.getOrNull()
    }
    return buildMap {
        for ((item, pageCount) in pageCounts) {
            val fraction = item.bookProgressFraction(pageCount) ?: continue
            put(item.id, fraction)
        }
    }
}

/**
 * The ONE Continue-Reading bar lookup both poster rows (offline-mirrored and
 * online) render through: the pre-decoded TOC fraction where the map knows
 * the item, the item's percent fallback ([bookProgressFraction]) otherwise.
 * Generic over the row's item type so the two rows share the selection —
 * [idOf] and [fallback] lift as each source needs (offline items map to
 * [MediaItem] for the fallback decode; online items already are one).
 */
internal fun <T> bookProgressFractionFor(
    fractions: Map<String, Float>,
    idOf: (T) -> String,
    fallback: (T) -> Float?,
): (T) -> Float? = { item -> fractions[idOf(item)] ?: fallback(item) }
