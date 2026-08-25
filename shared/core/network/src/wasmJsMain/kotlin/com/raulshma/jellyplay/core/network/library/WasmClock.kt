package com.raulshma.jellyplay.core.network.library

/**
 * Wall-clock reads for the wasm clients (`LocalDateTime.now()` has no wasm
 * equivalent). Only the two self-contained `js()` expressions touch the
 * platform — wasm requires each to be the single expression of a top-level
 * function — and the civil-date math + formatting live pure in
 * [isoLocalFromUtcMillis] so commonTest can pin them.
 *
 * `getTimezoneOffset()` follows the JS convention: minutes UTC is AHEAD of
 * local time (positive west of UTC) — the sign flip happens in the pure
 * formatter.
 */

/** Current epoch milliseconds (`Date.now()`). */
private fun jsNowMillis(): Double = js("Date.now()")

/** Local zone offset in JS convention minutes (west positive). */
private fun jsTimezoneOffsetMinutes(): Double = js("new Date().getTimezoneOffset()")

internal object WasmClock {

    /** Current epoch milliseconds. */
    fun nowMillis(): Long = jsNowMillis().toLong()

    /** Local zone offset in JS convention minutes (west positive). */
    fun timezoneOffsetMinutes(): Int = jsTimezoneOffsetMinutes().toInt()

    /**
     * `LocalDateTime.now().minusDays(days)` formatted as an ISO-8601
     * offset-date-time in the local zone — the wire form the SDK's
     * `nextUpDateCutoff` serializer produces on the JVM.
     */
    fun localNowMinusDaysIsoOffset(days: Long): String =
        isoLocalFromUtcMillis(nowMillis() - days * 86_400_000L, timezoneOffsetMinutes())
}
