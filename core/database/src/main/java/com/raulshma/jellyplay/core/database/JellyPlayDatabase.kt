package com.raulshma.jellyplay.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
        LyricsCacheEntity::class,
        OfflineMediaEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class JellyPlayDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun userDao(): UserDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun offlineMediaDao(): OfflineMediaDao
}
