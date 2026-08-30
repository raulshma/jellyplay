package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.model.subtitle.SavedSubtitle
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import java.io.File

/**
 * In-memory [StreamingSubtitleStore] for tests that construct collaborators
 * (e.g. [PlayerSessionManager], [SubtitleManager]) and need to verify a save
 * happened without touching the real file-backed impl.
 *
 * Tracks saves in a per-itemId map so `loadAll` round-trips what `save`
 * recorded. `fileFor` returns a placeholder path so side-load URL construction
 * doesn't NPE; the real persistence round-trip is covered by
 * `StreamingSubtitleStoreImplTest` in shared :core:data's jvmTest.
 */
internal fun noOpStreamingSubtitleStore(): StreamingSubtitleStore =
    object : StreamingSubtitleStore {
        private val saved = mutableMapOf<String, MutableList<SavedSubtitle>>()

        override suspend fun save(
            itemId: String,
            provider: SubtitleProviderKind,
            providerSubtitleId: String,
            fileName: String,
            language: String?,
            codec: String?,
            isForced: Boolean,
            isHearingImpaired: Boolean,
            bytes: ByteArray,
        ): SavedSubtitle {
            val entry = SavedSubtitle(
                provider = provider,
                providerSubtitleId = providerSubtitleId,
                fileName = fileName,
                language = language,
                codec = codec,
                isForced = isForced,
                isHearingImpaired = isHearingImpaired,
                fileRelativePath = "$providerSubtitleId.${codec ?: "srt"}",
            )
            val list = saved.getOrPut(itemId) { mutableListOf() }
            // Idempotent: same provider+id replaces, matching prod semantics.
            list.removeAll {
                it.provider == provider && it.providerSubtitleId == providerSubtitleId
            }
            list += entry
            return entry
        }

        override suspend fun loadAll(itemId: String): List<SavedSubtitle> =
            saved[itemId]?.toList() ?: emptyList()

        override suspend fun fileFor(itemId: String, saved: SavedSubtitle): File =
            File(System.getProperty("java.io.tmpdir"), saved.fileRelativePath)

        override suspend fun delete(itemId: String, saved: SavedSubtitle) {
            this.saved[itemId]?.removeAll {
                it.provider == saved.provider && it.providerSubtitleId == saved.providerSubtitleId
            }
        }

        override suspend fun markServerStreamIndex(itemId: String, saved: SavedSubtitle, index: Int) {
            val list = this.saved[itemId] ?: return
            val at = list.indexOfFirst {
                it.provider == saved.provider && it.providerSubtitleId == saved.providerSubtitleId
            }
            if (at >= 0) list[at] = list[at].copy(serverStreamIndex = index)
        }

        override suspend fun clear(itemId: String) {
            saved.remove(itemId)
        }
    }
