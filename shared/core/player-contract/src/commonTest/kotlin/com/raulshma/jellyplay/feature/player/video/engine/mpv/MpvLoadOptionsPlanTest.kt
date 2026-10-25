package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlaybackTls
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvLoadOptionsPlan
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure pins for the shared per-request mpv option ladder
 * ([MpvLoadOptionsPlan]) — the decisions both mpv hosts' load paths used to
 * hand-copy (Android `configureMpvForRequest`, desktop `MpvDesktopEngine.load`):
 * the full ordered write list, the User-Agent extraction, the language-list
 * normalization + blank-skip, the start composition, the TLS reset trio, the
 * readahead composition, and both header-fields spellings (including the
 * UNCONDITIONAL reset row that keeps the previous item's credentials off a
 * header-less request). The write-log assertion surface is the pair list
 * itself — the same shape MpvCoreTest asserts against a recorded binding.
 */
class MpvLoadOptionsPlanTest {

    private fun plan(
        headers: Map<String, String> = emptyMap(),
        audio: String? = null,
        subs: String? = null,
        startMs: Long = 0L,
        tls: PlaybackTls? = null,
        maxBufferMs: Int? = null,
        spelling: MpvLoadOptionsPlan.HeaderFieldsSpelling =
            MpvLoadOptionsPlan.HeaderFieldsSpelling.RESET_AND_APPEND,
    ): MpvLoadOptionsPlan.Plan = MpvLoadOptionsPlan.plan(
        MpvLoadOptionsPlan.Request(
            headers = headers,
            preferredAudioLanguage = audio,
            preferredSubtitleLanguage = subs,
            startPositionMs = startMs,
            tls = tls,
            maxBufferMs = maxBufferMs,
        ),
        headerFields = spelling,
    )

    // ── the full ladder, in order ───────────────────────────────────────────

    @Test
    fun fullLadder_writesAreOrdered_tlsThenLanguagesThenUaThenHeadersThenReadahead() {
        val p = plan(
            headers = mapOf("User-Agent" to "JellyPlay/1.0", "Accept" to "*/*"),
            audio = "en",
            subs = "ja",
            startMs = 61_500,
            tls = PlaybackTls(clientCertificatePath = "c.crt", clientKeyPath = "c.key"),
            maxBufferMs = 50_000,
        )
        assertEquals(
            listOf(
                "tls-cert-file" to "c.crt",
                "tls-key-file" to "c.key",
                MpvProperties.ALANG to "en",
                MpvProperties.SLANG to "ja",
                MpvProperties.USER_AGENT to "JellyPlay/1.0",
                MpvProperties.HTTP_HEADER_FIELDS to "",
                MpvProperties.HTTP_HEADER_FIELDS_APPEND to "Accept: */*",
                MpvProperties.DEMUXER_READAHEAD_SECS to "50",
            ),
            p.optionWrites,
        )
        // The resume position is NOT an option write — the hosts map it onto
        // their own start transports (property vs loadfile slot).
        assertEquals("+61.5", p.start)
    }

    // ── User-Agent extraction (the desktop parity fix) ──────────────────────

    @Test
    fun userAgent_extractedFromHeaders_anyKeyCase_neverBuriedInTheFields() {
        for (key in listOf("User-Agent", "user-agent", "USER-AGENT", "uSeR-aGeNt")) {
            val p = plan(headers = mapOf(key to "JellyPlay/2.0", "Accept" to "*/*"))
            assertTrue(
                MpvProperties.USER_AGENT to "JellyPlay/2.0" in p.optionWrites,
                "the UA property write must be planned for key '$key'",
            )
            val headerRows = p.optionWrites.filter { it.first == MpvProperties.HTTP_HEADER_FIELDS_APPEND }
            assertTrue(
                headerRows.all { !it.second.startsWith("User-Agent", ignoreCase = true) },
                "the UA entry must leave the header fields (key '$key'): $headerRows",
            )
        }
    }

