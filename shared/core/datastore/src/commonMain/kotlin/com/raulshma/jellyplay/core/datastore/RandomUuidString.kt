package com.raulshma.jellyplay.core.datastore

/**
 * Random UUID v4 string for persisted identity values (device id). Format is
 * stable across platforms because it is persisted and compared as an opaque
 * string.
 */
internal expect fun randomUuidString(): String
