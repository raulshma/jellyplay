package com.raulshma.jellyplay.core.datastore

/**
 * wasm actual is a placeholder: web v1 ships no PIN lock (plan §Phase W scope
 * cut) and the stdlib exposes no synchronous PBKDF2. Phase W either gates the
 * PIN feature off at the UI layer or ports hashing to WebCrypto — until then
 * every call throws rather than silently downgrading to a weak hash.
 */
internal actual object PinHasher {
    internal actual val V2_PREFIX: String = "v2\$"
    internal actual val PBKDF2_ITERATIONS: Int = 310_000
    internal actual val PBKDF2_KEY_BITS: Int = 256
    internal actual val SALT_BYTES: Int = 16

    actual fun hash(pin: String): String =
        throw UnsupportedOperationException("PIN hashing is not available on wasmJs (web v1 scope cut)")

    actual fun verify(input: String, storedHash: String?): Boolean =
        throw UnsupportedOperationException("PIN hashing is not available on wasmJs (web v1 scope cut)")

    actual fun needsMigration(storedHash: String?): Boolean =
        throw UnsupportedOperationException("PIN hashing is not available on wasmJs (web v1 scope cut)")
}
