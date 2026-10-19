package com.raulshma.jellyplay.feature.admin.logs

/**
 * The log-file line parse, extracted from [LogsScreen]'s file-private
 * `parseLogLine`: the severity find + span split is decode logic that was
 * Compose-typed ([AnnotatedString]) and therefore JVM-unreachable, though it
 * is pure. The decision lives here in plain types; the screen's composable
 * maps [LogLineRole] → color trivially and draws.
 *
 * The regex matches a whole-word severity token anywhere in the line (the
 * alternation order makes `WARN` yield to `WARNING` — the `WARN` arm fails
 * its trailing word boundary inside the longer word); the token keeps its
 * original casing in the span text.
 */
internal val LOG_LEVEL_REGEX = Regex("\\b(ERROR|FATAL|WARN|WARNING|INFO|DEBUG|TRACE)\\b", RegexOption.IGNORE_CASE)

/**
 * The severity roles the parsed token folds onto — the four-tint ladder the
 * screen renders (ERROR/FATAL share the error tint, DEBUG/TRACE the debug
 * tint, WARN and WARNING the warning tint, INFO its own).
 */
internal enum class LogLineRole {
    ERROR,
    WARNING,
    INFO,
    DEBUG,
}

/**
 * One contiguous span of a parsed log line: [text] verbatim, [role] the
 * severity the span's token folded onto (null = unhighlighted filler), and
 * [bold] whether the span renders bold (only severity tokens do).
 */
internal data class LogLineSpan(
    val text: String,
    val role: LogLineRole?,
    val bold: Boolean,
)

/**
 * Parses [line] into its spans: the text before the severity token, the
 * token itself (role-mapped, bold), and the text after it — or the whole
 * line as one plain span when no severity token appears.
 */
internal fun parseLogLineSpans(line: String): List<LogLineSpan> {
    val match = LOG_LEVEL_REGEX.find(line) ?: return listOf(LogLineSpan(line, role = null, bold = false))
    return listOf(
        LogLineSpan(line.substring(0, match.range.first), role = null, bold = false),
        LogLineSpan(match.value, roleOf(match.value), bold = true),
        LogLineSpan(line.substring(match.range.last + 1), role = null, bold = false),
    )
}

/** The token → role fold (case-insensitive — the regex already matched loosely). */
private fun roleOf(token: String): LogLineRole = when (token.uppercase()) {
    "ERROR", "FATAL" -> LogLineRole.ERROR
    "WARN", "WARNING" -> LogLineRole.WARNING
    "INFO" -> LogLineRole.INFO
    "DEBUG", "TRACE" -> LogLineRole.DEBUG
    else -> throw IllegalStateException("Unhandled log token: $token")
}
