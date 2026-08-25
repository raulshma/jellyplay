package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Web v1 cut (Phase W.3): the Kotlin stdlib ships no BCP-47 display-name
 * resolver and Intl.DisplayNames needs JS interop. Return null so track
 * labels degrade to the raw language code ("eng" instead of "English") —
 * consistent with the plan's web degrade posture; swap in an Intl-backed
 * actual if web labels need localization later.
 */
internal actual fun platformLanguageDisplayName(bcp47Tag: String): String? = null
