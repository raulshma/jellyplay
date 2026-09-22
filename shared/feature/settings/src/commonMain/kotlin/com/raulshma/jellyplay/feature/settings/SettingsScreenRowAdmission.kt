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
 * declaration.
 *
 * A gated row's admission is declared ONCE, per id, beside the group item
 * declaration ([SettingsSearchItemGroup.admissions] — decision Q11a:
 * `SettingsSearchItem` in core/ui is not widened): the totals below and the
 * screens' emission `if`s (via `SettingsSearchItemGroup.rowAdmitted`) both
 * read that one [RowAdmission] value. Totals choose one of two undeclared-id
 * defaults, each preserved from the hand-run arithmetic it replaced:
 * `?: (!item.isAdvanced || showAdvanced)` where the hand count fell back to
 * the advanced toggle (playback player, audio), and
 * `?: false` where every id was enumerated and anything else went uncounted
 * (notifications, security, language subtitles).
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
            RowAdmissionCapability.SystemNotificationSettings -> flags.supportsSystemNotificationSettings
            RowAdmissionCapability.Biometric -> flags.supportsBiometric
            RowAdmissionCapability.AudioDeviceSelection -> flags.supportsAudioDeviceSelection
            RowAdmissionCapability.MpvRenderProfiles -> flags.supportsMpvRenderProfiles
            RowAdmissionCapability.VolumeMemory -> flags.supportsVolumeMemory
            RowAdmissionCapability.IdleAmbientScreen -> flags.supportsIdleAmbientScreen
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

    /**
     * Always admitted — the explicit "no gate", for totals that count
     * strictly (`?: false`): the declaration states the row renders
     * unconditionally instead of relying on the undeclared default.
     */
    data object Always : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = true
    }
}

/** The [RowAdmission.Platform] capability vocabulary — one entry per gating flag. */
internal enum class RowAdmissionCapability { ScreenOrientation, TouchGestures, SystemNotificationSettings, Biometric, AudioDeviceSelection, MpvRenderProfiles, VolumeMemory, IdleAmbientScreen }

/**
 * The inputs a [RowAdmission] evaluates against. The capability flags default
 * to this binary's seam so screen call sites stay small; the contract test
 * injects both sides.
 */
internal data class RowAdmissionFlags(
    val isTv: Boolean = false,
    val showAdvanced: Boolean = false,
    val supportsScreenOrientation: Boolean = settingsCapabilities.supportsScreenOrientation,
    val supportsTouchGestures: Boolean = settingsCapabilities.supportsTouchGestures,
    /** The system-notification-settings row's platform-intent capability. */
    val supportsSystemNotificationSettings: Boolean = settingsCapabilities.supportsSystemNotificationSettings,
    /** The biometric row's capability flag — the screen passes its gate-aware computed value. */
    val supportsBiometric: Boolean = settingsCapabilities.supportsBiometric,
    /** The desktop mpv audio-device rows' capability flag. */
    val supportsAudioDeviceSelection: Boolean = settingsCapabilities.supportsAudioDeviceSelection,
    /** The desktop mpv render rows' capability flag. */
    val supportsMpvRenderProfiles: Boolean = settingsCapabilities.supportsMpvRenderProfiles,
    /** The per-content-type volume-memory toggle's capability flag. */
    val supportsVolumeMemory: Boolean = settingsCapabilities.supportsVolumeMemory,
    /** The desktop idle ambient screen rows' capability flag. */
    val supportsIdleAmbientScreen: Boolean = settingsCapabilities.supportsIdleAmbientScreen,
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
            RowAdmissionFlags(parentsOn = rowParentsOn(StorageSettingsIds.DOWNLOAD_SCHEDULE to downloadScheduleEnabled)),
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
            RowAdmissionFlags(parentsOn = rowParentsOn(PlaybackSettingsIds.DIALOGUE_BOOST to dialogueBoostEnabled)),
        )
    }

/**
 * The playback screen's engine-config branch for one engine: the branch's
 * declared rows (every id carrying [idPrefix], e.g. `"mpv_"`) that their
 * declared admissions admit — the desktop-gated mpv audio-device rows
 * drop where the platform has no enumerator — plus the one reset row (the
 * declared `reset_engine_defaults` item renders as that row in every engine
 * branch).
 */
