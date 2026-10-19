package com.raulshma.jellyplay.di

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Koin-registration totality guard for the app module's shell ViewModels —
 * the :app twin of the settings feature's
 * SettingsKoinModuleViewModelTotalityGuardTest (source-scan on plain text,
 * no PSI — deliberately cheap, but executable; the desktop
 * KoinModuleRegistrationGuardTest guards the shell-side module LIST, not
 * defs, and stays independent).
 *
 * androidAppViewModelsModule's ViewModel registrations are runtime-only
 * wiring that compile gates are blind to: a ViewModel class with NO
 * registration crashes `koinViewModel()`/KoinViewModelFactory with
 * NoDefinitionFound on first resolution, and one with TWO registrations
 * makes the definition ambiguous (Koin resolves the first match and
 * silently ignores the other). All three defs are `viewModelOf(::…)`
 * one-liners since the totality conversion (every ctor param is a plain
 * Koin-bound type); hand-written `viewModel { … }` blocks are still read so
 * a future seam-driven def can't regress the count.
 *
 * Derivation, both directions:
 *  - every concrete `class XViewModel(…)` declared anywhere under app's
 *    main source set must have EXACTLY ONE registration in AppKoinModule.kt;
 *  - every registration in the module must target a discovered ViewModel
 *    class (a rename that orphaned a registration fails here).
 *
 * RESIDUAL RISK this scan cannot catch: platform-specific RESOLUTION
 * failure — the wiring may be total in source while a constructor
 * parameter's binding is absent at startKoin time, which only explodes at
 * first resolution. A source scan sees neither graph.
 */
class AppKoinModuleViewModelTotalityGuardTest {

    private val appMainDir = "app/src/main/java"

    private val moduleFile =
        "app/src/main/java/com/raulshma/jellyplay/di/AppKoinModule.kt"

    /**
     * Ratchet floor: concrete `*ViewModel` classes under app's main source
     * set (3 as of the viewModelOf conversion: MainViewModel, PlayOnViewModel,
     * WidgetConfigViewModel — JellyPlayViewModel, the abstract base, lives in
     * core:ui and is never discovered here). A discovery-rot regression would
     * otherwise make the totality check vacuously green on an empty set.
     */
    private val minDiscoveredViewModels = 3

    /** `class XViewModel(` / `abstract class XViewModel(` — top-level style. */
    private val classDeclaration = Regex(
        """(?m)^\s*(?:internal\s+|private\s+|public\s+)?(abstract\s+)?class\s+(\w+ViewModel)\s*\(""",
    )

    /** `viewModelOf(::XViewModel)` — the one-liner registration. */
    private val viewModelOfRegistration = Regex("""viewModelOf\s*\(\s*::\s*(\w+)\s*\)""")

    @Test
    fun everyAppViewModel_hasExactlyOneRegistrationInTheKoinModule() {
        val root = repoRoot()
        val mainDir = root.resolve(appMainDir)
        assertTrue("missing $appMainDir — repo layout changed?", mainDir.isDirectory)

        val discovered = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { classDeclaration.findAll(stripComments(it.readText())) }
            .filter { it.groupValues[1].isEmpty() } // abstract bases are never registered
            .map { it.groupValues[2] }
            .toSet()
        assertTrue(
            "discovered only ${discovered.size} concrete ViewModel classes under " +
                "$appMainDir, expected >= $minDiscoveredViewModels — discovery is " +
                "broken (moved package? renamed declarations?), fix the scan in " +
                javaClass.simpleName,
            discovered.size >= minDiscoveredViewModels,
        )

        val moduleText = stripComments(root.resolve(moduleFile).readText())

        // The hand-written half of the registrations: every `viewModel { … }`
        // block's body (balanced braces, comments stripped). Empty today —
        // the module is all viewModelOf one-liners — but scanned so a future
        // seam-driven def (getOrNull / function-typed default, the two
        // reasons SettingsKoinModule keeps hand-written halves) stays counted.
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
            val blockEnd = requireNotNull(close) { "$moduleFile: unbalanced braces after a viewModel { block" }
            handWrittenBlocks += moduleText.substring(openBrace + 1, blockEnd)
            searchFrom = blockEnd + 1
        }

        val problems = mutableListOf<String>()
        for (name in discovered) {
            val ofCount = Regex("""viewModelOf\s*\(\s*::\s*$name\s*\)""").findAll(moduleText).count()
            val handCount = handWrittenBlocks.sumOf { Regex("""\b$name\s*\(""").findAll(it).count() }
            val total = ofCount + handCount
            if (total != 1) {
                problems += when {
                    total == 0 ->
                        "  - '$name' has NO registration in AppKoinModule.kt — " +
                            "koinViewModel()/KoinViewModelFactory would NoDefinitionFound-" +
                            "crash on first resolution (compile gates are blind to this)"
                    else ->
                        "  - '$name' has $total registrations ($ofCount viewModelOf + " +
                            "$handCount hand-written) — " +
                            "keep exactly one (a duplicate makes the definition ambiguous)"
                }
            }
        }

        // Reverse direction: every registration targets a discovered ViewModel
        // class (a rename that orphaned a registration fails here).
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
                    "$appMainDir — renamed or removed? Fix or drop the def."
            }
        }
        assertTrue(
            "duplicate registration names: ${
                registered.groupBy { it }.filterValues { it.size > 1 }.keys
            }",
            registered.size == registered.toSet().size,
        )

        if (problems.isNotEmpty()) {
            fail(
                "AppKoinModule.kt ViewModel registrations are not total (exactly one " +
                    "per app-module ViewModel, all resolving):\n" +
                    problems.joinToString("\n")
            )
        }
    }

    // ------------------------------------------------------------ plumbing

    /** Walks up from the test working dir (app/) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as registrations. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
