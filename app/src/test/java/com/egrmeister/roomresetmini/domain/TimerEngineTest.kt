package com.egrmeister.roomresetmini.domain

import com.egrmeister.roomresetmini.domain.timer.BootReferenceProvider
import com.egrmeister.roomresetmini.domain.timer.MonotonicClock
import com.egrmeister.roomresetmini.domain.timer.TimerEngine
import com.egrmeister.roomresetmini.domain.timer.TimerStatus
import com.egrmeister.roomresetmini.domain.timer.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeClocks : MonotonicClock, WallClock, BootReferenceProvider {
    var elapsed = 1_000_000L
    var wall = 1_700_000_000_000L
    var boot = "boot:1"

    override fun elapsedRealtimeMillis() = elapsed
    override fun currentTimeMillis() = wall
    override fun bootReference() = boot

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }
}

class TimerEngineTest {
    private val clocks = FakeClocks()
    private val tenMinutes = 10 * 60_000L

    private fun startTimer(duration: Long = tenMinutes) =
        TimerEngine.start(duration, clocks.elapsedRealtimeMillis(), clocks.bootReference())

    @Test
    fun allDurationsCountDownFromCheckpoint() {
        listOf(5, 10, 15).forEach { minutes ->
            val start = startTimer(minutes * 60_000L)
            assertEquals(minutes * 60_000L, TimerEngine.remaining(start, clocks.elapsed))
            assertEquals(minutes * 60_000L - 30_000L, TimerEngine.remaining(start, clocks.elapsed + 30_000L))
        }
    }

    @Test
    fun pauseStopsAccumulationAndResumeContinues() {
        var state = startTimer()
        clocks.advance(60_000L)
        state = TimerEngine.pause(state, clocks.elapsed)
        assertEquals(TimerStatus.PAUSED, state.status)
        assertEquals(tenMinutes - 60_000L, state.remainingAtCheckpointMillis)

        clocks.advance(5 * 60_000L) // paused for five minutes
        assertEquals(tenMinutes - 60_000L, TimerEngine.remaining(state, clocks.elapsed))

        state = TimerEngine.resume(state, clocks.elapsed, clocks.boot)
        assertEquals(TimerStatus.RUNNING, state.status)
        clocks.advance(60_000L)
        assertEquals(tenMinutes - 120_000L, TimerEngine.remaining(state, clocks.elapsed))
    }

    @Test
    fun backgroundingContinuesLogicallyFromCheckpoint() {
        var state = startTimer()
        clocks.advance(2 * 60_000L)
        state = TimerEngine.checkpoint(state, clocks.elapsed) // app goes to background
        assertEquals(tenMinutes - 120_000L, state.remainingAtCheckpointMillis)

        clocks.advance(3 * 60_000L) // three minutes in the background, same boot
        val recovered = TimerEngine.recover(state, clocks.elapsed, clocks.boot)
        assertFalse(recovered.pausedAfterRestart)
        assertEquals(TimerStatus.RUNNING, recovered.state.status)
        assertEquals(tenMinutes - 300_000L, TimerEngine.remaining(recovered.state, clocks.elapsed))
    }

    @Test
    fun expiryWhileBackgroundedIsDetectedOnReturnWithoutLosingTheSession() {
        val state = startTimer(5 * 60_000L)
        clocks.advance(6 * 60_000L)
        val recovered = TimerEngine.recover(state, clocks.elapsed, clocks.boot)
        assertEquals(TimerStatus.EXPIRED, recovered.state.status)
        assertEquals(0L, TimerEngine.remaining(recovered.state, clocks.elapsed))
        assertFalse(recovered.pausedAfterRestart)
    }

    @Test
    fun tickExpiresAtZeroAndNeverGoesNegative() {
        var state = startTimer(5 * 60_000L)
        clocks.advance(5 * 60_000L - 1)
        state = TimerEngine.tick(state, clocks.elapsed)
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(1L, TimerEngine.remaining(state, clocks.elapsed))
        clocks.advance(10_000L)
        assertEquals(0L, TimerEngine.remaining(state, clocks.elapsed))
        state = TimerEngine.tick(state, clocks.elapsed)
        assertEquals(TimerStatus.EXPIRED, state.status)
        // Expired timers cannot be resumed or paused into a negative state.
        assertEquals(state, TimerEngine.resume(state, clocks.elapsed, clocks.boot))
        assertEquals(state, TimerEngine.pause(state, clocks.elapsed))
    }

