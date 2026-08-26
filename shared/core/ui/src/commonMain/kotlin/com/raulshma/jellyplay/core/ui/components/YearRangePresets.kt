package com.raulshma.jellyplay.core.ui.components

/**
 * A decade (or larger) preset for year filtering. The [id] is stable enough
 * to round-trip through saved-state storage ("YYYY-YYYY"); [years] is the
 * fully expanded year set the preset represents.
 */
data class YearRangePreset(val id: String, val label: String, val years: IntRange) {
    companion object {
        const val ANY_ID: String = ""
    }
}

/**
 * Returns the standard preset catalog. The final preset is anchored to the
 * current year so "2020s" always extends to "now" rather than a frozen 2029.
 * The default `now` resolves through the [currentYear] platform seam (the
 * pre-wasm body read `Calendar.YEAR` directly).
 */
fun yearRangePresets(
    now: Int = currentYear(),
): List<YearRangePreset> = buildList {
    add(YearRangePreset("1920-1949", "1920s–40s", 1920..1949))
    for (decadeStart in 1950..2020 step 10) {
        val decadeEnd = if (decadeStart == 2020) now else decadeStart + 9
        val label = if (decadeStart == 2020) "2020s–now" else "${decadeStart}s"
        add(YearRangePreset("$decadeStart-$decadeEnd", label, decadeStart..decadeEnd))
    }
}

/**
 * Parses a "YYYY-YYYY" preset id into the underlying year list. Returns an
 * empty list for blank/invalid ids (which represents "Any").
 */
fun parseYearRangePreset(id: String): List<Int> {
    if (id.isBlank()) return emptyList()
    val parts = id.split("-")
    if (parts.size != 2) return emptyList()
    val start = parts[0].toIntOrNull() ?: return emptyList()
    val end = parts[1].toIntOrNull() ?: return emptyList()
    if (end < start) return emptyList()
    return (start..end).toList()
}

/**
 * Finds the preset id whose year set exactly equals [years]. Returns
 * [YearRangePreset.ANY_ID] when no preset matches — callers should preserve
 * the supplied [years] separately so custom selections survive round-trips
 * through persistence.
 */
fun findYearRangePresetId(
    years: Collection<Int>,
    presets: List<YearRangePreset> = yearRangePresets(),
): String {
    if (years.isEmpty()) return YearRangePreset.ANY_ID
    val set = years.toSet()
    return presets.firstOrNull { preset -> preset.years.toSet() == set }?.id
        ?: YearRangePreset.ANY_ID
}

/**
 * Selection state for a decade chip relative to the current year set.
 * Used to render full / partial / unselected visuals consistently across
 * the library and search filter sheets.
 */
enum class YearPresetSelection { Unselected, Partial, Full }

/**
 * Returns the selection state of [preset] against [years].
 */
fun yearPresetSelection(
    preset: YearRangePreset,
    years: Collection<Int>,
): YearPresetSelection {
    val set = years.toSet()
    val matched = preset.years.count { it in set }
    return when {
        matched == preset.years.count() -> YearPresetSelection.Full
        matched == 0 -> YearPresetSelection.Unselected
        else -> YearPresetSelection.Partial
    }
}

/**
 * Toggles a preset's year set against [years]: if the preset is fully
 * represented, removes its years; otherwise adds them (preserving any
 * existing individual selections).
 */
fun toggleYearPreset(
    preset: YearRangePreset,
    years: Collection<Int>,
): Set<Int> {
    val current = years.toMutableSet()
    return if (yearPresetSelection(preset, current) == YearPresetSelection.Full) {
        current.removeAll(preset.years.toSet())
        current
    } else {
        current.addAll(preset.years.toSet())
        current
    }
}
