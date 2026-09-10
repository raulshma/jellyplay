package com.raulshma.jellyplay.core.model

/**
 * The compile-time platform axis: which binary kind is running. Resolved by
 * the expect/actual [currentPlatform] per target, so every read is a constant
 * in that binary — never probed, never mutable at runtime.
 *
 * Deliberately coarse. Form factor (phone vs TV) is a RUNTIME property on
 * Android — it rides `LocalTvMode` in core/ui and must never be folded in
 * here: both shells ship from the same androidMain compilation, so no
 * compile-time constant can express it.
 */
enum class PlatformKind {
    ANDROID,
    DESKTOP,
    WEB,
}

/**
 * The [PlatformKind] of the running binary. One constant per target actual
 * (androidMain → ANDROID, jvmMain → DESKTOP, wasmJsMain → WEB); see
 * [PlatformKind] for why form factor must not join this axis.
 */
expect val currentPlatform: PlatformKind
