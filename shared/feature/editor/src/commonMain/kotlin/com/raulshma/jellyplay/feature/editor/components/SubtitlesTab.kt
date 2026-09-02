package com.raulshma.jellyplay.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
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
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.ui.components.SubtitleResultMetadata
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.feature.editor.EditorPickedFile
import com.raulshma.jellyplay.feature.editor.EditorUiState
import com.raulshma.jellyplay.feature.editor.EditorViewModel
import com.raulshma.jellyplay.feature.editor.rememberSubtitleFilePicker
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.editor.generated.resources.Res
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_action_cancel
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_field_language
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_change_file
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_delete_action
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_delete_message
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_delete_title
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_download
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_download_count_format
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_external
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_fallback_title
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_forced
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_forced_subtitle
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_frame_rate_format
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_hearing_impaired
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_label_forced
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_label_hi
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_label_mt
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_no_results
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_none_available
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_perfect_match
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_search
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_search_remote
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_search_title
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_select_file
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_unknown
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_upload
import com.raulshma.jellyplay.feature.editor.generated.resources.editor_subtitles_upload_title

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubtitlesTab(
    viewModel: EditorViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showUploadSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<MediaStream?>(null) }

    // Load configured providers once so the search sheet knows whether to show
    // provider filter chips + the merged provider list.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadConfiguredSubtitleProviders() }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            JellyPlayLoadingIndicator()
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
                Icon(Tabler.Outline.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.editor_subtitles_upload))
            }
            FilledTonalButton(onClick = { showSearchSheet = true }) {
                Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.editor_subtitles_search_remote))
            }
        }

        if (subtitles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.editor_subtitles_none_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                subtitle.displayTitle ?: subtitle.title ?: stringResource(Res.string.editor_subtitles_fallback_title, subtitle.index),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            val forcedLabel = stringResource(Res.string.editor_subtitles_forced)
                            val externalLabel = stringResource(Res.string.editor_subtitles_external)
                            val info = buildString {
                                subtitle.language?.let { append(it) }
                                subtitle.codec?.let { append(" • $it") }
                                if (subtitle.isForced) append(" • $forcedLabel")
                                if (subtitle.isExternal) append(" • $externalLabel")
                            }
                            if (info.isNotBlank()) Text(info)
                        },
                        trailingContent = {
                            if (subtitle.isExternal) {
                                IconButton(onClick = { showDeleteConfirm = subtitle }) {
                                    Icon(
                                        Tabler.Outline.Trash,
                                        contentDescription = stringResource(Res.string.editor_subtitles_delete_action),
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
            title = { Text(stringResource(Res.string.editor_subtitles_delete_title)) },
            text = { Text(stringResource(Res.string.editor_subtitles_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubtitle(subtitle.index)
                        showDeleteConfirm = null
                    },
                ) { Text(stringResource(Res.string.editor_subtitles_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(Res.string.editor_action_cancel)) }
            },
        )
    }

    if (showUploadSheet) {
        SubtitleUploadSheet(
            cultures = state.editorInfo?.cultures ?: emptyList(),
            onDismiss = { showUploadSheet = false },
            onUploadFile = { file, fileName, language, isForced, isHearingImpaired ->
                viewModel.uploadSubtitleFromFile(file, fileName, language, isForced, isHearingImpaired)
                showUploadSheet = false
            },
        )
    }

    if (showSearchSheet) {
        RemoteSubtitleSearchSheet(
            cultures = state.editorInfo?.cultures ?: emptyList(),
            results = state.remoteSubtitleResults,
            onSearch = { language ->
                // configuredSubtitleProviders always includes Jellyfin (server session),
                // so size > 1 means at least one external provider is on. Use the single
                // merged Jellyfin + external search in that case; otherwise the legacy
                // Jellyfin-only path avoids a wasted external round-trip.
                if (state.configuredSubtitleProviders.size > 1) {
                    viewModel.searchAllSubtitleProviders(language)
                } else {
                    viewModel.searchRemoteSubtitles(language)
                }
            },
            onDownload = { subtitleId -> viewModel.downloadRemoteSubtitle(subtitleId) },
            onDismiss = { showSearchSheet = false },
            providerResults = state.providerSubtitleResults,
            providerErrors = state.providerSubtitleErrors,
            configuredProviders = state.configuredSubtitleProviders,
            isSearchingProviders = state.isSearchingProviderSubtitles,
            isDownloadingProvider = state.isDownloadingProviderSubtitle,
            onDownloadProvider = { viewModel.downloadProviderSubtitle(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleUploadSheet(
    cultures: List<com.raulshma.jellyplay.core.model.CultureInfo>,
    onDismiss: () -> Unit,
    onUploadFile: (EditorPickedFile, String, String?, Boolean, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedLanguage by remember { mutableStateOf("") }
    var isForced by remember { mutableStateOf(false) }
    var isHearingImpaired by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<EditorPickedFile?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val filePicker = rememberSubtitleFilePicker { file ->
        selectedFile = file
        selectedFileName = file.fileName.ifBlank { "subtitle.srt" }
    }

    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                // TV sheet hides system bars, so no inset clearance is needed here
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_subtitles_upload_title), style = MaterialTheme.typography.headlineSmall)

                FilledTonalButton(
                    onClick = {
                        filePicker?.launch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedFile != null) stringResource(Res.string.editor_subtitles_change_file, selectedFileName) else stringResource(Res.string.editor_subtitles_select_file))
                }

                var langExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = { selectedLanguage = it },
                        label = { Text(stringResource(Res.string.editor_field_language)) },
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
                    Text(stringResource(Res.string.editor_subtitles_forced_subtitle))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHearingImpaired, onCheckedChange = { isHearingImpaired = it })
                    Text(stringResource(Res.string.editor_subtitles_hearing_impaired))
                }

                androidx.compose.material3.Button(
                    onClick = {
                        selectedFile?.let { file ->
                            onUploadFile(file, selectedFileName, selectedLanguage, isForced, isHearingImpaired)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFile != null && selectedLanguage.isNotBlank(),
                ) { Text(stringResource(Res.string.editor_subtitles_upload)) }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_subtitles_upload_title), style = MaterialTheme.typography.headlineSmall)

                FilledTonalButton(
                    onClick = {
                        filePicker?.launch()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedFile != null) stringResource(Res.string.editor_subtitles_change_file, selectedFileName) else stringResource(Res.string.editor_subtitles_select_file))
                }

                var langExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = { selectedLanguage = it },
                        label = { Text(stringResource(Res.string.editor_field_language)) },
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
                    Text(stringResource(Res.string.editor_subtitles_forced_subtitle))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHearingImpaired, onCheckedChange = { isHearingImpaired = it })
                    Text(stringResource(Res.string.editor_subtitles_hearing_impaired))
                }

                androidx.compose.material3.Button(
                    onClick = {
                        selectedFile?.let { file ->
                            onUploadFile(file, selectedFileName, selectedLanguage, isForced, isHearingImpaired)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFile != null && selectedLanguage.isNotBlank(),
                ) { Text(stringResource(Res.string.editor_subtitles_upload)) }
            }
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
    providerResults: List<SubtitleSearchResult>,
    providerErrors: Map<SubtitleProviderKind, String>,
    configuredProviders: Set<SubtitleProviderKind>,
    isSearchingProviders: Boolean,
    isDownloadingProvider: Boolean,
    onDownloadProvider: (SubtitleSearchResult) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchLanguage by remember { mutableStateOf("en") }
    var hasSearched by remember { mutableStateOf(false) }

    val isTv = com.raulshma.jellyplay.core.ui.tv.LocalTvMode.current
    if (isTv) {
        com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                // TV sheet hides system bars, so no inset clearance is needed here
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_subtitles_search_title), style = MaterialTheme.typography.headlineSmall)

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
                            label = { Text(stringResource(Res.string.editor_field_language)) },
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
                        Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.editor_subtitles_search))
                    }
                }

                // When external providers are configured the merged list
                // (ProviderResultsSection) already includes Jellyfin rows, so
                // the legacy Jellyfin-only list is redundant — render exactly one.
                if (configuredProviders.size <= 1) {
                    if (hasSearched && results.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.editor_subtitles_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
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
                if (configuredProviders.size > 1) {
                    ProviderResultsSection(
                        results = providerResults,
                        errors = providerErrors,
                        isLoading = isSearchingProviders,
                        isDownloading = isDownloadingProvider,
                        onDownload = onDownloadProvider,
                    )
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.editor_subtitles_search_title), style = MaterialTheme.typography.headlineSmall)

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
                            label = { Text(stringResource(Res.string.editor_field_language)) },
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
                        Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.editor_subtitles_search))
                    }
                }

                // When external providers are configured the merged list
                // (ProviderResultsSection) already includes Jellyfin rows, so
                // the legacy Jellyfin-only list is redundant — render exactly one.
                if (configuredProviders.size <= 1) {
                    if (hasSearched && results.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.editor_subtitles_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
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
                if (configuredProviders.size > 1) {
                    ProviderResultsSection(
                        results = providerResults,
                        errors = providerErrors,
                        isLoading = isSearchingProviders,
                        isDownloading = isDownloadingProvider,
                        onDownload = onDownloadProvider,
                    )
                }
            }
        }
    }
}

