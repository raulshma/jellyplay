package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveEngineFactoryTest {

    private lateinit var context: Context
    private lateinit var client: OkHttpClient
    private lateinit var factory: LiveEngineFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = OkHttpClient()
        factory = LiveEngineFactory(context, client)
    }

    @Test
    fun create_returnsExoLiveEngineInstance() {
        val config = LiveEngineConfig()
        val engine = factory.create(config) {}
        assertNotNull(engine)
    }
}
