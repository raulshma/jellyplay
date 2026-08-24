package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_change_password

@Composable
fun PasswordSection(
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onChangePassword,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) { Text(stringResource(Res.string.admin_change_password)) }
}
