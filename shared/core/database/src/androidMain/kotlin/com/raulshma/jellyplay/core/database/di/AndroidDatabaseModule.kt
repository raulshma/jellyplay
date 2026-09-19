package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.AndroidTokenCipher
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.migration.ContainerProbe
import com.raulshma.jellyplay.core.database.migration.allMigrations
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform Koin module (docs/kmp-migration-plan.md).
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
            // ContainerProbe (Migration53To54's download-container backfill)
            // resolves lazily from :shared:core:data's dataJvmModule — the
            // closest Koin module to both the sniffer and java.io, since this
            // module can see neither (core:data is downstream of core:database).
            .addMigrations(*allMigrations(get<TokenCipher>(), get<ContainerProbe>()).toTypedArray())
            .fallbackToDestructiveMigrationOnDowngrade()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