    @Test
    fun processRecreationRestoresRunningAndPausedStates() {
        val running = startTimer()
        clocks.advance(90_000L)
        // Process killed and recreated on the same boot: state read back from the database.
        val restoredRunning = TimerEngine.recover(running.copy(), clocks.elapsed, clocks.boot)
        assertEquals(TimerStatus.RUNNING, restoredRunning.state.status)
        assertEquals(tenMinutes - 90_000L, TimerEngine.remaining(restoredRunning.state, clocks.elapsed))

        val paused = TimerEngine.pause(running, clocks.elapsed)
        clocks.advance(10 * 60_000L)
        val restoredPaused = TimerEngine.recover(paused, clocks.elapsed, clocks.boot)
        assertEquals(TimerStatus.PAUSED, restoredPaused.state.status)
        assertEquals(tenMinutes - 90_000L, TimerEngine.remaining(restoredPaused.state, clocks.elapsed))
    }

    @Test
    fun rebootRestoresPausedWithLatestSavedRemaining() {
        var state = startTimer()
        clocks.advance(4 * 60_000L)
        state = TimerEngine.checkpoint(state, clocks.elapsed) // last saved checkpoint: 6 min left

        // Device reboots: elapsedRealtime restarts near zero and the boot reference changes.
        clocks.elapsed = 15_000L
        clocks.boot = "boot:2"
        val recovered = TimerEngine.recover(state, clocks.elapsed, clocks.boot)
        assertTrue(recovered.pausedAfterRestart)
        assertEquals(TimerStatus.PAUSED, recovered.state.status)
        assertEquals(6 * 60_000L, TimerEngine.remaining(recovered.state, clocks.elapsed))
        assertEquals("boot:2", recovered.state.bootReference)

        // Resuming after the reboot counts down from the restored value.
        val resumed = TimerEngine.resume(recovered.state, clocks.elapsed, clocks.boot)
        clocks.elapsed += 60_000L
        assertEquals(5 * 60_000L, TimerEngine.remaining(resumed, clocks.elapsed))
    }

    @Test
    fun invalidMonotonicReferenceIsTreatedLikeARestart() {
        val state = startTimer()
        clocks.elapsed -= 500_000L // now earlier than the checkpoint, same boot id
        val recovered = TimerEngine.recover(state, clocks.elapsed, clocks.boot)
        assertTrue(recovered.pausedAfterRestart)
        assertEquals(TimerStatus.PAUSED, recovered.state.status)
        assertEquals(tenMinutes, recovered.state.remainingAtCheckpointMillis)
    }

    @Test
    fun wallClockChangesDoNotAlterCountdown() {
        val state = startTimer()
        clocks.advance(60_000L)
        val before = TimerEngine.remaining(state, clocks.elapsed)
        clocks.wall += 3 * 3_600_000L // user moves the clock forward three hours
        assertEquals(before, TimerEngine.remaining(state, clocks.elapsed))
        clocks.wall -= 24 * 3_600_000L // and back a day
        assertEquals(before, TimerEngine.remaining(state, clocks.elapsed))
        val recovered = TimerEngine.recover(state, clocks.elapsed, clocks.boot)
        assertEquals(before, TimerEngine.remaining(recovered.state, clocks.elapsed))
    }

    @Test
    fun continueWithoutTimerKeepsSessionUsable() {
        var state = startTimer(5 * 60_000L)
        clocks.advance(5 * 60_000L)
        state = TimerEngine.tick(state, clocks.elapsed)
        assertEquals(TimerStatus.EXPIRED, state.status)
        state = TimerEngine.continueWithoutTimer(state)
        assertEquals(TimerStatus.OFF, state.status)
        assertTrue(state.continuedWithoutTimer)
        clocks.advance(60 * 60_000L)
        // No further expiry, no negative time, recovery leaves it alone.
        assertEquals(state, TimerEngine.tick(state, clocks.elapsed))
        assertEquals(state, TimerEngine.recover(state, clocks.elapsed, "boot:9").state)
        // Only an expired timer can switch to "continue without timer".
        val running = startTimer()
        assertEquals(running, TimerEngine.continueWithoutTimer(running))
    }
}
