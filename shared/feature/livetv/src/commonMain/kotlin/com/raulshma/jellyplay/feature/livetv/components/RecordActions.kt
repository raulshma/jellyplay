package com.raulshma.jellyplay.feature.livetv.components

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.LiveTvProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The timer mutation a [RecordOutcome] describes. */
enum class RecordAction {
    /** Create a single-episode timer ([LiveTvRepository.createTimer]). */
    RECORD_ONCE,
    /** Create a series timer ([LiveTvRepository.createSeriesTimer]). */
    RECORD_SERIES,
    /** Cancel an existing timer ([LiveTvRepository.cancelTimer]). */
    CANCEL_TIMER,
    /** Cancel a series timer ([LiveTvRepository.cancelSeriesTimer]). */
    CANCEL_SERIES,
}

/**
 * Identifies the timer mutation a [RecordOutcome] belongs to: which action on
 * which program and/or server timer id, so outcome consumers can tell requests
 * apart (e.g. create-vs-cancel feedback) without extra bookkeeping.
 */
data class RecordRequest(
    val action: RecordAction,
    /** The program the request was raised from; null for id-based cancels. */
    val program: LiveTvProgram? = null,
    val timerId: String? = null,
    val seriesTimerId: String? = null,
)

/**
 * The outcome of a [RecordActions] command. [Requesting] is published
 * synchronously when the command is issued; [Success]/[Error] follow when the
 * repository answers. [Error][Error.message] carries the raw exception
 * message — the consuming tab applies its own fallback literal.
 */
sealed interface RecordOutcome {
    /** Initial state — no request has been made. */
    data object Idle : RecordOutcome
    /** The repository call is in flight. */
    data class Requesting(val request: RecordRequest) : RecordOutcome
    /** The action completed successfully. */
    data class Success(val request: RecordRequest) : RecordOutcome
    /** The action failed. */
    data class Error(val request: RecordRequest, val message: String?) : RecordOutcome
}

/**
 * The ONE recording choreography behind the Live TV tabs, replacing the
 * per-ViewModel copies (Programs, Channel Detail, EPG, Schedule, Series).
 * Constructed over [LiveTvRepository] and the owning ViewModel's scope, it
 * runs every command through the same sequence — publish
 * [RecordOutcome.Requesting], make the single repository call, publish
 * [RecordOutcome.Success] or [RecordOutcome.Error] — and surfaces each outcome
 * twice: synchronously through [onOutcome] (so tabs can flip dialog state in
 * the same frame as the tap) and as the last value of [outcomes].
 *
 * Tabs are adapters: they map Success/Error onto their own refresh (Programs
 * load(), Channel Detail refreshPrograms, EPG loadGuide, Schedule/Series
 * reload) and their own feedback surface (the shared [RecordDialogState], a
 * one-shot message channel, or the tab's error field).
 */
class RecordActions(
    private val repository: LiveTvRepository,
    private val scope: CoroutineScope,
    private val onOutcome: (RecordOutcome) -> Unit,
) {

    private val _outcomes = MutableStateFlow<RecordOutcome>(RecordOutcome.Idle)

    /** Last published outcome — mirrors what [onOutcome] receives. */
    val outcomes: StateFlow<RecordOutcome> = _outcomes.asStateFlow()

    /** Schedules a single-episode timer for [program]. */
    fun recordOnce(program: LiveTvProgram) {
        run(RecordRequest(RecordAction.RECORD_ONCE, program = program)) {
            repository.createTimer(program.id)
        }
    }

    /** Schedules a series timer for [program]. */
    fun recordSeries(program: LiveTvProgram) {
        run(RecordRequest(RecordAction.RECORD_SERIES, program = program)) {
            repository.createSeriesTimer(program.id)
        }
    }

    /**
     * Cancels the timer attached to [program].
     * @return false (emitting nothing) when the program carries no timer id.
     */
    fun cancelTimer(program: LiveTvProgram): Boolean {
        val timerId = program.timerId ?: return false
        run(RecordRequest(RecordAction.CANCEL_TIMER, program = program, timerId = timerId)) {
            repository.cancelTimer(timerId)
        }
        return true
    }

    /**
     * Cancels the series timer attached to [program].
     * @return false (emitting nothing) when the program carries no series timer id.
     */
    fun cancelSeries(program: LiveTvProgram): Boolean {
        val seriesTimerId = program.seriesTimerId ?: return false
        run(RecordRequest(RecordAction.CANCEL_SERIES, program = program, seriesTimerId = seriesTimerId)) {
            repository.cancelSeriesTimer(seriesTimerId)
        }
        return true
    }

    /** Timer-id cancel for tabs holding timer rows (Schedule). */
    fun cancelTimer(timerId: String) {
        run(RecordRequest(RecordAction.CANCEL_TIMER, timerId = timerId)) {
            repository.cancelTimer(timerId)
        }
    }

    /** Series-timer-id cancel for tabs holding series-timer rows (Series). */
    fun cancelSeries(seriesTimerId: String) {
        run(RecordRequest(RecordAction.CANCEL_SERIES, seriesTimerId = seriesTimerId)) {
            repository.cancelSeriesTimer(seriesTimerId)
        }
    }

    /** The shared choreography: synchronous Requesting, then the call, then Success/Error. */
    private fun run(request: RecordRequest, call: suspend () -> Result<Unit>) {
        emit(RecordOutcome.Requesting(request))
        scope.launch {
            call()
                .onSuccess { emit(RecordOutcome.Success(request)) }
                .onFailure { emit(RecordOutcome.Error(request, it.message)) }
        }
    }

    private fun emit(outcome: RecordOutcome) {
        _outcomes.value = outcome
        onOutcome(outcome)
    }
}
