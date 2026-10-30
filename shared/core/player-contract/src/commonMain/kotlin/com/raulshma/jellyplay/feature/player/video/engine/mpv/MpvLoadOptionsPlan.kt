package com.raulshma.jellyplay.feature.player.video.engine.mpv

// Moved verbatim from :shared:feature:player-video (SAME package, zero consumer
// import churn) so the mpv option vocabulary has one home beside MpvPropertyIntake
// and MpvStyleMapping; the engine choreography (MpvCore, MpvBinding) stayed behind.
import com.raulshma.jellyplay.core.model.PlaybackTls
import com.raulshma.jellyplay.feature.player.video.engine.MpvTlsOptions

/**
 * Pure plan for the per-request mpv option ladder — the decision half of both
 * mpv hosts' load choreography (Android `MpvPlayerEngine.configureMpvForRequest`,
 * desktop `MpvDesktopEngine.load`; the [MpvSubtitleSideLoadPlan] precedent:
 * policy beside the mpv core, thin per-host transports). The engines execute
 * the returned [Plan.optionWrites] through their one `(option, value)` write
 * seam and map [Plan.start] onto their own start transport; the plan owns
 * every decision the two ladders used to hand-copy:
 *
 * - **TLS reset trio** via [MpvTlsOptions.from] — the `tls-*` file-path
 *   options PERSIST on the handle, so the empty trio when no certificate is
 *   active keeps the previous item's credentials off this item's server.
 * - **alang/slang** with the language-list normalization ([normalizeLanguageList]:
 *   split on `,`/`;`, trim, `_`→`-`, blanks dropped) and a BLANK-SKIP —
 *   both the Android decisions. The desktop formerly wrote the raw string
 *   (even a blank one) onto the option.
 * - **User-Agent extraction**: `User-Agent` (case-insensitive) is pulled out
 *   of the headers into the `user-agent` option — mpv drives its default UA
 *   from the property (including the stream-probe request), and some servers
 *   parse the buried header form inconsistently — again the Android
 *   decision; the desktop formerly buried it in http-header-fields. The
 *   entry leaves the header set EITHER WAY (a blank UA value vanishes, it is
 *   not demoted back into the fields).
 * - **http-header-fields persistence discipline** in the request's
 *   [HeaderFieldsSpelling] — the one genuinely per-transport row family.
 *   `REPLACE_LIST` (both engines) writes ONE unconditional
 *   `http-header-fields` replace-write whose entries carry mpv's canonical
 *   `%length%` list-quoting (`%n%` prefixes each entry with its byte length,
 *   so commas INSIDE a value — X-Emby-Authorization has them — never split
 *   the list, and a `%` inside a value is protected the same way). The write
 *   is UNCONDITIONAL — a header-less request writes the empty list — so the
 *   previous item's credentials (X-Emby-Authorization) are never sent to
 *   this item's server. The alternatives are rejected on evidence, not
 *   taste: a bare comma-join splits comma-containing headers into bogus
 *   entries, and the `-append` suffix — the desktop's former per-header
 *   spelling — is silently dropped by the option-write path on current
 *   libmpv (the parity test's readback comes back empty), which is exactly
 *   the kind of failure a silent-failing transport must not own.
 * - **Compositions**: `start` = `+{startPositionMs/1000.0}` when positive
 *   (file-local; Android writes the `start` property pre-load, the desktop
 *   rides loadfile's options slot — the value is the plan's, the transport
 *   the host's), `demuxer-readahead-secs` = `{maxBufferMs/1000}` (integer
 *   seconds). `maxBufferMs == null` means the host does not write readahead
 *   per request (Android never has) — a declared divergence, not drift.
 *
 * Canonical row order: TLS trio → alang → slang → user-agent → header rows →
 * readahead. Within-group disciplines are load-bearing and preserved exactly
 * (trio order from [MpvTlsOptions.from], reset-before-append, every write
 * before the load); the cross-group order is incidental — all rows are
 * independent option writes consumed at the next load — and replaces the
 * hosts' former accidental orders (desktop headers-first, Android
 * headers-last).
 *
 * Desktop consumption deliberately GAINS the three Android decisions (UA
 * extraction, language normalization, blank-skip) — that IS the unification.
 *
 * Pinned by `MpvLoadOptionsPlanTest` (player-video commonTest — the plan
 * is pure commonMain) and the real-libmpv load ladder slice of
 * `MpvDesktopEngineAndroidParityTest`.
 */
