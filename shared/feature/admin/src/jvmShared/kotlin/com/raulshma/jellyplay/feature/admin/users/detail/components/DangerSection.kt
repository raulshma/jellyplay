package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete

@Composable
fun DangerSection(
    isSelf: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onDelete,
        enabled = !isSelf,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(if (isSelf) "Cannot delete your own account" else stringResource(Res.string.admin_delete))
    }
}
