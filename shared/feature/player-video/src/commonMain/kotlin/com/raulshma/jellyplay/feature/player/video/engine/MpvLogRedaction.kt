package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The ONE mpv log-redaction fold: every spelling auth can take in an mpv log
 * line — the `ApiKey` query param plus its legacy lowercase `api_key` alias
 * and the percent-encoded forms mpv prints, the legacy `X-Emby-Token`
 * header, and the `Authorization: MediaBrowser Token="…"` header media
 * fetches now send. Lives in commonMain (pure string fold, no android
 * dependencies) so the regex set is pinned by
 * `MpvLogRedactionTest` beside it; [MpvPlayerEngine] delegates its
 * `redactSensitive` here.
 */
internal object MpvLogRedaction {

    private val REDACT_API_KEY = Regex("(?i)(api[_-]?key=)[^&\\s]+")
    private val REDACT_API_KEY_ENCODED = Regex("(?i)(api[_-]?key%3D)[^&\\s]+")
    private val REDACT_EMBY_TOKEN = Regex("(?i)(X-Emby-Token:\\s*)[^,\\s]+")
    private val REDACT_AUTHORIZATION = Regex("""(?i)(Authorization:\s*MediaBrowser\s+Token=")[^"]*(")""")

    /** Redacts every auth spelling from one mpv log line. */
    fun redact(value: String): String =
        value
            .replace(REDACT_API_KEY, "$1***")
            .replace(REDACT_API_KEY_ENCODED, "$1***")
            .replace(REDACT_EMBY_TOKEN, "$1***")
            .replace(REDACT_AUTHORIZATION, "$1***$2")
}
