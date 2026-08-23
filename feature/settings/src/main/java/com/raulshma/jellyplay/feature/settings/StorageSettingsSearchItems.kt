package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Storage, Network & Offline" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to StorageSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val StorageSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "clear_cache",
        titleRes = R.string.ss_clear_cache_title,
        subtitleRes = R.string.ss_clear_cache_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    ),
    SettingsSearchItem(
        id = "clear_image_cache",
        titleRes = R.string.ss_clear_image_cache_title,
        subtitleRes = R.string.ss_clear_image_cache_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("image cache", "clear images", "posters", "thumbnails", "coil"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Photo
    ),
    SettingsSearchItem(
        id = "wifi_only_downloads",
        titleRes = R.string.ss_wifi_only_downloads_title,
        subtitleRes = R.string.ss_wifi_only_downloads_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "auto_delete_cache",
        titleRes = R.string.ss_auto_delete_cache_title,
        subtitleRes = R.string.ss_auto_delete_cache_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("auto delete", "cache limit", "disk full"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "max_cache_size",
        titleRes = R.string.ss_max_cache_size_title,
        subtitleRes = R.string.ss_max_cache_size_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("max cache", "size limit", "cache limit"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "offline_mode",
        titleRes = R.string.ss_offline_mode_title,
        subtitleRes = R.string.ss_offline_mode_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("offline", "airplane mode", "no network", "local only"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.CloudOff
    ),
    SettingsSearchItem(
        id = "adaptive_bitrate",
        titleRes = R.string.ss_adaptive_bitrate_title,
        subtitleRes = R.string.ss_adaptive_bitrate_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "bandwidth_cap",
        titleRes = R.string.ss_bandwidth_cap_title,
        subtitleRes = R.string.ss_bandwidth_cap_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Lock
    ),
    SettingsSearchItem(
        id = "data_saver",
        titleRes = R.string.ss_data_saver_title,
        subtitleRes = R.string.ss_data_saver_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "cellular_download_warning",
        titleRes = R.string.ss_cellular_download_warning_title,
        subtitleRes = R.string.ss_cellular_download_warning_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.AlertTriangle,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "network_timeout",
        titleRes = R.string.ss_network_timeout_title,
        subtitleRes = R.string.ss_network_timeout_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "verbose_logging",
        titleRes = R.string.ss_verbose_logging_title,
        subtitleRes = R.string.ss_verbose_logging_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "user_data_sync",
        titleRes = R.string.ss_user_data_sync_title,
        subtitleRes = R.string.ss_user_data_sync_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "download_quality",
        titleRes = R.string.ss_download_quality_title,
        subtitleRes = R.string.ss_download_quality_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download quality", "offline quality", "1080p downloads"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Video
    ),
    SettingsSearchItem(
        id = "smart_downloads",
        titleRes = R.string.ss_smart_downloads_title,
        subtitleRes = R.string.ss_smart_downloads_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    ),
    SettingsSearchItem(
        id = "download_connections",
        titleRes = R.string.ss_download_connections_title,
        subtitleRes = R.string.ss_download_connections_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("connections", "parallel", "streams", "download", "segments"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "max_concurrent_downloads",
        titleRes = R.string.ss_max_concurrent_downloads_title,
        subtitleRes = R.string.ss_max_concurrent_downloads_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("concurrent", "simultaneous", "parallel", "downloads", "max", "queue"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "auto_offline",
        titleRes = R.string.ss_auto_offline_title,
        subtitleRes = R.string.ss_auto_offline_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("auto offline", "network lost", "offline", "automatic", "disconnect"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.WifiOff
    ),
    SettingsSearchItem(
        id = "metered_network_behavior",
        titleRes = R.string.ss_metered_network_behavior_title,
        subtitleRes = R.string.ss_metered_network_behavior_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("metered", "cellular", "behavior", "data", "mobile", "network"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Compass
    ),
    SettingsSearchItem(
        id = "cellular_streaming_quality",
        titleRes = R.string.ss_cellular_streaming_quality_title,
        subtitleRes = R.string.ss_cellular_streaming_quality_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("cellular", "streaming", "quality", "mobile", "data", "resolution"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.DeviceMobile
    ),
    SettingsSearchItem(
        id = "auto_download_new_episodes",
        titleRes = R.string.ss_auto_download_new_episodes_title,
        subtitleRes = R.string.ss_auto_download_new_episodes_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("auto download", "new episodes", "automatic", "next", "series"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download
    ),
    SettingsSearchItem(
        id = "download_schedule",
        titleRes = R.string.ss_download_schedule_title,
        subtitleRes = R.string.ss_download_schedule_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "hours", "overnight", "window", "time"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "download_schedule_start",
        titleRes = R.string.ss_download_schedule_start_title,
        subtitleRes = R.string.ss_download_schedule_start_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "start", "hour", "window", "begin"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Sun
    ),
    SettingsSearchItem(
        id = "download_schedule_end",
        titleRes = R.string.ss_download_schedule_end_title,
        subtitleRes = R.string.ss_download_schedule_end_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "end", "hour", "window", "stop"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Moon
    ),
    SettingsSearchItem(
        id = "download_schedule_wifi_only",
        titleRes = R.string.ss_download_schedule_wifi_only_title,
        subtitleRes = R.string.ss_download_schedule_wifi_only_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "wifi only", "unmetered", "require"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi
    ),
    SettingsSearchItem(
        id = "max_download_storage_limit",
        titleRes = R.string.ss_max_download_storage_limit_title,
        subtitleRes = R.string.ss_max_download_storage_limit_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "storage", "limit", "max", "size", "cap", "gb"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database
    ),
    SettingsSearchItem(
        id = "download_storage_location",
        titleRes = R.string.ss_download_storage_location_title,
        subtitleRes = R.string.ss_download_storage_location_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_storage,
        keywords = listOf("download", "storage", "location", "folder", "sd card", "internal"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Folder
    ),
)
