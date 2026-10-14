package com.raulshma.jellyplay.core.ui.image

import androidx.compose.runtime.compositionLocalOf

/**
 * Monotonic counter that a screen hosting a retry gesture (home's
 * pull-to-refresh) bumps when it wants its already-visible images to re-issue
 * failed requests. [MediaImage]'s fallback chain resets its error state when
 * this value changes, so a card stuck on its blurHash after a transient
 * failure (server restart, network blip) recovers on the next refresh instead
 * of staying dead until the row leaves composition.
 *
 * Only FAILED loads retry: a succeeded image keeps its identical Coil model,
 * which is an equality no-op for AsyncImage — no reload, no crossfade flash.
 * The default `0L` means screens that never provide it (details, search) get
 * the previous static behavior.
 */
val LocalImageReloadEpoch = compositionLocalOf { 0L }