/**
 * Renders the merged cross-provider subtitle results (Jellyfin + Wyzie +
 * OpenSubtitles) with a provider label per row and per-provider error chips.
 * Only shown when external providers are configured; collapses to nothing
 * otherwise so the legacy Jellyfin-only list stands alone.
 */
@Composable
private fun ProviderResultsSection(
    results: List<SubtitleSearchResult>,
    errors: Map<SubtitleProviderKind, String>,
    isLoading: Boolean,
    isDownloading: Boolean,
    onDownload: (SubtitleSearchResult) -> Unit,
) {
    if (results.isEmpty() && errors.isEmpty() && !isLoading) return
    // Wrap the result rows in a scrollable container: inside the search sheet the
    // parent Column is not itself scrollable, so a long provider result list would
    // overflow the sheet and leave rows unreachable. Nested scroll with a heightIn
    // cap keeps the list tappable while respecting sheet bounds.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (errors.isNotEmpty()) {
            errors.forEach { (_, msg) ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                JellyPlayLoadingIndicator()
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(results, key = { it.provider to it.id }) { r ->
            ListItem(
                headlineContent = { Text(r.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        r.releaseName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        SubtitleResultMetadata(
                            result = r,
                            perfectMatchLabel = stringResource(Res.string.editor_subtitles_perfect_match),
                        )
                        Text(
                            editorProviderLabel(r.provider),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                trailingContent = {
                    FilledTonalButton(
                        onClick = { onDownload(r) },
                        enabled = !isDownloading,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 0.dp,
                        ),
                    ) {
                        Text(stringResource(Res.string.editor_subtitles_download))
                    }
                },
            )
        }
    }
}

@Composable
private fun editorProviderLabel(kind: SubtitleProviderKind): String = kind.localizedDisplayName()

@Composable
private fun RemoteSubtitleCard(
    subtitle: RemoteSubtitleInfo,
    onDownload: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                subtitle.name ?: stringResource(Res.string.editor_subtitles_unknown),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                val downloadsFormat = stringResource(Res.string.editor_subtitles_download_count_format)
                val frameRateFormat = stringResource(Res.string.editor_subtitles_frame_rate_format)
                val info = buildString {
                    subtitle.providerName?.let { append(it) }
                    subtitle.format?.let { append(" • $it") }
                    if (subtitle.downloadCount > 0) append(" • ${downloadsFormat.format(subtitle.downloadCount)}")
                    if (subtitle.frameRate != null) append(" • ${frameRateFormat.format(subtitle.frameRate)}")
                    if (subtitle.communityRating != null) append(" • ★ ${"%.1f".format(subtitle.communityRating)}")
                }
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (subtitle.isHashMatch) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.editor_subtitles_perfect_match), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isForced) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.editor_subtitles_label_forced), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isHearingImpaired) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.editor_subtitles_label_hi), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    if (subtitle.isMachineTranslated == true) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.editor_subtitles_label_mt), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDownload) {
                Icon(
                    Tabler.Outline.Download,
                    contentDescription = stringResource(Res.string.editor_subtitles_download),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
