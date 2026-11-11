package com.dynamicisland.android.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    private data class CachedMediaNotification(
        val key: String,
        val packageName: String,
        val token: MediaSession.Token?,
        val title: String?,
        val artist: String?,
        val albumArt: Bitmap?,
        val contentIntent: PendingIntent?
    )

    private lateinit var coordinator: IslandStateCoordinator
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val trackedControllers = LinkedHashMap<MediaSession.Token, MediaController>()
    private val mediaNotifications = LinkedHashMap<String, CachedMediaNotification>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val componentName by lazy {
        ComponentName(this, IslandNotificationListenerService::class.java)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            selectAndPublishBestController()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            selectAndPublishBestController()
        }

        override fun onSessionDestroyed() {
            refreshActiveMediaSessions()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            syncTrackedControllers(controllers.orEmpty())
        }

    override fun onCreate() {
        super.onCreate()
        coordinator = (application as DynamicIslandApp).coordinator
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        wireCoordinatorActions()
        runCatching {
            activeNotifications?.forEach { processNotification(it, isInitialScan = true) }
        }
        setupMediaTracking()
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
        if (mediaNotifications.remove(sbn.key) != null) {
            refreshActiveMediaSessions()
        }
        coordinator.removeCall(sbn.key)
        coordinator.removeTimer(sbn.key)
        coordinator.onNotificationRemoved(sbn.key)
    }

    private fun processNotification(sbn: StatusBarNotification, isInitialScan: Boolean) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras

        // Cache MediaStyle notifications for metadata fallback and trigger a session sync
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            val art = loadIconBitmap(notification.getLargeIcon())?.let { scaleBitmap(it, 160) }
            mediaNotifications[sbn.key] = CachedMediaNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                token = token,
                title = title,
                artist = artist,
                albumArt = art,
                contentIntent = notification.contentIntent
            )
            if (!isInitialScan) {
                refreshActiveMediaSessions()
            }
            return
        }

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
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName, mainHandler)
            syncTrackedControllers(manager.getActiveSessions(componentName))
        }
    }

    private fun teardownMediaTracking() {
        trackedControllers.values.forEach { controller ->
            runCatching { controller.unregisterCallback(mediaCallback) }
        }
        trackedControllers.clear()
        mediaNotifications.clear()
        activeController = null
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        mediaSessionManager = null
    }

    private fun refreshActiveMediaSessions() {
        val manager = mediaSessionManager
            ?: (getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)?.also {
                mediaSessionManager = it
            }
        val active = runCatching {
            manager?.getActiveSessions(componentName).orEmpty()
        }.getOrDefault(emptyList())
        syncTrackedControllers(active)
    }

    private fun syncTrackedControllers(managerControllers: List<MediaController>) {
        val mergedByToken = LinkedHashMap<MediaSession.Token, MediaController>()
        managerControllers.forEach { controller ->
            mergedByToken[controller.sessionToken] = controller
        }
        mediaNotifications.values.forEach { cached ->
            val token = cached.token ?: return@forEach
            if (!mergedByToken.containsKey(token)) {
                val existing = trackedControllers[token]
                    ?: runCatching { MediaController(this, token) }.getOrNull()
                if (existing != null) {
                    mergedByToken[token] = existing
                }
            }
        }

        val iterator = trackedControllers.entries.iterator()
        while (iterator.hasNext()) {
            val (token, controller) = iterator.next()
            if (!mergedByToken.containsKey(token)) {
                runCatching { controller.unregisterCallback(mediaCallback) }
                iterator.remove()
            }
        }

        mergedByToken.forEach { (token, controller) ->
            if (!trackedControllers.containsKey(token)) {
                runCatching { controller.registerCallback(mediaCallback, mainHandler) }
                trackedControllers[token] = controller
            }
        }

        selectAndPublishBestController()
    }

    private fun selectAndPublishBestController() {
        val controllers = trackedControllers.values.toList()
        val chosen = controllers.firstOrNull {
            isActivePlaybackState(it.playbackState?.state)
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        } ?: controllers.firstOrNull { controller ->
            findNotificationFallback(controller.sessionToken, controller.packageName) != null
        }

        activeController = chosen
        publishCurrentMedia()
    }

    private fun isActivePlaybackState(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun findNotificationFallback(
        token: MediaSession.Token?,
        packageName: String
    ): CachedMediaNotification? {
        if (token != null) {
            mediaNotifications.values.lastOrNull { it.token == token }?.let { return it }
        }
        return mediaNotifications.values.lastOrNull { it.packageName == packageName }
    }

    private fun publishCurrentMedia() {
        val controller = activeController
        if (controller == null) {
            coordinator.updateMedia(null)
            return
        }

        val playbackState = controller.playbackState
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        val fallback = findNotificationFallback(controller.sessionToken, controller.packageName)

        if ((state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) && fallback == null) {
            coordinator.updateMedia(null)
            return
        }

        val metadata = controller.metadata
        val appLabel = getAppLabel(controller.packageName)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            ?: fallback?.title
            ?: appLabel
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: fallback?.artist
            ?: appLabel

        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val scaledArt = rawArt?.let { scaleBitmap(it, 160) } ?: fallback?.albumArt

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val position = playbackState?.position?.coerceAtLeast(0L) ?: 0L
        val lastUpdate = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L }
            ?: SystemClock.elapsedRealtime()
        val isPlaying = isActivePlaybackState(state) || (playbackState == null && fallback != null)

        coordinator.updateMedia(
            IslandActivity.MediaActivity(
                packageName = controller.packageName,
                appName = appLabel,
                trackTitle = title,
                artist = artist,
                albumArt = scaledArt,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration,
                lastPositionUpdateMs = lastUpdate,
                playbackSpeed = playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                contentIntent = controller.sessionActivity ?: fallback?.contentIntent
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

