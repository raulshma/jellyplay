package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.DownloadPreferences
import com.raulshma.jellyplay.core.model.StoragePreferences
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_storage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.downloads_auto_delete_after_watch
import com.raulshma.jellyplay.feature.settings.generated.resources.downloads_auto_delete_after_watch_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_adaptive_bitrate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_adaptive_bitrate_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_arm_from_arr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_arm_from_arr_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_delete_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_clean_up_now
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_keep_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_check_now
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_instant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_lookahead
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_lookahead_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_max_per_pass
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_max_per_pass_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_new
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_new_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_servers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_download_servers_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_offline
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_auto_offline_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_background_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_download_size_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_download_warning_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_streaming_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cellular_streaming_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_image_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connections_per_download
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connections_per_download_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_data_saver_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_data_saver_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_quality
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_quality_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_schedule_wifi_only_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_storage_location
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_storage_location_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_storage_network
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_manual_bandwidth_cap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_manual_bandwidth_cap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_cache_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_download_storage_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_max_simultaneous_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_metered_network_behavior
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_metered_network_behavior_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_diagnostics
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_timeouts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_network_timeouts_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_offline_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_end
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_start
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_schedule_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_smart_downloads
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_smart_downloads_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_verbose_logging
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_wifi_only
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_adaptive_bitrate_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_after_watch_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_after_watch_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_delete_cache_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_check_now_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_clean_up_now_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_keep_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_lookahead_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_max_per_pass_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_new_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_new_episodes_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_download_servers_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_offline_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_offline_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_arm_from_arr_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_auto_arm_from_arr_title
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
 * The storage domain's fused row declarations — the feature-side single home
 * of every storage row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] carries the search binding,
 * the screen face and the row identity in one declaration.
 *
 * Every row is a HAND-MAINTAINED residual (the knobs live in spec-less
 * stores) and carries its full hand search faces; the schedule-window and
 * auto-download rows declare their [RowAdmission.WhenOn] gates — the one
 * declaration both the derived totals and the screen emission `if`s read.
 * The projection ([List.toSearchItems]/[List.asRowGroup]) fails fast at
 * catalog init on any drift; search results, catalog order, group membership
 * and per-gate visibility derive from these declarations.
 */
internal object StorageRows {

    // -- The "Storage" (cache) group's seven rows — the two clear rows always-on, the five tuning rows advanced — in catalog order. --

    val ClearCache = SettingsRow(
        id = "clear_cache",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.settings_clear_cache,
        subtitleRes = Res.string.settings_clear_cache_subtitle,
        searchTitleRes = Res.string.ss_clear_cache_title,
        searchSubtitleRes = Res.string.ss_clear_cache_subtitle,
        keywords = listOf("clear cache", "trash", "free space", "clean", "reset"),
        route = Route.StorageSettings(),
    )

    val ClearImageCache = SettingsRow(
        id = "clear_image_cache",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_clear_image_cache,
        subtitleRes = Res.string.settings_clear_image_cache_subtitle,
        searchTitleRes = Res.string.ss_clear_image_cache_title,
        searchSubtitleRes = Res.string.ss_clear_image_cache_subtitle,
        keywords = listOf("image cache", "clear images", "posters", "thumbnails", "coil"),
        route = Route.StorageSettings(),
    )

