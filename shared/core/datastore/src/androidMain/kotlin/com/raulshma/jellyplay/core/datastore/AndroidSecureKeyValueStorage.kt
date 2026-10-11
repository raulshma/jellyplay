package com.raulshma.jellyplay.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences-backed [SecureKeyValueStorage]. Byte-for-byte the
 * pre-KMP wiring (AES256_GCM master key, AES256_SIV key encryption, AES256_GCM
 * value encryption) so existing installs read their secrets unchanged.
 *
 * SELF-HEALING on undecryptable state (issue #171's fresh-install crash loop):
 * the master key lives in AndroidKeystore, which NEVER survives uninstall,
 * backup restore or device transfer — but `allowBackup` happily restores the
 * encrypted XML files. Opening or reading such a file throws
 * `KeyStoreException` ("Signature/MAC verification failed") /
 * `AEADBadTagException` / `InvalidProtocolBufferException`. These reads run
 * from store constructors on the eager Koin startup path, so one poisoned
 * file crashed the app on every launch ("app keeps stopping"). The store
 * layer's "degrade to empty rather than crash" contract (see
 * [ArrSecureCredentialsStore.getManualServers]) can only hold if THIS seam
 * absorbs the crypto failure: the ciphertext is undecryptable forever (its
 * key is gone), so the only recovery is to wipe the file and start over.
 * Lost secrets are re-enterable; a crash loop is not.
 */
class AndroidSecureKeyValueStorage(
    private val context: Context,
    private val fileName: String,
) : SecureKeyValueStorage {

    // Rebuildable: a crypto failure swaps in a fresh empty instance after
    // deleting the poisoned file. The constructor open can itself throw on a
    // restored corrupt keyset (see class doc), so it takes the same reset
    // path as the read/write guards below.
    private var prefs: SharedPreferences = runCatching { open() }
        .getOrElse { reset() }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Deletes the file and rebuilds an empty encrypted store. Best effort: if
     * even the fresh open fails (keystore unavailable), an in-memory empty
     * prefs keeps reads/writes from crashing — the secrets were unreadable
     * either way.
     */
    private fun reset(): SharedPreferences {
        runCatching { context.deleteSharedPreferences(fileName) }
        prefs = runCatching { open() }.getOrElse { emptyPreferences() }
        return prefs
    }

    private fun emptyPreferences(): SharedPreferences =
        context.getSharedPreferences("${fileName}__memory_fallback", Context.MODE_PRIVATE)

    private fun <T> reading(fallback: T, block: SharedPreferences.() -> T): T =
        try {
            prefs.block()
        } catch (_: Exception) {
            reset().block()
        }

    override fun getString(key: String, defValue: String?): String? =
        reading(fallback = defValue) { getString(key, defValue) }

    override fun putString(key: String, value: String?) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (_: Exception) {
            reset().edit().putString(key, value).apply()
        }
    }

    override fun remove(key: String) {
        try {
            prefs.edit().remove(key).apply()
        } catch (_: Exception) {
            reset().edit().remove(key).apply()
        }
    }
}
