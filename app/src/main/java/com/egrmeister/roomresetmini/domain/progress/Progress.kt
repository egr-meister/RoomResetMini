package com.egrmeister.roomresetmini.domain.progress

import com.egrmeister.roomresetmini.domain.templates.ZoneType

/** Anything that belongs to a zone and can be checked. */
interface ZoneItem {
    val zone: ZoneType
    val completed: Boolean
}

enum class ZoneState(val label: String) {
    NO_TASKS("No tasks"),
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    DONE("Done"),
}

data class ZoneProgress(
    val zone: ZoneType,
    val completed: Int,
    val total: Int,
) {
    val state: ZoneState
        get() = when {
            total == 0 -> ZoneState.NO_TASKS
            completed >= total -> ZoneState.DONE
            completed == 0 -> ZoneState.NOT_STARTED
            else -> ZoneState.IN_PROGRESS
        }
}

data class SessionProgress(
    val completedTasks: Int,
    val totalTasks: Int,
    val completedZones: Int,
    val nonEmptyZones: Int,
) {
    /** completed tasks / total tasks — every task weighs the same, zones are NOT weighted equally. */
    val fraction: Float get() = if (totalTasks == 0) 0f else completedTasks.toFloat() / totalTasks

    val allChecked: Boolean get() = totalTasks > 0 && completedTasks == totalTasks
    val remainingTasks: Int get() = totalTasks - completedTasks
}

object Progress {
    fun zone(zone: ZoneType, items: List<ZoneItem>): ZoneProgress {
        val inZone = items.filter { it.zone == zone }
        return ZoneProgress(zone, inZone.count { it.completed }, inZone.size)
    }

    fun zones(items: List<ZoneItem>): List<ZoneProgress> = ZoneType.entries.map { zone(it, items) }

    /** Zones without tasks are excluded from totals. */
    fun session(items: List<ZoneItem>): SessionProgress {
        val zones = zones(items).filter { it.total > 0 }
        return SessionProgress(
            completedTasks = items.count { it.completed },
            totalTasks = items.size,
            completedZones = zones.count { it.state == ZoneState.DONE },
            nonEmptyZones = zones.size,
        )
    }
}
