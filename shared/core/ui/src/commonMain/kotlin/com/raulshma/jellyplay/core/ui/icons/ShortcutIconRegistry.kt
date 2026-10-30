package com.raulshma.jellyplay.core.ui.icons

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.Adjustments
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.ArrowsShuffle
import com.composables.icons.tabler.outline.AspectRatio
import com.composables.icons.tabler.outline.Badge4k
import com.composables.icons.tabler.outline.BadgeHd
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bolt
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Bookmarks
import com.composables.icons.tabler.outline.Broadcast
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.CalendarEvent
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.Category
import com.composables.icons.tabler.outline.ChartBar
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Compass
import com.composables.icons.tabler.outline.Confetti
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.DeviceDesktop
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Devices
import com.composables.icons.tabler.outline.Dice
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.FileText
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.Flame
import com.composables.icons.tabler.outline.Flask
import com.composables.icons.tabler.outline.Folder
import com.composables.icons.tabler.outline.FolderOpen
import com.composables.icons.tabler.outline.Gauge
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Hourglass
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Key
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Library
import com.composables.icons.tabler.outline.Link
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.ListDetails
import com.composables.icons.tabler.outline.Lock
import com.composables.icons.tabler.outline.Logout
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.Message
import com.composables.icons.tabler.outline.Microphone
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.MoonStars
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Music
import com.composables.icons.tabler.outline.News
import com.composables.icons.tabler.outline.Note
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.Piano
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Pinned
import com.composables.icons.tabler.outline.PinnedOff
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipForward
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.PlaylistAdd
import com.composables.icons.tabler.outline.Plug
import com.composables.icons.tabler.outline.PlugConnected
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Rocket
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.ShieldLock
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Stopwatch
import com.composables.icons.tabler.outline.Subtitles
import com.composables.icons.tabler.outline.Sun
import com.composables.icons.tabler.outline.Terminal2
import com.composables.icons.tabler.outline.Ticket
import com.composables.icons.tabler.outline.Tool
import com.composables.icons.tabler.outline.TrendingUp
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Upload
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.Vinyl
import com.composables.icons.tabler.outline.Wallet
import com.composables.icons.tabler.outline.Wand
import com.composables.icons.tabler.outline.Wifi
import com.composables.icons.tabler.outline.WifiOff
import com.composables.icons.tabler.outline.World

/**
 * The curated icon vocabulary a user shortcut may wear. One registry, the
 * `NavDestinationRegistry` "facts registered exactly once" rule: every entry
 * pairs a stable persisted string [ShortcutIconEntry.id] with its Tabler
 * [ShortcutIconEntry.icon] and its display name, so the persisted choice can
 * never depend on class identity and the picker, the resolver, and the
 * surfaces can never disagree.
 *
 * The set is CURATED (a navigable subset of the Tabler pack) rather than
 * exhaustive on purpose: the picker must stay browsable, and every icon here
 * reads at 22dp on a dock pill. Entries never ship removed — an icon that
 * ages out of style is kept so old persisted configs keep resolving (the
 * [ShortcutIconRegistry.resolve] fallback only fires for ids this registry
 * never knew, e.g. a config restored from a newer app version).
 *
 * Display names are plain English strings, deliberately NOT string resources:
 * they back `contentDescription`/tooltips only (icon glyphs carry no text to
 * localize), and keeping them out of the resources tables keeps the i18n
 * parity ratchet free of ~90 untranslatable keys. New entries go anywhere in
 * [ALL]; order is the picker's display order (media motifs first).
 */
object ShortcutIconRegistry {

    /** One curated icon: the persisted [id], the vector, and its English display name. */
    @Immutable
    data class ShortcutIconEntry(
        val id: String,
        val icon: ImageVector,
        val displayName: String,
    )

