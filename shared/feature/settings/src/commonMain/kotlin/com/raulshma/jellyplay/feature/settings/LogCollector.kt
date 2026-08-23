package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam behind [AboutViewModel.sendAppLogs] (V3 settings conveyor).
 * Collects the app's log output into a shareable file and returns an opaque
 * platform reference to it (the serialised content Uri on Android) for the
 * [PlatformIntents.shareLogFile] EXTRA_STREAM share; null means log
 * collection is unavailable on this platform (desktop), which gates the
 * share. The appVersion / buildType / serverAddress arguments feed the
 * fallback header the Android actual writes when logcat capture fails.
 */
fun interface LogCollector {
    fun collectLogs(appVersion: String, buildType: String, serverAddress: String?): String?
}
