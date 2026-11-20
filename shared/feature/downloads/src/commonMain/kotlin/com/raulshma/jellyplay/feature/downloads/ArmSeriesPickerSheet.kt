package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Radar
import androidx.compose.material3.Icon
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusGroupRestorer
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_action
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_empty
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_query_label
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_search_error
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_subtitle
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_arm_series_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The "Arm a series" picker (gh-225 follow-up): the Downloads screen's
 * manual-arm entry. A small search dialog restricted to SERIES library items
 * — the query rides the policy holder's [DownloadsSeriesPolicyActions.searchArmCandidates]
 * (the [com.raulshma.jellyplay.core.data.repository.LibraryFeed] seam, never
 * the network layer); picking a row arms it WITHOUT downloading anything
 * ([DownloadsSeriesPolicyActions.armSeries] — the store's manualArmSeries:
 * a follow-global write that also flips a NEVER tombstone back to armed,
 * so a deliberately stopped series is re-armable by hand).
 *
 * The query and results are dialog-local `remember` state — the whole sheet
 * dies on dismiss, so nothing crosses the VM bag (the structural/live split's
 * placement rule: a short-lived dialog's hot values have no outlife). Each
 * keystroke restarts the [LaunchedEffect] — the cancelled in-flight search
 * can never commit a stale result. The search itself waits for a settled
 * query (the SEARCH_DEBOUNCE_MS window, shared with the OfflineLibraryViewModel
 * debounce) so a typing burst fires one search instead of one per keystroke;
 * clearing the query still resets the results IMMEDIATELY (the blank branch
 * runs before the delay). A failed search renders its own error line — never
 * the "No series found" empty state, which would mask a connection failure
 * as zero matches.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ArmSeriesPickerSheet(
    onSearch: suspend (String) -> Result<List<MediaItem>>,
    onArm: (seriesId: String, seriesName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        // The blank reset stays immediate — only the non-blank search waits
        // out the typing-settle window (SEARCH_DEBOUNCE_MS, shared with the
        // OfflineLibraryViewModel query debounce). A keystroke restarts this
        // effect and cancels the pending delay or in-flight search, so the
        // settled query is the only one that reaches [onSearch].
        if (trimmed.isEmpty()) {
            results = emptyList()
            searchFailed = false
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(SEARCH_DEBOUNCE_MS)
        val outcome = onSearch(trimmed)
        searchFailed = outcome.isFailure
        results = outcome.getOrDefault(emptyList())
        isSearching = false
    }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.downloads_arm_series_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The search field owns the IME: the sheet's content clears it
                // so the field + results stay reachable while typing (the
                // SeriesDownloadSheet's imePadding precedent).
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.downloads_arm_series_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.downloads_arm_series_query_label)) },
                leadingIcon = { Icon(Tabler.Outline.Radar, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            when {
                isSearching -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                query.isNotBlank() && searchFailed -> Text(
                    text = stringResource(Res.string.downloads_arm_series_search_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )

                query.isNotBlank() && results.isEmpty() -> Text(
                    text = stringResource(Res.string.downloads_arm_series_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )

                results.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .tvFocusGroupRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        results,
                        key = { it.id },
                        contentType = { "armCandidate" },
                    ) { candidate ->
                        ArmCandidateRow(
                            candidate = candidate,
                            onArm = {
                                onArm(candidate.id, candidate.name)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One search result: the series name (id fallback mirrors the armed card) + the arm action. */
@Composable
private fun ArmCandidateRow(
    candidate: MediaItem,
    onArm: () -> Unit,
) {
    val focusState = rememberTvFocusState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
            .clickable(onClick = onArm)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name.ifBlank { candidate.id },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.year?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.downloads_arm_series_action),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
