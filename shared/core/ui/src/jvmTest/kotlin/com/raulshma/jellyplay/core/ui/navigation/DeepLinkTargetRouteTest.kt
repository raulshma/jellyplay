package com.raulshma.jellyplay.core.ui.navigation

import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [DeepLinkTarget.toRoute] — the ONE target→destination table both
 * platform shells consume (the Android DeepLinkHandler's former private
 * `targetToRoute` and the desktop DesktopLinkOpenPolicy's former
 * `targetRoute`, two hand-mirrored when-expressions held in agreement by
 * "same rows, same nulls" KDoc). The desktop copy was JVM-tested over there;
 * the Android copy was untested — this suite closes that from the shared
 * owner's side, so a row added to one shell's expectations without the fold
 * fails here instead of drifting per shell.
 */
class DeepLinkTargetRouteTest {

    @Test
    fun `media-bearing targets route to their detail routes`() {
        assertEquals(
            Route.MediaDetail("abc-123"),
            DeepLinkTarget.MediaDetail("abc-123").toRoute(),
        )
        assertEquals(
            Route.NewsletterSectionList("CONTINUE_WATCHING"),
            DeepLinkTarget.NewsletterSection("CONTINUE_WATCHING").toRoute(),
        )
        assertEquals(
            Route.SeerrDetail(tmdbId = 42, mediaType = "tv"),
            DeepLinkTarget.SeerrDetail(tmdbId = 42, mediaType = "tv").toRoute(),
        )
    }

    @Test
    fun `argument-less destinations route to their top-level routes`() {
        assertEquals(Route.Search, DeepLinkTarget.Search.toRoute())
        assertEquals(Route.Settings, DeepLinkTarget.Settings.toRoute())
        assertEquals(Route.Downloads, DeepLinkTarget.Downloads.toRoute())
        assertEquals(Route.Library, DeepLinkTarget.Library.toRoute())
    }

    @Test
    fun `syncplay join routes nowhere on every shell`() {
        // The Discord Rich Presence Join button's payload — the desktop's
        // DiscordPresenceService.handleJoinSecret consumes it; Android has no
        // join-by-link surface. Parses, but never a navigation destination.
        assertNull(DeepLinkTarget.SyncPlayJoin("group-9").toRoute())
    }

    @Test
    fun `a parse miss routes nowhere`() {
        assertNull(null.toRoute())
    }
}
