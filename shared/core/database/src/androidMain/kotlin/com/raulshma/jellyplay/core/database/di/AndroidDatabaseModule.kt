package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.AndroidTokenCipher
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.migration.allMigrations
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform Koin module (docs/kmp-migration-plan.md §Phase C4).
 * Byte-for-byte the legacy Hilt DatabaseModule wiring: Android Keystore
 * TokenCipher, "jellyplay.db" name, full migration chain, destructive
 * fallback on downgrade, WAL journal mode.
 */
fun androidDatabaseModule(context: Context): Module = module {

    single<TokenCipher> { AndroidTokenCipher(context) }

    single {
        Room.databaseBuilder(
            context,
            JellyPlayDatabase::class.java,
            "jellyplay.db",
        )
            .addMigrations(*allMigrations(get<TokenCipher>()).toTypedArray())
            .fallbackToDestructiveMigrationOnDowngrade()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
