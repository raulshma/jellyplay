package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Folder
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_all_libraries
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_media_deletion_from

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
    UserEditSection(title = stringResource(Res.string.admin_media_deletion_from), modifier = modifier) {
        SettingToggleItem(
            icon = Tabler.Outline.Trash,
            title = stringResource(Res.string.admin_all_libraries),
            subtitle = if (policy.enableContentDeletion) "User may delete from anywhere" else "User may only delete from the libraries below",
            checked = policy.enableContentDeletion,
            onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableContentDeletion) {
            libraries.forEachIndexed { i, folder ->
                val checked = folder.id in policy.enableContentDeletionFromFolders
                SettingToggleItem(
                    icon = Tabler.Outline.Folder,
                    title = folder.name.ifBlank { folder.collectionType ?: folder.id },
                    subtitle = "",
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
