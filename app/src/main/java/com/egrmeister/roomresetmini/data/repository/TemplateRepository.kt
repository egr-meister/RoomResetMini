package com.egrmeister.roomresetmini.data.repository

import com.egrmeister.roomresetmini.data.local.RoomTemplateEntity
import com.egrmeister.roomresetmini.data.local.TemplateDao
import com.egrmeister.roomresetmini.data.local.TemplateTaskEntity
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.StarterTemplates
import com.egrmeister.roomresetmini.domain.templates.TemplateDraft
import com.egrmeister.roomresetmini.domain.templates.TemplateNames
import com.egrmeister.roomresetmini.domain.templates.TemplateRules
import com.egrmeister.roomresetmini.domain.templates.TemplateTask
import com.egrmeister.roomresetmini.domain.templates.TemplateValidator
import com.egrmeister.roomresetmini.domain.templates.ValidationResult
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.domain.timer.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TemplateSaveResult {
    data class Saved(val id: Long) : TemplateSaveResult
    data class Invalid(val validation: ValidationResult) : TemplateSaveResult
    data object LimitReached : TemplateSaveResult
    data object NotFound : TemplateSaveResult
}

data class RestoreResult(val added: Int, val skippedForLimit: Int)

class TemplateRepository(
    private val dao: TemplateDao,
    private val settings: SettingsRepository,
    private val wallClock: WallClock,
) {
    /** Serialises template writes so limit and duplicate-name checks cannot race. */
    private val mutex = Mutex()

    val templates: Flow<List<RoomTemplate>> =
        combine(dao.observeTemplates(), dao.observeAllTasks()) { templates, tasks ->
            val byTemplate = tasks.groupBy { it.templateId }
            templates.map { it.toDomain(byTemplate[it.id].orEmpty()) }
        }

    /** Seeds starter templates exactly once; deleted templates are never recreated on launch. */
    suspend fun ensureSeeded() = mutex.withLock {
        if (settings.isSeeded()) return@withLock
        if (dao.count() == 0) {
            val ids = insertStartersLocked(existingNames = emptyList(), slots = TemplateRules.MAX_TEMPLATES)
            ids.firstOrNull()?.let { settings.setSelectedTemplate(it) }
        }
        settings.markSeeded()
    }

    suspend fun getAllOnce(): List<RoomTemplate> = templates.first()

    suspend fun getTemplate(id: Long): RoomTemplate? {
        val entity = dao.getTemplate(id) ?: return null
        return entity.toDomain(dao.getTasks(id))
    }

    suspend fun create(draft: TemplateDraft): TemplateSaveResult = mutex.withLock {
        val existing = dao.getTemplates()
        if (existing.size >= TemplateRules.MAX_TEMPLATES) return@withLock TemplateSaveResult.LimitReached
        val validation = TemplateValidator.validate(draft, existing.map { it.name })
        if (!validation.isValid) return@withLock TemplateSaveResult.Invalid(validation)
        TemplateSaveResult.Saved(insertLocked(draft.normalized(), dao.maxDisplayOrder() + 1))
    }

    suspend fun update(id: Long, draft: TemplateDraft): TemplateSaveResult = mutex.withLock {
        val current = dao.getTemplate(id) ?: return@withLock TemplateSaveResult.NotFound
        val others = dao.getTemplates().filter { it.id != id }.map { it.name }
        val validation = TemplateValidator.validate(draft, others)
        if (!validation.isValid) return@withLock TemplateSaveResult.Invalid(validation)
        val clean = draft.normalized()
        dao.replaceTemplate(
            current.copy(name = clean.name, updatedAt = wallClock.currentTimeMillis()),
            clean.toTaskEntities(),
        )
        TemplateSaveResult.Saved(id)
    }

    suspend fun duplicate(id: Long): TemplateSaveResult = mutex.withLock {
        val existing = dao.getTemplates()
        if (existing.size >= TemplateRules.MAX_TEMPLATES) return@withLock TemplateSaveResult.LimitReached
        val source = dao.getTemplate(id) ?: return@withLock TemplateSaveResult.NotFound
        val draft = TemplateDraft.from(source.toDomain(dao.getTasks(id)))
        val name = TemplateNames.unique("${source.name} copy", existing.map { it.name })
        TemplateSaveResult.Saved(insertLocked(draft.copy(name = name), dao.maxDisplayOrder() + 1))
    }

    suspend fun delete(id: Long) = mutex.withLock {
        dao.deleteTemplate(id)
        if (settings.current().selectedTemplateId == id) settings.setSelectedTemplate(null)
    }

    /** Adds starter templates with unique names, keeping every existing template. */
    suspend fun restoreStarters(): RestoreResult = mutex.withLock {
        val existing = dao.getTemplates()
        val slots = TemplateRules.MAX_TEMPLATES - existing.size
        val ids = insertStartersLocked(existing.map { it.name }, slots)
        RestoreResult(added = ids.size, skippedForLimit = StarterTemplates.all.size - ids.size)
    }

    /** Used by Clear All Data (inside the caller's transaction): wipe and re-insert starters. */
    suspend fun replaceAllWithStarters(): Long? {
        dao.deleteAllTasks()
        dao.deleteAllTemplates()
        return insertStartersLocked(emptyList(), TemplateRules.MAX_TEMPLATES).firstOrNull()
    }

    private suspend fun insertStartersLocked(existingNames: List<String>, slots: Int): List<Long> {
        val names = existingNames.toMutableList()
        val ids = mutableListOf<Long>()
        var order = dao.maxDisplayOrder() + 1
        for (starter in StarterTemplates.all) {
            if (ids.size >= slots) break
            val name = TemplateNames.unique(starter.name, names)
            ids += insertLocked(starter.copy(name = name), order++)
            names += name
        }
        return ids
    }

    private suspend fun insertLocked(draft: TemplateDraft, displayOrder: Int): Long {
        val now = wallClock.currentTimeMillis()
        return dao.insertWithTasks(
            RoomTemplateEntity(name = draft.name, displayOrder = displayOrder, createdAt = now, updatedAt = now),
            draft.toTaskEntities(),
        )
    }
}

private fun TemplateDraft.toTaskEntities(): List<TemplateTaskEntity> =
    ZoneType.entries.flatMap { zone ->
        labels(zone).mapIndexed { index, label ->
            TemplateTaskEntity(templateId = 0, zoneType = zone.name, label = label.trim(), displayOrder = index)
        }
    }

private fun RoomTemplateEntity.toDomain(tasks: List<TemplateTaskEntity>): RoomTemplate = RoomTemplate(
    id = id,
    name = name,
    displayOrder = displayOrder,
    tasks = tasks.mapNotNull { t ->
        ZoneType.fromKey(t.zoneType)?.let { zone -> TemplateTask(t.id, zone, t.label, t.displayOrder) }
    },
)
