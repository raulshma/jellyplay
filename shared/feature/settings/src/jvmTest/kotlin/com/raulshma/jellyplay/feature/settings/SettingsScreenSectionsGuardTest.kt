package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Screen↔declaration cross-check for the advanced screen-shape declarations
 * ([SettingsScreenSection], the payload-free projection).
 *
 * [deriveAdjustForAdvanced] folds a screen's `advancedOnly` flags into the
 * deep-link scroll adjust — but the flag and the screen's actual emission gate
 * live in different statements of the same file: a group flagged
 * `advancedOnly = true` whose emission is NOT inside the screen's
 * advanced-gated block (`if (showAdvanced) { … }`) would keep the adjust
 * scrolling to a group that always composes (and shift its always-visible
 * neighbours wrongly), silently. The compiler cannot see that pairing — the
 * flag is data, the gate is control flow.
 *
 * This guard scans the converted screens' source and asserts, per screen:
 *  - the set of sections flagged `advancedOnly = true` equals the expected
 *    emission map, BOTH directions (a new flag not mapped here fails loudly;
 *    an always-visible group flagged by mistake fails too);
 *  - each flagged group's emission call appears inside one of the screen's
 *    `if (showAdvanced) { … }` blocks.
 *
 * Source-scanning on plain text (no PSI) — deliberately cheap, but
 * executable, same shape as [SettingsSectionKeysGuardTest].
 */
class SettingsScreenSectionsGuardTest {

    private val screensPackageDir =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings"

    /**
     * The two converted screens: the groups each flags `advancedOnly` in its
     * [SettingsScreenSection] declaration, mapped to the composable emission
     * call that must live inside the screen's advanced-gated block.
     */
    private val advancedOnlyEmissions: Map<String, Map<String, String>> = mapOf(
        "PlaybackSettingsScreen.kt" to mapOf(
            "playbackAdvancedVideo" to "PlaybackAdvancedVideoGroup(",
            "playbackEngine" to "PlaybackEngineGroup(",
            "playbackMediaSegments" to "PlaybackMediaSegmentsGroup(",
        ),
        "AppearanceSettingsScreen.kt" to mapOf(
            "appearancePerformance" to "AppearancePerformanceGroup(",
            "appearanceEyeCare" to "AppearanceEyeCareGroup(",
            "appearanceNewsletter" to "AppearanceNewsletterGroup(",
        ),
    )

    /**
     * A sections-list entry flagged advanced-only, capturing the
     * `SettingsScreenGroups.X` reference — handles the one-line and
     * multi-line entry shapes alike (`\s*` spans newlines).
     */
    private val advancedOnlySection = Regex(
        """SettingsScreenSection\(\s*SettingsScreenGroups\.(\w+)\.itemIdSet,\s*advancedOnly\s*=\s*true""",
    )

    @Test
    fun everyAdvancedOnlySection_isEmittedInsideTheAdvancedGatedBlock() {
        val root = repoRoot()
        val packageDir = root.resolve(screensPackageDir)
        assertTrue(packageDir.isDirectory, "missing $screensPackageDir — repo layout changed?")

        for ((fileName, expectedEmissions) in advancedOnlyEmissions) {
            val file = packageDir.resolve(fileName)
            assertTrue(file.isFile, "missing $fileName — repo layout changed?")
            val text = stripComments(file.readText())

            val declared = advancedOnlySection.findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(
                expectedEmissions.keys.sorted(),
                declared.toSet().sorted(),
                "$fileName: the groups flagged advancedOnly in its SettingsScreenSection " +
                    "declaration drifted from the expected set — update " +
                    "${javaClass.simpleName}'s emission map in the same change (a flag " +
                    "without the gated emission shifts the derived scroll adjust wrongly).",
            )
            assertEquals(
                expectedEmissions.size,
                declared.size,
                "$fileName: a duplicate advancedOnly flag on '${declared.groupBy { it }.filterValues { it.size > 1 }.keys}'",
            )

            // The text inside every `if (showAdvanced) { … }` block (balanced
            // braces, comments stripped so doc mentions can't count).
            val gatedBlocks = mutableListOf<String>()
            var searchFrom = 0
            while (true) {
                val gate = text.indexOf("if (showAdvanced) {", searchFrom)
                if (gate < 0) break
                val openBrace = gate + "if (showAdvanced)".length
                var depth = 0
                val close = buildList {
                    for (i in openBrace until text.length) {
                        when (text[i]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    add(i)
                                    break
                                }
                            }
                        }
                    }
                }.firstOrNull()
                assertTrue(close != null, "$fileName: unbalanced braces after if (showAdvanced)")
                gatedBlocks += text.substring(openBrace + 1, close)
                searchFrom = close + 1
            }
            assertTrue(
                gatedBlocks.isNotEmpty(),
                "$fileName: no `if (showAdvanced) { … }` block found — discovery is " +
                    "broken (renamed gate? new emission shape?), fix the scan in " +
                    javaClass.simpleName,
            )

            val missing = expectedEmissions.filterValues { emission ->
                gatedBlocks.none { emission in it }
            }
            if (missing.isNotEmpty()) {
                fail(
                    "$fileName: advancedOnly-flagged group(s) emitted OUTSIDE the screen's " +
                        "advanced-gated block: ${missing.keys} — the flag drives " +
                        "deriveAdjustForAdvanced's scroll adjust, so a group that composes " +
                        "unconditionally must not carry it (move the emission inside " +
                        "`if (showAdvanced)` or drop the flag).",
                )
            }
        }
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

    /** Drops line and block comments so doc mentions don't count as declarations. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
