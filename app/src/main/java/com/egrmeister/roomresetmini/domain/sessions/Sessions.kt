package com.egrmeister.roomresetmini.domain.sessions

import com.egrmeister.roomresetmini.domain.progress.Progress
import com.egrmeister.roomresetmini.domain.progress.SessionProgress
import com.egrmeister.roomresetmini.domain.progress.ZoneItem
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.domain.timer.TimerState
import com.egrmeister.roomresetmini.domain.timer.WallClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SessionStatus {
    ACTIVE,
    SAVED;

    companion object {
        fun fromKey(key: String): SessionStatus? = entries.firstOrNull { it.name == key }
    }
}

object Durations {
    val MINUTES: List<Int> = listOf(5, 10, 15)
    const val DEFAULT_MINUTES = 10

    fun sanitize(minutes: Int?): Int = if (minutes != null && minutes in MINUTES) minutes else DEFAULT_MINUTES
    fun toMillis(minutes: Int): Long = minutes * 60_000L
    fun minutesOf(millis: Long): Int = (millis / 60_000L).toInt()
}

data class SessionTask(
    val id: Long,
    override val zone: ZoneType,
    val label: String,
    val order: Int,
    override val completed: Boolean,
) : ZoneItem

data class ResetSession(
    val id: Long,
    val templateName: String,
    val durationMillis: Long,
    val startedAtMillis: Long,
    val savedAtMillis: Long?,
    val status: SessionStatus,
    val timer: TimerState,
    val tasks: List<SessionTask>,
) {
    val progress: SessionProgress get() = Progress.session(tasks)
    val result: ResultKind get() = ResultKind.of(progress.completedTasks, progress.totalTasks)

    fun tasksIn(zone: ZoneType): List<SessionTask> =
        tasks.filter { it.zone == zone }.sortedWith(compareBy<SessionTask> { it.order }.thenBy { it.id })
}

/** Immutable copy of a template taken when a session starts. Later template edits never touch it. */
data class SnapshotTask(val zone: ZoneType, val label: String, val order: Int)

data class SessionDraft(
    val templateName: String,
    val durationMillis: Long,
    val tasks: List<SnapshotTask>,
)

object SessionSnapshot {
    fun from(template: RoomTemplate, durationMillis: Long): SessionDraft {
        require(durationMillis > 0) { "Duration must be positive" }
        val tasks = ZoneType.entries.flatMap { zone ->
            template.tasksIn(zone).mapIndexed { index, task -> SnapshotTask(zone, task.label, index) }
        }
        return SessionDraft(template.name, durationMillis, tasks.toList())
    }
}

enum class ResultKind(val label: String) {
    ALL_CHECKED("All checked"),
    PARTIAL("Partial"),
    NOTHING_CHECKED("Partial");

    companion object {
        fun of(completed: Int, total: Int): ResultKind = when {
            total > 0 && completed >= total -> ALL_CHECKED
            completed == 0 -> NOTHING_CHECKED
            else -> PARTIAL
        }
    }
}

/** Message shown after a save. Never claims more than the user checked. */
fun ResultKind.savedMessage(): String = when (this) {
    ResultKind.ALL_CHECKED -> "All tasks checked."
    ResultKind.PARTIAL -> "Progress saved."
    ResultKind.NOTHING_CHECKED -> "Session saved. No tasks were checked."
}

data class HistoryEntry(
    val id: Long,
    val templateName: String,
    val durationMillis: Long,
    val savedAtMillis: Long,
    val completedTasks: Int,
    val totalTasks: Int,
) {
    val result: ResultKind get() = ResultKind.of(completedTasks, totalTasks)
}

object HistoryPolicy {
    const val LIMIT = 100

    data class SavedRef(val id: Long, val savedAtMillis: Long)

    /** Saved sessions beyond the newest [keep] (by savedAt, then id). Mirrors the SQL used by Room. */
    fun idsToDelete(saved: List<SavedRef>, keep: Int = LIMIT): Set<Long> =
        saved.sortedWith(compareByDescending<SavedRef> { it.savedAtMillis }.thenByDescending { it.id })
            .drop(keep)
            .map { it.id }
            .toSet()
}

/** Persistence port for saving. The implementation must be atomic and only save an ACTIVE session. */
interface SessionSaveGateway {
    /** @return true if this call saved the session, false if it was already saved / no longer active. */
    suspend fun markSavedAndPrune(sessionId: Long, savedAtMillis: Long, keep: Int): Boolean
}

enum class SaveOutcome { SAVED, ALREADY_SAVED }

/**
 * Serialises save requests so double taps / repeated calls never create duplicate history entries.
 * The gateway's conditional update (status = ACTIVE) makes the save idempotent across processes too.
 */
class SaveCoordinator(
    private val gateway: SessionSaveGateway,
    private val wallClock: WallClock,
) {
    private val mutex = Mutex()

    suspend fun save(sessionId: Long): SaveOutcome = mutex.withLock {
        val saved = gateway.markSavedAndPrune(sessionId, wallClock.currentTimeMillis(), HistoryPolicy.LIMIT)
        if (saved) SaveOutcome.SAVED else SaveOutcome.ALREADY_SAVED
    }
}
