package com.raulshma.jellyplay.core.ui.player

import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_decoder
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_engine
import com.raulshma.jellyplay.core.ui.generated.resources.transcode_reason_hint_quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins the commonMain transcode-reason catalog (the ONE token → compose-resource
 * table shared by the android formatter's R-string mirror and the desktop
 * compose-resources seam):
 *
 *  - every [TranscodeReasonKind] has a catalog entry — a kind missing here
 *    would silently fall back to the unknown-token template on desktop;
 *  - both token spellings the server uses (SDK SCREAMING_SNAKE, wire
 *    PascalCase) resolve to the same entry;
 *  - the remedy-hint pairing stays aligned with the android table
 *    (engine/decoder/quality classes and the hint-less kinds);
 *  - unknown tokens return null so callers keep the raw text visible.
 */
class TranscodeReasonCatalogTest {

    @Test
    fun `every kind has a catalog entry`() {
        TranscodeReasonKind.entries.forEach { kind ->
            assertNotNull(
                TranscodeReasonCatalog.lookup(kind.name),
                "no catalog entry for $kind",
            )
        }
    }

    @Test
    fun `both token spellings resolve to the same entry`() {
        TranscodeReasonKind.entries.forEach { kind ->
            val pascalCase = kind.name.split('_').joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
            val fromSdkName = TranscodeReasonCatalog.lookup(kind.name)
            val fromServerName = TranscodeReasonCatalog.lookup(pascalCase)
            assertEquals(fromSdkName, fromServerName, "spelling-agnostic for $kind")
        }
    }

    @Test
    fun `hint pairings align with the android table`() {
        assertEquals(
            Res.string.transcode_reason_hint_engine,
            TranscodeReasonCatalog.lookup("VideoCodecNotSupported")?.hint,
        )
        assertEquals(
            Res.string.transcode_reason_hint_decoder,
            TranscodeReasonCatalog.lookup("AUDIO_BIT_DEPTH_NOT_SUPPORTED")?.hint,
        )
        assertEquals(
            Res.string.transcode_reason_hint_quality,
            TranscodeReasonCatalog.lookup("ContainerBitrateExceedsLimit")?.hint,
        )
        // Server probe failure has no user remedy.
        assertNull(TranscodeReasonCatalog.lookup("DirectPlayError")?.hint)
        assertNull(TranscodeReasonCatalog.lookup("StreamCountExceedsLimit")?.hint)
    }

    @Test
    fun `unknown token resolves to null`() {
        assertNull(TranscodeReasonCatalog.lookup("BrandNewServerReason"))
    }

    @Test
    fun `unknown template is the shared fallback resource`() {
        assertSame(
            TranscodeReasonCatalog.unknownTemplate,
            TranscodeReasonCatalog.unknownTemplate,
        )
    }
}
