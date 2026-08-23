package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bolt
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_api_key
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_connected
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_opensubtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_opensubtitles_help
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_password
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_save
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_test
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_testing
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_username
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_wyzie
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_wyzie_help

/**
 * Settings surface for subtitle providers (Wyzie Subs + OpenSubtitles). Wyzie
 * has an enable switch + an API-key field; OpenSubtitles has an enable switch +
 * username/password (it authenticates with the user's opensubtitles.com account
 * — the API key is a compiled-in shared app key, never user-visible, mirroring
 * the Jellyfin plugin). A "Test" action verifies the **in-progress form text**
 * against the provider (a real `/login` for OpenSubtitles, a probe search for
 * Wyzie) and surfaces a Connected / Error status — so the user can confirm a
 * key/password works **before** tapping Save, mirroring the *arr server-probe
 * UX.
 *
 * Mirrors [ArrSettingsScreen]'s scaffold + grouped-list layout. The screen is
 * purely a configuration surface; the player + editor consume the configured
 * providers via [com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository].
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubtitleProviderSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SubtitleProviderSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()

    // Seed the form fields from the secure store ONCE. Deliberately NOT keyed on
    // providerStatus: Test mutates providerStatus (Testing → Connected/Error), and
    // re-reading the snapshot then would reset `var apiKey by remember(initialApiKey)`
    // back to the SAVED value — clobbering the in-progress text the user just
    // verified. Since Test now validates exactly that typed text (before Save),
    // preserving it across a test is required. Re-entry (navigate away/back)
    // recomposes fresh and re-reads, so the seed stays current.
    val wyzieCreds = remember {
        viewModel.currentCredential(SubtitleProviderKind.WYZIE)
            as? SubtitleProviderCredentials.Wyzie
    }
    val osCreds = remember {
        viewModel.currentCredential(SubtitleProviderKind.OPENSUBTITLES)
            as? SubtitleProviderCredentials.OpenSubtitles
    }

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "subtitle_provider_init",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_integrations_subtitles)) },
                navigationIcon = { CircleBgBackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        // Center the focused item in the viewport when scrolling reaches the list
        // edges, instead of parking it at the bottom, which is the default
        // BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .imePadding()
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "wyzie_group") {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(Res.string.settings_subtitles_wyzie),
                ) {
                    ProviderSection(
                        enabled = preferences.wyzieEnabled,
                        onEnabledChange = viewModel::setWyzieEnabled,
                        apiKeyLabel = stringResource(Res.string.settings_subtitles_api_key),
                        initialApiKey = wyzieCreds?.apiKey.orEmpty(),
                        usernameLabel = null,
                        initialUsername = null,
                        passwordLabel = null,
                        initialPassword = null,
                        status = providerStatus[SubtitleProviderKind.WYZIE],
                        onSave = { apiKey, _, _ -> viewModel.saveWyzieApiKey(apiKey) },
                        onTest = { apiKey, _, _ -> viewModel.testWyzieApiKey(apiKey) },
                        helperText = stringResource(Res.string.settings_subtitles_wyzie_help),
                    )
                }
            }
            item(key = "opensubtitles_group") {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(Res.string.settings_subtitles_opensubtitles),
                ) {
                    ProviderSection(
                        enabled = preferences.openSubtitlesEnabled,
                        onEnabledChange = viewModel::setOpenSubtitlesEnabled,
                        apiKeyLabel = null,
                        initialApiKey = null,
                        usernameLabel = stringResource(Res.string.settings_subtitles_username),
                        initialUsername = osCreds?.username.orEmpty(),
                        passwordLabel = stringResource(Res.string.settings_subtitles_password),
                        initialPassword = osCreds?.password.orEmpty(),
                        status = providerStatus[SubtitleProviderKind.OPENSUBTITLES],
                        onSave = { _, username, password ->
                            viewModel.saveOpenSubtitlesCredentials(username, password)
                        },
                        onTest = { _, username, password ->
                            viewModel.testOpenSubtitlesCredentials(username, password)
                        },
                        helperText = stringResource(Res.string.settings_subtitles_opensubtitles_help),
                    )
                }
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
    onTest: (apiKey: String, username: String?, password: String?) -> Unit,
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
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.focusIndicator(),
            )
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
                modifier = Modifier.focusIndicator(),
            ) {
                Text(stringResource(Res.string.settings_subtitles_save))
            }
            // Test uses the live form text (not the saved store) so the user can
            // verify a freshly pasted key/password before tapping Save.
            OutlinedButton(
                onClick = { onTest(apiKey, usernameLabel?.let { username }, passwordLabel?.let { password }) },
                modifier = Modifier.focusIndicator(),
            ) {
                Text(stringResource(Res.string.settings_subtitles_test))
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
            Text(stringResource(Res.string.settings_subtitles_testing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is SubtitleProviderSettingsViewModel.ProviderStatus.Connected -> Text(
            stringResource(Res.string.settings_subtitles_connected),
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
