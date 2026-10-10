package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.lightModeHairlineBorder
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_player
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_browse_favorites
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_recents
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_downloads_storage
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_filter_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_matches
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_no_matches_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_recents_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_security
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management

/**
 * The shared column container for settings-search results (live matches and the
 * recents list). Both lists share the same padding, spacing, and TV back-key
 * handling (dismiss search + refocus the settings list); only the row content
 * differs, passed as [content]. Extracted so the container wiring can't drift
 * between the two branches the way the row rendering already can't.
 */
@Composable
private fun SearchResultsColumn(
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(Modifier.onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) { onBack() }
                    true
                },
            )),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        content = content,
    )
}

private fun highlightText(
    text: String,
    query: String,
    highlightColor: Color,
): androidx.compose.ui.text.AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val index = text.indexOf(trimmed, ignoreCase = true)
    if (index < 0) return androidx.compose.ui.text.AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(
            SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold,
            )
        ) {
            append(text.substring(index, index + trimmed.length))
        }
        append(text.substring(index + trimmed.length))
    }
}

/**
 * A single resolved settings-search result row, shared by the live search results
 * and the recent-settings list so both render identically (leading icon, title,
 * subtitle, category/advanced pills, chevron, expressive list shape, TV focus) and
 * share one tap handler.
 */
