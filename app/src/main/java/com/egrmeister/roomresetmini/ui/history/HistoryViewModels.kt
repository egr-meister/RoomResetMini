package com.egrmeister.roomresetmini.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.roomresetmini.data.repository.SessionRepository
import com.egrmeister.roomresetmini.domain.sessions.HistoryEntry
import com.egrmeister.roomresetmini.domain.sessions.ResetSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(val loading: Boolean = true, val entries: List<HistoryEntry> = emptyList())

class HistoryViewModel(private val sessions: SessionRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = sessions.history
        .map { HistoryUiState(loading = false, entries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(id: Long) {
        viewModelScope.launch { sessions.deleteSession(id) }
    }

    fun clear() {
        viewModelScope.launch { sessions.clearHistory() }
    }
}

data class SessionDetailUiState(val loading: Boolean = true, val session: ResetSession? = null)

/** One saved session with its zones and task states (read-only snapshot). */
class SessionDetailViewModel(
    private val id: Long,
    private val sessions: SessionRepository,
) : ViewModel() {
    val state: StateFlow<SessionDetailUiState> = sessions.observeSession(id)
        .map { SessionDetailUiState(loading = false, session = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            sessions.deleteSession(id)
            onDeleted()
        }
    }
}
