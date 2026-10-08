package com.egrmeister.roomresetmini.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.roomresetmini.data.repository.TemplateRepository
import com.egrmeister.roomresetmini.data.repository.TemplateSaveResult
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.TaskRef
import com.egrmeister.roomresetmini.domain.templates.TemplateDraft
import com.egrmeister.roomresetmini.domain.templates.TemplateRules
import com.egrmeister.roomresetmini.domain.templates.ValidationResult
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- Room (template) list ---------------------------------------------------------------------

data class TemplatesUiState(
    val loading: Boolean = true,
    val templates: List<RoomTemplate> = emptyList(),
    val message: String? = null,
) {
    val canCreate: Boolean get() = templates.size < TemplateRules.MAX_TEMPLATES
}

class TemplatesViewModel(private val repository: TemplateRepository) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<TemplatesUiState> = combine(repository.templates, message) { list, msg ->
        TemplatesUiState(loading = false, templates = list, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    fun duplicate(id: Long) {
        viewModelScope.launch {
            message.value = when (repository.duplicate(id)) {
                is TemplateSaveResult.Saved -> "Room duplicated."
                TemplateSaveResult.LimitReached -> "You can keep up to ${TemplateRules.MAX_TEMPLATES} rooms."
                else -> "This room no longer exists."
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            message.value = "Room deleted. Saved history is unchanged."
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}

// ---- Editor -----------------------------------------------------------------------------------

data class DraftTask(val key: Long, val label: String)

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val zones: Map<ZoneType, List<DraftTask>> = ZoneType.entries.associateWith { emptyList() },
    val validation: ValidationResult = ValidationResult(),
    val showErrors: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val limitReached: Boolean = false,
) {
    fun tasks(zone: ZoneType): List<DraftTask> = zones[zone].orEmpty()
    fun errorFor(zone: ZoneType, key: Long): String? {
        val index = tasks(zone).indexOfFirst { it.key == key }
        return if (showErrors) validation.taskErrors[TaskRef(zone, index)] else null
    }
}

/**
 * Edits a copy of a template. Nothing is written until Save; Cancel/Back asks before discarding.
 * Template changes affect future sessions only — an active session keeps its own snapshot.
 */
class EditorViewModel(
    private val templateId: Long?,
    private val repository: TemplateRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState(isNew = templateId == null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private var nextKey = 1L
    private var otherNames: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val all = repository.getAllOnce()
            otherNames = all.filter { it.id != templateId }.map { it.name }
            if (templateId == null) {
                _state.update {
                    it.copy(loading = false, limitReached = all.size >= TemplateRules.MAX_TEMPLATES).revalidated()
                }
            } else {
                val template = repository.getTemplate(templateId)
                if (template == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    val draft = TemplateDraft.from(template)
                    _state.update {
                        it.copy(
                            loading = false,
                            name = draft.name,
                            zones = ZoneType.entries.associateWith { zone -> draft.labels(zone).map { label -> DraftTask(nextKey++, label) } },
                        ).revalidated()
                    }
                }
            }
        }
    }

    fun setName(value: String) = edit { it.copy(name = value.take(TemplateRules.NAME_MAX + 10)) }

    fun addTask(zone: ZoneType) = edit { s ->
        val tasks = s.tasks(zone)
        if (tasks.size >= TemplateRules.MAX_TASKS_PER_ZONE) s
        else s.copy(zones = s.zones + (zone to tasks + DraftTask(nextKey++, "")))
    }

    fun updateTask(zone: ZoneType, key: Long, value: String) = edit { s ->
        s.copy(zones = s.zones + (zone to s.tasks(zone).map { if (it.key == key) it.copy(label = value.take(TemplateRules.LABEL_MAX + 10)) else it }))
    }

    fun removeTask(zone: ZoneType, key: Long) = edit { s ->
        s.copy(zones = s.zones + (zone to s.tasks(zone).filterNot { it.key == key }))
    }

    fun moveTask(zone: ZoneType, key: Long, up: Boolean) = edit { s ->
        val list = s.tasks(zone).toMutableList()
        val index = list.indexOfFirst { it.key == key }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) s
        else {
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(zones = s.zones + (zone to list.toList()))
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.saving) return
        val validated = current.revalidated().copy(showErrors = true)
        _state.value = validated
        if (!validated.validation.isValid) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = validated.toDraft()
            val result = if (templateId == null) repository.create(draft) else repository.update(templateId, draft)
            when (result) {
                is TemplateSaveResult.Saved -> {
                    _state.update { it.copy(saving = false, dirty = false) }
                    onSaved()
                }
                is TemplateSaveResult.Invalid -> _state.update {
                    it.copy(saving = false, validation = result.validation, showErrors = true)
                }
                TemplateSaveResult.LimitReached -> _state.update { it.copy(saving = false, limitReached = true) }
                TemplateSaveResult.NotFound -> _state.update { it.copy(saving = false, notFound = true) }
            }
        }
    }

    private fun edit(transform: (EditorUiState) -> EditorUiState) {
        _state.update { transform(it).copy(dirty = true).revalidated() }
    }

    private fun EditorUiState.toDraft(): TemplateDraft =
        TemplateDraft(name, ZoneType.entries.associateWith { zone -> tasks(zone).map { it.label } })

    private fun EditorUiState.revalidated(): EditorUiState =
        copy(validation = com.egrmeister.roomresetmini.domain.templates.TemplateValidator.validate(toDraft(), otherNames))
}
