package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

/**
 * One entrance section of the settings root screen: the lazy-item [key] plus
 * the visibility axis it renders on. [tvOnly] marks the TV-only on-screen
 * group (the screensaver group) — it occupies a slot in the phone numbering
 * that no phone section ever reads, exactly like the hand-typed literal it
 * replaced (`settingsSection("group_screensaver", 16)` inside `if (isTv)`).
 *
 * [revealGroup] marks the section that hosts a search-reveal group — a
 * `SettingsScreenGroups` group whose rows are bare-`Route.Settings` catalog
 * targets (the NoOp arm of `settingsResultClickAction`): the section's card
 * expands for its ids and the pane scrolls here on a search-reveal tap
 * ([resolveRootRevealItemIndex]). Null for sections with no reveal rows.
 */
internal data class SettingsEntranceSection(
    val key: String,
    val tvOnly: Boolean = false,
    val revealGroup: SettingsSearchItemGroup? = null,
)

/**
 * One root-screen entrance section OWNED by its domain — the fused twin of
 * [SettingsEntranceSection]. A converted domain declares one of these
 * and single-homes what it owns: the entrance [key] (the step index derives
 * from the entry's position in [SETTINGS_ENTRANCE_SECTIONS]) AND the
 * section's static row face (icon, title resource, deep-link row id), which
 * the root screen's emission consumes. The subtitle (a summary of the
 * domain's preferences) and the route builder stay at the call site — they
 * read the root screen's state, not the domain's declaration.
 *
 * To add a section: declare the section row in the domain's rows file, splice
 * [section] into [SETTINGS_ENTRANCE_SECTIONS] at the domain's render position,
 * consume the faces at the `settingsSection(…)` call site, and add the key to
 * `SettingsSectionKeysGuardTest`'s converted set — the guard's regex scanner
 * cannot see declaration-driven call sites, and the compile-time key pairing
 * makes the scan redundant for converted domains.
 */
internal data class SettingsEntranceSectionRow(
    val key: String,
    /** The deep-link row id the root screen passes to `openSetting`. */
    val rowId: String,
    val icon: ImageVector,
    val titleRes: StringResource,
) {
    /** The entrance-list entry — spliced into [SETTINGS_ENTRANCE_SECTIONS]. */
    val section: SettingsEntranceSection get() = SettingsEntranceSection(key)
}

/**
 * The settings root screen's entrance sections in render order — the single
 * source of the staggered-entrance step numbering. Adding a section means
 * inserting one entry here; every follower renumbers automatically instead of
 * 19 hand-typed literals drifting apart.
 *
 * The list mirrors the `settingsSection(...)` call sites in [SettingsScreen]:
 * `item_notifications` and the `group_idle_ambient` / `group_discord_presence`
 * / `group_shell_hooks` groups are the platform-gated sections (they compose
 * only where the matching `settingsCapabilities` flag holds) but keep their
 * slots — today's literal numbers keep counting them on every platform, and
 * the pinned derivation must equal those numbers exactly (the accepted
 * stagger hole on platforms without the capability). The gate applies to the
 * CONTENT, never to this declaration: a call site whose key is missing here
 * throws on first composition (SettingsScreenKt.settingsSection fails fast),
 * which on desktop meant crashing on open — SettingsSectionKeysGuardTest
 * pins the mirror so the drift cannot ship again.
 *
 * Converted domains splice their OWN entry ([SettingsEntranceSectionRow.section])
 * at their render position — appearance first, then the whole fused
 * catalog (home, playback, audio, language, notifications, storage, security,
 * backup, integrations, about — each entry declared in the domain's rows file
 * beside its rows) — so the emission and the step index derive from one
 * declaration. The remaining literal entries are the sections with no fused
 * row domain (the composite profile/power-user/devices sections, the
 * capability-gated on-screen groups, privacy data, experimental, what's-new).
 */
internal val SETTINGS_ENTRANCE_SECTIONS: List<SettingsEntranceSection> = listOf(
    SettingsEntranceSection("profile"),
    SettingsEntranceSection("power_user_mode"),
    SettingsEntranceSection("active_devices"),
    SettingsEntranceSection("account"),
    SettingsEntranceSection("activity"),
    SettingsEntranceSection("system"),
    SettingsEntranceSection("group_jellyplay", revealGroup = SettingsScreenGroups.jellyplayFeatures),
    HomeEntrance.section,
    AppearanceEntrance.section,
    PerformanceEntrance.section,
    PlaybackEntrance.section,
    AudioEntrance.section,
    LanguageEntrance.section,
    NotificationEntrance.section,
    StorageEntrance.section,
    SecurityEntrance.section,
    SettingsEntranceSection("item_privacy_data"),
    BackupEntrance.section,
    SettingsEntranceSection("group_screensaver", tvOnly = true, revealGroup = SettingsScreenGroups.systemScreensaver),
    SettingsEntranceSection("group_idle_ambient", revealGroup = SettingsScreenGroups.systemIdleAmbient),
    SettingsEntranceSection("group_discord_presence", revealGroup = SettingsScreenGroups.systemDiscordPresence),
    SettingsEntranceSection("group_shell_hooks", revealGroup = SettingsScreenGroups.systemHooks),
    SettingsEntranceSection("item_experimental"),
    IntegrationsEntrance.section,
    AboutEntrance.section,
    SettingsEntranceSection("item_whatsnew"),
)

