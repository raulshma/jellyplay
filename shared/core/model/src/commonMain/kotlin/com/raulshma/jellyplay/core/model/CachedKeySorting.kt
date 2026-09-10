package com.raulshma.jellyplay.core.model

/**
 * Sorts by a key computed **once per element** (decorate–sort–undecorate)
 * instead of once per comparison. Kotlin's `sortedBy`/`sortedWith` invoke
 * their selector inside the comparator, i.e. O(n log n) times; for keys that
 * allocate or parse (`String.lowercase` on large lists, ISO-date parsing)
 * the cached form computes each key exactly once. Both variants are stable
 * sorts, like their stdlib counterparts.
 *
 * Extracted when the inline idiom
 * (`map { it to key(it) }.sortedBy { it.second }.map { it.first }`) appeared
 * at five sort sites in one change — offline library filtering,
 * continue-watching, grouped library rows, user-statistics and
 * scheduled-task ordering. The shape is non-obvious enough that a named
 * helper reads better than a fifth copy.
 */
fun <T, K : Comparable<K>> List<T>.sortedByCachedKey(keySelector: (T) -> K): List<T> =
    sortedWithCachedKey(keySelector, compareBy { it.second })

/**
 * [sortedByCachedKey] for comparators that need more than the key —
 * descending order, or tie-breaks on other element fields. The comparator
 * receives the (element, key) pair, so the key is still computed once.
 */
fun <T, K> List<T>.sortedWithCachedKey(
    keySelector: (T) -> K,
    comparator: Comparator<in Pair<T, K>>,
): List<T> = map { it to keySelector(it) }.sortedWith(comparator).map { it.first }
