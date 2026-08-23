package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

/**
 * Resolves a deep-link [highlightSettingId] to a LazyColumn group index and
 * scrolls to it.
 *
 * Each settings sub-screen builds its `LazyColumn` from an ordered list of
 * groups; each group owns a set of setting ids. Passing that single
 * declarative [groupSettingIds] list here — the same list used to build the
 * rows — means the deep-link scroll target can never drift from the actual UI,
 * unlike a hand-maintained `when (id)` table that must be updated in lock-step
 * with composable reordering.
 *
 * [adjustForAdvanced] lets a screen offset the resolved index when an "advanced"
 * group is conditionally hidden (so a hidden advanced group doesn't shift the
 * target by one).
 */
@Composable
fun rememberHighlightScrollIndex(
    highlightSettingId: String?,
    groupSettingIds: List<Set<String>>,
    adjustForAdvanced: (groupIndex: Int) -> Int = { it },
): Int = remember(highlightSettingId, groupSettingIds, adjustForAdvanced) {
    if (highlightSettingId == null) return@remember -1
    val raw = groupSettingIds.indexOfFirst { highlightSettingId in it }
    if (raw < 0) -1 else adjustForAdvanced(raw)
}

/**
 * Scrolls [scrollState] to the index produced by [rememberHighlightScrollIndex],
 * swallowing the cancellation that `animateScrollToItem` throws if the scroll
 * is interrupted (e.g. by a second deep-link arriving mid-animation).
 */
@Composable
fun HighlightScrollEffect(scrollState: LazyListState, scrollIndex: Int) {
    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // animateScrollToItem failed (e.g. index no longer valid) — ignore.
            }
        }
    }
}
