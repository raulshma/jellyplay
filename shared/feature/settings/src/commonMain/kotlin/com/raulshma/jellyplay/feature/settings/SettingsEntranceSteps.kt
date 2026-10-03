package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

/**
 * One entrance section of the settings root screen: the lazy-item [key] plus
 * the visibility axis it renders on. [tvOnly] marks the TV-only on-screen
 * group (the screensaver group) — it occupies a slot in the phone numbering
 * that no phone section ever reads, exactly like the hand-typed literal it
 * replaced (`settingsSection("group_screensaver", 16)` inside `if (isTv)`).
 */
internal data class SettingsEntranceSection(
    val key: String,
    val tvOnly: Boolean = false,
)

/**
 * One root-screen entrance section OWNED by its domain — the fused-conversion
 * twin of [SettingsEntranceSection]. A converted domain declares one of these
 * and single-homes what it owns: the entrance [key] (the step index derives
 * from the entry's position in [SETTINGS_ENTRANCE_SECTIONS]) AND the
 * section's static row face (icon, title resource, deep-link row id), which
 * the root screen's emission consumes. The subtitle (a summary of the
 * domain's preferences) and the route builder stay at the call site — they
 * read the root screen's state, not the domain's declaration.
 *
 * The rollout recipe (appearance first): declare the section row in the
 * domain's rows file, splice [section] into [SETTINGS_ENTRANCE_SECTIONS] at
 * the domain's render position, consume the faces at the
 * `settingsSection(…)` call site, and add the key to
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
 * at their render position — appearance first, then the whole fused-catalog
 * wave (home, playback, audio, language, notifications, storage, security,
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
    HomeEntrance.section,
    AppearanceEntrance.section,
    PlaybackEntrance.section,
    AudioEntrance.section,
    LanguageEntrance.section,
    NotificationEntrance.section,
    StorageEntrance.section,
    SecurityEntrance.section,
    SettingsEntranceSection("item_privacy_data"),
    BackupEntrance.section,
    SettingsEntranceSection("group_screensaver", tvOnly = true),
    SettingsEntranceSection("group_idle_ambient"),
    SettingsEntranceSection("group_discord_presence"),
    SettingsEntranceSection("group_shell_hooks"),
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
