package com.raulshma.jellyplay.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pending playback-progress event that could not be delivered to the
 * Jellyfin server (e.g. because the device was offline). The
 * [PlaybackSyncWorker][com.raulshma.jellyplay.core.data.worker.PlaybackSyncWorker]
 * drains these entries when connectivity is restored.
 *
 * `eventType` is one of `"START"`, `"PROGRESS"`, `"STOP"` (see
 * [PlaybackOutboxEventType]). `playMethod` stores the
 * [PlayMethod][com.raulshma.jellyplay.core.model.PlayMethod] enum name.
 *
 * `recordedAt` (epoch millis) records when the event was captured locally —
 * used for latest-wins reconciliation against the server's `lastPlayedDate`.
 */
@Entity(
    tableName = "playback_outbox",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["createdAt"]),
    ],
)
data class PlaybackOutboxEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val eventType: String,
    val sessionId: String,
    @ColumnInfo(defaultValue = "0") val positionTicks: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isPaused: Boolean = false,
    @ColumnInfo(defaultValue = "'DIRECT_PLAY'") val playMethod: String = "DIRECT_PLAY",
    val mediaSourceId: String? = null,
    @ColumnInfo(defaultValue = "0") val recordedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
)
