package com.egrmeister.roomresetmini.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TemplateDao {

    @Query("SELECT * FROM room_templates ORDER BY displayOrder, id")
    abstract fun observeTemplates(): Flow<List<RoomTemplateEntity>>

    @Query("SELECT * FROM template_tasks ORDER BY templateId, displayOrder, id")
    abstract fun observeAllTasks(): Flow<List<TemplateTaskEntity>>

    @Query("SELECT * FROM room_templates ORDER BY displayOrder, id")
    abstract suspend fun getTemplates(): List<RoomTemplateEntity>

    @Query("SELECT * FROM room_templates WHERE id = :id")
    abstract suspend fun getTemplate(id: Long): RoomTemplateEntity?

    @Query("SELECT * FROM template_tasks WHERE templateId = :templateId ORDER BY displayOrder, id")
    abstract suspend fun getTasks(templateId: Long): List<TemplateTaskEntity>

    @Query("SELECT COUNT(*) FROM room_templates")
    abstract suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(displayOrder), -1) FROM room_templates")
    abstract suspend fun maxDisplayOrder(): Int

    @Insert
    abstract suspend fun insertTemplateRow(template: RoomTemplateEntity): Long

    @Insert
    abstract suspend fun insertTaskRows(tasks: List<TemplateTaskEntity>)

    @Update
    abstract suspend fun updateTemplateRow(template: RoomTemplateEntity)

    @Query("DELETE FROM template_tasks WHERE templateId = :templateId")
    abstract suspend fun deleteTasksFor(templateId: Long)

    @Query("DELETE FROM room_templates WHERE id = :id")
    abstract suspend fun deleteTemplateRow(id: Long)

    @Query("DELETE FROM template_tasks")
    abstract suspend fun deleteAllTasks()

    @Query("DELETE FROM room_templates")
    abstract suspend fun deleteAllTemplates()

    /** Inserts a template and its tasks atomically. Task templateIds are filled in here. */
    @Transaction
    open suspend fun insertWithTasks(template: RoomTemplateEntity, tasks: List<TemplateTaskEntity>): Long {
        val id = insertTemplateRow(template)
        insertTaskRows(tasks.map { it.copy(id = 0, templateId = id) })
        return id
    }

    /** Replaces name and the whole task list atomically. */
    @Transaction
    open suspend fun replaceTemplate(template: RoomTemplateEntity, tasks: List<TemplateTaskEntity>) {
        updateTemplateRow(template)
        deleteTasksFor(template.id)
        insertTaskRows(tasks.map { it.copy(id = 0, templateId = template.id) })
    }

    @Transaction
    open suspend fun deleteTemplate(id: Long) {
        deleteTasksFor(id)
        deleteTemplateRow(id)
    }
}

@Dao
abstract class SessionDao {

