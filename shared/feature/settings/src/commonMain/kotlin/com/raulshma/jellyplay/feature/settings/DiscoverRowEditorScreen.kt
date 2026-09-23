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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_edit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_added_within
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_limit
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_name
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_preview_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_people_selected
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_premiered_within
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_save
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_source
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_row_studios
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
                    EditorTextRow(
                        label = stringResource(Res.string.settings_discover_row_name),
                        value = row.title,
                    )
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
                                label = { Text(source.displayName) },
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
                                "All"
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

    if (showLibrariesSheet) {
        TvSafeSheet(onDismissRequest = { showLibrariesSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(Res.string.settings_discover_row_libraries),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                val currentRow = draft?.row ?: return@Column
                val selected = currentRow.libraryIds.toSet()
                LazyColumn {
                    items(libraryFolders.size, key = { libraryFolders[it].id }) { index ->
                        val folder = libraryFolders[index]
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            trailingContent = {
                                Switch(
                                    checked = folder.id in selected || selected.isEmpty(),
                                    onCheckedChange = { checked ->
                                        viewModel.updateRow { r ->
                                            val next = if (checked) {
                                                (r.libraryIds + folder.id).distinct()
                                            } else {
                                                r.libraryIds - folder.id
                                            }
                                            r.copy(libraryIds = next)
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

    if (showStudiosSheet) {
        TvSafeSheet(onDismissRequest = { showStudiosSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(Res.string.settings_discover_row_studios),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                val currentRow = draft?.row ?: return@Column
                val selected = currentRow.studios.map { it.id }.toSet()
                LazyColumn {
                    items(studios.size, key = { studios[it].id }) { index ->
                        val studio = studios[index]
                        ListItem(
                            headlineContent = { Text(studio.name) },
                            trailingContent = {
                                Switch(
                                    checked = studio.id in selected,
                                    onCheckedChange = { checked ->
                                        viewModel.updateRow { r ->
                                            val next = if (checked) {
                                                r.studios + StudioRef(studio.id, studio.name)
                                            } else {
                                                r.studios.filterNot { it.id == studio.id }
                                            }
                                            r.copy(studios = next)
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
        title = "Filters",
        initiallyExpanded = true,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        // Media types
        FilterChipFlow(label = "Media types") {
            filterableMediaTypes.forEach { type ->
                FilterChip(
                    selected = type in filters.mediaTypes,
                    onClick = {
                        onUpdate { it.copy(filters = it.filters.withMediaTypeToggled(type)) }
                    },
                    label = { Text(type.name.lowercase().replace('_', ' ')) },
                )
            }
        }
        // Sort
        FilterChipFlow(label = "Sort") {
            SortOption.entries.forEach { sort ->
                FilterChip(
                    selected = filters.sortBy == sort,
                    onClick = { onUpdate { it.copy(filters = it.filters.withSortBy(sort)) } },
                    label = { Text(sort.displayName) },
                )
            }
        }
        // Played status
        FilterChipFlow(label = "Status") {
            PlayedStatus.entries.forEach { status ->
                FilterChip(
                    selected = filters.playedStatus == status,
                    onClick = { onUpdate { it.copy(filters = it.filters.withPlayedStatus(status)) } },
                    label = { Text(status.displayName) },
                )
            }
        }
        // Min rating
        FilterChipFlow(label = "Min rating") {
            listOf(0f, 6f, 7f, 7.5f, 8f).forEach { rating ->
                FilterChip(
                    selected = filters.minRating == rating,
                    onClick = { onUpdate { it.copy(filters = it.filters.withMinRating(rating)) } },
                    label = { Text(if (rating == 0f) "Any" else "★ $rating") },
                )
            }
        }
        // Genres
        if (genres.isNotEmpty()) {
            FilterChipFlow(label = "Genres") {
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
            FilterChipFlow(label = "Tags") {
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
        FilterChipFlow(label = "Years") {
            FilterChip(
                selected = filters.years.isEmpty(),
                onClick = { onUpdate { it.copy(filters = it.filters.withYears(emptyList())) } },
                label = { Text("Any") },
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
                    label = { Text(days?.let { "$it d" } ?: "Any") },
                )
            }
        }
        // Premiered within
        FilterChipFlow(label = stringResource(Res.string.settings_discover_row_premiered_within)) {
            listOf(null, 1, 5, 10, 25).forEach { years ->
                FilterChip(
                    selected = row.premieredWithinYears == years,
                    onClick = { onUpdate { it.copy(premieredWithinYears = years) } },
                    label = { Text(years?.let { "$it y" } ?: "Any") },
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
        title = "Seerr filters",
        initiallyExpanded = true,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        FilterChipFlow(label = "Media") {
            SeerrRowMedia.entries.forEach { media ->
                FilterChip(
                    selected = f.media == media,
                    onClick = { onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(media = media)) } },
                    label = { Text(if (media == SeerrRowMedia.MOVIE) "Movies" else "TV") },
                )
            }
        }
        FilterChipFlow(label = "Genres") {
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
        FilterChipFlow(label = "Min vote") {
            listOf(0f, 6f, 7f, 7.5f, 8f).forEach { vote ->
                FilterChip(
                    selected = f.minVoteAverage == vote,
                    onClick = {
                        onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(minVoteAverage = vote)) }
                    },
                    label = { Text(if (vote == 0f) "Any" else "★ $vote") },
                )
            }
        }
        FilterChipFlow(label = "Sort") {
            SeerrRowSort.entries.forEach { sort ->
                FilterChip(
                    selected = f.sort == sort,
                    onClick = { onUpdate { it.copy(seerrFilters = it.seerrFilters.copy(sort = sort)) } },
                    label = { Text(sort.name.lowercase().replace('_', ' ')) },
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

private fun List<TmdbGenreRef>.toggleTmdbGenre(genre: TmdbGenreRef): List<TmdbGenreRef> =
    if (any { it.id == genre.id }) filterNot { it.id == genre.id } else this + genre

// ── Small editor rows ──────────────────────────────────────────────────────

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
private fun EditorTextRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value.ifBlank { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
