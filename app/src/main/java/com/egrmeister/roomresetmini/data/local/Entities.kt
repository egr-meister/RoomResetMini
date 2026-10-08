package com.egrmeister.roomresetmini.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "room_templates")
data class RoomTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "template_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoomTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    /** [com.egrmeister.roomresetmini.domain.templates.ZoneType] name. */
    val zoneType: String,
    val label: String,
    val displayOrder: Int,
)

@Entity(
    tableName = "reset_sessions",
    indices = [Index("status"), Index("savedAt")],
)
data class ResetSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateNameSnapshot: String,
    val selectedDurationMillis: Long,
    /** Wall-clock start time, for display only. */
    val startedAt: Long,
    /** Wall-clock save time; null while the session is unfinished. */
    val savedAt: Long?,
    /** [com.egrmeister.roomresetmini.domain.sessions.SessionStatus] name. */
    val status: String,
    /** [com.egrmeister.roomresetmini.domain.timer.TimerStatus] name. */
    val timerStatus: String,
    val remainingAtCheckpointMillis: Long,
    /** SystemClock.elapsedRealtime() at the checkpoint. Valid only for [bootReference]. */
    val checkpointElapsedRealtimeMillis: Long,
    val bootReference: String,
    val continuedWithoutTimer: Boolean,
)

@Entity(
    tableName = "session_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ResetSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val zoneType: String,
    val labelSnapshot: String,
    val displayOrder: Int,
    val completed: Boolean,
)

/** History row: a saved session plus task counts computed in SQL. */
data class SessionWithCounts(
    @Embedded val session: ResetSessionEntity,
    @ColumnInfo(name = "totalTasks") val totalTasks: Int,
    @ColumnInfo(name = "completedTasks") val completedTasks: Int,
)
