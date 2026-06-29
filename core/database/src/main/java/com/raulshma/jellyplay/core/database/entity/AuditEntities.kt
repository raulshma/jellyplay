package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_audit_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["actionType"]),
        Index(value = ["actionType", "timestamp"]),
    ],
)
data class MediaAuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val adminUserId: String,
    val adminUserName: String,
    val actionType: String,
    val configJson: String,
    val itemCount: Int,
    val itemDetailsJson: String,
)

@Entity(
    tableName = "scan_state",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class ScanStateEntity(
    @PrimaryKey val scanId: String,
    val type: String,
    val configJson: String,
    val status: String,
    @ColumnInfo(defaultValue = "0")
    val progress: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val total: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val itemsFound: Int = 0,
    val resultJson: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
)
