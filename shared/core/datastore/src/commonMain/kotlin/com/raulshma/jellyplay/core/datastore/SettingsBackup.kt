package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Versioned envelope for the exported settings backup.
 *
 * **v2 shape** (current): the aggregate `UserPreferences` payload is split into
 * one serializable blob per preference domain ([slices], keyed by
 * [BackupSliceKey]) plus an [extras] block for app-runtime state that no
 * preference domain owns (favorite channels, last live-TV channel, watch-later
 * playlist, onboarding flag, recent DLNA devices). Each slice is the canonical
 * `@Serializable XSlice` owned by its domain store, so export/import no longer
 * round-trips through the decommissioned `UserPreferences` aggregate and a
 * single domain can evolve its slice without touching the others.
 *
 * **Wave-3 secrets block (additive, optional):** [secrets] carries the
 * passphrase-encrypted secrets block (see [SecretsEnvelope] / [BackupSecrets])
 * for LOCAL FILE backups only — server-held backups (plugin sync) never carry
 * secrets. [originUserId]/[originServerId] stamp the exporting session's
 * identity (from `ServerIdentityStore`) so a cross-account restore can
 * warn. All three fields are additive: an older app version
 * decoding a newer backup ignores them (`PreferencesJson` ignores unknown
 * keys), so [CURRENT_SCHEMA_VERSION] stays 2. The three are annotated
 * `@EncodeDefault(EncodeDefault.Mode.NEVER)` so a PLAIN export (all three at
 * their null default) omits the keys entirely — byte-shape parity with every
 * pre-Wave-3 export — while a secrets export still writes all three.
 *
 * Legacy v0/v1 (un-enveloped / single-aggregate) backups stopped importing in
 * v0.11 — [BackupParser.parse] rejects them with a clear message.
 *
 * [CURRENT_SCHEMA_VERSION] is bumped only on a breaking change to the backup
 * shape; minor additive slice changes are covered by [PreferencesJson] ignoring
 * unknown keys.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SettingsBackup(
    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exportedAt")
    val exportedAt: Long = wallNowMillis(),
    @SerialName("slices")
    val slices: Map<String, JsonElement> = emptyMap(),
    @SerialName("extras")
    val extras: AppRuntimeState = AppRuntimeState(),

    // The three Wave-3 fields skip default-encoding (Mode.NEVER): a plain
    // export must not emit `"secrets": null`-shaped keys — see the class KDoc.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("secrets")
    val secrets: SecretsEnvelope? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("originUserId")
    val originUserId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("originServerId")
    val originServerId: String? = null,
) {
    companion object {
        /** Schema version stamped on every new export. */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/**
 * The passphrase-encrypted secrets block inside a v2 [SettingsBackup] — a
 * `SecretsEnvelope`-shaped object stored under the `"secrets"` key of the
 * backup document (only on exports where the user opted in and supplied a
 * passphrase; see [BackupSecretsCodec] for the exact wire format and crypto).
 *
 * The plaintext payload behind [ciphertextB64] is a [BackupSecrets] document.
 * This type carries ONLY cryptographic parameters — never plaintext secret
 * material.
 */
@Serializable
data class SecretsEnvelope(
    /** Envelope format version; [BackupSecretsCodec.ENVELOPE_VERSION] at the time of writing. */
    @SerialName("v")
    val v: Int = 1,
    /** Base64 of the random 16-byte PBKDF2 salt. */
    @SerialName("kdfSaltB64")
    val kdfSaltB64: String,
    /** PBKDF2 iteration count (the decrypt side must honor the stored value). */
    @SerialName("kdfIterations")
    val kdfIterations: Int,
    /** Base64 of the random 12-byte AES-GCM nonce. */
    @SerialName("nonceB64")
    val nonceB64: String,
    /** Base64 of the AES-256-GCM ciphertext (the 128-bit auth tag is appended). */
    @SerialName("ciphertextB64")
    val ciphertextB64: String,
)

/**
 * The plaintext secret payload a [SecretsEnvelope] protects. Contents (the
 * settled Wave-3 decision):
 *
 *  - [arrServers] — manually-entered Radarr/Sonarr configs incl. their API keys.
 *  - [subtitleCredentials] — per-provider credentials (Wyzie API key,
 *    OpenSubtitles username/password + cached JWT). The Jellyfin provider uses
 *    the active session and never appears.
 *  - [seerr] — Overseerr direct-mode credentials (API key / password / session
 *    cookie), each optional.
 *  - [servers] — the saved Jellyfin server list (id/name/address/alternates +
 *    per-server user NAMES for pre-fill). NEVER carries access tokens.
 *
 * Never in any block, by decision: Jellyfin access tokens, PIN hash, biometric
 * flags, auto-lock settings.
 */
@Serializable
data class BackupSecrets(
    @SerialName("arrServers")
    val arrServers: List<ArrServerSecret> = emptyList(),
    @SerialName("subtitleCredentials")
    val subtitleCredentials: List<SubtitleCredentialEntry> = emptyList(),
    @SerialName("seerr")
    val seerr: SeerrSecrets? = null,
    @SerialName("servers")
    val servers: List<ServerEntrySecret> = emptyList(),
)

/**
 * One manually-entered Radarr/Sonarr server — the secret payload proper (the
 * API key is the point). Mirrors [ArrServerConfig] field-for-field; kept as its
 * own wire type so the backup schema evolves independently of the runtime
 * model. Map back with [toArrServerConfig].
 */
@Serializable
data class ArrServerSecret(
    @SerialName("id")
    val id: String,
    @SerialName("baseUrl")
    val baseUrl: String,
    @SerialName("apiKey")
    val apiKey: String,
    @SerialName("name")
    val name: String,
    @SerialName("kind")
    val kind: ArrServiceKind,
    @SerialName("isManual")
    val isManual: Boolean = true,
)

fun ArrServerSecret.toArrServerConfig(): ArrServerConfig = ArrServerConfig(
    id = id,
    baseUrl = baseUrl,
    apiKey = apiKey,
    name = name,
    kind = kind,
    isManual = isManual,
)

fun ArrServerConfig.toSecret(): ArrServerSecret = ArrServerSecret(
    id = id,
    baseUrl = baseUrl,
    apiKey = apiKey,
    name = name,
    kind = kind,
    isManual = isManual,
)

/**
 * One subtitle provider's credentials, tagged with its [kind] so restore fans
 * back through the secure store's per-kind setter. [credentials] is the sealed
 * runtime credential type reused verbatim (its polymorphic `@SerialName`
 * discriminator travels inside the ciphertext like any other field).
 */
@Serializable
data class SubtitleCredentialEntry(
    @SerialName("kind")
    val kind: SubtitleProviderKind,
    @SerialName("credentials")
    val credentials: SubtitleProviderCredentials,
)

/**
 * Overseerr direct-mode credentials. Fields are independently optional so a
 * partially-configured Seerr restores partially (apply writes only the
 * non-null fields; an all-null [seerr] is simply omitted at export).
 */
@Serializable
data class SeerrSecrets(
    @SerialName("apiKey")
    val apiKey: String? = null,
    @SerialName("password")
    val password: String? = null,
    @SerialName("sessionCookie")
    val sessionCookie: String? = null,
)

/**
 * One saved Jellyfin server — connection metadata only, for the restore
 * wizard's cross-account warning and to spare the user re-adding servers
 * after a restore. Carries user NAMES per server (so a restore can pre-fill the sign-in
 * user) but NEVER `ServerEntity.accessToken`/`UserEntity.accessToken`: a
 * backup that leaks a token would leak the whole account, so tokens never
 * leave the device through ANY block.
 */
@Serializable
data class ServerEntrySecret(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("address")
    val address: String,
    @SerialName("alternateAddresses")
    val alternateAddresses: List<String> = emptyList(),
    @SerialName("userNames")
    val userNames: List<String> = emptyList(),
)

/**
 * Stable string keys for the [SettingsBackup.slices] map. Each key names one
 * preference domain and is a wire contract: renaming one breaks v2 backup
 * compatibility. The value is the domain store's canonical `@Serializable`
 * `XSlice`, encoded by export and decoded (and fanned to `store.restore`) by
 * import.
 */
object BackupSliceKey {
    const val PLAYBACK = "playback"
    const val APPEARANCE = "appearance"
    const val VIDEO_PLAYER = "videoPlayer"
    const val DOWNLOADS = "downloads"
    const val PLAYER_ENGINE = "playerEngine"
    const val HOME_DISCOVERY = "homeDiscovery"
    const val AUDIO = "audio"
    const val AUDIO_EFFECTS = "audioEffects"
    const val AUDIO_CACHE = "audioCache"
    const val LIBRARY = "library"
    const val NAVIGATION = "navigation"
    const val NETWORK_OFFLINE = "networkOffline"
    const val NOTIFICATION = "notification"
    const val SCREENSAVER = "screensaver"
    const val SECURITY = "security"
    const val SUBTITLE = "subtitle"
    const val SYNC_PLAY_CAST = "syncPlayCast"
    const val EXPERIMENTAL = "experimental"

    /** Per-content-type volume memory (levels map + master toggle). */
    const val VOLUME_PROFILE = "volumeProfile"

    // ------------------------------------------------------------------
    // Wave 2 — slices contributed OUTSIDE the domain-store fan-out (see
    // [com.raulshma.jellyplay.core.datastore.settings.ExternalBackupSlice]):
    // Room-backed config and the non-secret integration settings. Additive
    // keys — an older app version ignores them (PreferencesJson ignores
    // unknown keys), so CURRENT_SCHEMA_VERSION stays 2.
    // ------------------------------------------------------------------

    /** Non-secret Seerr / *arr / subtitle-provider settings (the sync allowlists' keys, values as JSON primitives). */
    const val INTEGRATIONS = "integrations"

    /** Every per-item/per-series playback-preference row (the local backup is NOT roam-capped — sync's 100-row cap is sync-only). */
    const val ITEM_PREFS = "itemPrefs"

    /** Smart/mood playlist definitions + per-playlist user preferences (whole rows; no cached item lists exist). */
    const val PLAYLISTS = "playlists"

    /** Home-screen widget CONFIG only (`widget_config` + `widget_configs`; the payload-cache keys stay out). */
    const val WIDGET = "widget"
}