object MpvLoadOptionsPlan {

    /**
     * How the plan spells the `http-header-fields` rows — the host's list
     * transport (see the discipline notes above for why the alternatives
     * were rejected).
     */
    enum class HeaderFieldsSpelling {
        /**
         * One unconditional `http-header-fields` replace-write; each entry
         * carries mpv's `%length%` list-quoting so value-internal commas and
         * `%` signs survive the list parse.
         */
        REPLACE_LIST,

        /** Unconditional `""` reset row, then one `-append` row per entry. */
        RESET_AND_APPEND,
    }

    /** The request slice the ladder consumes (see the class KDoc for each decision). */
    data class Request(
        val headers: Map<String, String> = emptyMap(),
        val preferredAudioLanguage: String? = null,
        val preferredSubtitleLanguage: String? = null,
        val startPositionMs: Long = 0L,
        val tls: PlaybackTls? = null,
        /** `null` = the host does not write `demuxer-readahead-secs` per request. */
        val maxBufferMs: Int? = null,
    )

    /**
     * The planned load options: [optionWrites] in execution order (each a
     * plain `(option, value)` — the desktop seam is `mpv_set_option_string`,
     * the Android seam property/option writes on the live handle), and
     * [start] as the raw `+{seconds}` VALUE for the host's own start
     * transport (`null` = no resume position, no write).
     */
    data class Plan(
        val optionWrites: List<Pair<String, String>>,
        val start: String?,
    )

    fun plan(request: Request, headerFields: HeaderFieldsSpelling): Plan {
        val writes = mutableListOf<Pair<String, String>>()
        writes += MpvTlsOptions.from(request.tls)
        request.preferredAudioLanguage
            ?.takeIf { it.isNotBlank() }
            ?.let { writes += MpvProperties.ALANG to normalizeLanguageList(it) }
        request.preferredSubtitleLanguage
            ?.takeIf { it.isNotBlank() }
            ?.let { writes += MpvProperties.SLANG to normalizeLanguageList(it) }
        val userAgent = request.headers.entries
            .firstOrNull { it.key.equals(MpvProperties.USER_AGENT, ignoreCase = true) }
            ?.value
        if (!userAgent.isNullOrBlank()) {
            writes += MpvProperties.USER_AGENT to userAgent
        }
        val headerEntries = request.headers.entries
            .filter { !it.key.equals(MpvProperties.USER_AGENT, ignoreCase = true) }
        // The reset is UNCONDITIONAL in both spellings: a header-less request
        // must still clear the previous item's credentials (options persist
        // on the handle — the persistence discipline). REPLACE_LIST clears by
        // writing the (possibly empty) quoted list; the append form clears
        // with the explicit "" reset first.
        when (headerFields) {
            HeaderFieldsSpelling.REPLACE_LIST -> {
                // `%n%` prefixes each entry with its UTF-8 byte length —
                // mpv's canonical list-entry quoting, so a comma or `%`
                // inside a value never splits or re-quotes the list.
                writes += MpvProperties.HTTP_HEADER_FIELDS to
                    headerEntries.joinToString(",") { entry ->
                        val text = "${entry.key}: ${entry.value}"
                        val byteLength = text.encodeToByteArray().size
                        "%$byteLength%$text"
                    }
            }
            HeaderFieldsSpelling.RESET_AND_APPEND -> {
                writes += MpvProperties.HTTP_HEADER_FIELDS to ""
                headerEntries.forEach { entry ->
                    writes += MpvProperties.HTTP_HEADER_FIELDS_APPEND to "${entry.key}: ${entry.value}"
                }
            }
        }
        request.maxBufferMs?.let {
            writes += MpvProperties.DEMUXER_READAHEAD_SECS to (it / 1000).toString()
        }
        return Plan(
            optionWrites = writes,
            start = if (request.startPositionMs > 0) "+${request.startPositionMs / 1000.0}" else null,
        )
    }

    /**
     * mpv's language-list composition (the Android engine's shipped normalizer):
     * comma/semicolon-separated preference list, trimmed, `en_US` → `en-US`,
     * blank entries dropped, comma-joined. Blank INPUT yields `""` — callers
     * blank-skip before normalizing (a blank `alang` would deselect mpv's
     * language heuristic).
     */
    fun normalizeLanguageList(language: String): String =
        language.split(',', ';')
            .map { it.trim().replace('_', '-') }
            .filter { it.isNotBlank() }
            .joinToString(",")
}
