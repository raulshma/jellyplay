package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.PlayerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (userName.isNotBlank()) {
                Text(
                    text = "Signed in as $userName",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                HorizontalDivider()
            }

            SettingsSectionHeader("Playback")

            SettingItem(
                icon = Icons.Default.PlayCircle,
                title = "Preferred Player",
                subtitle = when (preferences.preferredPlayer) {
                    PlayerType.INTERNAL -> "Internal (ExoPlayer)"
                    PlayerType.EXTERNAL -> "External Player"
                },
                onClick = {
                    val next = when (preferences.preferredPlayer) {
                        PlayerType.INTERNAL -> PlayerType.EXTERNAL
                        PlayerType.EXTERNAL -> PlayerType.INTERNAL
                    }
                    viewModel.setPreferredPlayer(next)
                },
            ) {
                Text(
                    when (preferences.preferredPlayer) {
                        PlayerType.INTERNAL -> "Internal"
                        PlayerType.EXTERNAL -> "External"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("Language")

            SettingItem(
                icon = Icons.Default.Language,
                title = "Preferred Audio Language",
                subtitle = preferences.preferredAudioLanguage ?: "Default",
                onClick = { viewModel.setPreferredAudioLanguage(null) },
            )

            HorizontalDivider()

            SettingItem(
                icon = Icons.Default.ClosedCaption,
                title = "Preferred Subtitle Language",
                subtitle = preferences.preferredSubtitleLanguage ?: "Default",
                onClick = { viewModel.setPreferredSubtitleLanguage(null) },
            )

            HorizontalDivider()

            SettingsSectionHeader("Appearance")

            SettingItem(
                title = "Dynamic Theming",
                subtitle = "Color theme from media artwork",
            ) {
                Switch(
                    checked = preferences.dynamicTheming,
                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("About")

            SettingItem(
                title = "Version",
                subtitle = "JellyPlay v1.0.0",
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Sign Out")
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
