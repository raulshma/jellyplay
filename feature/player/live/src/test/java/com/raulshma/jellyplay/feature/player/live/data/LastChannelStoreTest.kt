package com.raulshma.jellyplay.feature.player.live.data

import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LastChannelStoreTest {

    private lateinit var userPreferencesStore: UserPreferencesStore
    private lateinit var store: LastChannelStore

    @Before
    fun setUp() {
        userPreferencesStore = mockk(relaxed = true)
        store = LastChannelStore(userPreferencesStore)
    }

    @Test
    fun observeLastChannelId_delegatesToPreferencesStore() {
        every { userPreferencesStore.observeLiveTvLastChannelId() } returns flowOf("channel-10")

        store.observeLastChannelId()
        verify { userPreferencesStore.observeLiveTvLastChannelId() }
    }

    @Test
    fun setLastChannelId_delegatesToPreferencesStore() = runBlocking {
        store.setLastChannelId("channel-42")
        coVerify { userPreferencesStore.setLiveTvLastChannelId("channel-42") }
    }
}
