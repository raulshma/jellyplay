package com.raulshma.jellyplay.core.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private const val TAG = "SeerrRequestDialog"

/**
 * Enhanced request dialog that mirrors the Seerr web UI request options.
 *
 * For movies: shows destination server, quality profile, root folder, tags.
 * For TV: shows all of the above plus season selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeerrRequestDialog(
    item: SeerrSearchItem,
    radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    seasons: List<SeerrSeason> = emptyList(),
    isLoadingServices: Boolean = false,
    isRequesting: Boolean = false,
    requestSuccess: Boolean? = null,
    requestError: String? = null,
    onConfirm: (
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
        seasons: List<Int>?,
    ) -> Unit = { _, _, _, _, _ -> },
    onDismiss: () -> Unit,
) {
    val isTv = item.mediaType.equals("tv", ignoreCase = true)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // State for selections
    var selectedServerIndex by remember(item.id) { mutableStateOf(0) }
    var selectedProfileIndex by remember(item.id) { mutableStateOf(0) }
    var selectedRootFolderIndex by remember(item.id) { mutableStateOf(0) }
    val selectedTags = remember(item.id) { mutableStateListOf<Int>() }
    var selectAllSeasons by remember(item.id) { mutableStateOf(true) }
    val selectedSeasonNumbers = remember(item.id) { mutableStateListOf<Int>() }

    // Current server details
    val currentRadarrServer = radarrServers.getOrNull(selectedServerIndex)
    val currentSonarrServer = sonarrServers.getOrNull(selectedServerIndex)
    val currentProfiles = if (isTv) currentSonarrServer?.profiles else currentRadarrServer?.profiles
    val currentRootFolders = if (isTv) currentSonarrServer?.rootFolders else currentRadarrServer?.rootFolders
    val currentTags = if (isTv) currentSonarrServer?.tags else currentRadarrServer?.tags
    val availableSeasonNumbers = remember(seasons) {
        seasons.mapNotNull { season -> season.seasonNumber }
    }

    LaunchedEffect(isTv, radarrServers, sonarrServers) {
        if (isTv) {
            if (sonarrServers.isNotEmpty()) {
                val defaultIndex = sonarrServers.indexOfFirst { server -> server.isDefault || server.server?.isDefault == true }.takeIf { it >= 0 } ?: 0
                selectedServerIndex = defaultIndex.coerceIn(0, sonarrServers.lastIndex)
            } else {
                selectedServerIndex = 0
            }
        } else {
            if (radarrServers.isNotEmpty()) {
                val defaultIndex = radarrServers.indexOfFirst { server -> server.isDefault || server.server?.isDefault == true }.takeIf { it >= 0 } ?: 0
                selectedServerIndex = defaultIndex.coerceIn(0, radarrServers.lastIndex)
            } else {
                selectedServerIndex = 0
            }
        }
    }

    // Auto-select defaults when server changes
    // Prefer nested server.defaults (from /service/ endpoint) over top-level fields
    LaunchedEffect(currentProfiles) {
        val defaultProfileId = if (isTv) {
            currentSonarrServer?.server?.activeProfileId ?: currentSonarrServer?.activeProfileId
        } else {
            currentRadarrServer?.server?.activeProfileId ?: currentRadarrServer?.activeProfileId
        }
        val defaultIdx = currentProfiles?.indexOfFirst { it.id == defaultProfileId }?.takeIf { it >= 0 } ?: 0
        selectedProfileIndex = defaultIdx.coerceAtMost((currentProfiles?.size ?: 1) - 1).coerceAtLeast(0)
    }

    LaunchedEffect(currentRootFolders) {
        val defaultDir = if (isTv) {
            currentSonarrServer?.server?.activeDirectory ?: currentSonarrServer?.activeDirectory
        } else {
            currentRadarrServer?.server?.activeDirectory ?: currentRadarrServer?.activeDirectory
        }
        val defaultIdx = currentRootFolders?.indexOfFirst { it.path == defaultDir }?.takeIf { it >= 0 } ?: 0
        selectedRootFolderIndex = defaultIdx.coerceAtMost((currentRootFolders?.size ?: 1) - 1).coerceAtLeast(0)
    }

    // Auto-select default tags when server changes
    LaunchedEffect(currentTags) {
        val defaultTags = if (isTv) {
            currentSonarrServer?.server?.activeTags
        } else {
            currentRadarrServer?.server?.activeTags
        }
        if (defaultTags != null && defaultTags.isNotEmpty() && selectedTags.isEmpty()) {
            selectedTags.clear()
            selectedTags.addAll(defaultTags)
        }
    }

    // Select all seasons by default when seasons become available
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && selectedSeasonNumbers.isEmpty()) {
            selectAllSeasons = true
        }
    }

    // Debug log
    LaunchedEffect(radarrServers, sonarrServers) {
        Log.d(TAG, "Dialog state: isTv=$isTv, radarrServers=${radarrServers.size}, sonarrServers=${sonarrServers.size}, " +
                "isLoadingServices=$isLoadingServices, seasons=${seasons.size}")
        if (radarrServers.isNotEmpty()) {
            Log.d(TAG, "Radarr[0]: profiles=${radarrServers[0].profiles.size}, rootFolders=${radarrServers[0].rootFolders.size}, tags=${radarrServers[0].tags.size}")
        }
        if (sonarrServers.isNotEmpty()) {
            Log.d(TAG, "Sonarr[0]: profiles=${sonarrServers[0].profiles.size}, rootFolders=${sonarrServers[0].rootFolders.size}, tags=${sonarrServers[0].tags.size}")
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val isTvDevice = LocalTvMode.current

    val content: @Composable ColumnScope.() -> Unit = {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Header ──
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (isTv) Tabler.Outline.DeviceTv else Tabler.Outline.Movie,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.year?.let {
                                Text(
                                    text = it.toString(),
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── Success state ──
                item {
                    AnimatedVisibility(
                        visible = requestSuccess == true,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Tabler.Outline.Check,
                                contentDescription = null,
                                tint = StatusColors.success,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.core_ui_seerr_request_success),
                                style = typography.bodyMedium,
                                color = StatusColors.success,
                            )
                        }
                    }
                }

                // ── Error state ──
                item {
                    AnimatedVisibility(
                        visible = requestError != null,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Tabler.Outline.X,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = requestError ?: "",
                                style = typography.bodySmall,
                                color = colorScheme.error,
                            )
                        }
                    }
                }

                // ── Loading progress bar ──
                item {
                    AnimatedVisibility(
                        visible = isRequesting,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        JellyPlayLinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.primary,
                        )
                    }
                }

                // ── Request options (only show when not in success/error state) ──
                if (requestSuccess == null && requestError == null) {
                    // Show loading indicator while service details are being fetched
                    if (isLoadingServices) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                JellyPlayCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.core_ui_seerr_loading_options),
                                    style = typography.bodyMedium,
                                    color = colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    // Destination Server
                    val serverNames = if (isTv) {
                        sonarrServers.map { server -> server.server?.name ?: server.name }
                    } else {
                        radarrServers.map { server -> server.server?.name ?: server.name }
                    }
                    if (serverNames.isNotEmpty()) {
                        item {
                            LabeledDropdown(
                                label = if (isTv) stringResource(R.string.core_ui_seerr_destination_server_sonarr) else stringResource(R.string.core_ui_seerr_destination_server_radarr),
                                options = serverNames,
                                selectedIndex = selectedServerIndex,
                                onSelected = {
                                    selectedServerIndex = it
                                    selectedTags.clear()
                                },
                            )
                        }
                    }

                    // Quality Profile
                    if (!currentProfiles.isNullOrEmpty()) {
                        item {
                            LabeledDropdown(
                                label = stringResource(R.string.core_ui_seerr_quality_profile),
                                options = currentProfiles.map { it.name },
                                selectedIndex = selectedProfileIndex.coerceAtMost(currentProfiles.size - 1),
                                onSelected = { selectedProfileIndex = it },
                            )
                        }
                    }

                    // Root Folder
                    if (!currentRootFolders.isNullOrEmpty()) {
                        item {
                            LabeledDropdown(
                                label = stringResource(R.string.core_ui_seerr_root_folder),
                                options = currentRootFolders.map { it.path },
                                selectedIndex = selectedRootFolderIndex.coerceAtMost(currentRootFolders.size - 1),
                                onSelected = { selectedRootFolderIndex = it },
                            )
                        }
                    }

                    // Tags
                    if (!currentTags.isNullOrEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.core_ui_seerr_tags),
                                    style = typography.labelLarge,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    currentTags.forEach { tag ->
                                        val isSelected = tag.id in selectedTags
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) selectedTags.remove(tag.id)
                                                else selectedTags.add(tag.id)
                                            },
                                            label = {
                                                Text(
                                                    text = tag.label,
                                                    style = typography.bodySmall,
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colorScheme.primary.copy(alpha = 0.2f),
                                                selectedLabelColor = colorScheme.primary,
                                                containerColor = colorScheme.onSurface.copy(alpha = 0.08f),
                                                labelColor = colorScheme.onSurfaceVariant,
                                            ),
                                            border = null,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Season Selection (TV only)
                    if (isTv) {
                        if (seasons.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapeCache.smooth12)
                                        .focusIndicator()
                                        .clickable {
                                            selectAllSeasons = !selectAllSeasons
                                            selectedSeasonNumbers.clear()
                                            if (!selectAllSeasons) {
                                                selectedSeasonNumbers.addAll(availableSeasonNumbers)
                                            }
                                        },
                                ) {
                                    Switch(
                                        checked = selectAllSeasons,
                                        onCheckedChange = {
                                            selectAllSeasons = it
                                            selectedSeasonNumbers.clear()
                                            if (!it) {
                                                selectedSeasonNumbers.addAll(availableSeasonNumbers)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = colorScheme.primary,
                                        ),
                                        modifier = Modifier.height(24.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.core_ui_seerr_all_seasons),
                                        style = typography.labelLarge,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            items(seasons.sortedBy { it.seasonNumber }, key = { it.seasonNumber }, contentType = { "season" }) { season ->
                                val seasonNumber = season.seasonNumber
                                val isSelected = selectAllSeasons || seasonNumber in selectedSeasonNumbers
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapeCache.smooth12)
                                        .focusIndicator()
                                        .clickable {
                                            if (selectAllSeasons) {
                                                selectAllSeasons = false
                                                selectedSeasonNumbers.clear()
                                                selectedSeasonNumbers.addAll(availableSeasonNumbers)
                                            }
                                            if (isSelected) selectedSeasonNumbers.remove(seasonNumber)
                                            else selectedSeasonNumbers.add(seasonNumber)
                                        }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (selectAllSeasons) {
                                                selectAllSeasons = false
                                                selectedSeasonNumbers.clear()
                                                selectedSeasonNumbers.addAll(availableSeasonNumbers)
                                            }
                                            if (it == true) selectedSeasonNumbers.add(seasonNumber)
                                            else selectedSeasonNumbers.remove(seasonNumber)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorScheme.primary,
                                        ),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = season.name.ifBlank { stringResource(R.string.core_ui_seerr_season, season.seasonNumber) },
                                            style = typography.bodyMedium,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.core_ui_seerr_episodes, season.episodeCount),
                                            style = typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else if (isLoadingServices) {
                            // Show loading while services/seasons are being fetched
                            item {
                                JellyPlayLinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.core_ui_seerr_loading_seasons),
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── Action Buttons ──
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (requestSuccess == true || requestError != null) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    if (requestSuccess == true) stringResource(R.string.core_done) else stringResource(R.string.core_close),
                                    color = colorScheme.onSurface,
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isRequesting,
                                shape = ShapeCache.smooth12,
                            ) {
                                Text(stringResource(R.string.core_cancel), color = colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val serverId = if (isTv) currentSonarrServer?.id else currentRadarrServer?.id
                                    val profileId = currentProfiles?.getOrNull(selectedProfileIndex)?.id
                                    val rootFolder = currentRootFolders?.getOrNull(selectedRootFolderIndex)?.path
                                    val tags = selectedTags.toList().ifEmpty { null }

                                    val resolvedSeasons = if (isTv) {
                                        if (selectAllSeasons) {
                                            availableSeasonNumbers
                                        } else {
                                            selectedSeasonNumbers.toList()
                                        }
                                    } else null

                                    onConfirm(serverId, profileId, rootFolder, tags, resolvedSeasons)
                                },
                                enabled = !isRequesting && (!isTv || (seasons.isNotEmpty() && (selectAllSeasons || selectedSeasonNumbers.isNotEmpty()))),
                                shape = ShapeCache.smooth12,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                if (isRequesting) {
                                    JellyPlayCircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    stringResource(R.string.core_ui_seerr_request),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (isTvDevice) {
        TvSafeSheet(
            onDismissRequest = { if (!isRequesting) onDismiss() },
            content = content,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = { if (!isRequesting) onDismiss() },
            sheetState = sheetState,
            shape = ShapeCache.smoothTop28,
            containerColor = colorScheme.surfaceContainer,
            dragHandle = { SheetDragHandle() },
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            SuggestionChip(
                onClick = { expanded = true },
                label = {
                    Text(
                        text = options.getOrNull(selectedIndex) ?: stringResource(R.string.core_ui_select),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = colorScheme.onSurface.copy(alpha = 0.1f),
                    labelColor = colorScheme.onSurface,
                ),
                border = null,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = colorScheme.surfaceContainerHigh,
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
