package com.egrmeister.roomresetmini.data.repository

import com.egrmeister.roomresetmini.data.local.ResetSessionEntity
import com.egrmeister.roomresetmini.data.local.SessionDao
import com.egrmeister.roomresetmini.data.local.SessionTaskEntity
import com.egrmeister.roomresetmini.domain.sessions.HistoryEntry
import com.egrmeister.roomresetmini.domain.sessions.ResetSession
import com.egrmeister.roomresetmini.domain.sessions.SessionDraft
import com.egrmeister.roomresetmini.domain.sessions.SessionSaveGateway
import com.egrmeister.roomresetmini.domain.sessions.SessionStatus
import com.egrmeister.roomresetmini.domain.sessions.SessionTask
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.domain.timer.TimerState
import com.egrmeister.roomresetmini.domain.timer.TimerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SessionRepository(private val dao: SessionDao) : SessionSaveGateway {

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSession: Flow<ResetSession?> = dao.observeActive().flatMapLatest { session ->
        if (session == null) flowOf(null)
        else dao.observeTasks(session.id).map { tasks -> session.toDomain(tasks) }
    }

    val history: Flow<List<HistoryEntry>> = dao.observeSaved().map { rows ->
        rows.map { row ->
            HistoryEntry(
                id = row.session.id,
                templateName = row.session.templateNameSnapshot,
                durationMillis = row.session.selectedDurationMillis,
                savedAtMillis = row.session.savedAt ?: row.session.startedAt,
                completedTasks = row.completedTasks,
                totalTasks = row.totalTasks,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSession(id: Long): Flow<ResetSession?> = dao.observeSession(id).flatMapLatest { session ->
        if (session == null) flowOf(null)
        else dao.observeTasks(session.id).map { tasks -> session.toDomain(tasks) }
    }

    suspend fun getActiveSession(): ResetSession? {
        val session = dao.getActive() ?: return null
        return session.toDomain(dao.getTasks(session.id))
    }

    suspend fun getSession(id: Long): ResetSession? {
        val session = dao.getSession(id) ?: return null
        return session.toDomain(dao.getTasks(id))
    }

    /** @return new session id, or null if an unfinished session already exists (never overwritten). */
    suspend fun createSession(draft: SessionDraft, timer: TimerState, startedAtMillis: Long): Long? =
        dao.createSession(
            ResetSessionEntity(
                templateNameSnapshot = draft.templateName,
                selectedDurationMillis = draft.durationMillis,
                startedAt = startedAtMillis,
                savedAt = null,
                status = SessionStatus.ACTIVE.name,
                timerStatus = timer.status.name,
                remainingAtCheckpointMillis = timer.remainingAtCheckpointMillis,
                checkpointElapsedRealtimeMillis = timer.checkpointElapsedRealtimeMillis,
                bootReference = timer.bootReference,
                continuedWithoutTimer = timer.continuedWithoutTimer,
            ),
            draft.tasks.map { task ->
                SessionTaskEntity(
                    sessionId = 0,
                    zoneType = task.zone.name,
                    labelSnapshot = task.label,
                    displayOrder = task.order,
                    completed = false,
                )
            },
        )

    suspend fun setTaskCompleted(taskId: Long, completed: Boolean): Boolean =
        dao.setTaskCompleted(taskId, completed) > 0

    suspend fun updateTimer(sessionId: Long, timer: TimerState): Boolean =
        dao.updateTimer(
            id = sessionId,
            timerStatus = timer.status.name,
            remaining = timer.remainingAtCheckpointMillis,
            checkpoint = timer.checkpointElapsedRealtimeMillis,
            bootReference = timer.bootReference,
            continuedWithoutTimer = timer.continuedWithoutTimer,
        ) > 0

    override suspend fun markSavedAndPrune(sessionId: Long, savedAtMillis: Long, keep: Int): Boolean =
        dao.saveAndPrune(sessionId, savedAtMillis, keep)

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun deleteAll() {
        dao.deleteAllTasks()
        dao.deleteAllSessions()
    }
}

private fun ResetSessionEntity.toDomain(tasks: List<SessionTaskEntity>): ResetSession = ResetSession(
    id = id,
    templateName = templateNameSnapshot,
    durationMillis = selectedDurationMillis,
    startedAtMillis = startedAt,
    savedAtMillis = savedAt,
    status = SessionStatus.fromKey(status) ?: SessionStatus.SAVED,
    timer = TimerState(
        status = TimerStatus.fromKey(timerStatus) ?: TimerStatus.PAUSED,
        remainingAtCheckpointMillis = remainingAtCheckpointMillis,
        checkpointElapsedRealtimeMillis = checkpointElapsedRealtimeMillis,
        bootReference = bootReference,
        continuedWithoutTimer = continuedWithoutTimer,
    ),
    tasks = tasks.mapNotNull { t ->
        ZoneType.fromKey(t.zoneType)?.let { zone -> SessionTask(t.id, zone, t.labelSnapshot, t.displayOrder, t.completed) }
    },
)
