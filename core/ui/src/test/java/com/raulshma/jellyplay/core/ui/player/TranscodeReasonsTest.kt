package com.raulshma.jellyplay.core.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranscodeReasonsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun pascalCase(kind: TranscodeReasonKind): String =
        kind.name.split('_').joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }

    @Test
    fun `format resolves every kind from both SDK and server spellings`() {
        TranscodeReasonKind.entries.forEach { kind ->
            val fromSdkName = TranscodeReasonsFormatter.format(context, listOf(kind.name))
            val fromServerName = TranscodeReasonsFormatter.format(context, listOf(pascalCase(kind)))

            assertEquals("one entry for $kind", 1, fromSdkName.size)
            assertEquals(
                "spelling-agnostic for $kind",
                fromSdkName.single().explanation,
                fromServerName.single().explanation,
            )
            assertEquals(
                "hint matches across spellings for $kind",
                fromSdkName.single().hint,
                fromServerName.single().hint,
            )
            // The explanation string must actually resolve to non-blank text.
            assertTrue(fromSdkName.single().explanation.isNotBlank())
            // A kind missing from stringsByKind would silently fall back to
            // the unknown-token template (also non-blank) — catch it per kind.
            assertNotEquals(
                "no localized explanation mapped for $kind",
                context.getString(R.string.transcode_reason_unknown, kind.name),
                fromSdkName.single().explanation,
            )
        }
    }

    @Test
    fun `unknown token renders the raw server token in the generic explanation`() {
        val formatted = TranscodeReasonsFormatter.format(context, listOf("BrandNewServerReason"))
        assertEquals(1, formatted.size)
        assertTrue(formatted.single().explanation.contains("BrandNewServerReason"))
        assertNull(formatted.single().hint)
    }

    @Test
    fun `blank entries are dropped and casing duplicates are merged`() {
        val formatted = TranscodeReasonsFormatter.format(
            context,
            listOf("", "   ", "VideoCodecNotSupported", "VIDEO_CODEC_NOT_SUPPORTED"),
        )
        assertEquals(1, formatted.size)
    }

    @Test
    fun `codec reasons carry remedy hints`() {
        val video = TranscodeReasonsFormatter.format(context, listOf("VideoCodecNotSupported")).single()
        val bitrate = TranscodeReasonsFormatter.format(context, listOf("ContainerBitrateExceedsLimit")).single()
        val probe = TranscodeReasonsFormatter.format(context, listOf("DirectPlayError")).single()

        assertEquals(context.getString(R.string.transcode_reason_hint_engine), video.hint)
        assertEquals(context.getString(R.string.transcode_reason_hint_quality), bitrate.hint)
        assertNull("no user remedy for a server probe failure", probe.hint)
    }

    @Test
    fun `known kinds never render the unknown-token fallback`() {
        val known = TranscodeReasonsFormatter.format(context, listOf("VideoCodecNotSupported")).single()
        assertNotEquals(
            context.getString(R.string.transcode_reason_unknown, "VideoCodecNotSupported"),
            known.explanation,
        )
    }
}
