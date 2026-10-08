package com.egrmeister.roomresetmini.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.roomresetmini.domain.sessions.Durations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.roomResetStore: DataStore<Preferences> by preferencesDataStore(name = "roomreset_settings")

data class AppSettings(
    val defaultDurationMinutes: Int = Durations.DEFAULT_MINUTES,
    val soundEnabled: Boolean = false,
    val reducedAnimation: Boolean = false,
    val selectedTemplateId: Long? = null,
    val listView: Boolean = false,
    /** Lightweight UI state: show "timer was paused after restart" until dismissed. */
    val restartNotice: Boolean = false,
)

/** DataStore-backed preferences and lightweight UI state, in app-private storage (not backed up). */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.roomResetStore

    private object Keys {
        val SEEDED = booleanPreferencesKey("starter_templates_seeded")
        val DEFAULT_DURATION = intPreferencesKey("default_duration_minutes")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val REDUCED_ANIMATION = booleanPreferencesKey("reduced_animation")
        val SELECTED_TEMPLATE = longPreferencesKey("selected_template_id")
        val LIST_VIEW = booleanPreferencesKey("zone_list_view")
        val RESTART_NOTICE = booleanPreferencesKey("restart_notice")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            defaultDurationMinutes = Durations.sanitize(p[Keys.DEFAULT_DURATION]),
            soundEnabled = p[Keys.SOUND] ?: false,
            reducedAnimation = p[Keys.REDUCED_ANIMATION] ?: false,
            selectedTemplateId = p[Keys.SELECTED_TEMPLATE],
            listView = p[Keys.LIST_VIEW] ?: false,
            restartNotice = p[Keys.RESTART_NOTICE] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun isSeeded(): Boolean = store.data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() {
        store.edit { it[Keys.SEEDED] = true }
    }

    suspend fun setDefaultDuration(minutes: Int) {
        store.edit { it[Keys.DEFAULT_DURATION] = Durations.sanitize(minutes) }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setReducedAnimation(enabled: Boolean) {
        store.edit { it[Keys.REDUCED_ANIMATION] = enabled }
    }

    suspend fun setSelectedTemplate(id: Long?) {
        store.edit { if (id == null) it.remove(Keys.SELECTED_TEMPLATE) else it[Keys.SELECTED_TEMPLATE] = id }
    }

    suspend fun setListView(enabled: Boolean) {
        store.edit { it[Keys.LIST_VIEW] = enabled }
    }

    suspend fun setRestartNotice(show: Boolean) {
        store.edit { it[Keys.RESTART_NOTICE] = show }
    }

    /** Clear All Data: default settings, starter templates already re-seeded by the caller. */
    suspend fun resetToDefaults(selectedTemplateId: Long?) {
        store.edit {
            it.clear()
            it[Keys.SEEDED] = true
            if (selectedTemplateId != null) it[Keys.SELECTED_TEMPLATE] = selectedTemplateId
        }
    }
}
