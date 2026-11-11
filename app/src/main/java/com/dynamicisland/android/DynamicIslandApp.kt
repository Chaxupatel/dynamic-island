package com.dynamicisland.android

import android.app.Application
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DynamicIslandApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var coordinator: IslandStateCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        coordinator = IslandStateCoordinator(applicationScope)

        applicationScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                coordinator.updateSettings(settings)
            }
        }
    }
}

