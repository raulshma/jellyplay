package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Lock
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_confirm_field
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_export_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_export_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_export_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_field
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_mismatch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_passphrase_short_warning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_unlock_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_unlock_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_secrets_unlock_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Wave-3 passphrase dialog for the LOCAL FILE backup secrets block — the
 * one dialog in the backup flow that embeds text fields. Follows the
 * [com.raulshma.jellyplay.core.ui.components.ConfirmDialog] visual register
 * (floating [ShapeCache.smooth28] surface, tone-tinted squircle icon badge,
 * pill action bar) but does NOT auto-dismiss on confirm: the export arm
 * closes via its caller once the write settles, and the import arm must stay
 * up for an inline wrong-passphrase retry — so visibility is the caller's
 * state and [onConfirm] hands up the passphrase [CharArray].
 *
 * TV-safe like ConfirmDialog: initial focus lands on the first field (typing
 * is the point of the dialog; a stray D-pad OK cannot trigger anything since
 * the confirm stays disabled until the fields validate), and buttons disable
 * while the decrypt/encrypt action is in flight.
 */
@Composable
internal fun ExportPassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    PassphrasePanel(
        title = stringResource(Res.string.settings_backup_passphrase_export_title),
        message = stringResource(Res.string.settings_backup_passphrase_export_message),
        fieldLabel = stringResource(Res.string.settings_backup_passphrase_field),
        confirmText = stringResource(Res.string.settings_backup_passphrase_export_confirm),
        // Non-blocking: a short passphrase exports (the user's call) but is
        // warned about; a mismatch blocks (a typo'd passphrase loses the
        // secrets forever). The panel derives both lines itself.
        isBusy = false,
        icon = Tabler.Outline.Lock,
        showConfirmField = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        modifier = modifier,
    )
}

/**
 * The import-side unlock dialog: one passphrase field, an inline
 * wrong-passphrase error line ([errorText] — retry without leaving the
 * dialog), and a busy state while the 600k-iteration KDF + GCM decrypt run.
 */
@Composable
internal fun UnlockPassphraseDialog(
    isBusy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    PassphrasePanel(
        title = stringResource(Res.string.settings_import_secrets_unlock_title),
        message = stringResource(Res.string.settings_import_secrets_unlock_message),
        fieldLabel = stringResource(Res.string.settings_backup_passphrase_field),
        confirmText = stringResource(Res.string.settings_import_secrets_unlock_confirm),
        hint = errorText,
        hintIsError = true,
        isBusy = isBusy,
        icon = Tabler.Outline.Lock,
        showConfirmField = false,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        modifier = modifier,
    )
}

/** A passphrase under this length exports but draws the non-blocking warning line. */
internal const val MIN_PASSPHRASE_LENGTH = 8

@Composable
private fun PassphrasePanel(
    title: String,
    message: String,
    fieldLabel: String,
    confirmText: String,
    hint: String? = null,
    hintIsError: Boolean = false,
    isBusy: Boolean,
    icon: ImageVector,
    showConfirmField: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val mismatch = showConfirmField && confirm.isNotEmpty() && passphrase != confirm
    val shortWarning = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val confirmEnabled = when {
        isBusy -> false
        passphrase.isEmpty() -> false
        // Export mode additionally requires the confirm field to match.
        showConfirmField -> !mismatch
        else -> true
    }
    val hintText = when {
        hint != null -> hint
        mismatch -> stringResource(Res.string.settings_backup_passphrase_mismatch)
        shortWarning -> stringResource(Res.string.settings_backup_passphrase_short_warning)
        else -> null
    }
    val resolvedHintIsError = if (hint != null) hintIsError else mismatch

    val passphraseFieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Typing is the point of this dialog — lead with the field on TV too
        // (a stray OK on an empty field cannot trigger the disabled confirm).
        passphraseFieldFocus.tryRequestFocus("passphrase_field")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier.widthIn(max = 420.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = ShapeCache.smooth28,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
                shadowElevation = 8.dp,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = ShapeCache.smooth16,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(16.dp))
                    PasswordTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(fieldLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passphraseFieldFocus),
                        enabled = !isBusy,
                        isError = resolvedHintIsError,
                    )

                    if (showConfirmField) {
                        Spacer(Modifier.height(8.dp))
                        PasswordTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text(stringResource(Res.string.settings_backup_passphrase_confirm_field)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            isError = mismatch,
                        )
                    }

                    if (hintText != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (resolvedHintIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isBusy,
                            shape = ShapeCache.smoothPill,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(
                                stringResource(Res.string.settings_backup_passphrase_cancel),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Button(
                            onClick = { onConfirm(passphrase.toCharArray()) },
                            enabled = confirmEnabled,
                            shape = ShapeCache.smoothPill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(confirmText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
