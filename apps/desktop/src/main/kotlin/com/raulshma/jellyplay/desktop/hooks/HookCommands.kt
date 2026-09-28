package com.raulshma.jellyplay.desktop.hooks

import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice

/**
 * The playback-event shell hooks' pure command layer (feature 4.3): which
 * configured command an event maps to, the `{title}`/`{itemId}`/
 * `{position_ms}` placeholder substitution, and the shell-style command-line
 * tokenization (single/double-quoted arguments kept together, backslash
 * escaping) that feeds `ProcessBuilder`. Everything here is pure — the truth
 * table is pinned by `HookCommandsTest`, no process is ever started.
 */
internal object HookCommands {

    /** The five mpv-shim-named hook events (the settings names, for familiarity). */
    enum class HookEvent {
        PLAY_STARTED,
        PLAY_STOPPED,
        MEDIA_ENDED,
        IDLE_ENTER,
        IDLE_EXIT,
    }

    /** The placeholder context: the event's now-playing snapshot, blanks when it carries none. */
    data class HookContext(
        val title: String,
        val itemId: String,
        val positionMs: Long,
    )

    /** The five configured commands, verbatim from the settings slice. */
    data class HookConfig(
        val playCmd: String,
        val stopCmd: String,
        val endedCmd: String,
        val idleCmd: String,
        val idleEndedCmd: String,
    ) {
        companion object {
            /** The one slice → config conversion, so the field list lives once. */
            fun from(slice: ScreensaverSlice): HookConfig =
                HookConfig(
                    playCmd = slice.hooksPlayCmd,
                    stopCmd = slice.hooksStopCmd,
                    endedCmd = slice.hooksEndedCmd,
                    idleCmd = slice.hooksIdleCmd,
                    idleEndedCmd = slice.hooksIdleEndedCmd,
                )
        }
    }

    /**
     * A resolved, enabled hook: which [HookEvent] fired, its configured
     * command, and the substitution context for it.
     */
    data class ResolvedHook(
        val event: HookEvent,
        val command: String,
        val context: HookContext,
    )

    /**
     * The event → hook mapping truth table:
     *  - `Started` → play, `Stopped` → stop, `Ended` → ended,
     *    `IdleEntered` → idle, `IdleLeft` → idle-exit;
     *  - the context rides the event's meta (meta events) or the blank
     *    context (the idle transitions carry no item);
     *  - null when the event type has no configured (non-blank) command —
     *    an empty command IS the disabled state.
     */
    fun resolve(event: NowPlayingReporter.NowPlayingEvent, config: HookConfig): ResolvedHook? {
        val resolved = when (event) {
            is NowPlayingReporter.NowPlayingEvent.Started -> ResolvedHook(
                HookEvent.PLAY_STARTED,
                config.playCmd,
                event.meta.context(),
            )
            is NowPlayingReporter.NowPlayingEvent.Stopped -> ResolvedHook(
                HookEvent.PLAY_STOPPED,
                config.stopCmd,
                event.meta.context(),
            )
            is NowPlayingReporter.NowPlayingEvent.Ended -> ResolvedHook(
                HookEvent.MEDIA_ENDED,
                config.endedCmd,
                event.meta.context(),
            )
            NowPlayingReporter.NowPlayingEvent.IdleEntered -> ResolvedHook(
                HookEvent.IDLE_ENTER,
                config.idleCmd,
                BLANK_CONTEXT,
            )
            NowPlayingReporter.NowPlayingEvent.IdleLeft -> ResolvedHook(
                HookEvent.IDLE_EXIT,
                config.idleEndedCmd,
                BLANK_CONTEXT,
            )
        }
        if (resolved.command.isBlank()) return null
        return resolved
    }

    /**
     * Substitutes the placeholders in [command]: `{title}`, `{itemId}`,
     * `{position_ms}` (unknown placeholders pass through untouched — a
     * user's literal braces are their own business).
     */
    fun substitute(command: String, context: HookContext): String = command
        .replace("{title}", context.title)
        .replace("{itemId}", context.itemId)
        .replace("{position_ms}", context.positionMs.toString())

    /**
     * Splits a command line into argv the way a shell's word-splitting
     * would: unquoted runs split on whitespace, single- and double-quoted
     * runs stay one argument, `\` escapes the next character everywhere
     * (a literal quote, a literal backslash, or whitespace inside a quoted
     * run). An unterminated quote keeps the run to the end of input —
     * fail-open, never throws.
     */
    fun tokenize(command: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var hasArg = false
        var index = 0
        while (index < command.length) {
            val c = command[index]
            when {
                c == '\\' && index + 1 < command.length -> {
                    current.append(command[index + 1])
                    index += 2
                    hasArg = true
                }
                c == '\'' || c == '\"' -> {
                    val closing = command.indexOf(c, index + 1)
                    if (closing < 0) {
                        // Unterminated quote: the rest of the line is the run.
                        current.append(command.substring(index + 1))
                        hasArg = true
                        index = command.length
                    } else {
                        current.append(command.substring(index + 1, closing))
                        index = closing + 1
                        hasArg = true
                    }
                }
                c.isWhitespace() -> {
                    if (hasArg) {
                        args.add(current.toString())
                        current.setLength(0)
                        hasArg = false
                    }
                    index += 1
                }
                else -> {
                    current.append(c)
                    index += 1
                    hasArg = true
                }
            }
        }
        if (hasArg) args.add(current.toString())
        return args
    }

    private fun NowPlayingReporter.NowPlayingMeta.context() = HookContext(
        title = title,
        itemId = itemId,
        positionMs = positionMs,
    )

    private val BLANK_CONTEXT = HookContext(title = "", itemId = "", positionMs = 0L)
}
