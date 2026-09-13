package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Folder
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_enable_all_libraries
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_library_access
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_libraries_found

@Composable
fun AccessSection(
    policy: ManagedUserPolicy,
    libraries: List<LibraryFolder>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = if (policy.enableAllFolders) 1 else 1 + libraries.size
    UserEditSection(title = stringResource(Res.string.admin_library_access), modifier = modifier) {
        SettingToggleItem(
            icon = Tabler.Outline.Folder,
            title = stringResource(Res.string.admin_enable_all_libraries),
            subtitle = if (policy.enableAllFolders) "" else "Restrict to the libraries below",
            checked = policy.enableAllFolders,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllFolders = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllFolders) {
            if (libraries.isEmpty()) {
                Text(
                    stringResource(Res.string.admin_no_libraries_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                libraries.forEachIndexed { i, folder ->
                    val checked = folder.id in policy.enabledFolders
                    SettingToggleItem(
                        icon = Tabler.Outline.Folder,
                        title = folder.name.ifBlank { folder.collectionType ?: folder.id },
                        subtitle = "",
                        checked = checked,
                        onCheckedChange = { enable ->
                            val next = if (enable) policy.enabledFolders + folder.id
                            else policy.enabledFolders - folder.id
                            onPolicyChange(policy.copy(enabledFolders = next))
                        },
                        index = i + 1, count = rows,
                    )
                }
            }
        }
    }
}
