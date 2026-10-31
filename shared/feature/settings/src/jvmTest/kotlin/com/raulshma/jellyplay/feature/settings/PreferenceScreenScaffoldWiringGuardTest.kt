package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * PreferenceScreenScaffold wiring totality guard (source-scan on plain text,
 * no PSI — deliberately cheap, but executable, same shape as
 * [SettingsKoinModuleViewModelTotalityGuardTest] and the worker
 * registration guard in :core:data).
 *
 * The scaffold's two content paths are exclusive AND exhaustive — a call must
 * pass exactly ONE of:
 *  - `groups =` (the declaration path: ordered [PreferenceScreenGroup] slots),
 *  - `content =` or a trailing lambda (the inline path: the hand-written
 *    LazyListScope block).
 * Both paths at once is the illegal state the runtime `check` throws on;
 * NEITHER path renders an empty LazyColumn — top-bar chrome over a blank
 * body, no crash, and Kotlin only *warns* on the declaration list a screen
 * built and forgot to hand over. Compile gates are blind to it; this scan is
 * the gate.
 *
 * The neither-path leg is exactly the v0.11.6 regression: 82830c432 built
 * AudioSettingsScreen's `audioGroups` declaration (both slots, every row
 * body) and never passed it — the audio screen shipped blank while every
 * sibling screen passed its `groups`. (The scaffold check's inverted
 * predicate from the same build is pinned separately by
 * [PreferenceScreenPathsExclusiveTest].)
 *
 * Both directions this guard cannot see: a `groups` list whose BODY drifted
 * from the rows it declares (row-grain is hand-written per ADR-0009) and a
 * screen passing a DIFFERENT screen's list (wrong-instance wiring renders
 * fine). The contract tests pin the declarations themselves.
 */
class PreferenceScreenScaffoldWiringGuardTest {

    private val featurePackageDir =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings"

    /**
     * Floor: `PreferenceScreenScaffold(` call sites in the feature package
     * (currently 13 screens — the definition site in
     * PreferenceScreenScaffold.kt is skipped). A discovery-rot regression
     * would otherwise make the wiring check vacuously green on an empty set.
     */
    private val minDiscoveredCalls = 13

    /** `groups =` inside the call's argument block (the declaration path). */
    private val groupsArg = Regex("""\bgroups\s*=""")

    /** `content =` inside the call's argument block (inline, named form). */
    private val contentArg = Regex("""\bcontent\s*=""")

    @Test
    fun everyScaffoldCall_passesExactlyOneContentPath() {
        val root = repoRoot()
        val packageDir = root.resolve(featurePackageDir)
        assertTrue(packageDir.isDirectory, "missing $featurePackageDir — repo layout changed?")

        data class Call(val file: String, val args: String, val trailingLambda: Boolean)

        val calls = packageDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = stripComments(file.readText())
                var from = 0
                val found = mutableListOf<Call>()
                while (true) {
                    val idx = text.indexOf("PreferenceScreenScaffold(", from)
                    if (idx < 0) break
                    from = idx + "PreferenceScreenScaffold(".length
                    // Skip the definition site (fun PreferenceScreenScaffold()).
                    val before = text.substring(maxOf(0, idx - 4), idx)
                    if (before.endsWith("fun ")) continue
                    // The argument block: balanced parens from the call's '('.
                    var depth = 0
                    var close = -1
                    for (i in from - 1 until text.length) {
                        when (text[i]) {
                            '(' -> depth++
                            ')' -> {
                                depth--
                                if (depth == 0) {
                                    close = i
                                    break
                                }
                            }
                        }
                    }
                    if (close < 0) {
                        fail("${file.name}: unbalanced parens after a PreferenceScreenScaffold( call")
                    }
                    // Inline content also arrives as a trailing lambda after
                    // the argument block (`) { _ -> ... }`).
                    var after = close + 1
                    while (after < text.length && text[after].isWhitespace()) after++
                    val trailing = after < text.length && text[after] == '{'
                    found += Call(file.name, text.substring(from, close), trailing)
                }
                found
            }
            .toList()

        assertTrue(
            calls.size >= minDiscoveredCalls,
            "discovered only ${calls.size} PreferenceScreenScaffold call sites under " +
                "$featurePackageDir, expected >= $minDiscoveredCalls — discovery is " +
                "broken (moved package? renamed entry point?), fix the scan in " +
                javaClass.simpleName,
        )

        val problems = mutableListOf<String>()
        for (call in calls) {
            val paths = listOf(
                groupsArg.containsMatchIn(call.args),
                contentArg.containsMatchIn(call.args),
                call.trailingLambda,
            )
            when (paths.count { it }) {
                0 -> problems +=
                    "  - ${call.file}: the call passes NEITHER groups nor content — the " +
                        "LazyColumn renders empty (blank screen, no crash; compile gates " +
                        "are blind to it). Pass the screen's group declaration or the " +
                        "inline content block."
                1 -> {}
                else -> problems +=
                    "  - ${call.file}: the call passes BOTH content paths " +
                        "(groups + content/trailing lambda) — the paths are exclusive; " +
                        "the runtime check would throw at first composition."
            }
        }

        // A group declaration a screen builds but never hands over is the
        // regression's exact shape — count the declaration half too, so a
        // screen silently dropping off it fails even if some OTHER call site
        // drifts first and masks the per-call verdict.
        val declarationCalls = calls.filter { groupsArg.containsMatchIn(it.args) }
        if (declarationCalls.size != 7) {
            problems +=
                "  - expected 7 declaration-path calls (Appearance, Audio, Home, " +
                    "Language, Playback, Security, Storage) but found ${declarationCalls.size} " +
                    "(${declarationCalls.map { it.file }}) — a screen stopped passing its groups?"
        }

        if (problems.isNotEmpty()) {
            fail(
                "PreferenceScreenScaffold call sites are not wired one-content-path each:\n" +
                    problems.joinToString("\n")
            )
        }
    }

    // ------------------------------------------------------------ plumbing

    /** Walks up from the test working dir (shared/feature/settings) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as calls. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
