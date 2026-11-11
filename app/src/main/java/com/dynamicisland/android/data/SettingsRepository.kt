package com.dynamicisland.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dynamicisland.android.model.FeatureSizeSpec
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandThemeId
import com.dynamicisland.android.model.defaultFeatureSizes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.islandDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dynamic_island_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")
        val ENABLE_MEDIA = booleanPreferencesKey("enable_media")
        val ENABLE_SYSTEM_POPUPS = booleanPreferencesKey("enable_system_popups")
        val ENABLE_CALLS_TIMERS = booleanPreferencesKey("enable_calls_timers")
        val HIDE_WHEN_IDLE = booleanPreferencesKey("hide_when_idle")
        val HIDE_IN_LANDSCAPE = booleanPreferencesKey("hide_in_landscape")
        val SHOW_ON_LOCKSCREEN = booleanPreferencesKey("show_on_lockscreen")
        val POPUP_DURATION_MS = longPreferencesKey("popup_duration_ms")
        val OFFSET_X_DP = floatPreferencesKey("offset_x_dp")
        val OFFSET_Y_DP = floatPreferencesKey("offset_y_dp")
        val CORNER_RADIUS_DP = floatPreferencesKey("corner_radius_dp")
        val APP_THEME = stringPreferencesKey("app_theme")

        fun widthKey(kind: IslandFeatureKind) =
            floatPreferencesKey("size_${kind.name.lowercase()}_w")

        fun heightKey(kind: IslandFeatureKind) =
            floatPreferencesKey("size_${kind.name.lowercase()}_h")

        fun expandedWidthKey(kind: IslandFeatureKind) =
            floatPreferencesKey("size_${kind.name.lowercase()}_ew")
    }

    val settingsFlow: Flow<IslandSettings> = context.islandDataStore.data.map { prefs ->
        prefs.toIslandSettings()
    }

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        context.islandDataStore.edit { prefs ->
            val updated = transform(prefs.toIslandSettings())
            prefs[Keys.IS_ENABLED] = updated.isEnabled
            prefs[Keys.ENABLE_NOTIFICATIONS] = updated.enableNotifications
            prefs[Keys.ENABLE_MEDIA] = updated.enableMediaPlayer
            prefs[Keys.ENABLE_SYSTEM_POPUPS] = updated.enableSystemPopups
            prefs[Keys.ENABLE_CALLS_TIMERS] = updated.enableCallsAndTimers
            prefs[Keys.HIDE_WHEN_IDLE] = updated.hideWhenIdle
            prefs[Keys.HIDE_IN_LANDSCAPE] = updated.hideInLandscape
            prefs[Keys.SHOW_ON_LOCKSCREEN] = updated.showOnLockscreen
            prefs[Keys.POPUP_DURATION_MS] = updated.popupDurationMs
            prefs[Keys.OFFSET_X_DP] = updated.offsetXdp
            prefs[Keys.OFFSET_Y_DP] = updated.offsetYdp
            prefs[Keys.CORNER_RADIUS_DP] = updated.cornerRadiusDp
            prefs[Keys.APP_THEME] = updated.theme.name

            IslandFeatureKind.entries.forEach { kind ->
                val spec = updated.sizeFor(kind)
                prefs[Keys.widthKey(kind)] = spec.widthDp
                prefs[Keys.heightKey(kind)] = spec.heightDp
                prefs[Keys.expandedWidthKey(kind)] = spec.expandedWidthDp
            }
        }
    }

    private fun Preferences.toIslandSettings(): IslandSettings {
        val defaults = IslandSettings()
        val defaultMap = defaultFeatureSizes()
        val sizes = IslandFeatureKind.entries.associateWith { kind ->
            val def = defaultMap.getValue(kind)
            FeatureSizeSpec(
                widthDp = this[Keys.widthKey(kind)] ?: def.widthDp,
                heightDp = this[Keys.heightKey(kind)] ?: def.heightDp,
                expandedWidthDp = this[Keys.expandedWidthKey(kind)] ?: def.expandedWidthDp
            )
        }
        val storedTheme = this[Keys.APP_THEME]?.let { name ->
            runCatching { IslandThemeId.valueOf(name) }.getOrNull()
        } ?: defaults.theme
        return IslandSettings(
            isEnabled = this[Keys.IS_ENABLED] ?: defaults.isEnabled,
            enableNotifications = this[Keys.ENABLE_NOTIFICATIONS] ?: defaults.enableNotifications,
            enableMediaPlayer = this[Keys.ENABLE_MEDIA] ?: defaults.enableMediaPlayer,
            enableSystemPopups = this[Keys.ENABLE_SYSTEM_POPUPS] ?: defaults.enableSystemPopups,
            enableCallsAndTimers = this[Keys.ENABLE_CALLS_TIMERS] ?: defaults.enableCallsAndTimers,
            hideWhenIdle = this[Keys.HIDE_WHEN_IDLE] ?: defaults.hideWhenIdle,
            hideInLandscape = this[Keys.HIDE_IN_LANDSCAPE] ?: defaults.hideInLandscape,
            showOnLockscreen = this[Keys.SHOW_ON_LOCKSCREEN] ?: defaults.showOnLockscreen,
            popupDurationMs = this[Keys.POPUP_DURATION_MS] ?: defaults.popupDurationMs,
            offsetXdp = this[Keys.OFFSET_X_DP] ?: defaults.offsetXdp,
            offsetYdp = this[Keys.OFFSET_Y_DP] ?: defaults.offsetYdp,
            cornerRadiusDp = this[Keys.CORNER_RADIUS_DP] ?: defaults.cornerRadiusDp,
            theme = storedTheme,
            featureSizes = sizes
        )
    }
}
