package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the store-side provider-subtitle choreography
 * ([EditorSubtitleStore.saveUploadAndAttribute]) — the save → encode →
 * upload → reload → attribute sequencing EditorViewModel's
 * `handleDownloadProviderSubtitle` used to inline. A minimal event-recording
 * fake drives the REAL interface default body and pins its order, its
 * Base64 payload, and the pre-upload snapshot rule; the failure path pins
 * that a failed upload still leaves the durable copy and skips the reload +
 * attribution legs.
 */
class EditorSubtitleStoreChoreographyTest {

    private fun save(itemId: String = "item-1") = ProviderSubtitleSave(
        itemId = itemId,
        provider = SubtitleProviderKind.WYZIE,
        providerSubtitleId = "wz-1",
        fileName = "ext.srt",
        language = "eng",
        codec = "srt",
        isForced = false,
        isHearingImpaired = false,
        bytes = byteArrayOf(1, 2, 3),
    )

    private fun externalSubtitleStream(index: Int) =
        MediaStream(index = index, type = StreamType.SUBTITLE, codec = "srt", isExternal = true)

    /** Recording store: the abstract halves log events; the choreography itself is the inherited default body. */
    private class RecordingStore(
        val events: MutableList<String>,
    ) : EditorSubtitleStore {

        var attributedPreUploadIndices: Set<Int>? = null
        var attributedStreamsAfterUpload: List<MediaStream>? = null

        override suspend fun save(save: ProviderSubtitleSave) {
            events.add("save")
        }

        override suspend fun attributeUploaded(
            save: ProviderSubtitleSave,
            streamsAfterUpload: List<MediaStream>,
            preUploadExternalIndices: Set<Int>,
        ) {
            events.add("attribute")
            attributedPreUploadIndices = preUploadExternalIndices
            attributedStreamsAfterUpload = streamsAfterUpload
        }

        override suspend fun purgeDeletedServerStreamCopies(itemId: String, index: Int, deletedStream: MediaStream?) {
            error("not part of this choreography")
        }
    }

    @Test
    fun `the default choreography sequences save snapshot upload reload attribute`() = runTest {
        val events = mutableListOf<String>()
        val store = RecordingStore(events)
        var streams: List<MediaStream> = listOf(externalSubtitleStream(2))
        val uploads = mutableListOf<String>()

        val result = store.saveUploadAndAttribute(
            save = save(),
            streams = { streams },
            upload = { base64 ->
                events.add("upload")
                uploads.add(base64)
                // The upload itself lands the new server stream: the state the
                // later legs read must reflect the POST-upload world...
                streams = listOf(externalSubtitleStream(2), externalSubtitleStream(9))
                Result.success(Unit)
            },
            onUploaded = { events.add("reload") },
        )

        assertTrue(result.isSuccess)
        // Order: durable copy first, upload second, reload before attribution.
        assertEquals(listOf("save", "upload", "reload", "attribute"), events)
        // The upload received the Base64-encoded bytes (1,2,3 → "AQID").
        assertEquals(listOf(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))), uploads)
        // ...but the attribution's match set is the PRE-upload snapshot
        // (taken between save and upload): the choreography-added stream 9
        // is excluded, and the post-upload stream list carries both.
        assertEquals(setOf(2), store.attributedPreUploadIndices)
        assertEquals(listOf(2, 9), store.attributedStreamsAfterUpload?.map { it.index })
    }

    @Test
    fun `a failed upload skips the reload and the attribution but keeps the durable copy`() = runTest {
        val events = mutableListOf<String>()
        val store = RecordingStore(events)

        val result = store.saveUploadAndAttribute(
            save = save(),
            streams = { listOf(externalSubtitleStream(2)) },
            upload = { Result.failure(IllegalStateException("server offline")) },
            onUploaded = { events.add("reload") },
        )

        assertTrue(result.isFailure)
        assertEquals("server offline", result.exceptionOrNull()?.message)
        // Only the durable save ran — no reload, no attribution.
        assertEquals(listOf("save"), events)
        assertEquals(null, store.attributedPreUploadIndices)
    }

    @Test
    fun `non-external subtitle streams never enter the pre-upload match set`() = runTest {
        // The snapshot filter mirrors the former inline one: only EXTERNAL
        // subtitle streams are candidates for attribution matching.
        val events = mutableListOf<String>()
        val store = RecordingStore(events)
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO, codec = "h264"),
            MediaStream(index = 1, type = StreamType.AUDIO, codec = "aac"),
            MediaStream(index = 2, type = StreamType.SUBTITLE, codec = "srt", isExternal = true),
            // Embedded (non-external) subtitle: never a "local copy" candidate.
            MediaStream(index = 3, type = StreamType.SUBTITLE, codec = "subrip", isExternal = false),
        )

        store.saveUploadAndAttribute(
            save = save(),
            streams = { streams },
            upload = { Result.success(Unit) },
            onUploaded = { },
        )

        assertEquals(setOf(2), store.attributedPreUploadIndices)
    }
}