    val WifiOnlyDownloads = SettingsRow(
        id = "wifi_only_downloads",
        icon = Tabler.Outline.Wifi,
        titleRes = Res.string.settings_wifi_only,
        searchTitleRes = Res.string.ss_wifi_only_downloads_title,
        searchSubtitleRes = Res.string.ss_wifi_only_downloads_subtitle,
        keywords = listOf("wifi only", "downloads", "cellular downloads", "data saving"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val AutoDeleteCache = SettingsRow(
        id = "auto_delete_cache",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_auto_delete_cache,
        searchTitleRes = Res.string.ss_auto_delete_cache_title,
        searchSubtitleRes = Res.string.ss_auto_delete_cache_subtitle,
        keywords = listOf("auto delete", "cache limit", "disk full"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val MaxCacheSize = SettingsRow(
        id = "max_cache_size",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_max_cache_size,
        subtitleRes = Res.string.settings_max_cache_size_subtitle,
        searchTitleRes = Res.string.ss_max_cache_size_title,
        searchSubtitleRes = Res.string.ss_max_cache_size_subtitle,
        keywords = listOf("max cache", "size limit", "cache limit"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val DownloadConnections = SettingsRow(
        id = "download_connections",
        icon = Tabler.Outline.Download,
        titleRes = Res.string.settings_connections_per_download,
        subtitleRes = Res.string.settings_connections_per_download_subtitle,
        searchTitleRes = Res.string.ss_download_connections_title,
        searchSubtitleRes = Res.string.ss_download_connections_subtitle,
        keywords = listOf("connections", "parallel", "streams", "download", "segments"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val MaxConcurrentDownloads = SettingsRow(
        id = "max_concurrent_downloads",
        icon = Tabler.Outline.ArrowBarToDown,
        titleRes = Res.string.settings_max_simultaneous_downloads,
        subtitleRes = Res.string.settings_max_simultaneous_downloads_subtitle,
        searchTitleRes = Res.string.ss_max_concurrent_downloads_title,
        searchSubtitleRes = Res.string.ss_max_concurrent_downloads_subtitle,
        keywords = listOf("concurrent", "simultaneous", "parallel", "downloads", "max", "queue"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    // The user_data_sync row keeps the shipped-quirk shape (an advanced tag
    // the screen never honored — the explicit Always gate overrides the
    // flag, the appearance HapticsEnabled shape); the other three
    // advanced-tagged rows moved to the advanced "Network diagnostics"
    // group, so their explicit gates came off.
    // -- The "Network & Offline" group's seven rows — every row renders unconditionally (the screen feeds the declaration size itself) — in catalog order. --

    val OfflineMode = SettingsRow(
        id = "offline_mode",
        icon = Tabler.Outline.CloudOff,
        titleRes = Res.string.settings_offline_mode,
        subtitleRes = Res.string.settings_offline_mode_subtitle,
        searchTitleRes = Res.string.ss_offline_mode_title,
        searchSubtitleRes = Res.string.ss_offline_mode_subtitle,
        keywords = listOf("offline", "airplane mode", "no network", "local only"),
        route = Route.StorageSettings(),
    )

    val AdaptiveBitrate = SettingsRow(
        id = "adaptive_bitrate",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_adaptive_bitrate,
        subtitleRes = Res.string.settings_adaptive_bitrate_subtitle,
        searchTitleRes = Res.string.ss_adaptive_bitrate_title,
        searchSubtitleRes = Res.string.ss_adaptive_bitrate_subtitle,
        keywords = listOf("adaptive bitrate", "network", "bandwidth", "cellular", "buffer"),
        route = Route.StorageSettings(),
    )

    val BandwidthCap = SettingsRow(
        id = "bandwidth_cap",
        icon = Tabler.Outline.Lock,
        titleRes = Res.string.settings_manual_bandwidth_cap,
        subtitleRes = Res.string.settings_manual_bandwidth_cap_subtitle,
        searchTitleRes = Res.string.ss_bandwidth_cap_title,
        searchSubtitleRes = Res.string.ss_bandwidth_cap_subtitle,
        keywords = listOf("bandwidth cap", "limit", "throttle", "data cap"),
        route = Route.StorageSettings(),
    )

    val DataSaver = SettingsRow(
        id = "data_saver",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_data_saver_mode,
        subtitleRes = Res.string.settings_data_saver_mode_subtitle,
        searchTitleRes = Res.string.ss_data_saver_title,
        searchSubtitleRes = Res.string.ss_data_saver_subtitle,
        keywords = listOf("data saver", "saving", "cellular usage", "bandwidth"),
        route = Route.StorageSettings(),
    )

    val UserDataSync = SettingsRow(
        id = "user_data_sync",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_background_sync,
        searchTitleRes = Res.string.ss_user_data_sync_title,
        searchSubtitleRes = Res.string.ss_user_data_sync_subtitle,
        keywords = listOf("sync", "background", "user-data", "favorites", "played", "progress", "worker"),
        route = Route.StorageSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    val AutoOffline = SettingsRow(
        id = "auto_offline",
        icon = Tabler.Outline.WifiOff,
        titleRes = Res.string.settings_auto_offline,
        subtitleRes = Res.string.settings_auto_offline_subtitle,
        searchTitleRes = Res.string.ss_auto_offline_title,
        searchSubtitleRes = Res.string.ss_auto_offline_subtitle,
        keywords = listOf("auto offline", "network lost", "offline", "automatic", "disconnect"),
        route = Route.StorageSettings(),
    )

    val CellularStreamingQuality = SettingsRow(
        id = "cellular_streaming_quality",
        icon = Tabler.Outline.DeviceMobile,
        titleRes = Res.string.settings_cellular_streaming_quality,
        subtitleRes = Res.string.settings_cellular_streaming_quality_subtitle,
        searchTitleRes = Res.string.ss_cellular_streaming_quality_title,
        searchSubtitleRes = Res.string.ss_cellular_streaming_quality_subtitle,
        keywords = listOf("cellular", "streaming", "quality", "mobile", "data", "resolution"),
        route = Route.StorageSettings(),
    )

    // -- The advanced "Network diagnostics" group's four rows (the phase-5
    //    reclassification) — the timeout/log/metered/warning fine-tuning, in
    //    the network group's relative order. --

    val CellularDownloadWarning = SettingsRow(
        id = "cellular_download_warning",
        icon = Tabler.Outline.AlertTriangle,
        titleRes = Res.string.settings_cellular_download_size_warning,
        subtitleRes = Res.string.settings_cellular_download_warning_subtitle,
        searchTitleRes = Res.string.ss_cellular_download_warning_title,
        searchSubtitleRes = Res.string.ss_cellular_download_warning_subtitle,
        keywords = listOf("cellular", "download", "warning", "size", "data", "mobile"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val NetworkTimeout = SettingsRow(
        id = "network_timeout",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_network_timeouts,
        subtitleRes = Res.string.settings_network_timeouts_subtitle,
        searchTitleRes = Res.string.ss_network_timeout_title,
        searchSubtitleRes = Res.string.ss_network_timeout_subtitle,
        keywords = listOf("timeout", "network", "connect", "read", "write", "slow"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val VerboseLogging = SettingsRow(
        id = "verbose_logging",
        icon = Tabler.Outline.Code,
        titleRes = Res.string.settings_verbose_logging,
        searchTitleRes = Res.string.ss_verbose_logging_title,
        searchSubtitleRes = Res.string.ss_verbose_logging_subtitle,
        keywords = listOf("verbose", "debug", "logging", "network", "http", "developer"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    val MeteredNetworkBehavior = SettingsRow(
        id = "metered_network_behavior",
        icon = Tabler.Outline.Compass,
        titleRes = Res.string.settings_metered_network_behavior,
        subtitleRes = Res.string.settings_metered_network_behavior_subtitle,
        searchTitleRes = Res.string.ss_metered_network_behavior_title,
        searchSubtitleRes = Res.string.ss_metered_network_behavior_subtitle,
        keywords = listOf("metered", "cellular", "behavior", "data", "mobile", "network"),
        route = Route.StorageSettings(),
        isAdvanced = true,
    )

    // -- The "Downloads" group's sixteen rows, in catalog order. --

    val DownloadQuality = SettingsRow(
        id = "download_quality",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_download_quality,
        subtitleRes = Res.string.settings_download_quality_subtitle,
        searchTitleRes = Res.string.ss_download_quality_title,
        searchSubtitleRes = Res.string.ss_download_quality_subtitle,
        keywords = listOf("download quality", "offline quality", "1080p downloads"),
        route = Route.DownloadsSettings(),
    )

    val SmartDownloads = SettingsRow(
        id = "smart_downloads",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.settings_smart_downloads,
        subtitleRes = Res.string.settings_smart_downloads_subtitle,
        searchTitleRes = Res.string.ss_smart_downloads_title,
        searchSubtitleRes = Res.string.ss_smart_downloads_subtitle,
        keywords = listOf("smart downloads", "auto delete", "episodes", "clean space"),
        route = Route.DownloadsSettings(),
    )

    val AutoDownloadMode = SettingsRow(
        id = "auto_download_mode",
        icon = Tabler.Outline.Download,
        titleRes = Res.string.settings_auto_download_mode,
        subtitleRes = Res.string.settings_auto_download_mode_subtitle,
        searchTitleRes = Res.string.ss_auto_download_mode_title,
        searchSubtitleRes = Res.string.ss_auto_download_mode_subtitle,
        keywords = listOf("auto download", "new episodes", "automatic", "next", "series", "instant", "ask", "never"),
        route = Route.DownloadsSettings(),
    )

    val AutoDownloadCheckNow = SettingsRow(
        id = "auto_download_check_now",
        icon = Tabler.Outline.Refresh,
        titleRes = Res.string.settings_auto_download_check_now,
        searchSubtitleRes = Res.string.ss_auto_download_check_now_subtitle,
        keywords = listOf("auto download", "check now", "run", "sync", "new episodes"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadMode.id),
    )

    val AutoDownloadLookahead = SettingsRow(
        id = "auto_download_lookahead",
        icon = Tabler.Outline.PlayerTrackNext,
        titleRes = Res.string.settings_auto_download_lookahead,
        subtitleRes = Res.string.settings_auto_download_lookahead_subtitle,
        searchSubtitleRes = Res.string.ss_auto_download_lookahead_subtitle,
        keywords = listOf("auto download", "lookahead", "ahead", "episodes", "next up"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadMode.id),
    )

    val AutoDownloadMaxPerPass = SettingsRow(
        id = "auto_download_max_per_pass",
        icon = Tabler.Outline.ArrowBarToDown,
        titleRes = Res.string.settings_auto_download_max_per_pass,
        subtitleRes = Res.string.settings_auto_download_max_per_pass_subtitle,
        searchSubtitleRes = Res.string.ss_auto_download_max_per_pass_subtitle,
        keywords = listOf("auto download", "max per pass", "budget", "limit", "check"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadMode.id),
    )

    val AutoDownloadKeepDays = SettingsRow(
        id = "auto_download_keep_days",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_auto_download_keep_days,
        subtitleRes = Res.string.settings_auto_download_keep_days_subtitle,
        searchSubtitleRes = Res.string.ss_auto_download_keep_days_subtitle,
        keywords = listOf("keep downloads", "retention", "days", "delete old", "cleanup", "space"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadMode.id),
    )

    val AutoDownloadServers = SettingsRow(
        id = "auto_download_servers",
        icon = Tabler.Outline.Server,
        titleRes = Res.string.settings_auto_download_servers,
        subtitleRes = Res.string.settings_auto_download_servers_subtitle,
        searchSubtitleRes = Res.string.ss_auto_download_servers_subtitle,
        keywords = listOf("auto download", "servers", "allow list", "restrict", "which server"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadMode.id),
    )

    /**
     * The gh-225 follow-up opt-in: the auto-download pass arms Sonarr-monitored
     * series even with zero downloaded episodes. Its admission rides the
     * experimental Direct *arr Integration flag ([RowAdmission.ContentGated] —
     * the screen reads the flag through the VM's
     * [com.raulshma.jellyplay.core.datastore.experimental.ExperimentalFeatureGate]
     * and owns both the emission `if` and the explicit count term), not a
     * [RowAdmissionFlags]-expressible gate.
     */
    val AutoArmFromArr = SettingsRow(
        id = "auto_arm_from_arr",
        icon = Tabler.Outline.Rss,
        titleRes = Res.string.settings_auto_arm_from_arr,
        subtitleRes = Res.string.settings_auto_arm_from_arr_subtitle,
        searchTitleRes = Res.string.ss_auto_arm_from_arr_title,
        searchSubtitleRes = Res.string.ss_auto_arm_from_arr_subtitle,
        keywords = listOf("auto arm", "sonarr", "arr", "auto download", "arm series", "tracked", "monitor"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.ContentGated,
    )

    val AutoDownloadCleanUpNow = SettingsRow(
        id = "auto_download_clean_up_now",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.settings_auto_download_clean_up_now,
        searchSubtitleRes = Res.string.ss_auto_download_clean_up_now_subtitle,
        keywords = listOf("clean up", "sweep", "retention", "delete old downloads", "free space"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.AutoDownloadKeepDays.id),
    )

    val AutoDeleteAfterWatch = SettingsRow(
        id = "auto_delete_after_watch",
        icon = Tabler.Outline.Trash,
        titleRes = Res.string.downloads_auto_delete_after_watch,
        subtitleRes = Res.string.downloads_auto_delete_after_watch_subtitle,
        searchTitleRes = Res.string.ss_auto_delete_after_watch_title,
        searchSubtitleRes = Res.string.ss_auto_delete_after_watch_subtitle,
        keywords = listOf("auto delete", "after watching", "watched", "delete downloads", "cleanup", "space"),
        route = Route.DownloadsSettings(),
    )

    val DownloadSchedule = SettingsRow(
        id = "download_schedule",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_download_schedule,
        subtitleRes = Res.string.settings_download_schedule_subtitle,
        searchTitleRes = Res.string.ss_download_schedule_title,
        searchSubtitleRes = Res.string.ss_download_schedule_subtitle,
        keywords = listOf("download", "schedule", "hours", "overnight", "window", "time"),
        route = Route.DownloadsSettings(),
    )

    val DownloadScheduleStart = SettingsRow(
        id = "download_schedule_start",
        icon = Tabler.Outline.Sun,
        titleRes = Res.string.settings_schedule_start,
        subtitleRes = Res.string.settings_schedule_start_subtitle,
        searchTitleRes = Res.string.ss_download_schedule_start_title,
        searchSubtitleRes = Res.string.ss_download_schedule_start_subtitle,
        keywords = listOf("download", "schedule", "start", "hour", "window", "begin"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.DownloadSchedule.id),
    )

    val DownloadScheduleEnd = SettingsRow(
        id = "download_schedule_end",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_schedule_end,
        subtitleRes = Res.string.settings_schedule_end_subtitle,
        searchTitleRes = Res.string.ss_download_schedule_end_title,
        searchSubtitleRes = Res.string.ss_download_schedule_end_subtitle,
        keywords = listOf("download", "schedule", "end", "hour", "window", "stop"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.DownloadSchedule.id),
    )

    val DownloadScheduleWifiOnly = SettingsRow(
        id = "download_schedule_wifi_only",
        icon = Tabler.Outline.Wifi,
        titleRes = Res.string.settings_download_schedule_wifi_only,
        subtitleRes = Res.string.settings_download_schedule_wifi_only_subtitle,
        searchTitleRes = Res.string.ss_download_schedule_wifi_only_title,
        searchSubtitleRes = Res.string.ss_download_schedule_wifi_only_subtitle,
        keywords = listOf("download", "schedule", "wifi only", "unmetered", "require"),
        route = Route.DownloadsSettings(),
        gate = RowAdmission.WhenOn(StorageRows.DownloadSchedule.id),
    )

    val MaxDownloadStorageLimit = SettingsRow(
        id = "max_download_storage_limit",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_max_download_storage,
        subtitleRes = Res.string.settings_max_download_storage_subtitle,
        searchTitleRes = Res.string.ss_max_download_storage_limit_title,
        searchSubtitleRes = Res.string.ss_max_download_storage_limit_subtitle,
        keywords = listOf("download", "storage", "limit", "max", "size", "cap", "gb"),
        route = Route.DownloadsSettings(),
    )

    val DownloadStorageLocation = SettingsRow(
        id = "download_storage_location",
        icon = Tabler.Outline.Folder,
        titleRes = Res.string.settings_download_storage_location,
        subtitleRes = Res.string.settings_download_storage_location_subtitle,
        searchTitleRes = Res.string.ss_download_storage_location_title,
        searchSubtitleRes = Res.string.ss_download_storage_location_subtitle,
        keywords = listOf("download", "storage", "location", "folder", "sd card", "internal"),
        route = Route.DownloadsSettings(),
    )

    /**
     * Every fused storage row. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = StorageCacheRows + StorageNetworkRows + StorageNetworkTuningRows + StorageDownloadsRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val storageCategory = CoreUiRes.string.ss_cat_storage

internal val StorageCacheRows: List<SettingsRow> = listOf(
    StorageRows.ClearCache,
    StorageRows.ClearImageCache,
    StorageRows.WifiOnlyDownloads,
    StorageRows.AutoDeleteCache,
    StorageRows.MaxCacheSize,
    StorageRows.DownloadConnections,
    StorageRows.MaxConcurrentDownloads,
)

/**
 * The "Network & Offline" group's visible rows: the connectivity/offline
 * toggles and the mainstream quality/sync picks. The diagnostics
 * fine-tuning moved to [StorageNetworkTuningRows].
 */
internal val StorageNetworkRows: List<SettingsRow> = listOf(
    StorageRows.OfflineMode,
    StorageRows.AdaptiveBitrate,
    StorageRows.BandwidthCap,
    StorageRows.DataSaver,
    StorageRows.UserDataSync,
    StorageRows.AutoOffline,
    StorageRows.CellularStreamingQuality,
)

/**
 * The advanced "Network diagnostics" group (the phase-5 reclassification,
 * ADR-0017 decision 6): the timeout/log/metered/warning fine-tuning, moved
 * off the network group verbatim in its relative order — whole-group behind
 * the advanced toggle. Their residual `isAdvanced` flags make the search
 * hits auto-arm the gate.
 */
internal val StorageNetworkTuningRows: List<SettingsRow> = listOf(
    StorageRows.CellularDownloadWarning,
    StorageRows.NetworkTimeout,
    StorageRows.VerboseLogging,
    StorageRows.MeteredNetworkBehavior,
)

internal val StorageDownloadsRows: List<SettingsRow> = listOf(
    StorageRows.DownloadQuality,
    StorageRows.SmartDownloads,
    StorageRows.AutoDownloadMode,
    StorageRows.AutoDownloadCheckNow,
    StorageRows.AutoDownloadLookahead,
    StorageRows.AutoDownloadMaxPerPass,
    StorageRows.AutoDownloadKeepDays,
    StorageRows.AutoDownloadServers,
    StorageRows.AutoArmFromArr,
    StorageRows.AutoDownloadCleanUpNow,
    StorageRows.AutoDeleteAfterWatch,
    StorageRows.DownloadSchedule,
    StorageRows.DownloadScheduleStart,
    StorageRows.DownloadScheduleEnd,
    StorageRows.DownloadScheduleWifiOnly,
    StorageRows.MaxDownloadStorageLimit,
    StorageRows.DownloadStorageLocation,
)

// ── The downloads group's parent-gated exception sets ────────────────────
// The [RowAdmission.WhenOn] clusters, named where the rows are declared so
// the contract tests' count arithmetic derives from these lists instead of
// restating hand-counted literals.

/** The three schedule-window rows riding [StorageRows.DownloadSchedule]. */
internal val StorageDownloadScheduleWindowRows: List<SettingsRow> = listOf(
    StorageRows.DownloadScheduleStart,
    StorageRows.DownloadScheduleEnd,
    StorageRows.DownloadScheduleWifiOnly,
)

/** The five auto-download retention rows riding [StorageRows.AutoDownloadMode] (check-now included). */
internal val StorageAutoDownloadRetentionRows: List<SettingsRow> = listOf(
    StorageRows.AutoDownloadCheckNow,
    StorageRows.AutoDownloadLookahead,
    StorageRows.AutoDownloadMaxPerPass,
    StorageRows.AutoDownloadKeepDays,
    StorageRows.AutoDownloadServers,
)

/** The clean-up action riding [StorageRows.AutoDownloadKeepDays] (the non-zero keep-days window the screen feeds). */
internal val StorageAutoDownloadCleanUpNowRows: List<SettingsRow> = listOf(
    StorageRows.AutoDownloadCleanUpNow,
)

/** The auto-arm row's [RowAdmission.ContentGated] exception — counted by no total (the strict derivation); the screen adds its term off the experimental Direct *arr flag. */
internal val StorageAutoArmContentGatedRows: List<SettingsRow> = listOf(
    StorageRows.AutoArmFromArr,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val StorageCacheGroup =
    StorageCacheRows.asRowGroup("storage.cache", emptyList(), searchRoutes, storageCategory)

internal val StorageNetworkGroup =
    StorageNetworkRows.asRowGroup("storage.network", emptyList(), searchRoutes, storageCategory)

/** The storage screen's advanced "Network diagnostics" group WITH its searchable card face (the group-card hit lands on the first member row). */
internal val StorageNetworkTuningGroup =
    StorageNetworkTuningRows.asRowGroup(
        "storage.networkTuning",
        emptyList(),
        searchRoutes,
        storageCategory,
        cardTitleRes = Res.string.settings_network_diagnostics,
        cardSubtitleRes = Res.string.settings_storage_network,
        cardKeywords = listOf("diagnostics", "network", "cellular", "metered", "timeout", "verbose"),
    )

internal val StorageDownloadsGroup =
    StorageDownloadsRows.asRowGroup("storage.downloads", emptyList(), searchRoutes, storageCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val StorageCacheSearchItems: List<SettingsSearchItem> = StorageCacheGroup.items

internal val StorageNetworkSearchItems: List<SettingsSearchItem> = StorageNetworkGroup.items

internal val StorageNetworkTuningSearchItems: List<SettingsSearchItem> = StorageNetworkTuningGroup.items

internal val StorageDownloadsSearchItems: List<SettingsSearchItem> = StorageDownloadsGroup.items

// -- The domain's root-screen entrance declarations --

/**
 * The storage domain's root-screen entrance — the ONE ordered declaration
 * that drives both the settings root's `item_storage` section emission
 * (icon/title/route id) and its entrance-step index (spliced into
 * [SETTINGS_ENTRANCE_SECTIONS] at this render position). ADR-0017's storage
 * split relabeled the row "Storage & network" (the key and row id unchanged —
 * the old label string stays in resources); the row opens the cache+network
 * screen the Downloads group moved out of.
 */
internal val StorageEntrance = SettingsEntranceSectionRow(
    key = "item_storage",
    rowId = "storage",
    icon = Tabler.Outline.Database,
    titleRes = Res.string.settings_storage_network,
)

/**
 * The downloads domain's root-screen entrance (ADR-0017's storage split) —
 * the sibling declaration spliced into [SETTINGS_ENTRANCE_SECTIONS] right
 * after [StorageEntrance] (under the same System & Data header): the
 * "Downloads" entrance opening [Route.DownloadsSettings], where the whole
 * Downloads group lives. The on-screen subtitle is the live
 * [downloadsSummaryParts] summary; the static subtitleRes here stays the
 * fold's all-default fallback face and the search face.
 */
internal val DownloadsEntrance = SettingsEntranceSectionRow(
    key = "item_downloads",
    rowId = "downloads",
    icon = Tabler.Outline.Download,
    titleRes = Res.string.settings_downloads,
)

// ── Entrance summaries (ADR-0017 decision 3) ────────────────────────────
// The root Storage & network and Downloads rows' live subtitles. Pure folds
// over the storage screen's and downloads screen's slices (the
// `storagePreferences` / `downloadPreferences` projections — the same slices
// those screens read; the cache figure is the root VM's live estimate, passed
// in to keep the fold pure). Pinned by SettingsSummariesTest.

/**
 * The root Storage & network entrance's summary: the offline-mode marker
 * while forced offline, the live cache figure (the root's existing estimate)
 * and the data-saver marker while on.
 */
internal fun storageSummaryParts(
    preferences: StoragePreferences,
    cacheSizeMb: Long,
): List<SettingsSummaryPart> = buildList {
    if (preferences.manualOfflineEnabled) {
        add(SettingsSummaryPart.Token(Res.string.settings_offline_mode))
    }
    add(
        SettingsSummaryPart.Formatted(
            Res.string.settings_cache_subtitle,
            cacheSizeMb,
        ),
    )
    if (preferences.dataSaverEnabled) {
        add(SettingsSummaryPart.Token(Res.string.settings_data_saver_mode))
    }
}

/**
 * The root Downloads entrance's summary: the smart-downloads marker while on
 * and the auto-download policy off NEVER; the untouched default falls back to
 * the entrance's static subtitle.
 */
internal fun downloadsSummaryParts(preferences: DownloadPreferences): List<SettingsSummaryPart> = buildList {
    if (preferences.smartDownloadsEnabled) {
        add(SettingsSummaryPart.Token(Res.string.settings_smart_downloads))
    }
    when (preferences.autoDownloadMode) {
        AutoDownloadMode.INSTANT -> add(SettingsSummaryPart.Token(Res.string.settings_auto_download_mode_instant))
        AutoDownloadMode.ASK -> add(SettingsSummaryPart.Token(Res.string.settings_auto_download_mode_ask))
        // The pass stands down (and FOLLOW_GLOBAL is a series-row sentinel —
        // never a legal global value), so the default face carries.
        AutoDownloadMode.NEVER, AutoDownloadMode.FOLLOW_GLOBAL -> Unit
    }
    if (isEmpty()) add(SettingsSummaryPart.Token(Res.string.settings_downloads_subtitle))
}
