package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_no_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subsection_latest_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subsection_latest_media_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subsection_recently_added
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subsection_recently_added_desc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeSectionsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: LibraryLayoutViewModel = koinViewModel(),
) {
    val libraries by viewModel.libraryFolders.collectAsStateWithLifecycle()
    val overrides by viewModel.libraryHomeSectionOverridesFlow.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()
    val adaptiveInfo = LocalAdaptiveInfo.current

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(focusRequester = focusRequester, itemCount = 1, tag = "library_sections_init")

    val scrollState = rememberLazyListState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_configure_libraries),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .tvFocusRestorer(),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Folders,
                    title = stringResource(Res.string.settings_library_sections),
                    summary = {
                        if (libraries.isEmpty()) {
                            stringResource(Res.string.settings_library_no_libraries)
                        } else {
                            "${libraries.size} libraries"
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    if (libraries.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_library_no_libraries),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        )
                    } else {
                        libraries.forEachIndexed { index, folder ->
                            LibrarySectionRow(
                                folder = folder,
                                index = index,
                                count = libraries.size,
                                isExpanded = expanded[folder.id] == true,
                                disabledTypes = overrides[folder.id].orEmpty(),
                                onToggleExpand = {
                                    expanded[folder.id] = !(expanded[folder.id] == true)
                                },
                                onToggleSection = { type, enabled ->
                                    viewModel.setLibrarySectionEnabled(folder.id, type, enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionRow(
    folder: LibraryFolder,
    index: Int,
    count: Int,
    isExpanded: Boolean,
    disabledTypes: Set<HomeSectionType>,
    onToggleExpand: () -> Unit,
    onToggleSection: (HomeSectionType, Boolean) -> Unit,
) {
    val shape = expressiveListShape(index, count, innerRadius = 0.dp)
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        androidx.compose.material3.ListItem(
            headlineContent = {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
                )
            },
            supportingContent = {
                val disabledCount = disabledTypes.size
                Text(
                    text = if (disabledCount == 0) "Both sections on" else "$disabledCount of 2 off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Icon(
                    if (isExpanded) Tabler.Outline.ChevronDown else Tabler.Outline.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(tvFocusState.focusModifier)
                .tvFocusIndicator(tvFocusState, shape)
                .clickable(onClick = onToggleExpand),
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)) {
                SubSectionToggle(
                    title = stringResource(Res.string.settings_subsection_latest_media),
                    subtitle = stringResource(Res.string.settings_subsection_latest_media_desc),
                    checked = HomeSectionType.LATEST_MEDIA !in disabledTypes,
                    onCheckedChange = { onToggleSection(HomeSectionType.LATEST_MEDIA, it) },
                )
                Spacer(Modifier.size(2.dp))
                SubSectionToggle(
                    title = stringResource(Res.string.settings_subsection_recently_added),
                    subtitle = stringResource(Res.string.settings_subsection_recently_added_desc),
                    checked = HomeSectionType.RECENTLY_ADDED !in disabledTypes,
                    onCheckedChange = { onToggleSection(HomeSectionType.RECENTLY_ADDED, it) },
                )
            }
        }
    }
}

@Composable
private fun SubSectionToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    val shape = expressiveListShape(0, 1, innerRadius = 0.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
