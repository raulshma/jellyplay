package com.raulshma.jellyplay.feature.settings

/**
 * The one visibility surface for platform-gated settings. Each flag answers
 * "can THIS binary's settings surface offer this row?" — a compile-time
 * platform fact (per the expect/actual seam below), so a hidden row is
 * structurally absent, not rendered-then-disabled.
 *
 * **Ownership rule (the seam split):** capabilities own VISIBILITY; the
 * behavior seams own BEHAVIOR. A flag must exist iff some row's visibility
 * depends on the platform; the [BiometricGate] / [LogCollector] /
 * [PlatformIntents] / [SettingsMessenger] seams keep doing the actual work.
 * Every flag mirrors a desktop seam's null-ness / query, and those
 * equalities are pinned beside the seam actuals in
 * `DesktopPlatformActualsTest` — one review home for the platform's truth.
 * Android offers the rows its platform APIs can back; `supportsBiometric`
 * is the one with device-level nuance — a device without biometric
 * hardware still nulls the runtime gate, and the screen requires the gate
 * before rendering or counting the row.
 *
 * Deliberately NOT here: TV vs phone (`LocalTvMode` is the runtime form-factor
 * axis and stays in core/ui — platform identity itself is core/model's
 * `currentPlatform`, not a field here), admin gating
 * (server state, not platform), advanced-mode gating (user preference), and
 * per-engine playback capabilities (`EngineCapabilityMatrix` in
 * player-contract) or shipped-engine availability
 * (`platformEngineSupport` in core/model — consumed directly, not duplicated).
 */
internal data class SettingsCapabilities(
    /** Wallpaper-derived (Material You) scheme; Android 12+ only. */
    val supportsDynamicColor: Boolean,
    /** Notification sync/worker backend exists (it is Android-only today). */
    val supportsNotifications: Boolean,
    /** Per-app locale override actually applies (`AppLocaleSetter` no-ops on desktop). */
    val supportsAppLocaleOverride: Boolean,
    /** An audio-cache exists on disk to clear. */
    val supportsAudioCache: Boolean,
    /** A screen orientation can be locked (meaningless on desktop). */
    val supportsScreenOrientation: Boolean,
    /** Touch gestures exist (double-tap seek, gesture indicator). */
    val supportsTouchGestures: Boolean,
    /**
     * Desktop: `rememberBiometricGate() != null` (always false there —
     * pinned in `DesktopPlatformActualsTest`). Android: the platform has
     * biometric APIs; a device without hardware hides the row at runtime
     * via the null gate (the seam owns that), so the flag never
     * over-promises.
     */
    val supportsBiometric: Boolean,
    /** Mirrors `PlatformIntents.canOpenSystemNotificationSettings()`. */
    val supportsSystemNotificationSettings: Boolean,
    /** Mirrors `LogCollector` returning collected logs (null on desktop). */
    val supportsLogSharing: Boolean,
)

/**
 * The running binary's settings capabilities, with one actual per platform
 * source set: `SettingsCapabilities.android.kt` (full surface; dynamic color
 * is the one runtime read — the OS level) and `SettingsCapabilities.jvm.kt`
 * (visibility flags false wherever the desktop seam no-ops).
 */
internal expect val settingsCapabilities: SettingsCapabilities
