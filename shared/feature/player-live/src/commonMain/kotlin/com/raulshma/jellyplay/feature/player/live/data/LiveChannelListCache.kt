package com.raulshma.jellyplay.feature.player.live.data

import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.monotonicNowMillis

/**
 * Session-scoped TTL memo for the live player's channel list (the
 * `GET /LiveTv/Channels` snapshot the zap machine steps over).
 *
 * The player refetched the list on every entry — a cold round trip standing
 * between a re-entry and playback for a list that only changes when channels
 * are added/removed server-side. This cache mirrors the codebase's
 * client-level [TtlCache] idiom (the `core:network` API clients'
 * near-static-response memos): one process-singleton instance (Koin owns it
 * in `playerLiveModule`, alongside [LastChannelStore]) so the memoized list
 * survives player re-entries, entries expire on the TTL below, and the
 * scope key keeps a previous (server, user) pair's list from ever being
 * served to the next one.
 *
 * Scope keying: the live session's [com.raulshma.jellyplay.core.data.playback.PlaybackIdentity]
 * exposes the active server URL + access token (per-user, so a token covers
 * the user axis the way a `CacheIdentity` (serverId, userId) pair would —
 * the player-live module has no engine seam to read those ids from). A null
 * server/token (no active session) bypasses the cache entirely: fetch fresh
 * and do not memoize.
 *
 * Only SUCCESSFUL fetches are memoized — a failed load is retried on the
 * next entry exactly as before. Channel-list invalidation beyond the TTL:
 * the app has no channel-edit surface and emits no channel-list-change
 * signal, so the TTL is the only staleness bound.
 */
class LiveChannelListCache(
    private val clock: () -> Long = ::monotonicNowMillis,
) {

    private val cache = TtlCache<List<LiveTvChannel>>(
        maxSize = 1,
        ttlMs = TTL_MS,
        clock = clock,
    )

    /**
     * Returns the memoized list for [scopeKey] within the TTL, or null on a
     * miss/expiry (the caller fetches and calls [put]).
     */
    fun get(scopeKey: String): List<LiveTvChannel>? = cache.get(compositeKey(scopeKey))

    /** Memoizes a successful channel-list fetch under [scopeKey]. */
    fun put(scopeKey: String, channels: List<LiveTvChannel>) {
        cache.put(compositeKey(scopeKey), channels)
    }

    private fun compositeKey(scopeKey: String): String = "$KEY_PREFIX::$scopeKey"

    companion object {
        private const val KEY_PREFIX = "live_player_channels"

        /**
         * 5 minutes — the list is near-static within a viewing session (the
         * same freshness family as the other near-static-response caches),
         * and a re-entry inside the window skips the round trip.
         */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
