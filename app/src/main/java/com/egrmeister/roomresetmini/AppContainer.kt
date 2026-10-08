package com.egrmeister.roomresetmini

import android.content.Context
import com.egrmeister.roomresetmini.data.local.AndroidBootReference
import com.egrmeister.roomresetmini.data.local.AndroidMonotonicClock
import com.egrmeister.roomresetmini.data.local.AppDatabase
import com.egrmeister.roomresetmini.data.local.CompletionSound
import com.egrmeister.roomresetmini.data.local.SystemWallClock
import com.egrmeister.roomresetmini.data.repository.DataResetter
import com.egrmeister.roomresetmini.data.repository.SessionRepository
import com.egrmeister.roomresetmini.data.repository.SettingsRepository
import com.egrmeister.roomresetmini.data.repository.TemplateRepository
import com.egrmeister.roomresetmini.domain.sessions.SaveCoordinator
import com.egrmeister.roomresetmini.domain.timer.BootReferenceProvider
import com.egrmeister.roomresetmini.domain.timer.MonotonicClock
import com.egrmeister.roomresetmini.domain.timer.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val monotonicClock: MonotonicClock = AndroidMonotonicClock
    val wallClock: WallClock = SystemWallClock
    val bootReference: BootReferenceProvider = AndroidBootReference(context)
    val sound = CompletionSound()

    private val database: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val templates = TemplateRepository(database.templateDao(), settings, wallClock)
    val sessions = SessionRepository(database.sessionDao())
    val saveCoordinator = SaveCoordinator(sessions, wallClock)
    val dataResetter = DataResetter(database, templates, sessions, settings)
}
