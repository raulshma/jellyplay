package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCast
import com.raulshma.jellyplay.core.model.seerr.SeerrCast
import com.raulshma.jellyplay.core.ui.components.formatDurationFromMinutes
import kotlinx.datetime.LocalDate

/**
 * Neutral cast-member view used by the detail UI. The Seerr API exposes cast
 * two ways — [SeerrAggregateCast] (TV, role-based) and [SeerrCast] (movie,
 * single character) — with no shared interface. This value type is the single
 * shape the cast row renders, mapped at the call site, so the composable no
 * longer takes `List<Any>` and `when`-branches on the source type.
 */
internal data class SeerrCastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
)

/** Maps a TV aggregate-cast list to the neutral [SeerrCastMember] view. */
internal fun List<SeerrAggregateCast>.toAggregateCastMembers(): List<SeerrCastMember> =
    map { member ->
        SeerrCastMember(
            id = member.id,
            name = member.name,
            character = member.roles.firstOrNull()?.character ?: "",
            profileUrl = member.profileUrl,
        )
    }

/** Maps a movie cast list to the neutral [SeerrCastMember] view. */
internal fun List<SeerrCast>.toCastMembers(): List<SeerrCastMember> =
    map { member ->
        SeerrCastMember(
            id = member.id,
            name = member.name,
            character = member.character ?: "",
            profileUrl = member.profileUrl,
        )
    }

/**
 * Seerr-style date formatting. Parses an ISO date ("yyyy-MM-dd") and renders a
 * short form; falls back to the first 10 chars on parse failure.
 *
 * Wave 16C purification: HEAD parsed with `java.time.format.DateTimeFormatter
 * .ofPattern("yyyy-MM-dd")` and formatted with "MMM d, yyyy" + Locale.US. That
 * output is FIXED-ENGLISH on every platform (Locale.US was explicit), so the
 * pure-common replacement below — kotlinx-datetime parse + fixed English
 * month abbreviations — is byte-equal on valid Seerr dates ("Jul 29, 2026").
 * One deliberate edge delta: java's SMART resolver clamped impossible dates
 * ("2026-02-30" → "Feb 28, 2026"); kotlinx-datetime rejects them, so such
 * input now takes the take(10) fallback — which is what the documented
 * contract ("falls back to the first 10 chars on parse failure") already
 * prescribed. Real Seerr payloads only carry valid ISO dates.
 */
private val MONTH_ABBREVIATIONS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun formatDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val year = date.year.toString().padStart(4, '0')
        "${MONTH_ABBREVIATIONS[date.monthNumber - 1]} ${date.dayOfMonth}, $year"
    } catch (_: Exception) {
        dateStr.take(10)
    }
}

/**
 * Runtime formatting (minutes → "Xh Ym" / "Xh" / "Ym").
 *
 * Delegates to the shared [formatDurationFromMinutes] in core/ui — one
 * implementation instead of the copy that previously lived here.
 */
internal fun formatRuntime(minutes: Int): String = formatDurationFromMinutes(minutes.toLong())

/**
 * Builds a YouTube thumbnail URL for a related video, or null when the video is
 * not hosted on YouTube (no thumbnail source is available for other sites).
 * Centralized here so the media-detail and Seerr video rows share one builder.
 */
internal fun youTubeThumbnailUrl(site: String?, key: String?): String? =
    if (site?.equals("youtube", ignoreCase = true) == true && !key.isNullOrBlank()) {
        "https://img.youtube.com/vi/$key/mqdefault.jpg"
    } else null

/**
 * Converts a 2-letter ISO country code into its flag emoji.
 *
 * Extracted verbatim from `SeerrDetailScreen.kt`; wave 16C purification moved
 * the code-point arithmetic off `java.lang.Character` (JVM-only) onto the
 * exact-math replicas below — identical outputs for every ISO country code,
 * including the lowercase non-flag edge the jvmTest pins ('g' → U+1F10C, not
 * a regional indicator).
 */
internal fun getFlagEmoji(countryCode: String): String? {
    if (countryCode.length != 2) return null
    val firstChar = codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondChar = codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return codePointToString(firstChar) + codePointToString(secondChar)
}

/**
 * `java.lang.Character.codePointAt(seq, index)` verbatim: surrogate-pair aware
 * (a high+low pair at [index] yields the supplementary code point, otherwise
 * the char value).
 */
private fun codePointAt(seq: String, index: Int): Int {
    val c = seq[index]
    return if (c.isHighSurrogate() && index + 1 < seq.length && seq[index + 1].isLowSurrogate()) {
        ((c.code and 0x03FF) shl 10) + (seq[index + 1].code and 0x03FF) + 0x10000
    } else {
        c.code
    }
}

/** `String(Character.toChars(codePoint))` verbatim: BMP → 1 char, supplementary → surrogate pair. */
private fun codePointToString(codePoint: Int): String =
    if (codePoint < 0x10000) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - 0x10000
        charArrayOf(((offset ushr 10) + 0xD800).toChar(), ((offset and 0x3FF) + 0xDC00).toChar()).concatToString()
    }
