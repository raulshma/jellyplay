package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_danger_zone
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_danger_zone_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sign_in
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_sign_in_desc

/**
 * Account tab: immediate-action buttons (change password, delete user).
 * These are never batched — no dirty badge ever appears here.
 */
@Composable
fun AccountTab(
    isSelf: Boolean,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UserEditSection(
            title = stringResource(Res.string.admin_sign_in),
            description = stringResource(Res.string.admin_sign_in_desc),
        ) {
            PasswordSection(onChangePassword = onChangePassword)
        }
        UserEditSection(
            title = stringResource(Res.string.admin_danger_zone),
            description = stringResource(Res.string.admin_danger_zone_desc),
        ) {
            DangerSection(isSelf = isSelf, onDelete = onDelete)
        }
    }
}
