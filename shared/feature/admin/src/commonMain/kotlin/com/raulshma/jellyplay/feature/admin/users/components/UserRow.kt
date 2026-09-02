package com.raulshma.jellyplay.feature.admin.users.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_administrator
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete_user_cd
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status_disabled

@Composable
fun UserRow(
    user: ManagedUser,
    isSelf: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "userRowScale",
    )
    val deleteFocusState = rememberTvFocusState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusIndicator(ShapeCache.smooth16)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The initials circle stays composed underneath the image: it doubles
            // as the loading placeholder and reappears whenever the URL is null
            // or the fetch errors (same fallback contract as the home
            // user-switcher avatar).
            var avatarFailed by remember(avatarUrl) { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                if (avatarUrl != null && !avatarFailed) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp),
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Error) avatarFailed = true
                        },
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (user.policy.isAdministrator) BadgeChip(stringResource(Res.string.admin_administrator), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    if (user.policy.isDisabled) BadgeChip(stringResource(Res.string.admin_status_disabled), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    if (isSelf) BadgeChip("You", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                enabled = !isSelf,
                modifier = Modifier
                    .size(36.dp)
                    .then(deleteFocusState.focusModifier)
                    .tvFocusIndicator(deleteFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(Res.string.admin_delete_user_cd),
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelf) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}
