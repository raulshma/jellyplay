package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["query", "userId"], unique = true),
        Index(value = ["searchedAt"]),
        Index(value = ["userId", "searchedAt"]),
    ],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val userId: String,
    @ColumnInfo(defaultValue = "0")
    val searchedAt: Long = System.currentTimeMillis(),
)
