package com.raulshma.jellyplay.feature.editor.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.editor.EditorUiState
import com.raulshma.jellyplay.feature.editor.EditorViewModel

@Composable
fun SubtitlesTab(
    viewModel: EditorViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    var showUploadSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<MediaStream?>(null) }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val subtitles = state.mediaDetail?.mediaSources
        ?.flatMap { it.mediaStreams }
        ?.filter { it.type == StreamType.SUBTITLE }
        ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { showUploadSheet = true }) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload")
            }
            FilledTonalButton(onClick = { showSearchSheet = true }) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search Remote")
            }
        }

        if (subtitles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No subtitles available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(subtitles, key = { it.index }) { subtitle ->
                    ListItem(
                        headlineContent = {
                            Text(
                                subtitle.displayTitle ?: subtitle.title ?: "Subtitle ${subtitle.index}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            val info = buildString {
                                subtitle.language?.let { append(it) }
                                subtitle.codec?.let { append(" • $it") }
                                if (subtitle.isForced) append(" • Forced")
                                if (subtitle.isExternal) append(" • External")
                            }
                            if (info.isNotBlank()) Text(info)
                        },
                        trailingContent = {
                            if (subtitle.isExternal) {
                                IconButton(onClick = { showDeleteConfirm = subtitle }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
    }

    showDeleteConfirm?.let { subtitle ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Subtitle") },
            text = { Text("Are you sure you want to delete this subtitle?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubtitle(subtitle.index)
                        showDeleteConfirm = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            },
        )
    }

    if (showUploadSheet) {
        SubtitleUploadSheet(
            cultures = state.editorInfo?.cultures ?: emptyList(),
            onDismiss = { showUploadSheet = false },
            onUpload = { bytes, fileName, language, isForced, isHearingImpaired ->
                viewModel.uploadSubtitle(bytes, fileName, language, isForced, isHearingImpaired)
                showUploadSheet = false
            },
        )
    }

    if (showSearchSheet) {
        RemoteSubtitleSearchSheet(
            cultures = state.editorInfo?.cultures ?: emptyList(),
            results = state.remoteSubtitleResults,
            onSearch = { language -> viewModel.searchRemoteSubtitles(language) },
            onDownload = { subtitleId -> viewModel.downloadRemoteSubtitle(subtitleId) },
            onDismiss = { showSearchSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleUploadSheet(
    cultures: List<com.raulshma.jellyplay.core.model.CultureInfo>,
    onDismiss: () -> Unit,
    onUpload: (ByteArray, String, String?, Boolean, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedLanguage by remember { mutableStateOf("") }
    var isForced by remember { mutableStateOf(false) }
    var isHearingImpaired by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedFile = it
            selectedFileName = it.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Upload Subtitle", style = MaterialTheme.typography.headlineSmall)

            FilledTonalButton(
                onClick = {
                    fileLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selectedFile != null) "Change: $selectedFileName" else "Select File (.srt, .ass, .ssa, .vtt)")
            }

            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = { selectedLanguage = it },
                    label = { Text("Language") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                ) {
                    cultures.forEach { culture ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${culture.displayName} (${culture.name})") },
                            onClick = {
                                selectedLanguage = culture.name
                                langExpanded = false
                            },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isForced, onCheckedChange = { isForced = it })
                Text("Forced subtitle")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isHearingImpaired, onCheckedChange = { isHearingImpaired = it })
                Text("Hearing impaired")
            }

            androidx.compose.material3.Button(
                onClick = {
                    selectedFile?.let { uri ->
                        TODO("Read bytes from URI in activity context - handled by parent")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedFile != null && selectedLanguage.isNotBlank(),
            ) { Text("Upload") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteSubtitleSearchSheet(
    cultures: List<com.raulshma.jellyplay.core.model.CultureInfo>,
    results: List<RemoteSubtitleInfo>,
    onSearch: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchLanguage by remember { mutableStateOf("en") }
    var hasSearched by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Search Remote Subtitles", style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var langExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = searchLanguage,
                        onValueChange = { searchLanguage = it },
                        label = { Text("Language") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false },
                    ) {
                        cultures.forEach { culture ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${culture.displayName} (${culture.name})") },
                                onClick = {
                                    searchLanguage = culture.name
                                    langExpanded = false
                                },
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = {
                        onSearch(searchLanguage)
                        hasSearched = true
                    },
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search")
                }
            }

            if (hasSearched && results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.id }) { subtitle ->
                        RemoteSubtitleCard(
                            subtitle = subtitle,
                            onDownload = { onDownload(subtitle.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteSubtitleCard(
    subtitle: RemoteSubtitleInfo,
    onDownload: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                subtitle.name ?: "Unknown",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                val info = buildString {
                    subtitle.providerName?.let { append(it) }
                    subtitle.format?.let { append(" • $it") }
                    if (subtitle.downloadCount > 0) append(" • ${subtitle.downloadCount} downloads")
                    if (subtitle.frameRate != null) append(" • ${subtitle.frameRate}fps")
                }
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (subtitle.isHashMatch) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("Perfect Match", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isForced) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("Forced", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isHearingImpaired) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("HI", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isMachineTranslated == true) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("MT", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
