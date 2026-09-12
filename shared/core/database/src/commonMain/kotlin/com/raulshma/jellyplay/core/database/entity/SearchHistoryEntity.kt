package com.raulshma.jellyplay.core.database.entity

import com.raulshma.jellyplay.core.model.wallNowMillis
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
    val searchedAt: Long = wallNowMillis(),
)
