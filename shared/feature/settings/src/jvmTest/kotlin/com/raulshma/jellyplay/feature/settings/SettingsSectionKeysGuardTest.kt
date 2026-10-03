package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Screen↔declaration mirror guard for the settings root's entrance sections.
 *
 * `SettingsScreen`'s `settingsSection(key, …)` helper fails fast at
 * composition when its key is missing from [SETTINGS_ENTRANCE_SECTIONS] —
 * but that fail-fast is exactly what shipped the v0.11.2 desktop crash:
 * `group_discord_presence` / `group_shell_hooks` were added as
 * capability-gated call sites without declaration entries, and since those
 * capabilities are desktop-true / Android-false, every desktop build crashed
 * on opening Settings while every Android build (and the old
 * SettingsEntranceStepsTest, which pins the list only against its own
 * literals) stayed green. The runtime check is blind to the drift on the
 * platforms that don't compose the section.
 *
 * This guard scans the screen source for every `settingsSection("…")` call
 * site and asserts set-equality, BOTH directions, against the declaration
 * list:
 *  - a screen call site the list forgets fails (the v0.11.2 crash —
 *    caught in CI, not in a user's session);
 *  - a declared section with no call site fails (dead declaration — it
 *    silently numbers nothing);
 *  - a scan that stops matching fails (discovery rot would make the
 *    forward check vacuously green).
 *
 * CONVERTED domains are exempted from the scan both ways: their section
 * declaration ([SettingsEntranceSectionRow] — appearance's
 * [AppearanceEntrance]) is spliced into [SETTINGS_ENTRANCE_SECTIONS] and its
 * call site reads the same object's key, so the pairing is compile-time and
 * the text scan cannot see it. The exemption set must list every converted
 * key — and the scan must NOT find it as a literal (a literal call site
 * reappearing means the single-homing regressed into a second home).
 *
 * Source-scanning on plain text (no PSI) — deliberately cheap, but
 * executable, same shape as the desktop KoinModuleRegistrationGuardTest.
 */
class SettingsSectionKeysGuardTest {

    private val screenFile =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/SettingsScreen.kt"

    /** `settingsSection("key"` — every entrance call site passes a literal key. */
    private val callSite = Regex("""settingsSection\s*\(\s*"([A-Za-z0-9_]+)"""")

    /**
     * The converted domains' entrance keys — declaration-driven, invisible to
     * the [callSite] scan. Each fused-conversion wave adds its domain's key
     * here in the same change that splices its [SettingsEntranceSectionRow]
     * into [SETTINGS_ENTRANCE_SECTIONS]. The whole fused-catalog wave is
     * converted: appearance plus the home, playback, audio, language,
     * notifications, storage, security, backup, integrations and about
     * domains. The scan's reason persists for the sections with no fused row
     * domain — the composite profile/power-user/devices/account/activity/
     * system sections, privacy data, the capability-gated on-screen groups,
     * the experimental entry and what's-new.
     */
    private val convertedKeys: Set<String> = setOf(
        AppearanceEntrance.key,
        HomeEntrance.key,
        PlaybackEntrance.key,
        AudioEntrance.key,
        LanguageEntrance.key,
        NotificationEntrance.key,
        StorageEntrance.key,
        SecurityEntrance.key,
        BackupEntrance.key,
        IntegrationsEntrance.key,
        AboutEntrance.key,
    )

    @Test
    fun everyScreenSectionCallSite_isDeclaredInTheEntranceList() {
        val root = repoRoot()
        val source = root.resolve(screenFile)
        assertTrue(source.isFile, "missing $screenFile — repo layout changed?")

        val screenKeys = callSite.findAll(stripComments(source.readText()))
            .map { it.groupValues[1] }
            .toList()
        // The 13 remaining literal call sites: the sections with no fused row
        // domain (profile, power_user_mode, active_devices, account, activity,
        // system, item_privacy_data, group_screensaver, group_idle_ambient,
        // group_discord_presence, group_shell_hooks, item_experimental,
        // item_whatsnew). The 11 converted domains' call sites read their
        // SettingsEntranceSectionRow.key and are invisible to this scan —
        // a count below this floor means discovery is broken (renamed
        // helper? new call shape?), fix the scan here.
        assertTrue(
            screenKeys.size >= 13,
            "scanned only ${screenKeys.size} settingsSection call sites — discovery is " +
                "broken (renamed helper? new call shape?), fix the scan in " +
                javaClass.simpleName,
        )

        val declaredKeys = SETTINGS_ENTRANCE_SECTIONS.map { it.key }

        val undeclared = screenKeys.distinct() - declaredKeys.toSet()
        if (undeclared.isNotEmpty()) {
            fail(
                "settingsSection call site(s) missing from SETTINGS_ENTRANCE_SECTIONS — " +
                    "they crash on first composition wherever their capability gate " +
                    "opens (the v0.11.2 desktop settings-open crash):\n" +
                    undeclared.joinToString("\n") { key ->
                        "  - '$key' — add SettingsEntranceSection(\"$key\") in render order"
                    } +
                    "\nFix: declare each in $screenFile's list (SettingsEntranceSteps.kt), " +
                    "then re-pin the (phone, tv) literals in SettingsEntranceStepsTest.",
            )
        }

        val dead = declaredKeys - screenKeys.toSet() - convertedKeys
        assertTrue(
            dead.isEmpty(),
            "SETTINGS_ENTRANCE_SECTIONS declares section(s) with no settingsSection " +
                "call site in SettingsScreen.kt: $dead — a dead entry keeps numbering a " +
                "hole the screen never renders. Remove it or restore the call site.",
        )

        assertEquals(
            screenKeys.distinct().sorted(),
            (declaredKeys - convertedKeys).sorted(),
            "call-site set and declaration set disagree (also fires on a duplicated " +
                "declaration) — SETTINGS_ENTRANCE_SECTIONS must mirror SettingsScreen.kt's " +
                "sections exactly; the positional stagger numbering is pinned separately " +
                "in SettingsEntranceStepsTest",
        )
    }

    @Test
    fun convertedSections_stayDeclarationDriven_notLiteralCallSites() {
        // A converted key reappearing as a literal call site means the
        // domain's single-homed entrance regressed into a second home (the
        // declaration AND a hand-typed key) — exactly the drift this wave's
        // shape exists to kill.
        val root = repoRoot()
        val source = root.resolve(screenFile)
        val screenKeys = callSite.findAll(stripComments(source.readText()))
            .map { it.groupValues[1] }
            .toSet()
        val regressed = convertedKeys intersect screenKeys
        assertTrue(
            regressed.isEmpty(),
            "converted entrance key(s) reappeared as literal settingsSection call " +
                "sites: $regressed — the call site must read the domain's " +
                "SettingsEntranceSectionRow.key instead",
        )
    }

    // ------------------------------------------------------------ plumbing

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions of the helper don't count as call sites. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
