package com.raulshma.jellyplay.core.datastore

/**
 * PIN hashing contract (see the JVM actual for format history):
 * `v2$<iterations>$<saltHex>$<hashHex>` via PBKDF2-HMAC-SHA256, with legacy
 * unsalted SHA-256 hashes still accepted by [verify] so existing users aren't
 * locked out after upgrade.
 */
internal expect object PinHasher {
    internal val V2_PREFIX: String
    internal val PBKDF2_ITERATIONS: Int
    internal val PBKDF2_KEY_BITS: Int
    internal val SALT_BYTES: Int

    fun hash(pin: String): String

    fun verify(input: String, storedHash: String?): Boolean

    fun needsMigration(storedHash: String?): Boolean
}
