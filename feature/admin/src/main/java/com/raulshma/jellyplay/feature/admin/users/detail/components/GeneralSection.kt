package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.pressScaleValueForLogic

@Composable
fun GeneralSection(
    name: String,
    policy: ManagedUserPolicy,
    isSelf: Boolean,
    isLastAdmin: Boolean,
    onNameChange: (String) -> Unit,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        UserEditSection(
            title = "Identity",
            description = "Control who this user is and how they appear.",
        ) {
            ToggleRow(
                label = "Administrator",
                description = when {
                    isSelf -> "Cannot change your own admin status"
                    isLastAdmin -> "Cannot remove the last administrator"
                    else -> "Can manage the server and other users"
                },
                checked = policy.isAdministrator,
                enabled = !isSelf && !isLastAdmin,
                onCheckedChange = { onPolicyChange(policy.copy(isAdministrator = it)) },
                index = 0, count = 4,
            )
            ToggleRow(
                label = "Active",
                description = if (isSelf) "Cannot disable yourself" else "Disabled users cannot sign in",
                checked = !policy.isDisabled,
                enabled = !isSelf,
                onCheckedChange = { onPolicyChange(policy.copy(isDisabled = !it)) },
                index = 1, count = 4,
            )
            ToggleRow(
                label = "Hidden",
                description = "Hide this user from the login screen",
                checked = policy.isHidden,
                onCheckedChange = { onPolicyChange(policy.copy(isHidden = it)) },
                index = 2, count = 4,
            )
            ToggleRow(
                label = "Allow user preference access",
                description = "Let the user change their own display preferences",
                checked = policy.enableUserPreferenceAccess,
                onCheckedChange = { onPolicyChange(policy.copy(enableUserPreferenceAccess = it)) },
                index = 3, count = 4,
            )
        }
    }
}

/**
 * A toggle row rendered as a grouped MD3 [ListItem]: label + optional
 * [description] on the leading column, a [Switch] in the trailing slot. When
 * [index]/[count] are supplied, rows clip to [expressiveListShape] so adjacent
 * rows read as one cohesive rounded group (first/last get the outer radius,
 * middles the inner radius). Row-tap toggles, with a press-scale feedback.
 */
@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = isReducedMotion()
    val scale = pressScaleValueForLogic(pressed, reducedMotion)
    val shape = if (count > 1) expressiveListShape(index, count) else ShapeCache.smooth12

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = description?.let { desc ->
            {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    )
}
