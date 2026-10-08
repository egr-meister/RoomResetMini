package com.egrmeister.roomresetmini.ui.room

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.egrmeister.roomresetmini.domain.progress.Progress
import com.egrmeister.roomresetmini.domain.progress.ZoneItem
import com.egrmeister.roomresetmini.domain.progress.ZoneProgress
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.templates.ZoneType
import com.egrmeister.roomresetmini.domain.timer.TimerStatus
import com.egrmeister.roomresetmini.ui.theme.ConfirmDialog
import com.egrmeister.roomresetmini.ui.theme.EmptyState

@Composable
fun RoomScreen(
    state: RoomUiState,
    viewModel: RoomViewModel,
    onOpenZone: (ZoneType) -> Unit,
    onFinish: () -> Unit,
    onHistory: () -> Unit,
    onEditRooms: () -> Unit,
    onCreateRoom: () -> Unit,
    onSettings: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 840.dp
            val scroll = rememberScrollState()
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TemplateSelector(state, viewModel)
                        PlanArea(state, viewModel, onOpenZone, onCreateRoom, onFinish)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SessionArea(state, viewModel, onFinish)
                        UtilityArea(state, viewModel, onHistory, onEditRooms, onSettings)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TemplateSelector(state, viewModel)
                        PlanArea(state, viewModel, onOpenZone, onCreateRoom, onFinish)
                        SessionArea(state, viewModel, onFinish)
                        UtilityArea(state, viewModel, onHistory, onEditRooms, onSettings)
                    }
                }
            }
        }
    }

    if (state.unfinishedPrompt && state.session != null) {
        UnfinishedSessionDialog(
            templateName = state.session.templateName,
            onResume = viewModel::dismissUnfinishedPrompt,
            onFinish = {
                viewModel.dismissUnfinishedPrompt()
                onFinish()
            },
            onDiscard = { confirmDiscard = true },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard this reset?",
            text = "The unfinished session and its checked tasks will be removed. History is not affected.",
            confirmLabel = "Discard",
            dismissLabel = "Keep",
            onConfirm = {
                confirmDiscard = false
                viewModel.discardActive()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSelector(state: RoomUiState, viewModel: RoomViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Room",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        val session = state.session
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (session != null) {
                // Template choice is locked while a session runs; it uses its own snapshot.
                FilterChip(
                    selected = true,
                    onClick = {},
                    enabled = false,
                    label = { Text(session.templateName) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            } else {
                state.usableTemplates.forEach { template ->
                    FilterChip(
                        selected = template.id == state.selectedTemplate?.id,
                        onClick = { viewModel.selectTemplate(template.id) },
                        label = { Text(template.name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanArea(
    state: RoomUiState,
    viewModel: RoomViewModel,
    onOpenZone: (ZoneType) -> Unit,
    onCreateRoom: () -> Unit,
    onFinish: () -> Unit,
) {
    val session = state.session
    val selected = state.selectedTemplate
    val reduced = state.settings.reducedAnimation
    when {
        session != null -> {
            if (session.timer.status == TimerStatus.EXPIRED) {
                ExpiredBanner(
                    onSave = onFinish,
                    onContinue = viewModel::continueWithoutTimer,
                )
            }
            if (state.settings.restartNotice) {
                RestartBanner(onDismiss = viewModel::dismissRestartNotice)
            }
            RoomPlan(
                zones = Progress.zones(session.tasks),
                listView = state.settings.listView,
                reducedMotion = reduced,
                onZone = onOpenZone,
            )
            SessionProgressLine(session.progress)
        }
        state.templates.isEmpty() -> {
            EmptyState("No templates yet. Create a room.") {
                Button(onClick = onCreateRoom, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create a room") }
            }
        }
        selected == null -> {
            EmptyState("Choose a room to start.")
        }
        else -> {
            RoomPlan(
                zones = previewZones(selected),
                listView = state.settings.listView,
                reducedMotion = reduced,
                onZone = onOpenZone,
            )
        }
    }
}

@Composable
private fun SessionArea(state: RoomUiState, viewModel: RoomViewModel, onFinish: () -> Unit) {
    val session = state.session
    if (session == null) {
        StartControls(
            durationMinutes = state.durationMinutes,
            enabled = state.selectedTemplate != null,
            onDuration = viewModel::selectDuration,
            onStart = viewModel::startReset,
        )
    } else {
        TimerStrip(
            status = session.timer.status,
            remainingMillis = state.remainingMillis,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onFinish = onFinish,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UtilityArea(
    state: RoomUiState,
    viewModel: RoomViewModel,
    onHistory: () -> Unit,
    onEditRooms: () -> Unit,
    onSettings: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("History") }
        TextButton(onClick = onEditRooms, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit rooms") }
        TextButton(
            onClick = { viewModel.setListView(!state.settings.listView) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (state.settings.listView) "Plan view" else "List view") }
        TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
    }
}

@Composable
private fun UnfinishedSessionDialog(
    templateName: String,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onResume,
        title = { Text("Unfinished reset") },
        text = { Text("You have an unfinished “$templateName” reset. Its checked tasks are kept.") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onResume) { Text("Resume", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onFinish) { Text("Finish and save") }
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        },
    )
}

private data class PreviewItem(override val zone: ZoneType, override val completed: Boolean = false) : ZoneItem

/** Before a session the plan shows the selected template's task counts, all "Not started". */
private fun previewZones(template: RoomTemplate): List<ZoneProgress> =
    Progress.zones(template.tasks.map { PreviewItem(it.zone) })
