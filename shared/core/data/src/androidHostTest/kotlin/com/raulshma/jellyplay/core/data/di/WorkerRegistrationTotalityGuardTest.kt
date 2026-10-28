package com.raulshma.jellyplay.core.data.di

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Worker-registration totality guard for the app's DelegatingWorkerFactory
 * chain (source-scan on plain text, no PSI — deliberately cheap, but
 * executable, the same shape as the ViewModel totality guards in :app and
 * feature:settings). A CoroutineWorker whose class name no factory arm
 * recognizes falls through every delegate (each `createWorker` returns null
 * and WorkManager's reflection fallback cannot build the explicit-ctor
 * workers), so the enqueue silently fails at WorkManager RUN time — compile
 * gates are blind to this; the guard converts it into a build failure by
 * asserting every concrete worker class has EXACTLY ONE arm across the three
 * factories (`CoreDataWorkerFactory` + `NotificationWorkerFactory` keyed on
 * the simple name, `AppWidgetWorkerFactory` keyed on the FQN). To add a
 * worker: declare the class, land its factory arm WITH the class, and this
 * scan finds both halves — a worker with no arm, an orphaned arm after a
 * rename, or a duplicate arm (the first delegate wins and the second stays
 * dead) all fail here. Discovery is a ratcheted source scan over shared/ and
 * app's main source set (direct `: CoroutineWorker` subclasses plus the
 * concrete subclasses of the abstract `RecommendationWorkerSkeleton`); a new
 * worker outside those roots must extend the scan in
 * [WorkerRegistrationTotalityGuardTest].
 */
class WorkerRegistrationTotalityGuardTest {

    private val scanRoots = listOf("shared", "app/src/main/java")

    private val factoryFiles = listOf(
        "shared/core/data/src/androidMain/kotlin/com/raulshma/jellyplay/core/data/di/CoreDataWorkerFactory.kt",
        "shared/core/data/src/androidMain/kotlin/com/raulshma/jellyplay/core/notification/di/NotificationWorkerFactory.kt",
        "app/src/main/java/com/raulshma/jellyplay/widget/AppWidgetWorkerFactory.kt",
    )

    /**
     * Ratchet floor: concrete CoroutineWorker classes (currently 11: eight
     * :core:data workers, NewMediaCheckWorker, and the two widget
     * recommendation workers through the abstract skeleton). A
     * discovery-rot regression would otherwise make the totality check
     * vacuously green on an empty set.
     */
    private val minDiscoveredWorkers = 11

    /**
     * `class X(…) : CoroutineWorker(...)` / `: RecommendationWorkerSkeleton<…>(…)` —
     * primary-ctor supertype position, crossing the multi-line parameter
     * list ([^{] keeps the match inside the declaration).
     */
    private val workerDeclaration = Regex(
        """(?m)^\s*(?:internal\s+|private\s+|public\s+)?(abstract\s+)?class\s+(\w+)\s*(?:<[^{(]*>)?\s*\([^{]*?\)\s*:\s*(CoroutineWorker|RecommendationWorkerSkeleton)\b""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** `X::class.simpleName ->` — the two core factories' ladder arm keys. */
    private val simpleNameArm = Regex("""(\w+)::class\.simpleName""")

    /** `X::class.java.name ->` — the widget factory's FQN arm keys. */
    private val fqnArm = Regex("""(\w+)::class\.java\.name""")

    @Test
    fun everyCoroutineWorker_hasExactlyOneFactoryArmInTheDelegatingChain() {
        val root = repoRoot()

        val discovered = scanRoots.flatMap { rootName ->
            val dir = root.resolve(rootName)
            assertTrue("missing $rootName — repo layout changed?", dir.isDirectory)
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { File.separator + "build" + File.separator !in it.absolutePath }
                .flatMap { workerDeclaration.findAll(stripComments(it.readText())) }
                .filter { it.groupValues[1].isEmpty() } // abstract bases are never scheduled by name
                .map { it.groupValues[2] }
        }.toSet()
        assertTrue(
            "discovered only ${discovered.size} concrete CoroutineWorker classes under " +
                "${scanRoots.joinToString()}, expected >= $minDiscoveredWorkers — discovery is " +
                "broken (moved package? renamed declarations?), fix the scan in " +
                javaClass.simpleName,
            discovered.size >= minDiscoveredWorkers,
        )

        // Arm keys per factory, comments stripped so doc mentions don't count.
        val armsByFactory = factoryFiles.associateWith { file ->
            val text = stripComments(root.resolve(file).readText())
            (simpleNameArm.findAll(text) + fqnArm.findAll(text)).map { it.groupValues[1] }.toList()
        }

        val problems = mutableListOf<String>()
        for (name in discovered) {
            val hits = armsByFactory.filterValues { name in it }
            when (hits.size) {
                0 ->
                    problems += "  - '$name' has NO factory arm — WorkManager's " +
                        "DelegatingWorkerFactory chain would fall through every delegate and " +
                        "fail createWorker at run time (compile gates are blind to this); " +
                        "add exactly one arm keyed on the class name"
                1 -> {}
                else ->
                    problems += "  - '$name' has ${hits.size} arms (${hits.keys.joinToString(" + ") { File(it).name }}) — " +
                        "keep exactly one (the first matching delegate wins; the others stay dead)"
            }
        }

        // Reverse direction: every arm targets a discovered worker class (a
        // rename that orphaned an arm fails here).
        val orphaned = armsByFactory
            .flatMap { (file, names) -> names.map { it to file } }
            .filter { (name, _) -> name !in discovered }
        if (orphaned.isNotEmpty()) {
            problems += orphaned.map { (name, file) ->
                "  - arm for '$name' in ${File(file).name} matches no CoroutineWorker class " +
                    "declared under ${scanRoots.joinToString()} — renamed or removed? Fix or drop the arm."
            }
        }

        if (problems.isNotEmpty()) {
            fail(
                "the DelegatingWorkerFactory chain's worker arms are not total (exactly one " +
                    "per CoroutineWorker, all resolving):\n" +
                    problems.joinToString("\n")
            )
        }
    }

    // ------------------------------------------------------------ plumbing

    /** Walks up from the test working dir (shared/core/data) to settings.gradle.kts. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as arms. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
}
