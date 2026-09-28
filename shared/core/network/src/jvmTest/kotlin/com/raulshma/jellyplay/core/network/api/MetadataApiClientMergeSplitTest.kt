package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the raw-path request shapes of the version group/split endpoints —
 * the Jellyfin SDK has no typed API for either, so the wire contract lives
 * in the extracted builders ([mergeVersionsIdsValue], [splitVersionsPath],
 * [MERGE_VERSIONS_PATH]) and the client-side < 2 ids guard.
 */
class MetadataApiClientMergeSplitTest {

    // The guards/builders are pure; the engine is inert here (the mockk
    // pattern from MetadataApiClientImplImageSniffTest; same-package ctor
    // deps need no imports).
    private val client = MetadataApiClientImpl(
        JellyfinApiEngine(
            LazyProvider { mockk<Jellyfin>(relaxed = true) },
            LazyProvider { OkHttpClient() },
            DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            ServerAddressRouter(),
        ),
    )

    @Test
    fun `mergeVersionsIdsValue joins the ids comma-separated in call order`() {
        assertEquals("a,b,c", mergeVersionsIdsValue(listOf("a", "b", "c")))
        assertEquals("only", mergeVersionsIdsValue(listOf("only")))
    }

    @Test
    fun `splitVersionsPath embeds the item id`() {
        assertEquals("/Videos/item-9/AlternateSources", splitVersionsPath("item-9"))
    }

    @Test
    fun `mergeVersions fails fast on fewer than 2 ids`() = runTest {
        // The server 400s a degenerate call; the client-side guard keeps the
        // error local and stops the request from firing at all.
        assertFailsWith<IllegalArgumentException> {
            client.mergeVersions(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            client.mergeVersions(listOf("only-one"))
        }
    }
}
