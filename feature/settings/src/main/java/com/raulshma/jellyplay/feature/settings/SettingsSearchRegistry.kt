package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

data class SettingsSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val keywords: List<String>,
    val route: Route,
    val icon: ImageVector,
    val isAdvanced: Boolean = false
)

object SettingsSearchRegistry {
    val items = listOf(
        SettingsSearchItem(
            id = "logout",
            title = "Sign Out",
            subtitle = "Log out of current Jellyfin account",
            category = "Account",
            keywords = listOf("sign out", "logout", "exit", "disconnect"),
            route = Route.Settings,
            icon = Tabler.Outline.Logout
        ),
        // Account / Users / Servers
        SettingsSearchItem(
            id = "server_management",
            title = "Server Management",
            subtitle = "Switch servers or add new connections",
            category = "Account",
            keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
            route = Route.ServerManagement(),
            icon = Tabler.Outline.Server
        ),
        SettingsSearchItem(
            id = "user_management",
            title = "User Management",
            subtitle = "Manage server users and profiles",
            category = "Account",
            keywords = listOf("user", "accounts", "profile", "switch user", "admin"),
            route = Route.UserManagement(),
            icon = Tabler.Outline.Users
        ),
        SettingsSearchItem(
            id = "seerr_settings",
            title = "Seerr Integration",
            subtitle = "Configure Seerr/Jellyseerr requests and options",
            category = "Account",
            keywords = listOf("seerr", "jellyseerr", "request", "movies", "shows", "approve"),
            route = Route.SeerrSettings(),
            icon = Tabler.Outline.LayersLinked
        ),

        // Appearance Settings
        SettingsSearchItem(
            id = "pinned_home_sections",
            title = "Pinned Home Sections",
            subtitle = "Pin collections, playlists, favorites, genres or studios to home",
            category = "Appearance",
            keywords = listOf("pinned", "home", "collection", "playlist", "favorites", "genre", "studio", "shelf", "row"),
            route = Route.PinnedHomeSections(),
            icon = Tabler.Outline.Pinned,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "home_layout_presets",
            title = "Home Layout Presets",
            subtitle = "Save, share, import or reset your home screen layout",
            category = "Appearance",
            keywords = listOf("preset", "layout", "home", "save", "load", "import", "export", "share", "reset", "backup", "configuration"),
            route = Route.HomeLayoutPresets(),
            icon = Tabler.Outline.Bookmarks,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "date_format",
            title = "Date Format",
            subtitle = "Choose how dates are displayed throughout the app",
            category = "Appearance",
            keywords = listOf("date", "format", "time", "calendar", "day", "month", "year", "display"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Calendar,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "font_scale",
            title = "Font Size",
            subtitle = "Adjust the text size across the entire app",
            category = "Appearance",
            keywords = listOf("font", "size", "text", "scale", "accessibility", "readability", "large", "small"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.TextSize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "color_blind_mode",
            title = "Color Blind Mode",
            subtitle = "Adjust colors for color vision deficiency",
            category = "Appearance",
            keywords = listOf("color", "blind", "daltonize", "accessibility", "protanopia", "deuteranopia", "tritanopia", "vision"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hand_mode",
            title = "Handedness",
            subtitle = "Mirror navigation for left-handed use",
            category = "Appearance",
            keywords = listOf("hand", "left", "right", "handed", "accessibility", "mirror", "one-handed"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.HandClick,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_scheduler",
            title = "Theme Scheduler",
            subtitle = "Automatically switch between light and dark theme by time of day",
            category = "Appearance",
            keywords = listOf("theme", "scheduler", "day", "night", "auto", "time", "scheduled", "dark", "light"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_mode",
            title = "Theme Mode",
            subtitle = "Follow system setting, light mode, or dark mode",
            category = "Appearance",
            keywords = listOf("theme", "mode", "light", "dark", "system", "black"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Moon
        ),
        SettingsSearchItem(
            id = "synthwave_mode",
            title = "Synthwave Mode",
            subtitle = "Retro-futuristic neon theme with sharp corners",
            category = "Appearance",
            keywords = listOf("synthwave", "retro", "neon", "theme", "colors"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "soothing_mode",
            title = "Soothing Mode",
            subtitle = "Soft Facebook-inspired styling and rounded corners",
            category = "Appearance",
            keywords = listOf("soothing", "soft", "rounded", "calm", "theme"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Palette
        ),
        SettingsSearchItem(
            id = "dynamic_theming",
            title = "Dynamic Theming",
            subtitle = "Colors extracted dynamically from playing media artwork",
            category = "Appearance",
            keywords = listOf("dynamic", "artwork", "colors", "theme", "wallpaper"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Video
        ),
        SettingsSearchItem(
            id = "oled_mode",
            title = "OLED Mode",
            subtitle = "Pure black backgrounds optimized for AMOLED displays",
            category = "Appearance",
            keywords = listOf("oled", "black", "amoled", "pure black", "battery"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.BrightnessHalf
        ),
        SettingsSearchItem(
            id = "contrast",
            title = "Contrast Level",
            subtitle = "Adjust system contrast: standard, medium, or high",
            category = "Appearance",
            keywords = listOf("contrast", "accessibility", "legibility", "readability"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "library_view_mode",
            title = "Library View Mode",
            subtitle = "Display library items in grid or list layouts",
            category = "Appearance",
            keywords = listOf("library", "view", "grid", "list", "layout"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayoutGrid,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "home_mode",
            title = "Home Mode",
            subtitle = "Set default home screen layout: video or music",
            category = "Appearance",
            keywords = listOf("home", "layout", "mode", "video", "music"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Home,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hero_section",
            title = "Show Hero Section",
            subtitle = "Display featured content banner at top of home screen",
            category = "Appearance",
            keywords = listOf("hero", "banner", "featured", "home", "carousel"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayersLinked,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "clock_home",
            title = "Show Clock on Home",
            subtitle = "Display the current time in the home screen top bar",
            category = "Appearance",
            keywords = listOf("clock", "time", "home", "wall", "current"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "continue_watching_click",
            title = "Continue Watching Tap",
            subtitle = "Resume playback or open details when tapping a Continue Watching tile",
            category = "Appearance",
            keywords = listOf("continue watching", "tap", "click", "resume", "play", "details"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PlayerPlay,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "unhide_cw",
            title = "Unhide All from Continue Watching",
            subtitle = "Show all hidden items in the Continue Watching row",
            category = "Appearance",
            keywords = listOf("unhide", "continue watching", "hidden", "reset", "show"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "merge_continue_next_up",
            title = "Merge Continue & Next Up",
            subtitle = "Combine Next Up items into the Continue Watching row",
            category = "Appearance",
            keywords = listOf("merge", "combine", "continue watching", "next up", "single row"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.LayersLinked,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "next_up_max_days",
            title = "Next Up Time Window",
            subtitle = "Only show episodes watched within a time period",
            category = "Appearance",
            keywords = listOf("next up", "days", "time window", "recent", "max days", "filter"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.CalendarTime,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "next_up_rewatching",
            title = "Rewatching in Next Up",
            subtitle = "Include series you are rewatching in Next Up",
            category = "Appearance",
            keywords = listOf("next up", "rewatching", "rewatch", "rewatch", "repeat"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.History,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "theme_music",
            title = "Backdrop Theme Music",
            subtitle = "Play theme songs on detail pages",
            category = "Appearance",
            keywords = listOf("theme", "music", "backdrop", "ambience", "song", "score"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "nav_labels",
            title = "Show Navigation Labels",
            subtitle = "Show icons and text labels in bottom navigation",
            category = "Appearance",
            keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.TextSize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_unwatched_badge",
            title = "Show Unwatched Badge",
            subtitle = "Overlay indicator badges on unwatched items",
            category = "Appearance",
            keywords = listOf("unwatched", "badge", "indicator", "new", "marker"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Folder,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_watched_checkmark",
            title = "Show Watched Checkmark",
            subtitle = "Overlay checkmark badges on finished items",
            category = "Appearance",
            keywords = listOf("watched", "checkmark", "badge", "indicator", "finished"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.CircleCheck,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hide_watched_items",
            title = "Hide Watched Items",
            subtitle = "Automatically filter out watched items from libraries",
            category = "Appearance",
            keywords = listOf("hide", "watched", "filter", "library", "clean"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.EyeOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hide_episode_thumbnails",
            title = "Hide Episode Thumbnails",
            subtitle = "Hide episode preview images to avoid spoilers",
            category = "Appearance",
            keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PhotoOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "skip_specials",
            title = "Skip Special Episodes",
            subtitle = "Exclude specials/bonus episodes from episode lists",
            category = "Appearance",
            keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.PlayerSkipForward,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "haptics_enabled",
            title = "Haptic Feedback",
            subtitle = "Enable vibration feedback for UI interactions",
            category = "Appearance",
            keywords = listOf("haptic", "vibration", "feedback", "vibrate", "touch"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.DeviceMobileVibration,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_share_media",
            title = "Show Share Media",
            subtitle = "Show share options button on detail pages",
            category = "Appearance",
            keywords = listOf("share", "media", "send", "details"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Share,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_external_ratings",
            title = "Show External Ratings",
            subtitle = "Display critic rating scores (IMDb/TMDB) on details pages",
            category = "Appearance",
            keywords = listOf("ratings", "imdb", "tmdb", "critic", "score", "star"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Star,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "performance_mode",
            title = "Performance Mode",
            subtitle = "Reduces heavy animations and layout calculations for low-end devices",
            category = "Appearance",
            keywords = listOf("performance", "speed", "lag", "battery", "animations"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Gauge,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "reduce_motion",
            title = "Reduce Motion",
            subtitle = "Disable parallax transitions and extreme list scaling effects",
            category = "Appearance",
            keywords = listOf("motion", "reduce", "animations", "parallax", "effects"),
            route = Route.AppearanceSettings(),
            icon = Tabler.Outline.Activity,
            isAdvanced = true
        ),

        // Playback Settings
        SettingsSearchItem(
            id = "player_engine",
            title = "Player Engine",
            subtitle = "Select default media playback engine: MPV, ExoPlayer, LibVLC",
            category = "Playback",
            keywords = listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerPlay
        ),
        SettingsSearchItem(
            id = "seek_duration",
            title = "Seek Duration",
            subtitle = "Double-tap seek step duration in player",
            category = "Playback",
            keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerTrackNext
        ),
        SettingsSearchItem(
            id = "orientation",
            title = "Screen Orientation",
            subtitle = "Default screen orientation during video playback",
            category = "Playback",
            keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.DeviceMobileRotated
        ),
        SettingsSearchItem(
            id = "gestures",
            title = "Playback Gestures",
            subtitle = "Toggle swipe gestures for brightness, volume, and seeking",
            category = "Playback",
            keywords = listOf("gestures", "swipe", "brightness", "volume", "seeking"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.HandMove
        ),
        SettingsSearchItem(
            id = "default_speed",
            title = "Default Playback Speed",
            subtitle = "Default initial playback speed for videos",
            category = "Playback",
            keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "default_aspect",
            title = "Default Aspect Ratio",
            subtitle = "Set default video layout aspect ratio (e.g., Fill, Fit, Zoom)",
            category = "Playback",
            keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowAutofitHeight
        ),
        SettingsSearchItem(
            id = "video_autoplay_next",
            title = "Auto-play Next Video",
            subtitle = "Automatically start next episode in sequence",
            category = "Playback",
            keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.PlayerSkipForward
        ),
        SettingsSearchItem(
            id = "autoplay_countdown",
            title = "Auto-Play Countdown",
            subtitle = "Countdown duration before auto-playing next episode",
            category = "Playback",
            keywords = listOf("countdown", "timer", "autoplay", "next"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "controls_timeout",
            title = "Controls Timeout",
            subtitle = "Auto-hide on-screen player controls after delay",
            category = "Playback",
            keywords = listOf("controls", "timeout", "hide", "overlay"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "skip_back_on_resume",
            title = "Skip Back on Resume",
            subtitle = "Jump back a few seconds when un-pausing playback",
            category = "Playback",
            keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.History,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "show_clock_player",
            title = "Show Clock in Player",
            subtitle = "Display the current wall-clock time in the video player top bar",
            category = "Playback",
            keywords = listOf("clock", "time", "player", "wall", "current"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "pass_out_protection",
            title = "Pass-out Protection",
            subtitle = "Pause playback after a set number of hours with no interaction",
            category = "Playback",
            keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Moon,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "duck_on_transient_focus_loss",
            title = "Duck on Phone Call",
            subtitle = "Lower volume and rewind when a phone call arrives",
            category = "Playback",
            keywords = listOf("duck", "phone", "call", "focus", "transient", "volume", "rewind"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Phone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "autoplay_trailers",
            title = "Autoplay Trailers",
            subtitle = "Automatically play media trailers on details pages",
            category = "Playback",
            keywords = listOf("trailer", "autoplay", "preview", "details"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Clipboard,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "cinema_mode",
            title = "Cinema Mode",
            subtitle = "Play pre-roll intros before the main feature",
            category = "Playback",
            keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Video,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "episode_browser",
            title = "In-Player Episode Browser",
            subtitle = "Show inline list of episodes during playback",
            category = "Playback",
            keywords = listOf("episodes", "browser", "list", "in-player"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.List,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "playback_metadata",
            title = "Show Playback Metadata",
            subtitle = "Display stream stats, bitrate, and active codec overlay",
            category = "Playback",
            keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.InfoCircle,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "swipe_seek_range",
            title = "Swipe Seek Range",
            subtitle = "Limit maximum skip duration for swipe seek gestures",
            category = "Playback",
            keywords = listOf("seek range", "swipe limit", "skip max"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.ArrowBarRight,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "remember_brightness",
            title = "Remember Brightness",
            subtitle = "Persist custom brightness level across playback sessions",
            category = "Playback",
            keywords = listOf("brightness", "remember", "save", "light"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BrightnessHalf,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "trickplay_preview",
            title = "Trickplay Thumbnails Preview",
            subtitle = "Show tile previews when scrubbing seek bar",
            category = "Playback",
            keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "preload_buffer",
            title = "Playback Preload Buffer",
            subtitle = "Buffer size limit ahead of current video track",
            category = "Playback",
            keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "background_audio",
            title = "Background Video Audio",
            subtitle = "Keep video audio playing when app is put to background",
            category = "Playback",
            keywords = listOf("background", "audio", "video background", "pip"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "keep_screen_on",
            title = "Keep Screen On",
            subtitle = "Prevent device screen from turning off during video playback",
            category = "Playback",
            keywords = listOf("screen", "awake", "lock", "stay on", "timeout"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Eye,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "incognito_mode",
            title = "Incognito Mode",
            subtitle = "Bypasses reporting watching progress/history to server",
            category = "Playback",
            keywords = listOf("incognito", "private", "history", "stealth"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Ghost,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "dialogue_boost",
            title = "Dialogue Boost",
            subtitle = "Enhance spoken speech frequencies in video tracks",
            category = "Playback",
            keywords = listOf("dialogue", "boost", "speech", "vocal", "enhance"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Microphone2,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "decoder",
            title = "Hardware Decoder Mode",
            subtitle = "Choose hardware, software, or copy-back decoding",
            category = "Playback",
            keywords = listOf("decoder", "hardware", "software", "decoding", "codec"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BadgeHd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_passthrough",
            title = "Audio Passthrough",
            subtitle = "Directly route raw audio format streams to receiver",
            category = "Playback",
            keywords = listOf("passthrough", "surround", "hdmi", "receiver", "raw"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Movie,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "frame_rate_matching",
            title = "Frame Rate Match",
            subtitle = "Synchronize display refresh rate to video frames (TV/HDMI)",
            category = "Playback",
            keywords = listOf("refresh rate", "frame rate", "hz", "judder", "tv"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Maximize,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "streaming_quality",
            title = "Streaming Quality",
            subtitle = "Set default video quality for streaming: 1080p, 4K, SD, Auto",
            category = "Playback",
            keywords = listOf("quality", "streaming", "resolution", "4k", "1080p", "sd"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.BadgeHd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "audio_delay",
            title = "Audio Sync Delay",
            subtitle = "Adjust audio sync latency offset (ms)",
            category = "Playback",
            keywords = listOf("delay", "latency", "sync", "lip sync", "bluetooth"),
            route = Route.PlaybackSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),

        // Audio Player Settings
        SettingsSearchItem(
            id = "audio_default_speed",
            title = "Default Audio Speed",
            subtitle = "Default playback rate for audio tracks and music",
            category = "Audio Player",
            keywords = listOf("audio speed", "pitch", "podcast speed", "music rate"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "audio_visualizer",
            title = "Audio Visualizer",
            subtitle = "Enable real-time audio frequencies FFT visualizer background",
            category = "Audio Player",
            keywords = listOf("visualizer", "fft", "spectrum", "music wave", "effects"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Eye
        ),
        SettingsSearchItem(
            id = "sleep_timer",
            title = "Sleep Timer",
            subtitle = "Automatically pause music/audio after a selected duration",
            category = "Audio Player",
            keywords = listOf("sleep", "timer", "pause", "bedtime"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Clock
        ),
        SettingsSearchItem(
            id = "audio_description",
            title = "Audio Description",
            subtitle = "Prefer narrative audio descriptions for visually impaired",
            category = "Audio Player",
            keywords = listOf("audio description", "narrated", "accessibility", "visually impaired"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone
        ),
        SettingsSearchItem(
            id = "gapless_playback",
            title = "Gapless Playback",
            subtitle = "Play consecutive tracks without silence breaks",
            category = "Audio Player",
            keywords = listOf("gapless", "seamless", "transition", "silence"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.PlaylistAdd,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "crossfade",
            title = "Crossfade Duration",
            subtitle = "Overlap audio transitions between tracks",
            category = "Audio Player",
            keywords = listOf("crossfade", "fade", "transition", "overlap"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Music,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "volume_normalization",
            title = "Volume Normalization",
            subtitle = "Dynamic compression or ReplayGain (album/track)",
            category = "Audio Player",
            keywords = listOf("normalization", "volume", "replaygain", "compression", "gain"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "equalizer",
            title = "Equalizer (EQ)",
            subtitle = "Configure 10-band audio equalizer and presets",
            category = "Audio Player",
            keywords = listOf("equalizer", "eq", "bands", "bass", "treble", "audio profile"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "bass_boost",
            title = "Bass Boost Strength",
            subtitle = "Amplify low-end bass frequencies",
            category = "Audio Player",
            keywords = listOf("bass", "boost", "low end", "subwoofer", "amplify"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "virtualizer",
            title = "Virtualizer / Spatial Audio",
            subtitle = "Simulate multi-channel spatial audio field",
            category = "Audio Player",
            keywords = listOf("virtualizer", "spatial", "3d", "surround", "headphones"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "volume_boost",
            title = "Volume Boost Gain",
            subtitle = "Add software pre-amplifier volume gain",
            category = "Audio Player",
            keywords = listOf("volume boost", "boost", "loudness", "gain", "preamp"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "reverb",
            title = "Reverb Preset",
            subtitle = "Simulate environmental acoustics (e.g. Hall, Room, Concert)",
            category = "Audio Player",
            keywords = listOf("reverb", "acoustic", "environment", "room", "hall"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.WaveSine,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "channel_mixing",
            title = "Channel Mixing",
            subtitle = "Mix surround audio down to stereo or map channels",
            category = "Audio Player",
            keywords = listOf("mixing", "channel", "mono", "stereo", "surround"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Speakerphone,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "lr_balance",
            title = "L/R Balance",
            subtitle = "Adjust audio balance between left and right channels",
            category = "Audio Player",
            keywords = listOf("balance", "left", "right", "stereo balance"),
            route = Route.AudioSettings(),
            icon = Tabler.Outline.Adjustments,
            isAdvanced = true
        ),

        // Language & Subtitles
        SettingsSearchItem(
            id = "app_language",
            title = "Display Language",
            subtitle = "Override the app interface language",
            category = "Language & Subtitles",
            keywords = listOf("language", "display", "interface", "locale", "ui language", "app language"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Language
        ),
        SettingsSearchItem(
            id = "audio_language",
            title = "Preferred Audio Language",
            subtitle = "Select default audio language track",
            category = "Language & Subtitles",
            keywords = listOf("language", "audio track", "speech", "default language"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Language
        ),
        SettingsSearchItem(
            id = "subtitle_language",
            title = "Preferred Subtitle Language",
            subtitle = "Select default language for media subtitles",
            category = "Language & Subtitles",
            keywords = listOf("subtitles", "language", "cc", "captions"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Subtitles
        ),
        SettingsSearchItem(
            id = "subtitle_font_size",
            title = "Subtitle Font Size",
            subtitle = "Size of subtitle texts during video playback",
            category = "Language & Subtitles",
            keywords = listOf("subtitle size", "font size", "text size", "bigger"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Typography
        ),
        SettingsSearchItem(
            id = "subtitle_forced_only",
            title = "Forced Subtitles Only",
            subtitle = "Show subtitles only when forced tracks are present",
            category = "Language & Subtitles",
            keywords = listOf("forced", "subtitles", "foreign", "parts", "native"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.TextSize
        ),
        SettingsSearchItem(
            id = "pgs_direct_play",
            title = "PGS Direct Play",
            subtitle = "Render PGS subtitles natively instead of burning in",
            category = "Language & Subtitles",
            keywords = listOf("pgs", "subtitle", "direct play", "picture", "image subtitle", "bluray"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Photo,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "hdr_subtitle_style",
            title = "HDR Subtitle Style",
            subtitle = "Separate subtitle styling for HDR content",
            category = "Language & Subtitles",
            keywords = listOf("hdr", "subtitle", "style", "dolby vision", "hdr10", "brightness"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Sun,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_color",
            title = "Subtitle Text Color",
            subtitle = "Font color of subtitle characters",
            category = "Language & Subtitles",
            keywords = listOf("subtitle color", "text color", "yellow subtitles", "white"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Palette,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_background",
            title = "Subtitle Background Color",
            subtitle = "Color and opacity of subtitle container box",
            category = "Language & Subtitles",
            keywords = listOf("subtitle background", "opacity", "transparency", "box"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Background,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_edge_style",
            title = "Subtitle Edge Style",
            subtitle = "Adjust drop shadow, outline, or border styling",
            category = "Language & Subtitles",
            keywords = listOf("edge style", "shadow", "outline", "border"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.BorderAll,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_sync_offset",
            title = "Subtitle Sync Offset",
            subtitle = "Apply delay or advance adjustment to subtitles",
            category = "Language & Subtitles",
            keywords = listOf("sync", "offset", "delay", "lagging subtitles"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "subtitle_vertical_position",
            title = "Subtitle Vertical Position",
            subtitle = "Adjust subtitle position height from bottom screen boundary",
            category = "Language & Subtitles",
            keywords = listOf("position", "height", "vertical", "bottom", "margin"),
            route = Route.LanguageSettings(),
            icon = Tabler.Outline.ArrowBarDown,
            isAdvanced = true
        ),

        // Notifications
        SettingsSearchItem(
            id = "notifications_enable",
            title = "Notifications Check Frequency",
            subtitle = "Enable library background updates checking frequency",
            category = "Notifications",
            keywords = listOf("notifications", "frequency", "bell", "check frequency", "alerts"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Bell
        ),
        SettingsSearchItem(
            id = "respect_system_dnd",
            title = "Respect System DND",
            subtitle = "Don't show notifications when Do Not Disturb is active",
            category = "Notifications",
            keywords = listOf("dnd", "do not disturb", "quiet", "silent", "notification policy"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.BellOff,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "system_notification_settings",
            title = "System Notification Settings",
            subtitle = "Customize per-library channel in system settings",
            category = "Notifications",
            keywords = listOf("system", "notification", "channel", "settings", "customize"),
            route = Route.NotificationSettings(),
            icon = Tabler.Outline.Settings,
            isAdvanced = true
        ),

        // Storage, Network & Offline
        SettingsSearchItem(
            id = "clear_cache",
            title = "Clear Cache",
            subtitle = "Delete temporary image and network buffers to free space",
            category = "Storage",
            keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Trash
        ),
        SettingsSearchItem(
            id = "wifi_only_downloads",
            title = "WiFi Only Downloads",
            subtitle = "Restricts downloads to unmetered WiFi connections",
            category = "Storage",
            keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Wifi,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "auto_delete_cache",
            title = "Auto-delete Cache",
            subtitle = "Automatically clear cached files on low storage threshold",
            category = "Storage",
            keywords = listOf("auto delete", "cache limit", "disk full"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "max_cache_size",
            title = "Max Cache Size Limit",
            subtitle = "Cap maximum storage cache space for media artwork",
            category = "Storage",
            keywords = listOf("max cache", "size limit", "cache limit"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Database,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "offline_mode",
            title = "Offline Mode",
            subtitle = "Force application into offline state using locally cached/downloaded media",
            category = "Storage",
            keywords = listOf("offline", "airplane mode", "no network", "local only"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.CloudOff
        ),
        SettingsSearchItem(
            id = "adaptive_bitrate",
            title = "Adaptive Bitrate Streaming",
            subtitle = "Dynamically scale stream resolution according to connection speed",
            category = "Storage",
            keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "bandwidth_cap",
            title = "Manual Bandwidth Cap",
            subtitle = "Throttle maximum stream bandwidth to prevent cellular overages",
            category = "Storage",
            keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Lock
        ),
        SettingsSearchItem(
            id = "data_saver",
            title = "Data Saver Mode",
            subtitle = "Scale down images, restrict network use and auto-downloads",
            category = "Storage",
            keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Gauge
        ),
        SettingsSearchItem(
            id = "cellular_download_warning",
            title = "Cellular Download Size Warning",
            subtitle = "Warn before downloading large files on cellular",
            category = "Storage",
            keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.AlertTriangle,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "network_timeout",
            title = "Network Timeouts",
            subtitle = "Connect/read/write timeout preset for API requests",
            category = "Storage",
            keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Clock,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "verbose_logging",
            title = "Verbose Network Logging",
            subtitle = "Log HTTP request headers for debugging",
            category = "Storage",
            keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Code,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "user_data_sync",
            title = "Background User-Data Sync",
            subtitle = "Periodically refresh favourites, played, and progress from server",
            category = "Storage",
            keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Refresh,
            isAdvanced = true
        ),
        SettingsSearchItem(
            id = "download_quality",
            title = "Download Quality Profile",
            subtitle = "Preferred video resolution for downloaded offline media",
            category = "Storage",
            keywords = listOf("download quality", "offline quality", "1080p downloads"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Video
        ),
        SettingsSearchItem(
            id = "smart_downloads",
            title = "Smart Downloads",
            subtitle = "Auto-delete finished episodes to save storage",
            category = "Storage",
            keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
            route = Route.StorageSettings(),
            icon = Tabler.Outline.Trash
        ),

        // Security
        SettingsSearchItem(
            id = "pin_lock",
            title = "PIN Lock",
            subtitle = "Lock access to settings or app with a secure PIN code",
            category = "Security",
            keywords = listOf("pin", "lock", "code", "password", "security"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Lock
        ),
        SettingsSearchItem(
            id = "biometric_lock",
            title = "Biometric Lock",
            subtitle = "Use fingerprint or face recognition authorization",
            category = "Security",
            keywords = listOf("biometric", "fingerprint", "face lock", "iris", "sensors"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Fingerprint
        ),
        SettingsSearchItem(
            id = "pin_for_player_lock",
            title = "PIN for Player Lock",
            subtitle = "Require PIN to unlock the video player screen lock",
            category = "Security",
            keywords = listOf("pin", "player", "lock", "unlock", "screen lock"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Key
        ),
        SettingsSearchItem(
            id = "quick_connect_authorize",
            title = "Authorize Quick Connect",
            subtitle = "Approve Quick Connect codes from other devices",
            category = "Security",
            keywords = listOf("quick connect", "authorize", "approve", "code", "device", "pair"),
            route = Route.SecuritySettings(),
            icon = Tabler.Outline.Bolt
        ),

        // Backup
        SettingsSearchItem(
            id = "backup_export",
            title = "Export Settings Backup",
            subtitle = "Export current configuration settings to a JSON file",
            category = "Backup & Restore",
            keywords = listOf("backup", "export", "save config", "migration"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.DatabaseExport
        ),
        SettingsSearchItem(
            id = "backup_import",
            title = "Import Settings Backup",
            subtitle = "Restore configurations from a previously exported settings backup",
            category = "Backup & Restore",
            keywords = listOf("import", "restore", "load config", "backup restore"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.DatabaseImport
        ),
        SettingsSearchItem(
            id = "factory_reset",
            title = "Factory Reset",
            subtitle = "Reset all settings to factory defaults",
            category = "Backup & Restore",
            keywords = listOf("factory", "reset", "defaults", "clear", "wipe"),
            route = Route.BackupSettings(),
            icon = Tabler.Outline.AlertTriangle,
            isAdvanced = true
        ),

        // About
        SettingsSearchItem(
            id = "about_version",
            title = "App Info & Licenses",
            subtitle = "View application version, developers, and open source licenses",
            category = "About",
            keywords = listOf("about", "version", "licenses", "open source", "developer"),
            route = Route.About,
            icon = Tabler.Outline.InfoCircle
        )
    )
}
