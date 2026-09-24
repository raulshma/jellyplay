package com.raulshma.jellyplay.core.testfixtures

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Build-graph tripwire for this module's one house rule: :shared:core:test-fixtures
 * is consumed from TEST source sets only (AGP 9 has no KMP testFixtures, so
 * the rule cannot live in a self-enforcing Gradle configuration — it lives in
 * the convention comment plus this scan). The repo spells test dependencies as
 * `implementation(...)` inside `getByName("...Test")` source-set blocks, so
 * the guard tracks brace context: a reference to the module path is legal only
 * when the line itself is a `testImplementation`-style declaration or some
 * enclosing block was opened by a test-flavored line (`getByName("jvmTest")`,
 * `commonTest`, `androidHostTest`, ...). A main source set writing
 * `implementation(project(":shared:core:test-fixtures"))` fails here.
 *
 * Runs with this module's jvmTest — a consumer wiring the module badly keeps
 * compiling until someone runs this lane, which is the tripwire trade-off the
 * tiny-module rule accepts.
 */
class TestFixturesScopeGuardTest {

    private val modulePath = ":shared:core:test-fixtures"

    private val testFlavoredBlock =
        Regex("""getByName\("[^"]*Test"\)|\bcommonTest\b|\bjvmTest\b|\bandroidHostTest\b|\bcommonDeviceTest\b""")

    @Test
    fun `module is referenced only from test source sets`() {
        val start = File(System.getProperty("user.dir") ?: ".")
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: fail("repo root (settings.gradle.kts) not found at or above $start")

        val buildFiles = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", ".kotlin") }
            .filter { it.isFile && it.name == "build.gradle.kts" }
            .toList()

        assertTrue(buildFiles.isNotEmpty(), "no build.gradle.kts files found under $root — scan is broken, not the graph")

        val offenders = buildFiles.flatMap { file -> nonTestScopedReferences(file, root) }

        assertTrue(
            offenders.isEmpty(),
            "Non-test-scoped references to $modulePath found. This module must NEVER be a main " +
                "source set dependency (see shared/core/test-fixtures/build.gradle.kts):\n" +
                offenders.joinToString("\n"),
        )
    }

    /** Lines in [file] referencing the module path outside any test context. */
    private fun nonTestScopedReferences(file: File, root: File): List<String> {
        val offenses = mutableListOf<String>()
        // Stack parallel to the open `{`s: was that block opened by a test-flavored line?
        val openBlocks = ArrayDeque<Boolean>()
        file.readLines().forEachIndexed { index, rawLine ->
            // Strip line comments so prose braces don't skew depth tracking.
            val code = rawLine.substringBefore("//")
            val inTestContext = openBlocks.any { it }
            if (modulePath in code && !inTestContext && "estImplementation" !in rawLine) {
                offenses += "${file.relativeToOrSelf(root).path.replace('\\', '/')}:${index + 1}: ${rawLine.trim()}"
            }
            // Update depth AFTER checking, so the reference is judged by the
            // blocks it sits in, not one it opens itself.
            code.forEach { ch ->
                when (ch) {
                    '{' -> openBlocks.addLast(testFlavoredBlock.containsMatchIn(code))
                    '}' -> openBlocks.removeLastOrNull()
                }
            }
        }
        return offenses
    }
}
