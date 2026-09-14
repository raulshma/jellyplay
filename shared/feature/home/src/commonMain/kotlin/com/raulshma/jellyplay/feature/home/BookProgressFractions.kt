package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.bookProgressFraction

/**
 * The one Continue-Reading fraction decode, shared by the online refresher
 * and the offline gate: per item, a best-effort TOC-cache page-count lookup
 * (a paged book's real page count lives only in the local cache — the server
 * item carries ticks, never the count) feeding
 * [bookProgressFraction] — exact page fractions where the count is known,
 * the percent fallback otherwise. A cache miss or throw degrades that one
 * item (percent fallback or skipped when there is no position to decode),
 * never the row.
 */
internal suspend fun BookTocCacheRepository.decodeBookProgressFractions(
    items: List<MediaItem>,
): Map<String, Float> = buildMap {
    for (item in items) {
        val pageCount = runCatchingRethrowingCancellation {
            getToc(item.id)?.pageCount?.takeIf { it > 0 }
        }.getOrNull()
        val fraction = item.bookProgressFraction(pageCount) ?: continue
        put(item.id, fraction)
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
