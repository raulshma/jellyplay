package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * The single row-total derivation — the per-group `*ScreenRowTotal` functions
 * collapsed into one: a pure function of the catalog group declaration plus
 * the flag inputs that genuinely gate rows (advanced toggle, parent toggles,
 * platform visibility), so the `SettingsItemList(total = …)` a screen feeds
 * can never drift from the rows it actually emits. The per-row index is owned
 * by `SettingsItemList` itself (the core auto-indexing container), which leaves
 * the screens with no mutable counters at all.
 *
 * Every function here is pure (and internal) so
 * `SettingsCatalogScreenContractTest` can pin each gate against the
 * declaration.
 *
 * A gated row's admission is declared ONCE, per id, beside the group item
 * declaration ([SettingsSearchItemGroup.admissions] — decision Q11a:
 * `SettingsSearchItem` in core/ui is not widened): [rowTotalFor] and the
 * screens' emission `if`s (via `SettingsSearchItemGroup.rowAdmitted`) both
 * read that one [RowAdmission] value. Coverage is total: every id a group's
 * screen feeds a total from declares its gate (the strict unknown-id default
 * counts nothing, so a missing declaration fails the count loudly), with the
 * base gate derived from the record's own `isAdvanced` flag
 * ([admissionsByAdvancedFlag]) and only the genuinely-gated rows overridden.
 */

/**
 * One gated row's admission predicate — the single declaration both the
 * row-total derivation and the emission `if`s evaluate. Exactly today's gate
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
            RowAdmissionCapability.AppLocaleOverride -> flags.supportsAppLocaleOverride
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
     * Always admitted — the explicit "no gate": the declaration states the
     * row renders unconditionally instead of relying on any default.
     */
    data object Always : RowAdmission {
        override fun admitted(flags: RowAdmissionFlags): Boolean = true
    }
}

/** The [RowAdmission.Platform] capability vocabulary — one entry per gating flag. */
internal enum class RowAdmissionCapability { ScreenOrientation, TouchGestures, SystemNotificationSettings, Biometric, AudioDeviceSelection, MpvRenderProfiles, VolumeMemory, IdleAmbientScreen, AppLocaleOverride }

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
    /** The per-app display-language row's capability flag (the `AppLocaleSetter` seam). */
    val supportsAppLocaleOverride: Boolean = settingsCapabilities.supportsAppLocaleOverride,
    /** Parent row ids whose toggle is currently on — [RowAdmission.WhenOn] resolution. */
    val parentsOn: Set<String> = emptySet(),
)

/** [RowAdmissionFlags.parentsOn] from parent-id → on-state pairs. */
internal fun rowParentsOn(vararg toggles: Pair<String, Boolean>): Set<String> =
    toggles.toMap().filterValues { it }.keys

/**
 * The default admission of every record a group declares: the record's own
 * `isAdvanced` flag — an advanced row rides the advanced toggle, the rest
 * always render. This is the exact predicate the retired per-screen counts
 * fell back to for undeclared ids (`!isAdvanced || showAdvanced`), promoted
 * to an explicit declaration so the strict derivation needs no fallback. The
 * `*RowAdmissions` maps spread this base and override the genuinely-gated
 * ids.
 */
internal fun List<SettingsRowRecord>.admissionsByAdvancedFlag(): Map<String, RowAdmission> =
    associate { record -> record.id to if (record.isAdvanced) RowAdmission.Advanced else RowAdmission.Always }

/**
 * THE row-total derivation: how many of [SettingsSearchItemGroup.items] the
 * screen emits under [flags] — every included row's declared
 * [RowAdmission] evaluated, strict about ids that declare no gate (they are
 * NOT counted: a row added to a declaration without an admission fails the
 * count loudly instead of silently matching the rows that declare one).
 * Group-shaped rows whose screen renders only a subset pass an [include]
 * filter (the playback engine group's per-engine prefix).
 *
 * Screens whose group carries screen-local rows outside the declaration (the
 * storage cache-used info row, the confirm-library-reset action row) add the
 * `+ 1` explicitly at the call site, with a comment; rows gated by content
 * state with no admission vocabulary (the home display group's unhide row)
 * stay undeclared and ride the same explicit-term shape.
 */
internal fun rowTotalFor(
    group: SettingsSearchItemGroup,
    flags: RowAdmissionFlags,
    include: (SettingsSearchItem) -> Boolean = { true },
): Int = group.items.count { item -> include(item) && (group.admissionOf(item.id)?.admitted(flags) ?: false) }

// ── PlaybackSettingsScreen ──────────────────────────────────────────────

/**
 * The playback screen's engine-config branch for one engine: the branch's
 * declared rows (every id carrying [idPrefix], e.g. `"mpv_"`) that their
 * declared admissions admit — the desktop-gated mpv audio-device rows
 * drop where the platform has no enumerator; every engine row declares
 * [RowAdmission.Advanced] (the branch only composes behind the advanced
 * toggle, which the flags carry) — plus the one reset row (the declared
 * `reset_engine_defaults` item renders as that row in every engine branch,
 * a screen-local +1 like the storage cache-used info row).
 */
internal fun playbackEngineScreenRowTotal(
    idPrefix: String,
    supportsAudioDeviceSelection: Boolean = settingsCapabilities.supportsAudioDeviceSelection,
    supportsMpvRenderProfiles: Boolean = settingsCapabilities.supportsMpvRenderProfiles,
): Int {
    val flags = RowAdmissionFlags(
        showAdvanced = true,
        supportsAudioDeviceSelection = supportsAudioDeviceSelection,
        supportsMpvRenderProfiles = supportsMpvRenderProfiles,
    )
    return rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith(idPrefix) } + 1
}

// ── AppearanceSettingsScreen ────────────────────────────────────────────

/**
 * The appearance screen's "Library & Cards" group: the declared library rows
 * (every row renders unconditionally, so the declaration size is the whole
 * gate state) plus the confirm-library-reset action row (a screen-local row
 * with no search entry — the explicit +1 term).
 */
internal fun appearanceLibraryScreenRowTotal(): Int =
    SettingsScreenGroups.appearanceLibrary.items.size + 1
