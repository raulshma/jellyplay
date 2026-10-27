package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_suggestions_count
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_suggestions_dismiss
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_suggestions_download
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_suggestions_download_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_suggestions_subtitle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Downloads screen's Ask-mode suggestion card (gh-181): the pending
 * "ask before download" prompts an auto-download pass recorded, one row per
 * episode with per-row Download / dismiss plus a bulk Download all. The host
 * gates the list item on the suggestions being non-empty, so the card never
 * renders hollow.
 */
@Composable
internal fun AutoDownloadSuggestionsCard(
    suggestions: List<AutoDownloadSuggestion>,
    onDownloadAll: () -> Unit,
    onDownload: (episodeId: String) -> Unit,
    onDismiss: (episodeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Tabler.Outline.Bell,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        Res.plurals.downloads_suggestions_count,
                        suggestions.size,
                        suggestions.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.downloads_suggestions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onDownloadAll, shape = ShapeCache.smoothPill) {
                Icon(
                    imageVector = Tabler.Outline.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.downloads_suggestions_download_all))
            }
        }

        Spacer(Modifier.height(8.dp))

        suggestions.forEach { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                onDownload = { onDownload(suggestion.episodeId) },
                onDismiss = { onDismiss(suggestion.episodeId) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: AutoDownloadSuggestion,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.seriesName + "  ·  " + seasonEpisodeLabel(suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onDownload,
            shape = ShapeCache.smoothPill,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(stringResource(Res.string.downloads_suggestions_download))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Tabler.Outline.X,
                contentDescription = stringResource(Res.string.downloads_suggestions_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "SxxEyy" from the suggestion's season/episode numbers (0-padded, the list's convention). */
private fun seasonEpisodeLabel(suggestion: AutoDownloadSuggestion): String =
    "S" + suggestion.seasonNumber.toString().padStart(2, '0') +
        "E" + suggestion.episodeNumber.toString().padStart(2, '0')
