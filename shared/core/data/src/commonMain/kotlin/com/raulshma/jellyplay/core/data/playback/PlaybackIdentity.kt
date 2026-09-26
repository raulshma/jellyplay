package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackTls

/**
 * The playback session-identity reads: the token + base-URL pair a playback
 * consumer needs to address the active server. Extracted from the
 * `PlaybackRepository` surface, which used to smuggle these two session
 * credentials through as interface members — every one of that interface's
 * consumers thereby learned the identity vocabulary without needing it. The
 * four actual readers (BookSessionLoader, LiveTvPlayerViewModel,
 * PlayerSessionManager, DownloadSidecarCore) inject THIS narrow module
 * instead.
 *
 * Backed by [AuthApiClient] — the same source `PlaybackRepositoryImpl`'s
 * former pass-through members read (the engine's atomic
 * `activeServerAddress` / `currentUser` session) — so semantics are
 * byte-identical: `null` server URL or token means "no active session", and
 * URL builders keep their `""` sentinel handling.
 */
interface PlaybackIdentity {

    /** Base URL of the active server; `null` when no session is active. */
    fun serverUrl(): String?

    /** Access token of the signed-in user; `null` when no session is active. */
    fun accessToken(): String?

    /**
     * TLS client-certificate material for the active server: the
     * mpv engines' file-path view of the app-level mTLS certificate, or
     * `null` when none is enabled. Default `null` keeps fakes/test doubles
     * unchanged; the production wiring hands the
     * `ClientCertificateManager.playbackTlsPaths()` read through.
     */
    fun clientTls(): PlaybackTls? = null
}

/** Production impl: the network engine's session as surfaced by [AuthApiClient]. */
class DefaultPlaybackIdentity(
    private val apiClient: AuthApiClient,
    private val clientTlsReader: () -> PlaybackTls? = { null },
) : PlaybackIdentity {

    override fun serverUrl(): String? = apiClient.getServerUrl()

    override fun accessToken(): String? = apiClient.getAccessToken()

    override fun clientTls(): PlaybackTls? = clientTlsReader()
}
