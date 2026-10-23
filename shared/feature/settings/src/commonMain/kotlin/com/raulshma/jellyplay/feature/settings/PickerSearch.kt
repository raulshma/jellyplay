package com.raulshma.jellyplay.feature.settings

/** Match every search word across the visible labels and language codes. */
internal fun matchesPickerQuery(query: String, vararg labels: String): Boolean {
    val searchable = labels.joinToString(" ").normalizedPickerText()
    return query.normalizedPickerText().split(Regex("\\s+"))
        .all { it.isBlank() || searchable.contains(it) }
}

private fun String.normalizedPickerText(): String = lowercase()
    .replace("i\u0307", "i")
    .replace('ı', 'i').replace('ç', 'c').replace('ğ', 'g')
    .replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