    @Test
    fun userAgent_blankValue_writesNothing_andVanishesFromTheFields() {
        // A blank UA is not demoted back into http-header-fields — it is
        // dropped (the Android adapter's shipped shape).
        val p = plan(headers = mapOf("User-Agent" to "  ", "Accept" to "*/*"))
        assertFalse(p.optionWrites.any { it.first == MpvProperties.USER_AGENT })
        assertEquals(
            listOf(MpvProperties.HTTP_HEADER_FIELDS_APPEND to "Accept: */*"),
            p.optionWrites.filter { it.first == MpvProperties.HTTP_HEADER_FIELDS_APPEND },
        )
    }

    @Test
    fun userAgent_absent_noPropertyWrite() {
        val p = plan(headers = mapOf("Accept" to "*/*"))
        assertFalse(p.optionWrites.any { it.first == MpvProperties.USER_AGENT })
    }

    // ── language normalization + blank-skip (the desktop parity fixes) ──────

    @Test
    fun languageLists_normalized_semicolonSplitTrimmedUnderscoreToDash() {
        val p = plan(audio = "de; en_US , fr", subs = "  ja  ")
        assertEquals(MpvProperties.ALANG to "de,en-US,fr", p.optionWrites[3])
        assertEquals(MpvProperties.SLANG to "ja", p.optionWrites[4])
    }

    @Test
    fun languageLists_blankOrMissing_skippedEntirely() {
        for (blank in listOf(null, "", "   ")) {
            val p = plan(audio = blank, subs = blank)
            // Only the TLS trio + the append-spelling's unconditional header
            // reset — no language rows at all (a blank alang would deselect
            // mpv's language heuristic).
            assertTrue(
                p.optionWrites.none { it.first == MpvProperties.ALANG || it.first == MpvProperties.SLANG },
                "blank language '$blank' must plan no write",
            )
        }
    }

    @Test
    fun normalizeLanguageList_edgeShapes() {
        assertEquals("", MpvLoadOptionsPlan.normalizeLanguageList("  ; , "))
        assertEquals("und", MpvLoadOptionsPlan.normalizeLanguageList("und"))
        assertEquals("pt-BR,en", MpvLoadOptionsPlan.normalizeLanguageList("pt_BR,en"))
        assertEquals("zh,en", MpvLoadOptionsPlan.normalizeLanguageList("zh,,en"))
    }

    // ── start composition ───────────────────────────────────────────────────

    @Test
    fun start_zeroOrNegative_null() {
        assertNull(plan(startMs = 0L).start)
        assertNull(plan(startMs = -5_000L).start)
    }

    @Test
    fun start_positive_secondsWithFraction() {
        assertEquals("+90.0", plan(startMs = 90_000L).start)
        assertEquals("+1.5", plan(startMs = 1_500L).start)
        assertEquals("+61.234", plan(startMs = 61_234L).start)
    }

    // ── TLS trio ────────────────────────────────────────────────────────────

    @Test
    fun tls_absent_resetTrioLands_asTheFirstWrites() {
        val p = plan()
        assertEquals(
            listOf(
                "tls-cert-file" to "",
                "tls-key-file" to "",
                "tls-ca-file" to "",
            ),
            p.optionWrites.take(3),
        )
    }

    @Test
    fun tls_present_writesTheConfiguredPaths_caOnlyWhenSupplied() {
        val tlsRows = listOf(
            "tls-cert-file" to "c.crt",
            "tls-key-file" to "c.key",
        )
        assertEquals(
            tlsRows + ("tls-ca-file" to "ca.pem"),
            plan(tls = PlaybackTls("c.crt", "c.key", "ca.pem")).optionWrites.take(3),
        )
        // No caPath → no tls-ca-file row at all (mpv keeps its default CA
        // store; the reset trio only applies to the no-certificate case).
        val withoutCa = plan(tls = PlaybackTls("c.crt", "c.key")).optionWrites.take(2)
        assertEquals(tlsRows, withoutCa)
        assertFalse(withoutCa.any { it.first == "tls-ca-file" })
    }

    // ── readahead composition ───────────────────────────────────────────────

