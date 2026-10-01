package com.raulshma.jellyplay.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * CI lane-coverage ratchet (the TestFixturesScopeGuardTest idiom, pointed at
 * the workflow instead of the build graph).
 *
 * kmp-build.yml's shared-tree lanes used to HAND-enumerate every shared
 * module's Gradle tasks, and that hand list was the failure mode twice over:
 * photos, shell, player-book, and test-fixtures shipped full local suites
 * that ran in NO CI lane, and even after the tests were added,
 * test-fixtures stayed out of the build lane because its jvmTest line alone
 * satisfied the old "module path appears somewhere" guard (and :app itself
 * once ran in none — the workflow's android-app comment records that earlier
 * instance of the same bug). Nothing compile-side can catch an omission,
 * because the omission is a missing *invocation*, not missing code.
 *
 * The lanes are now DERIVED: a workflow step scans settings.gradle.kts for
 * include(":shared:…") and expands every hit into jvmJar +
 * compileAndroidMain (build lane) and jvmTest (test lane), so a new include
 * joins coverage by construction. This guard ratchets the derivation
 * itself:
 *
 *  1. settings.gradle.kts still contains the shared includes the scan
 *     expects (floor — keeps the scan from passing vacuously);
 *  2. the workflow still contains the derivation step (the include-scan
 *     regex + the two lane-step interpolations);
 *  3. NO hand-enumerated `:shared:…:{jvmJar,compileAndroidMain,jvmTest}`
 *     task literal creeps back into the workflow — a partial hand list
 *     beside the derivation is exactly the two-mechanism drift that bred
 *     the original dark suites. A genuine one-off special case (never seen
 *     so far) must consciously update this test.
 *
 * androidHostTest lanes are NOT derived here: which modules own hostTest
 * source sets is not textually derivable from settings.gradle.kts. They run
 * in the android-app job (core:data, core:ui, player-video, syncplay today);
 * hostTest additions ride review.
 */
class SharedModuleCiLaneGuardTest {

    /** Ratchet floor: shared includes at the time this guard landed. */
    private val minSharedModuleCount = 36

    private val sharedInclude = Regex("""include\("(:shared:[^"]+)"\)""")

    /** The derivation step's include-scan, as written in kmp-build.yml. */
    private val derivationScan =
        """grep -oE 'include\("(:shared:[^"]+)"\)' settings.gradle.kts"""

    /** The two lane steps must interpolate the derived outputs. */
    private val buildLaneUsage = "steps.shared-modules.outputs.build_tasks"
    private val testLaneUsage = "steps.shared-modules.outputs.test_tasks"

    /** A hand-written shared task invocation — forbidden inside the workflow. */
    private val handListedTask =
        Regex(""":shared:[a-z0-9-]+(?::[a-z0-9-]+)*:(?:jvmJar|compileAndroidMain|jvmTest)""")

    @Test
    fun sharedLanesAreDerivedFromSettingsNotHandListed() {
        val root = repoRoot()
        val settings = root.resolve("settings.gradle.kts")
        assertTrue(settings.isFile, "missing settings.gradle.kts — repo layout changed?")

        val included = sharedInclude.findAll(settings.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        assertTrue(
            included.size >= minSharedModuleCount,
            "discovered only ${included.size} shared includes, expected >= " +
                "$minSharedModuleCount — settings.gradle.kts include style changed? Fix the " +
                "scan in ${javaClass.simpleName} (it must not pass vacuously).",
        )

        val workflowFile = root.resolve(".github/workflows/kmp-build.yml")
        assertTrue(workflowFile.isFile, "missing kmp-build.yml — CI restructured?")
        val workflow = workflowFile.readText()

        assertTrue(
            workflow.contains(derivationScan),
            "kmp-build.yml no longer scans settings.gradle.kts for shared includes — the " +
                "derive step was renamed/rewritten? The lanes must stay DERIVED (the hand " +
                "list is what let photos/shell/player-book/test-fixtures run in no lane).",
        )
        for (usage in listOf(buildLaneUsage, testLaneUsage)) {
            assertTrue(
                workflow.contains(usage),
                "kmp-build.yml lane step no longer interpolates '$usage' — a lane stopped " +
                    "consuming the derived task list (back to a hand list?).",
            )
        }

        val handListed = handListedTask.findAll(workflow).map { it.value }.toSortedSet()
        if (handListed.isNotEmpty()) {
            fail(
                "hand-enumerated shared task literal(s) back in kmp-build.yml: $handListed — " +
                    "hand lists drift (that is how shared suites went dark); extend the " +
                    "derivation step instead, or update ${javaClass.simpleName} if a literal " +
                    "is genuinely required.",
            )
        }
    }

    /**
     * Replays the workflow's OWN derivation pipeline against the real
     * settings.gradle.kts: the grep pattern is lifted out of the workflow
     * text (so editing the scan there re-tests here), expanded with the
     * sed-style normalization, and compared against this guard's independent
     * capture-group extraction — a workflow-side scan edit that stops
     * recovering the full include set fails here, before CI runs a truncated
     * lane.
     */
    @Test
    fun workflowScanRecoversTheSameModuleSetAsThisGuard() {
        val root = repoRoot()
        val settingsText = root.resolve("settings.gradle.kts").readText()
        val workflowText = root.resolve(".github/workflows/kmp-build.yml").readText()

        val scanRegexText = Regex(
            """grep -oE '([^']+)' settings\.gradle\.kts""",
        ).find(workflowText)?.groupValues?.get(1)
            ?: fail("derive step's grep pattern not found in kmp-build.yml — derivation restructured?")
        val scan = Regex(scanRegexText)

        val fromWorkflowPipeline = scan.findAll(settingsText)
            .map { it.value }
            // The workflow's sed: strip include(" prefix and ") suffix.
            .map { it.removePrefix("include(\"").removeSuffix("\")") }
            .toSortedSet()
        val expected = sharedInclude.findAll(settingsText)
            .map { it.groupValues[1] }
            .toSortedSet()

        assertEquals(expected, fromWorkflowPipeline)
        assertTrue(
            fromWorkflowPipeline.size >= minSharedModuleCount,
            "workflow scan recovered only ${fromWorkflowPipeline.size} modules — regex rot.",
        )
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }
}
