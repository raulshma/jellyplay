package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_account
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_look_and_feel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_media_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_system_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations

/**
 * One entrance section of the settings root screen: the lazy-item [key] plus
 * the NON-CLICKABLE category [headerRes] the section renders above its
 * content when it leads a category (ADR-0017's flat root: entrance rows
 * wrapped under category headers — Account, Activity & Insights, Media &
 * playback, Look & feel, System & data, Integrations, About). The header is
 * a GROUP-grain fact declared on this section entry — never a per-row
 * wrapper — so the root's category taxonomy single-homes on
 * [SETTINGS_ENTRANCE_SECTIONS] next to the emission order it governs.
 */
internal data class SettingsEntranceSection(
    val key: String,
    val headerRes: StringResource? = null,
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
 * source of the entrance-step numbering AND of the category headers
 * (ADR-0017): media-first flat list under non-clickable category headers,
 * account pinned top, ONE taxonomy across phone/TV/desktop (surface
 * differences ride [SettingsCapabilities] and the TV fork only — the former
 * separate phone/TV step lists are converged onto this one list).
 *
 * The list mirrors the `settingsSection(...)` call sites in the browse pane:
 * `item_notifications` and the Ambient / JellyPlay-plugin entrances are the
 * capability/probe-gated sections (their CONTENT composes only where the
 * gate opens — the header a category lead carries still renders, so a
 * category never loses its label to a closed gate). The gate applies to the
 * CONTENT, never to this declaration: a call site whose key is missing here
 * throws on first composition (SettingsScreenKt.settingsSection fails fast),
 * which on desktop meant crashing on open — SettingsSectionKeysGuardTest
 * pins the mirror so the drift cannot ship again.
 *
 * Converted domains splice their OWN entry ([SettingsEntranceSectionRow.section])
 * at their render position; the remaining literal entries are the sections
 * with no fused row domain (the composite profile/power-user/devices/
 * account/activity/system sections, privacy data, experimental, what's-new,
 * and the two composite entrances declared beside their domains' rows —
 * AmbientEntrance in SystemSettingsRows.kt, JellyPlayPluginEntrance in
 * JellyPlayFeatureSettingsRows.kt). Category headers ride the category's
 * LEAD section via [SettingsEntranceSection.headerRes] (`.copy` at the splice
 * for converted domains).
 */
internal val SETTINGS_ENTRANCE_SECTIONS: List<SettingsEntranceSection> = listOf(
    SettingsEntranceSection("profile"),
    SettingsEntranceSection("power_user_mode"),
    SettingsEntranceSection("active_devices", headerRes = Res.string.settings_account),
    SettingsEntranceSection("account"),
    SettingsEntranceSection("activity", headerRes = Res.string.settings_activity_insights),
    PlaybackEntrance.section.copy(headerRes = Res.string.settings_cat_media_playback),
    AudioEntrance.section,
    LanguageEntrance.section,
    AppearanceEntrance.section.copy(headerRes = Res.string.settings_cat_look_and_feel),
    HomeEntrance.section,
    PerformanceEntrance.section,
    AmbientEntrance.section,
    StorageEntrance.section.copy(headerRes = Res.string.settings_cat_system_data),
    DownloadsEntrance.section,
    NotificationEntrance.section,
    SecurityEntrance.section,
    SettingsEntranceSection("item_privacy_data"),
    BackupEntrance.section,
    SettingsEntranceSection("system"),
    JellyPlayPluginEntrance.section.copy(headerRes = Res.string.settings_integrations),
    IntegrationsEntrance.section,
    SettingsEntranceSection("item_experimental"),
    AboutEntrance.section.copy(headerRes = Res.string.settings_about),
    SettingsEntranceSection("item_whatsnew"),
)

/**
 * Pure index lookup of a section's entrance step: the section's index in
 * [SETTINGS_ENTRANCE_SECTIONS]. ONE list, ONE axis — the wizard-mirror
 * property: the stagger sequence is DERIVED from the same declaration that
 * drives root emission, never a second hand-maintained taxonomy. Returns
 * `null` for a key not declared in the list — the call site fails fast
 * instead of silently numbering a section that was never inserted.
 */
internal fun settingsEntranceStep(key: String): Int? =
    SETTINGS_ENTRANCE_SECTIONS.indexOfFirst { it.key == key }.takeIf { it >= 0 }
