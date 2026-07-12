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

@Composable
fun AccessSection(
    policy: ManagedUserPolicy,
    libraries: List<LibraryFolder>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(
            label = "Enable all libraries",
            checked = policy.enableAllFolders,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllFolders = it)) },
        )
        if (policy.enableAllFolders) {
            Text("All libraries enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        } else if (libraries.isEmpty()) {
            Text("No libraries found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        } else {
            libraries.forEach { folder ->
                val checked = folder.id in policy.enabledFolders
                ToggleRow(
                    label = folder.name.ifBlank { folder.collectionType ?: folder.id },
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enabledFolders + folder.id else policy.enabledFolders - folder.id
                        onPolicyChange(policy.copy(enabledFolders = next))
                    },
                )
            }
        }
    }
}
