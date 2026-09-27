package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.network.config.ClientCertificateStatus
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.formatDate
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_ca
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_ca_set
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_change
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_choose
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_import
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_import_hint
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_import_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_issuer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_passphrase
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_pick_ca
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_pick_certificate
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_pick_key
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_remove
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_subject
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_unreadable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_use
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_validity
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_client_certificate_validity_range

/**
 * App-level "Client certificate (mTLS)" security group. When a
 * certificate is imported the parsed subject/issuer/validity window is shown
 * (the import flow's proof-of-parse) alongside the enable toggle and the
 * remove action; otherwise a single import affordance carries the section.
 * Orthogonal to the per-server self-signed grants: a CA override replaces
 * the platform trust anchors, the certificate is PRESENTED to every TLS
 * server that asks.
 */
@Composable
internal fun ClientCertificateSection(
    status: ClientCertificateStatus,
    isOperationInProgress: Boolean,
    onImport: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Tabler.Outline.Certificate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.settings_client_certificate),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (status.isImported) {
                Spacer(Modifier.height(8.dp))
                ClientCertificateDetails(status)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_client_certificate_use),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = status.enabled,
                        onCheckedChange = onToggle,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onRemove,
                        enabled = !isOperationInProgress,
                    ) { Text(stringResource(Res.string.settings_client_certificate_remove)) }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_client_certificate_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onImport) {
                        Text(stringResource(Res.string.settings_client_certificate_import))
                    }
                }
            }
        }
    }
}

/** Subject / issuer / validity summary of the imported certificate (proof of parse). */
@Composable
private fun ClientCertificateDetails(status: ClientCertificateStatus) {
    Column {
        ClientCertificateDetailLine(
            label = stringResource(Res.string.settings_client_certificate_subject),
            value = status.subject ?: stringResource(Res.string.settings_client_certificate_unreadable),
        )
        status.issuer?.let {
            ClientCertificateDetailLine(
                label = stringResource(Res.string.settings_client_certificate_issuer),
                value = it,
            )
        }
        val notValidBefore = status.notValidBeforeMs
        val notValidAfter = status.notValidAfterMs
        if (notValidBefore != null && notValidAfter != null) {
            ClientCertificateDetailLine(
                label = stringResource(Res.string.settings_client_certificate_validity),
                value = stringResource(
                    Res.string.settings_client_certificate_validity_range,
                    formatDate(notValidBefore),
                    formatDate(notValidAfter),
                ),
            )
        }
        if (status.customCaConfigured) {
            ClientCertificateDetailLine(
                label = stringResource(Res.string.settings_client_certificate_ca),
                value = stringResource(Res.string.settings_client_certificate_ca_set),
            )
        }
    }
}

@Composable
private fun ClientCertificateDetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The import sheet: certificate pick (.p12/.pfx bundle OR a PEM .crt — a
 * PEM pick reveals the private-key row), optional passphrase for protected
 * PKCS#12 bundles, and the optional server CA override. Import assembles the
 * [com.raulshma.jellyplay.core.network.config.ClientCertificateImport]; the
 * facade's parse failures land on the message bus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CertificateImportSheet(
    onDismiss: () -> Unit,
    onImport: (com.raulshma.jellyplay.core.network.config.ClientCertificateImport) -> Unit,
) {
    val picker = rememberCertificateFilePicker()
    var certificatePick by remember { mutableStateOf<CertificatePick?>(null) }
    var keyPick by remember { mutableStateOf<CertificatePick?>(null) }
    var caPick by remember { mutableStateOf<CertificatePick?>(null) }
    var passphrase by remember { mutableStateOf("") }

    // A PEM pick (vs a PKCS#12 bundle) is decided by content, not extension:
    // PEM files carry the -----BEGIN CERTIFICATE header up front.
    val certificateIsPem = certificatePick?.bytes?.let { bytes ->
        bytes.copyOf(minOf(bytes.size, 128))
            .toString(Charsets.US_ASCII)
            .contains("-----BEGIN CERTIFICATE")
    } ?: false

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.settings_client_certificate_import_title),
                icon = Tabler.Outline.Certificate,
            )
            Text(
                text = stringResource(Res.string.settings_client_certificate_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            CertificatePickRow(
                label = stringResource(Res.string.settings_client_certificate_pick_certificate),
                pickedName = certificatePick?.displayName,
                onLaunch = {
                    picker?.launch(CertificatePickRole.CERTIFICATE_OR_BUNDLE) { pick ->
                        certificatePick = pick
                    }
                },
            )
            if (certificateIsPem) {
                CertificatePickRow(
                    label = stringResource(Res.string.settings_client_certificate_pick_key),
                    pickedName = keyPick?.displayName,
                    onLaunch = {
                        picker?.launch(CertificatePickRole.PRIVATE_KEY) { pick ->
                            keyPick = pick
                        }
                    },
                )
            }
            CertificatePickRow(
                label = stringResource(Res.string.settings_client_certificate_pick_ca),
                pickedName = caPick?.displayName,
                onLaunch = {
                    picker?.launch(CertificatePickRole.SERVER_CA) { pick ->
                        caPick = pick
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(Res.string.settings_client_certificate_passphrase)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                val canImport = certificatePick != null && (!certificateIsPem || keyPick != null)
                androidx.compose.material3.Button(
                    onClick = {
                        val cert = checkNotNull(certificatePick)
                        onImport(
                            com.raulshma.jellyplay.core.network.config.ClientCertificateImport(
                                pkcs12Bytes = if (certificateIsPem) null else cert.bytes,
                                certificatePemBytes = if (certificateIsPem) cert.bytes else null,
                                privateKeyPemBytes = keyPick?.bytes,
                                serverCaPemBytes = caPick?.bytes,
                                passphrase = passphrase.takeIf { it.isNotEmpty() }?.toCharArray(),
                            ),
                        )
                    },
                    enabled = canImport,
                    shape = ShapeCache.smoothPill,
                ) { Text(stringResource(Res.string.settings_client_certificate_import)) }
            }
        }
    }
}

@Composable
private fun CertificatePickRow(
    label: String,
    pickedName: String?,
    onLaunch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
            if (pickedName != null) {
                Text(
                    text = pickedName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onLaunch) {
            Text(
                stringResource(
                    if (pickedName == null) Res.string.settings_client_certificate_choose
                    else Res.string.settings_client_certificate_change,
                ),
            )
        }
    }
}
