package com.raulshma.jellyplay.desktop.hooks

import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.desktop.hooks.HookCommands.HookConfig
import com.raulshma.jellyplay.desktop.hooks.HookCommands.HookContext
import com.raulshma.jellyplay.desktop.hooks.HookCommands.HookEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shell hooks' pure layer (feature 4.3): the event→hook mapping truth
 * table, the placeholder substitution, and the shell-style tokenization
 * (quoted arguments kept together). No process is ever started — the runner
 * consumes these results.
 */
class HookCommandsTest {

    private val meta = NowPlayingReporter.NowPlayingMeta(
        itemId = "item-9",
        title = "The Movie",
        subtitle = "S1E5",
        kind = NowPlayingReporter.Kind.VIDEO,
        positionMs = 65_432,
        durationMs = 100_000,
    )

    private val fullConfig = HookConfig(
        playCmd = "on-play {title}",
        stopCmd = "on-stop",
        endedCmd = "on-ended",
        idleCmd = "on-idle",
        idleEndedCmd = "on-idle-exit",
    )

    /** All five events, the meta events carrying [meta] — the exhaustive set. */
    private val allEvents = listOf(
        NowPlayingReporter.NowPlayingEvent.Started(meta),
        NowPlayingReporter.NowPlayingEvent.Stopped(meta),
        NowPlayingReporter.NowPlayingEvent.Ended(meta),
        NowPlayingReporter.NowPlayingEvent.IdleEntered,
        NowPlayingReporter.NowPlayingEvent.IdleLeft,
    )

    // ── event → hook mapping truth table ───────────────────────────────────

    @Test
    fun eachEventType_mapsToItsOwnHook() {
        val expectedHooks = listOf(
            HookEvent.PLAY_STARTED,
            HookEvent.PLAY_STOPPED,
            HookEvent.MEDIA_ENDED,
            HookEvent.IDLE_ENTER,
            HookEvent.IDLE_EXIT,
        )
        for ((event, expectedHook) in allEvents.zip(expectedHooks)) {
            val resolved = HookCommands.resolve(event, fullConfig)
            assertEquals(expectedHook, resolved?.event, "event $event must map to $expectedHook")
        }
    }

    @Test
    fun metaEvents_carryTheSnapshotContext_andIdleEventsDoNot() {
        val started = HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.Started(meta), fullConfig)
        assertEquals(HookContext(title = "The Movie", itemId = "item-9", positionMs = 65_432), started?.context)

        val idle = HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.IdleEntered, fullConfig)
        assertEquals(HookContext("", "", 0), idle?.context, "idle transitions carry no item")
    }

    @Test
    fun anEmptyCommand_meansTheHookIsDisabled() {
        val config = HookConfig(
            playCmd = "",
            stopCmd = "  ",
            endedCmd = "",
            idleCmd = "",
            idleEndedCmd = "",
        )
        for (event in allEvents) {
            assertNull(HookCommands.resolve(event, config), "a blank command disables its hook: $event")
        }
    }

    @Test
    fun onlyTheConfiguredHooksFire() {
        // Only the ended hook configured: started/idle resolve to null.
        val config = fullConfig.copy(playCmd = "", idleCmd = "")
        assertNull(HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.Started(meta), config))
        assertNull(HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.IdleEntered, config))
        assertEquals(HookEvent.MEDIA_ENDED, HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.Ended(meta), config)?.event)
    }

    // ── substitution ───────────────────────────────────────────────────────

    @Test
    fun substitution_exactPlaceholders() {
        val context = HookContext(title = "The Movie", itemId = "item-9", positionMs = 65_432)
        assertEquals("The Movie", HookCommands.substitute("{title}", context))
        assertEquals("item-9", HookCommands.substitute("{itemId}", context))
        assertEquals("65432", HookCommands.substitute("{position_ms}", context))
        assertEquals(
            "play --title \"The Movie\" --at 65432 --item item-9",
            HookCommands.substitute("play --title \"{title}\" --at {position_ms} --item {itemId}", context),
        )
    }

    @Test
    fun unknownPlaceholders_passThroughUntouched() {
        val context = HookContext(title = "T", itemId = "i", positionMs = 0)
        // `{unknown}` is the user's literal text; `{title}` still substitutes.
        assertEquals("{unknown} T", HookCommands.substitute("{unknown} {title}", context))
    }

    @Test
    fun substitution_onTheBlankIdleContext_producesEmptyFields() {
        assertEquals("idle --title  --item ", HookCommands.substitute("idle --title {title} --item {itemId}", HookContext("", "", 0)))
    }

    // ── tokenization ───────────────────────────────────────────────────────

    @Test
    fun plainWords_splitOnWhitespace() {
        assertEquals(listOf("notify-send", "hello", "world"), HookCommands.tokenize("notify-send  hello\tworld"))
    }

    @Test
    fun doubleQuotedArguments_stayTogether() {
        assertEquals(
            listOf("notify-send", "The Movie: part two"),
            HookCommands.tokenize("notify-send \"The Movie: part two\""),
        )
    }

    @Test
    fun singleQuotedArguments_stayTogether() {
        assertEquals(
            listOf("sh", "-c", "echo hi && echo bye"),
            HookCommands.tokenize("sh -c 'echo hi && echo bye'"),
        )
    }

    @Test
    fun placeholdersInsideQuotes_substituteBeforeSplitting() {
        // The runner's order: substitute the whole line, THEN tokenize — a
        // quoted {title} with spaces stays one argument.
        val substituted = HookCommands.substitute("notify-send \"{title}\"", HookContext("The Movie", "i", 0))
        assertEquals(listOf("notify-send", "The Movie"), HookCommands.tokenize(substituted))
    }

    @Test
    fun escapedCharacters_stayLiteral() {
        assertEquals(listOf("echo", "a\"b"), HookCommands.tokenize("echo a\\\"b"))
        assertEquals(listOf("path", "C:\\dir"), HookCommands.tokenize("path C:\\\\dir"))
    }

    @Test
    fun emptyAndBlankCommands_tokenizeToNothing() {
        assertEquals(emptyList(), HookCommands.tokenize(""))
        assertEquals(emptyList(), HookCommands.tokenize("   \t "))
    }

    @Test
    fun anUnterminatedQuote_keepsTheRestOfTheLine_failOpen() {
        assertEquals(listOf("echo", "never closed"), HookCommands.tokenize("echo \"never closed"))
    }

    @Test
    fun mixedQuoting_producesShellLikeArgv() {
        val argv = HookCommands.tokenize("script.sh --title \"The Movie\" --at 65432 --flag 'a b' end")
        assertEquals(
            listOf("script.sh", "--title", "The Movie", "--at", "65432", "--flag", "a b", "end"),
            argv,
        )
    }

    @Test
    fun tokenizeThenSubstituteOrder_producesTheRunnerArgv() {
        // The runner's actual pipeline: resolve → substitute → tokenize.
        val config = fullConfig.copy(playCmd = "notify-send \"{title}\" --item {itemId}")
        val resolved = HookCommands.resolve(NowPlayingReporter.NowPlayingEvent.Started(meta), config)!!
        val argv = HookCommands.tokenize(HookCommands.substitute(resolved.command, resolved.context))
        assertEquals(listOf("notify-send", "The Movie", "--item", "item-9"), argv)
    }
}
