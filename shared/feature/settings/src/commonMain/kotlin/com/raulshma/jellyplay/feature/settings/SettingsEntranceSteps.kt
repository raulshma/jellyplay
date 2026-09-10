package com.raulshma.jellyplay.feature.settings

/**
 * One entrance section of the settings root screen: the lazy-item [key] plus
 * the visibility axis it renders on. [tvOnly] marks the TV-only on-screen
 * group (the screensaver group) — it occupies a slot in the phone numbering
 * that no phone section ever reads, exactly like the hand-typed literal it
 * replaced (`settingsSection("group_screensaver", 15)` inside `if (isTv)`).
 */
internal data class SettingsEntranceSection(
    val key: String,
    val tvOnly: Boolean = false,
)

/**
 * The settings root screen's entrance sections in render order — the single
 * source of the staggered-entrance step numbering. Adding a section means
 * inserting one entry here; every follower renumbers automatically instead of
 * 19 hand-typed literals drifting apart.
 *
 * The list mirrors the `settingsSection(...)` call sites in [SettingsScreen]:
 * `item_notifications` is the platform-gated section (it composes only where
 * `settingsCapabilities.supportsNotifications` holds) but keeps its slot —
 * today's literal numbers keep counting it on every platform, and the pinned
 * derivation must equal those numbers exactly (the accepted stagger hole on
 * platforms without notifications).
 */
internal val SETTINGS_ENTRANCE_SECTIONS: List<SettingsEntranceSection> = listOf(
    SettingsEntranceSection("profile"),
    SettingsEntranceSection("power_user_mode"),
    SettingsEntranceSection("active_devices"),
    SettingsEntranceSection("account"),
    SettingsEntranceSection("activity"),
    SettingsEntranceSection("system"),
    SettingsEntranceSection("item_appearance"),
    SettingsEntranceSection("item_playback"),
    SettingsEntranceSection("item_audio"),
    SettingsEntranceSection("item_language"),
    SettingsEntranceSection("item_notifications"),
    SettingsEntranceSection("item_storage"),
    SettingsEntranceSection("item_security"),
    SettingsEntranceSection("item_privacy_data"),
    SettingsEntranceSection("item_backup"),
    SettingsEntranceSection("group_screensaver", tvOnly = true),
    SettingsEntranceSection("item_experimental"),
    SettingsEntranceSection("item_integrations"),
    SettingsEntranceSection("item_about"),
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
