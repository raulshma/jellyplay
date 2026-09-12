package com.raulshma.jellyplay.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "seen_media",
    indices = [
        Index(value = ["itemId"], unique = true),
        Index(value = ["seenAt"]),
    ],
)
data class SeenMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val libraryId: String,
    val mediaType: String,
    val seenAt: Long,
)
