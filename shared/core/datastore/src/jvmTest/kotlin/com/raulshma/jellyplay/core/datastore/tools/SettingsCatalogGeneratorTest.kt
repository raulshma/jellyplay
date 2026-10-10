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
            SettingsCatalogGenerator.render(searchStrings()),
            artifact.readText().replace("\r\n", "\n"),
            "stale catalog artifact — run :shared:core:datastore:generateSettingsCatalog and commit",
        )
    }

    @Test
    fun `entries are unique and policy clean`() {
        val entries = SettingsCatalogGenerator.entries(searchStrings())
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
        SettingsCatalogGenerator.entries(searchStrings())
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
        SettingsCatalogGenerator.entries(searchStrings()).forEach { entry ->
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

    @Test
    fun `every entry carries a human label and a group`() {
        SettingsCatalogGenerator.entries(searchStrings()).forEach { entry ->
            assertTrue(entry.label.isNotEmpty(), "${entry.id}: empty label")
            assertTrue(entry.group.isNotEmpty(), "${entry.id}: empty group")
        }
    }

    @Test
    fun `enum entries carry display labels matching their options`() {
        SettingsCatalogGenerator.entries(searchStrings())
            .filter { it.valueType == "enum" }
            .forEach { entry ->
                val labels = entry.optionLabels.orEmpty()
                assertEquals(entry.options.orEmpty().size, labels.size, "${entry.id}: optionLabels/options size mismatch")
                assertTrue(labels.all { it.isNotEmpty() }, "${entry.id}: empty option label")
            }
    }

    @Test
    fun `ranges are number-only, ordered, and limited to the audited rows`() {
        val entries = SettingsCatalogGenerator.entries(searchStrings())
        entries.forEach { entry ->
            if (entry.min == null && entry.max == null) { return@forEach }
            assertEquals("number", entry.valueType, "${entry.id}: range on a non-number row")
            entry.min?.let { min -> entry.max?.let { max -> assertTrue(min <= max, "${entry.id}: min > max") } }
        }
        // The audited bounds — each backed by a visible clamp in the owning
        // store (downmix 0–12 dB, the rest floor-at-zero). A new bound must
        // be evidence-backed: declare it on the spec row AND extend this set
        // in the same commit. (The dream_* rows carry no bounds — the sync
        // prefix policy excludes them from the catalog entirely.)
        assertEquals(
            setOf(
                "downmix_boost_db",
                "next_up_max_days",
                "video_pass_out_protection_hours",
                "video_skip_back_on_resume_ms",
                "still_watching_episode_threshold",
            ),
            entries.filter { it.min != null || it.max != null }.map { it.key }.toSet(),
            "the bounded-row set changed — audit the new clamp before advertising it",
        )
    }

    private fun searchStrings(): Map<String, String> {
        // KEEP IN SYNC with settingsCatalogStrings in build.gradle.kts — same
        // tables, resolved against the repo root.
        val root = findRepoRoot()
        val tables = listOf(
            "shared/feature/settings/src/commonMain/composeResources/values/strings_search.xml",
            "shared/feature/settings/src/commonMain/composeResources/values/strings_appearance.xml",
            "shared/feature/settings/src/commonMain/composeResources/values/strings_playback.xml",
            "shared/feature/settings/src/commonMain/composeResources/values/strings.xml",
            "shared/core/ui/src/commonMain/composeResources/values/strings.xml",
        )
        return tables.fold(mutableMapOf<String, String>()) { acc, table ->
            acc.putAll(SettingsCatalogGenerator.loadSearchStrings(root.resolve(table).absolutePath))
            acc
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
