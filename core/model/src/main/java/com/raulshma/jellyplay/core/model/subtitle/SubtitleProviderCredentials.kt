package com.raulshma.jellyplay.core.model.subtitle

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-provider credentials, persisted encrypted in
 * [com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore].
 *
 * Each concrete subclass corresponds 1:1 with a [SubtitleProviderKind] that
 * needs a secret. [SubtitleProviderKind.JELLYFIN] has no credentials (it uses
 * the active server session), so it never appears here.
 *
 * Adding a provider that needs auth = add a subclass here + handle it in the
 * secure store's (de)serialization and the network provider impl.
 */
@Immutable
@Serializable
sealed class SubtitleProviderCredentials {

    /**
     * True when the secret material needed to authenticate is present. Used by
     * the fan-out repository to decide whether a provider is actually
     * *configured* (enabled toggle + non-blank key), independent of the enable
     * toggle.
     */
    abstract val isConfigured: Boolean

    /** Wyzie Subs — a single API key, passed as the `key` query param. */
    @Immutable
    @Serializable
    @SerialName("wyzie")
    data class Wyzie(
        val apiKey: String,
    ) : SubtitleProviderCredentials() {
        override val isConfigured: Boolean get() = apiKey.isNotBlank()
    }

    /**
     * OpenSubtitles — an `Api-Key` header is always required; an optional
     * username + password unlocks a higher per-day download quota via JWT login.
     *
     * [jwt] / [jwtExpiresAt] cache the logged-in bearer token (epoch millis) so
     * the provider can skip re-login on every request; they are refreshed by the
     * network layer when expired or on a 401.
     */
    @Immutable
    @Serializable
    @SerialName("opensubtitles")
    data class OpenSubtitles(
        val apiKey: String,
        val username: String? = null,
        val password: String? = null,
        val jwt: String? = null,
        val jwtExpiresAt: Long = 0,
    ) : SubtitleProviderCredentials() {
        override val isConfigured: Boolean get() = apiKey.isNotBlank()
    }
}
