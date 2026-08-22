package com.raulshma.jellyplay.core.ui.util

import androidx.paging.compose.LazyPagingItems

/**
 * Crash-safe drop-in for [androidx.paging.compose.itemKey].
 *
 * paging-compose 3.5.0's `itemKey { ... }` resolves the key via [LazyPagingItems.peek],
 * which in turn calls `ItemSnapshotList.get(index)` with **no bounds check**. During a
 * refresh that shrinks the list (e.g. switching library folder/collection) the lazy
 * layout's key-index-map can query indices that are still valid against the stale
 * `itemCount` but lie past the freshly-shrunk `itemSnapshotList`, throwing
 * `IndexOutOfBoundsException` and crashing the app.
 *
 * This helper reads the snapshot directly and bounds-checks it. For the transient
 * window where `index >= snapshot.size` it falls back to the raw index as the key —
 * acceptable because those placeholders are never composed (the item lambda guards on
 * `pagedItems[index] != null`) and are replaced by stable keys once the snapshot
 * catches up.
 */
fun <T : Any> LazyPagingItems<T>.safeItemKey(key: (T) -> Any): (Int) -> Any =
    { index ->
        val snapshot = itemSnapshotList
        val item = snapshot.getOrNull(index)
        if (item != null) {
            key(item)
        } else {
            index
        }
    }
