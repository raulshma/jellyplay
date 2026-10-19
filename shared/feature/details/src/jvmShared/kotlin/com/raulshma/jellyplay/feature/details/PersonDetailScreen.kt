package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ExpandableText
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.animation.UiStateAnimatedContent
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshEffect
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.QuickActionAdapter
import com.raulshma.jellyplay.core.ui.components.QuickActionIntakeHost
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.MovieOff
import com.raulshma.jellyplay.core.ui.components.rememberQuickActionIntake
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_costars_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_empty_description
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_empty_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_acting
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_all
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_as
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_directing
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_other
import com.raulshma.jellyplay.feature.details.generated.resources.detail_person_role_writing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_clear_filters

private enum class PersonFilmographyFilter(val label: String, val match: (MediaItem) -> Boolean) {
    ALL("All", { true }),
    MOVIES("Movies", { it.mediaType == MediaType.MOVIE }),
    TV("TV", { it.mediaType == MediaType.SERIES }),
}

/**
 * The role-group dimension over the filmography — a second chip row that
 * only renders when the fetch carried role data ([PersonDetailUiState.Success.filmographyCredits]
 * non-empty under the showFilmographyRoles gate). [group] is null for the
 * ALL entry, which matches every item including uncredited ones.
 */
private enum class PersonFilmographyRoleFilter(val group: FilmographyRoleGroup? = null) {
    ALL,
    ACTING(FilmographyRoleGroup.ACTING),
    DIRECTING(FilmographyRoleGroup.DIRECTING),
    WRITING(FilmographyRoleGroup.WRITING),
    OTHER(FilmographyRoleGroup.OTHER),
    ;

    /** True when [itemGroup] belongs under this chip (ALL admits everything). */
    fun matches(itemGroup: FilmographyRoleGroup?): Boolean = group == null || itemGroup == group
}

