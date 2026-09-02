package com.raulshma.jellyplay.core.database.di

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import org.koin.dsl.module

/**
 * Koin construction owner for the JellyPlayDatabase DAOs
 * (docs/kmp-migration-plan.md §Phase C4). Mirrors the legacy Hilt
 * DatabaseModule provider list one-to-one; the JellyPlayDatabase instance
 * itself (and the TokenCipher feeding the migration chain) comes from the
 * platform modules [androidDatabaseModule] / [desktopDatabaseModule].
 */
val databaseDaosModule = module {

    single { get<JellyPlayDatabase>().serverDao() }

    single { get<JellyPlayDatabase>().userDao() }

    single { get<JellyPlayDatabase>().downloadDao() }

    single { get<JellyPlayDatabase>().lyricsCacheDao() }

    single { get<JellyPlayDatabase>().offlineMediaDao() }

    single { get<JellyPlayDatabase>().playbackStateDao() }

    single { get<JellyPlayDatabase>().syncBaselineDao() }

    single { get<JellyPlayDatabase>().auditLogDao() }

    single { get<JellyPlayDatabase>().scanStateDao() }

    single { get<JellyPlayDatabase>().smartPlaylistDao() }

    single { get<JellyPlayDatabase>().moodPlaylistDao() }

    single { get<JellyPlayDatabase>().audioQueueDao() }

    single { get<JellyPlayDatabase>().searchHistoryDao() }

    single { get<JellyPlayDatabase>().seenMediaDao() }

    single { get<JellyPlayDatabase>().itemPlaybackPreferenceDao() }

    single { get<JellyPlayDatabase>().playbackOutboxDao() }

    single { get<JellyPlayDatabase>().homeSectionCacheDao() }
}
