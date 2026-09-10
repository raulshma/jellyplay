package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Resume rows the user can actually continue — the client-side half of the
 * #157 rule, applied by every `getContinueWatching` implementation
 * (JVM `LibraryApiClientImpl`, wasm `KtorWasmLibraryApiClient`).
 *
 * /Items/Resume filters on PlaybackPositionTicks > 0 only — it does NOT
 * exclude played items. A position report landing on an already-played item
 * (a sub-threshold replayed STOP, a brief re-watch of a finished episode)
 * leaves the server with Played=true + position>0, which stays resumable
 * forever because nothing server-side ever resets it. Dropping played rows
 * here keeps a watched episode from occupying Continue Watching — the same
 * rule the offline row enforces (OfflineHomeSections) and the one behind
 * `MediaItem.hasWatchProgress`. The server-side half is the
 * `PlayedStateSyncImpl.reconcileOfflineRow` self-heal, which zeroes the
 * poisoned position at the source.
 *
 * The filter runs after the server applied its `limit`, so heavy poisoning
 * can leave the row short of `limit`; the self-heal repairs the server rows,
 * shrinking the gap. Over-fetching to compensate was considered and
 * rejected: the pre-existing parental-rating filter trims post-limit the
 * same way, so the row already accepts under-fill there.
 */
fun List<MediaItem>.resumableOnly(): List<MediaItem> = filter { !it.isPlayed }
