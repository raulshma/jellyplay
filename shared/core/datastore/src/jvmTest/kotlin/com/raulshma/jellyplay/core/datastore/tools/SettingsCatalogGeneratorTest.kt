package com.raulshma.jellyplay.core.datastore.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-file guard for the settings catalog artifact: the committed file in
 * the plugin tree must byte-match what the spec declarations render today
 * (`checkSettingsCatalog` enforces the same at Gradle level; this catches it
 * in the IDE test run too). The plugin tree is committed in this repo, so a
 * missing artifact fails here just as it fails `check`.
 */
class SettingsCatalogGeneratorTest {

    @Test
    fun `committed artifact matches the spec declarations`() {
        val artifact = findRepoRoot()
            .resolve("jellyfin-plugin-jellyplay/src/Jellyfin.Plugin.JellyPlay/Resources/jellyplay-settings-catalog.json")
        check(artifact.isFile) {
            "settings catalog artifact missing: $artifact — run :shared:core:datastore:generateSettingsCatalog and commit"
        }
        assertEquals(
            SettingsCatalogGenerator.render(),
            artifact.readText().replace("\r\n", "\n"),
            "stale catalog artifact — run :shared:core:datastore:generateSettingsCatalog and commit",
        )
    }

    @Test
    fun `entries are unique and policy clean`() {
        val entries = SettingsCatalogGenerator.entries()
        assertTrue(entries.size > 100, "expected the full spec surface, got ${entries.size}")
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "duplicate ids")
        assertEquals(
            emptySet(),
            entries.map { it.key }.toSet() intersect SettingsCatalogGenerator.DENYLIST,
            "denylisted key reached the catalog",
        )
        assertTrue(
            entries.none { entry -> SettingsCatalogGenerator.SYNC_EXCLUDED_PREFIXES.any { entry.key.startsWith(it) } },
            "excluded sync prefix leaked into the catalog",
        )
    }

    @Test
    fun `enum entries carry options and their default among them`() {
        SettingsCatalogGenerator.entries()
            .filter { it.valueType == "enum" }
            .forEach { entry ->
                assertTrue(entry.options.orEmpty().isNotEmpty(), "${entry.id}: enum without options")
                entry.defaultValue?.let { default ->
                    assertTrue(
                        entry.options.orEmpty().contains(default.content),
                        "${entry.id}: default ${default.content} not among options",
                    )
                }
            }
    }

    @Test
    fun `defaults match their declared value type`() {
        SettingsCatalogGenerator.entries().forEach { entry ->
            val default = entry.defaultValue ?: return@forEach
            when (entry.valueType) {
                "boolean" -> assertTrue(
                    default == kotlinx.serialization.json.JsonPrimitive(true) ||
                        default == kotlinx.serialization.json.JsonPrimitive(false),
                    "${entry.id}: ${default.content} is not a boolean",
                )
                "number" -> checkNotNull(default.content.toDoubleOrNull()) {
                    "${entry.id}: ${default.content} is not a number"
                }
                "enum", "string" -> assertTrue(default.isString, "${entry.id}: ${default.content} is not a string")
            }
        }
    }

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "repo root (settings.gradle.kts) not found above user.dir" }
    }
}