    @Query("SELECT * FROM reset_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    abstract fun observeActive(): Flow<ResetSessionEntity?>

    @Query("SELECT * FROM reset_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    abstract suspend fun getActive(): ResetSessionEntity?

    @Query("SELECT COUNT(*) FROM reset_sessions WHERE status = 'ACTIVE'")
    abstract suspend fun countActive(): Int

    @Query("SELECT * FROM reset_sessions WHERE id = :id")
    abstract suspend fun getSession(id: Long): ResetSessionEntity?

    @Query("SELECT * FROM reset_sessions WHERE id = :id")
    abstract fun observeSession(id: Long): Flow<ResetSessionEntity?>

    @Query("SELECT * FROM session_tasks WHERE sessionId = :sessionId ORDER BY displayOrder, id")
    abstract fun observeTasks(sessionId: Long): Flow<List<SessionTaskEntity>>

    @Query("SELECT * FROM session_tasks WHERE sessionId = :sessionId ORDER BY displayOrder, id")
    abstract suspend fun getTasks(sessionId: Long): List<SessionTaskEntity>

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM session_tasks t WHERE t.sessionId = s.id) AS totalTasks,
            (SELECT COUNT(*) FROM session_tasks t WHERE t.sessionId = s.id AND t.completed = 1) AS completedTasks
        FROM reset_sessions s
        WHERE s.status = 'SAVED'
        ORDER BY s.savedAt DESC, s.id DESC
        """,
    )
    abstract fun observeSaved(): Flow<List<SessionWithCounts>>

    @Insert
    abstract suspend fun insertSessionRow(session: ResetSessionEntity): Long

    @Insert
    abstract suspend fun insertTaskRows(tasks: List<SessionTaskEntity>)

    /** Only tasks of the ACTIVE session can change; saved history is immutable. */
    @Query(
        """
        UPDATE session_tasks SET completed = :completed
        WHERE id = :taskId AND sessionId IN (SELECT id FROM reset_sessions WHERE status = 'ACTIVE')
        """,
    )
    abstract suspend fun setTaskCompleted(taskId: Long, completed: Boolean): Int

    @Query(
        """
        UPDATE reset_sessions SET
            timerStatus = :timerStatus,
            remainingAtCheckpointMillis = :remaining,
            checkpointElapsedRealtimeMillis = :checkpoint,
            bootReference = :bootReference,
            continuedWithoutTimer = :continuedWithoutTimer
        WHERE id = :id AND status = 'ACTIVE'
        """,
    )
    abstract suspend fun updateTimer(
        id: Long,
        timerStatus: String,
        remaining: Long,
        checkpoint: Long,
        bootReference: String,
        continuedWithoutTimer: Boolean,
    ): Int

    /** Conditional update = duplicate-save guard: a session can only move ACTIVE → SAVED once. */
    @Query("UPDATE reset_sessions SET status = 'SAVED', savedAt = :savedAt WHERE id = :id AND status = 'ACTIVE'")
    abstract suspend fun markSaved(id: Long, savedAt: Long): Int

    @Query(
        """
        DELETE FROM session_tasks WHERE sessionId IN (
            SELECT id FROM reset_sessions WHERE status = 'SAVED' AND id NOT IN (
                SELECT id FROM reset_sessions WHERE status = 'SAVED' ORDER BY savedAt DESC, id DESC LIMIT :keep
            )
        )
        """,
    )
    abstract suspend fun pruneTasks(keep: Int)

    @Query(
        """
        DELETE FROM reset_sessions WHERE status = 'SAVED' AND id NOT IN (
            SELECT id FROM reset_sessions WHERE status = 'SAVED' ORDER BY savedAt DESC, id DESC LIMIT :keep
        )
        """,
    )
    abstract suspend fun pruneSessions(keep: Int)

    @Query("DELETE FROM session_tasks WHERE sessionId = :id")
    abstract suspend fun deleteTasksFor(id: Long)

    @Query("DELETE FROM reset_sessions WHERE id = :id")
    abstract suspend fun deleteSessionRow(id: Long)

    @Query("DELETE FROM session_tasks WHERE sessionId IN (SELECT id FROM reset_sessions WHERE status = 'SAVED')")
    abstract suspend fun deleteSavedTasks()

    @Query("DELETE FROM reset_sessions WHERE status = 'SAVED'")
    abstract suspend fun deleteSavedSessions()

    @Query("DELETE FROM session_tasks")
    abstract suspend fun deleteAllTasks()

    @Query("DELETE FROM reset_sessions")
    abstract suspend fun deleteAllSessions()

    /** Creates the session snapshot atomically. Returns null if an unfinished session already exists. */
    @Transaction
    open suspend fun createSession(session: ResetSessionEntity, tasks: List<SessionTaskEntity>): Long? {
        if (countActive() > 0) return null
        val id = insertSessionRow(session)
        insertTaskRows(tasks.map { it.copy(id = 0, sessionId = id) })
        return id
    }

    /** Save + history retention in one transaction. */
    @Transaction
    open suspend fun saveAndPrune(id: Long, savedAt: Long, keep: Int): Boolean {
        if (markSaved(id, savedAt) == 0) return false
        pruneTasks(keep)
        pruneSessions(keep)
        return true
    }

    @Transaction
    open suspend fun deleteSession(id: Long) {
        deleteTasksFor(id)
        deleteSessionRow(id)
    }

    @Transaction
    open suspend fun clearHistory() {
        deleteSavedTasks()
        deleteSavedSessions()
    }
}
