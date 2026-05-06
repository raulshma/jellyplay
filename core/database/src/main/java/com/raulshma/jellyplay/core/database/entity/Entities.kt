package com.raulshma.jellyplay.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val userId: String? = null,
    val accessToken: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val serverId: String,
    val name: String,
    val accessToken: String,
    val primaryImageTag: String? = null,
    val maxParentalAgeRating: Int? = null,
    val enabledFolderIds: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["mediaItemId"]),
        Index(value = ["status"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val downloadPath: String,
    val downloadUrl: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val speedBytesPerSec: Long = 0L,
    val mediaSourceId: String? = null,
    val imageUrl: String? = null,
    val imageBlurHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
