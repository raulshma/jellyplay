package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AdminApiClientImpl
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClientImpl
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClientImpl
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClientImpl
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClientImpl
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClientImpl
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import com.raulshma.jellyplay.core.network.api.PluginApiClientImpl
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClientImpl
import com.raulshma.jellyplay.core.network.api.UserApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClientImpl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiClientImpl @Inject constructor(
    private val authClient: AuthApiClientImpl,
    private val libraryClient: LibraryApiClientImpl,
    private val playbackClient: PlaybackApiClientImpl,
    private val syncPlayClient: SyncPlayApiClientImpl,
    private val liveTvClient: LiveTvApiClientImpl,
    private val adminClient: AdminApiClientImpl,
    private val metadataClient: MetadataApiClientImpl,
    private val mediaInfoClient: MediaInfoApiClientImpl,
    private val pluginClient: PluginApiClientImpl,
    private val userClient: UserApiClientImpl,
) : JellyfinApiClient,
    AuthApiClient by authClient,
    LibraryApiClient by libraryClient,
    PlaybackApiClient by playbackClient,
    SyncPlayApiClient by syncPlayClient,
    LiveTvApiClient by liveTvClient,
    AdminApiClient by adminClient,
    MetadataApiClient by metadataClient,
    MediaInfoApiClient by mediaInfoClient,
    PluginApiClient by pluginClient,
    UserApiClient by userClient
