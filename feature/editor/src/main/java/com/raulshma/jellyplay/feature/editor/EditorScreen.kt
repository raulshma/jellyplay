package com.raulshma.jellyplay.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.feature.editor.components.ImagesTab
import com.raulshma.jellyplay.feature.editor.components.MetadataTab
import com.raulshma.jellyplay.feature.editor.components.SubtitlesTab
import kotlinx.coroutines.launch
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(itemId) {
        viewModel.loadEditorData(itemId)
    }

    JellyPlayScreenScaffold(
        title = uiState.mediaDetail?.item?.name ?: "Edit Metadata",
        onBack = onBack,
        actions = {
            FilledTonalButton(
                onClick = { viewModel.saveMetadata() },
                enabled = uiState.isDirty && !uiState.isSaving,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(
                        Tabler.Outline.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.isSaving) "Saving..." else "Save")
            }
        },
    ) {
        val pagerState = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Metadata") },
                    icon = { Icon(Tabler.Outline.Edit, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Images") },
                    icon = { Icon(Tabler.Outline.Photo, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Subtitles") },
                    icon = { Icon(Tabler.Outline.Subtitles, contentDescription = null) },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> MetadataTab(viewModel = viewModel)
                    1 -> ImagesTab(viewModel = viewModel)
                    2 -> SubtitlesTab(viewModel = viewModel)
                }
            }
        }
    }
}
