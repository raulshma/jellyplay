package com.raulshma.jellyplay.core.model

/**
 * Compares two dotted numeric version strings (`"1.2.3"`, `"1.2"`, `"1.2.x"`),
 * with optional pre-release suffixes (`"1.2.3-alpha.1"`, `"1.2.3-rc"`).
 *
 * Returns a positive int if [v1] is newer, negative if older, `0` if equal.
 * Missing segments are treated as `0`, and non-numeric segments (e.g. a `"x"`
 * or suffix) also read as `0`, so `"1.2"` equals `"1.2.0"`.
 *
 * Pre-release ordering follows semver: any pre-release is OLDER than its
 * release (`"1.2.3-alpha.1" < "1.2.3"`), pre-release identifiers compare
 * numerically when both numeric (`alpha.2 > alpha.1`), lexically otherwise
 * (`beta > alpha`). KMP alpha tags (`v0.11.0-alpha.1`) flow straight from the
 * GitHub tag into this comparator via GitHubReleasesApiImpl, so a stable
 * `v0.11.0` published later must win over its own alphas — the plain dotted
 * walk read the `.1` suffix as an extra segment and flipped that order.
 *
 * Pure and side-effect free so it can be shared by the in-app update decision
 * logic, the GitHub release fetch, and the pending-APK check without any of
 * them depending on another module's implementation class.
 */
fun compareVersions(v1: String, v2: String): Int {
    val (core1, pre1) = v1.splitPreRelease()
    val (core2, pre2) = v2.splitPreRelease()

    val parts1 = core1.map { it.toIntOrNull() ?: 0 }
    val parts2 = core2.map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }

    // Equal numeric cores: a pre-release sorts below the plain release.
    if (pre1 == null && pre2 == null) return 0
    if (pre1 == null) return 1
    if (pre2 == null) return -1

    val ids1 = pre1.split('.')
    val ids2 = pre2.split('.')
    for (i in 0 until maxOf(ids1.size, ids2.size)) {
        val a = ids1.getOrNull(i)
        val b = ids2.getOrNull(i)
        if (a == b) continue
        // Shorter identifier list is the smaller pre-release (semver 11: the
        // larger set wins when all shared identifiers are equal).
        if (a == null) return -1
        if (b == null) return 1
        val na = a.toIntOrNull()
        val nb = b.toIntOrNull()
        if (na != null && nb != null) {
            if (na != nb) return na - nb
        } else if (na != null) {
            return -1 // numeric identifiers sort below alphanumeric ones
        } else if (nb != null) {
            return 1
        } else {
            val lexical = a.compareTo(b)
            if (lexical != 0) return lexical
        }
    }
    return 0
}

/** Splits `"1.2.3-alpha.1"` into `["1","2","3"]` to `"alpha.1"` (null suffix when none). */
private fun String.splitPreRelease(): Pair<List<String>, String?> {
    val dash = indexOf('-')
    return if (dash >= 0) {
        substring(0, dash).split(".") to substring(dash + 1)
    } else {
        split(".") to null
    }
}
