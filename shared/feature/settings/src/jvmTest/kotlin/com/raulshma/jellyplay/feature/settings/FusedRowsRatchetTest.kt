package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_commercial
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_intro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_outro
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_preview
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_recap
import com.raulshma.jellyplay.core.ui.generated.resources.core_segment_unknown
import java.io.File
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The fused-conversion ratchet for the WHOLE settings module — the enforcement
 * teeth of the single-homing wave, generalized from the appearance-domain
 * template it started as. Every domain but the experimental screen converted:
 * its visible behavior must stay derivable from the fused rows alone, and the
 * retired three-home machinery must stay retired:
 *
 *  1. ZERO retired declarations in production source: no `SettingsSearchBinding`
 *     table, no `SettingsRowRecord` list, no `*Ids` holder, no separate
 *     admissions map — outside the ONE documented exception (the experimental
 *     screen's spec-direct binding derivation and its holder). A resurrected
 *     table or holder is a second home by definition; compilation cannot catch
 *     it (the retired types that remain — the binding data class, the
 *     experimental holder — are live), so this is a source scan.
 *  2. The ordered row lists ARE the spine: every screen group carries exactly
 *     its row list's ids in order, the vocabulary is exactly their union
 *     (duplicate-free), and every id resolves to exactly one catalog item.
 *  3. The groups' admissions derive from the rows' gates: ContentGated rows
 *     carry no admission entry (counted by no total, always admitted for
 *     emission), residual rows' entries fold from their own advanced flag,
 *     and no row carries a hand face a spec-backed row must not.
 *  4. The no-screen-title exception set stays exactly the documented one, the
 *     media-segment rows keep sharing the enum's core_segment resources, and
 *     the list-level platform tags (the Android-only engine branches, the
 *     desktop-only mpv rows) survive the projection.
 *  5. The entrance single-homing: every converted domain's
 *     [SettingsEntranceSectionRow] is the list entry (spliced once, at the
 *     pinned render position) and its (phone, tv) steps stay the retired
 *     literals — the emission faces read the one declaration.
 *
 * Behavior byte-identity (search results, catalog order, totals, gates under
 * every flag permutation) is pinned where it is strongest: the absolute
 * number pins in `SettingsCatalogScreenContractTest` read the derived
 * admissions through [rowTotalFor], and `SpecDerivedSearchItemsTest` /
 * `SettingsSearchCatalogTest` pin the projections field-for-field.
 */
class FusedRowsRatchetTest {

    // ── 1. Zero retired declarations in production source ───────────────

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not locate repo root (no settings.gradle.kts walking up from ${System.getProperty("user.dir")})")
    }

    /** Drops line and block comments so doc mentions don't count as declarations. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun productionSources(): List<Pair<String, String>> {
        val root = repoRoot()
        val dir = root.resolve("shared/feature/settings/src")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { path -> path.relativeTo(root).path.split('\\', '/').none { it.endsWith("Test") } }
            .map { it.relativeTo(root).path.replace('\\', '/') to stripComments(it.readText()) }
            .toList()
    }

    /**
     * The documented exceptions to the zero-retired-machinery scan: the
     * experimental screen's spec-direct binding derivation (its per-row
     * search categories differ, so its five rows cannot fold into a
     * single-category fused row list) and its ids holder.
     */
    private val experimentalDerivationFile =
        "shared/feature/settings/src/commonMain/kotlin/com/raulshma/jellyplay/feature/settings/ExperimentalSettingsSearchItems.kt"

    @Test
    fun `production source declares no retired binding record or ids-holder machinery`() {
        fun declaresBindingEntry(text: String): Boolean {
            // The SettingsSearchBinding class declaration survives (the
            // experimental screen's spec-direct derivation uses it) — a
            // BINDING TABLE ENTRY is a call site, not a `class` declaration.
            Regex("SettingsSearchBinding\\s*\\(").findAll(text).forEach { m ->
                val matchStart = m.range.first
                val lineStart = text.lastIndexOf('\n', matchStart) + 1
                val prefix = text.slice(lineStart until matchStart)
                if ("class" !in prefix) return true
            }
            return false
        }

        val offenders = productionSources().filter { (path, text) ->
            path != experimentalDerivationFile && (
                declaresBindingEntry(text) ||
                    text.contains("SettingsRowRecord(") ||
                    text.contains("SettingsRowRecords") ||
                    Regex("""\bobject\s+\w+Ids\b""").containsMatchIn(text) ||
                    text.contains("admissionsByAdvancedFlag")
                )
        }.map { it.first }
        assertEquals(emptyList(), offenders, "retired three-home machinery reappeared in production source")
    }

    @Test
    fun `the retired per-domain ids holders are gone from production sources`() {
        // The holders' deletion is the wave's identity single-homing; a name
        // reappearing in PRODUCTION code means a second identity home came
        // back. The experimental holder is the documented exception (its
        // binding derivation names its own ids).
        val retiredHolders = listOf(
            "AppearanceSettingsIds", "PlaybackSettingsIds", "HomeSettingsIds", "AudioSettingsIds",
            "StorageSettingsIds", "LanguageSettingsIds", "TrackSelectionIds", "NotificationSettingsIds",
            "SecuritySettingsIds", "SettingsScreenIds", "IntegrationsScreenIds", "BackupSettingsIds",
            "AboutScreenIds",
        )
        val offenders = productionSources().filter { (_, text) -> retiredHolders.any { it in text } }
            .map { it.first }
        assertEquals(emptyList(), offenders, "a retired ids holder name reappeared in production source")
    }

    // ── 2. The row lists are the spine ──────────────────────────────────

    /**
     * Every converted domain's screen groups, as (group id, the ordered row
     * list that derives it). One line per group — a domain adding a group
     * without registering it here fails the all-groups-derived scan below
     * (its id appears in [SettingsScreenGroups.all] but in no spine entry).
     */
    private val spine: List<Pair<String, List<SettingsRow>>> = listOf(
        // appearance
        "appearance.theme" to AppearanceThemeRows,
        "appearance.navigation" to AppearanceNavigationRows,
        "appearance.library" to AppearanceLibraryRows,
        "appearance.performance" to AppearancePerformanceRows,
        "appearance.eyeCare" to AppearanceEyeCareRows,
        "appearance.newsletter" to AppearanceNewsletterRows,
        // home
        "home.display" to HomeDisplayRows,
        "home.nextUp" to HomeNextUpRows,
        "home.layout" to HomeLayoutRows,
        "home.cards" to HomeCardsRows,
        // playback/video
        "playback.player" to PlaybackPlayerRows,
        "playback.advancedVideo" to PlaybackAdvancedVideoRows,
        "playback.engine" to PlaybackEngineRows,
        "playback.syncPlay" to PlaybackSyncPlayRows,
        "playback.casting" to PlaybackCastingRows,
        "playback.dvr" to PlaybackDvrRows,
        "playback.mediaSegments" to PlaybackMediaSegmentsRows,
        // audio
        "audio" to AudioPlayerRows,
        "audio.cache" to AudioCacheRows,
        // language
        "language.general" to LanguageGeneralRows,
        "language.subtitles" to LanguageSubtitlesRows,
        "language.trackSelection" to TrackSelectionRowsList,
        // notifications
        "notifications" to NotificationRowsList,
        // storage
        "storage.cache" to StorageCacheRows,
        "storage.network" to StorageNetworkRows,
        "storage.downloads" to StorageDownloadsRows,
        // security
        "security" to SecurityRowsList,
        // system
        "system.core" to SystemCoreRows,
        "system.screensaver" to SystemScreensaverRows,
        "system.idleAmbient" to SystemIdleAmbientRows,
        "system.discordPresence" to SystemDiscordPresenceRows,
        "system.hooks" to SystemHooksRows,
        // account / activity-insights
        "account" to AccountRowsList,
        "activityInsights" to ActivityInsightsRowsList,
        // integrations / backup / about
        "integrations" to IntegrationsRowsList,
        "backup" to BackupRowsList,
        "about" to AboutRowsList,
    )

    /** Every fused row vocabulary — one entry per converted domain. */
    private val vocabularies: List<List<SettingsRow>> = listOf(
        AppearanceRows.all,
        HomeRows.all,
        PlaybackRows.all,
        AudioRows.all,
        StorageRows.all,
        LanguageRows.all,
        TrackSelectionRows.all,
        NotificationRows.all,
        SecurityRows.all,
        SystemRows.all,
        AccountRows.all,
        ActivityInsightsRows.all,
        IntegrationsRows.all,
        BackupRows.all,
        AboutRows.all,
    )

    @Test
    fun `every screen group is derived and carries exactly its row list in order`() {
        // The registered spine entries must all be derived groups
        // (asRowGroup products): each group's ids are its row list's ids.
        spine.forEach { (groupId, rows) ->
            val group = SettingsScreenGroups.all.singleOrNull { it.id == groupId }
                ?: fail("group $groupId missing from SettingsScreenGroups.all")
            assertEquals(rows.map { it.id }, group.itemIds, "$groupId drifted from its row list")
        }
        // …every group in the aggregation is a spine entry (no hand-built
        // group reappears) — the experimental screen's spec-direct group is
        // the documented exception.
        val spineIds = spine.map { it.first }.toSet()
        val undecorated = SettingsScreenGroups.all.map { it.id } - spineIds
        assertEquals(listOf("experimental"), undecorated, "an unregistered (hand-built?) group appeared in the aggregation")
        // …and the vocabularies are exactly their groups' union, duplicate-free.
        val union = spine.flatMap { it.second }
        assertEquals(union.map { it.id }, union.map { it.id }.toSet().toList(), "a row id is declared twice")
        assertEquals(union.map { it.id }.toSet(), vocabularies.flatten().map { it.id }.toSet(), "a vocabulary drifted from the group row lists")
    }

    @Test
    fun `every fused id resolves to exactly one catalog item`() {
        val catalogIds = SettingsSearchCatalog.items.map { it.id }
        val missing = vocabularies.flatten().map { it.id }.filter { id -> catalogIds.count { it == id } != 1 }
        assertEquals(emptyList(), missing, "fused ids that resolve to no (or several) catalog items")
    }

    // ── 3. Admissions derive from the rows' gates ───────────────────────

    @Test
    fun `the groups' admissions derive from the rows' gates`() {
        spine.forEach { (groupId, rows) ->
            val group = SettingsScreenGroups.all.single { it.id == groupId }
            rows.forEach { row ->
                if (row.gate is RowAdmission.ContentGated) {
                    // ContentGated: no admission entry (counted by no total)
                    // and always admitted for emission.
                    assertNull(group.admissions[row.id], "$groupId/${row.id} is ContentGated — it must carry no admission entry")
                    assertTrue(
                        group.rowAdmitted(row.id, RowAdmissionFlags()),
                        "$groupId/${row.id} is ContentGated — it must admit for emission",
                    )
                } else {
                    val declared = group.admissionOf(row.id)
                    // Residual rows fold to their own advanced flag; spec-
                    // backed rows fold to the spec's — either way the entry
                    // exists and the emission gate evaluates the row's gate.
                    assertTrue(declared != null, "$groupId/${row.id} declares no gate and carries no admission entry")
                    val effective = row.gate
                        ?: if (row.isAdvanced) RowAdmission.Advanced else RowAdmission.Always
                    if (row.isResidualRow()) {
                        assertEquals(
                            effective.admitted(RowAdmissionFlags()),
                            declared?.admitted(RowAdmissionFlags()),
                            "$groupId/${row.id} emission gate drifted from the row's declaration",
                        )
                    }
                }
            }
        }
    }

    private fun SettingsRow.isResidualRow(): Boolean = keywords.isNotEmpty() || route != null

    // ── 4. The documented exception sets, preserved through the fusion ──

    @Test
    fun `titleRes is null exactly for the documented no-screen-title rows`() {
        // style_accent (the hand-built VariantAccentPicker, titled
        // per-variant by core_ui_variant_accent_title) and newsletter_sections
        // (the runtime-reorderable rows render NewsletterSectionType.labelRes
        // — enum-driven, like media segments).
        val noTitle = vocabularies.flatten().filter { it.titleRes == null }.map { it.id }
        assertEquals(setOf("style_accent", "newsletter_sections"), noTitle.toSet())
    }

    @Test
    fun `media segment rows share the enum's core_segment resources`() {
        // The screen side is enum-driven (MediaSegmentType) and stays there;
        // the fused rows mirror the SAME accessors SegmentNames.kt maps per
        // enum entry, so the resource still has exactly one declaration home
        // — the default-title fold (no explicit search title) is how they
        // share it.
        val segmentRows = PlaybackRows.all.filter { it.id.startsWith("media_segment_") }
        assertEquals(MediaSegmentType.entries.size, segmentRows.size)
        segmentRows.forEach { row ->
            val type = requireNotNull(
                MediaSegmentType.entries.firstOrNull { "media_segment_${it.name.lowercase()}" == row.id },
            ) { "${row.id} does not follow the media_segment_<enum> naming" }
            assertNull(row.searchTitleRes, "${row.id} must default its search title to the enum's title")
            assertEquals(segmentTitleRes(type), row.titleRes, "${row.id} must reuse the enum's title accessor")
        }
    }

    /** The SegmentNames.kt title mapping, mirrored for the non-composable assertion above. */
    private fun segmentTitleRes(type: MediaSegmentType): StringResource = when (type) {
        MediaSegmentType.INTRO -> CoreUiRes.string.core_segment_intro
        MediaSegmentType.OUTRO -> CoreUiRes.string.core_segment_outro
        MediaSegmentType.PREVIEW -> CoreUiRes.string.core_segment_preview
        MediaSegmentType.RECAP -> CoreUiRes.string.core_segment_recap
        MediaSegmentType.COMMERCIAL -> CoreUiRes.string.core_segment_commercial
        MediaSegmentType.UNKNOWN -> CoreUiRes.string.core_segment_unknown
    }

    @Test
    fun `androidOnly projections stay android-only and desktop rows stay desktop-only`() {
        // The Vlc/Exo engine branches and notifications: items tagged
        // ANDROID-only (the retired list-level androidOnly preserved through
        // the projection as the rows' platform tags); the desktop mpv
        // render/audio-device rows keep their per-row DESKTOP tag.
        listOf(VlcEngineSearchItems, ExoPlayerEngineSearchItems, NotificationSettingsSearchItems).forEach { items ->
            assertTrue(items.all { it.platforms == setOf(PlatformKind.ANDROID) })
        }
        assertTrue(
            MpvEngineSearchItems.filter { it.id in setOf(PlaybackRows.MpvAudioDevice.id, PlaybackRows.MpvAudioExclusive.id, PlaybackRows.MpvAudioMode.id) }
                .all { it.platforms == setOf(PlatformKind.DESKTOP) },
        )
    }

    // ── 5. The entrance single-homing ───────────────────────────────────

    /**
     * Every converted domain's entrance: the section spliced exactly once, at
     * the pinned render position (the retired literal numbering — the same
     * pairs `SettingsEntranceStepsTest` pins for the whole list).
     */
    @Test
    fun `every converted domain's entrance section is the one declaration driving emission and step index`() {
        listOf(
            HomeEntrance to (6 to 6),
            AppearanceEntrance to (7 to 7),
            PlaybackEntrance to (8 to 8),
            AudioEntrance to (9 to 9),
            LanguageEntrance to (10 to 10),
            NotificationEntrance to (11 to 11),
            StorageEntrance to (12 to 12),
            SecurityEntrance to (13 to 13),
            BackupEntrance to (15 to 15),
            // HEAD's SettingsEntranceStepsTest literals: item_integrations
            // (20,21), item_about (21,22) — the tv side spans the whatsnew
            // slot that phone lacks.
            IntegrationsEntrance to (20 to 21),
            AboutEntrance to (21 to 22),
        ).forEach { (entrance, expectedSteps) ->
            val occurrences = SETTINGS_ENTRANCE_SECTIONS.count { it.key == entrance.key }
            assertEquals(1, occurrences, "${entrance.key} is missing from (or duplicated in) SETTINGS_ENTRANCE_SECTIONS")
            val index = SETTINGS_ENTRANCE_SECTIONS.indexOfFirst { it.key == entrance.key }
            assertEquals(expectedSteps.second, index, "the ${entrance.key} entrance slot moved")
            val steps = settingsEntranceStep(entrance.key) ?: fail("section '${entrance.key}' not derivable")
            assertEquals(expectedSteps, steps.phone to steps.tv, "the ${entrance.key} entrance steps drifted")
            // every face the root screen's emission needs is declared, not
            // hand-typed beside the call site.
            assertTrue(entrance.rowId.isNotEmpty(), "${entrance.key} declares no deep-link row id")
        }
    }

    @Test
    fun `every datastore spec entry is claimed by a fused row or the experimental holder`() {
        // Domain-agnostic totality: a spec entry the stores declare but no
        // row claims would silently vanish from search. The per-group
        // specEntriesFor filter cannot check this (each group receives the
        // DOMAIN-wide spec list and legitimately ignores sibling groups'
        // entries), so it is pinned here instead — union vs union.
        val claimed = (
            vocabularies.flatten().map { it.id } + listOf(
                ExperimentalSettingsIds.EXPERIMENTAL,
                ExperimentalSettingsIds.HOME_CARD_CLIPPING,
                ExperimentalSettingsIds.MEDIA_CARD_PEEK,
                ExperimentalSettingsIds.DIRECT_ARR_INTEGRATION,
                ExperimentalSettingsIds.ARR_SETTINGS,
            )
            ).toSet()
        val specIds = (
            VideoPlayerPreferenceSpecs.searchEntries +
                PlaybackPreferenceSpecs.searchEntries +
                HomeDiscoveryPreferenceSpecs.searchEntries +
                AppearancePreferenceSpecs.searchEntries +
                ScreensaverPreferenceSpecs.searchEntries +
                ExperimentalPreferenceSpecs.searchEntries +
                ExperimentalPreferenceSpecs.appearanceSearchEntries
            ).map { it.id }
        val unclaimed = specIds.toSet() - claimed
        assertEquals(emptySet(), unclaimed, "spec entries no fused row claims — they never surface in search")
        // …and no spec id is double-declared across the domain stores.
        assertEquals(specIds.size, specIds.toSet().size, "a spec id is declared twice across the stores")
    }
}
