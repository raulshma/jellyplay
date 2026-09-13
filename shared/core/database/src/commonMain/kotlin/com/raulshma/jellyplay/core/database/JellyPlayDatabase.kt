package com.raulshma.jellyplay.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.RoomDatabaseConstructor
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
import com.raulshma.jellyplay.core.database.dao.BookAnnotationDao
import com.raulshma.jellyplay.core.database.dao.BookBookmarkDao
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackOutboxDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.dao.SearchHistoryDao
import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import com.raulshma.jellyplay.core.database.entity.SeenMediaEntity
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity

/**
 * Current Room schema version. Single source of truth: bumping this requires a
 * corresponding [com.raulshma.jellyplay.core.database.migration.Migration] from
 * the previous version in [com.raulshma.jellyplay.core.database.migration.allMigrations];
 * `allMigrations_coversContiguousRange` enforces that chain.
 */
const val JELLY_PLAY_DATABASE_VERSION: Int = 55

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
        LyricsCacheEntity::class,
        OfflineMediaEntity::class,
        PlaybackStateEntity::class,
        SyncBaselineEntity::class,
        MediaAuditLogEntity::class,
        ScanStateEntity::class,
        SmartPlaylistEntity::class,
        MoodPlaylistEntity::class,
        MoodPlaylistPreferenceEntity::class,
        AudioQueueEntity::class,
        AudioQueueStateEntity::class,
        SearchHistoryEntity::class,
        SeenMediaEntity::class,
        ItemPlaybackPreferenceEntity::class,
        PlaybackOutboxEntity::class,
        HomeSectionCacheEntity::class,
        BookBookmarkEntity::class,
        BookAnnotationEntity::class,
    ],
    version = JELLY_PLAY_DATABASE_VERSION,
    exportSchema = true,
    views = [OfflineMediaWithPlayback::class],
)
@ConstructedBy(JellyPlayDatabaseConstructor::class)
@ColumnTypeConverters(Converters::class)
abstract class JellyPlayDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun userDao(): UserDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun offlineMediaDao(): OfflineMediaDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun syncBaselineDao(): SyncBaselineDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun scanStateDao(): ScanStateDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
    abstract fun moodPlaylistDao(): MoodPlaylistDao
    abstract fun audioQueueDao(): AudioQueueDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun seenMediaDao(): SeenMediaDao
    abstract fun itemPlaybackPreferenceDao(): ItemPlaybackPreferenceDao
    abstract fun playbackOutboxDao(): PlaybackOutboxDao
    abstract fun homeSectionCacheDao(): HomeSectionCacheDao
    abstract fun bookBookmarkDao(): BookBookmarkDao
    abstract fun bookAnnotationDao(): BookAnnotationDao
}

/**
 * Room 3 KMP instantiation seam (required once the module targets non-Android
 * platforms): the wasmJs/jvm Room builders construct the database through
 * this expect — each target's KSP run generates the actual that instantiates
 * the generated JellyPlayDatabase_Impl. Android keeps its Context-based
 * builder path, but the annotation applies commonMain-wide and Room's android
 * processing generates its actual identically.
 */
expect object JellyPlayDatabaseConstructor : RoomDatabaseConstructor<JellyPlayDatabase> {
    override fun initialize(): JellyPlayDatabase
}
