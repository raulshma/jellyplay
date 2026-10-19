package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Koin-registration totality guard for the settings feature's ViewModels
 * (source-scan on plain text, no PSI — deliberately cheap, but executable,
 * same shape as [SettingsSectionKeysGuardTest] and the desktop
 * KoinModuleRegistrationGuardTest).
 *
 * The settingsModule's ViewModel registrations are runtime-only wiring that
 * compile gates are blind to: a ViewModel class with NO registration crashes
 * `koinViewModel()` with NoDefinitionFound on first navigation, and one with
 * TWO registrations makes the definition ambiguous (Koin resolves the first
 * match and silently ignores the other). Hand-written `viewModel { … }` blocks
 * and `viewModelOf(::…)` one-liners are equally legitimate — this guard reads
 * BOTH, so a registration-style change can't regress the count.
 *
 * Derivation, both directions:
 *  - every concrete `class XViewModel(…)` declared in the feature's commonMain
 *    settings package must have EXACTLY ONE registration in
 *    SettingsKoinModule.kt (a `viewModelOf(::XViewModel)` one-liner or a
 *    `XViewModel(` call inside a `viewModel { … }` block);
 *  - every registration in the module must target a discovered ViewModel
 *    class (a rename that orphaned a registration fails here).
 *
 * RESIDUAL RISK this scan cannot catch: platform-specific RESOLUTION failure —
 * the wiring may be total in source while a constructor parameter's binding is
 * absent on one platform (an optional seam like the audio-device enumerator is
 * Android-absent by design), which only explodes at first resolution on that
 * platform. A source scan sees neither graph. The desktop
 * KoinModuleRegistrationGuardTest (apps/desktop) is untouched and independent:
 * it guards the shell-side module-list registration, not this file's defs.
 */
class SettingsKoinModuleViewModelTotalityGuardTest {

    private val featurePackageDir =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings"

    private val moduleFile =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/di/SettingsKoinModule.kt"

    /**
     * Floor: concrete `*ViewModel` classes in the feature package (currently
     * 28). A discovery-rot regression would otherwise make the totality check
     * vacuously green on an empty set.
     */
    private val minDiscoveredViewModels = 28

    /** `class XViewModel(` / `abstract class XViewModel(` — top-level style. */
    private val classDeclaration = Regex(
        """(?m)^\s*(?:internal\s+|private\s+|public\s+)?(abstract\s+)?class\s+(\w+ViewModel)\s*\(""",
    )

    /** `viewModelOf(::XViewModel)` — the converted one-liner registration. */
    private val viewModelOfRegistration = Regex("""viewModelOf\s*\(\s*::\s*(\w+)\s*\)""")

    @Test
    fun everyFeatureViewModel_hasExactlyOneRegistrationInTheKoinModule() {
        val root = repoRoot()
        val packageDir = root.resolve(featurePackageDir)
        assertTrue(packageDir.isDirectory, "missing $featurePackageDir — repo layout changed?")

        val discovered = packageDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { classDeclaration.findAll(stripComments(it.readText())) }
            .filter { it.groupValues[1].isEmpty() } // abstract bases are never registered
            .map { it.groupValues[2] }
            .toSet()
        assertTrue(
            discovered.size >= minDiscoveredViewModels,
            "discovered only ${discovered.size} concrete ViewModel classes under " +
                "$featurePackageDir, expected >= $minDiscoveredViewModels — discovery is " +
                "broken (moved package? renamed declarations?), fix the scan in " +
                javaClass.simpleName,
        )

        val moduleText = stripComments(root.resolve(moduleFile).readText())

        // The hand-written half of the registrations: every `viewModel { … }`
        // block's body (balanced braces, comments stripped).
        val handWrittenBlocks = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val block = moduleText.indexOf("viewModel {", searchFrom)
            if (block < 0) break
            val openBrace = block + "viewModel".length
            var depth = 0
            var close: Int? = null
            for (i in openBrace until moduleText.length) {
                when (moduleText[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            close = i
                            break
                        }
                    }
                }
            }
            assertTrue(close != null, "$moduleFile: unbalanced braces after a viewModel { block")
            handWrittenBlocks += moduleText.substring(openBrace + 1, close)
            searchFrom = close + 1
        }

        val problems = mutableListOf<String>()
        for (name in discovered) {
            val ofCount = Regex("""viewModelOf\s*\(\s*::\s*$name\s*\)""").findAll(moduleText).count()
            val handCount = handWrittenBlocks.sumOf { Regex("""\b$name\s*\(""").findAll(it).count() }
            val total = ofCount + handCount
            if (total != 1) {
                problems += when {
                    total == 0 ->
                        "  - '$name' has NO registration in SettingsKoinModule.kt — " +
                            "koinViewModel() would NoDefinitionFound-crash on first " +
                            "navigation (compile gates are blind to this)"
                    else ->
                        "  - '$name' has $total registrations ($ofCount viewModelOf + " +
                            "$handCount hand-written) — " +
                            "keep exactly one (a duplicate makes the definition ambiguous)"
                }
            }
        }

        // Reverse direction: every registration targets a discovered ViewModel
        // class (a rename that orphaned a registration, or a def registered
        // from outside the feature, fails here).
        val registered = buildList {
            addAll(viewModelOfRegistration.findAll(moduleText).map { it.groupValues[1] })
            handWrittenBlocks.forEach { block ->
                Regex("""\b(\w+ViewModel)\s*\(""").findAll(block).forEach { add(it.groupValues[1]) }
            }
        }
        val orphaned = registered.filter { it !in discovered }
        if (orphaned.isNotEmpty()) {
            problems += orphaned.map {
                "  - registration for '$it' matches no ViewModel class declared under " +
                    "$featurePackageDir — renamed or removed? Fix or drop the def."
            }
        }
        assertTrue(
            registered.size == registered.toSet().size,
            "duplicate registration names: ${
                registered.groupBy { it }.filterValues { it.size > 1 }.keys
            }",
        )

        if (problems.isNotEmpty()) {
            fail(
                "SettingsKoinModule.kt ViewModel registrations are not total (exactly one " +
                    "per feature ViewModel, all resolving):\n" +
                    problems.joinToString("\n")
            )
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

    /** Drops line and block comments so doc mentions don't count as registrations. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
