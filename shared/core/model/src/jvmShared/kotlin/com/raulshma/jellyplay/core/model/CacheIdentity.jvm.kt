package com.raulshma.jellyplay.core.model

/**
 * JVM actual of [CacheIdentity] (android + desktop): the exact historical
 * `@JvmInline value class` — inline representation, equality by the encoded
 * string, same `"$serverId/$userId"` encoding and `"__unknown__"` sentinel.
 */
@JvmInline
actual value class CacheIdentity private actual constructor(public actual val encoded: String) {
    actual companion object {
        actual val UNKNOWN: CacheIdentity
            get() = CacheIdentity("__unknown__")

        actual fun of(serverId: String, userId: String): CacheIdentity =
            CacheIdentity("$serverId/$userId")

        actual fun ofOrNull(serverId: String?, userId: String?): CacheIdentity {
            val s = serverId ?: return UNKNOWN
            val u = userId ?: return UNKNOWN
            return of(s, u)
        }
    }
}
