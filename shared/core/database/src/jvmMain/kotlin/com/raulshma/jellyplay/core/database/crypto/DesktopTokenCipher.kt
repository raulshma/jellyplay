package com.raulshma.jellyplay.core.database.crypto

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Production [TokenCipher] for desktop: a generated AES-256 key stored in a
 * `token.key` file under [keyDirectory] (owner-only permissions where the OS
 * supports them). Weaker than Android's hardware-backed Keystore by design of
 * desktop OSes; revisit in Phase V1 hardening if the desktop security work
 * wants an OS-keychain-backed provider instead.
 */
class DesktopTokenCipher(
    keyDirectory: File,
) : JvmTokenCipher(secretKeyProvider = { loadOrCreateKeyFile(keyDirectory) }) {

    companion object {
        private fun loadOrCreateKeyFile(keyDirectory: File): SecretKey {
            val keyFile = File(keyDirectory, "token.key")
            val bytes = if (keyFile.exists()) {
                keyFile.readBytes().also { require(it.size == 32) { "Corrupt token key file" } }
            } else {
                ByteArray(32).also { SecureRandom().nextBytes(it) }.also { fresh ->
                    keyDirectory.mkdirs()
                    keyFile.writeBytes(fresh)
                    runCatching {
                        Files.setPosixFilePermissions(
                            keyFile.toPath(),
                            PosixFilePermissions.fromString("rw-------"),
                        )
                    } // Windows FAT/NTFS: POSIX perms unsupported; NTFS ACLs stay per-user
                }
            }
            return SecretKeySpec(bytes, "AES")
        }
    }
}
