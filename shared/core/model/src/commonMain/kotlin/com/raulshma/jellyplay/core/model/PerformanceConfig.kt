package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The master UI performance level: ONE dial over the app's decorative-effect
 * budget that every granular option ([PerformanceOverride] rows plus the
 * [ImageDecodeCap]) defaults from. The pure fold combining level + overrides
 * is [PerformanceConfigResolver]; its output is [ResolvedPerformanceConfig].
 *
 * The levels anchor the app's historical behavior so existing installs keep
 * their semantics:
 *  - [STANDARD] — the historical "performance mode **OFF**": full effects
 *    (BlurHash placeholders, image crossfade, quality badges, ambient hero
 *    video, backdrop blur, screensaver motion, shared elements, animated nav)
 *    and NO image decode clamp (requested bounds pass through).
 *  - [PERFORMANCE] — the historical "performance mode **ON**": everything
 *    suppressed (animations off, placeholders/crossfade/badges/ambient/blur/
 *    screensaver/shared elements off, instant nav transitions) plus the
 *    historical tiered decode clamp (`largest >= 1080` requests decode at
 *    768 px, smaller ones at 256 px).
 *  - [BALANCED] — the new middle tier: animations, quality badges, ambient
 *    hero video, backdrop blur, effects, screensaver motion and BlurHash
 *    placeholders stay **ON**; image crossfade goes **OFF**; nav transitions
 *    stay animated; no decode clamp (matches [STANDARD]).
 */
@Immutable
@Serializable
enum class PerformanceLevel {
    STANDARD,
    BALANCED,
    PERFORMANCE,
}

/**
 * The tri-state per-option override layered over a [PerformanceLevel]'s
 * defaults. [AUTO] follows the level's default (the level decides);
 * [ON]/[OFF] are the user's explicit choice and win over EVERY level default
 * — the fold never second-guesses them.
 */
@Immutable
@Serializable
enum class PerformanceOverride {
    AUTO,
    ON,
    OFF,
    ;

    /**
     * Folds this override over the enclosing level's [default]: [AUTO]
     * follows it, [ON] forces `true`, [OFF] forces `false`.
     */
    fun resolve(default: Boolean): Boolean = when (this) {
        AUTO -> default
        ON -> true
        OFF -> false
    }
}

/**
 * The explicit image decode-size cap for the image pipeline. [AUTO] follows
 * the active [PerformanceLevel]'s policy (see
 * [ResolvedPerformanceConfig.imageDecodeCapPx]); [UNLIMITED] removes every
 * clamp; [P1080]/[P720]/[P512]/[P256] hard-cap any request's decode at that
 * pixel height regardless of the level. Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class ImageDecodeCap {
    AUTO,
    UNLIMITED,
    P1080,
    P720,
    P512,
    P256,
}

/**
 * The user's per-option tri-state choices riding on top of a
 * [PerformanceLevel] — one [PerformanceOverride] per boolean option plus one
 * [ImageDecodeCap] for decode. Every field defaults to [PerformanceOverride.AUTO]
 * so the zero-argument value is "follow the level everywhere".
 */
@Immutable
@Serializable
data class PerformanceOverrides(
    val suppressAnimations: PerformanceOverride = PerformanceOverride.AUTO,
    val blurHashPlaceholders: PerformanceOverride = PerformanceOverride.AUTO,
    val imageCrossfade: PerformanceOverride = PerformanceOverride.AUTO,
    val qualityBadges: PerformanceOverride = PerformanceOverride.AUTO,
    val ambientHeroVideo: PerformanceOverride = PerformanceOverride.AUTO,
    val backdropBlur: PerformanceOverride = PerformanceOverride.AUTO,
    val screensaverMotion: PerformanceOverride = PerformanceOverride.AUTO,
    val sharedElements: PerformanceOverride = PerformanceOverride.AUTO,
    val navTransitionsInstant: PerformanceOverride = PerformanceOverride.AUTO,
    val imageDecodeCap: ImageDecodeCap = ImageDecodeCap.AUTO,
)

/**
 * The performance posture clump, declared exactly once: the master
 * [PerformanceLevel] plus the ten granular tri-state rows, under the field
 * names every carrier already uses. The preference aggregates that surface
 * the clump implement this interface (`AppearanceSlice`,
 * `MainPreferences`, `SettingsScreenPreferences`,
 * `OnboardingPreferences` — their own persisted `val`s satisfy it), so
 * adding a granular row means adding the property here and every carrier
 * fails to compile until it carries it — the field list cannot drift apart
 * the way hand-copied lists do.
 *
 * The [PerformanceOverrides] fold over any carrier has one home below
 * ([performanceOverrides]); call sites must not re-map the fields by hand
 * (the `AppearanceTheme` pattern: one declaration, every lane derives).
 */
