package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_cache_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_new_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_new_episodes_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_offline_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_offline_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bandwidth_cap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_bandwidth_cap_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cellular_download_warning_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cellular_download_warning_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cellular_streaming_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_cellular_streaming_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clear_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clear_cache_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clear_image_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clear_image_cache_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_data_saver_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_data_saver_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_connections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_connections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_quality_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_end_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_start_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_wifi_only_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_schedule_wifi_only_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_storage_location_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_download_storage_location_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_cache_size_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_concurrent_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_concurrent_downloads_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_download_storage_limit_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_max_download_storage_limit_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_metered_network_behavior_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_metered_network_behavior_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_network_timeout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_network_timeout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_offline_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_offline_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_smart_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_smart_downloads_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_data_sync_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_data_sync_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_verbose_logging_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_verbose_logging_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_wifi_only_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_wifi_only_downloads_title

/**
 * Settings-search items for the "Storage, Network & Offline" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to StorageSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val StorageSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "clear_cache",
        titleRes = Res.string.ss_clear_cache_title,
        subtitleRes = Res.string.ss_clear_cache_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    ),
    SettingsSearchItem(
        id = "clear_image_cache",
        titleRes = Res.string.ss_clear_image_cache_title,
        subtitleRes = Res.string.ss_clear_image_cache_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("image cache", "clear images", "posters", "thumbnails", "coil"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Photo
    ),
    SettingsSearchItem(
        id = "wifi_only_downloads",
        titleRes = Res.string.ss_wifi_only_downloads_title,
        subtitleRes = Res.string.ss_wifi_only_downloads_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "auto_delete_cache",
        titleRes = Res.string.ss_auto_delete_cache_title,
        subtitleRes = Res.string.ss_auto_delete_cache_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("auto delete", "cache limit", "disk full"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "max_cache_size",
        titleRes = Res.string.ss_max_cache_size_title,
        subtitleRes = Res.string.ss_max_cache_size_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("max cache", "size limit", "cache limit"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "offline_mode",
        titleRes = Res.string.ss_offline_mode_title,
        subtitleRes = Res.string.ss_offline_mode_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("offline", "airplane mode", "no network", "local only"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.CloudOff
    ),
    SettingsSearchItem(
        id = "adaptive_bitrate",
        titleRes = Res.string.ss_adaptive_bitrate_title,
        subtitleRes = Res.string.ss_adaptive_bitrate_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "bandwidth_cap",
        titleRes = Res.string.ss_bandwidth_cap_title,
        subtitleRes = Res.string.ss_bandwidth_cap_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Lock
    ),
    SettingsSearchItem(
        id = "data_saver",
        titleRes = Res.string.ss_data_saver_title,
        subtitleRes = Res.string.ss_data_saver_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    ),
    SettingsSearchItem(
        id = "cellular_download_warning",
        titleRes = Res.string.ss_cellular_download_warning_title,
        subtitleRes = Res.string.ss_cellular_download_warning_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.AlertTriangle,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "network_timeout",
        titleRes = Res.string.ss_network_timeout_title,
        subtitleRes = Res.string.ss_network_timeout_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "verbose_logging",
        titleRes = Res.string.ss_verbose_logging_title,
        subtitleRes = Res.string.ss_verbose_logging_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "user_data_sync",
        titleRes = Res.string.ss_user_data_sync_title,
        subtitleRes = Res.string.ss_user_data_sync_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "download_quality",
        titleRes = Res.string.ss_download_quality_title,
        subtitleRes = Res.string.ss_download_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download quality", "offline quality", "1080p downloads"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Video
    ),
    SettingsSearchItem(
        id = "smart_downloads",
        titleRes = Res.string.ss_smart_downloads_title,
        subtitleRes = Res.string.ss_smart_downloads_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    ),
    SettingsSearchItem(
        id = "download_connections",
        titleRes = Res.string.ss_download_connections_title,
        subtitleRes = Res.string.ss_download_connections_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("connections", "parallel", "streams", "download", "segments"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "max_concurrent_downloads",
        titleRes = Res.string.ss_max_concurrent_downloads_title,
        subtitleRes = Res.string.ss_max_concurrent_downloads_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("concurrent", "simultaneous", "parallel", "downloads", "max", "queue"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    ),
    SettingsSearchItem(
        id = "auto_offline",
        titleRes = Res.string.ss_auto_offline_title,
        subtitleRes = Res.string.ss_auto_offline_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("auto offline", "network lost", "offline", "automatic", "disconnect"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.WifiOff
    ),
    SettingsSearchItem(
        id = "metered_network_behavior",
        titleRes = Res.string.ss_metered_network_behavior_title,
        subtitleRes = Res.string.ss_metered_network_behavior_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("metered", "cellular", "behavior", "data", "mobile", "network"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Compass
    ),
    SettingsSearchItem(
        id = "cellular_streaming_quality",
        titleRes = Res.string.ss_cellular_streaming_quality_title,
        subtitleRes = Res.string.ss_cellular_streaming_quality_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("cellular", "streaming", "quality", "mobile", "data", "resolution"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.DeviceMobile
    ),
    SettingsSearchItem(
        id = "auto_download_new_episodes",
        titleRes = Res.string.ss_auto_download_new_episodes_title,
        subtitleRes = Res.string.ss_auto_download_new_episodes_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("auto download", "new episodes", "automatic", "next", "series"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download
    ),
    SettingsSearchItem(
        id = "download_schedule",
        titleRes = Res.string.ss_download_schedule_title,
        subtitleRes = Res.string.ss_download_schedule_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "hours", "overnight", "window", "time"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock
    ),
    SettingsSearchItem(
        id = "download_schedule_start",
        titleRes = Res.string.ss_download_schedule_start_title,
        subtitleRes = Res.string.ss_download_schedule_start_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "start", "hour", "window", "begin"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Sun
    ),
    SettingsSearchItem(
        id = "download_schedule_end",
        titleRes = Res.string.ss_download_schedule_end_title,
        subtitleRes = Res.string.ss_download_schedule_end_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "end", "hour", "window", "stop"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Moon
    ),
    SettingsSearchItem(
        id = "download_schedule_wifi_only",
        titleRes = Res.string.ss_download_schedule_wifi_only_title,
        subtitleRes = Res.string.ss_download_schedule_wifi_only_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "schedule", "wifi only", "unmetered", "require"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi
    ),
    SettingsSearchItem(
        id = "max_download_storage_limit",
        titleRes = Res.string.ss_max_download_storage_limit_title,
        subtitleRes = Res.string.ss_max_download_storage_limit_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "storage", "limit", "max", "size", "cap", "gb"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database
    ),
    SettingsSearchItem(
        id = "download_storage_location",
        titleRes = Res.string.ss_download_storage_location_title,
        subtitleRes = Res.string.ss_download_storage_location_subtitle,
        categoryRes = CoreUiRes.string.ss_cat_storage,
        keywords = listOf("download", "storage", "location", "folder", "sd card", "internal"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Folder
    ),
)
