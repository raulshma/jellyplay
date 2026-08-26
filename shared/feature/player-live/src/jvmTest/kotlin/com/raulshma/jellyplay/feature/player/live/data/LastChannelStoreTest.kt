package com.raulshma.jellyplay.feature.player.live.data

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

class LastChannelStoreTest {

    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var store: LastChannelStore

    @BeforeTest
    fun setUp() {
        appRuntimeStateStore = mockk(relaxed = true)
        store = LastChannelStore(appRuntimeStateStore)
    }

    @Test
    fun observeLastChannelId_delegatesToPreferencesStore() {
        every { appRuntimeStateStore.observeLiveTvLastChannelId() } returns flowOf("channel-10")

        store.observeLastChannelId()
        verify { appRuntimeStateStore.observeLiveTvLastChannelId() }
    }

    @Test
    fun setLastChannelId_delegatesToPreferencesStore() = runBlocking {
        store.setLastChannelId("channel-42")
        coVerify { appRuntimeStateStore.setLiveTvLastChannelId("channel-42") }
    }
}
