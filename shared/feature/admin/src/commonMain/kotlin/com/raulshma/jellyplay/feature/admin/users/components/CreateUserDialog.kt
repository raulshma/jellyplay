package com.raulshma.jellyplay.feature.admin.users.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_create
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_name
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_new_user_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_password_optional

@Composable
fun CreateUserDialog(
    name: String,
    password: String,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // TV: land initial focus on the primary action; while it is disabled (blank
    // name) the first D-pad press finds the name field instead.
    val isTv = LocalTvMode.current
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) confirmFocusRequester.tryRequestFocus("create_user_confirm")
    }

    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_new_user_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.admin_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth())
                PasswordTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(Res.string.admin_password_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    contentType = ContentType.NewPassword,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
                modifier = Modifier.focusRequester(confirmFocusRequester),
            ) { Text(stringResource(Res.string.admin_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_cancel)) }
        },
    )
}
