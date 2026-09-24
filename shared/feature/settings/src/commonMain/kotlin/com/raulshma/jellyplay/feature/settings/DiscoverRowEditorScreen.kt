package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SeerrRowMedia
import com.raulshma.jellyplay.core.model.SeerrRowSort
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.StudioRef
import com.raulshma.jellyplay.core.model.TmdbGenreRef
import com.raulshma.jellyplay.core.model.TmdbGenres
import com.raulshma.jellyplay.core.model.filterableMediaTypes
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayName
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_add_discover_row
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_back
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_added_within
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_any
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_edit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_filters
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_genres
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_limit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_media_types
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_min_rating
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_min_vote
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_name
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_preview_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people_selected
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_premiered_within
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_save
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_seerr_filters
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_source
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_source_my_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_source_seerr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_status
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_studios
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_sort
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_sort_popularity
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_sort_rating
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_sort_release_date
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_tags
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_years
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_upcoming

/**
 * The discover-row editor: name, source (My Server / Seerr), the full filter
 * mix for the chosen source, row length, and a live preview strip (Jellyfin
 * sources). Edits mutate the VM's draft only; Save upserts and exits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverRowEditorScreen(
    onBack: () -> Unit,
    rowId: String?,
    viewModel: DiscoverRowsViewModel = koinViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val libraryFolders by viewModel.libraryFolders.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val studios by viewModel.studios.collectAsStateWithLifecycle()
    val peopleResults by viewModel.peopleResults.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    var showLibrariesSheet by remember { mutableStateOf(false) }
    var showStudiosSheet by remember { mutableStateOf(false) }
    var showPeopleSheet by remember { mutableStateOf(false) }

    LaunchedEffect(rowId) {
        if (rowId != null) viewModel.startEdit(rowId) else viewModel.startNew()
    }

    val current = draft
    JellyPlayScreenScaffold(
        title = stringResource(
            if (rowId == null) Res.string.settings_add_discover_row else Res.string.settings_discover_row_edit,
        ),
        onBack = {
            viewModel.closeEditor()
            onBack()
        },
        backgroundColorState = backgroundColorState,
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.settings_discover_row_save),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (current != null && current.row.title.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .clickable(enabled = current?.row?.title?.isNotBlank() == true) {
                            viewModel.saveDraft()
                            onBack()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        if (current == null) {
            Row(Modifier.fillMaxSize().padding(innerPadding)) {}
            return@JellyPlayScreenScaffold
        }
        val row = current.row
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Basics ──────────────────────────────────────────────────────
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Compass,
                    title = stringResource(Res.string.settings_discover_row_name),
                    initiallyExpanded = true,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    // The editable name field IS the row's title display — no
                    // read-only mirror above it.
                    EditorInlineTextField(
                        value = row.title,
                        onValueChange = { value -> viewModel.updateRow { it.copy(title = value.take(60)) } },
                    )
                    EditorToggleRow(
                        label = stringResource(Res.string.settings_discover_row_enabled),
                        checked = row.enabled,
                        onCheckedChange = { value -> viewModel.updateRow { it.copy(enabled = value) } },
                    )
                    // Source switch
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DiscoverRowSource.entries.forEach { source ->
                            FilterChip(
                                selected = row.source == source,
                                onClick = { viewModel.updateRow { it.copy(source = source) } },
                                label = { Text(discoverRowSourceLabel(source)) },
                            )
                        }
                    }
                    EditorSliderRow(
                        label = stringResource(Res.string.settings_discover_row_limit),
                        value = row.limit,
                        onValueChange = { value -> viewModel.updateRow { it.copy(limit = value) } },
                    )
                }
            }

            if (row.source == DiscoverRowSource.JELLYFIN) {
                item {
                    JellyfinFilterGroups(
                        row = row,
                        genres = genres.map { it.name },
                        tags = tags,
                        onUpdate = viewModel::updateRow,
                    )
                }
                item {
                    SettingsGroup(
                        icon = Tabler.Outline.Library,
                        title = stringResource(Res.string.settings_discover_row_libraries),
                        initiallyExpanded = false,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        EditorClickRow(
                            label = stringResource(Res.string.settings_discover_row_libraries),
                            value = if (row.libraryIds.isEmpty()) {
                                stringResource(Res.string.settings_discover_row_all)
                            } else {
                                row.libraryIds.size.toString()
                            },
                            onClick = { showLibrariesSheet = true },
                        )
                        EditorClickRow(
                            label = stringResource(Res.string.settings_discover_row_studios),
                            value = row.studios.joinToString("/") { it.name }.ifEmpty { "—" },
                            onClick = { showStudiosSheet = true },
                        )
                        EditorClickRow(
                            label = stringResource(Res.string.settings_discover_row_people),
                            value = row.people.joinToString("/") { it.name }.ifEmpty { "—" },
                            onClick = { showPeopleSheet = true },
                        )
                    }
                }
                item {
                    // ── Preview ──
                    SettingsGroup(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(Res.string.settings_discover_row_preview),
                        initiallyExpanded = true,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        when {
                            current.previewLoading -> Text(
                                "…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            current.previewError != null -> Text(
                                current.previewError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            current.previewItems.isEmpty() -> Text(
                                stringResource(Res.string.settings_discover_row_preview_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            else -> Text(
                                current.previewItems.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    SeerrFilterGroups(row = row, onUpdate = viewModel::updateRow)
                }
            }
        }
    }

    if (showLibrariesSheet && current != null) {
        // Empty selection = the whole catalog: every library shows checked.
        DiscoverRowToggleSheet(
            title = stringResource(Res.string.settings_discover_row_libraries),
            items = libraryFolders,
            keyFor = { it.id },
            labelFor = { it.name },
            isChecked = { folder ->
                val ids = current.row.libraryIds
                ids.isEmpty() || folder.id in ids
            },
            onToggle = { folder, checked ->
                viewModel.updateRow { r ->
                    val next = if (checked) {
                        (r.libraryIds + folder.id).distinct()
                    } else {
                        r.libraryIds - folder.id
                    }
                    r.copy(libraryIds = next)
                }
            },
            onDismissRequest = { showLibrariesSheet = false },
        )
    }

    if (showStudiosSheet && current != null) {
        DiscoverRowToggleSheet(
            title = stringResource(Res.string.settings_discover_row_studios),
            items = studios,
            keyFor = { it.id },
            labelFor = { it.name },
            isChecked = { studio -> current.row.studios.any { it.id == studio.id } },
            onToggle = { studio, checked ->
                viewModel.updateRow { r ->
                    val next = if (checked) {
                        r.studios + StudioRef(studio.id, studio.name)
                    } else {
                        r.studios.filterNot { it.id == studio.id }
                    }
                    r.copy(studios = next)
                }
            },
            onDismissRequest = { showStudiosSheet = false },
        )
    }

    if (showPeopleSheet) {
        TvSafeSheet(onDismissRequest = { showPeopleSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(Res.string.settings_discover_row_people),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                var peopleQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = peopleQuery,
                    onValueChange = { value ->
                        peopleQuery = value
                        viewModel.searchPeople(value)
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.settings_discover_row_people_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val currentRow = draft?.row ?: return@Column
                val selected = currentRow.people
                val selectedIds = selected.map { it.id }.toSet()
                if (selected.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.settings_discover_row_people_selected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                LazyColumn {
                    // Selected entries first so they stay reachable (and
                    // removable) regardless of the current search term.
                    items(selected.size, key = { "sel_${selected[it].id}" }) { index ->
                        val person = selected[index]
                        ListItem(
                            headlineContent = { Text(person.name) },
                            trailingContent = {
                                Switch(
                                    checked = true,
                                    onCheckedChange = {
                                        viewModel.updateRow { r ->
                                            r.copy(people = r.people.filterNot { it.id == person.id })
                                        }
                                    },
                                )
                            },
                        )
                    }
                    items(peopleResults.size, key = { peopleResults[it].id }) { index ->
                        val person = peopleResults[index]
                        if (person.id in selectedIds) return@items // already listed above
                        ListItem(
                            headlineContent = { Text(person.name) },
                            trailingContent = {
                                Switch(
                                    checked = false,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            viewModel.updateRow { r ->
                                                r.copy(people = r.people + PersonRef(person.id, person.name))
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Jellyfin filter groups ─────────────────────────────────────────────────

/** The rating/vote chip ladder shared by the Jellyfin min-rating and Seerr min-vote groups. */
private val RatingChipSteps = listOf(0f, 6f, 7f, 7.5f, 8f)

