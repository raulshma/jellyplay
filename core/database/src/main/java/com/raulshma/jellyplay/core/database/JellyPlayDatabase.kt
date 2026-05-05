package com.raulshma.jellyplay.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class JellyPlayDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun userDao(): UserDao
    abstract fun downloadDao(): DownloadDao
}
