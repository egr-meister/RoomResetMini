package com.egrmeister.roomresetmini.data.repository

import androidx.room.withTransaction
import com.egrmeister.roomresetmini.data.local.AppDatabase

/** "Clear all local data": one Room transaction, then default settings. */
class DataResetter(
    private val database: AppDatabase,
    private val templates: TemplateRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
) {
    suspend fun clearAll() {
        val firstStarter = database.withTransaction {
            sessions.deleteAll()
            templates.replaceAllWithStarters()
        }
        settings.resetToDefaults(selectedTemplateId = firstStarter)
    }
}