@Composable
private fun JellyfinFilterGroups(
    row: DiscoverRowConfig,
    genres: List<String>,
    tags: List<String>,
    onUpdate: ((DiscoverRowConfig) -> DiscoverRowConfig) -> Unit,
) {
    val filters = row.filters
    SettingsGroup(
        icon = Tabler.Outline.Filter,
        title = stringResource(Res.string.settings_discover_row_filters),
        initiallyExpanded = true,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        // Media types
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_media_types)) {
            filterableMediaTypes.forEach { type ->
                FilterChip(
                    selected = type in filters.mediaTypes,
                    onClick = {
                        onUpdate { it.copy(filters = it.filters.withMediaTypeToggled(type)) }
                    },
                    label = { Text(type.mediaTypeDisplayName()) },
                )
            }
        }
        // Sort
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_sort)) {
            SortOption.entries.forEach { sort ->
                FilterChip(
                    selected = filters.sortBy == sort,
                    onClick = { onUpdate { it.copy(filters = it.filters.withSortBy(sort)) } },
                    label = { Text(sort.displayName) },
                )
            }
        }
        // Played status
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_status)) {
            PlayedStatus.entries.forEach { status ->
                FilterChip(
                    selected = filters.playedStatus == status,
                    onClick = { onUpdate { it.copy(filters = it.filters.withPlayedStatus(status)) } },
                    label = { Text(status.displayName) },
                )
            }
        }
        // Min rating
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_min_rating)) {
            RatingChipSteps.forEach { rating ->
                FilterChip(
                    selected = filters.minRating == rating,
                    onClick = { onUpdate { it.copy(filters = it.filters.withMinRating(rating)) } },
                    label = { Text(if (rating == 0f) stringResource(Res.string.settings_discover_row_any) else "★ $rating") },
                )
            }
        }
        // Genres
        if (genres.isNotEmpty()) {
            FilterChipFlow(label = stringResource(Res.string.settings_discover_row_genres)) {
                genres.take(24).forEach { genre ->
                    FilterChip(
                        selected = genre in filters.genres,
                        onClick = { onUpdate { it.copy(filters = it.filters.withGenreToggled(genre)) } },
                        label = { Text(genre) },
                    )
                }
            }
        }
        // Tags
        if (tags.isNotEmpty()) {
            FilterChipFlow(label = stringResource(Res.string.settings_discover_row_tags)) {
                tags.take(20).forEach { tag ->
                    FilterChip(
                        selected = tag in filters.tags,
                        onClick = { onUpdate { it.copy(filters = it.filters.withTagToggled(tag)) } },
                        label = { Text(tag) },
                    )
                }
            }
        }
        // Years
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_years)) {
            FilterChip(
                selected = filters.years.isEmpty(),
                onClick = { onUpdate { it.copy(filters = it.filters.withYears(emptyList())) } },
                label = { Text(stringResource(Res.string.settings_discover_row_any)) },
            )
            listOf(2020..2026, 2010..2019, 2000..2009, 1990..1999).forEach { range ->
                FilterChip(
                    selected = filters.years.toSet() == range.toSet(),
                    onClick = { onUpdate { it.copy(filters = it.filters.withYears(range.toList())) } },
                    label = { Text("${range.first}–${range.last}") },
                )
            }
        }
        // Added within
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_added_within)) {
            listOf(null, 7, 30, 90, 365).forEach { days ->
                FilterChip(
                    selected = row.addedWithinDays == days,
                    onClick = { onUpdate { it.copy(addedWithinDays = days) } },
                    label = { Text(days?.let { "$it d" } ?: stringResource(Res.string.settings_discover_row_any)) },
                )
            }
        }
        // Premiered within
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_premiered_within)) {
            listOf(null, 1, 5, 10, 25).forEach { years ->
                FilterChip(
                    selected = row.premieredWithinYears == years,
                    onClick = { onUpdate { it.copy(premieredWithinYears = years) } },
                    label = { Text(years?.let { "$it y" } ?: stringResource(Res.string.settings_discover_row_any)) },
                )
            }
        }
    }
}

