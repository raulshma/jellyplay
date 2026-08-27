package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Koin-registration ratchet guard (docs/kmp-migration-plan.md §Phase X, the
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
 * apps/web/Main.kt is covered per-site (wave 15C, the move the KDoc below
 * anticipated): the web shell registers only the slice of the feature graph
 * that has a wasmJs target — requestsModule since wave 15C, more as features
 * land web targets — so its forward check runs against an explicit
 * ALLOWLIST ([webForwardAllowlist]) with set-equality in BOTH directions:
 * an allowlisted module missing from the web startKoin fails, and a feature
 * module registered on web but absent from the allowlist fails too, so the
 * allowlist can only grow by a conscious test edit (the same ratchet spirit
 * as the desktop floor below). Desktop forward check + floor stay untouched.
 *
 * Source-scanning on plain text (no PSI) — deliberately cheap, like the
 * plan's "grep both registration files per feature" audit, but executable.
 */
class KoinModuleRegistrationGuardTest {

    /** The two startKoin registration files this ratchet covers (forward + reverse checks). */
    private val registrationFiles = listOf(
        "Android app" to "app/src/main/java/com/raulshma/jellyplay/JellyPlayApplication.kt",
        "Desktop app" to "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/Main.kt",
    )

    /**
     * Registration files whose forward check runs against a per-site
     * allowlist instead of the full feature graph. The reverse (typo/rename)
     * check still applies to them in full.
     */
    private val forwardAllowlistedRegistrationFiles = listOf(
        "Web app" to "apps/web/src/wasmJsMain/kotlin/com/raulshma/jellyplay/web/Main.kt",
    )

    /**
     * The web shell's forward allowlist: every shared feature module the web
     * startKoin is EXPECTED to register, exactly. apps/web depends on two
     * features today ([requestsModule] — wave 15C put Route.Requests on the
     * browser; [calendarModule] — wave 16A put Route.UpcomingCalendar there);
     * when the next feature gains a wasmJs target and a web nav entry,
     * register it in Main.kt AND add it here in the same commit.
     */
    private val webForwardAllowlist = setOf("calendarModule", "requestsModule")

    /**
     * Module-variant prefixes that are intentionally platform-/core-scoped:
     * each startKoin site registers its own platform actuals plus the shared
     * core graph, so these names are not required to come from a feature's
     * commonMain (e.g. androidDataModule, desktopPlayerModule,
     * hiltInteropModule, datastoreCommonModule, networkJvmModule…).
     */
    private val platformPrefixes = listOf(
        "android", "desktop", "web", "hilt",
        "datastore", "database", "network", "data",
    )

    /** Koin's own modules (koin-core), not ours to place under shared/. */
    private val koinOwnModules = setOf("defaultModule", "loggerModule")

    /**
     * Ratchet floor: the count of commonMain feature modules, re-measured at
     * each housekeeping pass. 21 as of the wave-9C re-count (23 features;
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
     * Web-site forward ratchet (set-equality, both directions):
     *  - every allowlisted module MUST appear in the web startKoin block
     *    (the arrqueue/shortcuts lesson, web edition);
     *  - every feature module appearing in the web block MUST be allowlisted
     *    (a web target + registration for a new feature must update the
     *    allowlist consciously, never silently).
     */
    @Test
    fun webStartKoin_matchesItsForwardAllowlistExactly() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)
        val staleAllowlistEntries = webForwardAllowlist.filter { it !in features.keys }
        assertTrue(
            staleAllowlistEntries.isEmpty(),
            "web forward allowlist names module(s) no longer declared in any shared " +
                "feature commonMain: $staleAllowlistEntries — rename or removal? Update " +
                "webForwardAllowlist alongside Main.kt.",
        )

        val (site, path) = forwardAllowlistedRegistrationFiles.single()
        val block = startKoinModulesBlock(root.resolve(path))
        val registered = features.keys.filter { name ->
            Regex("\\b$name\\b").containsMatchIn(block)
        }.toSet()

        val missing = webForwardAllowlist - registered
        val unlisted = registered - webForwardAllowlist
        if (missing.isNotEmpty() || unlisted.isNotEmpty()) {
            fail(
                "$site's startKoin does not match its forward allowlist " +
                    "(expected exactly $webForwardAllowlist):\n" +
                    missing.joinToString("\n") { "  - '$it' allowlisted but NOT registered in $path" } +
                    unlisted.joinToString("\n") {
                        "  - '$it' registered in $path but NOT allowlisted (feature module — " +
                            "add it to webForwardAllowlist in the same commit)"
                } +
                    "\nFix: keep apps/web/src/wasmJsMain/kotlin/com/raulshma/jellyplay/web/" +
                    "Main.kt's modules(...) and webForwardAllowlist in lockstep.",
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
     * Every Koin Module declared in any shared feature's commonMain, mapped to
     * its declaring file. subtitle-tester (androidMain-only) contributes
     * nothing here and is naturally excluded.
     */
    private fun discoverFeatureModules(root: File): Map<String, File> {
        val featuresDir = root.resolve("shared/feature")
        assertTrue(featuresDir.isDirectory, "missing $featuresDir — repo layout changed?")
        return scanModuleDeclarations(
            featuresDir.listFiles { f -> f.isDirectory }.orEmpty()
                .map { it.resolve("src/commonMain") }
                .filter { it.isDirectory },
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
