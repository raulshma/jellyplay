package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.ui.components.ImeAlertDialog
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_failed
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_field_body
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_field_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_field_url
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_send
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_title
import com.raulshma.jellyplay.feature.admin.generated.resources.jellyplay_bc_url_invalid

/**
 * The loose link-URL validation behind the broadcast composer's Send gate:
 * blank (no link) or a scheme-prefixed, whitespace-free URL. Deliberately
 * loose — the plugin re-checks server-side and a bad link degrades to a
 * plain toast on the clients; this only catches obvious typos.
 */
internal fun isBroadcastUrlValid(url: String): Boolean =
    url.isBlank() || (!url.any { it.isWhitespace() } && url.contains("://"))

/**
 * The "Send broadcast" composer dialog (ADR 0010): title + body + optional
 * link URL, fanned out to every connected client as a toast-style broadcast
 * event over the plugin's events stream. The CreateUserDialog precedent —
 * [ImeAlertDialog] so the soft keyboard lifts the fields, Send disabled until
 * the required pair is non-blank and the optional URL validates loosely.
 *
 * While the send is in flight ([isSending]) Send and Cancel stay disabled and
 * Send shows its loading state — the dialog closes only when the request
 * settles (the dashboard's stop-session confirm shape).
 */
@Composable
fun BroadcastDialog(
    title: String,
    body: String,
    url: String,
    isSending: Boolean,
    sendFailed: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // TV: land initial focus on the primary action; while it is disabled
    // (blank title/body) the first D-pad press finds the title field instead.
    val isTv = LocalTvMode.current
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) confirmFocusRequester.tryRequestFocus("send_broadcast_confirm")
    }

    val urlValid = isBroadcastUrlValid(url)
    ImeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.jellyplay_bc_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(Res.string.jellyplay_bc_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    label = { Text(stringResource(Res.string.jellyplay_bc_field_body)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(Res.string.jellyplay_bc_field_url)) },
                    singleLine = true,
                    isError = !urlValid,
                    supportingText = {
                        if (!urlValid) {
                            Text(stringResource(Res.string.jellyplay_bc_url_invalid))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sendFailed) {
                    Text(
                        text = stringResource(Res.string.jellyplay_bc_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = title.isNotBlank() && body.isNotBlank() && urlValid && !isSending,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            ) { Text(stringResource(Res.string.jellyplay_bc_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text(stringResource(Res.string.admin_cancel))
            }
        },
    )
}
