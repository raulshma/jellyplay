package com.raulshma.jellyplay.core.model

/**
 * Compares two dotted numeric version strings (`"1.2.3"`, `"1.2"`, `"1.2.x"`).
 *
 * Returns a positive int if [v1] is newer, negative if older, `0` if equal.
 * Missing segments are treated as `0`, and non-numeric segments (e.g. a `"x"`
 * or suffix) also read as `0`, so `"1.2"` equals `"1.2.0"`.
 *
 * Pure and side-effect free so it can be shared by the in-app update decision
 * logic, the GitHub release fetch, and the pending-APK check without any of
 * them depending on another module's implementation class.
 */
fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }
    return 0
}
