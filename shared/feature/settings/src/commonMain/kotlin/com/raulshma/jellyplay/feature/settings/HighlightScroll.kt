package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException

/**
 * Pure resolver behind [rememberHighlightScrollIndex]: the group index whose
 * settings ids contain [highlightSettingId], offset through
 * [adjustForAdvanced]; `-1` when the id is null or matches no group.
 *
 * Each settings sub-screen builds its `LazyColumn` from an ordered list of
 * groups; each group owns a set of setting ids. Passing that single
 * declarative [groupSettingIds] list here — the same list that builds the
 * rows — means the deep-link scroll target can never drift from the actual UI,
 * unlike a hand-maintained `when (id)` table that must be updated in lock-step
 * with composable reordering. [adjustForAdvanced] lets a screen offset the
 * resolved index when an "advanced" group is conditionally hidden (so a hidden
 * advanced group doesn't shift the target by one).
 */
internal fun resolveHighlightScrollIndex(
    highlightSettingId: String?,
    groupSettingIds: List<Set<String>>,
    adjustForAdvanced: (groupIndex: Int) -> Int = { it },
): Int {
    if (highlightSettingId == null) return -1
    val raw = groupSettingIds.indexOfFirst { highlightSettingId in it }
    return if (raw < 0) -1 else adjustForAdvanced(raw)
}

@Composable
fun rememberHighlightScrollIndex(
    highlightSettingId: String?,
    groupSettingIds: List<Set<String>>,
    adjustForAdvanced: (groupIndex: Int) -> Int = { it },
): Int = remember(highlightSettingId, groupSettingIds, adjustForAdvanced) {
    resolveHighlightScrollIndex(highlightSettingId, groupSettingIds, adjustForAdvanced)
}

/**
 * One screen's group slot in LazyColumn order — the payload-free projection a
 * screen declares next to its groups so [deriveAdjustForAdvanced] can read the
 * advanced shape instead of each screen hand-writing an adjust lambda. Pure
 * data: the group's id set plus whether the group only composes behind the
 * advanced toggle. Payload-free BY DESIGN — a composable slot here would
 * reintroduce a slot registry at group grain: the emission bodies stay
 * hand-written in the screens; this type carries only the shape the scroll
 * resolver needs.
 */
internal data class SettingsScreenSection(
    val idSet: Set<String>,
    val advancedOnly: Boolean = false,
)

/**
 * The [rememberHighlightScrollIndex adjustForAdvanced] derivation over a
 * screen's [SettingsScreenSection] declaration — the one adjust function both
 * shipped advanced shapes fold into. With [showAdvanced] true every group
 * composes, so the adjust is the identity. With advanced hidden, an
 * advanced-only group at raw index `i` doesn't compose (its rows cannot take a
 * highlight → `-1`), and every advanced-only group before index `i` shifts the
 * always-visible groups at and after `i` up by one — i.e. `i` minus the count
 * of hidden groups declared before it. The two shipped shapes are special
 * cases of that fold:
 *  - the contiguous-middle shape (PlaybackSettingsScreen: advanced groups at
 *    declared indices 1-3, always-visible groups trailing) — the hidden middle
 *    groups return `-1` and the trailing groups shift up by exactly those
 *    three slots;
 *  - the trailing shape (AppearanceSettingsScreen: expert groups at declared
 *    indices 3-5) — the hidden trailing groups return `-1` and nothing trails
 *    them, so the leading groups keep their indices.
 *
 * Pure (and internal) so `SettingsCatalogScreenContractTest` can pin both
 * shipped shapes against the same derivation the screens consume.
 */
internal fun deriveAdjustForAdvanced(
    sections: List<SettingsScreenSection>,
    showAdvanced: Boolean,
): (Int) -> Int =
    if (showAdvanced) {
        { it }
    } else {
        { raw ->
            if (raw in sections.indices && sections[raw].advancedOnly) {
                -1
            } else {
                raw - sections.take(raw.coerceAtMost(sections.size)).count { it.advancedOnly }
            }
        }
    }

/**
 * Scrolls [scrollState] to the index produced by [rememberHighlightScrollIndex],
 * ignoring a plain scroll failure but rethrowing the cancellation that
 * `animateScrollToItem` throws if the scroll is interrupted (e.g. by a second
 * deep-link arriving mid-animation) — swallowing it would cancel the wrong
 * coroutine's clean shutdown.
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
