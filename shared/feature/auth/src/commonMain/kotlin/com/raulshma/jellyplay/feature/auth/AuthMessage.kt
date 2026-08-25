package com.raulshma.jellyplay.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.auth.generated.resources.Res

/**
 * Screen-forward message seal for auth flows — the commonMain-safe
 * replacement for the legacy `appContext.getString(R.string.…)` values the
 * ViewModels used to build error strings at failure time (music
 * MixErrorMessage / syncplay SyncPlayMessage conveyor pattern). The resource
 * stays unresolved until render so the locale resolves at display; exception
 * messages are carried raw (already final, no localized form).
 *
 * No variant carries format arguments: every auth error resource is
 * args-free (the two format-bearing strings, `auth_remove_server_message` /
 * `auth_remove_user_message`, are pre-resolved in composition at their
 * render sites — newsletter args-free seal shape).
 */
@Immutable
sealed interface AuthMessage {

    /** Localized message resolved from a compose-resources string. */
    @Immutable
    data class Resource(val res: StringResource) : AuthMessage

    /** Raw failure text (exception message — already final). */
    @Immutable
    data class Raw(val text: String) : AuthMessage
}

/** Collapse to display text inside composition (locale resolves here). */
@Composable
fun AuthMessage.asText(): String = when (this) {
    is AuthMessage.Resource -> stringResource(res)
    is AuthMessage.Raw -> text
}
