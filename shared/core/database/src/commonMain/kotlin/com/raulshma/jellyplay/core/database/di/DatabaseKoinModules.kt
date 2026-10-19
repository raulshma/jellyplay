package com.raulshma.jellyplay.core.database.di

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import org.koin.dsl.module

/**
 * Koin construction owner for the JellyPlayDatabase DAOs. Mirrors the
 * DatabaseModule provider list one-to-one; the JellyPlayDatabase instance
 * itself (and the TokenCipher feeding the migration chain) comes from the
 * platform modules [androidDatabaseModule] / [desktopDatabaseModule].
 *
 * Every declaration it touches ([JellyPlayDatabase] and the DAO getters) is
 * commonMain Room 3, and Koin 4.2.2 koin-core is multiplatform, so the DAO
 * bindings resolve identically on android/desktop.
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

    single { get<JellyPlayDatabase>().bookBookmarkDao() }

    single { get<JellyPlayDatabase>().bookAnnotationDao() }
    single { get<JellyPlayDatabase>().bookTocCacheDao() }
}
