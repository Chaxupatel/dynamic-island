package com.dynamicisland.android.ui.overlay

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.model.BluetoothDeviceType
import com.dynamicisland.android.model.ChargeSource
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.NotificationActionInfo
import com.dynamicisland.android.model.RingerMode
import com.dynamicisland.android.ui.theme.IslandTheme
import kotlinx.coroutines.delay

@Composable
fun ExpandedIslandContent(
    state: IslandUiState,
    coordinator: IslandStateCoordinator,
    modifier: Modifier = Modifier
) {
    val primary = state.primary ?: return
    Column(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 620f
                )
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        when (primary) {
            is IslandActivity.MediaActivity -> ExpandedMediaCard(
                media = primary,
                coordinator = coordinator
            )
            is IslandActivity.NotificationActivity -> ExpandedNotificationCard(
                notif = primary,
                isInlineReplyOpen = state.isInlineReplyOpen,
                coordinator = coordinator
            )
            is IslandActivity.CallActivity -> ExpandedCallCard(
                call = primary,
                coordinator = coordinator
            )
            is IslandActivity.TimerActivity -> ExpandedTimerCard(
                timer = primary,
                coordinator = coordinator
            )
            is IslandActivity.ChargingPopup -> ExpandedChargingCard(
                charging = primary,
                coordinator = coordinator
            )
            is IslandActivity.BluetoothPopup -> ExpandedBluetoothCard(
                bluetooth = primary,
                coordinator = coordinator
            )
            is IslandActivity.RingerPopup -> ExpandedRingerCard(
                ringer = primary,
                coordinator = coordinator
            )
        }
    }
}

