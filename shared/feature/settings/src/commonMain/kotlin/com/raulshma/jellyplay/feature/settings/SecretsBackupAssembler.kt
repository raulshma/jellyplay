package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ServerBackupMetadata
import com.raulshma.jellyplay.core.data.repository.ServerBackupMetadataStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.BackupSecrets
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SeerrSecrets
import com.raulshma.jellyplay.core.datastore.ServerEntrySecret
import com.raulshma.jellyplay.core.datastore.SubtitleCredentialEntry
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.toArrServerConfig
import com.raulshma.jellyplay.core.datastore.toSecret
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind

/**
 * What a decrypted [BackupSecrets] will restore — the counts the import
 * preview shows after a successful passphrase unlock (the "WHAT will be
 * restored" gate) and the payload-free shape the UI renders.
 */
data class SecretsRestoreSummary(
    val servers: Int,
    val arrServers: Int,
    val subtitleProviders: Int,
    val hasSeerr: Boolean,
) {
    companion object {
        /** The preview counts for a decrypted payload — the one place they are computed. */
        fun of(secrets: BackupSecrets) = SecretsRestoreSummary(
            servers = secrets.servers.size,
            arrServers = secrets.arrServers.size,
            subtitleProviders = secrets.subtitleCredentials.size,
            hasSeerr = secrets.seerr != null,
        )
    }
}

/**
 * Outcome of applying a decrypted secrets block — the per-domain counts the
 * "Secrets restored" confirmation reports.
 */
data class SecretsApplyResult(
    val arrServersApplied: Int,
    val subtitleProvidersApplied: Int,
    val seerrApplied: Boolean,
    val serversRestored: Int,
)

/**
 * Gathers the device's secrets into a [BackupSecrets] payload (export) and
 * applies a decrypted one back (import) — the one place that knows how the
 * three secure credential stores plus the saved-server list map to and from
 * the backup wire shape.
 *
 * Lives in feature/settings (not core) by dependency direction: it needs the
 * three core:datastore secure stores AND core:data's
 * [ServerBackupMetadataStore] (the AuthRepositorySurfaceTest ratchet's narrow
 * server-list collaborator), and feature:settings is the only layer that
 * already sees both — a core home would have dragged feature wiring down with
 * it. Stateless besides the injected stores; constructed once by the settings
 * Koin module and shared by the export ([SettingsViewModel]) and restore-wizard
 * ([RestoreWizardViewModel]) ViewModels.
 *
 * Semantics:
 *  - [gather] reads ONLY secret material: manual *arr configs, per-provider
 *    subtitle credentials (the Jellyfin provider uses the active session and
 *    never appears), Seerr direct-mode credentials, and the token-free server
 *    list. No Jellyfin access token, PIN hash, biometric flag, or auto-lock
 *    setting ever enters the payload.
 *  - [apply] is MERGE-by-id, deliberately gentler than the Wave-2 external
 *    slices' wholesale-adopt: incoming entries overwrite same-id entries and
 *    are added alongside local ones — restoring a friend's backup never
 *    erases the locally-configured credentials the backup knows nothing
 *    about. Absent domains are untouched.
 *  - Callers must gate [apply] behind an explicit user confirmation (the
 *    import preview's secrets card); it is never invoked automatically.
 */
class SecretsBackupAssembler(
    private val arrStore: ArrSecureCredentialsStore,
    private val seerrStore: SeerrSecureCredentialsStore,
    private val subtitleStore: SubtitleProviderSecureCredentialsStore,
    private val serverMetadataStore: ServerBackupMetadataStore,
) {

    /** The subtitle kinds that can carry credentials (Jellyfin rides the session). */
    private val credentialKinds: List<SubtitleProviderKind> =
        SubtitleProviderKind.entries.filter { it != SubtitleProviderKind.JELLYFIN }

    /**
     * Reads the device's secret material into the backup payload. Blank
     * credentials collapse to null so an export carries exactly what is
     * configured; an all-blank Seerr omits the [SeerrSecrets] block entirely.
     */
    suspend fun gather(): BackupSecrets = BackupSecrets(
        arrServers = arrStore.getManualServers().map { it.toSecret() },
        subtitleCredentials = credentialKinds.mapNotNull { kind ->
            subtitleStore.getCredentials(kind)?.let { SubtitleCredentialEntry(kind, it) }
        },
        seerr = seerrSecretsOrNull(),
        servers = serverMetadataStore.gatherServers().map { server ->
            ServerEntrySecret(
                id = server.id,
                name = server.name,
                address = server.address,
                alternateAddresses = server.alternateAddresses,
                userNames = server.userNames,
            )
        },
    )

    /**
     * Applies a decrypted [secrets] payload to the device (see the class KDoc
     * for the merge semantics). Errors from the stores propagate — the import
     * VM surfaces them as a failed import event, never a crash.
     */
    suspend fun apply(secrets: BackupSecrets): SecretsApplyResult {
        // *arr: read-modify-write (the store's own documented usage) —
        // incoming manual servers replace their same-id entries and keep
        // locally-configured others.
        val incomingArr = secrets.arrServers.map { it.toArrServerConfig() }
        if (incomingArr.isNotEmpty()) {
            val merged = arrStore.getManualServers()
                .filter { existing -> incomingArr.none { it.id == existing.id } } + incomingArr
            arrStore.setManualServers(merged)
        }

        // Subtitle providers: per-kind set — kinds the backup doesn't carry
        // keep their local credentials.
        secrets.subtitleCredentials.forEach { entry ->
            subtitleStore.setCredentials(entry.kind, entry.credentials)
        }

        // Seerr: partial restore — only the fields the backup actually
        // carries are written, never a blank overwrite of a configured one.
        val seerr = secrets.seerr
        seerr?.apiKey?.takeIf { it.isNotBlank() }?.let { seerrStore.setApiKey(it) }
        seerr?.password?.takeIf { it.isNotBlank() }?.let { seerrStore.setPassword(it) }
        seerr?.sessionCookie?.takeIf { it.isNotBlank() }?.let { seerrStore.setSessionCookie(it) }

        // Servers: token-free upsert through the narrow seam; the returned
        // count is INSERTS (existing rows reconciled in place).
        val serversRestored = if (secrets.servers.isEmpty()) {
            0
        } else {
            serverMetadataStore.restoreServerMetadata(
                secrets.servers.map { server ->
                    ServerBackupMetadata(
                        id = server.id,
                        name = server.name,
                        address = server.address,
                        alternateAddresses = server.alternateAddresses,
                        userNames = server.userNames,
                    )
                },
            )
        }

        return SecretsApplyResult(
            arrServersApplied = incomingArr.size,
            subtitleProvidersApplied = secrets.subtitleCredentials.size,
            seerrApplied = seerr != null,
            serversRestored = serversRestored,
        )
    }

    /** The preview counts for an already-decrypted payload (no store reads). */
    fun summarize(secrets: BackupSecrets): SecretsRestoreSummary = SecretsRestoreSummary.of(secrets)

    private fun seerrSecretsOrNull(): SeerrSecrets? {
        val apiKey = seerrStore.getApiKey().takeIf { it.isNotBlank() }
        val password = seerrStore.getPassword().takeIf { it.isNotBlank() }
        val sessionCookie = seerrStore.getSessionCookie().takeIf { it.isNotBlank() }
        if (apiKey == null && password == null && sessionCookie == null) return null
        return SeerrSecrets(apiKey = apiKey, password = password, sessionCookie = sessionCookie)
    }
}
