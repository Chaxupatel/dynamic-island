package com.dynamicisland.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.android.DynamicIslandApp
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.NotificationActionInfo

/**
 * Listens to system notifications, ongoing calls, active timers/chronometers,
 * and active MediaSessions to feed live updates into [IslandStateCoordinator].
 */
class IslandNotificationListenerService : NotificationListenerService() {

    private lateinit var coordinator: IslandStateCoordinator
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val componentName by lazy {
        ComponentName(this, IslandNotificationListenerService::class.java)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishCurrentMedia()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishCurrentMedia()
        }

        override fun onSessionDestroyed() {
            activeController = null
            coordinator.updateMedia(null)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBestController(controllers.orEmpty())
        }

    override fun onCreate() {
        super.onCreate()
        coordinator = (application as DynamicIslandApp).coordinator
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        wireCoordinatorActions()
        setupMediaTracking()
        runCatching {
            activeNotifications?.forEach { processNotification(it, isInitialScan = true) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        teardownMediaTracking()
        clearCoordinatorActions()
    }

    override fun onDestroy() {
        teardownMediaTracking()
        clearCoordinatorActions()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        processNotification(sbn, isInitialScan = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        coordinator.removeCall(sbn.key)
        coordinator.removeTimer(sbn.key)
        coordinator.onNotificationRemoved(sbn.key)
    }

    private fun processNotification(sbn: StatusBarNotification, isInitialScan: Boolean) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras

        // Skip MediaStyle notifications because MediaSessionManager provides richer live playback state
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return

        val actions = extractActions(notification)

        // 1. Calls
        if (notification.category == Notification.CATEGORY_CALL) {
            val caller = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: getAppLabel(sbn.packageName)
            val status = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Ongoing call"
            val startElapsed = toElapsedRealtime(notification.`when`)
            val isIncoming = notification.fullScreenIntent != null &&
                (notification.flags and Notification.FLAG_ONGOING_EVENT) == 0

            coordinator.upsertCall(
                IslandActivity.CallActivity(
                    id = sbn.key,
                    packageName = sbn.packageName,
                    callerName = caller,
                    statusText = status,
                    isIncoming = isIncoming,
                    startTimeMs = startElapsed,
                    avatar = loadIconBitmap(notification.getLargeIcon()),
                    contentIntent = notification.contentIntent,
                    actions = actions
                )
            )
            return
        }

        // 2. Ongoing Timers / Chronometers (only genuine timers/alarms/clock stopwatches, not VPN/recorder services)
        val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val isCountDown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        } else {
            false
        }
        val timerTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() } ?: getAppLabel(sbn.packageName)
        val looksLikeTimerOrClock = isCountDown ||
            notification.category == Notification.CATEGORY_ALARM ||
            notification.category == Notification.CATEGORY_STOPWATCH ||
            sbn.packageName.contains("clock", ignoreCase = true) ||
            timerTitle.contains("timer", ignoreCase = true) ||
            timerTitle.contains("stopwatch", ignoreCase = true)

        if (usesChronometer && notification.`when` > 0L && looksLikeTimerOrClock) {
            coordinator.upsertTimer(
                IslandActivity.TimerActivity(
                    id = sbn.key,
                    packageName = sbn.packageName,
                    label = timerTitle,
                    baseElapsedRealtimeMs = toElapsedRealtime(notification.`when`),
                    isCountDown = isCountDown,
                    contentIntent = notification.contentIntent,
                    actions = actions
                )
            )
            return
        } else {
            coordinator.removeTimer(sbn.key)
        }

        // 3. Standard incoming notifications (ignore persistent background ongoing services on initial scan)
        if (sbn.isOngoing || isInitialScan) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val appName = getAppLabel(sbn.packageName)
        val appIcon = loadAppIconBitmap(sbn.packageName) ?: loadIconBitmap(notification.smallIcon)
        val largeIcon = loadIconBitmap(notification.getLargeIcon())

        coordinator.onNotificationPosted(
            IslandActivity.NotificationActivity(
                id = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title.ifBlank { appName },
                body = body,
                timestampMs = sbn.postTime,
                appIcon = appIcon,
                largeIcon = largeIcon,
                contentIntent = notification.contentIntent,
                actions = actions,
                accentColor = notification.color.takeIf { it != Notification.COLOR_DEFAULT }
                    ?: 0xFF4F8CFF.toInt()
            )
        )
    }

