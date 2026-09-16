package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Koin-registration ratchet guard (docs/kmp-migration-plan.md §, the
 * arrqueue + subtitle-tester lessons, plan lines ~1402-1408).
 *
 * Koin module registration is runtime-only wiring: compile gates and
 * hiltJavaCompile are BLIND to it. Twice a shared feature module shipped
 * without being added to a startKoin module list — arrqueue (app-side
 * missing, caught post-merge) and shortcuts (app-side missing;
 * `Route.Shortcuts` navigation would NoDefinitionFound-crash on first open).
 * Neither broke any compile step.
 *
 * This guard auto-derives the expected list instead of hardcoding it: every
 * top-level `val <name>: Module = module {` declared in a shared feature's
 * commonMain MUST be registered in BOTH startKoin blocks (Android
 * JellyPlayApplication.kt and desktop Main.kt), and every `<identifier>`
 * Module` referenced in either block must resolve to a shared feature or
 * shared core definition (rename/typo guard). New features are picked up
 * automatically; forgetting the one-line registration fails this test with
 * the exact fix.
 *
 * apps/web/Main.kt is covered per-site: the web shell registers only the
 * slice of the feature graph that has a wasmJs target — requestsModule first,
 * more as features land web targets. Its web check DERIVES the expected set
 * from that file's own `val webFeatureModules = listOf(...)` declaration —
 * the same list the startKoin block consumes via a spread, so there is no
 * hand-kept test-side copy to drift — with set-equality in BOTH directions
 * between the declaration and the feature modules the file actually names:
 * a feature module registered/imported on web but absent from the
 * declaration fails (conscious Main.kt edit required), a declared module
 * that no longer resolves fails (rename/typo), and the startKoin block must
 * consume the declaration rather than a divergent hand-copied list. Desktop
 * forward check + floor stay untouched.
 *
 * Source-scanning on plain text (no PSI) — deliberately cheap, but executable.
 */
class KoinModuleRegistrationGuardTest {

    /** The two startKoin registration files this ratchet covers (forward + reverse checks). */
    private val registrationFiles = listOf(
        "Android app" to "app/src/main/java/com/raulshma/jellyplay/JellyPlayApplication.kt",
        "Desktop app" to "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/Main.kt",
    )

    /**
     * Registration files whose forward check derives from the file's own
     * `val <name> = listOf(...)` feature-module declaration instead of the
     * full feature graph. The reverse (typo/rename) check still applies to
     * them in full.
     */
    private val forwardAllowlistedRegistrationFiles = listOf(
        "Web app" to "apps/web/src/wasmJsMain/kotlin/com/raulshma/jellyplay/web/Main.kt",
    )

    /** The declaration identifier the web check derives the expected set from. */
    private val webFeatureModulesDeclaration = "webFeatureModules"

    /**
     * Module-variant prefixes that are intentionally platform-/core-scoped:
     * each startKoin site registers its own platform actuals plus the shared
     * core graph, so these names are not required to come from a feature's
     * commonMain (e.g. androidDataModule, desktopPlayerModule,
     * desktopMusicMessageBusModule, datastoreCommonModule, networkJvmModule…).
     */
    private val platformPrefixes = listOf(
        "android", "desktop", "web",
        "datastore", "database", "network", "data",
    )

    /** Koin's own modules (koin-core), not ours to place under shared/. */
    private val koinOwnModules = setOf("defaultModule", "loggerModule")

    /**
     * Ratchet floor: the count of commonMain feature modules, re-measured at
     * each housekeeping pass. 21 as of the re-count (23 features;
     * subtitle-tester contributes none — androidMain-only — and player-video's
     * defs live in its platform modules androidPlayerVideoModule/
     * desktopPlayerVideoModule, so its commonMain declares no Module val).
     * Bump when features land. A discovery-rot regression (regex stops
     * matching, dirs move) would otherwise make the forward check vacuously
     * green on an empty list.
     */
    private val minFeatureModuleCount = 21

    @Test
    fun everyCommonMainFeatureModule_isRegisteredInBothStartKoinBlocks() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)
        assertTrue(
            features.size >= minFeatureModuleCount,
            "discovered only ${features.size} commonMain feature modules " +
                "(${features.keys.sorted()}), expected >= $minFeatureModuleCount — " +
                "discovery is broken (moved dirs? new declaration style?), fix the scan in " +
                javaClass.simpleName,
        )

        val missing = buildList {
            for ((site, path) in registrationFiles) {
                val block = startKoinModulesBlock(root.resolve(path))
                for ((name, declaring) in features) {
                    if (!Regex("\\b$name\\b").containsMatchIn(block)) {
                        add(
                            "'$name' (declared at ${relative(root, declaring)}) is NOT registered " +
                                "in $site's $path",
                        )
                    }
                }
            }
        }
        if (missing.isNotEmpty()) {
            fail(
                "Shared feature Koin modules missing from a startKoin registration — " +
                    "compile gates are BLIND to this; koinViewModel would throw " +
                    "NoDefinitionFound at runtime on first navigation:\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\nFix: add one line `<module>,` (plus its import) inside the modules(...) " +
                    "block of that file's startKoin.",
            )
        }
    }

    /**
     * Web-site forward ratchet, derived (set-equality, both directions):
     *  - the web Main.kt must declare `val webFeatureModules = listOf(…)`
     *    and its startKoin block must consume that declaration (a spread —
     *    no divergent hand-copied module list);
     *  - every declared module MUST resolve to a discovered shared feature
     *    module (the arrqueue/shortcuts rename/typo lesson);
     *  - every feature module named in the file (declaration, startKoin
     *    block, import) MUST be declared — a web registration for a new
     *    feature is a conscious Main.kt list edit, never silent.
     */
    @Test
    fun webStartKoin_matchesItsDeclaredFeatureModulesExactly() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)
        val (site, path) = forwardAllowlistedRegistrationFiles.single()
        val file = root.resolve(path)
        val text = stripComments(file.readText())

        val declared = declaredFeatureModuleList(text, webFeatureModulesDeclaration).toSet()
        assertTrue(
            declared.isNotEmpty(),
            "$site's `$webFeatureModulesDeclaration` declaration is empty — web registers no " +
                "feature modules anymore? Restore the list.",
        )

        val stale = declared.filter { it !in features.keys }
        assertTrue(
            stale.isEmpty(),
            "$site's `$webFeatureModulesDeclaration` names module(s) no longer declared in any " +
                "shared feature commonMain/jvmShared: $stale — rename or removal? Update " +
                "webFeatureModules in ${file.name}.",
        )

        val block = startKoinModulesBlock(file)
        assertTrue(
            block.contains(webFeatureModulesDeclaration),
            "$site's startKoin must register its feature modules via " +
                "`*$webFeatureModulesDeclaration` (the declaration this test derives from), " +
                "not a hand-copied module list.",
        )

        val named = features.keys.filter { name ->
            Regex("\\b$name\\b").containsMatchIn(text)
        }.toSet()
        val undeclared = named - declared
        if (undeclared.isNotEmpty()) {
            fail(
                "$site names feature module(s) its `$webFeatureModulesDeclaration` declaration " +
                    "does not list (expected exactly $declared):\n" +
                    undeclared.joinToString("\n") {
                        "  - '$it' registered/imported in $path but NOT in " +
                            "$webFeatureModulesDeclaration (feature module — add it to the " +
                            "declaration in the same commit)"
                } +
                    "\nFix: keep ${file.name}'s modules(...) consuming " +
                    "`*$webFeatureModulesDeclaration` and grow the declaration itself.",
            )
        }
    }

    @Test
    fun everyNonPlatformModuleInStartKoinBlocks_isDefinedInSharedSources() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)
        val core = discoverCoreModules(root)
        val known = features.keys + core.keys + koinOwnModules

        val unknown = buildList {
            for ((site, path) in registrationFiles + forwardAllowlistedRegistrationFiles) {
                val block = startKoinModulesBlock(root.resolve(path))
                for (identifier in Regex("\\b[A-Za-z]\\w*Module\\b").findAll(block).map { it.value }) {
                    val skipped = platformPrefixes.any { identifier.startsWith(it) }
                    if (!skipped && identifier !in known) {
                        add("$identifier in $site's $path")
                    }
                }
            }
        }
        if (unknown.isNotEmpty()) {
            fail(
                "startKoin references module(s) that exist neither in a shared feature's " +
                    "commonMain (${features.keys.sorted()}) nor in shared/core " +
                    "(${core.keys.sorted()}) — typo or stale rename?:\n" +
                unknown.joinToString("\n") { "  - $it" } +
                    "\nFix: correct the identifier to a discovered module name, or define it as " +
                    "`val <name>: Module = module { … }` under shared/feature/<feature>/src/" +
                    "commonMain (feature) or shared/core/<module>/src (core).",
            )
        }
    }

    // ------------------------------------------------------------------ repo

    /** Walks up from the test working dir (may be apps/desktop/) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    // ------------------------------------------------------------ discovery

    /** `val <name>: Module = module {` / `val <name> = module {`, top-level style. */
    private val moduleDeclaration = Regex(
        """(?m)^[ \t]*(?:internal[ \t]+|private[ \t]+|public[ \t]+)?val[ \t]+(\w+)[ \t]*(?::[ \t]*Module[ \t]*)?=[\s]{0,8}module[ \t]*[<{(]""",
    )

    /**
     * Every Koin Module declared in any shared feature's commonMain OR
     * jvmShared, mapped to its declaring file. jvmShared counts too —
     * a feature whose whole module surface is JVM-only (insights moved
     * its Kotlin there for the wasm split) is still legitimately registered
     * by both shells this test guards. subtitle-tester (androidMain-only)
     * contributes nothing here and is naturally excluded.
     */
    private fun discoverFeatureModules(root: File): Map<String, File> {
        val featuresDir = root.resolve("shared/feature")
        assertTrue(featuresDir.isDirectory, "missing $featuresDir — repo layout changed?")
        val featureDirs = featuresDir.listFiles { f -> f.isDirectory }.orEmpty()
        return scanModuleDeclarations(
            featureDirs.flatMap { feature ->
                listOf("commonMain", "jvmShared").map { feature.resolve("src/$it") }
            }.filter { it.isDirectory },
        )
    }

    /** Every Koin Module val (all source sets) across shared/core — the shared core graph. */
    private fun discoverCoreModules(root: File): Map<String, File> {
        val coreDir = root.resolve("shared/core")
        assertTrue(coreDir.isDirectory, "missing $coreDir — repo layout changed?")
        return scanModuleDeclarations(
            coreDir.listFiles { f -> f.isDirectory }.orEmpty()
                .map { it.resolve("src") }
                .filter { it.isDirectory },
        )
    }

    private fun scanModuleDeclarations(roots: List<File>): Map<String, File> =
        buildMap {
            for (dir in roots) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        moduleDeclaration.findAll(file.readText()).forEach { match ->
                            put(match.groupValues[1], file)
                        }
                    }
            }
        }

    // ------------------------------------------------------- startKoin block

    /**
     * The text inside `modules( … )` of the file's startKoin block, with
     * comments stripped (comment parens must not break the balance scan).
     * Imports are deliberately outside the block — a module must be listed,
     * not merely imported.
     */
    private fun startKoinModulesBlock(file: File): String {
        assertTrue(file.isFile, "registration file ${file.path} does not exist")
        val text = stripComments(file.readText())
        val startKoin = text.indexOf("startKoin")
        assertTrue(startKoin >= 0, "${file.path}: no startKoin block")
        val modules = text.indexOf("modules(", startKoin)
        assertTrue(modules >= 0, "${file.path}: no modules(...) call in startKoin")

        val openParen = modules + "modules".length
        var depth = 0
        for (i in openParen until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(openParen + 1, i)
                }
            }
        }
        fail("${file.path}: unbalanced parentheses in modules(...)")
    }

    /**
     * Parses `val <name> = listOf(<module>, …)` out of already-stripped
     * source text — the declaration a forward-allowlisted site's startKoin
     * consumes, and the single place this test derives that site's expected
     * feature-module set from.
     */
    private fun declaredFeatureModuleList(text: String, declarationName: String): List<String> {
        val declarationStart = text.indexOf("val $declarationName")
        assertTrue(
            declarationStart >= 0,
            "expected a `val $declarationName = listOf(...)` declaration — the derivation " +
                "source for this site's feature-module set is gone",
        )
        val listOfStart = text.indexOf("listOf(", declarationStart)
        assertTrue(listOfStart >= 0, "$declarationName must be declared as a listOf(...) literal")
        val openParen = listOfStart + "listOf".length
        var depth = 0
        for (i in openParen until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return Regex("\\b(\\w+Module)\\b")
                            .findAll(text.substring(openParen + 1, i))
                            .map { it.groupValues[1] }
                            .toList()
                    }
                }
            }
        }
        fail("$declarationName declaration has unbalanced parentheses")
    }

    private fun stripComments(text: String): String {
        // Kotlin block comments NEST (/* /* */ */) — loop until stable so an
        // inner close marker can't leave stray "*/" text behind.
        var stripped = text
        while (true) {
            val next = stripped.replace(
                Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL),
                "",
            )
            if (next == stripped) break
            stripped = next
        }
        return stripped.replace(Regex("//[^\n]*"), "")
    }

    private fun relative(root: File, file: File): String =
        file.toRelativeString(root).replace('\\', '/')
}
