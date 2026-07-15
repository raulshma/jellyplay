package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

/**
 * Per-library media-deletion permission. "All libraries" toggles
 * [ManagedUserPolicy.enableContentDeletion]; when off, the per-folder list
 * ([ManagedUserPolicy.enableContentDeletionFromFolders]) controls which
 * individual folders the user may delete from. Reuses the loaded library list.
 */
@Composable
fun DeletionFolderSection(
    policy: ManagedUserPolicy,
    libraries: List<LibraryFolder>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Media deletion from", style = MaterialTheme.typography.titleSmall)
        ToggleRow(
            label = "All libraries",
            checked = policy.enableContentDeletion,
            onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) },
        )
        if (!policy.enableContentDeletion) {
            libraries.forEach { folder ->
                val checked = folder.id in policy.enableContentDeletionFromFolders
                ToggleRow(
                    label = folder.name.ifBlank { folder.collectionType ?: folder.id },
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enableContentDeletionFromFolders + folder.id
                        else policy.enableContentDeletionFromFolders - folder.id
                        onPolicyChange(policy.copy(enableContentDeletionFromFolders = next))
                    },
                )
            }
        }
    }
}
