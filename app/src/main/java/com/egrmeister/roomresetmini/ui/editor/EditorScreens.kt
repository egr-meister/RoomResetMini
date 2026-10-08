package com.egrmeister.roomresetmini.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.TemplateRules
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.theme.ConfirmDialog
import com.egrmeister.roomresetmini.ui.theme.EmptyState
import com.egrmeister.roomresetmini.ui.theme.SimpleTopBar
import com.egrmeister.roomresetmini.ui.theme.iconRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel,
    activeTemplateName: String?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = "Rooms", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = onCreate,
                        enabled = state.canCreate,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create room")
                    }
                    Text(
                        if (state.canCreate) "Up to ${TemplateRules.MAX_TEMPLATES} rooms. Changes apply to future resets."
                        else "You can keep up to ${TemplateRules.MAX_TEMPLATES} rooms. Delete one to add another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activeTemplateName != null) {
                        Text(
                            "The unfinished “$activeTemplateName” reset keeps its own copy of the tasks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!state.loading && state.templates.isEmpty()) {
                item { EmptyState("No templates yet. Create a room.") }
            }
            items(state.templates, key = { it.id }) { template ->
                TemplateRow(
                    template = template,
                    canDuplicate = state.canCreate,
                    onEdit = { onEdit(template.id) },
                    onDuplicate = { viewModel.duplicate(template.id) },
                    onDelete = { pendingDelete = template.id },
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }

    pendingDelete?.let { id ->
        val name = state.templates.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDialog(
            title = "Delete “$name”?",
            text = "The room and its tasks will be removed. Saved history keeps its own copy.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                viewModel.delete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateRow(
    template: RoomTemplate,
    canDuplicate: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (template.taskCount == 1) "1 task" else "${template.taskCount} tasks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                TextButton(onClick = onDuplicate, enabled = canDuplicate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Duplicate") }
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete") }
            }
        }
    }
}

@Composable
fun TemplateEditorScreen(
    viewModel: EditorViewModel,
    sessionActive: Boolean,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onDone()
        }
    }

    // Predictive-back compatible: only intercepts Back when there are unsaved changes.
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            SimpleTopBar(title = if (state.isNew) "New room" else "Edit room", onBack = requestClose) {
                TextButton(onClick = { viewModel.save(onDone) }, enabled = !state.saving && !state.loading) { Text("Save") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.notFound -> {
                        Text("This room no longer exists.")
                        Button(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                    }
                    state.isNew && state.limitReached -> {
                        Text("You can keep up to ${TemplateRules.MAX_TEMPLATES} rooms. Delete one to add another.")
                        Button(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                    }
                    else -> EditorForm(state, viewModel, sessionActive, onCancel = requestClose, onSave = { viewModel.save(onDone) })
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to this room have not been saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                confirmDiscard = false
                onDone()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun EditorForm(
    state: EditorUiState,
    viewModel: EditorViewModel,
    sessionActive: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val nameError = if (state.showErrors) state.validation.nameError else null
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::setName,
        label = { Text("Room name") },
        singleLine = true,
        isError = nameError != null,
        supportingText = { Text(nameError ?: "${state.name.trim().length}/${TemplateRules.NAME_MAX}") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    if (sessionActive) {
        Text(
            "Changes apply to future resets. The unfinished reset keeps its own task list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val formError = if (state.showErrors) state.validation.formError else null
    if (formError != null) {
        Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    ZoneType.entries.forEach { zone ->
        ZoneEditor(zone, state, viewModel)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCancel, modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)) { Text("Cancel") }
        Button(onClick = onSave, enabled = !state.saving, modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)) { Text("Save") }
    }
}

@Composable
private fun ZoneEditor(zone: ZoneType, state: EditorUiState, viewModel: EditorViewModel) {
    val tasks = state.tasks(zone)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(zone.iconRes()), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(zone.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${tasks.size}/${TemplateRules.MAX_TASKS_PER_ZONE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tasks.isEmpty()) {
                Text(
                    "No tasks in this zone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tasks.forEachIndexed { index, task ->
                val error = state.errorFor(zone, task.key)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = task.label,
                        onValueChange = { viewModel.updateTask(zone, task.key, it) },
                        label = { Text("Task ${index + 1}") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = if (error != null) { { Text(error) } } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Column {
                        IconButton(onClick = { viewModel.moveTask(zone, task.key, up = true) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${task.label.ifBlank { "task" }} up")
                        }
                        IconButton(onClick = { viewModel.moveTask(zone, task.key, up = false) }, enabled = index < tasks.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${task.label.ifBlank { "task" }} down")
                        }
                    }
                    IconButton(onClick = { viewModel.removeTask(zone, task.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${task.label.ifBlank { "task" }}")
                    }
                }
            }
            TextButton(
                onClick = { viewModel.addTask(zone) },
                enabled = tasks.size < TemplateRules.MAX_TASKS_PER_ZONE,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add task")
            }
        }
    }
}
