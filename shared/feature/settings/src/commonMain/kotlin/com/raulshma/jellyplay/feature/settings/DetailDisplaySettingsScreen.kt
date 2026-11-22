package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DetailBackdropResolution
import com.raulshma.jellyplay.core.model.DetailChapterCardSize
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.DetailStaggerStyle
import com.raulshma.jellyplay.core.model.FilmographySort
import com.raulshma.jellyplay.core.model.PromotedRailSource
import com.raulshma.jellyplay.core.model.SmartPlayPriority
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_entry_detail
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_detail_tuning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_airing_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_cast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_chapters
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_franchise
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_media_info
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_overview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_reviews
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_seasons
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_similar
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_up_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order_default

/**
 * The detail section-order editor's rows: the persisted keys ARE the detail
 * feature's section-kind names (the `detail_section_order` CSV's vocabulary),
 * declared here as (key, label) descriptors — the NAV_ITEMS pattern. The
 * consumption side reconciles the stored permutation against its own known
 * list, so this UI vocabulary drifting from the feature's enum degrades to
 * "those tokens ignored" — never a broken detail body. The pinned chrome
 * sections (header / action row / book reading card) are deliberately absent:
 * they are not reorderable.
 */
internal data class DetailSectionOrderDescriptor(
    val key: String,
    val labelRes: StringResource,
)

internal val DETAIL_SECTION_ORDER_SECTIONS: List<DetailSectionOrderDescriptor> = listOf(
    DetailSectionOrderDescriptor("MEDIA_INFO", Res.string.settings_detail_section_media_info),
    DetailSectionOrderDescriptor("OVERVIEW", Res.string.settings_detail_section_overview),
    DetailSectionOrderDescriptor("CHAPTERS_OR_TOC", Res.string.settings_detail_section_chapters),
    DetailSectionOrderDescriptor("UP_NEXT", Res.string.settings_detail_section_up_next),
    DetailSectionOrderDescriptor("UPCOMING_EPISODES", Res.string.settings_detail_section_airing_next),
    DetailSectionOrderDescriptor("SEASONS", Res.string.settings_detail_section_seasons),
    DetailSectionOrderDescriptor("MORE_IN_COLLECTION", Res.string.settings_detail_section_franchise),
    DetailSectionOrderDescriptor("CAST", Res.string.settings_detail_section_cast),
    DetailSectionOrderDescriptor("MORE_LIKE_THIS", Res.string.settings_detail_section_similar),
    DetailSectionOrderDescriptor("TMDB_REVIEWS", Res.string.settings_detail_section_reviews),
)

/**
 * The stream-fallback language picker's common 3-letter codes (ISO 639-2/B
 * where distinct): the same vocabulary the store setter validates
 * (lowercased 3-letter tokens only, else the "eng" default).
 */
internal val STREAM_FALLBACK_LANGUAGE_CODES: List<String> = listOf(
    "und", "eng", "jpn", "ger", "fre", "spa", "ita", "por", "rus", "kor", "chi", "fra",
)

/**
 * The Detail screen leaf's ONE ordered slot declaration (ADR-0009 amendment;
 * ADR-0017's appearance split narrowed by the phase-5 reclassification): the
 * visible detail group, then the advanced-gated "Density & limits" trailing
 * group — the tuning-shaped detail rows moved off the visible group whole
 * (the Theme leaf's trailing-expert-group shape). The screen attaches its
 * hand-written bodies via [withContent]; the contract test pins the slot
 * parity against this same declaration.
 */
internal val DetailDisplaySettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.appearanceDetail.asScreenGroup(),
    SettingsScreenGroups.appearanceDetailTuning.asScreenGroup(advancedOnly = true),
)

