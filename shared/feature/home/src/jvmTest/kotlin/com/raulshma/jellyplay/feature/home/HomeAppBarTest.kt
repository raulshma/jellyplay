package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeAppBarTest {

    @Test
    fun resolveHeaderStatus_whenOffline_returnsOffline() {
        val status = resolveHeaderStatus(
            isLoading = false,
            hasError = false,
            networkStatus = NetworkStatus.Offline,
            serverHealth = ServerHealth.Healthy(50L),
        )
        assertEquals(HeaderStatus.Offline, status)
    }

    @Test
    fun resolveHeaderStatus_whenLoading_returnsLoading() {
        val status = resolveHeaderStatus(
            isLoading = true,
            hasError = false,
            networkStatus = NetworkStatus.Online,
            serverHealth = ServerHealth.Healthy(50L),
        )
        assertEquals(HeaderStatus.Loading, status)
    }

    @Test
    fun homeMode_videoAndMusic_values() {
        val videoMode = HomeMode.VIDEO
        val musicMode = HomeMode.MUSIC

        assertEquals(HomeMode.VIDEO, videoMode)
        assertEquals(HomeMode.MUSIC, musicMode)
    }
}
