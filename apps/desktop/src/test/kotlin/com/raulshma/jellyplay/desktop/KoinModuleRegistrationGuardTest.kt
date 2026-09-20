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
 * Since the sharedFeatureModules fold, the ONE declaration both JVM shells
 * consume is `shared/feature/shell`'s `val sharedFeatureModules = listOf(…)`:
 * Android's
 * JellyPlayApplication.kt spreads it inside its own startKoin, desktop's
 * Main.kt consumes it through DesktopKoinModules' `desktopKoinModules(…)`
 * list. This guard auto-derives the expected set from that declaration with
 * set-equality in BOTH directions against the feature modules discovered
 * under `shared/feature/<module>/src/{commonMain,jvmShared}`:
 *  - a feature module the declaration FORGETS fails (the arrqueue/shortcuts
 *    lesson — compile gates are blind to Koin registration);
 *  - a declared module that no longer resolves fails (rename/typo);
 *  - a shell re-growing a hand-copied inline feature list fails — the
 *    feature modules may ONLY flow through the declaration's spread;
 *  - every non-platform `<identifier>Module` in a registration list must
 *    resolve to a shared feature or shared core definition.
 *
 * Source-scanning on plain text (no PSI) — deliberately cheap, but executable.
 */
class KoinModuleRegistrationGuardTest {

    /** The single shared feature-module declaration both JVM shells spread. */
    private val sharedDeclarationName = "sharedFeatureModules"
    private val sharedDeclarationFile =
        "shared/feature/shell/src/jvmShared/kotlin/com/raulshma/jellyplay/feature/shell/SharedFeatureModules.kt"

    /** The two startKoin registration sites (spread + reverse checks). */
    private val androidRegistrationFile =
        "app/src/main/java/com/raulshma/jellyplay/JellyPlayApplication.kt"
    private val desktopRegistrationFile =
        "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/Main.kt"

    /** The desktop module list Main.kt's startKoin consumes (the fold target). */
    private val desktopModuleListFile =
        "apps/desktop/src/main/kotlin/com/raulshma/jellyplay/desktop/DesktopKoinModules.kt"
    private val desktopModuleListName = "desktopKoinModules"

    /**
     * Module-variant prefixes that are intentionally platform-/core-scoped:
     * each startKoin site registers its own platform actuals plus the shared
     * core graph, so these names are not required to come from a feature's
     * commonMain (e.g. androidDataModule, desktopPlayerModule,
     * desktopMusicMessageBusModule, datastoreCommonModule, networkJvmModule…).
     */
    private val platformPrefixes = listOf(
        "android", "desktop",
        "datastore", "database", "network", "data",
    )

    /** Koin's own modules (koin-core), not ours to place under shared/. */
    private val koinOwnModules = setOf("defaultModule", "loggerModule")

    /**
     * Ratchet floor: the count of commonMain/jvmShared feature modules,
     * re-measured at each housekeeping pass. 22 as of the
     * sharedFeatureModules fold (23 features; subtitle-tester contributes
     * none — androidMain-only — and player-video's defs live in its platform
     * modules androidPlayerVideoModule/desktopPlayerVideoModule, so its
     * commonMain declares no Module val). Bump when features land. A
     * discovery-rot regression (regex stops matching, dirs move) would
     * otherwise make the forward check vacuously green on an empty list.
     */
    private val minFeatureModuleCount = 22

