package com.egrmeister.roomresetmini.ui.zone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.egrmeister.roomresetmini.domain.progress.Progress
import com.egrmeister.roomresetmini.domain.progress.ZoneState
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.room.RoomUiState
import com.egrmeister.roomresetmini.ui.room.RoomViewModel
import com.egrmeister.roomresetmini.ui.room.TimerStrip
import com.egrmeister.roomresetmini.ui.theme.CheckBadge
import com.egrmeister.roomresetmini.ui.theme.EmptyState
import com.egrmeister.roomresetmini.ui.theme.SimpleTopBar
import com.egrmeister.roomresetmini.ui.theme.iconRes

/**
 * A zone's checklist. Tapping a task toggles it and persists immediately; the zone is Done only
 * when every task is checked. Back returns to the room plan without touching the session.
 */
@Composable
fun ZoneScreen(
    zone: ZoneType,
    state: RoomUiState,
    viewModel: RoomViewModel,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onEditTemplate: (Long) -> Unit,
) {
    val session = state.session
    Scaffold(
        topBar = { SimpleTopBar(title = zone.label, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (session != null) {
                TimerStrip(
                    status = session.timer.status,
                    remainingMillis = state.remainingMillis,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onFinish = onFinish,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (session != null) {
                    val tasks = session.tasksIn(zone)
                    val progress = Progress.zone(zone, session.tasks)
                    ZoneSummary(zone, progress.completed, progress.total, progress.state)
                    if (tasks.isEmpty()) {
                        EmptyState("No tasks in this zone.")
                    } else {
                        tasks.forEach { task ->
                            TaskRow(
                                label = task.label,
                                checked = task.completed,
                                onToggle = { viewModel.toggleTask(task.id, !task.completed) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                } else {
                    val template = state.selectedTemplate
                    val tasks = template?.tasksIn(zone).orEmpty()
                    ZoneSummary(zone, 0, tasks.size, if (tasks.isEmpty()) ZoneState.NO_TASKS else ZoneState.NOT_STARTED)
                    if (tasks.isEmpty()) {
                        EmptyState("No tasks in this zone.")
                    } else {
                        Text(
                            "Start a reset to check these tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        tasks.forEach { task ->
                            Text(
                                "• ${task.label}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                    if (template != null) {
                        OutlinedButton(
                            onClick = { onEditTemplate(template.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Edit tasks") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneSummary(zone: ZoneType, completed: Int, total: Int, state: ZoneState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(painterResource(zone.iconRes()), contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (total == 0) "No tasks" else "$completed of $total tasks",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state == ZoneState.DONE) {
                CheckBadge(done = true, doneText = "Done")
            } else {
                Text(state.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaskRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (checked) "Done" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
