package com.egrmeister.roomresetmini

import android.app.Application
import kotlinx.coroutines.launch

class RoomResetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.templates.ensureSeeded() }
    }
}
