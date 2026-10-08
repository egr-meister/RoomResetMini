package com.egrmeister.roomresetmini.domain.templates

/** The five fixed zone types. Order here is the canonical display/snapshot order. */
enum class ZoneType(val label: String) {
    DESK("Desk"),
    FLOOR("Floor"),
    CLOTHES("Clothes"),
    TRASH("Trash"),
    SHELF("Shelf");

    companion object {
        fun fromKey(key: String): ZoneType? = entries.firstOrNull { it.name == key }
    }
}

data class TemplateTask(
    val id: Long,
    val zone: ZoneType,
    val label: String,
    val order: Int,
)

data class RoomTemplate(
    val id: Long,
    val name: String,
    val displayOrder: Int,
    val tasks: List<TemplateTask>,
) {
    val taskCount: Int get() = tasks.size

    /** A template needs at least one task to start a session. */
    val isUsable: Boolean get() = tasks.isNotEmpty()

    fun tasksIn(zone: ZoneType): List<TemplateTask> =
        tasks.filter { it.zone == zone }.sortedWith(compareBy<TemplateTask> { it.order }.thenBy { it.id })
}

/** Editable form of a template: a name plus ordered task labels per zone. */
data class TemplateDraft(
    val name: String,
    val zones: Map<ZoneType, List<String>>,
) {
    fun labels(zone: ZoneType): List<String> = zones[zone].orEmpty()

    val totalTasks: Int get() = ZoneType.entries.sumOf { labels(it).size }

    fun normalized(): TemplateDraft = TemplateDraft(
        name = name.trim(),
        zones = ZoneType.entries.associateWith { zone -> labels(zone).map { it.trim() } },
    )

    companion object {
        fun from(template: RoomTemplate): TemplateDraft = TemplateDraft(
            name = template.name,
            zones = ZoneType.entries.associateWith { zone -> template.tasksIn(zone).map { it.label } },
        )

        fun of(name: String, vararg zones: Pair<ZoneType, List<String>>): TemplateDraft =
            TemplateDraft(name, ZoneType.entries.associateWith { zone -> zones.toMap()[zone].orEmpty() })
    }
}

object TemplateRules {
    const val MAX_TEMPLATES = 12
    const val NAME_MAX = 30
    const val LABEL_MAX = 80
    const val MAX_TASKS_PER_ZONE = 10
}

data class TaskRef(val zone: ZoneType, val index: Int)

data class ValidationResult(
    val nameError: String? = null,
    val taskErrors: Map<TaskRef, String> = emptyMap(),
    val formError: String? = null,
) {
    val isValid: Boolean get() = nameError == null && taskErrors.isEmpty() && formError == null
}

object TemplateValidator {
    /**
     * @param otherNames names of all OTHER templates (exclude the template being edited).
     */
    fun validate(draft: TemplateDraft, otherNames: Collection<String>): ValidationResult {
        val name = draft.name.trim()
        val nameError = when {
            name.isEmpty() -> "Enter a room name."
            name.length > TemplateRules.NAME_MAX -> "Use up to ${TemplateRules.NAME_MAX} characters."
            otherNames.any { it.trim().equals(name, ignoreCase = true) } -> "A room with this name already exists."
            else -> null
        }
        val taskErrors = mutableMapOf<TaskRef, String>()
        var formError: String? = null
        ZoneType.entries.forEach { zone ->
            val labels = draft.labels(zone)
            if (labels.size > TemplateRules.MAX_TASKS_PER_ZONE) {
                formError = "Each zone can have up to ${TemplateRules.MAX_TASKS_PER_ZONE} tasks."
            }
            labels.forEachIndexed { index, raw ->
                val label = raw.trim()
                when {
                    label.isEmpty() -> taskErrors[TaskRef(zone, index)] = "Enter a task or remove it."
                    label.length > TemplateRules.LABEL_MAX ->
                        taskErrors[TaskRef(zone, index)] = "Use up to ${TemplateRules.LABEL_MAX} characters."
                }
            }
        }
        if (formError == null && draft.totalTasks == 0) {
            formError = "Add at least one task."
        }
        return ValidationResult(nameError, taskErrors.toMap(), formError)
    }
}

object TemplateNames {
    /** Returns [base] or "base 2", "base 3"… so the name is unique (ignoring case) and ≤ 30 chars. */
    fun unique(base: String, existing: Collection<String>): String {
        val trimmed = base.trim().take(TemplateRules.NAME_MAX).trimEnd().ifEmpty { "Room" }
        fun taken(candidate: String) = existing.any { it.trim().equals(candidate, ignoreCase = true) }
        if (!taken(trimmed)) return trimmed
        var i = 2
        while (true) {
            val suffix = " $i"
            val candidate = trimmed.take(TemplateRules.NAME_MAX - suffix.length).trimEnd() + suffix
            if (!taken(candidate)) return candidate
            i++
        }
    }
}

object StarterTemplates {
    val all: List<TemplateDraft> = listOf(
        TemplateDraft.of(
            "Bedroom",
            ZoneType.DESK to listOf("Put stationery away", "Clear the surface"),
            ZoneType.FLOOR to listOf("Return loose items", "Clear the walking area"),
            ZoneType.CLOTHES to listOf("Put clean clothes away", "Place laundry in the basket"),
            ZoneType.TRASH to listOf("Collect ordinary rubbish", "Empty the small bin if needed"),
            ZoneType.SHELF to listOf("Return books", "Straighten small items"),
        ),
        TemplateDraft.of(
            "Study Space",
            ZoneType.DESK to listOf("Stack papers", "Put supplies away"),
            ZoneType.FLOOR to listOf("Return items to storage"),
            ZoneType.CLOTHES to listOf("Put bags and jackets away"),
            ZoneType.TRASH to listOf("Collect ordinary rubbish"),
            ZoneType.SHELF to listOf("Return books and folders"),
        ),
        TemplateDraft.of(
            "Living Room",
            ZoneType.DESK to listOf("Clear the table"),
            ZoneType.FLOOR to listOf("Put loose items away"),
            ZoneType.CLOTHES to listOf("Put blankets and clothing away"),
            ZoneType.TRASH to listOf("Collect ordinary rubbish"),
            ZoneType.SHELF to listOf("Return books and small objects"),
        ),
    )
}
