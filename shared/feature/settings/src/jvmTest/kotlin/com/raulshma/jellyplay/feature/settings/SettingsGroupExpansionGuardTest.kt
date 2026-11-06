package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * SettingsGroup expansion-wiring guard (source-scan on plain text, no PSI —
 * deliberately cheap, executable, same shape as
 * [PreferenceScreenScaffoldWiringGuardTest] and its siblings).
 *
 * The search-reveal contract lives in TWO halves the data tests split
 * between them:
 *  - WHICH ids reveal on-screen → the root screen's coverage is pinned by
 *    `SettingsCatalogScreenContractTest`'s reveal tests over
 *    [SettingsScreenGroups.rootRevealGroups];
 *  - HOW a group expands for a highlight → every highlight-driven
 *    `SettingsGroup(initiallyExpanded = …)` expression must DERIVE from a
 *    declaration (`… in SettingsScreenGroups.<group>.itemIdSet`), never
 *    hand-test a subset of the group's ids.
 *
 * The second half is the `skip_prompt_duration` class of bug: the
 * media-segments group's expansion was `startsWith("media_segment_") ||
 * id == skip_segments_on_seek` while its declaration held nine ids —
 * `skip_prompt_duration` and `next_up_preroll` searched fine (the route
 * carried the highlight) but the group never expanded, so the highlighted
 * row stayed invisible inside a collapsed card. Hand-maintained id checks
 * drift exactly when a row is added to the group declaration; the
 * `itemIdSet` derivation cannot.
 *
 * Blind spots (documented, same caveat as the scaffold guard): the scan
 * sees the CONDITION's shape, not which group the call site renders — a
 * site deriving from the WRONG group's `itemIdSet` compiles and passes here;
 * the per-screen scroll pins and the reveal coverage tests bound that side.
 */
class SettingsGroupExpansionGuardTest {

    private val featurePackageDir =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings"

    /**
     * Floor: highlight-driven expansion expressions discovered across the
     * package. A discovery-rot regression (path moved, name changed) would
     * otherwise make the scan vacuously green on an empty set.
     */
    private val minDiscoveredExpressions = 15

    /** The reveal drivers — a condition reading either is highlight-driven. */
    private val highlightDrivers = listOf("highlightSettingId", "lastClickedSettingId")

    /**
     * The whitelisted hand-shaped conditions, each with its reason. Keyed by
     * (file name, distinguishing fragment) so a whitelist entry survives
     * refactors of the surrounding line.
     */
    private val whitelisted: List<Pair<String, String>> = listOf(
        // The presets screen's highlight target is a screen-local sentinel id
        // (not a catalog row id), so no group declaration can carry it.
        "HomeLayoutPresetsScreen.kt" to "PRESET_LIST_HIGHLIGHT_ID",
        // The seerr screen's one reveal id (`integrations_seerr`) belongs to
        // the ROOT screen's `integrations` catalog group — the seerr card is
        // state-driven (`preferences.enabled || …`) and is not a catalog
        // screen-group, so the named-row equality is the derivation there.
        "SeerrSettingsScreen.kt" to "IntegrationsRows.SeerrSettings.id",
    )

    @Test
    fun everyHighlightDrivenExpansion_derivesFromAGroupDeclaration() {
        val root = repoRoot()
        val packageDir = root.resolve(featurePackageDir)
        assertTrue(packageDir.isDirectory, "missing $featurePackageDir — repo layout changed?")

        data class Site(val file: String, val expression: String)

        val sites = packageDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = stripComments(file.readText())
                val found = mutableListOf<Site>()
                var from = 0
                while (true) {
                    val idx = text.indexOf("initiallyExpanded =", from)
                    if (idx < 0) break
                    from = idx + "initiallyExpanded =".length
                    val expression = extractExpression(text, from)
                    if (highlightDrivers.any { it in expression }) {
                        found += Site(file.name, expression)
                    }
                }
                found
            }
            .toList()

        assertTrue(
            sites.size >= minDiscoveredExpressions,
            "only ${sites.size} highlight-driven expansion sites found (floor $minDiscoveredExpressions) — discovery rotted?",
        )

        val violations = sites.filter { (file, expression) ->
            "itemIdSet" !in expression && whitelisted.none { it.first == file && it.second in expression }
        }
        if (violations.isNotEmpty()) {
            fail(
                violations.joinToString("\n") { (file, expression) ->
                    "$file: hand-shaped highlight-driven expansion condition:\n    $expression\n" +
                        "  derive it from the group declaration instead — " +
                        "`initiallyExpanded = <driver> in SettingsScreenGroups.<group>.itemIdSet` — so rows " +
                        "added to the declaration cannot search-navigate into a collapsed group."
                },
            )
        }
    }

    /**
     * The expression from [from] up to the terminating `,` at bracket depth 0
     * (the argument-list terminator), newlines collapsed for the failure text.
     */
    private fun extractExpression(text: String, from: Int): String {
        var depth = 0
        for (i in from until text.length) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) return text.substring(from, i).replace(Regex("\\s+"), " ").trim()
            }
        }
        return text.substring(from).replace(Regex("\\s+"), " ").trim()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as sites. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
