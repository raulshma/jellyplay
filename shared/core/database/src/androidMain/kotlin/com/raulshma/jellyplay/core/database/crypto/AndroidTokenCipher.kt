package com.raulshma.jellyplay.core.database.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Production [TokenCipher] for Android: key material lives inside the Android
 * Keystore (hardware-backed on devices that support it). The key is generated
 * on first use with [KEY_ALIAS] and persists across app restarts and device
 * reboots until the user uninstalls the app — identical to the pre-KMP
 * implementation.
 */
class AndroidTokenCipher(
    context: Context,
) : JvmTokenCipher(secretKeyProvider = { loadKeystoreKey(context) }) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jellyplay_token_cipher_v1"

        /**
         * Loads (or creates on first use) the AES-256-GCM key from the Android Keystore.
         * The key persists across app restarts and is identified by [KEY_ALIAS].
         */
        private fun loadKeystoreKey(context: Context): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return keyGenerator.generateKey()
        }
    }
}
