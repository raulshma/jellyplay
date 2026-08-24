package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Link
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginRepository
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add_repository_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_name
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_repositories
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_package_repositories
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_remove_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_url

@Composable
fun RepositoriesTab(
    repositories: List<PluginRepository>,
    isLoading: Boolean,
    onAddRepository: (name: String, url: String) -> Unit,
    onRemoveRepository: (index: Int) -> Unit,
    onToggleRepository: (index: Int, enabled: Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val isTv = LocalTvMode.current

    if (isLoading && repositories.isEmpty()) {
        ScreenLoadingState()
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.admin_package_repositories),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Icon(
                        Tabler.Outline.Plus,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.admin_add), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        itemsIndexed(repositories, key = { _, repo -> repo.url }) { index, repo ->
            RepositoryListItem(
                repository = repo,
                onToggle = { enabled -> onToggleRepository(index, enabled) },
                onRemove = { onRemoveRepository(index) },
            )
        }

        if (repositories.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.admin_no_repositories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
    }

    if (showAddDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                onAddRepository(name, url)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun RepositoryListItem(
    repository: PluginRepository,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repository.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = repository.isEnabled,
                onCheckedChange = onToggle,
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.admin_remove_cd),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_add_repository_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.admin_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCache.smooth16,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.admin_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCache.smooth16,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) {
                Text(stringResource(Res.string.admin_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.admin_cancel))
            }
        },
    )
}
