package com.egrmeister.roomresetmini.domain.timer

/** Monotonic time since boot (SystemClock.elapsedRealtime on Android). Injected for tests. */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

/** Wall-clock time, used only for timestamps shown in history — never for the countdown. */
fun interface WallClock {
    fun currentTimeMillis(): Long
}

/** Identifies the current device boot so a stored elapsedRealtime checkpoint can be trusted. */
fun interface BootReferenceProvider {
    fun bootReference(): String
}

enum class TimerStatus {
    /** Counting down from a checkpoint. */
    RUNNING,

    /** Manually paused, or paused after a restart. */
    PAUSED,

    /** Reached zero; the session and its tasks are untouched. */
    EXPIRED,

    /** User chose "Continue without timer" after expiry ("Extra time"). */
    OFF;

    companion object {
        fun fromKey(key: String): TimerStatus? = entries.firstOrNull { it.name == key }
    }
}

/**
 * Persisted timer checkpoint. Remaining time is never decremented in storage; it is derived:
 * remaining = remainingAtCheckpoint − (elapsedRealtimeNow − checkpointElapsedRealtime), clamped at 0.
 */
data class TimerState(
    val status: TimerStatus,
    val remainingAtCheckpointMillis: Long,
    val checkpointElapsedRealtimeMillis: Long,
    val bootReference: String,
    val continuedWithoutTimer: Boolean = false,
)

data class RecoveryResult(
    val state: TimerState,
    /** True when a running timer had to be paused because the monotonic reference is no longer valid. */
    val pausedAfterRestart: Boolean,
)

object TimerEngine {

    fun start(durationMillis: Long, nowElapsed: Long, bootReference: String): TimerState {
        require(durationMillis > 0) { "Duration must be positive" }
        return TimerState(
            status = TimerStatus.RUNNING,
            remainingAtCheckpointMillis = durationMillis,
            checkpointElapsedRealtimeMillis = nowElapsed,
            bootReference = bootReference,
        )
    }

    fun remaining(state: TimerState, nowElapsed: Long): Long = when (state.status) {
        TimerStatus.RUNNING -> {
            val elapsed = (nowElapsed - state.checkpointElapsedRealtimeMillis).coerceAtLeast(0L)
            (state.remainingAtCheckpointMillis - elapsed).coerceAtLeast(0L)
        }
        TimerStatus.PAUSED -> state.remainingAtCheckpointMillis.coerceAtLeast(0L)
        TimerStatus.EXPIRED, TimerStatus.OFF -> 0L
    }

    /** Manual pause: stops elapsed-time accumulation. */
    fun pause(state: TimerState, nowElapsed: Long): TimerState {
        if (state.status != TimerStatus.RUNNING) return state
        val left = remaining(state, nowElapsed)
        return if (left == 0L) expire(state, nowElapsed)
        else state.copy(
            status = TimerStatus.PAUSED,
            remainingAtCheckpointMillis = left,
            checkpointElapsedRealtimeMillis = nowElapsed,
        )
    }

    fun resume(state: TimerState, nowElapsed: Long, bootReference: String): TimerState {
        if (state.status != TimerStatus.PAUSED || state.remainingAtCheckpointMillis <= 0L) return state
        return state.copy(
            status = TimerStatus.RUNNING,
            checkpointElapsedRealtimeMillis = nowElapsed,
            bootReference = bootReference,
        )
    }

    /**
     * Re-bases a running timer onto a fresh checkpoint (e.g. when the app goes to the background or
     * periodically), so a later reboot restores the latest saved remaining duration.
     */
    fun checkpoint(state: TimerState, nowElapsed: Long): TimerState {
        if (state.status != TimerStatus.RUNNING) return state
        if (nowElapsed < state.checkpointElapsedRealtimeMillis) return state
        val left = remaining(state, nowElapsed)
        return if (left == 0L) expire(state, nowElapsed)
        else state.copy(remainingAtCheckpointMillis = left, checkpointElapsedRealtimeMillis = nowElapsed)
    }

    /** Moves a running timer to EXPIRED once it reaches zero. Never touches tasks. */
    fun tick(state: TimerState, nowElapsed: Long): TimerState =
        if (state.status == TimerStatus.RUNNING && remaining(state, nowElapsed) == 0L) expire(state, nowElapsed)
        else state

    fun continueWithoutTimer(state: TimerState): TimerState =
        if (state.status != TimerStatus.EXPIRED) state
        else state.copy(status = TimerStatus.OFF, remainingAtCheckpointMillis = 0L, continuedWithoutTimer = true)

    /**
     * Called after process recreation or when the app returns to the foreground.
     * - Same boot and a sane monotonic clock: the running timer continued logically (may now be expired).
     * - Different boot, or "now" earlier than the checkpoint: the stored checkpoint is meaningless, so the
     *   session is restored PAUSED with its latest saved remaining duration.
     */
    fun recover(state: TimerState, nowElapsed: Long, currentBootReference: String): RecoveryResult {
        if (state.status != TimerStatus.RUNNING) return RecoveryResult(state, pausedAfterRestart = false)
        val invalid = state.bootReference != currentBootReference ||
            nowElapsed < state.checkpointElapsedRealtimeMillis
        if (invalid) {
            val paused = state.copy(
                status = TimerStatus.PAUSED,
                remainingAtCheckpointMillis = state.remainingAtCheckpointMillis.coerceAtLeast(0L),
                checkpointElapsedRealtimeMillis = nowElapsed,
                bootReference = currentBootReference,
            )
            return if (paused.remainingAtCheckpointMillis == 0L) {
                RecoveryResult(expire(paused, nowElapsed), pausedAfterRestart = false)
            } else {
                RecoveryResult(paused, pausedAfterRestart = true)
            }
        }
        return RecoveryResult(tick(state, nowElapsed), pausedAfterRestart = false)
    }

    private fun expire(state: TimerState, nowElapsed: Long): TimerState = state.copy(
        status = TimerStatus.EXPIRED,
        remainingAtCheckpointMillis = 0L,
        checkpointElapsedRealtimeMillis = nowElapsed,
    )
}
