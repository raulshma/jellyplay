package com.raulshma.jellyplay.feature.details

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Pure [DetailStrings] fake for the detail unit tests: reconstructs the
 * smart-play label templates so label assertions stay meaningful ("Play
 * S1:E1"), and returns a stable `res#<id>` token (plus args) for everything
 * else. Replaces the former hand-stubbed `context.getString` blocks — no
 * Android context anywhere in the tests.
 */
internal fun fakeDetailStrings(): DetailStrings = DetailStrings { res, args ->
    when (res) {
        R.string.detail_resume_episode -> "Resume S${args[0]}:E${args[1]}"
        R.string.detail_next_up_episode -> "NextUp S${args[0]}:E${args[1]}"
        R.string.detail_play_episode -> "Play S${args[0]}:E${args[1]}"
        R.string.detail_replay_episode -> "Replay S${args[0]}:E${args[1]}"
        else -> if (args.isEmpty()) "res#$res" else "res#$res:${args.joinToString()}"
    }
}

/**
 * Captures the one-shot [DetailMessage]s a helper `tryEmit`s into the shared
 * flow. Replay-backed so a test can read [recorded] after `advanceUntilIdle`
 * without launching a collector.
 */
internal class RecordingMessages {
    var flow = MutableSharedFlow<DetailMessage>(replay = 64)
        private set
    val recorded: List<DetailMessage> get() = flow.replayCache

    /** Swaps in a fresh flow so per-test recordings start empty. */
    fun reset() {
        flow = MutableSharedFlow(replay = 64)
    }
}
