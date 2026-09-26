package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.IdentifyQuery
import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cancel
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_apply
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_name
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_provider_id
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_replace_images
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_results_empty
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_search
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_identify_year
import org.jetbrains.compose.resources.stringResource

/**
 * The "Identify" sheet (jellyfin-web parity): edit the prefill (name, year,
 * provider id), run the provider search, and pick the real match — applying
 * replaces the item's metadata (and optionally all images) server-side.
 *
 * State comes from [IdentifyUiState] (the VM's [IdentifyActions] helper); the
 * sheet is pure rendering + edit/apply callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentifySheet(
    state: IdentifyUiState,
    onQueryChange: ((IdentifyQuery) -> IdentifyQuery) -> Unit,
    onSearch: () -> Unit,
    onApply: (result: IdentifyResult, replaceAllImages: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val query = state.query ?: return
    // The single editable provider id: the item's first known provider id
    // (tmdb/tvdb/imdb precedence by map order) with its key.
    val providerEntry = remember(query.providerIds) { query.providerIds.entries.firstOrNull() }
    var replaceAllImages by remember { mutableStateOf(true) }
    var selected by remember(state.results) { mutableStateOf<IdentifyResult?>(null) }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.detail_identify_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            // ── Prefill editors ──
            OutlinedTextField(
                value = query.name,
                onValueChange = { name -> onQueryChange { it.copy(name = name) } },
                label = { Text(stringResource(Res.string.detail_identify_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query.year?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val year = raw.filter { it.isDigit() }.take(4).toIntOrNull()
                        onQueryChange { it.copy(year = year) }
                    },
                    label = { Text(stringResource(Res.string.detail_identify_year)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = providerEntry?.value.orEmpty(),
                    onValueChange = { id ->
                        onQueryChange { it.copy(providerIds = it.providerIds.updatedProviderId(id.ifBlank { null })) }
                    },
                    label = { Text(providerEntry?.key?.uppercase() ?: stringResource(Res.string.detail_identify_provider_id)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSearch,
                enabled = query.name.isNotBlank() && !state.isSearching && !state.isApplying,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.detail_identify_search))
            }
            Spacer(Modifier.height(8.dp))

            // ── Results ──
            if (state.isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.hasSearched && state.results.isEmpty()) {
                Text(
                    text = stringResource(Res.string.detail_identify_results_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else if (state.results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results) { result ->
                        IdentifyResultRow(
                            result = result,
                            selected = selected == result,
                            onClick = { selected = result },
                        )
                    }
                }
            }

            if (selected != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.detail_identify_replace_images),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = replaceAllImages, onCheckedChange = { replaceAllImages = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.detail_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { selected?.let { onApply(it, replaceAllImages) } },
                        enabled = !state.isApplying,
                    ) {
                        Text(stringResource(Res.string.detail_identify_apply))
                    }
                }
            }
        }
    }
}

/**
 * Single-slot provider-id edit: replaces the value of the item's first known
 * provider key, or seeds the tmdb key when the item has none. A null clears.
 */
internal fun Map<String, String>.updatedProviderId(newValue: String?): Map<String, String> {
    val key = keys.firstOrNull() ?: "tmdb"
    return if (newValue.isNullOrBlank()) this - key else this + (key to newValue)
}

@Composable
private fun IdentifyResultRow(
    result: IdentifyResult,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val imageUrl = result.imageUrl
        if (imageUrl != null) {
            MediaImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 64.dp, height = 96.dp).clip(ShapeCache.smooth8),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(result.year?.toString(), result.searchProviderName).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
