package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_queue",
    indices = [
        Index(value = ["position"]),
        Index(value = ["createdAt"]),
    ],
)
data class AudioQueueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val imageUrl: String? = null,
    val mediaSourceId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0L,
    val normalizationGain: Float? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audio_queue_state")
data class AudioQueueStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "-1")
    val currentIndex: Int = -1,
    @ColumnInfo(defaultValue = "0")
    val currentPositionMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val isPlaying: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val repeatMode: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val shuffleEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1.0")
    val playbackSpeed: Float = 1.0f,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)
