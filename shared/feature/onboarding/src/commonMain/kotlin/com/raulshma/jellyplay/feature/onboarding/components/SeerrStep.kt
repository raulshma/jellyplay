package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_api_key
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_api_key_placeholder
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_connected
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_connection
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_disconnect
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_discover
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_discover_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_email
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_email_placeholder
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_enable_integration
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_features
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_jellyfin
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_local
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_master_switch
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_password
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_password_placeholder_jellyfin
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_password_placeholder_seerr
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_recommendations
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_recommendations_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_regions
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_search_integration
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_search_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_server_url
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_server_url_placeholder
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_streaming_region
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_title
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_username
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_seerr_username_placeholder
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrStep(
    seerrPreferences: SeerrPreferences,
    onSetServerUrl: (String) -> Unit,
    onSetApiKey: (String) -> Unit,
    onSetAuthMethod: (SeerrAuthMethod) -> Unit,
    onSetUsername: (String) -> Unit,
    onSetEmail: (String) -> Unit,
    onSetPassword: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetSearchEnabled: (Boolean) -> Unit,
    onSetRecommendationsEnabled: (Boolean) -> Unit,
    onSetDiscoverEnabled: (Boolean) -> Unit,
    onSetStreamingRegion: (String) -> Unit,
    onSetDiscoverRegion: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember(seerrPreferences.serverUrl) { mutableStateOf(seerrPreferences.serverUrl) }
    var authMethod by remember(seerrPreferences.authMethod) { mutableStateOf(seerrPreferences.authMethod) }
    var apiKey by remember { mutableStateOf("") }
    var username by remember(seerrPreferences.username) { mutableStateOf(seerrPreferences.username) }
    var email by remember(seerrPreferences.email) { mutableStateOf(seerrPreferences.email) }
    var password by remember { mutableStateOf("") }
    val isConnected = seerrPreferences.serverUrl.isNotBlank()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = stringResource(Res.string.onboarding_seerr_title),
            subtitle = stringResource(Res.string.onboarding_seerr_subtitle),
            icon = Tabler.Outline.Puzzle,
            onNext = {},
        ) {
            Text(
                text = stringResource(Res.string.onboarding_seerr_connection),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    onSetServerUrl(it.trim())
                },
                label = { Text(stringResource(Res.string.onboarding_seerr_server_url)) },
                placeholder = { Text(stringResource(Res.string.onboarding_seerr_server_url_placeholder)) },
                leadingIcon = { Icon(Tabler.Outline.Link, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    onClick = {
                        authMethod = SeerrAuthMethod.API_KEY
                        onSetAuthMethod(SeerrAuthMethod.API_KEY)
                    },
                    selected = authMethod == SeerrAuthMethod.API_KEY,
                    icon = {},
                ) {
                    Text(stringResource(Res.string.onboarding_seerr_api_key))
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    onClick = {
                        authMethod = SeerrAuthMethod.JELLYFIN
                        onSetAuthMethod(SeerrAuthMethod.JELLYFIN)
                    },
                    selected = authMethod == SeerrAuthMethod.JELLYFIN,
                    icon = {},
                ) {
                    Text(stringResource(Res.string.onboarding_seerr_jellyfin))
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    onClick = {
                        authMethod = SeerrAuthMethod.LOCAL
                        onSetAuthMethod(SeerrAuthMethod.LOCAL)
                    },
                    selected = authMethod == SeerrAuthMethod.LOCAL,
                    icon = {},
                ) {
                    Text(stringResource(Res.string.onboarding_seerr_local))
                }
            }

            Spacer(Modifier.height(4.dp))

            when (authMethod) {
                SeerrAuthMethod.API_KEY -> {
                    PasswordTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            onSetApiKey(it.trim())
                        },
                        label = { Text(stringResource(Res.string.onboarding_seerr_api_key)) },
                        placeholder = { Text(stringResource(Res.string.onboarding_seerr_api_key_placeholder)) },
                        leadingIcon = { Icon(Tabler.Outline.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        contentType = ContentType.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SeerrAuthMethod.JELLYFIN -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            onSetUsername(it.trim())
                        },
                        label = { Text(stringResource(Res.string.onboarding_seerr_username)) },
                        placeholder = { Text(stringResource(Res.string.onboarding_seerr_username_placeholder)) },
                        leadingIcon = { Icon(Tabler.Outline.User, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username },
                    )
                    Spacer(Modifier.height(4.dp))
                    PasswordTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onSetPassword(it)
                        },
                        label = { Text(stringResource(Res.string.onboarding_seerr_password)) },
                        placeholder = { Text(stringResource(Res.string.onboarding_seerr_password_placeholder_jellyfin)) },
                        leadingIcon = { Icon(Tabler.Outline.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        contentType = ContentType.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SeerrAuthMethod.LOCAL -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            onSetEmail(it.trim())
                        },
                        label = { Text(stringResource(Res.string.onboarding_seerr_email)) },
                        placeholder = { Text(stringResource(Res.string.onboarding_seerr_email_placeholder)) },
                        leadingIcon = { Icon(Tabler.Outline.Mail, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    PasswordTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onSetPassword(it)
                        },
                        label = { Text(stringResource(Res.string.onboarding_seerr_password)) },
                        placeholder = { Text(stringResource(Res.string.onboarding_seerr_password_placeholder_seerr)) },
                        leadingIcon = { Icon(Tabler.Outline.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        contentType = ContentType.Password,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(4.dp))
                val disconnectFocusState = rememberTvFocusState(focusedScale = 1.04f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.CircleCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.onboarding_seerr_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            serverUrl = ""
                            apiKey = ""
                            username = ""
                            email = ""
                            password = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .then(disconnectFocusState.focusModifier)
                            .tvFocusIndicator(disconnectFocusState, ShapeCache.smooth12)
                            .height(36.dp),
                    ) {
                        Text(stringResource(Res.string.onboarding_seerr_disconnect), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.onboarding_seerr_features),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))

                OnboardingToggleRow(
                    title = stringResource(Res.string.onboarding_seerr_enable_integration),
                    subtitle = stringResource(Res.string.onboarding_seerr_master_switch),
                    checked = seerrPreferences.enabled,
                    onCheckedChange = onSetEnabled,
                )

                AnimatedVisibility(
                    visible = seerrPreferences.enabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OnboardingToggleRow(
                            title = stringResource(Res.string.onboarding_seerr_search_integration),
                            subtitle = stringResource(Res.string.onboarding_seerr_search_subtitle),
                            checked = seerrPreferences.searchEnabled,
                            onCheckedChange = onSetSearchEnabled,
                        )
                        OnboardingToggleRow(
                            title = stringResource(Res.string.onboarding_seerr_recommendations),
                            subtitle = stringResource(Res.string.onboarding_seerr_recommendations_subtitle),
                            checked = seerrPreferences.recommendationsEnabled,
                            onCheckedChange = onSetRecommendationsEnabled,
                        )
                        OnboardingToggleRow(
                            title = stringResource(Res.string.onboarding_seerr_discover),
                            subtitle = stringResource(Res.string.onboarding_seerr_discover_subtitle),
                            checked = seerrPreferences.discoverEnabled,
                            onCheckedChange = onSetDiscoverEnabled,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.onboarding_seerr_regions),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RegionChip(
                        label = stringResource(Res.string.onboarding_seerr_streaming_region),
                        selected = seerrPreferences.streamingRegion,
                        options = SeerrRegions,
                        onSelect = onSetStreamingRegion,
                        modifier = Modifier.weight(1f),
                    )
                    RegionChip(
                        label = stringResource(Res.string.onboarding_seerr_discover),
                        selected = seerrPreferences.discoverRegion,
                        options = SeerrRegions,
                        onSelect = onSetDiscoverRegion,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val SeerrRegions = listOf(
    "US" to "US",
    "GB" to "GB",
    "CA" to "CA",
    "AU" to "AU",
    "DE" to "DE",
    "FR" to "FR",
    "JP" to "JP",
    "KR" to "KR",
    "BR" to "BR",
    "IN" to "IN",
)

@Composable
private fun RegionChip(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.take(5).forEach { (code, _) ->
                val isSelected = code == selected
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ShapeCache.smooth8)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .focusIndicator()
                        .clickable { onSelect(code) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
