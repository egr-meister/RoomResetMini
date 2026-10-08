package com.egrmeister.roomresetmini.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.roomresetmini.data.local.CompletionSound
import com.egrmeister.roomresetmini.data.repository.AppSettings
import com.egrmeister.roomresetmini.data.repository.SessionRepository
import com.egrmeister.roomresetmini.data.repository.SettingsRepository
import com.egrmeister.roomresetmini.data.repository.TemplateRepository
import com.egrmeister.roomresetmini.domain.sessions.Durations
import com.egrmeister.roomresetmini.domain.sessions.ResetSession
import com.egrmeister.roomresetmini.domain.sessions.SaveCoordinator
import com.egrmeister.roomresetmini.domain.sessions.SessionSnapshot
import com.egrmeister.roomresetmini.domain.templates.RoomTemplate
import com.egrmeister.roomresetmini.domain.timer.BootReferenceProvider
import com.egrmeister.roomresetmini.domain.timer.MonotonicClock
import com.egrmeister.roomresetmini.domain.timer.TimerEngine
import com.egrmeister.roomresetmini.domain.timer.TimerState
import com.egrmeister.roomresetmini.domain.timer.TimerStatus
import com.egrmeister.roomresetmini.domain.timer.WallClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RoomUiState(
    val loading: Boolean = true,
    val templates: List<RoomTemplate> = emptyList(),
    val selectedTemplate: RoomTemplate? = null,
    val durationMinutes: Int = Durations.DEFAULT_MINUTES,
    val session: ResetSession? = null,
    val remainingMillis: Long = 0L,
    val settings: AppSettings = AppSettings(),
    val unfinishedPrompt: Boolean = false,
    val message: String? = null,
    val saving: Boolean = false,
) {
    val usableTemplates: List<RoomTemplate> get() = templates.filter { it.isUsable }
}

private sealed interface SessionLoad {
    data object Loading : SessionLoad
    data class Loaded(val session: ResetSession?) : SessionLoad
}

private data class Transient(
    val unfinishedPrompt: Boolean = false,
    val message: String? = null,
    val saving: Boolean = false,
)

/**
 * Owns the active session and its timer. Activity-scoped, shared by the room, zone and review screens.
 * The countdown is derived from a persisted elapsedRealtime checkpoint; the ticker only refreshes the
 * display while the app is visible. No background service, alarm or notification is used.
 */
