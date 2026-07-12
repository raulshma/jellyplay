package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.api.PluginApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.api.UserApiClient

interface JellyfinApiClient :
    AuthApiClient,
    LibraryApiClient,
    PlaybackApiClient,
    SyncPlayApiClient,
    LiveTvApiClient,
    AdminApiClient,
    MetadataApiClient,
    MediaInfoApiClient,
    PluginApiClient,
    UserApiClient
