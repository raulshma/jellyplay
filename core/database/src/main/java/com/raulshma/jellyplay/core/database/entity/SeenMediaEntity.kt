package com.raulshma.jellyplay.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