class RoomViewModel(
    private val templates: TemplateRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val saver: SaveCoordinator,
    private val monotonic: MonotonicClock,
    private val wall: WallClock,
    private val boot: BootReferenceProvider,
    private val sound: CompletionSound,
) : ViewModel() {

    private val timerMutex = Mutex()
    private val now = MutableStateFlow(monotonic.elapsedRealtimeMillis())
    private val chosenDuration = MutableStateFlow<Int?>(null)
    private val transient = MutableStateFlow(Transient())
    private var visible = false
    private var tickJob: Job? = null
    private var lastCheckpointWrite = 0L

    private val active: StateFlow<SessionLoad> = sessions.activeSession
        .map<ResetSession?, SessionLoad> { SessionLoad.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionLoad.Loading)

    val uiState: StateFlow<RoomUiState> = combine(
        combine(templates.templates, settings.settings) { t, s -> t to s },
        active,
        now,
        chosenDuration,
        transient,
    ) { (templateList, appSettings), load, nowMs, duration, tr ->
        val session = (load as? SessionLoad.Loaded)?.session
        val selected = appSettings.selectedTemplateId?.let { id -> templateList.firstOrNull { it.id == id && it.isUsable } }
        RoomUiState(
            loading = load is SessionLoad.Loading,
            templates = templateList,
            selectedTemplate = selected,
            durationMinutes = Durations.sanitize(duration ?: appSettings.defaultDurationMinutes),
            session = session,
            remainingMillis = session?.let { TimerEngine.remaining(it.timer, nowMs) } ?: 0L,
            settings = appSettings,
            unfinishedPrompt = tr.unfinishedPrompt && session != null,
            message = tr.message,
            saving = tr.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RoomUiState())

    init {
        viewModelScope.launch {
            // Process start / recreation: restore the running or paused timer, then offer
            // Resume / Finish and save / Discard instead of silently continuing or overwriting.
            if (recoverActive()) transient.update { it.copy(unfinishedPrompt = true) }
        }
    }

    // ---- Lifecycle ---------------------------------------------------------------------------

    fun onVisible() {
        visible = true
        viewModelScope.launch { recoverActive() }
        startTicker()
    }

    fun onHidden() {
        visible = false
        tickJob?.cancel()
        tickJob = null
        // Save a fresh checkpoint so a reboot while backgrounded restores the latest remaining time.
        viewModelScope.launch { mutateTimer { TimerEngine.checkpoint(it, monotonic.elapsedRealtimeMillis()) } }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val t = monotonic.elapsedRealtimeMillis()
                now.value = t
                onTick(t)
                delay(TICK_MS)
            }
        }
    }

    private suspend fun onTick(t: Long) {
        val session = (active.value as? SessionLoad.Loaded)?.session ?: return
        if (session.timer.status != TimerStatus.RUNNING) return
        val left = TimerEngine.remaining(session.timer, t)
        if (left == 0L) {
            val expiredNow = timerMutex.withLock {
                val current = sessions.getActiveSession() ?: return@withLock false
                val next = TimerEngine.tick(current.timer, monotonic.elapsedRealtimeMillis())
                if (next.status == TimerStatus.EXPIRED && current.timer.status == TimerStatus.RUNNING) {
                    sessions.updateTimer(current.id, next)
                } else {
                    false
                }
            }
            // Sound only for an expiry observed while visible — never a delayed sound after returning.
            if (expiredNow && visible && settings.current().soundEnabled) sound.play()
        } else if (t - lastCheckpointWrite >= CHECKPOINT_EVERY_MS) {
            lastCheckpointWrite = t
            mutateTimer { TimerEngine.checkpoint(it, monotonic.elapsedRealtimeMillis()) }
        }
    }

    /** @return true if an unfinished session exists. Idempotent; safe to call repeatedly. */
    private suspend fun recoverActive(): Boolean = timerMutex.withLock {
        val current = sessions.getActiveSession() ?: return@withLock false
        val result = TimerEngine.recover(current.timer, monotonic.elapsedRealtimeMillis(), boot.bootReference())
        if (result.state != current.timer) sessions.updateTimer(current.id, result.state)
        if (result.pausedAfterRestart) settings.setRestartNotice(true)
        now.value = monotonic.elapsedRealtimeMillis()
        true
    }

    private suspend fun mutateTimer(transform: (TimerState) -> TimerState) {
        timerMutex.withLock {
            val current = sessions.getActiveSession() ?: return@withLock
            val next = transform(current.timer)
            if (next != current.timer) sessions.updateTimer(current.id, next)
        }
        now.value = monotonic.elapsedRealtimeMillis()
    }

    // ---- Selection ---------------------------------------------------------------------------

    fun selectTemplate(id: Long) {
        viewModelScope.launch { settings.setSelectedTemplate(id) }
    }

    fun selectDuration(minutes: Int) {
        chosenDuration.value = Durations.sanitize(minutes)
    }

    fun setListView(enabled: Boolean) {
        viewModelScope.launch { settings.setListView(enabled) }
    }

    // ---- Session -----------------------------------------------------------------------------

    fun startReset() {
        val state = uiState.value
        val template = state.selectedTemplate
        if (template == null) {
            showMessage("Choose a room to start.")
            return
        }
        start(template, state.durationMinutes)
    }

    /** "Start Another Reset" after saving: same room (matched by name) and duration when possible. */
    fun startAnother(previous: ResetSession?) {
        viewModelScope.launch {
            val all = uiState.value.usableTemplates
            val template = previous?.let { p -> all.firstOrNull { it.name.equals(p.templateName, ignoreCase = true) } }
                ?: uiState.value.selectedTemplate
            if (template == null) {
                showMessage("Choose a room to start.")
                return@launch
            }
            val minutes = previous?.let { Durations.sanitize(Durations.minutesOf(it.durationMillis)) }
                ?: uiState.value.durationMinutes
            settings.setSelectedTemplate(template.id)
            chosenDuration.value = minutes
            start(template, minutes)
        }
    }

    private fun start(template: RoomTemplate, minutes: Int) {
        viewModelScope.launch {
            val fresh = templates.getTemplate(template.id)
            if (fresh == null || !fresh.isUsable) {
                showMessage("Add at least one task to this room first.")
                return@launch
            }
            val draft = SessionSnapshot.from(fresh, Durations.toMillis(minutes))
            val created = timerMutex.withLock {
                val timer = TimerEngine.start(draft.durationMillis, monotonic.elapsedRealtimeMillis(), boot.bootReference())
                sessions.createSession(draft, timer, wall.currentTimeMillis())
            }
            if (created == null) {
                // Never overwrite an unfinished session.
                transient.update { it.copy(unfinishedPrompt = true) }
            } else {
                settings.setRestartNotice(false)
                now.value = monotonic.elapsedRealtimeMillis()
            }
        }
    }

    fun toggleTask(taskId: Long, completed: Boolean) {
        viewModelScope.launch { sessions.setTaskCompleted(taskId, completed) }
    }

    fun pause() {
        viewModelScope.launch { mutateTimer { TimerEngine.pause(it, monotonic.elapsedRealtimeMillis()) } }
    }

    fun resume() {
        viewModelScope.launch {
            mutateTimer { TimerEngine.resume(it, monotonic.elapsedRealtimeMillis(), boot.bootReference()) }
            settings.setRestartNotice(false)
        }
    }

    fun continueWithoutTimer() {
        viewModelScope.launch { mutateTimer { TimerEngine.continueWithoutTimer(it) } }
    }

    /** Saves the active session once; repeated taps reuse the same result. */
    fun saveActive(onSaved: (Long) -> Unit) {
        if (transient.value.saving) return
        transient.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = timerMutex.withLock {
                    val current = sessions.getActiveSession() ?: return@withLock null
                    val stopped = TimerEngine.pause(current.timer, monotonic.elapsedRealtimeMillis())
                    if (stopped != current.timer) sessions.updateTimer(current.id, stopped)
                    current.id
                }
                if (id != null) {
                    saver.save(id)
                    settings.setRestartNotice(false)
                    transient.update { it.copy(unfinishedPrompt = false) }
                    onSaved(id)
                }
            } finally {
                transient.update { it.copy(saving = false) }
            }
        }
    }

    fun discardActive() {
        viewModelScope.launch {
            timerMutex.withLock {
                sessions.getActiveSession()?.let { sessions.deleteSession(it.id) }
            }
            settings.setRestartNotice(false)
            transient.update { it.copy(unfinishedPrompt = false) }
        }
    }

    fun dismissUnfinishedPrompt() {
        transient.update { it.copy(unfinishedPrompt = false) }
    }

    fun dismissRestartNotice() {
        viewModelScope.launch { settings.setRestartNotice(false) }
    }

    fun consumeMessage() {
        transient.update { it.copy(message = null) }
    }

    private fun showMessage(text: String) {
        transient.update { it.copy(message = text) }
    }

    private companion object {
        const val TICK_MS = 250L
        const val CHECKPOINT_EVERY_MS = 30_000L
    }
}
