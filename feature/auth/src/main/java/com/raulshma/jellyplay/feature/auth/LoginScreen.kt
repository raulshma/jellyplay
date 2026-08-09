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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.auth.R
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/**
 * Which credential field (if any) a login error is attached to. Ambiguous auth
 * failures (e.g. HTTP 401) use [SERVER] so neither field is reddened while the
 * message still surfaces in the error banner.
 */
enum class LoginFieldError { USERNAME, PASSWORD, SERVER }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    serverAddress: String,
    onLoginSuccess: () -> Unit,
    onQuickConnect: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoggingIn by rememberSaveable { mutableStateOf(false) }
    // Typed login error: which field is at fault + the message to show in the
    // banner. Stored as two saveables (enum name + message) so the pair survives
    // config changes without needing a Parcelable/Serializable on the enum.
    var errorField by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val error: Pair<LoginFieldError, String>? = run {
        val msg = errorMessage
        val field = errorField?.let { f -> LoginFieldError.entries.firstOrNull { it.name == f } }
        if (msg != null && field != null) field to msg else null
    }
    fun setError(field: LoginFieldError, message: String) {
        errorField = field.name
        errorMessage = message
    }
    fun clearError() {
        errorField = null
        errorMessage = null
    }
    var contentVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    JellyPlayScreenScaffold(
        title = stringResource(R.string.auth_sign_in_title),
        onBack = onBack,
    ) { padding ->
        val usernameRequiredError = stringResource(R.string.auth_error_username_required)
        val loginFailedError = stringResource(R.string.auth_login_failed)
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        val contentPad = adaptiveInfo.contentPadding(isTv)
        val usernameFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (isTv) usernameFocusRequester.tryRequestFocus("login_username")
        }

        fun submit() {
            if (username.isBlank()) {
                setError(LoginFieldError.USERNAME, usernameRequiredError)
                return
            }
            isLoggingIn = true
            clearError()
            keyboardController?.hide()
            viewModel.login(serverAddress, username, password) { result ->
                isLoggingIn = false
                result.onSuccess {
                    onLoginSuccess()
                }.onFailure {
                    // Ambiguous auth failure (likely credentials/server); surface
                    // the message but don't redden a specific field.
                    setError(LoginFieldError.SERVER, it.message ?: loginFailedError)
                }
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
                        clearError()
                    },
                    label = { Text(stringResource(R.string.auth_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .semantics { contentType = ContentType.Username },
                    isError = error?.first == LoginFieldError.USERNAME,
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
                        clearError()
                    },
                    label = { Text(stringResource(R.string.auth_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error?.first == LoginFieldError.PASSWORD,
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
                            error!!.second,
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
                    Text(if (isLoggingIn) stringResource(R.string.auth_signing_in) else stringResource(R.string.auth_sign_in))
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
                    Text(stringResource(R.string.auth_quick_connect))
                }
            }
        }
    }
}
