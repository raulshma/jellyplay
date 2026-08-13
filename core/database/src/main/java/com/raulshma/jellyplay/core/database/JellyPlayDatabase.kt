package com.raulshma.jellyplay.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
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
    ],
    version = 47,
    exportSchema = true,
    views = [OfflineMediaWithPlayback::class],
)
@TypeConverters(Converters::class)
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
}
