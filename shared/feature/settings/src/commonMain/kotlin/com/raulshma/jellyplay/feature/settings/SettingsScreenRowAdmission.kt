package com.raulshma.jellyplay.feature.settings

/**
 * Per-group row-admission totals — the `audioScreenRowTotal` shape extended to
 * every screen group that used to hand-run the count (or hand-bump a row
 * index): a pure function of the catalog group declaration plus the flag
 * inputs that genuinely gate rows (advanced toggle, parent toggles, platform
 * visibility), so the `SettingsItemList(total = …)` a screen feeds can never
 * drift from the rows it actually emits. The per-row index is owned by
 * `SettingsItemList` itself (the core auto-indexing container), which leaves
 * the screens with no mutable counters at all.
 *
 * Every function here is pure (and internal) so
 * `SettingsCatalogScreenContractTest` can pin each gate against the
 * declaration; the screens consume them (consumption is itself pinned by the
 * test's `requiredDerivationUsage`).
 */

// ── StorageSettingsScreen ───────────────────────────────────────────────

/**
 * The storage screen's "Storage" (cache) group: the cache-used info row (a
 * screen-local row with no search entry) plus the declared cache rows, the
 * advanced ones only behind the advanced toggle.
 */
internal fun storageCacheScreenRowTotal(showAdvanced: Boolean): Int =
    1 + SettingsScreenGroups.storageCache.items.count { item ->
        showAdvanced || !item.isAdvanced
    }

/**
 * The storage screen's "Downloads" group: the three declared
 * `download_schedule_*` window rows only render when scheduling is on (the
 * `download_schedule` toggle itself always does).
 */
internal fun storageDownloadsScreenRowTotal(downloadScheduleEnabled: Boolean): Int =
    SettingsScreenGroups.storageDownloads.items.count { item ->
        item.id == "download_schedule" ||
            !item.id.startsWith("download_schedule_") ||
            downloadScheduleEnabled
    }

// ── PlaybackSettingsScreen ──────────────────────────────────────────────

/**
 * The playback screen's player group: the platform-gated rows drop where the
 * capability is missing, the TV rows need the TV form factor, and isAdvanced
 * rows only render behind the advanced toggle. The capability flags default
 * to this binary's seam so call sites stay two-argument; the contract test
 * injects both sides.
 */
internal fun playbackPlayerScreenRowTotal(
    isTv: Boolean,
    showAdvanced: Boolean,
    supportsScreenOrientation: Boolean = settingsCapabilities.supportsScreenOrientation,
    supportsTouchGestures: Boolean = settingsCapabilities.supportsTouchGestures,
): Int = SettingsScreenGroups.playbackPlayer.items.count { item ->
    when (item.id) {
        "orientation" -> supportsScreenOrientation
        "seek_duration", "gestures", "gesture_indicator_side" -> supportsTouchGestures
        "android_tv_watch_next", "tv_zoom_mode" -> isTv
        else -> showAdvanced || !item.isAdvanced
    }
}

/**
 * The playback screen's advanced-video group: the dialogue-boost strength row
 * only renders while the dialogue-boost toggle is on.
 */
internal fun playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled: Boolean): Int =
    SettingsScreenGroups.playbackAdvancedVideo.items.count { item ->
        item.id != "dialogue_boost_strength" || dialogueBoostEnabled
    }

/**
 * The playback screen's engine-config branch for one engine: the branch's
 * declared rows (every id carrying [idPrefix], e.g. `"mpv_"`) plus the one
 * reset row — the declared `reset_engine_defaults` item renders as that row
 * in every engine branch.
 */
internal fun playbackEngineScreenRowTotal(idPrefix: String): Int =
    SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith(idPrefix) } + 1

// ── NotificationSettingsScreen ──────────────────────────────────────────

/**
 * The notification screen's single group: the master toggle always renders;
 * the four rows behind it (check frequency, sound, vibrate, lights) ride the
 * toggle; the quiet-hours trio and DND/max-per-check/libraries rows ride
 * advanced mode (the start/end pair additionally the quiet-hours toggle, the
 * system-settings row the platform intent). The hand-run arithmetic this
 * replaces (`1 + 4 + 4 + cap + 2`) never drifted from these gates — now it
 * cannot.
 */
internal fun notificationScreenRowTotal(
    enabled: Boolean,
    showAdvanced: Boolean,
    quietHoursEnabled: Boolean,
    canOpenSystemNotificationSettings: Boolean,
): Int = SettingsScreenGroups.notifications.items.count { item ->
    when (item.id) {
        "notifications_enable" -> true
        "notification_check_frequency", "notification_sound",
        "notification_vibrate", "notification_lights",
        -> enabled
        "quiet_hours", "respect_system_dnd", "max_per_check", "notification_libraries" ->
            enabled && showAdvanced
        "quiet_start", "quiet_end" ->
            enabled && showAdvanced && quietHoursEnabled
        "system_notification_settings" ->
            enabled && showAdvanced && canOpenSystemNotificationSettings
        else -> false
    }
}

// ── LanguageSettingsScreen ──────────────────────────────────────────────

/**
 * The language screen's "Language" group (the declared leading trio): the
 * per-app display-language row only renders where the `AppLocaleSetter` seam
 * is real.
 */
internal fun languageGeneralScreenRowTotal(showsAppLocaleRow: Boolean): Int =
    SettingsScreenGroups.languageGeneral.items.count { item ->
        showsAppLocaleRow || item.id != "app_language"
    }

/**
 * The language screen's "Subtitles" group: the tester/font-size/forced-only
 * rows always render — as does high-contrast subtitles, which the declaration
 * marks advanced but every mode shows — while the style rows only render
 * behind the advanced toggle and the HDR font-size row additionally behind
 * the HDR-style toggle.
 */
internal fun languageSubtitlesScreenRowTotal(
    showAdvanced: Boolean,
    hdrSubtitleStyleEnabled: Boolean,
): Int = SettingsScreenGroups.languageSubtitles.items.count { item ->
    when (item.id) {
        "high_contrast_subtitles" -> true
        "hdr_subtitle_font_size" -> showAdvanced && hdrSubtitleStyleEnabled
        else -> !item.isAdvanced || showAdvanced
    }
}

// ── AppearanceSettingsScreen ────────────────────────────────────────────

/**
 * The appearance screen's "Library & Cards" group: the declared library rows
 * plus the confirm-library-reset action row (a screen-local row with no
 * search entry).
 */
internal fun appearanceLibraryScreenRowTotal(): Int =
    SettingsScreenGroups.appearanceLibrary.items.size + 1

// ── SecuritySettingsScreen ──────────────────────────────────────────────

/**
 * The security screen's lock group: the pin row always renders, the biometric
 * row rides the platform+gate flag, and the auto-lock timer row rides the
 * advanced toggle. Two shipped count quirks are preserved verbatim (the
 * rendered rows may legitimately differ — these gates are the count the
 * screen has always fed): `pin_for_player_lock` renders behind the pin
 * toggle but was never admitted into the count, and the biometric row's
 * render gate additionally requires the runtime gate to exist while the
 * count only ever saw [canShowBiometric].
 */
internal fun securityScreenRowTotal(canShowBiometric: Boolean, showAdvanced: Boolean): Int =
    SettingsScreenGroups.security.items.count { item ->
        when (item.id) {
            "pin_lock" -> true
            "biometric_lock" -> canShowBiometric
            "auto_lock_timer" -> showAdvanced
            else -> false
        }
    }