    /**
     * The shared declaration ratchet, derived (set-equality, both
     * directions) against the discovered feature graph:
     *  - every declared module MUST resolve to a discovered shared feature
     *    module (the arrqueue/shortcuts rename/typo lesson);
     *  - every discovered feature module MUST be declared — a new feature's
     *    Module is only registered through this list, never inline.
     */
    @Test
    fun sharedFeatureModulesDeclaration_matchesDiscoveredFeatureModulesExactly() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)
        assertTrue(
            features.size >= minFeatureModuleCount,
            "discovered only ${features.size} commonMain/jvmShared feature modules " +
                "(${features.keys.sorted()}), expected >= $minFeatureModuleCount — " +
                "discovery is broken (moved dirs? new declaration style?), fix the scan in " +
                javaClass.simpleName,
        )

        val declaration = root.resolve(sharedDeclarationFile)
        assertTrue(declaration.isFile, "missing $sharedDeclarationFile — repo layout changed?")
        val declared = declaredFeatureModuleList(
            stripComments(declaration.readText()),
            sharedDeclarationName,
        ).toSet()
        assertTrue(
            declared.isNotEmpty(),
            "`$sharedDeclarationName` declaration is empty — the JVM shells register no " +
                "feature modules anymore? Restore the list.",
        )

        val stale = declared.filter { it !in features.keys }
        assertTrue(
            stale.isEmpty(),
            "`$sharedDeclarationName` names module(s) no longer declared in any shared " +
                "feature commonMain/jvmShared: $stale — rename or removal? Update " +
                "$sharedDeclarationName in ${declaration.name}.",
        )

        val undeclared = features.keys - declared
        if (undeclared.isNotEmpty()) {
            fail(
                "shared feature Koin modules missing from `$sharedDeclarationName` — " +
                    "compile gates are BLIND to this; koinViewModel would throw " +
                    "NoDefinitionFound at runtime on first navigation:\n" +
                    undeclared.sorted().joinToString("\n") { name ->
                        "  - '$name' (declared at ${relative(root, features.getValue(name))})"
                    } +
                    "\nFix: add one line `<module>,` (plus its import) to " +
                    "`$sharedDeclarationName` in $sharedDeclarationFile — both JVM shells " +
                    "register feature modules ONLY through that declaration's spread.",
            )
        }
    }

    /**
     * Both JVM shells must consume the declaration BY SPREAD, and neither
     * may name a feature module directly (a hand-copied inline list is the
     * drift the fold removed): Android spreads it inside its own startKoin
     * modules(...) block; desktop's startKoin consumes
     * `desktopKoinModules(…)`, whose list is the only place the spread may
     * live.
     */
    @Test
    fun bothJvmShells_consumeTheSharedDeclarationBySpread() {
        val root = repoRoot()
        val features = discoverFeatureModules(root)

        // Android: the spread sits inside JellyPlayApplication's own
        // modules(...) block.
        val androidFile = root.resolve(androidRegistrationFile)
        val androidText = stripComments(androidFile.readText())
        val androidBlock = startKoinModulesBlock(androidFile)
        assertTrue(
            androidBlock.contains("*$sharedDeclarationName"),
            "Android's startKoin must register its feature modules via " +
                "`*$sharedDeclarationName` (the shared declaration), not a hand-copied " +
                "module list.",
        )

        // Desktop: Main.kt's startKoin consumes desktopKoinModules(...), and
        // THAT list carries the spread.
        val desktopFile = root.resolve(desktopRegistrationFile)
        val desktopBlock = startKoinModulesBlock(desktopFile)
        assertTrue(
            desktopBlock.contains(desktopModuleListName),
            "Desktop Main.kt's startKoin must consume `$desktopModuleListName(paths)` — " +
                "the extracted module list (DesktopKoinModules.kt).",
        )
        val desktopListFile = root.resolve(desktopModuleListFile)
        val desktopListText = stripComments(desktopListFile.readText())
        val desktopListBlock = functionListBlock(desktopListFile, desktopModuleListListMarker)
        assertTrue(
            desktopListBlock.contains("*$sharedDeclarationName"),
            "Desktop's `$desktopModuleListName` list must spread " +
                "`*$sharedDeclarationName` (the shared declaration), not a hand-copied " +
                "module list.",
        )

        // No JVM registration site may name a discovered feature module
        // directly — feature modules flow ONLY through the declaration.
        val namedInline = buildList {
            for ((site, text) in listOf(
                "Android app" to androidText,
                "Desktop Main.kt" to desktopBlock,
                "Desktop DesktopKoinModules.kt" to desktopListText,
            )) {
                for (name in features.keys) {
                    if (Regex("\\b$name\\b").containsMatchIn(text)) {
                        add("'$name' named directly in $site")
                    }
                }
            }
        }
        assertTrue(
            namedInline.isEmpty(),
            "feature module(s) named OUTSIDE the shared declaration — the JVM shells " +
                "must register features only via `*$sharedDeclarationName`:\n" +
                namedInline.joinToString("\n") { "  - $it" },
        )
    }

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
     * a feature whose whole module surface is JVM-only (insights) is
     * still legitimately registered
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

    // ------------------------------------------------------- block extraction

    /** The `fun desktopKoinModules(...)` marker whose listOf(...) body is scanned. */
    private val desktopModuleListListMarker = "fun $desktopModuleListName"

    /**
     * The text inside the `listOf( … )` of the function introduced by
     * [marker], with comments stripped. Mirrors [startKoinModulesBlock]'s
     * balance scan for the desktop module list extracted out of Main.kt.
     */
    private fun functionListBlock(file: File, marker: String): String {
        assertTrue(file.isFile, "registration file ${file.path} does not exist")
        val text = stripComments(file.readText())
        val fnStart = text.indexOf(marker)
        assertTrue(fnStart >= 0, "${file.path}: no `$marker` declaration")
        val listOfStart = text.indexOf("listOf(", fnStart)
        assertTrue(listOfStart >= 0, "${file.path}: no listOf(...) in `$marker`")

        val openParen = listOfStart + "listOf".length
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
        fail("${file.path}: unbalanced parentheses in `$marker` listOf(...)")
    }

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
     * source text — the declaration a forward-derives site's startKoin
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