/**
 * The `appearance.detail` group (ADR-0017's split of the former "Library &
 * Cards" group, narrowed by the phase-5 reclassification): the detail
 * behavior/display rows — the filmography pair, the spoiler/chip/toggle
 * cluster, the section-order editor, and the play/menu behavior picks — in
 * the emission order they always had. The density/limit fine-tuning moved to
 * [AppearanceDetailTuningGroup] below.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppearanceDetailGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.Photo,
        title = stringResource(Res.string.settings_appearance_entry_detail),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId == null || highlightSettingId in SettingsScreenGroups.appearanceDetail.itemIdSet,
    ) {
        // The declared visible detail rows plus the section-order editor's
        // reorderable rows (screen-local) — the admission total beside
        // SettingsScreenGroups.
        SettingsItemList(total = appearanceDetailScreenRowTotal()) {

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowFilmographyRoles),
            title = rowTitle(AppearanceRows.ShowFilmographyRoles),
            subtitle = rowSubtitle(AppearanceRows.ShowFilmographyRoles),
            checked = preferences.showFilmographyRoles,
            highlighted = highlightSettingId == AppearanceRows.ShowFilmographyRoles.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowFilmographyRoles(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowFrequentCostars),
            title = rowTitle(AppearanceRows.ShowFrequentCostars),
            subtitle = rowSubtitle(AppearanceRows.ShowFrequentCostars),
            checked = preferences.showFrequentCostars,
            highlighted = highlightSettingId == AppearanceRows.ShowFrequentCostars.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowFrequentCostars(it) } },
        )

        // ── The detail-screen display toggles. ──────────────────────────────

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.HideWatchedEpisodes),
            title = rowTitle(AppearanceRows.HideWatchedEpisodes),
            subtitle = rowSubtitle(AppearanceRows.HideWatchedEpisodes),
            checked = preferences.hideWatchedEpisodes,
            highlighted = highlightSettingId == AppearanceRows.HideWatchedEpisodes.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailHideWatchedEpisodes(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ConfirmMarkPlayed),
            title = rowTitle(AppearanceRows.ConfirmMarkPlayed),
            subtitle = rowSubtitle(AppearanceRows.ConfirmMarkPlayed),
            checked = preferences.confirmMarkPlayed,
            highlighted = highlightSettingId == AppearanceRows.ConfirmMarkPlayed.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailConfirmMarkPlayed(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.BackdropParallax),
            title = rowTitle(AppearanceRows.BackdropParallax),
            subtitle = rowSubtitle(AppearanceRows.BackdropParallax),
            checked = preferences.backdropParallax,
            highlighted = highlightSettingId == AppearanceRows.BackdropParallax.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailBackdropParallax(it) } },
        )

        val overviewCollapseLinesTitle = rowTitle(AppearanceRows.OverviewCollapseLines)
        SettingListItem(
            icon = rowIcon(AppearanceRows.OverviewCollapseLines),
            title = overviewCollapseLinesTitle,
            subtitle = rowSubtitle(AppearanceRows.OverviewCollapseLines),
            trailingText = preferences.overviewCollapseLines.toString(),
            highlighted = highlightSettingId == AppearanceRows.OverviewCollapseLines.id,
            onClick = {
                // The shipped picker vocabulary: 3/4/5/6/8 (the sparse step
                // at the top; the setter clamps to the row's full range).
                activePicker.value = PickerState.List(
                    title = overviewCollapseLinesTitle,
                    items = listOf(3, 4, 5, 6, 8),
                    label = { it.toString() },
                    isSelected = { it == preferences.overviewCollapseLines },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailOverviewCollapseLines(it) } },
                )
            },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowStudioChips),
            title = rowTitle(AppearanceRows.ShowStudioChips),
            subtitle = rowSubtitle(AppearanceRows.ShowStudioChips),
            checked = preferences.showStudioChips,
            highlighted = highlightSettingId == AppearanceRows.ShowStudioChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowStudioChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowGenreChips),
            title = rowTitle(AppearanceRows.ShowGenreChips),
            subtitle = rowSubtitle(AppearanceRows.ShowGenreChips),
            checked = preferences.showGenreChips,
            highlighted = highlightSettingId == AppearanceRows.ShowGenreChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowGenreChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowTagChips),
            title = rowTitle(AppearanceRows.ShowTagChips),
            subtitle = rowSubtitle(AppearanceRows.ShowTagChips),
            checked = preferences.showTagChips,
            highlighted = highlightSettingId == AppearanceRows.ShowTagChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowTagChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowSkipChips),
            title = rowTitle(AppearanceRows.ShowSkipChips),
            subtitle = rowSubtitle(AppearanceRows.ShowSkipChips),
            checked = preferences.showSkipChips,
            highlighted = highlightSettingId == AppearanceRows.ShowSkipChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowSkipChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowTrailerBadges),
            title = rowTitle(AppearanceRows.ShowTrailerBadges),
            subtitle = rowSubtitle(AppearanceRows.ShowTrailerBadges),
            checked = preferences.showTrailerBadges,
            highlighted = highlightSettingId == AppearanceRows.ShowTrailerBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowTrailerBadges(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.PersistSectionHides),
            title = rowTitle(AppearanceRows.PersistSectionHides),
            subtitle = rowSubtitle(AppearanceRows.PersistSectionHides),
            checked = preferences.persistSectionHides,
            highlighted = highlightSettingId == AppearanceRows.PersistSectionHides.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailPersistSectionHides(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.SeasonTabsArtwork),
            title = rowTitle(AppearanceRows.SeasonTabsArtwork),
            subtitle = rowSubtitle(AppearanceRows.SeasonTabsArtwork),
            checked = preferences.seasonTabsArtwork,
            highlighted = highlightSettingId == AppearanceRows.SeasonTabsArtwork.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailSeasonTabsArtwork(it) } },
        )

        // ── The play/menu behavior picks (mainstream; the density/limit
        // tuning lives in the advanced group below). ────────────────────────

        val smartPlayPriorityTitle = rowTitle(AppearanceRows.SmartPlayPriority)
        SettingListItem(
            icon = rowIcon(AppearanceRows.SmartPlayPriority),
            title = smartPlayPriorityTitle,
            subtitle = rowSubtitle(AppearanceRows.SmartPlayPriority),
            trailingText = preferences.smartPlayPriority.displayName,
            highlighted = highlightSettingId == AppearanceRows.SmartPlayPriority.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = smartPlayPriorityTitle,
                    items = SmartPlayPriority.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.smartPlayPriority },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailSmartPlayPriority(it) } },
                )
            },
        )

        val streamFallbackLanguageTitle = rowTitle(AppearanceRows.StreamFallbackLanguage)
        SettingListItem(
            icon = rowIcon(AppearanceRows.StreamFallbackLanguage),
            title = streamFallbackLanguageTitle,
            subtitle = rowSubtitle(AppearanceRows.StreamFallbackLanguage),
            trailingText = preferences.streamFallbackLanguage.uppercase(),
            highlighted = highlightSettingId == AppearanceRows.StreamFallbackLanguage.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = streamFallbackLanguageTitle,
                    // The common 3-letter codes (the store setter lowercases
                    // and accepts only 3-letter tokens, else resets to "eng").
                    items = STREAM_FALLBACK_LANGUAGE_CODES,
                    label = { it.uppercase() },
                    isSelected = { it == preferences.streamFallbackLanguage },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailStreamFallbackLanguage(it) } },
                )
            },
        )

        val promotedRailSourceTitle = rowTitle(AppearanceRows.PromotedRailSource)
        SettingListItem(
            icon = rowIcon(AppearanceRows.PromotedRailSource),
            title = promotedRailSourceTitle,
            subtitle = rowSubtitle(AppearanceRows.PromotedRailSource),
            trailingText = preferences.promotedRailSource.displayName,
            highlighted = highlightSettingId == AppearanceRows.PromotedRailSource.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = promotedRailSourceTitle,
                    items = PromotedRailSource.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.promotedRailSource },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailPromotedRailSource(it) } },
                )
            },
        )

        // ── Detail Section Order: the declared header row + the reorderable
        // content-section rows (the nav-customization group's reorder
        // grammar: a shared reorderable holder mirrors the stored order,
        // reconciled against the known sections — unknown dropped, missing
        // appended canonically — and persists the joined CSV). The stored
        // vocabulary is the detail feature's section-kind names; the
        // consumption side reconciles, so a settings-side drift can never
        // strand a stale stored value. ──────────────────────────────────────
        val sectionOrderTitle = rowTitle(AppearanceRows.SectionOrder)
        val sectionOrderCustomLabel = stringResource(Res.string.settings_section_order_custom)
        val sectionOrderDefaultLabel = stringResource(Res.string.settings_section_order_default)
        val sectionOrderLabels = DETAIL_SECTION_ORDER_SECTIONS.associate { it.key to stringResource(it.labelRes) }
        SettingListItem(
            icon = rowIcon(AppearanceRows.SectionOrder),
            title = sectionOrderTitle,
            subtitle = rowSubtitle(AppearanceRows.SectionOrder),
            trailingText = if (preferences.sectionOrder.isEmpty()) sectionOrderDefaultLabel else sectionOrderCustomLabel,
            highlighted = highlightSettingId == AppearanceRows.SectionOrder.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = sectionOrderTitle,
                    items = listOf(sectionOrderDefaultLabel, sectionOrderCustomLabel),
                    label = { it },
                    isSelected = { it == (if (preferences.sectionOrder.isEmpty()) sectionOrderDefaultLabel else sectionOrderCustomLabel) },
                    onSelect = { selected ->
                        // Picking "Default" clears the stored permutation;
                        // "Custom" seeds the editor's order from the current
                        // known order (kept canonical until a row is dragged).
                        viewModel.edit { scope ->
                            scope.library.setDetailSectionOrder(
                                if (selected == sectionOrderDefaultLabel) {
                                    emptyList()
                                } else {
                                    preferences.sectionOrder.ifEmpty {
                                        DETAIL_SECTION_ORDER_SECTIONS.map { it.key }
                                    }
                                },
                            )
                        }
                    },
                )
            },
        )

        val sectionOrderEditor = rememberReorderableOrderedList(
            storedOrder = preferences.sectionOrder,
            knownOrder = DETAIL_SECTION_ORDER_SECTIONS.map { it.key },
            onPersist = { order -> viewModel.edit { scope -> scope.library.setDetailSectionOrder(order) } },
        )

        sectionOrderEditor.items.forEachIndexed { index, key ->
            val descriptor = DETAIL_SECTION_ORDER_SECTIONS.first { it.key == key }
            SettingReorderableItem(
                icon = Tabler.Outline.LayoutList,
                title = sectionOrderLabels.getValue(key),
                index = index,
                count = sectionOrderEditor.items.size,
                modifier = Modifier.onSizeChanged { sectionOrderEditor.recordHeight(key, it.height) },
                onDrag = { delta -> sectionOrderEditor.onDrag(key, delta) },
                onDragStart = { sectionOrderEditor.onDragStart(key) },
                onDragEnd = sectionOrderEditor::onDragEnd,
            )
        }
        }
    }
}

/**
 * The Detail screen leaf's advanced-gated "Density & limits" group (the
 * phase-5 reclassification, ADR-0017 decision 6): the tuning-shaped detail
 * rows moved off the visible group verbatim — the cast/crew geometry pair,
 * the grid density, the rail/row limits, the decode resolution, the chapter
 * card size, the reviews preview lines, the filmography sort, the franchise
 * limits, the stagger cascade and the episode-overview preview. The shell
 * only composes the slot behind the advanced toggle, so the total reads the
 * advanced leg directly (every row carries its declared advanced base).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceDetailTuningGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.Adjustments,
        title = stringResource(Res.string.settings_appearance_detail_tuning),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceDetailTuning.itemIdSet,
    ) {
        // The whole declared tuning set — the slot only composes behind the
        // advanced toggle (the shell's whole-group gate), so the advanced leg
        // IS the composed state.
        SettingsItemList(total = rowTotalFor(SettingsScreenGroups.appearanceDetailTuning, RowAdmissionFlags(showAdvanced = true))) {

        // The two DetailDensity pickers (cast rail geometry + Cast & Crew
        // grid density) and the bounded int pickers, following the theme
        // group's enum-picker shape.
        val castItemSizeTitle = rowTitle(AppearanceRows.CastItemSize)
        SettingListItem(
            icon = rowIcon(AppearanceRows.CastItemSize),
            title = castItemSizeTitle,
            subtitle = rowSubtitle(AppearanceRows.CastItemSize),
            trailingText = preferences.castItemSize.displayName,
            highlighted = highlightSettingId == AppearanceRows.CastItemSize.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = castItemSizeTitle,
                    items = DetailDensity.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.castItemSize },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailCastItemSize(it) } },
                )
            },
        )

        val peopleGridDensityTitle = rowTitle(AppearanceRows.PeopleGridDensity)
        SettingListItem(
            icon = rowIcon(AppearanceRows.PeopleGridDensity),
            title = peopleGridDensityTitle,
            subtitle = rowSubtitle(AppearanceRows.PeopleGridDensity),
            trailingText = preferences.peopleGridDensity.displayName,
            highlighted = highlightSettingId == AppearanceRows.PeopleGridDensity.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = peopleGridDensityTitle,
                    items = DetailDensity.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.peopleGridDensity },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailPeopleGridDensity(it) } },
                )
            },
        )

        val castSeeAllLimitTitle = rowTitle(AppearanceRows.CastSeeAllLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.CastSeeAllLimit),
            title = castSeeAllLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.CastSeeAllLimit),
            trailingText = preferences.castSeeAllLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.CastSeeAllLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = castSeeAllLimitTitle,
                    // The store setter clamps to 6..24 (the row's declared
                    // bounds) — the picker offers exactly that range.
                    items = (6..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.castSeeAllLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailCastSeeAllLimit(it) } },
                )
            },
        )

        val similarLimitTitle = rowTitle(AppearanceRows.SimilarLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.SimilarLimit),
            title = similarLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.SimilarLimit),
            trailingText = preferences.similarItemsLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.SimilarLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = similarLimitTitle,
                    // The store setter clamps to 6..24 (the row's declared bounds).
                    items = (6..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.similarItemsLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailSimilarLimit(it) } },
                )
            },
        )

        val upcomingLimitTitle = rowTitle(AppearanceRows.UpcomingLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.UpcomingLimit),
            title = upcomingLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.UpcomingLimit),
            trailingText = preferences.upcomingEpisodesLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.UpcomingLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = upcomingLimitTitle,
                    // The store setter clamps to 4..24 (the row's declared bounds).
                    items = (4..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.upcomingEpisodesLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailUpcomingLimit(it) } },
                )
            },
        )

        // ── The wave-2 tuning picks. ────────────────────────────────────────

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.EpisodeOverviewPreview),
            title = rowTitle(AppearanceRows.EpisodeOverviewPreview),
            subtitle = rowSubtitle(AppearanceRows.EpisodeOverviewPreview),
            checked = preferences.episodeOverviewPreview,
            highlighted = highlightSettingId == AppearanceRows.EpisodeOverviewPreview.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailEpisodeOverviewPreview(it) } },
        )

        val staggerStyleTitle = rowTitle(AppearanceRows.StaggerStyle)
        SettingListItem(
            icon = rowIcon(AppearanceRows.StaggerStyle),
            title = staggerStyleTitle,
            subtitle = rowSubtitle(AppearanceRows.StaggerStyle),
            trailingText = preferences.staggerStyle.displayName,
            highlighted = highlightSettingId == AppearanceRows.StaggerStyle.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = staggerStyleTitle,
                    items = DetailStaggerStyle.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.staggerStyle },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailStaggerStyle(it) } },
                )
            },
        )

        val backdropResolutionTitle = rowTitle(AppearanceRows.BackdropResolution)
        SettingListItem(
            icon = rowIcon(AppearanceRows.BackdropResolution),
            title = backdropResolutionTitle,
            subtitle = rowSubtitle(AppearanceRows.BackdropResolution),
            trailingText = preferences.backdropResolution.displayName,
            highlighted = highlightSettingId == AppearanceRows.BackdropResolution.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = backdropResolutionTitle,
                    items = DetailBackdropResolution.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.backdropResolution },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailBackdropResolution(it) } },
                )
            },
        )

        val chapterCardSizeTitle = rowTitle(AppearanceRows.ChapterCardSize)
        SettingListItem(
            icon = rowIcon(AppearanceRows.ChapterCardSize),
            title = chapterCardSizeTitle,
            subtitle = rowSubtitle(AppearanceRows.ChapterCardSize),
            trailingText = preferences.chapterCardSize.displayName,
            highlighted = highlightSettingId == AppearanceRows.ChapterCardSize.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = chapterCardSizeTitle,
                    items = DetailChapterCardSize.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.chapterCardSize },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailChapterCardSize(it) } },
                )
            },
        )

        val reviewsPreviewLinesTitle = rowTitle(AppearanceRows.ReviewsPreviewLines)
        SettingListItem(
            icon = rowIcon(AppearanceRows.ReviewsPreviewLines),
            title = reviewsPreviewLinesTitle,
            subtitle = rowSubtitle(AppearanceRows.ReviewsPreviewLines),
            trailingText = preferences.reviewsPreviewLines.toString(),
            highlighted = highlightSettingId == AppearanceRows.ReviewsPreviewLines.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = reviewsPreviewLinesTitle,
                    // The store setter clamps to 2..12 (the row's declared bounds).
                    items = (2..12).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.reviewsPreviewLines },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailReviewsPreviewLines(it) } },
                )
            },
        )

        val filmographySortTitle = rowTitle(AppearanceRows.FilmographySort)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FilmographySort),
            title = filmographySortTitle,
            subtitle = rowSubtitle(AppearanceRows.FilmographySort),
            trailingText = preferences.filmographySort.displayName,
            highlighted = highlightSettingId == AppearanceRows.FilmographySort.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = filmographySortTitle,
                    items = FilmographySort.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.filmographySort },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFilmographySort(it) } },
                )
            },
        )

        val franchiseBoxLimitTitle = rowTitle(AppearanceRows.FranchiseBoxLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FranchiseBoxLimit),
            title = franchiseBoxLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.FranchiseBoxLimit),
            trailingText = preferences.franchiseBoxLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.FranchiseBoxLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = franchiseBoxLimitTitle,
                    // The store setter clamps to 1..5 (the row's declared bounds).
                    items = (1..5).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.franchiseBoxLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFranchiseBoxLimit(it) } },
                )
            },
        )

        val franchiseMemberLimitTitle = rowTitle(AppearanceRows.FranchiseMemberLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FranchiseMemberLimit),
            title = franchiseMemberLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.FranchiseMemberLimit),
            trailingText = preferences.franchiseMemberLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.FranchiseMemberLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = franchiseMemberLimitTitle,
                    // The store setter clamps to 10..100 (the row's declared bounds).
                    items = (10..100).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.franchiseMemberLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFranchiseMemberLimit(it) } },
                )
            },
        )
        }
    }
}

/**
 * The Detail screen LEAF (ADR-0017's appearance split): the visible detail
 * rows plus the advanced "Density & limits" trailing group behind the hub's
 * Detail screen entrance. The visible group carries the behavior/display
 * rows exactly as on the former combined screen; the density/limit
 * fine-tuning hides behind the leaf's advanced toggle (the phase-5
 * reclassification), so the leaf now carries the toggle and the derived
 * hidden hint.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailDisplaySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()

    val detailGroups = DetailDisplaySettingsScreenSlots.withContent(
        bodies = listOf(
            { activePicker ->
                AppearanceDetailGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    activePicker = activePicker,
                )
            },
            { activePicker ->
                AppearanceDetailTuningGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    activePicker = activePicker,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_entry_detail),
        onBack = onBack,
        focusTag = "appearance_detail_init",
        highlightSettingId = highlightSettingId,
        groups = detailGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            rowFlags = RowAdmissionFlags(showAdvanced = false, isTv = LocalTvMode.current),
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
    )
}
