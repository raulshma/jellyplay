package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_clear
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_find
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_plain
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_search_placeholder
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_synced
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSearchSheet(
    artist: String,
    title: String,
    searchResults: List<LrcLibTrack>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onApplyTrack: (LrcLibTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf(if (artist.isNotBlank()) "$artist - $title" else title) }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.audio_lyrics_find),
                icon = Tabler.Outline.Search,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(Res.string.audio_lyrics_search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Tabler.Outline.X, stringResource(Res.string.audio_lyrics_clear), modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.audio_cancel)) }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { onSearch(searchQuery) },
                    enabled = searchQuery.isNotBlank() && !isSearching,
                ) {
                    if (isSearching) {
                        JellyPlayLoadingIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(stringResource(Res.string.audio_search))
                    }
                }
            }

            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 400.dp),
                ) {
                    items(searchResults.size, key = { it }, contentType = { "searchResult" }) { index ->
                        val track = searchResults[index]
                        Card(
                            onClick = { onApplyTrack(track) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.trackName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        track.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (track.hasSyncedLyrics) {
                                    Text(
                                        stringResource(Res.string.audio_lyrics_synced),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                ShapeCache.smooth8,
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                } else if (track.hasPlainLyrics) {
                                    Text(
                                        stringResource(Res.string.audio_lyrics_plain),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
