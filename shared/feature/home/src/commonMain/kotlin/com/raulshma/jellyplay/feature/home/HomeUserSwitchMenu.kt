package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * A single user-switcher entry, shared by the touch [DropdownMenuItem] and the
 * TV sheet renderer so the two menus can never drift apart — mirrors
 * `rememberMediaOptions` in feature/details.
 */
internal data class UserSwitchOption(
    val user: UserInfo,
    val avatarColor: Color,
    val onAvatarColor: Color,
    val isCurrent: Boolean,
    val onClick: () -> Unit,
)

/**
 * Builds the ordered list of user options for the quick user switcher.
 * Both renderers iterate this list. The current user is kept in the list (so
 * the user sees who's active) but flagged [UserSwitchOption.isCurrent] and
 * rendered non-clickable.
 */
@Composable
internal fun rememberUserSwitchOptions(
    users: List<UserInfo>,
    currentUserId: String?,
    onClose: () -> Unit,
    onUserSwitch: (String) -> Unit,
): List<UserSwitchOption> {
    val (containerTriplet, onContainerTriplet) = avatarColorPair(MaterialTheme.colorScheme)
    return remember(users, currentUserId, containerTriplet, onContainerTriplet, onUserSwitch) {
        users.map { user ->
            val (bg, fg) = pickAvatarColor(
                name = user.name,
                containerTriplet = containerTriplet,
                onContainerTriplet = onContainerTriplet,
            )
            UserSwitchOption(
                user = user,
                avatarColor = bg,
                onAvatarColor = fg,
                isCurrent = user.id == currentUserId,
                onClick = {
                    onClose()
                    onUserSwitch(user.id)
                },
            )
        }
    }
}

/**
 * Renders the user avatar — a colored circle with the first initial — used by
 * both the dock chip and each menu row. Matches the initials-avatar convention
 * from `ActiveSessionsSection` / `ActiveDevicesRow` (no user image URL builder
 * exists in the project; image fetching is a follow-up, not v1).
 */
@Composable
internal fun UserAvatar(
    name: String,
    size: Dp,
    avatarColor: Color,
    onAvatarColor: Color,
    modifier: Modifier = Modifier,
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = onAvatarColor,
        )
    }
}

/**
 * A touch-mode [DropdownMenuItem] row for a user. The current user shows a
 * trailing check and is disabled.
 */
@Composable
internal fun UserSwitchDropdownItem(option: UserSwitchOption) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    name = option.user.name,
                    size = 24.dp,
                    avatarColor = option.avatarColor,
                    onAvatarColor = option.onAvatarColor,
                )
                Text(
                    text = option.user.name.ifBlank { "?" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        },
        onClick = option.onClick,
        enabled = !option.isCurrent,
        trailingIcon = if (option.isCurrent) {
            { Icon(Tabler.Outline.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
    )
}

/**
 * A TV (D-pad focusable) row for a user, mirroring the `TvOptionItem` shape
 * from `DetailTopBar`. The current user shows a trailing check and is disabled.
 */
@Composable
internal fun UserSwitchTvRow(option: UserSwitchOption) {
    Surface(
        onClick = option.onClick,
        enabled = !option.isCurrent,
        shape = ShapeCache.smooth12,
        color = Color.Transparent,
        contentColor = if (option.isCurrent) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.focusIndicator().fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserAvatar(
                name = option.user.name,
                size = 28.dp,
                avatarColor = option.avatarColor,
                onAvatarColor = option.onAvatarColor,
            )
            Text(
                text = option.user.name.ifBlank { "?" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (option.isCurrent) {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Palette + index selection for the initials avatar. */
private fun pickAvatarColor(
    name: String,
    containerTriplet: List<Color>,
    onContainerTriplet: List<Color>,
): Pair<Color, Color> {
    val index = name.hashCode().mod(containerTriplet.size)
    return containerTriplet[index] to onContainerTriplet[index]
}

/** Reads the M3 container color triplet + matching "on" colors once. */
private fun avatarColorPair(cs: ColorScheme): Pair<List<Color>, List<Color>> {
    val containers = listOf(
        cs.primaryContainer,
        cs.secondaryContainer,
        cs.tertiaryContainer,
    )
    val onContainers = listOf(
        cs.onPrimaryContainer,
        cs.onSecondaryContainer,
        cs.onTertiaryContainer,
    )
    return containers to onContainers
}