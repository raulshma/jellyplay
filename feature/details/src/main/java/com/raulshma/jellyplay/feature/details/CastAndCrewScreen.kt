package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridMinSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.TopBarStyle
import com.raulshma.jellyplay.core.ui.components.rememberStableCallback
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid

private enum class CastCrewTab { CAST, CREW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastAndCrewScreen(
    itemId: String,
    onPersonClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CastAndCrewViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    JellyPlayScreenScaffold(
        title = stringResource(R.string.detail_section_cast_crew),
        onBack = onBack,
        topBarStyle = TopBarStyle.Collapsing,
    ) { padding ->
        when (state) {
            CastAndCrewUiState.Loading -> DelayedLoadingScreen(modifier = Modifier.padding(padding))
            is CastAndCrewUiState.Error -> ErrorScreen(
                message = (state as CastAndCrewUiState.Error).message,
                onRetry = { viewModel.load(itemId) },
                modifier = Modifier.padding(padding),
            )
            is CastAndCrewUiState.Success -> {
                val success = state as CastAndCrewUiState.Success
                val adaptiveInfo = LocalAdaptiveInfo.current
                val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
                val contentPad = adaptiveInfo.contentPadding(isTv)
                val gridMin = adaptiveInfo.gridMinSize(isTv)
                val spacing = adaptiveInfo.itemSpacing(isTv)

                var tab by remember { mutableStateOf(CastCrewTab.CAST) }
                var query by remember { mutableStateOf("") }
                val people = remember(success, tab, query) {
                    val base = when (tab) {
                        CastCrewTab.CAST -> success.cast
                        CastCrewTab.CREW -> success.crew
                    }
                    val trimmed = query.trim()
                    if (trimmed.isEmpty()) {
                        base
                    } else {
                        // Search/filter across name + role so crew credited by job
                        // (e.g. "Director of Photography") is reachable too.
                        base.filter { person ->
                            person.name.contains(trimmed, ignoreCase = true) ||
                                person.role?.contains(trimmed, ignoreCase = true) == true
                        }
                    }
                }

                TvFocusableGrid(
                    itemCount = people.size,
                    key = { people[it].id },
                    columns = GridCells.Adaptive(gridMin),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentType = { "person" },
                    extraContent = {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = contentPad, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(stringResource(com.raulshma.jellyplay.core.ui.R.string.core_search))
                                    },
                                    singleLine = true,
                                )
                                androidx.compose.foundation.layout.Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CastCrewTab.entries.forEach { option ->
                                        FilterChip(
                                            selected = tab == option,
                                            onClick = { tab = option },
                                            label = {
                                                Text(
                                                    when (option) {
                                                        CastCrewTab.CAST -> stringResource(R.string.detail_section_cast)
                                                        CastCrewTab.CREW -> stringResource(R.string.detail_section_crew)
                                                    },
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(),
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { index, itemModifier ->
                    val person = people[index]
                    PersonItem(
                        person = person,
                        // Only fetch a portrait when the person carries a primary
                        // image tag (PersonInfo.hasPortrait); tagless entries
                        // render the placeholder instead of issuing a 404'ing URL.
                        imageUrl = remember(person.id) {
                            if (person.hasPortrait()) viewModel.getImageUrl(person.id) else ""
                        },
                        onClick = rememberStableCallback { onPersonClick(person.id) },
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}