internal fun playbackEngineScreenRowTotal(
    idPrefix: String,
    supportsAudioDeviceSelection: Boolean = settingsCapabilities.supportsAudioDeviceSelection,
    supportsMpvRenderProfiles: Boolean = settingsCapabilities.supportsMpvRenderProfiles,
): Int {
    val flags = RowAdmissionFlags(
        supportsAudioDeviceSelection = supportsAudioDeviceSelection,
        supportsMpvRenderProfiles = supportsMpvRenderProfiles,
    )
    return SettingsScreenGroups.playbackEngine.items.count { item ->
        item.id.startsWith(idPrefix) &&
            SettingsScreenGroups.playbackEngine.rowAdmitted(item.id, flags)
    } + 1
}

// ── NotificationSettingsScreen ──────────────────────────────────────────

/**
 * The notification screen's single group, through its declared admissions:
 * the master toggle always renders ([RowAdmission.Always]); the four rows
 * behind it (check frequency, sound, vibrate, lights) ride the toggle; the
 * quiet-hours trio and DND/max-per-check/libraries rows ride advanced mode
 * (the start/end pair additionally the quiet-hours toggle, the
 * system-settings row the platform intent). Undeclared ids are NOT counted —
 * the strict `else -> false` the hand-run `when` (the
 * `1 + 4 + 4 + cap + 2` arithmetic it replaced) used, preserved verbatim.
 */
internal fun notificationScreenRowTotal(
    enabled: Boolean,
    showAdvanced: Boolean,
    quietHoursEnabled: Boolean,
    canOpenSystemNotificationSettings: Boolean,
): Int {
    val flags = RowAdmissionFlags(
        showAdvanced = showAdvanced,
        supportsSystemNotificationSettings = canOpenSystemNotificationSettings,
        parentsOn = rowParentsOn(
            NotificationSettingsIds.NOTIFICATIONS_ENABLE to enabled,
            NotificationSettingsIds.QUIET_HOURS to quietHoursEnabled,
        ),
    )
    return SettingsScreenGroups.notifications.items.count { item ->
        SettingsScreenGroups.notifications.admissionOf(item.id)?.admitted(flags) ?: false
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
        showsAppLocaleRow || item.id != LanguageSettingsIds.APP_LANGUAGE
    }

/**
 * The language screen's "Track Selection" group: every row declares
 * [RowAdmission.Always], so the total is the declaration size — counted
 * through the admissions function like its siblings so the strict-count
 * contract stays uniform.
 */
internal fun languageTrackSelectionScreenRowTotal(): Int =
    SettingsScreenGroups.languageTrackSelection.items.count { item ->
        SettingsScreenGroups.languageTrackSelection.admissionOf(item.id)
            ?.admitted(RowAdmissionFlags()) ?: false
    }

/**
 * The language screen's "Subtitles" group, through its declared admissions
 * (every id declared — the strict notifications/security shape): the
 * tester/font-size/forced-only rows always render — as does high-contrast
 * subtitles, which the declaration marks advanced but every mode shows (its
 * declared [RowAdmission.Always], the shipped quirk verbatim) — while the
 * style rows ride their declared [RowAdmission.Advanced] gate and the HDR
 * font-size row additionally the HDR-style toggle (its declared
 * All(Advanced, WhenOn) gate). Undeclared ids are NOT counted — the strict
 * `else -> false` default.
 */
internal fun languageSubtitlesScreenRowTotal(
    showAdvanced: Boolean,
    hdrSubtitleStyleEnabled: Boolean,
): Int {
    val flags = RowAdmissionFlags(
        showAdvanced = showAdvanced,
        parentsOn = rowParentsOn(LanguageSettingsIds.HDR_SUBTITLE_STYLE to hdrSubtitleStyleEnabled),
    )
    return SettingsScreenGroups.languageSubtitles.items.count { item ->
        SettingsScreenGroups.languageSubtitles.admissionOf(item.id)?.admitted(flags) ?: false
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
 * The security screen's lock group, through its declared admissions: the pin
 * row always renders ([RowAdmission.Always]), the biometric row rides the
 * platform+gate flag, and the auto-lock timer row rides the advanced toggle.
 * Two shipped count quirks are preserved verbatim: `pin_for_player_lock`
 * renders behind the pin toggle but carries NO declared admission (and the
 * strict default counts nothing undeclared), and the quick-connect /
 * remote-control rows render in their own single-row groups outside this
 * count. The biometric flag is the screen's gate-aware computed value, not
 * the raw capability.
 */
internal fun securityScreenRowTotal(canShowBiometric: Boolean, showAdvanced: Boolean): Int {
    val flags = RowAdmissionFlags(showAdvanced = showAdvanced, supportsBiometric = canShowBiometric)
    return SettingsScreenGroups.security.items.count { item ->
        SettingsScreenGroups.security.admissionOf(item.id)?.admitted(flags) ?: false
    }
}