@Composable
private fun ExpandedMediaCard(
    media: IslandActivity.MediaActivity,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val context = LocalContext.current
    val art = remember(media.albumArt) { media.albumArt?.toSafeImageBitmap() }

    var livePositionMs by remember(media.positionMs, media.lastPositionUpdateMs, media.isPlaying) {
        mutableLongStateOf(media.positionMs)
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(media.positionMs, media.lastPositionUpdateMs, media.isPlaying, media.durationMs) {
        while (true) {
            val elapsed = if (media.isPlaying) {
                ((SystemClock.elapsedRealtime() - media.lastPositionUpdateMs) * media.playbackSpeed).toLong()
            } else {
                0L
            }
            livePositionMs = (media.positionMs + elapsed).coerceIn(0L, media.durationMs.coerceAtLeast(1L))
            if (!media.isPlaying) break
            delay(500L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                launchPendingIntent(context, media.contentIntent)
                coordinator.collapse()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .border(0.75.dp, palette.islandTextPrimary.copy(alpha = 0.16f), RoundedCornerShape(15.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(palette.secondaryAccent.copy(alpha = 0.14f))
                    .border(0.75.dp, palette.secondaryAccent.copy(alpha = 0.3f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = palette.secondaryAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.trackTitle,
                color = palette.islandTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = media.artist,
                color = palette.islandTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.secondaryAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = media.appName,
                    color = palette.secondaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        AudioWaveformBars(
            isPlaying = media.isPlaying,
            barColor = palette.secondaryAccent,
            maxHeight = 22.dp,
            barWidth = 3.dp
        )
    }

    if (media.durationMs > 0L) {
        Spacer(modifier = Modifier.height(12.dp))
        val progress = if (isDragging) {
            dragFraction
        } else {
            (livePositionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
        }
        SleekMediaScrubber(
            progress = progress,
            accentColor = palette.secondaryAccent,
            trackBg = palette.islandSurfaceChip,
            thumbColor = palette.islandTextPrimary,
            onSeekChange = { fraction ->
                isDragging = true
                dragFraction = fraction
            },
            onSeekCommit = { fraction ->
                isDragging = false
                coordinator.seekMediaTo((fraction * media.durationMs).toLong())
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(if (isDragging) (dragFraction * media.durationMs).toLong() else livePositionMs),
                color = palette.islandTextMuted,
                fontSize = 11.sp,
                style = TabularTextStyle
            )
            Text(
                text = formatDurationMs(media.durationMs),
                color = palette.islandTextMuted,
                fontSize = 11.sp,
                style = TabularTextStyle
            )
        }
    } else {
        Spacer(modifier = Modifier.height(10.dp))
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { coordinator.onMediaPrevious?.invoke() }) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = palette.islandTextPrimary.copy(alpha = 0.88f),
                modifier = Modifier.size(28.dp)
            )
        }

        FilledTonalIconButton(
            onClick = { coordinator.toggleMediaPlayPause() },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = palette.islandPrimaryButtonBg,
                contentColor = palette.islandPrimaryButtonFg
            ),
            modifier = Modifier.size(46.dp)
        ) {
            Icon(
                imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (media.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = { coordinator.onMediaNext?.invoke() }) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = palette.islandTextPrimary.copy(alpha = 0.88f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedNotificationCard(
    notif: IslandActivity.NotificationActivity,
    isInlineReplyOpen: Boolean,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val accent = readableIslandAccent(
        rawColor = notif.accentColor,
        fallback = palette.primaryAccent,
        isLightSurface = palette.isLightTheme
    )
    val context = LocalContext.current
    val avatar = remember(notif.largeIcon, notif.appIcon) {
        (notif.largeIcon ?: notif.appIcon)?.toSafeImageBitmap()
    }
    val smallAppIcon = remember(notif.appIcon, notif.largeIcon) {
        if (notif.largeIcon != null) notif.appIcon?.toSafeImageBitmap() else null
    }
    var activeReplyAction by remember(notif.id) { mutableStateOf<NotificationActionInfo?>(null) }
    var replyText by remember(notif.id) { mutableStateOf("") }
    val replyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isInlineReplyOpen, activeReplyAction) {
        if (isInlineReplyOpen && activeReplyAction != null) {
            runCatching { replyFocusRequester.requestFocus() }
        }
    }

    fun submitReply() {
        val sent = activeReplyAction?.let {
            sendRemoteInputReply(context, it, replyText)
        } ?: false
        if (sent) {
            replyText = ""
            activeReplyAction = null
            coordinator.setInlineReplyOpen(false)
            coordinator.collapse()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = notif.contentIntent != null) {
                launchPendingIntent(context, notif.contentIntent)
                coordinator.collapse()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(0.75.dp, palette.islandTextPrimary.copy(alpha = 0.16f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (smallAppIcon != null) {
                Image(
                    bitmap = smallAppIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .border(1.dp, palette.islandSurfaceBottom, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notif.appName.uppercase(),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = notif.title,
                color = palette.islandTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = {
                coordinator.onDismissNotification?.invoke(notif.id)
                coordinator.collapse()
            },
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(palette.islandSurfaceChip)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = palette.islandTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (notif.body.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.islandSurfaceChip)
                .border(0.5.dp, palette.islandTextPrimary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = notif.body,
                color = palette.islandTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (isInlineReplyOpen && activeReplyAction != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = { Text("Reply...", color = palette.islandTextMuted, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitReply() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.islandTextPrimary,
                    unfocusedTextColor = palette.islandTextPrimary,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = palette.islandTextPrimary.copy(alpha = 0.16f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(replyFocusRequester)
            )
            FilledTonalIconButton(
                onClick = { submitReply() },
                enabled = replyText.isNotBlank(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = accent,
                    contentColor = Color.White,
                    disabledContainerColor = palette.islandSurfaceChip,
                    disabledContentColor = palette.islandTextMuted
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val displayActions = remember(notif.actions) {
        notif.actions.sortedByDescending { it.hasRemoteInput }.take(2)
    }

    val renderNotificationAction: @Composable (NotificationActionInfo, Modifier) -> Unit =
        { action, itemModifier ->
            ActionPillChip(
                label = action.title,
                accentColor = accent,
                icon = if (action.hasRemoteInput) Icons.AutoMirrored.Rounded.Reply else null,
                onClick = {
                    if (action.hasRemoteInput) {
                        if (isInlineReplyOpen && activeReplyAction == action) {
                            coordinator.setInlineReplyOpen(false)
                        } else {
                            activeReplyAction = action
                            coordinator.setInlineReplyOpen(true)
                        }
                    } else {
                        launchPendingIntent(context, action.actionIntent)
                        coordinator.collapse()
                    }
                },
                modifier = itemModifier
            )
        }

    if (displayActions.size <= 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayActions.forEach { action ->
                renderNotificationAction(action, Modifier.weight(1f))
            }
            ActionPillChip(
                label = "Open",
                accentColor = palette.islandTextPrimary,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = {
                    launchPendingIntent(context, notif.contentIntent)
                    coordinator.collapse()
                },
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayActions.forEach { action ->
                renderNotificationAction(action, Modifier.defaultMinSize(minWidth = 68.dp))
            }
            ActionPillChip(
                label = "Open",
                accentColor = palette.islandTextPrimary,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = {
                    launchPendingIntent(context, notif.contentIntent)
                    coordinator.collapse()
                },
                modifier = Modifier.defaultMinSize(minWidth = 68.dp)
            )
        }
    }
}

@Composable
private fun ExpandedCallCard(
    call: IslandActivity.CallActivity,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val context = LocalContext.current
    val avatar = remember(call.avatar) { call.avatar?.toSafeImageBitmap() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                launchPendingIntent(context, call.contentIntent)
                coordinator.collapse()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.positiveAccent.copy(alpha = 0.5f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.positiveAccent.copy(alpha = 0.18f))
                    .border(0.75.dp, palette.positiveAccent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = palette.positiveAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.callerName,
                color = palette.islandTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = call.statusText,
                color = palette.islandTextMuted,
                fontSize = 12.sp
            )
        }

        LiveElapsedText(
            baseElapsedMs = call.startTimeMs,
            isCountDown = false,
            color = palette.positiveAccent,
            fontSizeSp = 16
        )
    }

    if (call.actions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            call.actions.forEach { action ->
                val isHangup = action.title.contains("end", ignoreCase = true) ||
                    action.title.contains("decline", ignoreCase = true) ||
                    action.title.contains("hang", ignoreCase = true)
                ActionPillChip(
                    label = action.title,
                    accentColor = if (isHangup) palette.dangerAccent else palette.positiveAccent,
                    onClick = {
                        launchPendingIntent(context, action.actionIntent)
                        coordinator.collapse()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExpandedTimerCard(
    timer: IslandActivity.TimerActivity,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                launchPendingIntent(context, timer.contentIntent)
                coordinator.collapse()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(palette.warningAccent.copy(alpha = 0.18f))
                .border(0.75.dp, palette.warningAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = palette.warningAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timer.label,
                color = palette.islandTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (timer.isCountDown) "Countdown Timer" else "Stopwatch",
                color = palette.islandTextMuted,
                fontSize = 12.sp
            )
        }

        LiveElapsedText(
            baseElapsedMs = timer.baseElapsedRealtimeMs,
            isCountDown = timer.isCountDown,
            color = palette.warningAccent,
            fontSizeSp = 24
        )
    }

    if (timer.actions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timer.actions.forEach { action ->
                ActionPillChip(
                    label = action.title,
                    accentColor = palette.warningAccent,
                    onClick = {
                        launchPendingIntent(context, action.actionIntent)
                        coordinator.collapse()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExpandedChargingCard(
    charging: IslandActivity.ChargingPopup,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val sourceLabel = when (charging.chargeSource) {
        ChargeSource.AC -> if (charging.isFastCharging) "Fast AC Charging" else "AC Charging"
        ChargeSource.USB -> "USB Charging"
        ChargeSource.WIRELESS -> "Wireless Charging"
        ChargeSource.UNKNOWN -> "Charging"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { coordinator.collapse() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(palette.positiveAccent.copy(alpha = 0.18f))
                .border(0.75.dp, palette.positiveAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = palette.positiveAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sourceLabel,
                color = palette.islandTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (charging.batteryPercent / 100f).coerceIn(0f, 1f) },
                color = palette.positiveAccent,
                trackColor = palette.islandSurfaceChip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "${charging.batteryPercent}%",
            color = palette.positiveAccent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = TabularTextStyle
        )
    }
}

@Composable
private fun ExpandedBluetoothCard(
    bluetooth: IslandActivity.BluetoothPopup,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val icon = when (bluetooth.deviceType) {
        BluetoothDeviceType.HEADPHONES -> Icons.Rounded.Headphones
        BluetoothDeviceType.WATCH -> Icons.Rounded.Watch
        BluetoothDeviceType.SPEAKER -> Icons.Rounded.Speaker
        BluetoothDeviceType.DEVICE -> Icons.Rounded.Bluetooth
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { coordinator.collapse() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(palette.primaryAccent.copy(alpha = 0.18f))
                .border(0.75.dp, palette.primaryAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.primaryAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bluetooth.deviceName,
                color = palette.islandTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (bluetooth.isConnected) "Bluetooth Connected" else "Disconnected",
                color = palette.islandTextMuted,
                fontSize = 12.sp
            )
        }
        bluetooth.batteryPercent?.let { pct ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.positiveAccent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$pct%",
                    color = palette.positiveAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    style = TabularTextStyle
                )
            }
        }
    }
}

@Composable
private fun ExpandedRingerCard(
    ringer: IslandActivity.RingerPopup,
    coordinator: IslandStateCoordinator
) {
    val palette = IslandTheme.palette
    val (icon, title, color) = when (ringer.mode) {
        RingerMode.SILENT -> Triple(Icons.Rounded.NotificationsOff, "Silent Mode", palette.dangerAccent)
        RingerMode.VIBRATE -> Triple(Icons.Rounded.Vibration, "Vibrate Mode", palette.warningAccent)
        RingerMode.NORMAL -> Triple(Icons.Rounded.Notifications, "Ring Mode", palette.positiveAccent)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { coordinator.collapse() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f))
                .border(0.75.dp, color.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = palette.islandTextPrimary,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionPillChip(
    label: String,
    accentColor: Color,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    horizontalPadding: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = accentColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(36.dp)
            .border(0.5.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SleekMediaScrubber(
    progress: Float,
    accentColor: Color,
    trackBg: Color,
    thumbColor: Color,
    onSeekChange: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit
) {
    val clamped = progress.coerceIn(0f, 1f)
    var latestFraction by remember { mutableFloatStateOf(clamped) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekCommit(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekChange(latestFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekChange(latestFraction)
                    },
                    onDragEnd = { onSeekCommit(latestFraction) },
                    onDragCancel = { onSeekCommit(latestFraction) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackHeight = 5.dp
        val thumbSize = 12.dp
        val maxOffsetPx = (constraints.maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { thumbSize.toPx() }).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(trackBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset((maxOffsetPx * clamped).toInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
