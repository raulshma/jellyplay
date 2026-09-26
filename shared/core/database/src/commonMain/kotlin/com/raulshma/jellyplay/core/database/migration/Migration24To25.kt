package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.raulshma.jellyplay.core.database.crypto.TokenCipher

/**
 * Migration v24 → v25: encrypts plaintext Jellyfin
 * access tokens stored in the `users.accessToken` and `servers.accessToken` columns using
 * the Keystore-backed [TokenCipher]. Existing encrypted rows are left untouched (cipher is
 * idempotent), and rows with empty/null tokens are skipped.
 *
 * After this migration runs, the DB columns contain ciphertext that's only readable via
 * [TokenCipher.decrypt]. The encryption key lives in the Android Keystore and never leaves
 * the device, so an attacker extracting the DB file via `adb backup` or root access cannot
 * read the tokens.
 *
 * If the Keystore is transiently unavailable (rare — usually right after a fresh boot before
 * the user has unlocked the device), the migration re-throws to surface the failure; Room
 * will retry on next launch.
 */
class Migration24To25(
    private val tokenCipher: TokenCipher,
) : Migration(24, 25) {
    override suspend fun migrate(db: SQLiteConnection) {
        encryptUserTokens(db)
        encryptServerTokens(db)
    }

    private suspend fun encryptUserTokens(db: SQLiteConnection) {
        db.collectRowsThenUpdate("SELECT userId, accessToken FROM users") { userId, rawToken ->
            val encrypted = try {
                tokenCipher.encrypt(rawToken)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Migration 24→25: failed to encrypt token for user $userId. " +
                        "Aborting migration so Room can retry on next launch.",
                    e,
                )
            }
            if (encrypted != rawToken) {
                db.execSQL(
                    "UPDATE users SET accessToken = ? WHERE userId = ?",
                    arrayOf(encrypted ?: "", userId),
                )
            }
        }
    }

    private suspend fun encryptServerTokens(db: SQLiteConnection) {
        db.collectRowsThenUpdate("SELECT id, accessToken FROM servers") { serverId, rawToken ->
            val encrypted = try {
                tokenCipher.encrypt(rawToken)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Migration 24→25: failed to encrypt token for server $serverId.",
                    e,
                )
            }
            if (encrypted != rawToken) {
                db.execSQL(
                    "UPDATE servers SET accessToken = ? WHERE id = ?",
                    arrayOf(encrypted, serverId),
                )
            }
        }
    }
}

/**
 * Collect-then-update scanning lives in SQLiteMigrationCompat.kt
 * (`SQLiteConnection.collectRowsThenUpdate`), expect/actual so the metadata
 * compilation never sees the platform-only `prepare`/`step` calls.
 */
