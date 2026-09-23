package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronUp
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.filterableMediaTypes
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayNamePlural
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * The filter-sheet sections a caller wants rendered, in canonical render
 * order. The enum's declaration order IS the layout order both shipped
 * sheets render (library: sort → media type → status + resumable/downloaded
 * → genres → years → tags → rating; search: the same order minus the four
 * library-only sections), so rendering iterates [FilterSection.entries]
 * filtered by the caller's set — per-screen section order can never drift.
 *
 * `PLAYED_STATUS` and `AVAILABILITY` render as one visual block (the status
 * `ButtonGroup` with the resumable/downloaded chips directly under it,
 * exactly the library sheet's historical grouping); `AVAILABILITY` alone
 * would render as a label-less chip row.
 */
enum class FilterSection {
    SORT,
    MEDIA_TYPE,
    PLAYED_STATUS,
    AVAILABILITY,
    GENRES,
    YEARS,
    TAGS,
    MIN_RATING,
}

/** The library sheet's full section set — everything, in canonical order. */
val LibraryFilterSheetSections: Set<FilterSection> = FilterSection.entries.toSet()

/**
 * The search sheet's section set — library minus sort / played status /
 * resumable+downloaded (search manages those three through its dedicated
 * per-dimension sheets).
 */
val SearchFilterSheetSections: Set<FilterSection> = setOf(
    FilterSection.MEDIA_TYPE,
    FilterSection.GENRES,
    FilterSection.YEARS,
    FilterSection.TAGS,
    FilterSection.MIN_RATING,
)

/**
 * The filter sheet's draft state — every toggleable dimension as a plain
 * value, so the toggle/reset/apply algebra lives here once and is
 * unit-testable without Compose. Both callers of [MediaFilterSheet] used to
 * hand-roll this (and search's copy had already dropped the tri-state
 * resumable/downloaded dimensions).
 *
 * The write folds mirror [LibraryFilters]'s algebra (the `withX` shape) but
 * operate on the sheet's draft view of it: media types / genres / tags keep
 * their toggle order, years/tags/rating are whole-set replaces.
 */
data class MediaFilterDraft(
    val mediaTypes: List<MediaType> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: Set<Int> = emptySet(),
    val sortBy: SortOption = SortOption.YEAR_DESC,
    val playedStatus: PlayedStatus = PlayedStatus.ALL,
    val tags: Set<String> = emptySet(),
    val minRating: Float = 0f,
    // Resumable mirrors LibraryFilters.isResumable: tri-state surfaced as a
    // single toggle (null/false = off, true = only items with a resume
    // position). Downloaded mirrors isDownloaded the same way.
    val isResumable: Boolean = false,
    val isDownloaded: Boolean = false,
) {
    /** Seeds the draft from the caller's persisted filter blob. */
    constructor(filters: LibraryFilters) : this(
        mediaTypes = filters.mediaTypes,
        genres = filters.genres,
        years = filters.years.toSet(),
        sortBy = filters.sortBy,
        playedStatus = filters.playedStatus,
        tags = filters.tags.toSet(),
        minRating = filters.minRating,
        isResumable = filters.isResumable == true,
        isDownloaded = filters.isDownloaded == true,
    )

    fun withMediaTypeToggled(mediaType: MediaType): MediaFilterDraft =
        copy(mediaTypes = if (mediaType in mediaTypes) mediaTypes - mediaType else mediaTypes + mediaType)

    fun withGenreToggled(genre: String): MediaFilterDraft =
        copy(genres = if (genre in genres) genres - genre else genres + genre)

    fun withTagToggled(tag: String): MediaFilterDraft =
        copy(tags = if (tag in tags) tags - tag else tags + tag)

    fun withYears(years: Set<Int>): MediaFilterDraft = copy(years = years)

    fun withSortBy(sortBy: SortOption): MediaFilterDraft = copy(sortBy = sortBy)

    fun withPlayedStatus(playedStatus: PlayedStatus): MediaFilterDraft = copy(playedStatus = playedStatus)

    fun withMinRating(minRating: Float): MediaFilterDraft = copy(minRating = minRating)

    fun withResumableToggled(): MediaFilterDraft = copy(isResumable = !isResumable)

    fun withDownloadedToggled(): MediaFilterDraft = copy(isDownloaded = !isDownloaded)

    /**
     * The sheet's Reset chip: every dimension back to its default EXCEPT the
     * downloaded pin. The library sheet's historical reset deliberately kept
     * the downloaded toggle on (while it's active the grid is auto-served
     * from the local offline store, so resetting it would silently un-pin
     * the screen); search never renders the toggle, so the exception is
     * invisible there.
     */
    fun clearedKeepingDownloaded(): MediaFilterDraft = copy(
        mediaTypes = emptyList(),
        genres = emptyList(),
        years = emptySet(),
        sortBy = SortOption.YEAR_DESC,
        playedStatus = PlayedStatus.ALL,
        tags = emptySet(),
        minRating = 0f,
        isResumable = false,
    )

    /**
     * The Apply fold: sections the caller renders contribute their draft
     * value; sections it doesn't render contribute the [current] value so
     * applying the sheet never silently resets dimensions managed elsewhere
     * (search's dedicated sort/status sheets; the offline auto-filter's
     * downloaded pin). The tri-states emit `null` when off. While the
     * downloaded pin is on, tags are dropped (no offline column — the same
     * rule [LibraryFilters.isDownloaded] documents).
     */
    fun appliedTo(current: LibraryFilters, sections: Set<FilterSection>): LibraryFilters = LibraryFilters(
        mediaTypes = mediaTypes,
        genres = genres,
        years = years.toList(),
        sortBy = if (FilterSection.SORT in sections) sortBy else current.sortBy,
        playedStatus = if (FilterSection.PLAYED_STATUS in sections) playedStatus else current.playedStatus,
        tags = if (FilterSection.AVAILABILITY in sections && isDownloaded) emptyList() else tags.toList(),
        minRating = minRating,
        isResumable = if (FilterSection.AVAILABILITY in sections) isResumable.takeIf { it } else current.isResumable,
        isDownloaded = if (FilterSection.AVAILABILITY in sections) isDownloaded.takeIf { it } else current.isDownloaded,
    )
}

/**
 * Every label the sheet renders, resolved by the caller so each host keeps
 * its own string resources (library_* vs search_* — they genuinely differ,
 * and search localizes "Any"/the rating suffix while the library sheet
 * hardcodes "Any"/"3.0+"). Only the labels of sections the caller enables
 * are read; the availability/collapsible strings default to "" for hosts
 * that don't render those.
 */
class MediaFilterSheetTexts(
    /** In-content header title. */
    val title: String,
    /** The reset chip. */
    val reset: String,
    /** The apply button. */
    val apply: String,
    /** The "Any" chip label (year presets and the zero rating). */
    val anyLabel: String,
    val mediaType: String,
    val genres: String,
    val years: String,
    val tags: String,
    val minRating: String,
    /** Suffix label for a non-zero rating option (e.g. "3.0+"). */
    val ratingLabel: @Composable (Float) -> String,
    val sortBy: String = "",
    val status: String = "",
    val resumable: String = "",
    val downloaded: String = "",
    /**
     * [TvSafeSheet]'s chrome title — on TV the dialog renders it above the
     * content. The library sheet passes one; search historically passed
     * none, so null preserves that.
     */
    val sheetTitle: String? = null,
    /** Chevron content descriptions for collapsible sections. */
    val collapseContentDescription: String = "",
    val expandContentDescription: String = "",
)

/**
 * The one media filter sheet behind the library and search screens — a pixel
 * verbatim unification of the two near-verbatim sheets that used to live in
 * `feature/library/components/LibraryFilterSheet.kt` and
 * `feature/search/components/SearchFilterSheet.kt` (same draft algebra, same
 * chrome; the badge fold `LibraryFilters.hasActiveFilters` already assumed
 * they moved in lockstep).
 *
 * Structure, draft state ([MediaFilterDraft]), apply/reset folds and chrome
 * are written once here; hosts contribute only
 *  - which sections render ([sections], order fixed by the enum),
 *  - their own strings ([texts]),
 *  - the two section-shape knobs the hosts historically differed on:
 *    [genreLimit] (24 vs 20 chips), [collapsibleSections] (library collapses
 *    years/tags/rating, search renders them flat), [contentScrollable]
 *    (library scrolls its body, search relies on the sheet) and
 *    [customYearRange] (library's from/to slider — a feature-module
 *    composable, slotted in so its strings stay feature-local).
 *
 * [MediaFilterSheetSectionsTest] pins the section order for both shipped
 * sets and the apply/reset algebra.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun MediaFilterSheet(
    currentFilters: LibraryFilters,
    sections: Set<FilterSection>,
    texts: MediaFilterSheetTexts,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
    genreLimit: Int = 20,
    collapsibleSections: Set<FilterSection> = emptySet(),
    contentScrollable: Boolean = true,
    customYearRange: (@Composable (years: Set<Int>, onRangeChange: (IntRange) -> Unit) -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(MediaFilterDraft(currentFilters)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isLight = LocalIsLightTheme.current

    // TvSafeSheet owns both variants: the TV dialog grabs initial focus on a focusGroup content
    // node (the hand-rolled Dialog split opened with orphaned focus, so the first D-pad presses
    // did nothing or escaped the dialog), and the mobile bottom sheet keeps the drag handle +
    // inset handling the library sheet used to replicate by hand.
    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = texts.sheetTitle,
        sheetState = sheetState,
    ) {
        val contentColor = MaterialTheme.colorScheme.onSurface
        val contentColorMedium = MaterialTheme.colorScheme.onSurfaceVariant
        val glassBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
        val yearPresets = remember { yearRangePresets() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (contentScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Header (title + reset) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = texts.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                val resetInteractionSource = remember { MutableInteractionSource() }
                val isResetPressed by resetInteractionSource.collectIsPressedAsState()
                val resetScale by animateFloatAsState(
                    targetValue = if (isResetPressed) 0.95f else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "filterSheetResetPressedScale"
                )
                val resetShape = ShapeCache.smooth12
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = resetScale * resetFocusState.scale
                            scaleY = resetScale * resetFocusState.scale
                        }
                        .clip(resetShape)
                        .background(glassBg)
                        .then(resetFocusState.focusModifier)
                        .tvFocusIndicator(resetFocusState, resetShape)
                        .clickable(
                            interactionSource = resetInteractionSource,
                            indication = null,
                            onClick = { draft = draft.clearedKeepingDownloaded() },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        texts.reset,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sections, in canonical (enum) order, filtered by the caller's set.
            // Every rendered section is preceded by a 20dp gap except the first
            // one (the header's 24dp gap is the first separator); a section whose
            // source list is empty renders nothing and stays "not rendered" so
            // the next section takes the gap instead (the historical behavior of
            // both sheets' conditional genre/tag blocks).
            var renderedAnySection = false
            var availabilityRendered = false
            FilterSection.entries.forEach { section ->
                if (section !in sections) return@forEach

                when (section) {
                    FilterSection.SORT -> {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        SectionLabel(texts.sortBy)
                        ButtonGroup(
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            SortOption.entries.forEach { option ->
                                customItem({
                                    ToggleButton(
                                        checked = option == draft.sortBy,
                                        onCheckedChange = {
                                            if (it) draft = draft.withSortBy(option)
                                        },
                                        colors = ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = glassBg,
                                            contentColor = contentColor,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (option == draft.sortBy) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }) {}
                            }
                        }
                        renderedAnySection = true
                    }

                    FilterSection.MEDIA_TYPE -> {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        SectionLabel(texts.mediaType)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filterableMediaTypes.forEach { mediaType ->
                                GlassFilterChip(
                                    label = mediaType.mediaTypeDisplayNamePlural(),
                                    selected = mediaType in draft.mediaTypes,
                                    onClick = { draft = draft.withMediaTypeToggled(mediaType) },
                                )
                            }
                        }
                        renderedAnySection = true
                    }

                    FilterSection.PLAYED_STATUS -> {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        SectionLabel(texts.status)
                        ButtonGroup(
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            PlayedStatus.entries.forEach { status ->
                                customItem({
                                    ToggleButton(
                                        checked = status == draft.playedStatus,
                                        onCheckedChange = {
                                            if (it) draft = draft.withPlayedStatus(status)
                                        },
                                        colors = ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = glassBg,
                                            contentColor = contentColor,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        Text(
                                            text = status.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (status == draft.playedStatus) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }) {}
                            }
                        }
                        // ── Resumable (In Progress) + Downloaded toggles ──
                        // A single chip that restricts the grid to items with a
                        // playback position (Jellyfin ItemFilter.IsResumable),
                        // plus the downloaded pin that swaps the browse list to
                        // the local offline store. They render as one block with
                        // the status group — the library sheet's historical
                        // grouping (12dp gap, no section label).
                        if (FilterSection.AVAILABILITY in sections) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AvailabilityChips(
                                draft = draft,
                                glassBg = glassBg,
                                resumableLabel = texts.resumable,
                                downloadedLabel = texts.downloaded,
                                onDraftChange = { draft = it },
                            )
                            availabilityRendered = true
                        }
                        renderedAnySection = true
                    }

                    FilterSection.AVAILABILITY -> {
                        // Only reached by a hypothetical caller that renders the
                        // toggles without the status group.
                        if (!availabilityRendered) {
                            if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                            AvailabilityChips(
                                draft = draft,
                                glassBg = glassBg,
                                resumableLabel = texts.resumable,
                                downloadedLabel = texts.downloaded,
                                onDraftChange = { draft = it },
                            )
                            renderedAnySection = true
                        }
                    }

                    FilterSection.GENRES -> if (genres.isNotEmpty()) {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        SectionLabel(texts.genres)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            genres.take(genreLimit).forEach { genre ->
                                GlassFilterChip(
                                    label = genre.name,
                                    selected = genre.name in draft.genres,
                                    onClick = { draft = draft.withGenreToggled(genre.name) },
                                )
                            }
                        }
                        renderedAnySection = true
                    }

                    FilterSection.YEARS -> {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        FilterSheetSection(
                            title = texts.years,
                            collapsible = FilterSection.YEARS in collapsibleSections,
                            collapseContentDescription = texts.collapseContentDescription,
                            expandContentDescription = texts.expandContentDescription,
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GlassFilterChip(
                                    label = texts.anyLabel,
                                    selected = draft.years.isEmpty(),
                                    onClick = { draft = draft.withYears(emptySet()) },
                                )
                                yearPresets.forEach { preset ->
                                    val selection = yearPresetSelection(preset, draft.years)
                                    GlassFilterChip(
                                        label = preset.label,
                                        selected = selection == YearPresetSelection.Full,
                                        onClick = {
                                            draft = draft.withYears(toggleYearPreset(preset, draft.years))
                                        },
                                    )
                                }
                            }
                            if (customYearRange != null) {
                                Box(modifier = Modifier.padding(top = 8.dp)) {
                                    customYearRange(draft.years) { range ->
                                        draft = draft.withYears(range.toSet())
                                    }
                                }
                            }
                        }
                        renderedAnySection = true
                    }

                    FilterSection.TAGS -> if (availableTags.isNotEmpty()) {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        FilterSheetSection(
                            title = texts.tags,
                            collapsible = FilterSection.TAGS in collapsibleSections,
                            collapseContentDescription = texts.collapseContentDescription,
                            expandContentDescription = texts.expandContentDescription,
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableTags.take(20).forEach { tag ->
                                    GlassFilterChip(
                                        label = tag,
                                        selected = tag in draft.tags,
                                        onClick = { draft = draft.withTagToggled(tag) },
                                    )
                                }
                            }
                        }
                        renderedAnySection = true
                    }

                    FilterSection.MIN_RATING -> {
                        if (renderedAnySection) Spacer(modifier = Modifier.height(20.dp))
                        FilterSheetSection(
                            title = texts.minRating,
                            collapsible = FilterSection.MIN_RATING in collapsibleSections,
                            collapseContentDescription = texts.collapseContentDescription,
                            expandContentDescription = texts.expandContentDescription,
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val ratingOptions = listOf(0f, 3f, 3.5f, 4f, 4.5f)
                                ratingOptions.forEach { rating ->
                                    GlassFilterChip(
                                        label = if (rating == 0f) texts.anyLabel else texts.ratingLabel(rating),
                                        selected = draft.minRating == rating,
                                        onClick = { draft = draft.withMinRating(rating) },
                                    )
                                }
                            }
                        }
                        renderedAnySection = true
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Apply button ──
            val applyFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val applyInteractionSource = remember { MutableInteractionSource() }
            val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
            val applyScale by animateFloatAsState(
                targetValue = if (isApplyPressed) 0.95f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "filterSheetApplyPressedScale"
            )
            val applyShape = ShapeCache.smooth16
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = applyScale * applyFocusState.scale
                        scaleY = applyScale * applyFocusState.scale
                    }
                    .clip(applyShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .then(applyFocusState.focusModifier)
                    .tvFocusIndicator(applyFocusState, applyShape)
                    .clickable(
                        interactionSource = applyInteractionSource,
                        indication = null,
                        onClick = { onApply(draft.appliedTo(currentFilters, sections)) },
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    texts.apply,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AvailabilityChips(
    draft: MediaFilterDraft,
    glassBg: Color,
    resumableLabel: String,
    downloadedLabel: String,
    onDraftChange: (MediaFilterDraft) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassFilterChip(
            label = resumableLabel,
            selected = draft.isResumable,
            onClick = { onDraftChange(draft.withResumableToggled()) },
        )
        GlassFilterChip(
            label = downloadedLabel,
            selected = draft.isDownloaded,
            onClick = { onDraftChange(draft.withDownloadedToggled()) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * A section body behind either the plain label (search's historical flat
 * years/tags/rating) or the tap-to-expand [CollapsibleSection] header
 * (library's progressive disclosure) — the content half is identical.
 */
@Composable
private fun FilterSheetSection(
    title: String,
    collapsible: Boolean,
    collapseContentDescription: String,
    expandContentDescription: String,
    content: @Composable () -> Unit,
) {
    if (collapsible) {
        CollapsibleSection(
            title = title,
            collapseContentDescription = collapseContentDescription,
            expandContentDescription = expandContentDescription,
            content = content,
        )
    } else {
        SectionLabel(title)
        content()
    }
}

/**
 * A section whose header can be tapped to expand/collapse its content. Used for
 * the less-frequently-used filter sections (Year, Tags, Rating) to reduce visual
 * clutter — progressive disclosure.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    collapseContentDescription: String,
    expandContentDescription: String,
    startExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val headerFocusState = rememberTvFocusState()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(headerFocusState.focusModifier)
                .tvFocusIndicator(headerFocusState, MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Tabler.Outline.ChevronUp else Tabler.Outline.ChevronDown,
                contentDescription = if (expanded) collapseContentDescription else expandContentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) { content() }
        }
    }
}
