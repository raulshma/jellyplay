package com.raulshma.jellyplay.core.datastore

import java.util.UUID

/** JVM/Android actual: standard UUID v4 string, as persisted historically. */
internal actual fun randomUuidString(): String = UUID.randomUUID().toString()