    val ALL: List<ShortcutIconEntry> = listOf(
        ShortcutIconEntry("home", Tabler.Outline.Home, "Home"),
        ShortcutIconEntry("search", Tabler.Outline.Search, "Search"),
        ShortcutIconEntry("library", Tabler.Outline.Library, "Library"),
        ShortcutIconEntry("movie", Tabler.Outline.Movie, "Movies"),
        ShortcutIconEntry("device-tv", Tabler.Outline.DeviceTv, "Live TV"),
        ShortcutIconEntry("music", Tabler.Outline.Music, "Music"),
        ShortcutIconEntry("photo", Tabler.Outline.Photo, "Photos"),
        ShortcutIconEntry("video", Tabler.Outline.Video, "Video"),
        ShortcutIconEntry("player-play", Tabler.Outline.PlayerPlay, "Play"),
        ShortcutIconEntry("player-pause", Tabler.Outline.PlayerPause, "Pause"),
        ShortcutIconEntry("player-skip-forward", Tabler.Outline.PlayerSkipForward, "Skip forward"),
        ShortcutIconEntry("arrows-shuffle", Tabler.Outline.ArrowsShuffle, "Shuffle"),
        ShortcutIconEntry("playlist", Tabler.Outline.Playlist, "Playlist"),
        ShortcutIconEntry("playlist-add", Tabler.Outline.PlaylistAdd, "Add to playlist"),
        ShortcutIconEntry("disc", Tabler.Outline.Disc, "Disc"),
        ShortcutIconEntry("vinyl", Tabler.Outline.Vinyl, "Vinyl"),
        ShortcutIconEntry("piano", Tabler.Outline.Piano, "Instruments"),
        ShortcutIconEntry("microphone", Tabler.Outline.Microphone, "Microphone"),
        ShortcutIconEntry("heart", Tabler.Outline.Heart, "Favorites"),
        ShortcutIconEntry("star", Tabler.Outline.Star, "Star"),
        ShortcutIconEntry("bookmark", Tabler.Outline.Bookmark, "Bookmark"),
        ShortcutIconEntry("bookmarks", Tabler.Outline.Bookmarks, "Bookmarks"),
        ShortcutIconEntry("pin", Tabler.Outline.Pin, "Pin"),
        ShortcutIconEntry("pinned", Tabler.Outline.Pinned, "Pinned"),
        ShortcutIconEntry("pinned-off", Tabler.Outline.PinnedOff, "Unpinned"),
        ShortcutIconEntry("calendar", Tabler.Outline.Calendar, "Calendar"),
        ShortcutIconEntry("calendar-event", Tabler.Outline.CalendarEvent, "Event"),
        ShortcutIconEntry("clock", Tabler.Outline.Clock, "Clock"),
        ShortcutIconEntry("history", Tabler.Outline.History, "History"),
        ShortcutIconEntry("hourglass", Tabler.Outline.Hourglass, "Hourglass"),
        ShortcutIconEntry("stopwatch", Tabler.Outline.Stopwatch, "Stopwatch"),
        ShortcutIconEntry("download", Tabler.Outline.Download, "Downloads"),
        ShortcutIconEntry("cloud-download", Tabler.Outline.CloudDownload, "Cloud download"),
        ShortcutIconEntry("upload", Tabler.Outline.Upload, "Upload"),
        ShortcutIconEntry("wand", Tabler.Outline.Wand, "Wand"),
        ShortcutIconEntry("sparkles", Tabler.Outline.Sparkles, "Surprise"),
        ShortcutIconEntry("bolt", Tabler.Outline.Bolt, "Quick"),
        ShortcutIconEntry("flame", Tabler.Outline.Flame, "Trending"),
        ShortcutIconEntry("trending-up", Tabler.Outline.TrendingUp, "Chart"),
        ShortcutIconEntry("chart-bar", Tabler.Outline.ChartBar, "Statistics"),
        ShortcutIconEntry("activity", Tabler.Outline.Activity, "Activity"),
        ShortcutIconEntry("gauge", Tabler.Outline.Gauge, "Dashboard"),
        ShortcutIconEntry("dice", Tabler.Outline.Dice, "Random"),
        ShortcutIconEntry("confetti", Tabler.Outline.Confetti, "Celebrate"),
        ShortcutIconEntry("rocket", Tabler.Outline.Rocket, "Launch"),
        ShortcutIconEntry("puzzle", Tabler.Outline.Puzzle, "Extensions"),
        ShortcutIconEntry("flask", Tabler.Outline.Flask, "Experiments"),
        ShortcutIconEntry("tool", Tabler.Outline.Tool, "Tools"),
        ShortcutIconEntry("terminal-2", Tabler.Outline.Terminal2, "Console"),
        ShortcutIconEntry("cpu", Tabler.Outline.Cpu, "System"),
        ShortcutIconEntry("server", Tabler.Outline.Server, "Server"),
        ShortcutIconEntry("database", Tabler.Outline.Database, "Database"),
        ShortcutIconEntry("folder", Tabler.Outline.Folder, "Folder"),
        ShortcutIconEntry("folder-open", Tabler.Outline.FolderOpen, "Open folder"),
        ShortcutIconEntry("layout-grid", Tabler.Outline.LayoutGrid, "Grid"),
        ShortcutIconEntry("category", Tabler.Outline.Category, "Categories"),
        ShortcutIconEntry("list", Tabler.Outline.List, "List"),
        ShortcutIconEntry("list-details", Tabler.Outline.ListDetails, "Detailed list"),
        ShortcutIconEntry("stack", Tabler.Outline.Stack, "Stack"),
        ShortcutIconEntry("filter", Tabler.Outline.Filter, "Filter"),
        ShortcutIconEntry("adjustments", Tabler.Outline.Adjustments, "Adjust"),
        ShortcutIconEntry("settings", Tabler.Outline.Settings, "Settings"),
        ShortcutIconEntry("user", Tabler.Outline.User, "User"),
        ShortcutIconEntry("users", Tabler.Outline.Users, "Users"),
        ShortcutIconEntry("devices", Tabler.Outline.Devices, "Devices"),
        ShortcutIconEntry("device-desktop", Tabler.Outline.DeviceDesktop, "Desktop"),
        ShortcutIconEntry("device-mobile", Tabler.Outline.DeviceMobile, "Mobile"),
        ShortcutIconEntry("cast", Tabler.Outline.Cast, "Cast"),
        ShortcutIconEntry("broadcast", Tabler.Outline.Broadcast, "Broadcast"),
        ShortcutIconEntry("plug", Tabler.Outline.Plug, "Plug"),
        ShortcutIconEntry("plug-connected", Tabler.Outline.PlugConnected, "Connected"),
        ShortcutIconEntry("wifi", Tabler.Outline.Wifi, "Online"),
        ShortcutIconEntry("wifi-off", Tabler.Outline.WifiOff, "Offline"),
        ShortcutIconEntry("shield", Tabler.Outline.Shield, "Shield"),
        ShortcutIconEntry("shield-lock", Tabler.Outline.ShieldLock, "Security"),
        ShortcutIconEntry("lock", Tabler.Outline.Lock, "Lock"),
        ShortcutIconEntry("key", Tabler.Outline.Key, "Key"),
        ShortcutIconEntry("eye", Tabler.Outline.Eye, "Visible"),
        ShortcutIconEntry("eye-off", Tabler.Outline.EyeOff, "Hidden"),
        ShortcutIconEntry("logout", Tabler.Outline.Logout, "Sign out"),
        ShortcutIconEntry("subtitles", Tabler.Outline.Subtitles, "Subtitles"),
        ShortcutIconEntry("aspect-ratio", Tabler.Outline.AspectRatio, "Aspect ratio"),
        ShortcutIconEntry("badge-4k", Tabler.Outline.Badge4k, "4K"),
        ShortcutIconEntry("badge-hd", Tabler.Outline.BadgeHd, "HD"),
        ShortcutIconEntry("bell", Tabler.Outline.Bell, "Notifications"),
        ShortcutIconEntry("mail", Tabler.Outline.Mail, "Mail"),
        ShortcutIconEntry("message", Tabler.Outline.Message, "Messages"),
        ShortcutIconEntry("news", Tabler.Outline.News, "News"),
        ShortcutIconEntry("note", Tabler.Outline.Note, "Note"),
        ShortcutIconEntry("file-text", Tabler.Outline.FileText, "Document"),
        ShortcutIconEntry("archive", Tabler.Outline.Archive, "Archive"),
        ShortcutIconEntry("trash", Tabler.Outline.Trash, "Trash"),
        ShortcutIconEntry("wallet", Tabler.Outline.Wallet, "Wallet"),
        ShortcutIconEntry("ticket", Tabler.Outline.Ticket, "Ticket"),
        ShortcutIconEntry("compass", Tabler.Outline.Compass, "Explore"),
        ShortcutIconEntry("world", Tabler.Outline.World, "World"),
        ShortcutIconEntry("sun", Tabler.Outline.Sun, "Day"),
        ShortcutIconEntry("moon", Tabler.Outline.Moon, "Night"),
        ShortcutIconEntry("moon-stars", Tabler.Outline.MoonStars, "Night sky"),
        ShortcutIconEntry("palette", Tabler.Outline.Palette, "Theme"),
        ShortcutIconEntry("link", Tabler.Outline.Link, "Link"),
        ShortcutIconEntry("alert-triangle", Tabler.Outline.AlertTriangle, "Warning"),
        ShortcutIconEntry("info-circle", Tabler.Outline.InfoCircle, "Info"),
    )

    private val byId: Map<String, ImageVector> = ALL.associate { it.id to it.icon }

    /**
     * The vector for a persisted icon id, or null when this registry never
     * knew the id (a config from a newer app version) — the caller falls back
     * to the shortcut's catalog default icon.
     */
    fun resolve(id: String): ImageVector? = byId[id]

    /** The display name for a registry entry (English-only — see the object KDoc). */
    fun displayName(id: String): String? = ALL.firstOrNull { it.id == id }?.displayName
}
