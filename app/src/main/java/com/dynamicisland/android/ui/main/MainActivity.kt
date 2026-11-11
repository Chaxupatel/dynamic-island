package com.dynamicisland.android.ui.main

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicisland.android.DynamicIslandApp
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.ui.theme.DynamicIslandTheme
import com.dynamicisland.android.ui.theme.paletteFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val app = application as DynamicIslandApp

        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = IslandSettings()
            )
            val isLight = paletteFor(settings.theme).isLightTheme

            LaunchedEffect(isLight) {
                if (isLight) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    )
                }
            }

            DynamicIslandTheme(themeId = settings.theme) {
                HomeScreen(
                    settingsRepository = app.settingsRepository,
                    coordinator = app.coordinator
                )
            }
        }
    }
}