/** The derived entrance steps for one section: phone and TV stagger indexes. */
internal data class SettingsEntranceSteps(
    val phone: Int,
    val tv: Int,
)

/**
 * Pure index lookup of a section's (phone, tv) entrance steps: TV numbers are
 * the section's index in [SETTINGS_ENTRANCE_SECTIONS]; phone numbers skip the
 * [tvOnly] entries (so the TV-only screensaver group shifts only the
 * followers that can actually render after it). Returns `null` for a key not
 * declared in the list — the call site fails fast instead of silently
 * numbering a section that was never inserted.
 */
internal fun settingsEntranceStep(key: String): SettingsEntranceSteps? {
    val index = SETTINGS_ENTRANCE_SECTIONS.indexOfFirst { it.key == key }
    if (index < 0) return null
    val phone = SETTINGS_ENTRANCE_SECTIONS.take(index).count { !it.tvOnly }
    return SettingsEntranceSteps(phone = phone, tv = index)
}

/**
 * The root browse pane's reveal-scroll resolution — the sub-screens'
 * `resolveHighlightScrollIndex` idiom over this declaration. A search-reveal
 * tap (the `settingsResultClickAction` NoOp arm) marks `lastClickedSettingId`,
 * and the pane must SCROLL to the section hosting that id: the highlighted
 * row's own focus choreography (SettingListItem's 400ms-delayed focus grab +
 * bring-into-view) can only fire once its lazy slot composes, and an
 * off-screen section never composes — so before this scroll existed, a
 * search-reveal into a below-fold group landed on a screen that never moved:
 * no reveal, no focus animation.
 *
 * Returns the browse pane's LazyColumn ITEM index for the section hosting
 * [revealId] on THIS platform: the declaration index minus every gated
 * section before it whose gate is closed here (the pane's `if` skips those
 * lazy items entirely — the stagger numbering tolerates the hole, the item
 * index must not). `-1` when the id is null or no reveal group hosts it —
 * navigation ids (the entrance/account rows) navigate away and must not
 * scroll the pane on back. Pure, so the contract tests pin it directly.
 */
internal fun resolveRootRevealItemIndex(
    revealId: String?,
    isTv: Boolean,
    capabilities: SettingsCapabilities,
): Int {
    if (revealId == null) return -1
    val hostIndex = SETTINGS_ENTRANCE_SECTIONS.indexOfFirst { section ->
        section.revealGroup != null && revealId in section.revealGroup.itemIdSet
    }
    if (hostIndex < 0) return -1
    val host = SETTINGS_ENTRANCE_SECTIONS[hostIndex]
    // A host whose own gate is closed here renders no section at all (a TV
    // group on a phone; a desktop-backed group on Android) — nothing to
    // scroll to; the catalog's platform filter should not have offered the
    // id in the first place.
    if (!sectionGateOpen(host, isTv, capabilities)) return -1
    val gatedOff = SETTINGS_ENTRANCE_SECTIONS.take(hostIndex).count { !sectionGateOpen(it, isTv, capabilities) }
    return hostIndex - gatedOff
}

/**
 * The pane's per-section presence gates, mirrored from SettingsBrowsePane's
 * emission `if`s (the declaration's [SettingsEntranceSection.tvOnly] plus the
 * four capability flags). `SettingsRootRevealScrollTest` pins this mirror
 * against the pane source — the same textual pin
 * SettingsSectionKeysGuardTest runs for the keys themselves.
 */
private fun sectionGateOpen(
    section: SettingsEntranceSection,
    isTv: Boolean,
    capabilities: SettingsCapabilities,
): Boolean = when (section.key) {
    "group_screensaver" -> isTv
    NotificationEntrance.key -> capabilities.supportsNotifications
    "group_idle_ambient" -> capabilities.supportsIdleAmbientScreen
    "group_discord_presence" -> capabilities.supportsDiscordPresence
    "group_shell_hooks" -> capabilities.supportsShellHooks
    else -> true
}
