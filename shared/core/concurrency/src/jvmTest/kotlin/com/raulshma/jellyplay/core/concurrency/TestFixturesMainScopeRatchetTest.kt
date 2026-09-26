package com.raulshma.jellyplay.core.concurrency

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ratchet against the one hard cycle :shared:core:test-fixtures can create:
 * the fixtures module's jvmShared depends on :shared:core:data (and
 * core:model/player-contract), while consumers' TEST source sets depend on
 * the fixtures — legal ONLY because the two edges sit on disjoint
 * configurations (test → fixtures, fixtures-jvmShared → main). A MAIN-scope
 * declaration of :shared:core:test-fixtures anywhere (commonMain/jvmShared/
 * jvmMain/androidMain/api/top-level dependencies) closes a project
 * dependency cycle Gradle cannot order, and would also leak test doubles
 * into production binaries.
 *
 * The ratchet is ZERO-TOLERANCE — there is no count baseline to raise and no
 * legitimate main-scope case to grandfather. A module that needs a double at
 * main scope has a design problem: the double belongs in the consuming
 * module (or core:data), not on a production classpath.
 *
 * Guarded module set: DISCOVERED from the build itself, the same two-step
 * discovery [BareRunCatchingRatchetTest] uses — every `include(":…")` module
 * declared in settings.gradle.kts plus every directory under shared/ and
 * apps/ carrying its own build.gradle.kts (catches a module mid-wiring
 * before its settings include lands) — each mapped to its build.gradle.kts.
 * The fixtures module's own build file is exempt (the module cannot depend
 * on itself; its jvmShared → core:data edge is the mirrored legal direction,
 * not a fixtures reference).
 *
 * Context classification walks a stack of block-opening lines over
 * comment-stripped source: a block is test-flavored when its opening line
 * names a test source set (`getByName("jvmTest")`, the core:data
 * `if (name == "androidHostTest")` lane gate, …) or the declaration itself
 * is `testImplementation`; a module-path reference with NO test-flavored
 * block anywhere in its enclosing stack fails, with the innermost
 * main-scope context named in the report. Trade-off (shared with the other
 * house scanners): line-comment stripping does not respect `//` inside
 * string literals, and classification reads opening lines, not PSI —
 * deliberately cheap, but executable.
 *
 * Sibling of test-fixtures' own TestFixturesScopeGuardTest (the same rule,
 * running in THAT module's jvmTest — a lane no consumer's check pulls
 * transitively); this one lives in the repo-wide ratchet home so the
 * regularly-exercised core lanes re-scan the whole graph too.
 */
class TestFixturesMainScopeRatchetTest {

    /** The module path that must never appear at main scope. */
    private val modulePath = ":shared:core:test-fixtures"

    /** The fixtures module itself — exempt (see class KDoc). */
    private val exemptModuleDir = "shared/core/test-fixtures"

    /** Known test-scoped consumers the discovery canary pins coverage for. */
    private val knownTestScopedConsumers = listOf(
        "shared/core/data",
        "shared/feature/details",
        "shared/feature/home",
        "shared/feature/livetv",
        "shared/feature/player-video",
    )

    /** A block opened by a line naming a TEST source set — or a lane gate (`if (name == "androidHostTest")`). */
    private val testFlavoredBlock = Regex("""getByName\("[^"]*Test"\)|maybeCreate\("[^"]*Test"\)|\b\w*Test\b""")

