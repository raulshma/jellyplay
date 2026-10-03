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
 * Source-scanning on plain text (no PSI) — deliberately cheap, but
 * executable, same shape as the desktop KoinModuleRegistrationGuardTest.
 */
class SettingsSectionKeysGuardTest {

    private val screenFile =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/SettingsScreen.kt"

    /** `settingsSection("key"` — every entrance call site passes a literal key. */
    private val callSite = Regex("""settingsSection\s*\(\s*"([A-Za-z0-9_]+)"""")

    @Test
    fun everyScreenSectionCallSite_isDeclaredInTheEntranceList() {
        val root = repoRoot()
        val source = root.resolve(screenFile)
        assertTrue(source.isFile, "missing $screenFile — repo layout changed?")

        val screenKeys = callSite.findAll(stripComments(source.readText()))
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            screenKeys.size >= 20,
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

        val dead = declaredKeys - screenKeys.toSet()
        assertTrue(
            dead.isEmpty(),
            "SETTINGS_ENTRANCE_SECTIONS declares section(s) with no settingsSection " +
                "call site in SettingsScreen.kt: $dead — a dead entry keeps numbering a " +
                "hole the screen never renders. Remove it or restore the call site.",
        )

        assertEquals(
            screenKeys.distinct().sorted(),
            declaredKeys.sorted(),
            "call-site set and declaration set disagree (also fires on a duplicated " +
                "declaration) — SETTINGS_ENTRANCE_SECTIONS must mirror SettingsScreen.kt's " +
                "sections exactly; the positional stagger numbering is pinned separately " +
                "in SettingsEntranceStepsTest",
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
