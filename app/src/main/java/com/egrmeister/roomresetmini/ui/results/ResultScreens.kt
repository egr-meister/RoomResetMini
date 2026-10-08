package com.egrmeister.roomresetmini.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.roomresetmini.domain.sessions.ResetSession
import com.egrmeister.roomresetmini.domain.sessions.savedMessage
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.ui.history.SessionDetailViewModel
import com.egrmeister.roomresetmini.ui.room.RoomUiState
import com.egrmeister.roomresetmini.ui.room.RoomViewModel
import com.egrmeister.roomresetmini.ui.theme.ConfirmDialog
import com.egrmeister.roomresetmini.ui.theme.SectionTitle
import com.egrmeister.roomresetmini.ui.theme.SimpleTopBar
import com.egrmeister.roomresetmini.ui.theme.formatMinutes

/** Finish review: shown at any time; Save Session or Continue Reset. */
@Composable
fun ReviewScreen(
    state: RoomUiState,
    viewModel: RoomViewModel,
    onContinue: () -> Unit,
    onSaved: (Long) -> Unit,
    onDiscarded: () -> Unit,
) {
    val session = state.session
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { SimpleTopBar(title = "Review reset", onBack = onContinue) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (session == null) {
            CenteredColumn(Modifier.padding(padding)) {
                if (state.loading || state.saving) {
                    CircularProgressIndicator()
                } else {
                    Text("There is no unfinished reset.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onDiscarded, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to Rooms") }
                }
            }
            return@Scaffold
        }
        val progress = session.progress
        CenteredColumn(Modifier.padding(padding)) {
            Text(session.templateName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            SummaryLine("Selected duration", formatMinutes(session.durationMillis))
            SummaryLine("Tasks", "${progress.completedTasks} of ${progress.totalTasks} completed")
            SummaryLine("Zones", "${progress.completedZones} of ${progress.nonEmptyZones} done")

            val remaining = ZoneType.entries.associateWith { zone -> session.tasksIn(zone).filter { !it.completed } }
                .filterValues { it.isNotEmpty() }
            if (remaining.isEmpty()) {
                Text("All tasks checked.", style = MaterialTheme.typography.titleMedium)
            } else {
                SectionTitle("Remaining tasks (${progress.remainingTasks})")
                remaining.forEach { (zone, tasks) ->
                    Text(zone.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    tasks.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (progress.completedTasks == 0) {
                Text(
                    "No tasks are checked yet. You can still save this session, or discard it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { viewModel.saveActive(onSaved) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Save Session") }
            OutlinedButton(
                onClick = onContinue,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Continue Reset") }
            if (progress.completedTasks == 0) {
                TextButton(
                    onClick = { confirmDiscard = true },
                    enabled = !state.saving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Discard reset") }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard this reset?",
            text = "Nothing will be added to history.",
            confirmLabel = "Discard",
            dismissLabel = "Keep",
            onConfirm = {
                confirmDiscard = false
                viewModel.discardActive()
                onDiscarded()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** After saving: outcome message, Back to Rooms, Start Another Reset. */
@Composable
fun ResultScreen(
    viewModel: SessionDetailViewModel,
    onBackToRooms: () -> Unit,
    onStartAnother: (ResetSession?) -> Unit,
) {
    val detail by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SimpleTopBar(title = "Reset saved", onBack = onBackToRooms) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        CenteredColumn(Modifier.padding(padding)) {
            val session = detail.session
            when {
                detail.loading -> CircularProgressIndicator()
                session == null -> Text("This reset is no longer in history.")
                else -> {
                    val progress = session.progress
                    Text(
                        session.result.savedMessage(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    SummaryLine("Room", session.templateName)
                    SummaryLine("Selected duration", formatMinutes(session.durationMillis))
                    SummaryLine("Tasks", "${progress.completedTasks} of ${progress.totalTasks} completed")
                    SummaryLine("Zones", "${progress.completedZones} of ${progress.nonEmptyZones} done")
                }
            }
            Button(
                onClick = onBackToRooms,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Back to Rooms") }
            OutlinedButton(
                onClick = { onStartAnother(detail.session) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Start Another Reset") }
        }
    }
}

@Composable
internal fun CenteredColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
internal fun SummaryLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
