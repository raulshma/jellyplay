package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.buildUserImageUrl
import com.raulshma.jellyplay.core.ui.animation.pressScale
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.groupedItemContainerColor
import com.raulshma.jellyplay.core.designsystem.theme.hairlineBorderColor
import com.raulshma.jellyplay.core.designsystem.theme.lightModeHairlineBorder
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.settingsGroupContainerColor
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_admin_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connected_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_member_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_power_user_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_power_user_mode_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user

internal val LocalAnimateSettingsEntrance = staticCompositionLocalOf { false }

/**
 * Pill-shaped admin/member role badge. Used in the account [SettingsGroup] header and the
 * [SettingsProfileBanner]. Defaults mirror the group-header padding; the banner passes tighter
 * padding via [horizontalPadding]/[verticalPadding].
 */
@Composable
internal fun RoleBadge(
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val container = if (isAdmin) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val onContainer = if (isAdmin) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (isAdmin) Tabler.Outline.Shield else Tabler.Outline.User,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(if (isAdmin) Res.string.settings_admin_badge else Res.string.settings_member_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = onContainer,
        )
    }
}

@Composable
internal fun SettingsProfileBanner(
    userName: String,
    currentUser: UserInfo?,
    serverAddress: String?,
    isAdmin: Boolean,
    onNewsletterClick: () -> Unit,
    onUserManagementClick: () -> Unit,
    onServerManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarUrl = remember(serverAddress, currentUser) {
        if (!serverAddress.isNullOrBlank() && currentUser != null) {
            val url = buildUserImageUrl(
                baseUrl = serverAddress,
                userId = currentUser.id,
                imageType = "Primary",
                maxWidth = 160,
                tag = currentUser.primaryImageTag,
            )
            url.ifBlank { null }
        } else null
    }

    val isLight = LocalIsLightTheme.current
    val isTv = LocalTvMode.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLight) Modifier.shadow(2.dp, ShapeCache.smooth24) else Modifier)
            .clip(ShapeCache.smooth24)
            .background(settingsGroupContainerColor())
            .lightModeHairlineBorder(ShapeCache.smooth24)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Profile Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar (Clean circle without flashy border)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = if (userName.isNotBlank()) userName.take(1).uppercase() else "U",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name, Role & Server Status
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RoleBadge(
                        isAdmin = isAdmin,
                        horizontalPadding = 6.dp,
                        verticalPadding = 1.dp,
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (!isTv) Modifier.clickable(onClick = onServerManagementClick)
                            else Modifier,
                        )
                        .padding(vertical = 1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                    )
                    Text(
                        text = serverAddress?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.settings_connected_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Trailing Action Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconButton(
                    onClick = onUserManagementClick,
                    icon = Tabler.Outline.Users,
                    contentDescription = stringResource(Res.string.settings_switch_user),
                    iconSize = 19.dp,
                )
                SettingsIconButton(
                    onClick = onNewsletterClick,
                    icon = Tabler.Outline.News,
                    contentDescription = stringResource(Res.string.settings_newsletter_cd),
                    iconSize = 19.dp,
                )
            }
        }
    }
}

/**
 * Compact hero-styled toggle card for Power User Mode. Visually contiguous with the profile
 * banner above it and the [SettingsGroup] cards below — same smooth24 container,
 * [settingsGroupContainerColor] fill, hairline border and light-mode shadow — but a single
 * compact row: the group-header icon tile (tints primary while enabled) and a shrunken switch
 * instead of a full-height ListItem, so the toggle reads as part of the hero cluster rather
 * than a detached list row.
 */
@Composable
internal fun PowerUserModeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val interactionSource = remember { MutableInteractionSource() }
    val confirmHaptic = rememberConfirmHaptic()

    val iconTint by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "powerUserIconTint",
    )
    val iconTileColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "powerUserIconTile",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLight) Modifier.shadow(2.dp, ShapeCache.smooth24) else Modifier)
            .pressScale(
                interactionSource = interactionSource,
                defaultScale = 0.98f,
                spec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
            .clip(ShapeCache.smooth24)
            .background(settingsGroupContainerColor())
            .lightModeHairlineBorder(ShapeCache.smooth24)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth24)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                confirmHaptic()
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(ShapeCache.smooth12)
                .background(iconTileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.AdjustmentsHorizontal,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_power_user_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(Res.string.settings_power_user_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // The row itself is the tap target; the switch is a display-only affordance shrunk
        // below M3's 48dp minimum so the card stays one compact row tall.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.8f),
            )
        }
    }
}

/**
 * Section scaffold: one lazy item wrapped in the staggered entrance. The
 * (phone, tv) steps derive from the ordered SETTINGS_ENTRANCE_SECTIONS
 * list — pinned by SettingsEntranceStepsTest to equal the hand-typed
 * literals this replaced — so inserting a section renumbers the followers
 * automatically. Hoisted out of [SettingsScreen] (it used to be a local fun
 * closing over the screen's isTv) so it reads the same value as an explicit
 * parameter; the entrance-step key check still throws on undeclared keys.
 */
internal fun LazyListScope.settingsSection(
    key: String,
    isTv: Boolean,
    content: @Composable () -> Unit,
) {
    val steps = requireNotNull(settingsEntranceStep(key)) {
        "undeclared settings entrance section '$key' — add it to SETTINGS_ENTRANCE_SECTIONS"
    }
    item(key = key) {
        AnimatedSettingsEntrance(if (isTv) steps.tv else steps.phone) { content() }
    }
}

@Composable
private fun AnimatedSettingsEntrance(
    index: Int,
    content: @Composable () -> Unit,
) {
    val animate = LocalAnimateSettingsEntrance.current
    var visible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(animate) }

    LaunchedEffect(animate) {
        if (animate && !visible) {
            kotlinx.coroutines.delay(index * 20L)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        ) + expandVertically(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
    ) {
        content()
    }
}

@Composable
internal fun SettingsIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val isTv = LocalTvMode.current
    if (isTv) {
        val focusState = rememberTvFocusState(focusedScale = 1.15f)
        Box(
            modifier = modifier
                .size(36.dp)
                .then(focusState.focusModifier)
                .tvFocusIndicator(focusState, ShapeCache.smooth10)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}

@Composable
internal fun SettingsTvCollapsedSearchRow(
    onSearchClicked: () -> Unit,
    searchBoxFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = hairlineBorderColor(),
                shape = ShapeCache.smooth16
            )
            .background(
                color = groupedItemContainerColor(darkAlpha = 0.4f),
                shape = ShapeCache.smooth16
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searchBoxFocusState = rememberTvFocusState(focusedScale = 1.02f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(searchBoxFocusRequester)
                .then(searchBoxFocusState.focusModifier)
                .tvFocusIndicator(searchBoxFocusState, ShapeCache.smooth12)
                .clickable(onClick = onSearchClicked)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Tabler.Outline.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.settings_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
