package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import java.io.File

/**
 * Ratchet against reintroducing the settings-open ANR
 * (docs/e2e/device-locale-pass.md): the whole-catalog compose-resources
 * resolve — one blocking read per entry when cold, 780 reads over 534
 * distinct entries today — must
 * stay confined to the sanctioned off-main homes:
 *
 *  - [SettingsSearchCatalog.resolved] / [recentItems] (the Dispatchers.Default
 *    seams every caller goes through), and
 *  - [SettingsSearchCatalogPrewarmer] (the app-start warm pass).
 *
 * Any other occurrence of a `.resolve()` call in this module's main sources
 * — a produceState body, a LaunchedEffect, a VM init — is a resolve landing
 * back on the caller's (composition/main) thread and fails this test.
 * Baseline counts lower when call sites disappear; never raise them.
 */
class SettingsMainThreadCatalogResolveGuardTest {

    /** Sanctioned file -> exact number of `.resolve()` occurrences (see KDoc). */
    private val sanctionedCounts = mapOf(
        "SettingsSearchCatalog.kt" to 2,        // resolved() + recentItems() default seams
        "SettingsSearchCatalogPrewarmer.kt" to 1, // the default warm pass
    )

    private fun mainSources(): List<File> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        val roots = listOf("commonMain", "androidMain", "jvmMain")
            .map { File(moduleRoot!!, "src/$it/kotlin") }
            .filter { it.isDirectory }
        return roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
    }

    /**
     * Removes line/block comments so the ratchet checks code, not KDoc prose
     * (the sanctioned KDocs legitimately explain the resolve mechanics).
     */
    private fun String.stripComments(): String {
        val out = StringBuilder()
        var inLine = false
        var inBlock = false
        var inString = false
        var i = 0
        while (i < length) {
            val c = this[i]
            val next = if (i + 1 < length) this[i + 1] else ' '
            when {
                inLine -> if (c == '\n') { inLine = false; out.append(c) }
                inBlock -> if (c == '*' && next == '/') { inBlock = false; i++ }
                inString -> {
                    out.append(c)
                    if (c == '\\') { out.append(next); i++ }
                    else if (c == '"') inString = false
                    else if (c == '\n') inString = false
                }
                c == '/' && next == '/' -> inLine = true
                c == '/' && next == '*' -> inBlock = true
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    @Test
    fun `whole-catalog resolve calls stay confined to the sanctioned off-main homes`() {
        val pattern = Regex("""\.resolve\(\)""")
        val offenders = mutableListOf<String>()
        val seen = mutableMapOf<String, Int>()

        for (file in mainSources()) {
            val count = pattern.findAll(file.readText(Charsets.UTF_8).stripComments()).count()
            if (count == 0) continue
            seen[file.name] = (seen[file.name] ?: 0) + count
            val allowed = sanctionedCounts[file.name] ?: 0
            if (count > allowed) {
                offenders += "${file.name}: $count occurrences (allowed $allowed)"
            }
        }

        // The sanctioned homes must still exist (discovery-rot guard).
        for (name in sanctionedCounts.keys) {
            assertTrue(
                (seen[name] ?: 0) >= 1,
                "$name no longer contains its resolve call — update sanctionedCounts " +
                    "in ${javaClass.simpleName} (lower it, never raise it)",
            )
        }

        if (offenders.isNotEmpty()) {
            fail(
                "whole-catalog resolve escaped the off-main seams (settings-open ANR mechanism): " +
                    offenders.joinToString("; ") +
                    ". Route callers through SettingsSearchCatalog.resolved()/recentItems() instead.",
            )
        }
    }

    @Test
    fun `prewarm stays eager at Koin registration`() {
        // The warm pass is the cold-latency half of the ANR fix; flipping
        // createdAtStart to false (or dropping the warm() kick) would keep
        // every other test green while silently disabling it.
        val candidates = mainSources().filter { it.name == "SettingsKoinModule.kt" }
        assertTrue(candidates.isNotEmpty(), "SettingsKoinModule.kt not found in main sources")
        val moduleFile = candidates.singleOrNull()
            ?: fail("expected exactly one SettingsKoinModule.kt, found ${candidates.size}")
        val lines = moduleFile.readText(Charsets.UTF_8).stripComments().lines()
        val registrationLine = lines.withIndex().singleOrNull { (_, line) ->
            "SettingsSearchCatalogPrewarmer(" in line
        } ?: fail("SettingsSearchCatalogPrewarmer is no longer registered in SettingsKoinModule.kt")
        val window = lines.subList(
            maxOf(0, registrationLine.index - 3),
            minOf(lines.size, registrationLine.index + 4),
        )
        assertTrue(
            window.any { "createdAtStart = true" in it },
            "the SettingsSearchCatalogPrewarmer single must keep createdAtStart = true — " +
                "a lazy registration defers the warm pass until first Settings " +
                "resolution, reopening the cold-start ANR window",
        )
    }
}
