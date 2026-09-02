package com.raulshma.jellyplay.core.model

/**
 * wasmJs actual of [CacheIdentity]: same value-class semantics and encoding
 * as the JVM actual, minus `@JvmInline` (a JVM-only representation hint —
 * kotlin/wasm boxes value classes where it cannot unbox).
 */
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
