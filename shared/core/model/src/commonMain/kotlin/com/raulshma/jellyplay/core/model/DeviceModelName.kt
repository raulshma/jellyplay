package com.raulshma.jellyplay.core.model

/**
 * The human-readable hardware model of the running device — "Nokia 6.1 Plus"
 * on Android, null where the platform has no honest model string (desktop).
 * Feeds the plugin registry's self-reported `model` (the dashboard's device
 * rows) and the default device NAME on Android, where "Android device" told
 * the user nothing.
 */
expect val deviceModelName: String?
