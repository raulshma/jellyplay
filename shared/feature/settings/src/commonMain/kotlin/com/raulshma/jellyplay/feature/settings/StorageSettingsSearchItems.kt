package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_storage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.downloads_auto_delete_after_watch
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_adaptive_bitrate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_delete_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_new
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_offline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_download_size_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_streaming_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connections_per_download
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_data_saver_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_storage_location
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_manual_bandwidth_cap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_metered_network_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_timeouts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_smart_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_verbose_logging
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_after_watch_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_after_watch_title
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
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object StorageSettingsIds {
    const val CLEAR_CACHE = "clear_cache"
    const val CLEAR_IMAGE_CACHE = "clear_image_cache"
    const val WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    const val AUTO_DELETE_CACHE = "auto_delete_cache"
    const val MAX_CACHE_SIZE = "max_cache_size"
    const val DOWNLOAD_CONNECTIONS = "download_connections"
    const val MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
    const val OFFLINE_MODE = "offline_mode"
    const val ADAPTIVE_BITRATE = "adaptive_bitrate"
    const val BANDWIDTH_CAP = "bandwidth_cap"
    const val DATA_SAVER = "data_saver"
    const val CELLULAR_DOWNLOAD_WARNING = "cellular_download_warning"
    const val NETWORK_TIMEOUT = "network_timeout"
    const val VERBOSE_LOGGING = "verbose_logging"
    const val USER_DATA_SYNC = "user_data_sync"
    const val AUTO_OFFLINE = "auto_offline"
    const val METERED_NETWORK_BEHAVIOR = "metered_network_behavior"
    const val CELLULAR_STREAMING_QUALITY = "cellular_streaming_quality"
    const val DOWNLOAD_QUALITY = "download_quality"
    const val SMART_DOWNLOADS = "smart_downloads"
    const val AUTO_DOWNLOAD_NEW_EPISODES = "auto_download_new_episodes"
    const val DOWNLOAD_SCHEDULE = "download_schedule"
    const val DOWNLOAD_SCHEDULE_START = "download_schedule_start"
    const val DOWNLOAD_SCHEDULE_END = "download_schedule_end"
    const val DOWNLOAD_SCHEDULE_WIFI_ONLY = "download_schedule_wifi_only"
    const val MAX_DOWNLOAD_STORAGE_LIMIT = "max_download_storage_limit"
    const val DOWNLOAD_STORAGE_LOCATION = "download_storage_location"
    const val AUTO_DELETE_AFTER_WATCH = "auto_delete_after_watch"
}

/**
 * Settings-search items for the "Storage" (cache) group of StorageSettingsScreen.
 * The list is the group declaration: SettingsScreenGroups.storageCache decorates
 * it, and the screen derives its scroll group, expand set and row total from it.
 * Aggregated in [SettingsSearchCatalog].
 */
