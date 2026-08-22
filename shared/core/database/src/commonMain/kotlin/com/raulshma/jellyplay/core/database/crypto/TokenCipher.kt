package com.raulshma.jellyplay.core.database.crypto

/**
 * Encrypts and decrypts short strings (auth tokens) using an AES-256-GCM key.
 * Used to keep Jellyfin `accessToken` values opaque at rest in Room.
 *
 * - The ciphertext is Base64-encoded so it round-trips through SQLite TEXT
 *   columns safely.
 * - Format: `v1$<iv-base64>$<ct-base64>` — a version prefix leaves room for
 *   future key rotation without breaking existing rows.
 * - `encrypt` is idempotent: passing an already-encrypted value returns it
 *   unchanged.
 * - `decrypt` is forward-compatible: passing plaintext (e.g. a row not yet
 *   migrated) returns it unchanged. This makes the migration path safe — a row
 *   written before v25 still reads correctly.
 *
 * Null and empty inputs are passed through unchanged so callers don't need to
 * special-case missing tokens.
 *
 * Platform implementations: Android keeps the key in the Android Keystore
 * (hardware-backed where available); desktop stores a generated key file under
 * the OS user directory (replaced by an OS-keychain-backed provider if the
 * desktop security work in Phase V1 calls for it).
 */
interface TokenCipher {
    fun encrypt(plaintext: String?): String?

    fun decrypt(ciphertext: String?): String?

    /** Returns true if [value] was produced by [encrypt]. */
    fun isEncrypted(value: String?): Boolean
}