    @Test
    fun readahead_integerSeconds_nullMeansTheHostNeverWritesIt() {
        fun readaheadRow(maxBufferMs: Int?) =
            plan(maxBufferMs = maxBufferMs).optionWrites
                .lastOrNull { it.first == MpvProperties.DEMUXER_READAHEAD_SECS }
        // Integer composition: milliseconds floor to whole seconds.
        assertEquals("50", readaheadRow(50_000)?.second)
        assertEquals("49", readaheadRow(49_999)?.second)
        // null = the host does not write readahead per request (Android).
        assertEquals(null, readaheadRow(null))
    }

    // ── header-fields spellings ─────────────────────────────────────────────

    @Test
    fun resetAndAppend_resetRowIsUnconditional_keepingHeaderlessRequestsClean() {
        // The persistence discipline: options persist on the handle, so a
        // header-less request still clears the previous item's credentials —
        // the reset row must land even with NOTHING to append.
        val p = plan(headers = emptyMap(), spelling = MpvLoadOptionsPlan.HeaderFieldsSpelling.RESET_AND_APPEND)
        assertTrue(MpvProperties.HTTP_HEADER_FIELDS to "" in p.optionWrites)
        assertFalse(p.optionWrites.any { it.first == MpvProperties.HTTP_HEADER_FIELDS_APPEND })
    }

    @Test
    fun resetAndAppend_commaContainingValue_landsVerbatim_inOneAppend() {
        val emby = "MediaBrowser Client=\"JellyPlay\", DeviceId=\"abc\", Version=\"1.0\""
        val p = plan(
            headers = mapOf("X-Emby-Authorization" to emby),
            spelling = MpvLoadOptionsPlan.HeaderFieldsSpelling.RESET_AND_APPEND,
        )
        // The reason the spelling exists: the -append row carries the value
        // whole — no comma-splitting between client and device fields.
        assertEquals(
            listOf(MpvProperties.HTTP_HEADER_FIELDS_APPEND to "X-Emby-Authorization: $emby"),
            p.optionWrites.filter { it.first == MpvProperties.HTTP_HEADER_FIELDS_APPEND },
        )
    }

    @Test
    fun replaceList_entriesCarryMpvLengthQuoting_commaValuesSurvive() {
        // X-Emby-Authorization carries commas; a bare comma-join would split
        // it into bogus entries — each entry rides mpv's `%length%` quoting
        // instead (the parity test's readback pins the parsed value).
        val auth = "X-Emby-Authorization: MediaBrowser Client=\"T\", DeviceId=\"abc\""
        val p = plan(
            headers = mapOf(
                "User-Agent" to "JellyPlay/3.0",
                "X-Emby-Authorization" to "MediaBrowser Client=\"T\", DeviceId=\"abc\"",
            ),
            spelling = MpvLoadOptionsPlan.HeaderFieldsSpelling.REPLACE_LIST,
        )
        val expected = "%${auth.encodeToByteArray().size}%$auth"
        assertEquals(
            listOf(MpvProperties.HTTP_HEADER_FIELDS to expected),
            p.optionWrites.filter { it.first == MpvProperties.HTTP_HEADER_FIELDS },
        )
    }

    @Test
    fun commaJoinedProperty_singleReplaceWrite_resetIsUnconditional() {
        val p = plan(
            headers = mapOf("Accept" to "*/*", "Range" to "bytes=0-"),
            spelling = MpvLoadOptionsPlan.HeaderFieldsSpelling.REPLACE_LIST,
        )
        assertEquals(
            listOf(MpvProperties.HTTP_HEADER_FIELDS to "%11%Accept: */*,%15%Range: bytes=0-"),
            p.optionWrites.filter { it.first.startsWith(MpvProperties.HTTP_HEADER_FIELDS) },
        )
        // The persistence discipline holds in BOTH spellings: a request whose
        // headers leave nothing after the UA extraction still writes the
        // replace row — empty — so the previous item's credentials clear (the
        // property write replaces the whole list; the reset is never omitted).
        val uaOnly = plan(
            headers = mapOf("User-Agent" to "JellyPlay/3.0"),
            spelling = MpvLoadOptionsPlan.HeaderFieldsSpelling.REPLACE_LIST,
        )
        assertEquals(
            listOf(MpvProperties.HTTP_HEADER_FIELDS to ""),
            uaOnly.optionWrites.filter { it.first.startsWith(MpvProperties.HTTP_HEADER_FIELDS) },
        )
    }
}
