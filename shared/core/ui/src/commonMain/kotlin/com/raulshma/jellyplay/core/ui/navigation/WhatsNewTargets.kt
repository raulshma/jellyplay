package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable

/**
 * The compiled-in deep-link vocabulary for What's-New entries: every `target`
 * id the feed may reference, resolved to a [Route] HERE — never in the feed
 * document. A remote `target` string cannot invent a destination; an unknown
 * id simply yields no "take me there" action (feed forward-compatibility,
 * see `com.raulshma.jellyplay.core.model.WhatsNewFeed`).
 *
 * Id convention: dot-namespaced, `settings.<screen>` for settings screens
 * (those carry a `highlightSettingId` capable of scroll-to/focus), bare
 * section ids for top-level destinations. Adding a target = one map entry;
 * the id is the wire contract once published (never rename).
 */
@Immutable
object WhatsNewTargets {

    private val routes: Map<String, () -> Route> = mapOf(
        // Top-level sections.
        "home" to { Route.Home },
        "library" to { Route.Library },
        "search" to { Route.Search },
        "liveTv" to { Route.LiveTv },
        "downloads" to { Route.Downloads },
        "requests" to { Route.Requests },
        "calendar" to { Route.UpcomingCalendar },
        "newsletter" to { Route.Newsletter },
        "shortcuts" to { Route.Shortcuts },
        // Settings screens (highlight-capable via HighlightableRoute).
        "settings.home" to { Route.HomeSettings() },
        "settings.discoverRows" to { Route.DiscoverRows() },
        "settings.pinnedSections" to { Route.PinnedHomeSections() },
        "settings.appearance" to { Route.AppearanceSettings() },
        "settings.playback" to { Route.PlaybackSettings() },
        "settings.audio" to { Route.AudioSettings() },
        "settings.language" to { Route.LanguageSettings() },
        "settings.notifications" to { Route.NotificationSettings() },
        "settings.storage" to { Route.StorageSettings() },
        "settings.security" to { Route.SecuritySettings() },
        "settings.backup" to { Route.BackupSettings() },
        "settings.experimental" to { Route.ExperimentalSettings() },
        "settings.integrations" to { Route.Integrations() },
        "settings.server" to { Route.ServerManagement() },
        "settings.users" to { Route.UserManagement() },
        "settings.about" to { Route.About },
        // The archive itself (the What's New feature's own entry).
        "settings.whatsNew" to { Route.WhatsNew },
    )

    /** All published target ids (for tooling/tests — not used at runtime). */
    val ids: Set<String> = routes.keys

    /**
     * Resolves [target] to a route, with [highlightSettingId] applied when the
     * resolved route is highlight-capable (settings screens scroll to / focus
     * the matched row). Null for unknown ids.
     */
    fun resolve(target: String?, highlightSettingId: String? = null): Route? {
        if (target.isNullOrBlank()) return null
        return routes[target]?.invoke()
            ?.let { route -> highlightSettingId?.let { id -> route.withHighlightSettingId(id) } ?: route }
    }
}
