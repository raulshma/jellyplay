package com.raulshma.jellyplay.core.database.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.DesktopTokenCipher
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.migration.allMigrations
import java.io.File
import okio.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform Koin module (docs/kmp-migration-plan.md §Phase C4).
 * [dbPath] is the full path of the `jellyplay.db` file; the DesktopTokenCipher
 * key file (`token.key`) sits in the same directory. Builder arrangement
 * mirrors the jvmTest DAO/migration setup: BundledSQLiteDriver over the
 * file-based Room builder with the full migration chain.
 */
fun desktopDatabaseModule(dbPath: Path): Module {
    val keyDirectory: File = dbPath.parent?.toFile() ?: File(".")
    return module {

        single<TokenCipher> { DesktopTokenCipher(keyDirectory) }

        single {
            Room.databaseBuilder<JellyPlayDatabase>(dbPath.toString())
                .addMigrations(*allMigrations(get<TokenCipher>()).toTypedArray())
                // dropAllTables=true matches the Android no-arg overload's
                // behavior (the KMP builder makes the flag explicit).
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .setDriver(BundledSQLiteDriver())
                .build()
        }
    }
}
