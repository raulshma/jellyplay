package com.raulshma.jellyplay.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences-backed [SecureKeyValueStorage]. Byte-for-byte the
 * pre-KMP wiring (AES256_GCM master key, AES256_SIV key encryption, AES256_GCM
 * value encryption) so existing installs read their secrets unchanged.
 */
class AndroidSecureKeyValueStorage(
    context: Context,
    fileName: String,
) : SecureKeyValueStorage {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getString(key: String, defValue: String?): String? =
        prefs.getString(key, defValue)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

}
