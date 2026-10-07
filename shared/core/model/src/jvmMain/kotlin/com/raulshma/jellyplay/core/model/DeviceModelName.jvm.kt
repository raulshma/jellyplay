package com.raulshma.jellyplay.core.model

/**
 * Desktop has no honest "model" string the JVM can read — the dashboard's
 * model column stays empty for desktop rows rather than inventing one.
 */
actual val deviceModelName: String? = null
