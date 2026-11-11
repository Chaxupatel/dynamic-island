package com.dynamicisland.android.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap

enum class IslandVisualMode {
    HIDDEN,
    IDLE,
    COMPACT,
    POPUP,
    EXPANDED
}

enum class IslandThemeId(
    val displayName: String,
    val subtitle: String
) {
    OBSIDIAN_GLASS("Obsidian Glass", "Midnight OLED glass & cyan"),
    ALABASTER_GOLD("Alabaster Gold", "Warm ceramic pearl & copper"),
    MONOLITH_CARBON("Monolith Carbon", "Stealth HUD & signal red")
}

enum class IslandFeatureKind(val label: String) {
    IDLE("Idle Pill"),
    NOTIFICATION("Notification"),
    MEDIA("Music"),
    CHARGING("Charging"),
    BLUETOOTH("Bluetooth"),
    TIMER("Timer"),
    CALL("Call"),
    RINGER("Silent / Ring")
}

data class FeatureSizeSpec(
    val widthDp: Float,
    val heightDp: Float,
    val expandedWidthDp: Float = 346f
)

fun defaultFeatureSizes(): Map<IslandFeatureKind, FeatureSizeSpec> = mapOf(
    IslandFeatureKind.IDLE to FeatureSizeSpec(widthDp = 114f, heightDp = 32f, expandedWidthDp = 346f),
    IslandFeatureKind.NOTIFICATION to FeatureSizeSpec(widthDp = 286f, heightDp = 48f, expandedWidthDp = 346f),
    IslandFeatureKind.MEDIA to FeatureSizeSpec(widthDp = 190f, heightDp = 34f, expandedWidthDp = 346f),
    IslandFeatureKind.CHARGING to FeatureSizeSpec(widthDp = 270f, heightDp = 46f, expandedWidthDp = 336f),
    IslandFeatureKind.BLUETOOTH to FeatureSizeSpec(widthDp = 286f, heightDp = 48f, expandedWidthDp = 340f),
    IslandFeatureKind.TIMER to FeatureSizeSpec(widthDp = 176f, heightDp = 34f, expandedWidthDp = 336f),
    IslandFeatureKind.CALL to FeatureSizeSpec(widthDp = 196f, heightDp = 34f, expandedWidthDp = 346f),
    IslandFeatureKind.RINGER to FeatureSizeSpec(widthDp = 184f, heightDp = 42f, expandedWidthDp = 300f)
)

enum class ChargeSource {
    AC,
    USB,
    WIRELESS,
    UNKNOWN
}

enum class BluetoothDeviceType {
    HEADPHONES,
    WATCH,
    SPEAKER,
    DEVICE
}

enum class RingerMode {
    SILENT,
    VIBRATE,
    NORMAL
}

data class NotificationActionInfo(
    val title: String,
    val actionIntent: PendingIntent? = null,
    val remoteInput: RemoteInput? = null
) {
    val hasRemoteInput: Boolean
        get() = remoteInput != null
}

sealed interface IslandActivity {
    val id: String

    data class CallActivity(
        override val id: String,
        val packageName: String,
        val callerName: String,
        val statusText: String,
        val isIncoming: Boolean,
        val startTimeMs: Long,
        val avatar: Bitmap? = null,
        val contentIntent: PendingIntent? = null,
        val actions: List<NotificationActionInfo> = emptyList()
    ) : IslandActivity

    data class MediaActivity(
        override val id: String = "media_active",
        val packageName: String,
        val appName: String,
        val trackTitle: String,
        val artist: String,
        val albumArt: Bitmap? = null,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val lastPositionUpdateMs: Long,
        val playbackSpeed: Float = 1f,
        val contentIntent: PendingIntent? = null,
        val accentColor: Int = 0xFF5CE1E6.toInt()
    ) : IslandActivity

    data class TimerActivity(
        override val id: String,
        val packageName: String,
        val label: String,
        val baseElapsedRealtimeMs: Long,
        val isCountDown: Boolean,
        val isPaused: Boolean = false,
        val contentIntent: PendingIntent? = null,
        val actions: List<NotificationActionInfo> = emptyList()
    ) : IslandActivity

    data class NotificationActivity(
        override val id: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val body: String,
        val timestampMs: Long,
        val appIcon: Bitmap? = null,
        val largeIcon: Bitmap? = null,
        val contentIntent: PendingIntent? = null,
        val actions: List<NotificationActionInfo> = emptyList(),
        val accentColor: Int = 0xFF4F8CFF.toInt()
    ) : IslandActivity

    data class ChargingPopup(
        override val id: String = "charging_popup",
        val batteryPercent: Int,
        val isCharging: Boolean,
        val chargeSource: ChargeSource = ChargeSource.UNKNOWN,
        val isFastCharging: Boolean = false
    ) : IslandActivity

    data class BluetoothPopup(
        override val id: String = "bluetooth_popup",
        val deviceName: String,
        val batteryPercent: Int? = null,
        val isConnected: Boolean = true,
        val deviceType: BluetoothDeviceType = BluetoothDeviceType.DEVICE
    ) : IslandActivity

    data class RingerPopup(
        override val id: String = "ringer_popup",
        val mode: RingerMode
    ) : IslandActivity
}

fun IslandActivity.featureKind(): IslandFeatureKind = when (this) {
    is IslandActivity.NotificationActivity -> IslandFeatureKind.NOTIFICATION
    is IslandActivity.MediaActivity -> IslandFeatureKind.MEDIA
    is IslandActivity.ChargingPopup -> IslandFeatureKind.CHARGING
    is IslandActivity.BluetoothPopup -> IslandFeatureKind.BLUETOOTH
    is IslandActivity.TimerActivity -> IslandFeatureKind.TIMER
    is IslandActivity.CallActivity -> IslandFeatureKind.CALL
    is IslandActivity.RingerPopup -> IslandFeatureKind.RINGER
}

data class IslandSettings(
    val isEnabled: Boolean = true,
    val enableNotifications: Boolean = true,
    val enableMediaPlayer: Boolean = true,
    val enableSystemPopups: Boolean = true,
    val enableCallsAndTimers: Boolean = true,
    val hideWhenIdle: Boolean = false,
    val hideInLandscape: Boolean = true,
    val showOnLockscreen: Boolean = true,
    val popupDurationMs: Long = 4000L,
    val offsetXdp: Float = 0f,
    val offsetYdp: Float = 8f,
    val cornerRadiusDp: Float = 26f,
    val theme: IslandThemeId = IslandThemeId.OBSIDIAN_GLASS,
    val featureSizes: Map<IslandFeatureKind, FeatureSizeSpec> = defaultFeatureSizes()
) {
    fun sizeFor(kind: IslandFeatureKind): FeatureSizeSpec {
        return featureSizes[kind] ?: defaultFeatureSizes().getValue(kind)
    }

    fun withFeatureSize(kind: IslandFeatureKind, spec: FeatureSizeSpec): IslandSettings {
        val updated = featureSizes.toMutableMap().apply { put(kind, spec) }
        return copy(featureSizes = updated)
    }
}

data class IslandUiState(
    val mode: IslandVisualMode = IslandVisualMode.IDLE,
    val primary: IslandActivity? = null,
    val secondary: IslandActivity? = null,
    val isLandscape: Boolean = false,
    val isScreenLocked: Boolean = false,
    val isInlineReplyOpen: Boolean = false,
    val isPreviewPinned: Boolean = true,
    val settings: IslandSettings = IslandSettings()
)
