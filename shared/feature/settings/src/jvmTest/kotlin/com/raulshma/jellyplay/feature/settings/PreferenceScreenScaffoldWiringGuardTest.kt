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
 * The declaration-path calls must ALSO forward the screen's deep-link
 * highlight id (`highlightSettingId =`): the shell's scroll derivation reads
 * its OWN argument, while the slot bodies read the screen's — a screen that
 * consumes the id in its bodies but forgets the shell arg renders a landing
 * that expands and glows NOTHING scrolled-to (the group-card landing
 * regression: the Audio "Fine-tuning" card expanded the Fine-tuning group
 * while the list stayed at the top; the screen was the one declaration-path
 * call missing the pass-through). The runtime `highlightSettingId` default
 * (`null`) makes the omission silent — Kotlin cannot see it; this scan is
 * the gate. The pass-through is judged among the call's TOP-LEVEL named
 * arguments only: a lambda-valued argument's body (a group builder handing
 * the id to its slot composables) nests below the call's own argument list
 * and never satisfies it ([highlightPassThrough_isJudgedAtTopLevelOnly]
 * pins both directions).
 *
 * The directions this guard cannot see: a `groups` list whose BODY drifted
 * from the rows it declares (row-grain is hand-written per ADR-0009), a
 * screen passing a DIFFERENT screen's list (wrong-instance wiring renders
 * fine), and a forwarded id that is not the screen's own parameter. The
 * contract tests pin the declarations themselves.
 */
class PreferenceScreenScaffoldWiringGuardTest {

    private val featurePackageDir =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings"

    /**
     * Floor: `PreferenceScreenScaffold(` call sites in the feature package
     * (the scan currently finds 33 — the definition site in
     * PreferenceScreenScaffold.kt is skipped; the floor stays just below so a
     * newly added screen does not trip it, while a discovery-rot regression
     * would otherwise make the wiring check vacuously green on an empty set).
     */
    private val minDiscoveredCalls = 32

    /**
     * The intended declaration-path call count: every screen handing the
     * scaffold its ordered group declaration — the appearance hub + its four
     * leaves, the playback hub + its eight leaves, the home hub + its four
     * leaves, Audio, Ambient, Backup, Integrations, JellyPlayPlugin, Language,
     * Notification, Performance, Security, Storage.
     */
    private val expectedDeclarationCalls = 29

    /** `groups =` inside the call's argument block (the declaration path). */
    private val groupsArg = Regex("""\bgroups\s*=""")

    /** `content =` inside the call's argument block (inline, named form). */
    private val contentArg = Regex("""\bcontent\s*=""")

