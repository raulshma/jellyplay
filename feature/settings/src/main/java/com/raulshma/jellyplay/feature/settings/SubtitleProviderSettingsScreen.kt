package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bolt
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton

/**
 * Settings surface for subtitle providers (Wyzie Subs + OpenSubtitles). Wyzie
 * has an enable switch + an API-key field; OpenSubtitles has an enable switch +
 * username/password (it authenticates with the user's opensubtitles.com account
 * — the API key is a compiled-in shared app key, never user-visible, mirroring
 * the Jellyfin plugin). A "Test" action exercises the provider's auth + search
 * path and surfaces a Connected / Error status, mirroring the *arr
 * server-probe UX.
 *
 * Mirrors [ArrSettingsScreen]'s scaffold + grouped-list layout. The screen is
 * purely a configuration surface; the player + editor consume the configured
 * providers via [com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleProviderSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SubtitleProviderSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()

    // Read the current credential snapshot once per save (keyed on providerStatus,
    // which changes after a Save mutates the secure store). Without `remember` this
    // synchronous EncryptedSharedPreferences read runs on every recomposition — and
    // a new snapshot also resets `var apiKey by remember(initialApiKey){...}` in
    // ProviderSection mid-typing, clobbering in-progress input.
    val wyzieCreds = remember(providerStatus) {
        viewModel.currentCredential(SubtitleProviderKind.WYZIE)
            as? SubtitleProviderCredentials.Wyzie
    }
    val osCreds = remember(providerStatus) {
        viewModel.currentCredential(SubtitleProviderKind.OPENSUBTITLES)
            as? SubtitleProviderCredentials.OpenSubtitles
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_integrations_subtitles)) },
                navigationIcon = { CircleBgBackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "wyzie_group") {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(R.string.settings_subtitles_wyzie),
                ) {
                    ProviderSection(
                        enabled = preferences.wyzieEnabled,
                        onEnabledChange = viewModel::setWyzieEnabled,
                        apiKeyLabel = stringResource(R.string.settings_subtitles_api_key),
                        initialApiKey = wyzieCreds?.apiKey.orEmpty(),
                        usernameLabel = null,
                        initialUsername = null,
                        passwordLabel = null,
                        initialPassword = null,
                        status = providerStatus[SubtitleProviderKind.WYZIE],
                        onSave = { apiKey, _, _ -> viewModel.saveWyzieApiKey(apiKey) },
                        onTest = { viewModel.testProvider(SubtitleProviderKind.WYZIE) },
                        helperText = stringResource(R.string.settings_subtitles_wyzie_help),
                    )
                }
            }
            item(key = "opensubtitles_group") {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(R.string.settings_subtitles_opensubtitles),
                ) {
                    ProviderSection(
                        enabled = preferences.openSubtitlesEnabled,
                        onEnabledChange = viewModel::setOpenSubtitlesEnabled,
                        apiKeyLabel = null,
                        initialApiKey = null,
                        usernameLabel = stringResource(R.string.settings_subtitles_username),
                        initialUsername = osCreds?.username.orEmpty(),
                        passwordLabel = stringResource(R.string.settings_subtitles_password),
                        initialPassword = osCreds?.password.orEmpty(),
                        status = providerStatus[SubtitleProviderKind.OPENSUBTITLES],
                        onSave = { _, username, password ->
                            viewModel.saveOpenSubtitlesCredentials(username, password)
                        },
                        onTest = { viewModel.testProvider(SubtitleProviderKind.OPENSUBTITLES) },
                        helperText = stringResource(R.string.settings_subtitles_opensubtitles_help),
                    )
                }
            }
        }
    }
}

/**
 * One provider's configuration card: enable switch, optional API key (Wyzie),
 * optional username/password (OpenSubtitles), Save + Test actions, and the
 * connection status. Reused for both providers.
 */
@Composable
private fun ProviderSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    apiKeyLabel: String?,
    initialApiKey: String?,
    usernameLabel: String?,
    initialUsername: String?,
    passwordLabel: String?,
    initialPassword: String?,
    status: SubtitleProviderSettingsViewModel.ProviderStatus?,
    onSave: (apiKey: String, username: String?, password: String?) -> Unit,
    onTest: () -> Unit,
    helperText: String,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(helperText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(8.dp))

        var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey.orEmpty()) }
        var username by remember(initialUsername) { mutableStateOf(initialUsername.orEmpty()) }
        var password by remember(initialPassword) { mutableStateOf(initialPassword.orEmpty()) }

        if (apiKeyLabel != null) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(apiKeyLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (usernameLabel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(usernameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (passwordLabel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(passwordLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { onSave(apiKey, usernameLabel?.let { username }, passwordLabel?.let { password }) },
            ) {
                Text(stringResource(R.string.settings_subtitles_save))
            }
            OutlinedButton(onClick = onTest) {
                Text(stringResource(R.string.settings_subtitles_test))
            }
            StatusIndicator(status)
        }
    }
}

@Composable
private fun StatusIndicator(status: SubtitleProviderSettingsViewModel.ProviderStatus?) {
    when (status) {
        is SubtitleProviderSettingsViewModel.ProviderStatus.Testing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                modifier = Modifier.size(14.dp),
            )
            Text(stringResource(R.string.settings_subtitles_testing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is SubtitleProviderSettingsViewModel.ProviderStatus.Connected -> Text(
            stringResource(R.string.settings_subtitles_connected),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColors.success,
            fontWeight = FontWeight.SemiBold,
        )
        is SubtitleProviderSettingsViewModel.ProviderStatus.Error -> Text(
            status.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
        )
        null, SubtitleProviderSettingsViewModel.ProviderStatus.Idle -> Unit
    }
}

// Helper extension to read the current credential snapshot synchronously for
// form seeding without exposing the store publicly.
private fun SubtitleProviderSettingsViewModel.currentCredential(
    kind: SubtitleProviderKind,
): SubtitleProviderCredentials? = credentialSnapshot(kind)
