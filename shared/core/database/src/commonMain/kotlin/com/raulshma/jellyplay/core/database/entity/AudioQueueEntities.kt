package com.raulshma.jellyplay.core.database.entity

import com.raulshma.jellyplay.core.model.wallNowMillis
import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
    val createdAt: Long = wallNowMillis(),
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
    /**
     * The `kotlin.random.Random` seed the current shuffle order was generated
     * with (the finamp pattern) — null when shuffle is off or the row
     * predates the column. The persisted `audio_queue` rows already ARE the
     * shuffled order; the seed makes that order reproducible (a re-shuffle
     * cycle with the same input order yields the exact same arrangement).
     */
    val shuffleSeed: Long? = null,
    @ColumnInfo(defaultValue = "1.0")
    val playbackSpeed: Float = 1.0f,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = wallNowMillis(),
)