interface PerformancePosture {
    val performanceLevel: PerformanceLevel
    val perfAnimations: PerformanceOverride
    val perfSharedElements: PerformanceOverride
    val perfNavTransitions: PerformanceOverride
    val perfImageDecodeCap: ImageDecodeCap
    val perfImageCrossfade: PerformanceOverride
    val perfBlurhash: PerformanceOverride
    val perfQualityBadges: PerformanceOverride
    val perfAmbientHeroVideo: PerformanceOverride
    val perfBackdropBlur: PerformanceOverride
    val perfScreensaverMotion: PerformanceOverride
}

/**
 * The ONE fold from a posture carrier's granular rows to the
 * [PerformanceOverrides] input [PerformanceConfigResolver.resolve] takes —
 * the carriers' `perf*` names mapped onto the overrides bundle's slots.
 * `performanceLevel` and the reduce-motion flag are resolver arguments, not
 * overrides, so they stay at the call sites.
 */
fun PerformancePosture.performanceOverrides(): PerformanceOverrides = PerformanceOverrides(
    suppressAnimations = perfAnimations,
    sharedElements = perfSharedElements,
    navTransitionsInstant = perfNavTransitions,
    imageDecodeCap = perfImageDecodeCap,
    imageCrossfade = perfImageCrossfade,
    blurHashPlaceholders = perfBlurhash,
    qualityBadges = perfQualityBadges,
    ambientHeroVideo = perfAmbientHeroVideo,
    backdropBlur = perfBackdropBlur,
    screensaverMotion = perfScreensaverMotion,
)

/**
 * The fully folded performance posture: what the UI should actually do. Pure
 * derived data — never persisted, never a preference — produced only by
 * [PerformanceConfigResolver.resolve] from a [PerformanceLevel], the
 * reduce-motion accessibility flag and a [PerformanceOverrides].
 *
 * Compatibility contract: the all-[PerformanceOverride.AUTO] folds pin the
 * app's historical single `performanceMode` boolean exactly —
 * [ResolvedPerformanceConfig.DEFAULT] (STANDARD, reduce-motion off) is today's
 * performance-mode-OFF posture, and PERFORMANCE is today's
 * performance-mode-ON posture (everything suppressed, [reducedMotion] forced
 * true, decode collapsed to the aggressive flat cap).
 */
@Immutable
data class ResolvedPerformanceConfig(
    /** The level's (or override's) animation-suppression choice. */
    val suppressAnimations: Boolean,
    /**
     * The unified reduce-motion flag — the historical
     * `performanceMode || reduceMotion` fold
     * (`core/designsystem`'s `LocalReducedMotion`): `true` when [suppressAnimations]
     * is on OR the user enabled the reduce-motion accessibility setting, so a
     * single guard honors both.
     */
    val reducedMotion: Boolean,
    val blurHashPlaceholders: Boolean,
    val imageCrossfade: Boolean,
    val qualityBadges: Boolean,
    val ambientHeroVideo: Boolean,
    val backdropBlur: Boolean,
    val screensaverMotion: Boolean,
    val sharedElements: Boolean,
    val navTransitionsInstant: Boolean,
    /**
     * The image decode cap in px: `null` = no clamp; non-null =
     * the decode size for requests at/above the image pipeline's
     * "full-bleed" threshold. When [decodeSmallRequestFloorPx] is also
     * non-null this is the two-tier policy's large-request tier (the
     * PERFORMANCE-AUTO fold reproducing the historical performance-mode
     * clamp, `largest >= 1080 -> 768`); when it is `null` this is a FLAT cap
     * on any request (an explicit [ImageDecodeCap] height).
     */
    val imageDecodeCapPx: Int?,
    /**
     * The small-request tier of the two-tier decode policy: non-null = the
     * fixed decode size for requests below the image pipeline's full-bleed
     * threshold (historically `largest < 1080 -> 256`), letting the image
     * pipeline keep ONE definition of the tiered clamp. `null` = no
     * small-request tier — [imageDecodeCapPx] applies flat (or not at all).
     */
    val decodeSmallRequestFloorPx: Int?,
) {
    companion object {
        /**
         * All-[PerformanceOverride.AUTO] + [PerformanceLevel.STANDARD] +
         * reduce-motion off — today's performance-mode-OFF posture. The value
         * a fresh install (or a user who never touches the new rows) resolves
         * to.
         */
        val DEFAULT: ResolvedPerformanceConfig = PerformanceConfigResolver.resolve(
            level = PerformanceLevel.STANDARD,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(),
        )
    }
}

/**
 * The ONE pure fold from ([PerformanceLevel], reduce-motion,
 * [PerformanceOverrides]) to [ResolvedPerformanceConfig] — the settings rows
 * and the UI call sites must never re-implement it. Side-effect free: same
 * inputs, same posture.
 *
 * Precedence: an explicit [PerformanceOverride.ON]/[OFF] beats every level
 * default; [PerformanceOverride.AUTO] takes the level's default; reduce-motion
 * ORs into [ResolvedPerformanceConfig.reducedMotion] on top of everything.
 */
