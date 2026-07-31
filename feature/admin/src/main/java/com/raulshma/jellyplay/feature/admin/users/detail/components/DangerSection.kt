package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.R

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
        Text(if (isSelf) "Cannot delete your own account" else stringResource(R.string.admin_delete))
    }
}
