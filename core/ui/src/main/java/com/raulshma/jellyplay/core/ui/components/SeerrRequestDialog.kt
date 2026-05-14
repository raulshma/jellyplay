package com.raulshma.jellyplay.core.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail

private const val TAG = "SeerrRequestDialog"

/**
 * Enhanced request dialog that mirrors the Seerr web UI request options.
 *
 * For movies: shows destination server, quality profile, root folder, tags.
 * For TV: shows all of the above plus season selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    ModalBottomSheet(
        onDismissRequest = { if (!isRequesting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f),
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
                        imageVector = if (isTv) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // ── Success state ──
            item {
                AnimatedVisibility(
                    visible = requestSuccess == true,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Request submitted successfully!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }
            }

            // ── Error state ──
            item {
                AnimatedVisibility(
                    visible = requestError != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = requestError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── Loading progress bar ──
            item {
                AnimatedVisibility(
                    visible = isRequesting,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Loading options…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
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
                            label = if (isTv) "Destination Server (Sonarr)" else "Destination Server (Radarr)",
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
                            label = "Quality Profile",
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
                            label = "Root Folder",
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
                                text = "Tags",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.7f),
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
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color.White.copy(alpha = 0.6f),
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
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.height(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "All Seasons",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        items(seasons.sortedBy { it.seasonNumber }, key = { it.seasonNumber }) { season ->
                            val seasonNumber = season.seasonNumber
                            val isSelected = selectAllSeasons || seasonNumber in selectedSeasonNumbers
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = season.name.ifBlank { "Season ${season.seasonNumber}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "${season.episodeCount} episodes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    } else if (isLoadingServices) {
                        // Show loading while services/seasons are being fetched
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Loading seasons…",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
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
                                if (requestSuccess == true) "Done" else "Close",
                                color = Color.White,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isRequesting,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val serverId = if (isTv) currentSonarrServer?.id else currentRadarrServer?.id
                                val profileId = currentProfiles?.getOrNull(selectedProfileIndex)?.id
                                val rootFolder = currentRootFolders?.getOrNull(selectedRootFolderIndex)?.path
                                val tags = selectedTags.toList().ifEmpty { null }

                                val resolvedSeasons = if (isTv) {
                                    if (selectAllSeasons) null // null = all seasons
                                    else selectedSeasonNumbers.toList().ifEmpty { null }
                                } else null

                                onConfirm(serverId, profileId, rootFolder, tags, resolvedSeasons)
                            },
                            enabled = !isRequesting && (!isTv || selectAllSeasons || selectedSeasonNumbers.isNotEmpty()),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "Request",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
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

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
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
                        text = options.getOrNull(selectedIndex) ?: "Select…",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    labelColor = Color.White,
                ),
                border = null,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Color(0xFF2A2A3E),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else Color.White,
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