object PerformanceConfigResolver {

    /**
     * Folds the performance posture for [level] over [reduceMotionEnabled]
     * and [overrides].
     */
    fun resolve(
        level: PerformanceLevel,
        reduceMotionEnabled: Boolean,
        overrides: PerformanceOverrides,
    ): ResolvedPerformanceConfig {
        val aggressive = level == PerformanceLevel.PERFORMANCE
        val suppressAnimations = overrides.suppressAnimations.resolve(default = aggressive)
        val decode = resolveDecodeCap(overrides.imageDecodeCap, level)
        return ResolvedPerformanceConfig(
            suppressAnimations = suppressAnimations,
            reducedMotion = suppressAnimations || reduceMotionEnabled,
            // Placeholders are a loading affordance, not a decoration: only
            // the aggressive tier drops them. Crossfade IS a decoration and
            // drops on the middle tier.
            blurHashPlaceholders = overrides.blurHashPlaceholders
                .resolve(default = !aggressive),
            imageCrossfade = overrides.imageCrossfade
                .resolve(default = level == PerformanceLevel.STANDARD),
            qualityBadges = overrides.qualityBadges.resolve(default = !aggressive),
            ambientHeroVideo = overrides.ambientHeroVideo.resolve(default = !aggressive),
            backdropBlur = overrides.backdropBlur.resolve(default = !aggressive),
            screensaverMotion = overrides.screensaverMotion.resolve(default = !aggressive),
            sharedElements = overrides.sharedElements.resolve(default = !aggressive),
            navTransitionsInstant = overrides.navTransitionsInstant.resolve(default = aggressive),
            imageDecodeCapPx = decode.capPx,
            decodeSmallRequestFloorPx = decode.smallRequestFloorPx,
        )
    }

    /** The decoded decode-cap pair: [DecodeCap.capPx] + [DecodeCap.smallRequestFloorPx]. */
    private data class DecodeCap(val capPx: Int?, val smallRequestFloorPx: Int?)

    /**
     * The decode-policy fold: [ImageDecodeCap.AUTO] follows the level —
     * STANDARD/BALANCED carry today's performance-mode-OFF decode posture
     * (NO clamp: requested bounds pass through), PERFORMANCE carries the
     * historical performance-mode-ON tiered clamp (`largest >= 1080` decodes
     * at 768 px, smaller requests at 256 px — the pair the image pipeline
     * applies as its two-tier clamp). Explicit caps replace the level policy
     * wholesale with a floorless flat cap; UNLIMITED removes every clamp.
     */
    private fun resolveDecodeCap(override: ImageDecodeCap, level: PerformanceLevel): DecodeCap =
        when (override) {
            ImageDecodeCap.AUTO -> when (level) {
                PerformanceLevel.STANDARD,
                PerformanceLevel.BALANCED,
                -> DecodeCap(capPx = null, smallRequestFloorPx = null)
                PerformanceLevel.PERFORMANCE -> DecodeCap(
                    capPx = DECODE_FULL_BLEED_TIER_PX,
                    smallRequestFloorPx = DECODE_FULL_BLEED_FLOOR_PX,
                )
            }
            ImageDecodeCap.UNLIMITED -> DecodeCap(capPx = null, smallRequestFloorPx = null)
            ImageDecodeCap.P1080 -> DecodeCap(capPx = 1080, smallRequestFloorPx = null)
            ImageDecodeCap.P720 -> DecodeCap(capPx = 720, smallRequestFloorPx = null)
            ImageDecodeCap.P512 -> DecodeCap(capPx = 512, smallRequestFloorPx = null)
            ImageDecodeCap.P256 -> DecodeCap(capPx = 256, smallRequestFloorPx = null)
        }
}

/**
 * The tiered decode clamp's edges, named once beside the fold that emits them
 * ([PerformanceConfigResolver.resolveDecodeCap]): the historical
 * performance-mode-ON policy is "`largest` at/above [DECODE_FULL_BLEED_THRESHOLD_PX]
 * decodes at [DECODE_FULL_BLEED_TIER_PX] px, smaller requests at
 * [DECODE_FULL_BLEED_FLOOR_PX] px". The image pipeline (core:ui's
 * `mediaImageDecodeSize`) applies the same two tiers against the SAME
 * constants instead of mirroring the literals, so the pair can't drift.
 */
const val DECODE_FULL_BLEED_THRESHOLD_PX = 1080

/** The tiered clamp's large-request decode size (see [DECODE_FULL_BLEED_THRESHOLD_PX]). */
const val DECODE_FULL_BLEED_TIER_PX = 768

/** The tiered clamp's small-request decode size (see [DECODE_FULL_BLEED_THRESHOLD_PX]). */
const val DECODE_FULL_BLEED_FLOOR_PX = 256
