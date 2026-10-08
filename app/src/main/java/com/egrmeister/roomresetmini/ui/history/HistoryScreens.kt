package com.egrmeister.roomresetmini.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.roomresetmini.domain.progress.ZoneState
import com.egrmeister.roomresetmini.domain.progress.Progress
import com.egrmeister.roomresetmini.domain.sessions.HistoryEntry
import com.egrmeister.roomresetmini.domain.sessions.ResultKind
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.theme.CheckBadge
import com.egrmeister.roomresetmini.ui.theme.ConfirmDialog
import com.egrmeister.roomresetmini.ui.theme.EmptyState
import com.egrmeister.roomresetmini.ui.theme.RoomColors
import com.egrmeister.roomresetmini.ui.theme.SimpleTopBar
import com.egrmeister.roomresetmini.ui.theme.formatMinutes
import com.egrmeister.roomresetmini.ui.theme.iconRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDateTime(millis: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            SimpleTopBar(title = "History", onBack = onBack) {
                if (state.entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(Modifier.padding(24.dp)) }
            state.entries.isEmpty() -> EmptyState(
                "Your saved resets will appear here.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpen = { onOpen(entry.id) },
                        onDelete = { pendingDelete = entry.id },
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            text = "All saved resets will be deleted from this device. Rooms are not affected.",
            confirmLabel = "Clear history",
            onConfirm = {
                confirmClear = false
                viewModel.clear()
            },
            onDismiss = { confirmClear = false },
        )
    }
    pendingDelete?.let { id ->
        ConfirmDialog(
            title = "Delete this entry?",
            text = "This saved reset will be removed from history.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                viewModel.delete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatDateTime(entry.savedAtMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.templateName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatMinutes(entry.durationMillis)} · ${entry.completedTasks}/${entry.totalTasks} tasks",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CheckBadge(
                    done = entry.result == ResultKind.ALL_CHECKED,
                    doneText = ResultKind.ALL_CHECKED.label,
                    openText = ResultKind.PARTIAL.label,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry from ${formatDateTime(entry.savedAtMillis)}")
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(viewModel: SessionDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            SimpleTopBar(title = "Saved reset", onBack = onBack) {
                if (state.session != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val session = state.session
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    state.loading -> CircularProgressIndicator()
                    session == null -> Text("This entry was deleted.")
                    else -> {
                        val progress = session.progress
                        Text(session.templateName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatDateTime(session.savedAtMillis ?: session.startedAtMillis)} · ${formatMinutes(session.durationMillis)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${progress.completedTasks} of ${progress.totalTasks} tasks · ${session.result.label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ZoneType.entries.forEach { zone ->
                            val zp = Progress.zone(zone, session.tasks)
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
                                            if (zp.total == 0) "No tasks" else "${zp.completed}/${zp.total} · ${zp.state.label}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (zp.state == ZoneState.DONE) RoomColors.SageDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    session.tasksIn(zone).forEach { task ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 32.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(task.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            CheckBadge(done = task.completed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            text = "This saved reset will be removed from history.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
