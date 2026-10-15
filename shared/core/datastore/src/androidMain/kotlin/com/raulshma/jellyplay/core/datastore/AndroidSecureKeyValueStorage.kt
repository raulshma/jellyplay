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
 *
 * The heal decisions are platform-free — [SecureStorageHealMachine] (pinned by
 * jvmTest against fake operations) — and this class is its thin adapter: the
 * real open/delete operations plus the storage instances.
 */
class AndroidSecureKeyValueStorage(
    private val context: Context,
    private val fileName: String,
) : SecureKeyValueStorage {

    // Rebuildable: a crypto failure swaps in a fresh empty instance after
    // deleting the poisoned file (the machine's reset path).
    private val healMachine = SecureStorageHealMachine(
        fallback = InMemoryPreferences(),
        open = { open() },
        delete = { context.deleteSharedPreferences(fileName) },
    )

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
     * Genuinely process-local store for the reset path: writes land in a
     * plain map, NEVER on disk. A file-backed fallback (a second
     * SharedPreferences name) would persist credentials unencrypted — and
     * keep them there even after the encrypted store recovers on a later
     * launch. Values saved here instead die with the process: once the
     * encrypted store is reachable again, anything written to the fallback
     * is gone and the user re-enters credentials.
     */
    private class InMemoryPreferences : SharedPreferences {
        private val values = HashMap<String, Any?>()

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("In-memory fallback serves the string seam only")

        override fun getAll(): Map<String, Any?> = HashMap(values)

        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = unsupported()
        override fun getInt(key: String, defValue: Int): Int = unsupported()
        override fun getLong(key: String, defValue: Long): Long = unsupported()
        override fun getFloat(key: String, defValue: Float): Float = unsupported()
        override fun getBoolean(key: String, defValue: Boolean): Boolean = unsupported()
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = unsupported()

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = unsupported()

        private inner class Editor : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                if (value == null) remove(key) else {
                    removals.remove(key)
                    pending[key] = value
                }
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = unsupported()
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = unsupported()
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = unsupported()
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = unsupported()
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = unsupported()

            override fun remove(key: String): SharedPreferences.Editor = apply {
                removals.add(key)
                pending.remove(key)
            }

            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun apply() { commit() }

            override fun commit(): Boolean = synchronized(this@InMemoryPreferences) {
                if (clearAll) values.clear()
                values.keys.removeAll(removals)
                values.putAll(pending)
                true
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? =
        healMachine.guarded { prefs -> prefs.getString(key, defValue) }

    override fun putString(key: String, value: String?) {
        healMachine.guarded { prefs -> prefs.edit().putString(key, value).apply() }
    }

    override fun remove(key: String) {
        healMachine.guarded { prefs -> prefs.edit().remove(key).apply() }
    }
}
