package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val rows = if (policy.enableContentDeletion) 1 else 1 + libraries.size
    UserEditSection(title = "Media deletion from", modifier = modifier) {
        ToggleRow(
            label = "All libraries",
            description = if (policy.enableContentDeletion) "User may delete from anywhere" else "User may only delete from the libraries below",
            checked = policy.enableContentDeletion,
            onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableContentDeletion) {
            libraries.forEachIndexed { i, folder ->
                val checked = folder.id in policy.enableContentDeletionFromFolders
                ToggleRow(
                    label = folder.name.ifBlank { folder.collectionType ?: folder.id },
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enableContentDeletionFromFolders + folder.id
                        else policy.enableContentDeletionFromFolders - folder.id
                        onPolicyChange(policy.copy(enableContentDeletionFromFolders = next))
                    },
                    index = i + 1, count = rows,
                )
            }
        }
    }
}