    /** A block opened by a line naming a MAIN source set (commonMain/jvmShared/jvmMain/androidMain/…) or a bare dependencies block. */
    private val mainFlavoredBlock = Regex(
        """getByName\("[^"]*(Main|Shared)[^"]*"\)|maybeCreate\("[^"]*(Main|Shared)[^"]*"\)|\b\w*(Main|Shared)\b|\bdependencies\b""",
    )

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        assertTrue(dir != null, "could not locate settings.gradle.kts from ${System.getProperty("user.dir")}")
        return dir!!
    }

    /**
     * Discovers the module build scripts to guard, straight from the build —
     * the [BareRunCatchingRatchetTest] discovery (settings includes + a walk
     * of shared/ and apps/ for build.gradle.kts-carrying dirs), mapped to
     * each module's build.gradle.kts.
     */
    private fun discoverGuardedBuildFiles(root: File): Set<File> {
        val moduleDirs = linkedSetOf<File>()

        // (1) the settings-declared module graph
        val settings = File(root, "settings.gradle.kts")
        if (settings.isFile) {
            val includeCall = Regex("""include\s*\(([^)]*)\)""")
            val quoted = Regex("\"([^\"]+)\"")
            settings.readText().lineSequence()
                .map { it.substringBefore("//") } // ignore commented-out includes
                .forEach { line ->
                    includeCall.findAll(line).forEach { call ->
                        quoted.findAll(call.groupValues[1])
                            .map { it.groupValues[1] }
                            .filter { it.startsWith(":") }
                            .forEach { modulePath ->
                                val dir = File(root, modulePath.removePrefix(":").replace(':', '/'))
                                if (dir.isDirectory) moduleDirs += dir
                            }
                    }
                }
        }

        // (2) modules on disk under shared/ or apps/ regardless of settings
        val pending = ArrayDeque<File>()
        listOf("shared", "apps").forEach { top ->
            val topDir = File(root, top)
            if (topDir.isDirectory) pending += topDir
        }
        while (pending.isNotEmpty()) {
            val dir = pending.removeFirst()
            val children = dir.listFiles() ?: continue
            if (children.any { it.isFile && it.name == "build.gradle.kts" }) moduleDirs += dir
            children.filter { it.isDirectory && it.name != "build" }.forEach { pending += it }
        }

        return moduleDirs
            .filter { it.canonicalFile != File(root, exemptModuleDir).canonicalFile }
            .mapNotNullTo(linkedSetOf()) { module ->
                val buildFile = File(module, "build.gradle.kts")
                if (buildFile.isFile) buildFile else null // module with no build script yet — nothing to guard
            }
    }

    /**
     * Strips line comments (and nested-closed block comments) so the scan
     * reads code, not prose — string literals stay INTACT, since the guarded
     * module path only ever appears inside one.
     */
    private fun stripComments(text: String): String {
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

    data class Violation(val file: File, val line: Int, val context: String, val sourceLine: String)

    /**
     * The [modulePath] references in [buildFile] whose enclosing block stack
     * carries no test-flavored block (and whose own line is not
     * testImplementation) — each reported with the innermost main-scope
     * context the reference actually sits in.
     */
    private fun mainScopeFixtureReferences(buildFile: File): List<Violation> {
        val violations = mutableListOf<Violation>()
        // Stack parallel to the open `{`s: the comment-stripped opening line
        // of each block. The reference is judged AFTER the preceding lines'
        // blocks opened and BEFORE its own line opens one, so it is classified
        // by the blocks it sits in, never one it opens itself.
        val openBlocks = ArrayDeque<String>()

        buildFile.readLines().forEachIndexed { index, rawLine ->
            val code = stripComments(rawLine)
            val inTestContext = openBlocks.any { testFlavoredBlock.containsMatchIn(it) }
            if (modulePath in code && !code.contains("testImplementation") && !inTestContext) {
                val innermostMain = openBlocks.lastOrNull { mainFlavoredBlock.containsMatchIn(it) }
                violations += Violation(
                    file = buildFile,
                    line = index + 1,
                    context = innermostMain?.trim()?.take(80) ?: "top-level build-script scope",
                    sourceLine = rawLine.trim(),
                )
            }
            code.forEach { ch ->
                when (ch) {
                    '{' -> openBlocks.addLast(code)
                    '}' -> openBlocks.removeLastOrNull()
                }
            }
        }
        return violations
    }

    @Test
    fun `test-fixtures is never declared at main scope`() {
        val root = repoRoot()
        val buildFiles = discoverGuardedBuildFiles(root)
        assertTrue(
            buildFiles.size >= 30,
            "discovered only ${buildFiles.size} module build files — discovery is broken " +
                "(moved dirs? new declaration style?), fix the scan in ${javaClass.simpleName}",
        )

        val violations = buildFiles.sortedBy { it.path }
            .flatMap { mainScopeFixtureReferences(it) }
        val report = violations.joinToString("\n") { v ->
            "  ${v.file.toRelativeString(root).replace('\\', '/')}:${v.line} " +
                "[in: ${v.context}] ${v.sourceLine}"
        }
        assertTrue(
            violations.isEmpty(),
            "main-scope declarations of $modulePath found (${violations.size}) — a main source set " +
                "depending on test doubles closes a hard project cycle (fixtures jvmShared → " +
                "core:data) and leaks test doubles into production binaries. There is NO " +
                "legitimate main-scope case to grandfather: move the double into the consuming " +
                "module or core:data instead (shared/core/test-fixtures/build.gradle.kts house " +
                "rules). Offending declarations:\n$report",
        )
    }

    @Test
    fun `discovered build files cover the known test-scoped consumers`() {
        val root = repoRoot()
        val discovered = discoverGuardedBuildFiles(root).map { it.canonicalFile }
        val uncovered = knownTestScopedConsumers
            .map { File(File(root, it), "build.gradle.kts") }
            .filter { it.isFile }
            .filter { it.canonicalFile !in discovered }
        assertTrue(
            uncovered.isEmpty(),
            "guard discovery no longer covers ${uncovered.map { it.path }} — a known test-scoped " +
                "consumer escaped the scan (the ratchet would be vacuously green on it); " +
                "fix discoverGuardedBuildFiles, do not just re-add the path",
        )
    }
}
