package com.raulshma.jellyplay.core.ui.components

/**
 * Case- and diacritics-tolerant matcher behind the picker sheets' search
 * fields (settings pickers, the shortcuts customize editor's target picker):
 * every whitespace-separated word of [query] must appear across [labels].
 * The normalization folds the Turkish dotted/dotless i and the common
 * Turkish/ Hungarian diacritics so "turk" finds "Türkçe" and "ingilizce"
 * finds "İngilizce" — the vocabulary a language/target picker must match.
 */
fun matchesPickerQuery(query: String, vararg labels: String): Boolean {
    val searchable = labels.joinToString(" ").normalizedPickerText()
    return query.normalizedPickerText().split(Regex("\\s+"))
        .all { it.isBlank() || searchable.contains(it) }
}

private fun String.normalizedPickerText(): String = lowercase()
    .replace("i\u0307", "i")
    .replace('ı', 'i').replace('ç', 'c').replace('ğ', 'g')
    .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
