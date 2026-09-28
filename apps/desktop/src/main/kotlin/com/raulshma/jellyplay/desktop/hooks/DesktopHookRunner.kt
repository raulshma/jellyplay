package com.raulshma.jellyplay.desktop.hooks

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The playback-event shell-hook runner (feature 4.3): consumes the app-wide
 * [NowPlayingReporter] event stream, maps each event to its configured
 * command ([HookCommands.resolve] — the mpv-shim event names), substitutes
 * the `{title}`/`{itemId}`/`{position_ms}` placeholders, tokenizes respecting
 * quoted arguments, and executes via `ProcessBuilder` fire-and-forget on the
 * injected [scope].
 *
 * Safety posture (the settings screen's warning copy repeats it): the
 * commands are exactly what the user configured on this machine — they run
 * locally and never round-trip through any server. The runner never blocks
 * playback: process start is fire-and-forget (output/error discarded), every
 * failure logs and moves on, and a blank command (or the master toggle off)
 * disables the hook entirely. The [commandExecutor] seam keeps the truth
 * table testable without spawning real processes.
 */
internal class DesktopHookRunner(
    private val scope: CoroutineScope,
    /** The settings slice — the master toggle + five commands are read per event. */
    private val settings: StateFlow<ScreensaverSlice>,
    private val events: Flow<NowPlayingReporter.NowPlayingEvent>,
    /** The execution seam: receives the tokenized, substituted argv. */
    private val commandExecutor: (List<String>) -> Unit = ::executeProcess,
) {

    private var collectJob: Job? = null

    /** Idempotent app-lifetime kickoff — the launchDesktopStartup idiom. */
    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            events.collect { event -> runHook(event) }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    /** One event → at most one process. Never throws. */
    internal fun runHook(event: NowPlayingReporter.NowPlayingEvent) {
        val slice = settings.value
        if (!slice.hooksEnabled) return
        val hook = HookCommands.resolve(event, HookCommands.HookConfig.from(slice)) ?: return
        val argv = HookCommands.tokenize(HookCommands.substitute(hook.command, hook.context))
        if (argv.isEmpty()) return
        try {
            commandExecutor(argv)
            Log.d(TAG, "${hook.event} hook ran: ${argv.first()}")
        } catch (e: Exception) {
            Log.w(TAG, "${hook.event} hook failed (playback unaffected)", e)
        }
    }

    companion object {
        private const val TAG = "DesktopHookRunner"

        /**
         * The production executor: start the process, discard its output,
         * never wait for it — a hook cannot block or crash playback.
         */
        private fun executeProcess(argv: List<String>) {
            ProcessBuilder(argv)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
    }
}
