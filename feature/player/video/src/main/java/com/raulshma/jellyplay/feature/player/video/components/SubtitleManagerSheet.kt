package com.raulshma.jellyplay.feature.player.video.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusState
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround

private enum class SubtitleManagerTab(val label: String) {
    DOWNLOAD("Download"),
    SEARCH("Search"),
    UPLOAD("Upload"),
}

/**
 * In-player subtitle manager. A single bottom sheet with three tabs:
 *
 * - **Download** — the server's default remote-subtitle browse + "Load from
 *   device" (the former [SubtitleDownloadSheet] surface).
 * - **Search** — language-scoped remote subtitle search (OpenSubtitles via the
 *   server), reusing the editor's search flow.
 * - **Upload** — upload a local subtitle file with language + forced/SDH flags,
 *   reusing the editor's upload flow via `MetadataApiClient.uploadSubtitle`.
 *
 * Replaces the single-purpose `SubtitleDownloadSheet` without leaving playback.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubtitleManagerSheet(
    // Download tab
    downloadSubtitles: List<RemoteSubtitleInfo>,
    isDownloading: Boolean,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
    // Search tab
    searchResults: List<RemoteSubtitleInfo>,
    isSearching: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    onSearch: (String) -> Unit,
    onDownloadSearched: (RemoteSubtitleInfo) -> Unit,
    // Upload tab
    isUploading: Boolean,
    onUpload: (Uri, String, String?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalTvMode.current
    // selectedTabIndex is remembered *saveably* so a config change (rotation,
    // locale switch) while the sheet is open restores the active tab.
    var selectedTab by rememberSaveable { mutableIntStateOf(SubtitleManagerTab.DOWNLOAD.ordinal) }
    val tabs = SubtitleManagerTab.entries
    // One focus requester per tab's primary action so D-pad focus lands on a
    // real, on-screen target whenever the tab changes — not just the Download
    // tab. Without this the Search/Upload tabs are unreachable on a TV remote.
    val downloadFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val uploadFocus = remember { FocusRequester() }
    val loadBtnFocus = rememberTvFocusState()

    LaunchedEffect(isTv, selectedTab) {
        if (!isTv) return@LaunchedEffect
        // Restore focus to the active tab's primary action on every tab change.
        val requester = when (tabs.getOrElse(selectedTab) { SubtitleManagerTab.DOWNLOAD }) {
            SubtitleManagerTab.DOWNLOAD -> downloadFocus
            SubtitleManagerTab.SEARCH -> searchFocus
            SubtitleManagerTab.UPLOAD -> uploadFocus
        }
        requester.tryRequestFocus("sheet")
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Get Subtitles",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                tab.label,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tabs.getOrElse(selectedTab) { SubtitleManagerTab.DOWNLOAD }) {
                SubtitleManagerTab.DOWNLOAD -> DownloadTab(
                    subtitles = downloadSubtitles,
                    isLoading = isDownloading,
                    onDownload = onDownload,
                    onLoadLocalFile = onLoadLocalFile,
                    isTv = isTv,
                    focusRequester = downloadFocus,
                    loadBtnFocus = loadBtnFocus,
                )
                SubtitleManagerTab.SEARCH -> SearchTab(
                    cultures = cultures,
                    defaultLanguage = defaultLanguage,
                    results = searchResults,
                    isLoading = isSearching,
                    hasSearched = hasSearched,
                    searchError = searchError,
                    onSearch = onSearch,
                    onDownload = onDownloadSearched,
                    isTv = isTv,
                    focusRequester = searchFocus,
                )
                SubtitleManagerTab.UPLOAD -> UploadTab(
                    cultures = cultures,
                    defaultLanguage = defaultLanguage,
                    isUploading = isUploading,
                    onUpload = onUpload,
                    isTv = isTv,
                    focusRequester = uploadFocus,
                )
            }
        }
    }
}

// region Download tab (formerly SubtitleDownloadSheet body) ------------------

@Composable
private fun DownloadTab(
    subtitles: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    onLoadLocalFile: () -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
    loadBtnFocus: TvFocusState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onLoadLocalFile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .ifElse(isTv, Modifier.focusRequester(focusRequester))
                .then(loadBtnFocus.focusModifier)
                .tvFocusIndicator(loadBtnFocus, ShapeCache.smoothPill),
        ) {
            Icon(
                imageVector = Tabler.Outline.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Load from device")
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            JellyPlayLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp),
            )
        } else if (subtitles.isEmpty()) {
            Text(
                "No remote subtitles available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.verticalWrapAround()) {
                // Composite key: providers (OpenSubtitles, etc.) occasionally
                // return duplicate `Id`s across sources, which previously
                // crashed with `IllegalArgumentException: Key "x" was already
                // used`. Combining the (stable where unique) id with the
                // index guarantees uniqueness without losing identity on
                // ordinary list updates.
                itemsIndexed(
                    subtitles,
                    key = { index, sub -> "${sub.id}_$index" },
                    contentType = { _, _ -> "subtitle" },
                ) { index, sub ->
                    SubtitleDownloadItem(
                        subtitle = sub,
                        isLast = index == subtitles.lastIndex,
                        itemCount = subtitles.size,
                        onDownload = { onDownload(sub) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleDownloadItem(
    subtitle: RemoteSubtitleInfo,
    isLast: Boolean,
    itemCount: Int,
    onDownload: () -> Unit,
) {
    val shape = when {
        itemCount == 1 -> ShapeCache.smooth16
        isLast -> com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(if (isLast) itemCount - 1 else 0, itemCount)
        else -> ShapeCache.smooth8
    }
    val focusState = rememberTvFocusState(focusedScale = 1.02f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable { onDownload() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                subtitle.name ?: subtitle.language ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subtitle.isHashMatch) {
                    Text(
                        "Perfect Match",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                subtitle.communityRating?.let {
                    Text(
                        "\u2605 ${"%.1f".format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.language?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.format?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subtitle.provider?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        subtitle.downloadCount.let { count ->
            if (count > 0) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Search tab ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTab(
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    results: List<RemoteSubtitleInfo>,
    isLoading: Boolean,
    hasSearched: Boolean,
    searchError: String?,
    onSearch: (String) -> Unit,
    onDownload: (RemoteSubtitleInfo) -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
) {
    var searchLanguage by rememberSaveable { mutableStateOf(defaultLanguage) }
    val searchBtnFocus = rememberTvFocusState()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageDropdown(
                language = searchLanguage,
                onLanguageChange = { searchLanguage = it },
                cultures = cultures,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { onSearch(searchLanguage) },
                modifier = Modifier
                    .ifElse(isTv, Modifier.focusRequester(focusRequester))
                    .then(searchBtnFocus.focusModifier)
                    .tvFocusIndicator(searchBtnFocus, ShapeCache.smoothPill),
            ) {
                Icon(Tabler.Outline.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search")
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> JellyPlayLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp),
            )
            // A failure must read as a failure, not as "no subtitles exist", so
            // the user is prompted to retry rather than change their query.
            searchError != null -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Search failed",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        searchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            hasSearched && results.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            results.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Pick a language and search to find subtitles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.verticalWrapAround(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    results,
                    key = { index, sub -> "${sub.id}_$index" },
                    contentType = { _, _ -> "searchedSubtitle" },
                ) { index, sub ->
                    SubtitleDownloadItem(
                        subtitle = sub,
                        isLast = index == results.lastIndex,
                        itemCount = results.size,
                        onDownload = { onDownload(sub) },
                    )
                }
            }
        }
    }
}

// endregion

// region Upload tab ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadTab(
    cultures: List<CultureInfo>,
    defaultLanguage: String,
    isUploading: Boolean,
    onUpload: (Uri, String, String?, Boolean, Boolean) -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester,
) {
    var selectedLanguage by rememberSaveable { mutableStateOf(defaultLanguage) }
    var isForced by rememberSaveable { mutableStateOf(false) }
    var isHearingImpaired by rememberSaveable { mutableStateOf(false) }
    // Stored as strings so they survive a config change mid-upload, consistent
    // with the saveable tab/language state above (a Uri isn't Saveable by default).
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf("") }
    val selectFileBtnFocus = rememberTvFocusState()

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedFile = it.toString()
            selectedFileName = it.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = { fileLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier
                .fillMaxWidth()
                .ifElse(isTv, Modifier.focusRequester(focusRequester))
                .then(selectFileBtnFocus.focusModifier)
                .tvFocusIndicator(selectFileBtnFocus, ShapeCache.smoothPill),
            enabled = !isUploading,
        ) {
            Icon(Tabler.Outline.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedFile != null) "Change: $selectedFileName" else "Select File (.srt, .ass, .ssa, .vtt)")
        }

        LanguageDropdown(
            language = selectedLanguage,
            onLanguageChange = { selectedLanguage = it },
            cultures = cultures,
            modifier = Modifier.fillMaxWidth(),
            label = "Language",
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isForced, onCheckedChange = { isForced = it }, enabled = !isUploading)
            Text("Forced subtitle")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isHearingImpaired, onCheckedChange = { isHearingImpaired = it }, enabled = !isUploading)
            Text("Hearing impaired")
        }

        androidx.compose.material3.Button(
            onClick = {
                selectedFile?.let { uriStr ->
                    onUpload(Uri.parse(uriStr), selectedFileName, selectedLanguage, isForced, isHearingImpaired)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUploading && selectedFile != null && selectedLanguage.isNotBlank(),
        ) {
            if (isUploading) {
                Text("Uploading…")
            } else {
                Text("Upload")
            }
        }
    }
}

// endregion

// region Shared language dropdown -------------------------------------------

/**
 * Editable language field backed by the server's [CultureInfo] list, mirroring
 * the editor's subtitle sheets. Free text is still allowed so users can type a
 * code the server didn't return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    language: String,
    onLanguageChange: (String) -> Unit,
    cultures: List<CultureInfo>,
    modifier: Modifier = Modifier,
    label: String = "Language",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = language,
            onValueChange = onLanguageChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cultures.forEach { culture ->
                DropdownMenuItem(
                    text = { Text("${culture.displayName} (${culture.name})") },
                    onClick = {
                        onLanguageChange(culture.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

// endregion
