package com.raulshma.jellyplay.feature.book.epub

import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection

/**
 * Typed view of the JS↔native event channel (`reader.js` → host). The events
 * are self-authored JSON of a fixed shape (`{"type":…,"value":…}`), so this
 * parser is hand-rolled — this module has no kotlinx-serialization compile
 * dependency of its own. Any input that does not decode into a known event is
 * dropped, never thrown. Raw strings decode to domain types here at the
 * boundary; nothing downstream compares wire tokens.
 */
internal sealed interface EpubEvent {
    /** Relocation percent, 0.0..1.0. */
    data class Percent(val value: Double) : EpubEvent

    /** `loading` | `locations` | `locationsReady` | `ready` | `error` — see [EpubReaderStatus]. */
    data class Status(val status: EpubReaderStatus) : EpubEvent

    /** `ltr` | `rtl` as reported by the book's package metadata. */
    data class Direction(val direction: ReadingDirection) : EpubEvent

    /** Flattened table of contents (JS side already flattened subitems). */
    data class Toc(val items: List<EpubTocItem>) : EpubEvent
}

internal data class EpubTocItem(val label: String, val href: String)

internal object EpubEventParser {

    /**
     * Parses a bridge payload into events. Accepts a single event object, an
     * array of them, or a JSON-quoted string wrapping either (what WebView
     * `evaluateJavascript` callbacks receive on desktop). Malformed input →
     * empty list.
     */
    fun parse(raw: String?): List<EpubEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        val value = MiniJson.parse(raw) ?: return emptyList()
        return when (value) {
            is String -> parse(value)
            is List<*> -> value.mapNotNull { event(it) }
            else -> event(value)?.let(::listOf) ?: emptyList()
        }
    }

    private fun event(value: Any?): EpubEvent? {
        val map = value as? Map<*, *> ?: return null
        return when (map["type"]) {
            "percent" -> (map["value"] as? Number)
                ?.let { EpubEvent.Percent(it.toDouble().coerceIn(0.0, 1.0)) }
            "status" -> (map["value"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { EpubEvent.Status(it.toEpubStatus()) }
            "direction" -> (map["value"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { EpubEvent.Direction(it.toReadingDirection()) }
            "toc" -> EpubEvent.Toc(tocItems(map["value"]))
            else -> null
        }
    }

    private fun tocItems(value: Any?): List<EpubTocItem> =
        (value as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val href = (map["href"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EpubTocItem(label = (map["label"] as? String).orEmpty(), href = href)
        } ?: emptyList()
}

/**
 * `evaluateJavascript` on desktop may hand back the payload as a quoted JSON
 * string; the dispatcher unwraps that form before parsing.
 */
internal fun dispatchEpubEvents(raw: String?, callbacks: EpubReaderCallbacks) {
    for (event in EpubEventParser.parse(raw)) {
        when (event) {
            is EpubEvent.Percent -> callbacks.onPercentChanged(event.value)
            is EpubEvent.Status -> callbacks.onStatusChanged(event.status)
            is EpubEvent.Direction -> callbacks.onDirectionReported(event.direction)
            is EpubEvent.Toc -> callbacks.onTocReady(event.items)
        }
    }
}

private fun String.toEpubStatus(): EpubReaderStatus = when (this) {
    // `locations` = epub.js started generating locations; the book is still
    // booting, so it folds into LOADING like the initial `loading`.
    "locations" -> EpubReaderStatus.LOADING
    "locationsReady" -> EpubReaderStatus.LOCATIONS_READY
    "ready" -> EpubReaderStatus.READY
    "error" -> EpubReaderStatus.ERROR
    else -> EpubReaderStatus.LOADING
}

private fun String.toReadingDirection(): ReadingDirection =
    if (equals("rtl", ignoreCase = true)) ReadingDirection.RTL else ReadingDirection.LTR

/** Minimal strict JSON reader (objects/arrays/strings/numbers/booleans/null). */
internal object MiniJson {

    fun parse(text: String): Any? = runCatching {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.expectEnd()
        value
    }.getOrNull()

    private class Parser(private val text: String) {
        private var pos = 0

        fun expectEnd() {
            skipWhitespace()
            if (pos != text.length) fail("trailing content")
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) fail("eof")
            return when (text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("literal")
            pos += word.length
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // {
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (takeIfPresent('}')) return result
            while (true) {
                skipWhitespace()
                if (pos >= text.length || text[pos] != '"') fail("key")
                val key = parseString()
                skipWhitespace()
                if (pos >= text.length || text[pos] != ':') fail("colon")
                pos++
                result[key] = parseValue()
                skipWhitespace()
                when {
                    takeIfPresent(',') -> Unit
                    takeIfPresent('}') -> return result
                    else -> fail("object")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // [
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (takeIfPresent(']')) return result
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                when {
                    takeIfPresent(',') -> Unit
                    takeIfPresent(']') -> return result
                    else -> fail("array")
                }
            }
        }

        private fun parseString(): String {
            pos++ // opening quote
            val builder = StringBuilder()
            while (pos < text.length) {
                when (val c = text[pos++]) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(escape())
                    else -> builder.append(c)
                }
            }
            fail("unterminated string")
        }

        private fun escape(): Char {
            if (pos >= text.length) fail("eof escape")
            return when (val e = text[pos++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (pos + 4 > text.length) fail("unicode escape")
                    val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("unicode escape")
                    pos += 4
                    code.toChar()
                }
                else -> fail("escape")
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            if (pos < text.length && (text[pos] == '-' || text[pos] == '+')) pos++
            while (pos < text.length) {
                val c = text[pos]
                if (c.isDigit() || c == '.' || c == 'e' || c == 'E') {
                    pos++
                } else if ((c == '-' || c == '+') && pos > start && (text[pos - 1] == 'e' || text[pos - 1] == 'E')) {
                    pos++
                } else {
                    break
                }
            }
            return text.substring(start, pos).toDoubleOrNull() ?: fail("number")
        }

        private fun takeIfPresent(c: Char): Boolean {
            if (pos < text.length && text[pos] == c) {
                pos++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun fail(why: String): Nothing = throw IllegalArgumentException(why)
    }
}