    /**
     * `highlightSettingId =` among the call's TOP-LEVEL named arguments — the
     * declaration-path screens' mandatory shell pass-through (every settings
     * route is a [com.raulshma.jellyplay.core.ui.navigation.HighlightableRoute],
     * so every declaration-path screen takes the id and must hand it on).
     * Nested occurrences (inside a lambda-valued argument's group-building
     * body) do NOT count — [hasTopLevelNamedArg] does the depth filtering.
     */
    private val highlightArgName = "highlightSettingId"

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
            problems += callProblems(call.file, call.args, call.trailingLambda)
        }

        // A group declaration a screen builds but never hands over is the
        // regression's exact shape — count the declaration half too, so a
        // screen silently dropping off it fails even if some OTHER call site
        // drifts first and masks the per-call verdict.
        val declarationCalls = calls.filter { groupsArg.containsMatchIn(it.args) }
        if (declarationCalls.size != expectedDeclarationCalls) {
            problems +=
                "  - expected $expectedDeclarationCalls declaration-path calls (the appearance hub + its four leaves, " +
                    "the playback hub + its eight leaves, the home hub + its four leaves, Audio, " +
                    "Ambient, Backup, Integrations, JellyPlayPlugin, Language, Notification, " +
                    "Performance, Security, Storage) but found " +
                    "${declarationCalls.size} (${declarationCalls.map { it.file }}) — a screen stopped " +
                    "passing its groups?"
        }

        if (problems.isNotEmpty()) {
            fail(
                "PreferenceScreenScaffold call sites are not wired one-content-path each:\n" +
                    problems.joinToString("\n")
            )
        }
    }

    @Test
    fun highlightPassThrough_isJudgedAtTopLevelOnly() {
        // A group-building lambda INSIDE the scaffold's argument list may
        // forward the id to its slot composables — that nested forward is
        // NOT the shell pass-through, whose runtime default (`null`) makes
        // the real omission silent. The id appearing only inside the nested
        // body must fail the guard…
        val nestedOnly = """
            title = "Probe",
            groups = listOf(
                SettingsScreenGroups.systemScreensaver.asScreenGroup { activePicker ->
                    SettingsScreensaverSection(
                        highlightSettingId = highlightSettingId,
                        activeDialog = activePicker,
                    )
                },
            ),
            pickerHost = true,
        """.trimIndent()
        assertTrue(
            callProblems("NestedOnly.kt", nestedOnly, trailingLambda = false)
                .any { highlightArgName in it },
            "an id present only inside the group-building body must NOT count as " +
                "the scaffold's highlightSettingId pass-through",
        )

        // …while forwarding the id as the scaffold's own named argument —
        // beside the same nested body — passes cleanly.
        val forwarded = "$nestedOnly,\n        highlightSettingId = highlightSettingId,"
        assertTrue(
            callProblems("Forwarded.kt", forwarded, trailingLambda = false).isEmpty(),
            "the top-level highlightSettingId pass-through must satisfy the guard",
        )
    }

    // ------------------------------------------------------------ plumbing

    /**
     * The wiring verdicts for ONE discovered scaffold call — the tree scan
     * and the fixture regressions in
     * [highlightPassThrough_isJudgedAtTopLevelOnly] share it, so the pins
     * exercise the exact logic the scan applies.
     */
    private fun callProblems(file: String, args: String, trailingLambda: Boolean): List<String> =
        buildList {
            val paths = listOf(
                groupsArg.containsMatchIn(args),
                contentArg.containsMatchIn(args),
                trailingLambda,
            )
            when (paths.count { it }) {
                0 -> add(
                    "  - $file: the call passes NEITHER groups nor content — the " +
                        "LazyColumn renders empty (blank screen, no crash; compile gates " +
                        "are blind to it). Pass the screen's group declaration or the " +
                        "inline content block."
                )
                1 -> {}
                else -> add(
                    "  - $file: the call passes BOTH content paths " +
                        "(groups + content/trailing lambda) — the paths are exclusive; " +
                        "the runtime check would throw at first composition."
                )
            }
            // The declaration path derives the deep-link scroll from the shell's
            // OWN highlightSettingId argument — a screen that consumes the id
            // in its slot bodies but forgets this pass-through lands expanded
            // and glowing at the top of the screen (the group-card regression).
            if (groupsArg.containsMatchIn(args) && !hasTopLevelNamedArg(args, highlightArgName)) {
                add(
                    "  - $file: a declaration-path call that does NOT forward " +
                        "highlightSettingId to the shell — deep-link landings expand and " +
                        "glow but never scroll (the group-card landing regression's exact " +
                        "shape; the runtime null default makes it silent). Pass the " +
                        "screen's highlightSettingId beside its groups."
                )
            }
        }

    /**
     * True when [args] names [argName] as a TOP-LEVEL named argument — at
     * bracket depth 0 relative to the call's own argument list. A
     * lambda-valued argument's body (a group builder forwarding the id into
     * its slot composables) nests below depth 0 and must NOT count: the
     * shell's scroll derivation reads the scaffold's OWN argument, so a
     * nested forward alone is exactly the omission this check exists to
     * catch.
     */
    private fun hasTopLevelNamedArg(args: String, argName: String): Boolean {
        val pattern = Regex("""\b${Regex.escape(argName)}\s*=""")
        // depthAt[i] = the bracket depth BEFORE args[i] — a match starting
        // where depth is 0 sits at the call's own argument level.
        val depthAt = IntArray(args.length)
        var depth = 0
        for (i in args.indices) {
            depthAt[i] = depth
            when (args[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
            }
        }
        return pattern.findAll(args).any { depthAt[it.range.first] == 0 }
    }

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