internal val StorageCacheRowRecords = listOf(
    SettingsRowRecord(
        id = StorageSettingsIds.CLEAR_CACHE,
        titleRes = Res.string.settings_clear_cache,
        searchTitleRes = Res.string.ss_clear_cache_title,
        searchSubtitleRes = Res.string.ss_clear_cache_subtitle,
        keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.CLEAR_IMAGE_CACHE,
        titleRes = Res.string.settings_clear_image_cache,
        searchTitleRes = Res.string.ss_clear_image_cache_title,
        searchSubtitleRes = Res.string.ss_clear_image_cache_subtitle,
        keywords = listOf("image cache", "clear images", "posters", "thumbnails", "coil"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Photo
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.WIFI_ONLY_DOWNLOADS,
        titleRes = Res.string.settings_wifi_only,
        searchTitleRes = Res.string.ss_wifi_only_downloads_title,
        searchSubtitleRes = Res.string.ss_wifi_only_downloads_subtitle,
        keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.AUTO_DELETE_CACHE,
        titleRes = Res.string.settings_auto_delete_cache,
        searchTitleRes = Res.string.ss_auto_delete_cache_title,
        searchSubtitleRes = Res.string.ss_auto_delete_cache_subtitle,
        keywords = listOf("auto delete", "cache limit", "disk full"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.MAX_CACHE_SIZE,
        titleRes = Res.string.settings_max_cache_size,
        searchTitleRes = Res.string.ss_max_cache_size_title,
        searchSubtitleRes = Res.string.ss_max_cache_size_subtitle,
        keywords = listOf("max cache", "size limit", "cache limit"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_CONNECTIONS,
        titleRes = Res.string.settings_connections_per_download,
        searchTitleRes = Res.string.ss_download_connections_title,
        searchSubtitleRes = Res.string.ss_download_connections_subtitle,
        keywords = listOf("connections", "parallel", "streams", "download", "segments"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.MAX_CONCURRENT_DOWNLOADS,
        titleRes = Res.string.settings_max_simultaneous_downloads,
        searchTitleRes = Res.string.ss_max_concurrent_downloads_title,
        searchSubtitleRes = Res.string.ss_max_concurrent_downloads_subtitle,
        keywords = listOf("concurrent", "simultaneous", "parallel", "downloads", "max", "queue"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.ArrowBarToDown,
        isAdvanced = true
    ))

/** The catalog projection of `StorageCacheRowRecords`: the search faces + the shared category. */
internal val StorageCacheSearchItems: List<SettingsSearchItem> = StorageCacheRowRecords.toSearchItems(CoreUiRes.string.ss_cat_storage)


/**
 * Settings-search items for the "Network & Offline" group of
 * StorageSettingsScreen. Split from the flat storage list along the
 * screen-group line. Aggregated in [SettingsSearchCatalog].
 */
internal val StorageNetworkRowRecords = listOf(
    SettingsRowRecord(
        id = StorageSettingsIds.OFFLINE_MODE,
        titleRes = Res.string.settings_offline_mode,
        searchTitleRes = Res.string.ss_offline_mode_title,
        searchSubtitleRes = Res.string.ss_offline_mode_subtitle,
        keywords = listOf("offline", "airplane mode", "no network", "local only"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.CloudOff
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.ADAPTIVE_BITRATE,
        titleRes = Res.string.settings_adaptive_bitrate,
        searchTitleRes = Res.string.ss_adaptive_bitrate_title,
        searchSubtitleRes = Res.string.ss_adaptive_bitrate_subtitle,
        keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.BANDWIDTH_CAP,
        titleRes = Res.string.settings_manual_bandwidth_cap,
        searchTitleRes = Res.string.ss_bandwidth_cap_title,
        searchSubtitleRes = Res.string.ss_bandwidth_cap_subtitle,
        keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Lock
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DATA_SAVER,
        titleRes = Res.string.settings_data_saver_mode,
        searchTitleRes = Res.string.ss_data_saver_title,
        searchSubtitleRes = Res.string.ss_data_saver_subtitle,
        keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Gauge
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.CELLULAR_DOWNLOAD_WARNING,
        titleRes = Res.string.settings_cellular_download_size_warning,
        searchTitleRes = Res.string.ss_cellular_download_warning_title,
        searchSubtitleRes = Res.string.ss_cellular_download_warning_subtitle,
        keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.AlertTriangle,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.NETWORK_TIMEOUT,
        titleRes = Res.string.settings_network_timeouts,
        searchTitleRes = Res.string.ss_network_timeout_title,
        searchSubtitleRes = Res.string.ss_network_timeout_subtitle,
        keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.VERBOSE_LOGGING,
        titleRes = Res.string.settings_verbose_logging,
        searchTitleRes = Res.string.ss_verbose_logging_title,
        searchSubtitleRes = Res.string.ss_verbose_logging_subtitle,
        keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Code,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.USER_DATA_SYNC,
        titleRes = Res.string.settings_background_sync,
        searchTitleRes = Res.string.ss_user_data_sync_title,
        searchSubtitleRes = Res.string.ss_user_data_sync_subtitle,
        keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Refresh,
        isAdvanced = true
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.AUTO_OFFLINE,
        titleRes = Res.string.settings_auto_offline,
        searchTitleRes = Res.string.ss_auto_offline_title,
        searchSubtitleRes = Res.string.ss_auto_offline_subtitle,
        keywords = listOf("auto offline", "network lost", "offline", "automatic", "disconnect"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.WifiOff
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.METERED_NETWORK_BEHAVIOR,
        titleRes = Res.string.settings_metered_network_behavior,
        searchTitleRes = Res.string.ss_metered_network_behavior_title,
        searchSubtitleRes = Res.string.ss_metered_network_behavior_subtitle,
        keywords = listOf("metered", "cellular", "behavior", "data", "mobile", "network"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Compass
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.CELLULAR_STREAMING_QUALITY,
        titleRes = Res.string.settings_cellular_streaming_quality,
        searchTitleRes = Res.string.ss_cellular_streaming_quality_title,
        searchSubtitleRes = Res.string.ss_cellular_streaming_quality_subtitle,
        keywords = listOf("cellular", "streaming", "quality", "mobile", "data", "resolution"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.DeviceMobile
    ))

/** The catalog projection of `StorageNetworkRowRecords`: the search faces + the shared category. */
internal val StorageNetworkSearchItems: List<SettingsSearchItem> = StorageNetworkRowRecords.toSearchItems(CoreUiRes.string.ss_cat_storage)


/**
 * Settings-search items for the "Downloads" group of StorageSettingsScreen.
 * Split from the flat storage list along the screen-group line. Aggregated
 * in [SettingsSearchCatalog].
 */
internal val StorageDownloadsRowRecords = listOf(
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_QUALITY,
        titleRes = Res.string.settings_download_quality,
        searchTitleRes = Res.string.ss_download_quality_title,
        searchSubtitleRes = Res.string.ss_download_quality_subtitle,
        keywords = listOf("download quality", "offline quality", "1080p downloads"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Video
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.SMART_DOWNLOADS,
        titleRes = Res.string.settings_smart_downloads,
        searchTitleRes = Res.string.ss_smart_downloads_title,
        searchSubtitleRes = Res.string.ss_smart_downloads_subtitle,
        keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.AUTO_DOWNLOAD_NEW_EPISODES,
        titleRes = Res.string.settings_auto_download_new,
        searchTitleRes = Res.string.ss_auto_download_new_episodes_title,
        searchSubtitleRes = Res.string.ss_auto_download_new_episodes_subtitle,
        keywords = listOf("auto download", "new episodes", "automatic", "next", "series"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Download
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_SCHEDULE,
        titleRes = Res.string.settings_download_schedule,
        searchTitleRes = Res.string.ss_download_schedule_title,
        searchSubtitleRes = Res.string.ss_download_schedule_subtitle,
        keywords = listOf("download", "schedule", "hours", "overnight", "window", "time"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Clock
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_SCHEDULE_START,
        titleRes = Res.string.settings_schedule_start,
        searchTitleRes = Res.string.ss_download_schedule_start_title,
        searchSubtitleRes = Res.string.ss_download_schedule_start_subtitle,
        keywords = listOf("download", "schedule", "start", "hour", "window", "begin"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Sun
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_SCHEDULE_END,
        titleRes = Res.string.settings_schedule_end,
        searchTitleRes = Res.string.ss_download_schedule_end_title,
        searchSubtitleRes = Res.string.ss_download_schedule_end_subtitle,
        keywords = listOf("download", "schedule", "end", "hour", "window", "stop"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Moon
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_SCHEDULE_WIFI_ONLY,
        titleRes = Res.string.settings_download_schedule_wifi_only,
        searchTitleRes = Res.string.ss_download_schedule_wifi_only_title,
        searchSubtitleRes = Res.string.ss_download_schedule_wifi_only_subtitle,
        keywords = listOf("download", "schedule", "wifi only", "unmetered", "require"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Wifi
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.MAX_DOWNLOAD_STORAGE_LIMIT,
        titleRes = Res.string.settings_max_download_storage,
        searchTitleRes = Res.string.ss_max_download_storage_limit_title,
        searchSubtitleRes = Res.string.ss_max_download_storage_limit_subtitle,
        keywords = listOf("download", "storage", "limit", "max", "size", "cap", "gb"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Database
    )
,
    SettingsRowRecord(
        id = StorageSettingsIds.DOWNLOAD_STORAGE_LOCATION,
        titleRes = Res.string.settings_download_storage_location,
        searchTitleRes = Res.string.ss_download_storage_location_title,
        searchSubtitleRes = Res.string.ss_download_storage_location_subtitle,
        keywords = listOf("download", "storage", "location", "folder", "sd card", "internal"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Folder
    ),
    SettingsRowRecord(
        id = StorageSettingsIds.AUTO_DELETE_AFTER_WATCH,
        titleRes = Res.string.downloads_auto_delete_after_watch,
        searchTitleRes = Res.string.ss_auto_delete_after_watch_title,
        searchSubtitleRes = Res.string.ss_auto_delete_after_watch_subtitle,
        keywords = listOf("auto delete", "after watching", "watched", "delete downloads", "cleanup", "space"),
        route = Route.StorageSettings(),
        icon = Tabler.Outline.Trash
    ))

/** The catalog projection of `StorageDownloadsRowRecords`: the search faces + the shared category. */
internal val StorageDownloadsSearchItems: List<SettingsSearchItem> = StorageDownloadsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_storage)


/**
 * The cache group's per-id declared row admissions — full coverage, so
 * `rowTotalFor` (plus the screen's explicit cache-used +1 term) reads one gate
 * per id: the base gate is each record's own `isAdvanced` flag — the two
 * clear rows always render, the five tuning rows ride the advanced toggle
 * (the screen's `if (showAdvanced)` block reads the same gates).
 */
internal val StorageCacheRowAdmissions: Map<String, RowAdmission> =
    StorageCacheRowRecords.admissionsByAdvancedFlag()

/**
 * The downloads group's per-id declared row admissions — full coverage, so
 * `rowTotalFor` and StorageSettingsScreen's emission `if`s read one gate per
 * id. The base gate is each record's own `isAdvanced` flag (every download
 * record renders unconditionally); the overrides are the three
 * `download_schedule_*` window rows, which only render while their parent
 * toggle is on.
 */
internal val StorageDownloadsRowAdmissions: Map<String, RowAdmission> =
    StorageDownloadsRowRecords.admissionsByAdvancedFlag() + mapOf(
        StorageSettingsIds.DOWNLOAD_SCHEDULE_START to RowAdmission.WhenOn(StorageSettingsIds.DOWNLOAD_SCHEDULE),
        StorageSettingsIds.DOWNLOAD_SCHEDULE_END to RowAdmission.WhenOn(StorageSettingsIds.DOWNLOAD_SCHEDULE),
        StorageSettingsIds.DOWNLOAD_SCHEDULE_WIFI_ONLY to RowAdmission.WhenOn(StorageSettingsIds.DOWNLOAD_SCHEDULE),
    )
