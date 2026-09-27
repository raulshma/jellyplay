package com.raulshma.jellyplay.core.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the ContextWrapper chain to find the hosting Activity. Promoted from
 * the player-video module (now shared/feature/player-video) so all three
 * player screens — the VOD player, the live TV player and the book reader —
 * can use it.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