// ── Seerr filter groups ────────────────────────────────────────────────────

@Composable
private fun SeerrFilterGroups(
    row: DiscoverRowConfig,
    onUpdate: ((DiscoverRowConfig) -> DiscoverRowConfig) -> Unit,
) {
    val f = row.seerrFilters
    SettingsGroup(
        icon = Tabler.Outline.Filter,
        title = stringResource(Res.string.settings_discover_row_seerr_filters),
        initiallyExpanded = true,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_media)) {
            SeerrRowMedia.entries.forEach { media ->
                FilterChip(
                    selected = f.media == media,
                    onClick = { onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(media = media)) } },
                    label = {
                        Text(
                            stringResource(
                                if (media == SeerrRowMedia.MOVIE) Res.string.settings_discover_row_movies
                                else Res.string.settings_discover_row_tv,
                            ),
                        )
                    },
                )
            }
        }
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_genres)) {
            TmdbGenres.forMedia(f.media).forEach { genre ->
                FilterChip(
                    selected = f.genres.any { it.id == genre.id },
                    onClick = {
                        onUpdate {
                            val next = it.seerrFilters.genres.toggleTmdbGenre(genre)
                            it.copy(seerrFilters = it.seerrFilters.copy(genres = next))
                        }
                    },
                    label = { Text(genre.name) },
                )
            }
        }
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_min_vote)) {
            RatingChipSteps.forEach { vote ->
                FilterChip(
                    selected = f.minVoteAverage == vote,
                    onClick = {
                        onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(minVoteAverage = vote)) }
                    },
                    label = { Text(if (vote == 0f) stringResource(Res.string.settings_discover_row_any) else "★ $vote") },
                )
            }
        }
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_sort)) {
            SeerrRowSort.entries.forEach { sort ->
                FilterChip(
                    selected = f.sort == sort,
                    onClick = { onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(sort = sort)) } },
                    label = { Text(seerrRowSortLabel(sort)) },
                )
            }
        }
        EditorToggleRow(
            label = stringResource(Res.string.settings_discover_row_upcoming),
            checked = f.upcomingOnly,
            onCheckedChange = { value ->
                onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(upcomingOnly = value)) }
            },
        )
    }
}

