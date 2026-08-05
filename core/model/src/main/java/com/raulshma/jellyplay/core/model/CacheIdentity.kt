package com.raulshma.jellyplay.core.model

/**
 * The `(serverId, userId)` pair that scopes a cache entry to a single account
 * on a single server. Encoded once and threaded into [TtlCache]'s identity-aware
 * overloads so a wrong identity is a guaranteed cache miss — closing the
 * cross-user leak surface without a parallel invalidation API.
 *
 * `core:model` already owns [ServerInfo] / [UserInfo]; this is the natural home
 * for the identity pair they form.
 *
 * Construct via [of] from a real logged-in session, or use [UNKNOWN] before
 * login / after logout (nothing cached under that key can leak across users,
 * since no real identity ever collides with it).
 */
@JvmInline
value class CacheIdentity private constructor(val encoded: String) {
    companion object {
        /** Sentinel used before login / after logout — never collides with a real identity. */
        val UNKNOWN = CacheIdentity("__unknown__")

        /** Encodes a `(serverId, userId)` pair as a stable cache-key segment. */
        fun of(serverId: String, userId: String): CacheIdentity = CacheIdentity("$serverId/$userId")

        /**
         * Encodes a `(serverId, userId)` pair as a stable cache-key segment,
         * or returns [UNKNOWN] if either id is null (before login / after
         * logout). The single source for the null-guarded `CacheIdentity.of()`
         * shape both `MediaRepositoryImpl.currentIdentity()` (reads an
         * `AtomicReference` mirror) and `LibraryApiClientImpl.currentHomeCacheIdentity()`
         * (reads engine `StateFlow`s) used to hand-roll inline.
         */
        fun ofOrNull(serverId: String?, userId: String?): CacheIdentity {
            val s = serverId ?: return UNKNOWN
            val u = userId ?: return UNKNOWN
            return of(s, u)
        }
    }
}
