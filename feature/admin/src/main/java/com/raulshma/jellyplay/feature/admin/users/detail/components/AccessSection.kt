package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

@Composable
fun AccessSection(
    policy: ManagedUserPolicy,
    libraries: List<LibraryFolder>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = if (policy.enableAllFolders) 1 else 1 + libraries.size
    UserEditSection(title = "Library access", modifier = modifier) {
        ToggleRow(
            label = "Enable access to all libraries",
            description = if (policy.enableAllFolders) null else "Restrict to the libraries below",
            checked = policy.enableAllFolders,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllFolders = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllFolders) {
            if (libraries.isEmpty()) {
                Text(
                    "No libraries found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                libraries.forEachIndexed { i, folder ->
                    val checked = folder.id in policy.enabledFolders
                    ToggleRow(
                        label = folder.name.ifBlank { folder.collectionType ?: folder.id },
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
