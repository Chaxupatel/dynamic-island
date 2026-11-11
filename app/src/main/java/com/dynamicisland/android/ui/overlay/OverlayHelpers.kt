package com.dynamicisland.android.ui.overlay

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.NotificationActionInfo
import com.dynamicisland.android.model.RingerMode
import com.dynamicisland.android.ui.theme.IslandPalette
import java.util.Locale
import kotlin.math.abs

val TabularTextStyle = TextStyle(fontFeatureSettings = "tnum")

fun launchPendingIntent(context: Context, pendingIntent: PendingIntent?) {
    pendingIntent ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
        } else {
            pendingIntent.send()
        }
    }
}

fun sendRemoteInputReply(
    context: Context,
    action: NotificationActionInfo,
    replyText: String
): Boolean {
    val remoteInput = action.remoteInput ?: return false
    val intent = action.actionIntent ?: return false
    if (replyText.isBlank()) return false

    return runCatching {
        val fillIn = Intent()
        val bundle = Bundle().apply {
            putCharSequence(remoteInput.resultKey, replyText.trim())
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, bundle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            intent.send(context, 0, fillIn, null, null, null, options.toBundle())
        } else {
            intent.send(context, 0, fillIn)
        }
        true
    }.getOrDefault(false)
}

fun primaryPendingIntent(activity: IslandActivity?): PendingIntent? = when (activity) {
    is IslandActivity.NotificationActivity -> activity.contentIntent
    is IslandActivity.MediaActivity -> activity.contentIntent
    is IslandActivity.CallActivity -> activity.contentIntent
    is IslandActivity.TimerActivity -> activity.contentIntent
    else -> null
}

/**
 * Ensures notification brand colors (such as WhatsApp's dark teal-green) maintain
 * crisp legibility on both dark and light island capsule surfaces.
 */
fun readableIslandAccent(
    rawColor: Int,
    fallback: Color,
    isLightSurface: Boolean
): Color {
    if (rawColor == 0xFF4F8CFF.toInt() || rawColor == 0) return fallback
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rawColor, hsl)
    if (hsl[1] < 0.12f) return fallback
    hsl[2] = if (isLightSurface) {
        hsl[2].coerceIn(0.26f, 0.44f)
    } else {
        hsl[2].coerceIn(0.56f, 0.72f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

fun activityAccentColor(activity: IslandActivity?, palette: IslandPalette): Color = when (activity) {
    is IslandActivity.MediaActivity -> palette.secondaryAccent
    is IslandActivity.CallActivity -> palette.positiveAccent
    is IslandActivity.TimerActivity -> palette.warningAccent
    is IslandActivity.ChargingPopup -> palette.positiveAccent
    is IslandActivity.BluetoothPopup -> palette.primaryAccent
    is IslandActivity.RingerPopup -> when (activity.mode) {
        RingerMode.SILENT -> palette.dangerAccent
        RingerMode.VIBRATE -> palette.warningAccent
        RingerMode.NORMAL -> palette.positiveAccent
    }
    is IslandActivity.NotificationActivity -> {
        readableIslandAccent(activity.accentColor, palette.primaryAccent, palette.isLightTheme)
    }
    null -> Color.Transparent
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (abs(ms) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun Bitmap.toSafeImageBitmap() = runCatching { asImageBitmap() }.getOrNull()

@Composable
fun AudioWaveformBars(
    isPlaying: Boolean,
    barColor: Color,
    maxHeight: Dp = 14.dp,
    barWidth: Dp = 2.4.dp,
    modifier: Modifier = Modifier
) {
    val barBrush = Brush.verticalGradient(
        colors = listOf(barColor, barColor.copy(alpha = 0.65f))
    )

    if (!isPlaying) {
        Row(
            modifier = modifier.height(maxHeight),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until 5) {
                val fraction = 0.24f + (index % 3) * 0.1f
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(maxHeight * fraction)
                        .clip(RoundedCornerShape(50))
                        .background(barBrush)
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "waveform")
    val bars = listOf(
        transition.animateFloat(
            initialValue = 0.24f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                tween(460, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "b1"
        ),
        transition.animateFloat(
            initialValue = 0.88f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                tween(370, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "b2"
        ),
        transition.animateFloat(
            initialValue = 0.32f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                tween(520, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "b3"
        ),
        transition.animateFloat(
            initialValue = 0.78f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                tween(410, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "b4"
        ),
        transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(
                tween(490, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "b5"
        )
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { anim ->
            val fraction by anim
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .clip(RoundedCornerShape(50))
                    .background(barBrush)
            )
        }
    }
}
