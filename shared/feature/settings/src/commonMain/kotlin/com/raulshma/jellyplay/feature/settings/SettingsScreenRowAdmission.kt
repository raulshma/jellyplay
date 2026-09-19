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
 *
 * A gated row's admission is declared ONCE, per id, beside the group item
 * declaration ([SettingsSearchItemGroup.admissions] — decision Q11a:
 * `SettingsSearchItem` in core/ui is not widened): the totals below and the
 * screens' emission `if`s (via `SettingsSearchItemGroup.rowAdmitted`) both
 * read that one [RowAdmission] value.
 */

/**
 * One gated row's admission predicate — the single declaration both the
 * row-total counts and the emission `if`s evaluate. Exactly today's gate
 * shapes, nothing speculative.
 */
internal sealed interface RowAdmission {
    /** Evaluates this gate against [RowAdmissionFlags]. */
    fun admitted(flags: RowAdmissionFlags): Boolean

    /** A [settingsCapabilities] platform-visibility gate (hidden = structurally absent). */
    data class Platform(val capability: RowAdmissionCapability) : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = when (capability) {
            RowAdmissionCapability.ScreenOrientation -> flags.supportsScreenOrientation
            RowAdmissionCapability.TouchGestures -> flags.supportsTouchGestures
        }
    }

    /** The TV form-factor gate (`LocalTvMode`). */
    data object Tv : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = flags.isTv
    }

    /** The advanced-toggle gate. */
    data object Advanced : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = flags.showAdvanced
    }

    /** A parent toggle's on-state gate — [parentId] is the parent row's id. */
    data class WhenOn(val parentId: String) : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = parentId in flags.parentsOn
    }

    /** Every gate must hold (an advanced strength row behind its parent toggle). */
    data class All(val gates: List<RowAdmission>) : RowAdmission {
        constructor(vararg gates: RowAdmission) : this(gates.toList())
        override fun admitted(flags: RowAdmissionFlags): Boolean = gates.all { it.admitted(flags) }
    }
}

/** The [RowAdmission.Platform] capability vocabulary — one entry per gating flag. */
internal enum class RowAdmissionCapability { ScreenOrientation, TouchGestures }

/**
 * The inputs a [RowAdmission] evaluates against. The capability flags default
 * to this binary's seam so screen call sites stay small; the contract test
 * injects both sides.
 */
internal class RowAdmissionFlags(
    val isTv: Boolean = false,
    val showAdvanced: Boolean = false,
    val supportsScreenOrientation: Boolean = settingsCapabilities.supportsScreenOrientation,
    val supportsTouchGestures: Boolean = settingsCapabilities.supportsTouchGestures,
    /** Parent row ids whose toggle is currently on — [RowAdmission.WhenOn] resolution. */
    val parentsOn: Set<String> = emptySet(),
)

/** [RowAdmissionFlags.parentsOn] from parent-id → on-state pairs. */
internal fun rowParentsOn(vararg toggles: Pair<String, Boolean>): Set<String> =
    toggles.toMap().filterValues { it }.keys

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
 * `download_schedule` toggle itself always does) — the declared
 * [RowAdmission.WhenOn] gate the screen's emission `if` reads too.
 */
internal fun storageDownloadsScreenRowTotal(downloadScheduleEnabled: Boolean): Int =
    SettingsScreenGroups.storageDownloads.items.count { item ->
        SettingsScreenGroups.storageDownloads.rowAdmitted(
            item.id,
            RowAdmissionFlags(parentsOn = rowParentsOn("download_schedule" to downloadScheduleEnabled)),
        )
    }

// ── PlaybackSettingsScreen ──────────────────────────────────────────────

/**
 * The playback screen's player group: the platform-gated rows drop where the
 * capability is missing, the TV rows need the TV form factor, and isAdvanced
 * rows only render behind the advanced toggle — each via the row's declared
 * [RowAdmission], the same predicate the screen's emission `if`s read. The
 * capability flags default to this binary's seam so call sites stay
 * two-argument; the contract test injects both sides.
 */
internal fun playbackPlayerScreenRowTotal(
    isTv: Boolean,
    showAdvanced: Boolean,
    supportsScreenOrientation: Boolean = settingsCapabilities.supportsScreenOrientation,
    supportsTouchGestures: Boolean = settingsCapabilities.supportsTouchGestures,
): Int {
    val flags = RowAdmissionFlags(
        isTv = isTv,
        showAdvanced = showAdvanced,
        supportsScreenOrientation = supportsScreenOrientation,
        supportsTouchGestures = supportsTouchGestures,
    )
    return SettingsScreenGroups.playbackPlayer.items.count { item ->
        SettingsScreenGroups.playbackPlayer.admissionOf(item.id)?.admitted(flags)
            ?: (showAdvanced || !item.isAdvanced)
    }
}

/**
 * The playback screen's advanced-video group: the dialogue-boost strength row
 * only renders while the dialogue-boost toggle is on (its declared
 * [RowAdmission.WhenOn] gate).
 */
internal fun playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled: Boolean): Int =
    SettingsScreenGroups.playbackAdvancedVideo.items.count { item ->
        SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(
            item.id,
            RowAdmissionFlags(parentsOn = rowParentsOn("dialogue_boost" to dialogueBoostEnabled)),
        )
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
