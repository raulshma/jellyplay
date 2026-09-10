package com.raulshma.jellyplay.navigation

/**
 * Pure parse of the external-player ActivityResult's position extras into
 * 100-ns Jellyfin ticks (extracted verbatim from JellyPlayApp's launcher
 * callback so the alias/coercion/gate rules are JVM-testable). The caller
 * reads the two extras off the result [android.content.Intent] and feeds the
 * raw values in — no Android types cross this file.
 *
 * Contract (the former inline fold):
 *  - `"position"` wins; when it is absent (or present-but-null — the same
 *    elvis the inline `extras?.get("position") ?: extras?.get("positionMs")`
 *    performed) `"positionMs"` is consulted — foreign players use either key;
 *  - the value must be a [Number] (Int/Long/Float/… — coerced via
 *    `Number.toLong()`, so fractional values truncate); anything else parses
 *    as "no position";
 *  - negative positions are rejected (`>= 0` gate — 0 is a legitimate
 *    "start from the beginning" credit);
 *  - milliseconds convert to ticks ×10 000.
 *
 * @return the resume position in ticks, or `-1L` when no valid position was
 *   reported (the caller forwards -1 and Continue Watching keeps its old
 *   position).
 */
internal fun externalPlayerPositionTicks(position: Any?, positionMs: Any?): Long {
    val ms = when (val pos = position ?: positionMs) {
        is Number -> pos.toLong()
        else -> -1L
    }
    return ms.takeIf { it >= 0 }?.let { it * 10_000 } ?: -1L
}
