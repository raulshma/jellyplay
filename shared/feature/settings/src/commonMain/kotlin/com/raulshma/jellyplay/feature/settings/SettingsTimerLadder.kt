package com.raulshma.jellyplay.feature.settings

/**
 * The ONE duration ladder (ms) behind the two "after this long" pickers that
 * step in the same 30s→10m increments: Security's auto-lock row takes the
 * whole ladder; the screensaver's dim-after row takes the prefix without the
 * 10-minute rung (`dropLast(1)`). Each consumer resolves its own labels in
 * composition (the rows own their wording) and indexes the stored value with
 * the `indexOf(...).coerceAtMost(lastIndex)` clamp, so a value outside the
 * ladder degrades to the nearest rung instead of throwing.
 */
internal val SETTINGS_TIMER_LADDER_MS: List<Long> = listOf(0L, 30_000L, 60_000L, 300_000L, 600_000L)
