package com.raulshma.jellyplay.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.auth.generated.resources.Res
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_error_username_required
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_password
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_quick_connect
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_sign_in
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_sign_in_title
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_signing_in
import com.raulshma.jellyplay.feature.auth.generated.resources.auth_username
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.core.ui.message.asText
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    serverAddress: String,
    onLoginSuccess: () -> Unit,
    onQuickConnect: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // The login machine is VM-owned (AuthViewModel.loginPhase / loginError) —
    // NOT a screen-local saveable. The former `isLoggingIn` rememberSaveable
    // flag's only writer lived inside the login result callback, so a process
    // death mid-login restored a stuck `true` that disabled the submit button
    // forever (the recorded `pinVerifying` bug shape, worse because saveable
    // persisted it). A fresh ViewModel constructs at Idle with no error; a
    // config change keeps the surviving VM's phase.
    val loginPhase by viewModel.loginPhase.collectAsStateWithLifecycle()
    val isLoggingIn = loginPhase is LoginPhase.Submitting
    val error by viewModel.loginError.collectAsStateWithLifecycle()
    var contentVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.auth_sign_in_title),
        onBack = onBack,
    ) { padding ->
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val usernameFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (isTv) usernameFocusRequester.tryRequestFocus("login_username")
        }

        fun submit() {
            if (username.isBlank()) {
                viewModel.setLoginFieldError(
                    LoginFieldError.USERNAME,
                    UiMessage.Resource(Res.string.auth_error_username_required),
                )
                return
            }
            keyboardController?.hide()
            viewModel.login(serverAddress, username, password) { result ->
                result.onSuccess {
                    onLoginSuccess()
                }
                // Failure: the VM parked the typed error (SERVER + the
                // exception-vs-fallback message) and settled the phase to
                // Idle — nothing left for the screen to do here.
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = contentPad, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Tabler.Outline.User,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        viewModel.clearLoginError()
                    },
                    label = { Text(stringResource(Res.string.auth_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .semantics { contentType = ContentType.Username },
                    isError = error?.field == LoginFieldError.USERNAME,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                PasswordTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        viewModel.clearLoginError()
                    },
                    label = { Text(stringResource(Res.string.auth_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error?.field == LoginFieldError.PASSWORD,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit(); keyboardController?.hide() }),
                    contentType = ContentType.Password,
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            error!!.message.asText(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                val signInFocusState = rememberTvFocusState(focusedScale = 1.04f)
                Button(
                    onClick = { submit() },
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth()
                        .then(signInFocusState.focusModifier)
                        .tvFocusIndicator(signInFocusState, ShapeCache.smooth12),
                ) {
                    if (isLoggingIn) {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoggingIn) stringResource(Res.string.auth_signing_in) else stringResource(Res.string.auth_sign_in))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                val quickConnectFocusState = rememberTvFocusState(focusedScale = 1.04f)
                OutlinedButton(
                    onClick = onQuickConnect,
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth()
                        .then(quickConnectFocusState.focusModifier)
                        .tvFocusIndicator(quickConnectFocusState, ShapeCache.smooth12),
                ) {
                    Icon(Tabler.Outline.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.auth_quick_connect))
                }
            }
        }
    }
}
