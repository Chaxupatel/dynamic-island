package com.dynamicisland.android.service

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.core.PermissionHelper
import com.dynamicisland.android.model.BluetoothDeviceType
import com.dynamicisland.android.model.ChargeSource
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.RingerMode

/**
 * Monitors battery charging, Bluetooth device connections, ringer mode changes,
 * and screen lock state via system broadcasts.
 */
class SystemEventsMonitor(
    private val context: Context,
    private val coordinator: IslandStateCoordinator
) {
    private var isRegistered = false
    private var wasCharging: Boolean? = null
    private var lastRingerMode: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryIntent(intent)

                BluetoothDevice.ACTION_ACL_CONNECTED -> handleBluetoothEvent(intent, connected = true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleBluetoothEvent(intent, connected = false)

                AudioManager.RINGER_MODE_CHANGED_ACTION -> handleRingerChanged(intent)

                Intent.ACTION_SCREEN_OFF -> coordinator.setScreenLocked(true)
                Intent.ACTION_USER_PRESENT -> coordinator.setScreenLocked(false)
                Intent.ACTION_SCREEN_ON -> updateKeyguardState()
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        isRegistered = true
        updateKeyguardState()
    }

    fun stop() {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        isRegistered = false
    }

    private fun updateKeyguardState() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        coordinator.setScreenLocked(km?.isKeyguardLocked == true)
    }

    private fun handleBatteryIntent(intent: Intent) {
        val batteryIntent = if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            intent
        } else {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 0

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargeSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargeSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            else -> ChargeSource.UNKNOWN
        }

        val previous = wasCharging
        wasCharging = isCharging

        // Trigger popup when newly plugged in or explicit power connected action
        val shouldShow = intent.action == Intent.ACTION_POWER_CONNECTED ||
            (previous != null && !previous && isCharging)

        if (shouldShow) {
            coordinator.showSystemPopup(
                IslandActivity.ChargingPopup(
                    batteryPercent = percent,
                    isCharging = true,
                    chargeSource = source,
                    isFastCharging = source == ChargeSource.AC || source == ChargeSource.WIRELESS
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothEvent(intent: Intent, connected: Boolean) {
        if (!PermissionHelper.isBluetoothConnectGranted(context)) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val name = runCatching { device?.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "Bluetooth Device"

        val batteryExtra = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        val batteryPercent = batteryExtra.takeIf { it in 0..100 }

        val deviceType = classifyBluetoothDevice(device)
        coordinator.showSystemPopup(
            IslandActivity.BluetoothPopup(
                deviceName = name,
                batteryPercent = batteryPercent,
                isConnected = connected,
                deviceType = deviceType
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun classifyBluetoothDevice(device: BluetoothDevice?): BluetoothDeviceType {
        val majorClass = runCatching { device?.bluetoothClass?.majorDeviceClass }.getOrNull()
        val deviceClass = runCatching { device?.bluetoothClass?.deviceClass }.getOrNull()
        return when {
            majorClass == BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceType.WATCH
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BluetoothDeviceType.HEADPHONES
            majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceType.SPEAKER
            else -> BluetoothDeviceType.DEVICE
        }
    }

    private fun handleRingerChanged(intent: Intent) {
        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        if (mode == -1 || mode == lastRingerMode) return
        val hadPrevious = lastRingerMode != null
        lastRingerMode = mode
        if (!hadPrevious) return

        val ringerMode = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
        coordinator.showSystemPopup(IslandActivity.RingerPopup(mode = ringerMode))
    }
}