    private fun extractActions(notification: Notification): List<NotificationActionInfo> {
        val rawActions = notification.actions ?: return emptyList()
        return rawActions.take(3).mapNotNull { action ->
            val label = action.title?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            NotificationActionInfo(
                title = label,
                actionIntent = action.actionIntent,
                remoteInput = action.remoteInputs?.firstOrNull()
            )
        }
    }

    private fun setupMediaTracking() {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        mediaSessionManager = manager
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            bindBestController(manager.getActiveSessions(componentName))
        }
    }

    private fun teardownMediaTracking() {
        activeController?.unregisterCallback(mediaCallback)
        activeController = null
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        mediaSessionManager = null
    }

    private fun bindBestController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            val state = it.playbackState?.state
            state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull()

        if (chosen?.sessionToken == activeController?.sessionToken) {
            publishCurrentMedia()
            return
        }

        activeController?.unregisterCallback(mediaCallback)
        activeController = chosen
        chosen?.registerCallback(mediaCallback)
        publishCurrentMedia()
    }

    private fun publishCurrentMedia() {
        val controller = activeController
        val metadata = controller?.metadata
        val playbackState = controller?.playbackState
        if (controller == null || metadata == null || playbackState == null) {
            coordinator.updateMedia(null)
            return
        }

        val state = playbackState.state
        if (state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
            coordinator.updateMedia(null)
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Playing Media"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: getAppLabel(controller.packageName)

        val rawArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val scaledArt = rawArt?.let { scaleBitmap(it, 160) }

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val position = playbackState.position.coerceAtLeast(0L)
        val lastUpdate = playbackState.lastPositionUpdateTime.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()

        coordinator.updateMedia(
            IslandActivity.MediaActivity(
                packageName = controller.packageName,
                appName = getAppLabel(controller.packageName),
                trackTitle = title,
                artist = artist,
                albumArt = scaledArt,
                isPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
                positionMs = position,
                durationMs = duration,
                lastPositionUpdateMs = lastUpdate,
                playbackSpeed = playbackState.playbackSpeed.takeIf { it > 0f } ?: 1f,
                contentIntent = controller.sessionActivity
            )
        )
    }

    private fun wireCoordinatorActions() {
        coordinator.onMediaPlayPause = {
            val ctrl = activeController
            val state = ctrl?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                ctrl.transportControls.pause()
            } else {
                ctrl?.transportControls?.play()
            }
        }
        coordinator.onMediaNext = { activeController?.transportControls?.skipToNext() }
        coordinator.onMediaPrevious = { activeController?.transportControls?.skipToPrevious() }
        coordinator.onMediaSeekTo = { posMs -> activeController?.transportControls?.seekTo(posMs) }
        coordinator.onDismissNotification = { key ->
            runCatching { cancelNotification(key) }
            coordinator.onNotificationRemoved(key)
        }
    }

    private fun clearCoordinatorActions() {
        coordinator.onMediaPlayPause = null
        coordinator.onMediaNext = null
        coordinator.onMediaPrevious = null
        coordinator.onMediaSeekTo = null
        coordinator.onDismissNotification = null
    }

    private fun toElapsedRealtime(epochWhenMs: Long): Long {
        if (epochWhenMs <= 0L) return SystemClock.elapsedRealtime()
        return SystemClock.elapsedRealtime() + (epochWhenMs - System.currentTimeMillis())
    }

    private fun getAppLabel(pkg: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg)
    }

    private fun loadAppIconBitmap(pkg: String): Bitmap? {
        return runCatching {
            drawableToBitmap(packageManager.getApplicationIcon(pkg))
        }.getOrNull()
    }

    private fun loadIconBitmap(icon: Icon?): Bitmap? {
        icon ?: return null
        return runCatching {
            icon.loadDrawable(this)?.let { drawableToBitmap(it) }
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleBitmap(drawable.bitmap, 128)
        }
        val width = drawable.intrinsicWidth.coerceIn(48, 128)
        val height = drawable.intrinsicHeight.coerceIn(48, 128)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        if (source.width <= maxDim && source.height <= maxDim) return source
        val ratio = minOf(maxDim.toFloat() / source.width, maxDim.toFloat() / source.height)
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}