@Composable
private fun SettingsSearchResultRow(
    item: ResolvedSettingsItem,
    index: Int,
    count: Int,
    advancedBadgeLabel: String,
    query: String = "",
    onClick: () -> Unit,
) {
    val shape = com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(index, count, innerRadius = 0.dp)
    val itemTvFocusState = rememberTvFocusState(focusedScale = 1.01f)
    ListItem(
        headlineContent = {
            Text(
                text = highlightText(item.title, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = highlightText(item.subtitle, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (item.isAdvanced) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = advancedBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Icon(
                    imageVector = Tabler.Outline.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = groupedItemContainerColor(),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .lightModeHairlineBorder(shape)
            .then(itemTvFocusState.focusModifier)
            .tvFocusIndicator(itemTvFocusState, shape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else groupedItemContainerColor(darkAlpha = 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else hairlineBorderColor(),
        ),
        modifier = modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private data class QuickCategory(
    val title: String,
    val icon: ImageVector,
    val route: Route,
)

@Composable
private fun SettingsQuickCategoriesGrid(
    onNavigate: (Route) -> Unit,
    onDismissSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = listOf(
        QuickCategory(stringResource(Res.string.settings_appearance), Tabler.Outline.Palette, Route.AppearanceSettings()),
        QuickCategory(stringResource(Res.string.settings_playback), Tabler.Outline.PlayerPlay, Route.PlaybackSettings()),
        QuickCategory(stringResource(Res.string.settings_audio_player), Tabler.Outline.Headphones, Route.AudioSettings()),
        QuickCategory(stringResource(Res.string.settings_language_subtitles), Tabler.Outline.Subtitles, Route.LanguageSettings()),
        QuickCategory(stringResource(Res.string.settings_downloads_storage), Tabler.Outline.Download, Route.StorageSettings()),
        QuickCategory(stringResource(Res.string.settings_security), Tabler.Outline.ShieldLock, Route.SecuritySettings()),
        QuickCategory(stringResource(Res.string.settings_server_management), Tabler.Outline.Server, Route.ServerManagement(null)),
        QuickCategory(stringResource(Res.string.settings_browse_favorites), Tabler.Outline.Heart, Route.Favorites),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.chunked(2).forEach { rowPair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPair.forEach { cat ->
                    val catFocusState = rememberTvFocusState(focusedScale = 1.02f)
                    Surface(
                        onClick = {
                            onDismissSearch()
                            onNavigate(cat.route)
                        },
                        shape = ShapeCache.smooth16,
                        color = groupedItemContainerColor(darkAlpha = 0.45f),
                        border = BorderStroke(1.dp, hairlineBorderColor().copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .then(catFocusState.focusModifier)
                            .tvFocusIndicator(catFocusState, ShapeCache.smooth16)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(ShapeCache.smooth10)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (rowPair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The search-active pane: the category filter chip row above the four-way
 * result box (live matches / no-matches + quick categories / recent settings /
 * browse hint). Relocated verbatim from [SettingsScreen]'s inline
 * `if (searchPanel.isSearchActive)` subtree; [modifier] carries the
 * Column-scope `weight(1f)` the result box needs (the chips row stays
 * unweighted above it, exactly as before).
 */
@Composable
internal fun SettingsSearchResultsPane(
    searchPanel: SettingsSearchPanelState,
    availableCategories: List<String>,
    displayItems: List<ResolvedSettingsItem>,
    recentItems: List<ResolvedSettingsItem>,
    advancedBadgeLabel: String,
    onResultClick: (ResolvedSettingsItem) -> Unit,
    onDismissSearch: () -> Unit,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
        if (availableCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "cat_all") {
                    SettingsCategoryChip(
                        label = stringResource(Res.string.settings_filter_all),
                        selected = searchPanel.selectedCategory == null,
                        onClick = { searchPanel.selectAllCategories() },
                    )
                }
                items(availableCategories, key = { it }) { cat ->
                    SettingsCategoryChip(
                        label = cat,
                        selected = searchPanel.selectedCategory == cat,
                        onClick = { searchPanel.toggleCategory(cat) },
                    )
                }
            }
        }

        Box(
            // The Column-scope weight(1f) arrives via [modifier] from the call
            // site (the pane itself is not a ColumnScope).
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)
        ) {
            when {
                searchPanel.searchQuery.isNotBlank() && displayItems.isNotEmpty() -> {
                    SearchResultsColumn(
                        onBack = onDismissSearch
                    ) {
                        itemsIndexed(displayItems, key = { _, item -> item.id }, contentType = { _, _ -> "searchResult" }) { index, item ->
                            SettingsSearchResultRow(
                                item = item,
                                query = searchPanel.searchQuery,
                                index = index,
                                count = displayItems.size,
                                advancedBadgeLabel = advancedBadgeLabel,
                                onClick = { onResultClick(item) },
                            )
                        }
                    }
                }
                searchPanel.searchQuery.isNotBlank() && displayItems.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 24.dp)
                    ) {
                        item(key = "no_matches_banner") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Tabler.Outline.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(Res.string.settings_no_matches),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(Res.string.settings_no_matches_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (searchPanel.selectedCategory != null) {
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(onClick = { searchPanel.selectAllCategories() }) {
                                        Text(stringResource(Res.string.settings_filter_all))
                                    }
                                }
                            }
                        }
                        item(key = "categories_header") {
                            Text(
                                text = stringResource(Res.string.settings_browse_categories),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        item(key = "categories_grid") {
                            SettingsQuickCategoriesGrid(
                                onNavigate = onNavigate,
                                onDismissSearch = onDismissSearch
                            )
                        }
                    }
                }
                searchPanel.searchQuery.isBlank() && recentItems.isNotEmpty() -> {
                    SearchResultsColumn(
                        onBack = onDismissSearch
                    ) {
                        item(key = "recents_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_recents_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(onClick = { searchPanel.clearRecents() }) {
                                    Text(stringResource(Res.string.settings_clear_recents))
                                }
                            }
                        }
                        itemsIndexed(recentItems, key = { _, item -> item.id }, contentType = { _, _ -> "recentResult" }) { index, item ->
                            SettingsSearchResultRow(
                                item = item,
                                query = "",
                                index = index,
                                count = recentItems.size,
                                advancedBadgeLabel = advancedBadgeLabel,
                                onClick = { onResultClick(item) },
                            )
                        }
                        item(key = "browse_cats_header") {
                            Text(
                                text = stringResource(Res.string.settings_browse_categories),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        item(key = "browse_cats_grid") {
                            SettingsQuickCategoriesGrid(
                                onNavigate = onNavigate,
                                onDismissSearch = onDismissSearch
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = adaptiveInfo.contentPadding(LocalTvMode.current)),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item(key = "browse_header") {
                            Text(
                                text = stringResource(Res.string.settings_browse_categories),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        item(key = "browse_grid") {
                            SettingsQuickCategoriesGrid(
                                onNavigate = onNavigate,
                                onDismissSearch = onDismissSearch
                            )
                        }
                        item(key = "hint") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

}
