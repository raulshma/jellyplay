package com.raulshma.jellyplay.core.data.receiver

import android.content.Intent

/**
 * The read half of a broadcast's extras bundle, abstracted behind plain
 * accessors so an entry fold decodes values without touching
 * `android.content.Intent` — the seam that keeps the decode pure Kotlin and
 * JVM-testable without Robolectric (GLOSSARY "Receiver intake fold"). The
 * receiver adapts its [Intent] once per broadcast via [broadcastExtras].
 *
 * Deliberately read-only and per-key: a fold sees exactly the getters the
 * decoded verbs need, so an extras-shape change surfaces as a fold compile
 * error instead of a silent contract drift between producer and consumer.
 */
interface BroadcastExtras {
    fun string(key: String): String?
    fun stringArray(key: String): Array<String>?
    fun intArray(key: String): IntArray?
}

/**
 * Adapts an [Intent]'s extras bundle to [BroadcastExtras]. The delegation is
 * a one-liner per getter — the typed Intent getters stay the single source
 * of truth, so this never reimplements bundle semantics.
 */
fun Intent.broadcastExtras(): BroadcastExtras = object : BroadcastExtras {
    override fun string(key: String): String? = getStringExtra(key)
    override fun stringArray(key: String): Array<String>? = getStringArrayExtra(key)
    override fun intArray(key: String): IntArray? = getIntArrayExtra(key)
}