/**
 * Localized [SeerrRowSort] label for the sort chips — the enum itself stays
 * resource-free (core:model has no string resources), same doctrine as
 * [discoverRowSourceLabel].
 */
@Composable
internal fun seerrRowSortLabel(sort: SeerrRowSort): String = when (sort) {
    SeerrRowSort.POPULARITY -> stringResource(Res.string.settings_discover_row_sort_popularity)
    SeerrRowSort.RATING -> stringResource(Res.string.settings_discover_row_sort_rating)
    SeerrRowSort.RELEASE_DATE -> stringResource(Res.string.settings_discover_row_sort_release_date)
}

private fun List<TmdbGenreRef>.toggleTmdbGenre(genre: TmdbGenreRef): List<TmdbGenreRef> =
    if (any { it.id == genre.id }) filterNot { it.id == genre.id } else this + genre

// ── Small editor rows ──────────────────────────────────────────────────────

/**
 * Localized [DiscoverRowSource] label for the editor's source chips and the
 * manage screen's row summaries — the enum itself stays resource-free
 * (core:model has no string resources).
 */
@Composable
internal fun discoverRowSourceLabel(source: DiscoverRowSource): String = when (source) {
    DiscoverRowSource.JELLYFIN -> stringResource(Res.string.settings_discover_row_source_my_server)
    DiscoverRowSource.SEERR -> stringResource(Res.string.settings_discover_row_source_seerr)
}

/**
 * The shared shape of the editor's simple pickers (libraries, studios): a
 * TV-safe sheet headed by [title] listing [items] with a trailing Switch per
 * row. Selection stays live — [isChecked]/[onToggle] read and write through
 * the caller's draft, so a toggle re-renders the sheet's rows immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DiscoverRowToggleSheet(
    title: String,
    items: List<T>,
    keyFor: (T) -> Any,
    labelFor: (T) -> String,
    isChecked: (T) -> Boolean,
    onToggle: (T, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TvSafeSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn {
                items(items.size, key = { keyFor(items[it]) }) { index ->
                    val item = items[index]
                    ListItem(
                        headlineContent = { Text(labelFor(item)) },
                        trailingContent = {
                            Switch(
                                checked = isChecked(item),
                                onCheckedChange = { checked -> onToggle(item, checked) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipFlow(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EditorToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun EditorClickRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EditorInlineTextField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun EditorSliderRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$label: $value", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = DiscoverRowConfig.MIN_LIMIT.toFloat()..DiscoverRowConfig.MAX_LIMIT.toFloat(),
            steps = (DiscoverRowConfig.MAX_LIMIT - DiscoverRowConfig.MIN_LIMIT) - 1,
        )
    }
}
