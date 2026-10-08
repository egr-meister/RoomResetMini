package com.egrmeister.roomresetmini.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.roomresetmini.data.repository.AppSettings
import com.egrmeister.roomresetmini.data.repository.DataResetter
import com.egrmeister.roomresetmini.data.repository.SessionRepository
import com.egrmeister.roomresetmini.data.repository.SettingsRepository
import com.egrmeister.roomresetmini.data.repository.TemplateRepository
import com.egrmeister.roomresetmini.domain.sessions.Durations
import com.egrmeister.roomresetmini.ui.theme.ConfirmDialog
import com.egrmeister.roomresetmini.ui.theme.SectionTitle
import com.egrmeister.roomresetmini.ui.theme.SimpleTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val message: String? = null,
    val busy: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val templates: TemplateRepository,
    private val sessions: SessionRepository,
    private val resetter: DataResetter,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, message, busy) { s, m, b ->
        SettingsUiState(s, m, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDefaultDuration(minutes: Int) = launchSafe { settings.setDefaultDuration(minutes) }
    fun setSound(enabled: Boolean) = launchSafe { settings.setSoundEnabled(enabled) }
    fun setReducedAnimation(enabled: Boolean) = launchSafe { settings.setReducedAnimation(enabled) }

    fun restoreStarters() = launchSafe {
        val result = templates.restoreStarters()
        message.value = when {
            result.added == 0 -> "No room left: you can keep up to 12 rooms."
            result.skippedForLimit > 0 -> "Added ${result.added} starter rooms. Some were skipped (12-room limit)."
            else -> "Added ${result.added} starter rooms. Existing rooms were kept."
        }
    }

    fun clearHistory() = launchSafe {
        sessions.clearHistory()
        message.value = "History cleared."
    }

    fun clearAll() = launchSafe {
        resetter.clearAll()
        message.value = "All local data cleared. Starter rooms and default settings restored."
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }
}

private enum class PendingAction { RESTORE, CLEAR_HISTORY, CLEAR_ALL }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = "Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionTitle("Default duration")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Durations.MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = state.settings.defaultDurationMinutes == minutes,
                            onClick = { viewModel.setDefaultDuration(minutes) },
                            label = { Text("$minutes min") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "Sound when time is up",
                    subtitle = "A short tone, only while the app is open. Off by default.",
                    checked = state.settings.soundEnabled,
                    onChange = viewModel::setSound,
                )
                SwitchRow(
                    title = "Reduce decorative animation",
                    subtitle = "Zone colours and checkmarks change without motion.",
                    checked = state.settings.reducedAnimation,
                    onChange = viewModel::setReducedAnimation,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionTitle("Data")
                OutlinedButton(
                    onClick = { pending = PendingAction.RESTORE },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Restore starter templates") }
                OutlinedButton(
                    onClick = { pending = PendingAction.CLEAR_HISTORY },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Clear history") }
                OutlinedButton(
                    onClick = { pending = PendingAction.CLEAR_ALL },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Clear all local data") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onPrivacy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Privacy information") }
            }
        }
    }

    when (pending) {
        PendingAction.RESTORE -> ConfirmDialog(
            title = "Restore starter templates?",
            text = "Bedroom, Study Space and Living Room are added again. Your rooms and history are kept; " +
                "names get a number if they already exist.",
            confirmLabel = "Restore",
            onConfirm = {
                pending = null
                viewModel.restoreStarters()
            },
            onDismiss = { pending = null },
        )
        PendingAction.CLEAR_HISTORY -> ConfirmDialog(
            title = "Clear history?",
            text = "All saved resets will be deleted from this device.",
            confirmLabel = "Clear history",
            onConfirm = {
                pending = null
                viewModel.clearHistory()
            },
            onDismiss = { pending = null },
        )
        PendingAction.CLEAR_ALL -> ConfirmDialog(
            title = "Clear all local data?",
            text = "Rooms, history, any unfinished reset and settings are deleted. " +
                "Starter templates and default settings are restored.",
            confirmLabel = "Clear all",
            onConfirm = {
                pending = null
                viewModel.clearAll()
            },
            onDismiss = { pending = null },
        )
        null -> Unit
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SimpleTopBar(title = "Privacy", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PRIVACY_PARAGRAPHS.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

private val PRIVACY_PARAGRAPHS = listOf(
    "RoomReset Mini works completely offline.",
    "Your room templates, session progress and saved history stay on this device, in the app's private storage. " +
        "Nothing is uploaded, synced or shared.",
    "The app has no account, no advertising, no analytics and no internet access. It does not use the camera, " +
        "microphone, location or your files, and it asks for no permissions.",
    "App data is excluded from cloud backup and device-to-device transfer, so it is not copied off this device.",
    "Task completion is reported by you. The app does not inspect rooms or verify cleanliness.",
    "Settings → Clear all local data deletes everything the app has stored. Uninstalling the app also removes it.",
)
