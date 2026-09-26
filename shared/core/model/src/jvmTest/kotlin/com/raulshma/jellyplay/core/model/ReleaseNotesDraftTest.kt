package com.raulshma.jellyplay.core.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TEMPORARY: validates the v0.11.1 release-notes draft's `## What's New`
 * table through the real parser. Delete before tagging.
 */
class ReleaseNotesDraftTest {

    private fun draft(): String {
        // Test working dir is the module dir (shared/core/model) -> repo root is three levels up.
        val repoRoot = File(System.getProperty("user.dir")).resolve("../../../release-notes-v0.11.1.md").canonicalFile
        return requireNotNull(repoRoot.takeIf { it.exists() }?.readText()) {
            "Draft not found at ${repoRoot.absolutePath} (user.dir=${System.getProperty("user.dir")})"
        }
    }

    @Test
    fun whatsNewTableParsesIntoExpectedCards() {
        val body = draft()
        val entries = parseWhatsNewEntries(body)
        println("PARSED ${entries.size} ENTRIES:")
        entries.forEach {
            println("  [${it.category}] ${it.title} | howTo=${it.howTo} | icon=${it.icon} | target=${it.target}")
        }
        assertEquals(14, entries.size, "Expected 14 cards, got ${entries.size}")
        assertEquals("Custom Discover rows", entries.first().title)
        assertEquals(WhatsNewCategory.NEW, entries.first().category)
        assertEquals("settings.discoverRows", entries.first().target)
        assertEquals("rows", entries.first().icon)
        assertEquals("Player memory leak fixed", entries.last().title)
        assertEquals(WhatsNewCategory.FIX, entries.last().category)
        assertTrue(entries.none { it.summary.isBlank() }, "Every card needs a summary")
        assertTrue(entries.none { it.title.contains("|") }, "No stray pipes survived in titles")
    }

    @Test
    fun releaseMapsThroughWhatsNewReleaseFromNotes() {
        val release = whatsNewReleaseFromNotes("v0.11.1", "2026-09-26", "Release v0.11.1", draft())
        requireNotNull(release) { "Draft must map onto a WhatsNewRelease" }
        assertEquals("0.11.1", release.version)
        assertEquals(14, release.entries.size)
    }
}
