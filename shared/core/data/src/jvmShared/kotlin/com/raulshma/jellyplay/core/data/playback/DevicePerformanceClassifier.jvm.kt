package com.raulshma.jellyplay.core.data.playback

import java.security.MessageDigest

/**
 * JVM (Android + desktop) actual for the classifier's fingerprint hash — the
 * same `java.security.MessageDigest` SHA-256 the module's other fingerprints
 * use (OfflineSyncComparator.sha256Hex).
 */
internal actual fun sha256Hex(input: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(input.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
