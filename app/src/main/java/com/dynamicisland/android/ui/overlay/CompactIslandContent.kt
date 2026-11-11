package com.dynamicisland.android.ui.overlay

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.model.BluetoothDeviceType
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.RingerMode
import com.dynamicisland.android.ui.theme.IslandTheme
import kotlinx.coroutines.delay

@Composable
fun CompactIslandContent(
    state: IslandUiState,
    modifier: Modifier = Modifier
) {
    val palette = IslandTheme.palette
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (state.mode) {
            IslandVisualMode.IDLE -> {
                if (palette.showTelemetryPip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette.islandTextMuted.copy(alpha = 0.45f))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(palette.primaryAccent)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth())
                }
            }
            IslandVisualMode.POPUP -> {
                PopupCapsuleContent(activity = state.primary)
            }
            IslandVisualMode.COMPACT -> {
                val primary = state.primary
                val secondary = state.secondary
                if (primary != null && secondary != null) {
                    SplitCapsuleContent(primary = primary, secondary = secondary)
                } else if (primary != null) {
                    SingleCompactCapsuleContent(activity = primary)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SingleCompactCapsuleContent(activity: IslandActivity) {
    val palette = IslandTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (activity) {
            is IslandActivity.MediaActivity -> {
                val art = remember(activity.albumArt) { activity.albumArt?.toSafeImageBitmap() }
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(21.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(0.5.dp, palette.islandTextPrimary.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.secondaryAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = palette.secondaryAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                AudioWaveformBars(
                    isPlaying = activity.isPlaying,
                    barColor = palette.secondaryAccent,
                    maxHeight = 13.dp
                )
            }

            is IslandActivity.CallActivity -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(palette.positiveAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = palette.positiveAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    LiveElapsedText(
                        baseElapsedMs = activity.startTimeMs,
                        isCountDown = false,
                        color = palette.positiveAccent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AudioWaveformBars(
                    isPlaying = true,
                    barColor = palette.positiveAccent,
                    maxHeight = 12.dp
                )
            }

            is IslandActivity.TimerActivity -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(palette.warningAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = palette.warningAccent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                LiveElapsedText(
                    baseElapsedMs = activity.baseElapsedRealtimeMs,
                    isCountDown = activity.isCountDown,
                    color = palette.warningAccent
                )
            }

            is IslandActivity.NotificationActivity -> {
                val iconBmp = remember(activity.appIcon) { activity.appIcon?.toSafeImageBitmap() }
                if (iconBmp != null) {
                    Image(
                        bitmap = iconBmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(19.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activity.title,
                    color = palette.islandTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun SplitCapsuleContent(primary: IslandActivity, secondary: IslandActivity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MiniWingBadge(activity = primary)
        Spacer(modifier = Modifier.width(16.dp))
        MiniWingBadge(activity = secondary)
    }
}

@Composable
private fun MiniWingBadge(activity: IslandActivity) {
    val palette = IslandTheme.palette
    when (activity) {
        is IslandActivity.MediaActivity -> {
            AudioWaveformBars(
                isPlaying = activity.isPlaying,
                barColor = palette.secondaryAccent,
                maxHeight = 12.dp
            )
        }
        is IslandActivity.CallActivity -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = palette.positiveAccent,
                    modifier = Modifier.size(13.dp)
                )
                LiveElapsedText(
                    baseElapsedMs = activity.startTimeMs,
                    isCountDown = false,
                    color = palette.positiveAccent
                )
            }
        }
        is IslandActivity.TimerActivity -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = palette.warningAccent,
                    modifier = Modifier.size(13.dp)
                )
                LiveElapsedText(
                    baseElapsedMs = activity.baseElapsedRealtimeMs,
                    isCountDown = activity.isCountDown,
                    color = palette.warningAccent
                )
            }
        }
        else -> {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = palette.primaryAccent,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun PopupCapsuleContent(activity: IslandActivity?) {
    val palette = IslandTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (activity) {
            is IslandActivity.ChargingPopup -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(palette.positiveAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = palette.positiveAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = if (activity.isFastCharging) "Fast Charging" else "Charging",
                        color = palette.islandTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.positiveAccent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${activity.batteryPercent}%",
                        color = palette.positiveAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularTextStyle
                    )
                }
            }

            is IslandActivity.BluetoothPopup -> {
                val icon = when (activity.deviceType) {
                    BluetoothDeviceType.HEADPHONES -> Icons.Rounded.Headphones
                    BluetoothDeviceType.WATCH -> Icons.Rounded.Watch
                    BluetoothDeviceType.SPEAKER -> Icons.Rounded.Speaker
                    BluetoothDeviceType.DEVICE -> Icons.Rounded.Bluetooth
                }
                val accent = if (activity.isConnected) palette.primaryAccent else palette.islandTextMuted
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column {
                        Text(
                            text = activity.deviceName,
                            color = palette.islandTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activity.isConnected) "Connected" else "Disconnected",
                            color = palette.islandTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
                activity.batteryPercent?.let { pct ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.positiveAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$pct%",
                            color = palette.positiveAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularTextStyle
                        )
                    }
                }
            }

            is IslandActivity.RingerPopup -> {
                val (icon, label, color) = when (activity.mode) {
                    RingerMode.SILENT -> Triple(Icons.Rounded.NotificationsOff, "Silent", palette.dangerAccent)
                    RingerMode.VIBRATE -> Triple(Icons.Rounded.Vibration, "Vibrate", palette.warningAccent)
                    RingerMode.NORMAL -> Triple(Icons.Rounded.Notifications, "Ring", palette.positiveAccent)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = label,
                        color = palette.islandTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }

            is IslandActivity.NotificationActivity -> {
                val accent = readableIslandAccent(
                    rawColor = activity.accentColor,
                    fallback = palette.primaryAccent,
                    isLightSurface = palette.isLightTheme
                )
                val bmp = remember(activity.largeIcon, activity.appIcon) {
                    (activity.largeIcon ?: activity.appIcon)?.toSafeImageBitmap()
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .border(0.5.dp, palette.islandTextPrimary.copy(alpha = 0.16f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activity.title,
                            color = palette.islandTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activity.body.isNotBlank()) {
                            Text(
                                text = activity.body,
                                color = palette.islandTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            is IslandActivity.CallActivity -> {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(palette.positiveAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = palette.positiveAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column {
                        Text(
                            text = activity.callerName,
                            color = palette.islandTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activity.statusText,
                            color = palette.positiveAccent,
                            fontSize = 10.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
fun LiveElapsedText(
    baseElapsedMs: Long,
    isCountDown: Boolean,
    color: Color,
    fontSizeSp: Int = 11
) {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(baseElapsedMs, isCountDown) {
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(500L)
        }
    }
    val deltaMs = if (isCountDown) {
        (baseElapsedMs - nowMs).coerceAtLeast(0L)
    } else {
        (nowMs - baseElapsedMs).coerceAtLeast(0L)
    }
    Text(
        text = formatDurationMs(deltaMs),
        color = color,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
        style = TabularTextStyle,
        maxLines = 1
    )
}
