package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack2
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * TV-safe bottom-sheet picker for adding the current movie/episode/series to a
 * Jellyfin collection. A mirror of [AddToPlaylistSheet] minus the pinned
 * "Watch Later" row (collections have no reserved bucket): the user's
 * collections follow, with "Create new collection" always last so its position
 * is predictable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToCollectionSheet(
    collections: List<CollectionSummary>,
    isLoading: Boolean,
    isAdding: Boolean,
    onPick: (CollectionSummary) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Cap the sheet body so it never grows past the viewport and pushes
            // its own rows off screen (the bottom sheet sizes to content).
            .heightIn(max = 560.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.detail_collection_picker_title),
            icon = Tabler.Outline.Stack2,
            onClose = onDismiss,
        )

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    JellyPlayLoadingIndicator(modifier = Modifier.size(28.dp))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (collections.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.detail_msg_no_collections),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        items(collections, key = { it.id }, contentType = { "collection" }) { collection ->
                            val count = collection.itemCount
                            val subtitle = if (count > 0) {
                                pluralStringResource(
                                    R.plurals.detail_collection_item_count,
                                    count,
                                    count,
                                )
                            } else {
                                null
                            }
                            CollectionRow(
                                name = collection.name,
                                subtitle = subtitle,
                                enabled = !isAdding,
                                onClick = { onPick(collection) },
                            )
                        }
                    }

                    // Create new — always last.
                    item(key = "create_new") {
                        CollectionRow(
                            name = stringResource(R.string.detail_collection_create_new),
                            subtitle = null,
                            enabled = !isAdding,
                            onClick = onCreateNew,
                            leadingIcon = Tabler.Outline.Plus,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isAdding,
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    }
}

@Composable
private fun CollectionRow(
    name: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector = Tabler.Outline.Stack2,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ShapeCache.smooth12,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.focusIndicator().fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
