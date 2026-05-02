package com.raulshma.jellyplay.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.ServerEntity

@Database(
    entities = [
        ServerEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class JellyPlayDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
}
