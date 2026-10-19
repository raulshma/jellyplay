package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.HomeSession

/**
 * Shared test harness for the [UserDataAnnouncer] leaf: builds the announcer
 * with the #157 confirmed-write arming wired to the repository the harness
 * is building (the DI wiring resolves the same hook through
 * `MediaRepositoryImpl.armStaleReadGroupsForConfirmedWrite`). The provider
 * is late-binding — the announcer is constructed BEFORE the repository it
 * arms (the announcer is a ctor dependency of the repository), so the lambda
 * reads the built instance at announce time.
 */
internal fun announcerFor(
    homeSession: HomeSession,
    implProvider: () -> MediaRepositoryImpl?,
): UserDataAnnouncerImpl =
    UserDataAnnouncerImpl(
        homeSession = homeSession,
        recentLocalWrites = RecentLocalUserDataWritesImpl(),
        onConfirmedWrite = { implProvider()?.armStaleReadGroupsForConfirmedWrite() },
    )
