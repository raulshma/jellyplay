package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the primary scrollable surface is currently idle (not being
 * scrolled). Provided by screen-level composables that own a LazyListState /
 * LazyGridState so leaf composables (e.g. [rememberDominantColor]) can defer
 * non-essential work — Palette extraction, heavy prefetch — until scroll
 * settles, avoiding CPU contention with image decodes during fast scroll.
 *
 * Defaults to `true` (idle) so composables outside a scrollable surface are
 * unaffected.
 */
val LocalScrollIdle = compositionLocalOf<() -> Boolean> { { true } }
