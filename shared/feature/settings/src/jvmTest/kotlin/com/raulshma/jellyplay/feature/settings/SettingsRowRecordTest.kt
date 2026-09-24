package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural ratchet for the row records (candidate B1 "settings row-twin
 * records"): every catalog row's identity lives in ONE [SettingsRowRecord]
 * naming the row id, the settings-screen title resource (`titleRes`) and the
 * search faces (`searchTitleRes`/`searchSubtitleRes`), and every
 * `*SearchItems` list is the pure [SettingsRowRecord.toSearchItems] projection
 * of its record list — the catalog adds nothing but the shared `ss_cat_*`
 * category resource.
 *
 * As in `SettingsSearchCatalogTest`, resource resolvability is
 * compile-time-guaranteed by the generated accessors (the JVM test never
 * resolves strings), so what is pinned here is the STRUCTURE: the record
 * table covers exactly the declared ids, the projections are faithful, and
 * the no-screen-title exception set is exactly the documented one.
 */
class SettingsRowRecordTest {

    /** Every (record list, derived item list) pair — one line per declaration list. */
    private val pairs: List<Pair<List<SettingsRowRecord>, List<SettingsSearchItem>>> = listOf(
        AccountRowRecords to AccountSearchItems,
        IntegrationsRowRecords to IntegrationsSearchItems,
        ActivityInsightsRowRecords to ActivityInsightsSearchItems,
        SystemRowRecords to SystemSearchItems,
        HomeDisplayRowRecords to HomeDisplaySearchItems,
        HomeNextUpRowRecords to HomeNextUpSearchItems,
        HomeLayoutRowRecords to HomeLayoutSearchItems,
        AppearanceThemeRowRecords to AppearanceThemeSearchItems,
        AppearanceNavigationRowRecords to AppearanceNavigationSearchItems,
        AppearanceLibraryRowRecords to AppearanceLibrarySearchItems,
        AppearancePerformanceRowRecords to AppearancePerformanceSearchItems,
        AppearanceEyeCareRowRecords to AppearanceEyeCareSearchItems,
        AppearanceNewsletterRowRecords to AppearanceNewsletterSearchItems,
        PlaybackSettingsRowRecords to PlaybackSettingsSearchItems,
        PlaybackAdvancedVideoRowRecords to PlaybackAdvancedVideoSearchItems,
        MpvEngineRowRecords to MpvEngineSearchItems,
        VlcEngineRowRecords to VlcEngineSearchItems,
        ExoPlayerEngineRowRecords to ExoPlayerEngineSearchItems,
        SyncPlayRowRecords to SyncPlaySearchItems,
        CastingRowRecords to CastingSearchItems,
        LiveTvRowRecords to LiveTvSearchItems,
        AudioSettingsRowRecords to AudioSettingsSearchItems,
        AudioCacheRowRecords to AudioCacheSearchItems,
        LanguageSettingsRowRecords to LanguageSettingsSearchItems,
        TrackSelectionRowRecords to TrackSelectionSearchItems,
        NotificationSettingsRowRecords to NotificationSettingsSearchItems,
        StorageCacheRowRecords to StorageCacheSearchItems,
        StorageNetworkRowRecords to StorageNetworkSearchItems,
        StorageDownloadsRowRecords to StorageDownloadsSearchItems,
        SecuritySettingsRowRecords to SecuritySettingsSearchItems,
        BackupSettingsRowRecords to BackupSettingsSearchItems,
        AboutRowRecords to AboutSearchItems,
    )

    @Test
    fun `every record list projects to exactly its derived item list`() {
        pairs.forEach { (records, items) ->
            assertEquals(records.size, items.size)
            records.zip(items).forEach { (rec, item) ->
                assertEquals(rec.id, item.id)
                assertIs<StringResource>(item.titleRes)
                assertEquals(rec.searchTitleRes, item.titleRes, "search title drift for ${rec.id}")
                assertEquals(rec.searchSubtitleRes, item.subtitleRes, "search subtitle drift for ${rec.id}")
                assertEquals(rec.keywords, item.keywords, "keywords drift for ${rec.id}")
                assertEquals(rec.route, item.route, "route drift for ${rec.id}")
                assertEquals(rec.icon, item.icon, "icon drift for ${rec.id}")
                assertEquals(rec.isAdvanced, item.isAdvanced, "isAdvanced drift for ${rec.id}")
                // the three androidOnly projection lists narrow the record's
                // platform tag at the item level — narrowing is allowed, widening is not
                assertTrue(
                    rec.platforms.containsAll(item.platforms),
                    "platform widening for ${rec.id}",
                )
            }
        }
    }

    @Test
    fun `the record registry covers the record lists exactly and single-valued`() {
        val allIds = SettingsRowRecords.all.map { it.id }
        val pairIds = pairs.flatMap { (records, _) -> records.map { it.id } }
        assertEquals(pairIds.toSet(), allIds.toSet(), "the registry is not exactly the union of the record lists")
        assertEquals(allIds.size, allIds.toSet().size, "duplicate record ids in the registry")
        assertEquals(SettingsRowRecords.all.size, SettingsRowRecords.byId.size, "byId collapsed a duplicate id")
    }

    @Test
    fun `records span the declared catalog minus the experimental binding-derived screen`() {
        // 271 hand-declared records; the experimental screen's 5 rows stay on
        // the ExperimentalPreferenceSpecs derivation (the Stage-A pilot) and
        // are the documented records exception.
        val experimentalIds = setOf(
            ExperimentalSettingsIds.EXPERIMENTAL,
            ExperimentalSettingsIds.HOME_CARD_CLIPPING,
            ExperimentalSettingsIds.MEDIA_CARD_PEEK,
            ExperimentalSettingsIds.DIRECT_ARR_INTEGRATION,
            ExperimentalSettingsIds.ARR_SETTINGS,
        )
        val recordIds = SettingsRowRecords.all.map { it.id }.toSet()
        val catalogIds = SettingsSearchCatalog.items.map { it.id }.toSet()
        assertEquals(recordIds, catalogIds - experimentalIds)
        assertEquals(5, ExperimentalSettingsSearchItems.size)
    }

    @Test
    fun `titleRes is null exactly for the documented no-screen-title rows`() {
        val nullTitle = SettingsRowRecords.all.filter { it.titleRes == null }.map { it.id }
        // style_accent: the hand-built VariantAccentPicker (titled per-variant
        // by core_ui_variant_accent_title) — no single screen title resource.
        // newsletter_sections: the runtime-reorderable rows render
        // NewsletterSectionType.labelRes — enum-driven, like media segments.
        assertEquals(setOf("style_accent", "newsletter_sections"), nullTitle.toSet())
    }

    @Test
    fun `rowIcon projects the record icon`() {
        // The screen-side icon resolver must be the pure record projection:
        // rowIcon(id) === record.icon for every declared row, so a screen row
        // adopting it can never drift from its record's icon field.
        SettingsRowRecords.all.forEach { rec ->
            assertEquals(rec.icon, rowIcon(rec.id), "rowIcon drift for ${rec.id}")
        }
    }

    @Test
    fun `media segment records share the enum's core_segment resources`() {
        // The screen side is enum-driven (MediaSegmentType) and stays there;
        // the records mirror the SAME accessors the enum names, so the
        // resource still has exactly one declaration home.
        val segmentRecords = SettingsRowRecords.all.filter { it.id.startsWith("media_segment_") }
        assertEquals(MediaSegmentType.entries.size, segmentRecords.size)
        segmentRecords.forEach { rec ->
            assertEquals(rec.titleRes, rec.searchTitleRes, "${rec.id} must reuse the enum's title accessor")
        }
    }

    @Test
    fun `androidOnly projections stay android-only and desktop rows stay desktop-only`() {
        // Vlc/Exo engine configs and notifications: items tagged ANDROID-only
        // (the pre-existing list-level androidOnly preserved through the
        // projection); the desktop mpv render/audio-device rows keep their
        // per-record DESKTOP tag.
        listOf(VlcEngineSearchItems, ExoPlayerEngineSearchItems, NotificationSettingsSearchItems).forEach { items ->
            assertTrue(items.all { it.platforms == setOf(PlatformKind.ANDROID) })
        }
        assertTrue(
            MpvEngineSearchItems.filter { it.id in setOf(PlaybackSettingsIds.MPV_AUDIO_DEVICE, PlaybackSettingsIds.MPV_AUDIO_EXCLUSIVE, PlaybackSettingsIds.MPV_AUDIO_MODE) }
                .all { it.platforms == setOf(PlatformKind.DESKTOP) },
        )
        assertNull(
            SettingsRowRecords.byId.getValue("style_accent").titleRes,
            "style_accent keeps its no-screen-title exception",
        )
    }
}
