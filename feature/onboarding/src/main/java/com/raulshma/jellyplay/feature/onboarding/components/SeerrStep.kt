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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences

@Composable
fun SeerrStep(
    seerrPreferences: SeerrPreferences,
    onSetServerUrl: (String) -> Unit,
    onSetApiKey: (String) -> Unit,
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
    var apiKey by remember(seerrPreferences.apiKey) { mutableStateOf(seerrPreferences.apiKey) }
    val isConnected = seerrPreferences.serverUrl.isNotBlank() && seerrPreferences.apiKey.isNotBlank()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingStepScaffold(
            title = "Integrations",
            subtitle = "Optionally connect Seerr (Jellyseerr/Overseerr) for enhanced features. You can skip this step.",
            icon = Tabler.Outline.Puzzle,
            onNext = {},
        ) {
            Text(
                text = "Connection",
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
                label = { Text("Server URL") },
                placeholder = { Text("http://localhost:5055") },
                leadingIcon = { Icon(Tabler.Outline.Link, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    onSetApiKey(it.trim())
                },
                label = { Text("API Key") },
                placeholder = { Text("Enter your Seerr API key") },
                leadingIcon = { Icon(Tabler.Outline.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isConnected) {
                Spacer(Modifier.height(4.dp))
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
                        text = "Connected",
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
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))

                OnboardingToggleRow(
                    title = "Enable Seerr Integration",
                    subtitle = "Master switch for all Seerr features",
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
                            title = "Search Integration",
                            subtitle = "Show Seerr results in search",
                            checked = seerrPreferences.searchEnabled,
                            onCheckedChange = onSetSearchEnabled,
                        )
                        OnboardingToggleRow(
                            title = "Recommendations",
                            subtitle = "Show similar titles and recommendations",
                            checked = seerrPreferences.recommendationsEnabled,
                            onCheckedChange = onSetRecommendationsEnabled,
                        )
                        OnboardingToggleRow(
                            title = "Discover",
                            subtitle = "Trending and popular content from Seerr",
                            checked = seerrPreferences.discoverEnabled,
                            onCheckedChange = onSetDiscoverEnabled,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Regions",
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
                        label = "Streaming",
                        selected = seerrPreferences.streamingRegion,
                        options = SeerrRegions,
                        onSelect = onSetStreamingRegion,
                        modifier = Modifier.weight(1f),
                    )
                    RegionChip(
                        label = "Discover",
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
