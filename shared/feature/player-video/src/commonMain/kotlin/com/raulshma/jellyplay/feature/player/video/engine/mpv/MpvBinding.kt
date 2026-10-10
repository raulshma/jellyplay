package com.raulshma.jellyplay.feature.player.video.engine.mpv

/**
 * The platform seam of [MpvCore]: everything the shared mpv choreography
 * needs FROM the platform binding, expressed without leaking either
 * platform's handle shape (Android's `is.xyz.mpv` MPV JNI wrapper vs the
 * desktop's libmpv-over-JNA `Pointer`).
 *
 * Extracted from the two engines' actual usage — every member has two
 * shipped implementations' worth of precedent:
 *
 *  - **Writes** ride [MpvPropertySurface] (player-contract), the interface
 *    both engines already implement for the shared style/config appliers —
 *    implementations ABSORB their platform's failure contract (Android
 *    logs-and-swallow, desktop ignores the boolean result), so the shared
 *    choreography stays exception-free.
 *  - **Commands** ([command]) are normalized to a success boolean while the
 *    THROW-vs-RETURN failure split stays a transport detail: the Android JNI
 *    wrapper throws on a rejected command (the engine's shipped
 *    try/catch-and-log bodies), the desktop JNA transport returns `false`
 *    (never throws). [MpvCore] wraps every command call in a catch so both
 *    shapes land in the same [MpvCore.Hosts.onTransportError] seam.
 *  - **Reads** return the platforms' common "absent on failure" shapes:
 *    `Double?`/`String?` null on a failed read, [readFlag] folding the
 *    platforms' differing read-failure defaults at the binding (Android's
 *    shipped `?: true`, desktop's alive-context-else-`true` /
 *    failed-read-`false` — each binding encodes exactly what its engine
 *    used to return).
 *  - **Node reads** ([readNode]) return the plain Kotlin tree
 *    (`Map`/`List`/`String`/`Long`/`Double`/`Boolean`) both hosts already
 *    normalize to for the shared [MpvTrackCatalog] — the Android binding
 *    flattens its event-carried `MPVNode` (the engine's former private
 *    `asPlainValue`), the desktop returns `MpvLib.readNode`'s parse as-is.
 *  - **Track-id writes** ([writeIntOrString]) are the two engines' shared
 *    int-then-string-fallback discipline for `sid`/`aid`-shaped choice
 *    properties (the typed FORMAT_INT64 write first, the decimal-string
 *    form as the fallback — Android's catch, desktop's `if (!ok)`).
 *
 * Deliberately NOT on the seam: observer registration and the native event
 * pump (the platforms' callback plumbing is irreconcilable — an
 * `MPV.EventObserver` vs a `mpv_wait_event` thread — and is the adapter's
 * job: it TRANSLATES native callbacks into [MpvCore] calls), the render /
 * window setup, the process lifecycle, and every Android- or desktop-only
 * option write (those stay in the engines' init paths).
 *
 * Thread-safety mirrors the shipped engines: implementations may be called
 * from the platform's event surface (mpv's single event queue / the
 * `mpv-desktop-event-loop` thread) and the UI thread; the bindings route to
 * their native APIs which are the same surfaces the engines always used.
 */
public interface MpvBinding : MpvPropertySurface {

    /**
     * Whether the platform handle is live. The core's transport entry points
     * early-return on `false` — the shipped `mpvView?.mpv ?: return` /
     * `aliveCtx() ?: return` guards. Must stay cheap (it is checked per op).
     */
    public fun isAlive(): Boolean

    /**
     * Runs an mpv command (`seek` / `stop` / `sub-add` / `vf clr` / ...).
     * Returns `true` on success. Implementations either throw on failure
     * (Android-class transports) or return `false` (JNA-class) — callers
     * that care about the failure wrap the call and route the throwable
     * through their transport-error seam.
     */
    public fun command(vararg args: String): Boolean

    /**
     * The LIVE `MPV_FORMAT_FLAG` read (the `pause` re-derivations, the
     * FILE_LOADED seed). Failure defaults are the binding's own — see the
     * class KDoc.
     */
    public fun readFlag(name: String): Boolean

    /** Live `MPV_FORMAT_DOUBLE` read; null on failure / dead handle. */
    public fun readDouble(name: String): Double?

    /** Live `MPV_FORMAT_STRING` read; null on failure / dead handle. */
    public fun readString(name: String): String?

    /**
     * Live `MPV_FORMAT_NODE` read as the plain Kotlin tree; null on failure
     * / dead handle. `track-list` rows and the `demuxer-cache-state` map
     * arrive in exactly the shape [MpvTrackCatalog] and
     * [MpvCore.decodeBufferedRanges] parse.
     */
    public fun readNode(name: String): Any?

    /**
     * Typed INT write with the decimal-string fallback — the track-id
     * discipline both engines shipped for `sid`/`aid`: the int write first,
     * and when the transport rejects it the string form is written instead.
     * Returns whether the INT write landed (the caller does not branch on
     * the fallback).
     */
    public fun writeIntOrString(name: String, value: Int): Boolean
}
