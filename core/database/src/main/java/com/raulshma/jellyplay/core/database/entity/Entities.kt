package com.raulshma.jellyplay.core.database.entity

import androidx.room.Entity
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

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mediaItemId: String,
    val name: String,
    val mediaType: String,
    val downloadPath: String,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val mediaSourceId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
