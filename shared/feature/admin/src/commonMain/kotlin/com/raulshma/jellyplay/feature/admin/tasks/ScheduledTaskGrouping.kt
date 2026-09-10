package com.raulshma.jellyplay.feature.admin.tasks

import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.sortedByCachedKey

/**
 * Groups scheduled tasks by category, mirroring jellyfin-web's
 * `getCategories()` / `getTasksByCategory()`: tasks with a blank category are
 * dropped, categories are sorted alphabetically (locale-aware, case-insensitive),
 * and tasks within a category are sorted by lowercase name.
 *
 * Extracted as a pure function so the [ScheduledTasksScreen] composable can memoize
 * the result (via `remember`) instead of re-running the `filter` / `groupBy` /
 * `toSortedMap` / `sortedBy` chain on every recomposition — which allocated several
 * intermediate collections and re-sorted on each state change.
 *
 * Returns an ordered list of `(category, tasks)` pairs; the order is deterministic:
 * categories ascending (case-insensitive), tasks within a category by lowercase name
 * (stable sort, so equal names keep their original relative order).
 */
internal fun groupScheduledTasksByCategory(
    tasks: List<ScheduledTaskInfo>,
): List<Pair<String, List<ScheduledTaskInfo>>> =
    tasks
        .filter { !it.category.isNullOrBlank() }
        .groupBy { it.category!! }
        .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        .mapValues { (_, grouped) -> grouped.sortedByCachedKey { it.name.lowercase() } }
        .map { (category, grouped) -> category to grouped }