private fun PersonFilmographyRoleFilter.labelRes(): StringResource = when (this) {
    PersonFilmographyRoleFilter.ALL -> Res.string.detail_person_role_all
    PersonFilmographyRoleFilter.ACTING -> Res.string.detail_person_role_acting
    PersonFilmographyRoleFilter.DIRECTING -> Res.string.detail_person_role_directing
    PersonFilmographyRoleFilter.WRITING -> Res.string.detail_person_role_writing
    PersonFilmographyRoleFilter.OTHER -> Res.string.detail_person_role_other
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonFilmography(
    filmography: List<MediaItem>,
    biography: String?,
    credits: Map<String, PersonInfo>,
    costars: List<PersonRef>,
    getImageUrl: (String) -> String,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onFocusedMediaItem: (MediaItem?) -> Unit,
    contentPad: androidx.compose.ui.unit.Dp,
    gridMin: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    var filter by remember { mutableStateOf(PersonFilmographyFilter.ALL) }
    var roleFilter by remember { mutableStateOf(PersonFilmographyRoleFilter.ALL) }
    // The group chips exist only when the fetch carried role data — an
    // empty credits map degrades the second dimension to a no-op ALL so a
    // stale selection can never strand the grid on an invisible filter.
    val showRoleGroups = credits.isNotEmpty()
    val activeRoleFilter = if (showRoleGroups) roleFilter else PersonFilmographyRoleFilter.ALL
    val visibleFilmography by remember(filmography, filter, activeRoleFilter, credits) {
        derivedStateOf {
            filmography
                .filter(filter.match)
                .filter { item ->
                    activeRoleFilter.matches(credits[item.id]?.let { filmographyRoleGroup(it.type) })
                }
        }
    }

    TvFocusableGrid(
        itemCount = visibleFilmography.size,
        key = { visibleFilmography[it].id },
        columns = GridCells.Adaptive(gridMin),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPad,
            end = contentPad,
            top = topInset,
            bottom = bottomInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentType = { "mediaItem" },
        onFocusedIndexChange = { index -> onFocusedMediaItem(visibleFilmography.getOrNull(index)) },
        // Full-span header (biography + co-stars + filter chips) stays in scroll
        // flow above the poster grid. extraContent is emitted before the items.
        extraContent = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PersonHeader(
                    biography = biography,
                    costars = costars,
                    getImageUrl = getImageUrl,
                    onPersonClick = onPersonClick,
                    filter = filter,
                    onFilterChange = { filter = it },
                    roleFilter = activeRoleFilter,
                    onRoleFilterChange = { roleFilter = it },
                    showRoleGroups = showRoleGroups,
                    contentPad = contentPad,
                )
            }
            // an empty filmography (no titles, or a Movies/TV or role-group
            // filter that matches nothing) previously rendered just the header.
            // Surface an empty state with a one-tap "Clear filter" CTA so the
            // zero-result case is recoverable.
            if (visibleFilmography.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                        icon = Tabler.Outline.MovieOff,
                        title = stringResource(Res.string.detail_person_empty_title),
                        description = stringResource(Res.string.detail_person_empty_description),
                        actionLabel = if (filter != PersonFilmographyFilter.ALL ||
                            activeRoleFilter != PersonFilmographyRoleFilter.ALL
                        ) {
                            stringResource(CoreUiRes.string.core_clear_filters)
                        } else null,
                        onAction = if (filter != PersonFilmographyFilter.ALL ||
                            activeRoleFilter != PersonFilmographyRoleFilter.ALL
                        ) {
                            {
                                filter = PersonFilmographyFilter.ALL
                                roleFilter = PersonFilmographyRoleFilter.ALL
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            }
        },
    ) { index, itemModifier ->
        val item = visibleFilmography[index]
        val visible = remember { mutableStateOf(false) }
        val clickHandler = remember(item.id) { { onItemClick(item.id) } }
        LaunchedEffect(Unit) { visible.value = true }
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
            ) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ),
        ) {
            // The "as {Role}" subtitle rides under the poster in a cell
            // wrapper — PosterCard itself stays subtitle-free for every other
            // surface. No credit → no text, the card renders unchanged.
            androidx.compose.foundation.layout.Column(modifier = itemModifier) {
                PosterCard(
                    item = item,
                    imageUrl = getImageUrl(item.id),
                    onClick = clickHandler,
                    modifier = Modifier.fillMaxWidth(),
                )
                val roleLabel = creditRoleLabel(credits[item.id])
                if (roleLabel != null) {
                    Text(
                        text = stringResource(Res.string.detail_person_role_as, roleLabel),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonHeader(
    biography: String?,
    costars: List<PersonRef>,
    getImageUrl: (String) -> String,
    onPersonClick: (String) -> Unit,
    filter: PersonFilmographyFilter,
    onFilterChange: (PersonFilmographyFilter) -> Unit,
    roleFilter: PersonFilmographyRoleFilter,
    onRoleFilterChange: (PersonFilmographyRoleFilter) -> Unit,
    showRoleGroups: Boolean,
    contentPad: androidx.compose.ui.unit.Dp,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!biography.isNullOrBlank()) {
            ExpandableText(
                text = biography,
                collapsedMaxLines = 4,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = contentPad),
            )
        }
        // "frequently appears with" — avatars of the co-stars credited on
        // the person's most-recent title, tap-through to that person's screen
        // (the same Route.PersonDetail jump the detail cast row uses). Hidden
        // entirely when the fetch produced none (pref off, empty, or failed).
        if (costars.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.detail_person_costars_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = contentPad),
            )
            TvFocusableItemRow(
                items = costars,
                key = { "person_costar_${it.id}" },
                contentPadding = PaddingValues(horizontal = contentPad),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) { _, costar, focusModifier ->
                val costarClick = remember(costar.id) { { onPersonClick(costar.id) } }
                val costarImageUrl = remember(costar.id) { getImageUrl(costar.id) }
                // PersonRef carries no role — the shared avatar cell renders
                // the name line only.
                PersonItem(
                    person = PersonInfo(id = costar.id, name = costar.name, type = "Actor"),
                    imageUrl = costarImageUrl,
                    onClick = costarClick,
                    modifier = focusModifier,
                )
            }
        }
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPad),
        ) {
            PersonFilmographyFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        // Role-group dimension — a second chip row that only exists when
        // the filmography carried role data; no data → exactly the single row
        // above, as before.
        if (showRoleGroups) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = contentPad),
            ) {
                PersonFilmographyRoleFilter.entries.forEach { option ->
                    FilterChip(
                        selected = roleFilter == option,
                        onClick = { onRoleFilterChange(option) },
                        label = { Text(stringResource(option.labelRes())) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}
@Composable
fun PersonDetailScreen(
    personId: String,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    DeferredRefreshEffect(viewModel.deferredRefresher)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? PersonDetailUiState.Success)?.name ?: ""
    val isLoading = state is PersonDetailUiState.Loading
    // The filmography gates — read through the VM's eagerly-shared projection so
    // toggling a setting hides/shows the role subtitles, group chips, and
    // co-star row without waiting for a reload.
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    // Collected (not read as a .value snapshot inside the intake's
    // isDownloaded lambda) so the resolver is rebuilt when the downloaded set
    // changes — a download completing flips the card's Download↔Remove-download
    // action without waiting for an unrelated recomposition. The set is
    // distinct-collapsed upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Long-press / TV-Menu quick actions for filmography cards, on the shared
    // intake (core/ui — see QuickActionIntake). Download / Remove download
    // ride the same intake as the library grid (#147).
    val quickActionIntake = rememberQuickActionIntake(
        scope = MediaQuickActionScope.DETAIL,
        includeDownload = true,
        isDownloaded = remember(downloadedIds) {
            { item: MediaItem -> downloadedIds.contains(item.id) }
        },
        adapter = remember(viewModel, onItemClick) {
            QuickActionAdapter(
                onPlay = { item -> onItemClick(item.id) },
                onOpenDetail = { item -> onItemClick(item.id) },
                onMarkPlayed = viewModel::markItemPlayed,
                onDownload = { item -> viewModel.downloadItem(item, onOpenDetail = onItemClick) },
                onRemoveDownload = viewModel::removeItemDownload,
            )
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    quickActionIntake.openFocusedItem()
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionIntake.controller) {
        JellyPlayScreenScaffold(
            title = title,
            onBack = onBack,
            topBarStyle = TopBarStyle.Collapsing,
            actions = {
                if (isLoading) {
                    JellyPlayLoadingIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(24.dp)
                    )
                }
            },
        ) { padding ->
        // The screen's Loading↔Error↔Success swap through the shared animated
        // branch primitive (core:ui): crossfade + barely-there scale, snapped
        // under reduced motion via the motion scheme.
        val loadingBranchState: JellyPlayViewModel.LoadingState<PersonDetailUiState.Success> =
            when (val s = state) {
                is PersonDetailUiState.Error -> JellyPlayViewModel.LoadingState.Error(s.message)
                PersonDetailUiState.Loading -> JellyPlayViewModel.LoadingState.Loading
                is PersonDetailUiState.Success -> JellyPlayViewModel.LoadingState.Success(s)
            }
        UiStateAnimatedContent(
            state = loadingBranchState,
            modifier = Modifier.padding(padding),
            loading = { DelayedLoadingScreen() },
            error = { branch ->
                ErrorScreen(
                    message = branch.message,
                    onRetry = { viewModel.loadPerson(personId) },
                )
            },
        ) { success ->
            val adaptiveInfo = LocalAdaptiveInfo.current
            val isTv = LocalTvMode.current
            val contentPad = adaptiveInfo.contentPadding(isTv)
            val gridMin = adaptiveInfo.gridMinSize(isTv)
            val spacing = adaptiveInfo.itemSpacing(isTv)

            // Filter + sort are client-side over the already-loaded filmography,
            // so they don't add server round-trips. Defaults surface everything
            // newest-first, matching how most cast pages read.
            val filmography = remember(success.filmography) {
                success.filmography.sortedByDescending { it.year ?: 0 }
            }
            // The filmography surfaces gate on the preferences here so a toggle
            // takes effect on the already-loaded screen (an off pref reads as
            // absent data — the cards and header degrade to the role-less /
            // co-star-less render).
            val credits = if (preferences.showFilmographyRoles) {
                success.filmographyCredits
            } else {
                emptyMap()
            }
            val costars = if (preferences.showFrequentCostars) success.costars else emptyList()

            PersonFilmography(
                filmography = filmography,
                biography = success.biography,
                credits = credits,
                costars = costars,
                getImageUrl = viewModel::getImageUrl,
                onItemClick = onItemClick,
                onPersonClick = onPersonClick,
                onFocusedMediaItem = { item -> quickActionIntake.tvFocusedItem = item },
                contentPad = contentPad,
                gridMin = gridMin,
                spacing = spacing,
                topInset = padding.calculateTopPadding() + 16.dp,
                bottomInset = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
            )
        }
        } // close scaffold content lambda
        } // close CompositionLocalProvider
    } // close Box
    // Quick-action sheet + remove-download confirm — the shared intake hosts
    // both (removal only ever deletes the local download; the server copy is
    // untouched).
    QuickActionIntakeHost(quickActionIntake)
} // close PersonDetailScreen
