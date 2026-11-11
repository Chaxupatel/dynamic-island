package com.dynamicisland.android.core

import android.os.SystemClock
import com.dynamicisland.android.model.BluetoothDeviceType
import com.dynamicisland.android.model.ChargeSource
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.RingerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central state machine for the Dynamic Island overlay.
 * Merges notifications, active media sessions, calls, timers, and system popups
 * into a single deterministic [IslandUiState].
 */
class IslandStateCoordinator(
    private val scope: CoroutineScope,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private data class InternalState(
        val calls: Map<String, IslandActivity.CallActivity> = emptyMap(),
        val media: IslandActivity.MediaActivity? = null,
        val timers: Map<String, IslandActivity.TimerActivity> = emptyMap(),
        val recentNotification: IslandActivity.NotificationActivity? = null,
        val transientPopup: IslandActivity? = null,
        val transientPopupIsPreview: Boolean = false,
        val activePreviewKind: PreviewKind? = null,
        val isPreviewPinned: Boolean = true,
        val isExpanded: Boolean = false,
        val isInlineReplyOpen: Boolean = false,
        val isLandscape: Boolean = false,
        val isScreenLocked: Boolean = false,
        val rotationOffset: Int = 0,
        val settings: IslandSettings = IslandSettings()
    )

    private var internal = InternalState()
    private var popupDismissJob: Job? = null
    private var pausedMediaTimeoutJob: Job? = null

    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    // Callback hooks wired by services so the UI can dispatch real system actions
    var onMediaPlayPause: (() -> Unit)? = null
    var onMediaNext: (() -> Unit)? = null
    var onMediaPrevious: (() -> Unit)? = null
    var onMediaSeekTo: ((Long) -> Unit)? = null
    var onDismissNotification: ((String) -> Unit)? = null

    fun updateSettings(settings: IslandSettings) {
        internal = if (!settings.isEnabled) {
            popupDismissJob?.cancel()
            pausedMediaTimeoutJob?.cancel()
            internal.copy(
                settings = settings,
                transientPopup = null,
                transientPopupIsPreview = false,
                activePreviewKind = null,
                isExpanded = false,
                isInlineReplyOpen = false
            )
        } else {
            internal.copy(settings = settings)
        }
        publish()
    }

    fun setLandscape(isLandscape: Boolean) {
        if (internal.isLandscape == isLandscape) return
        internal = internal.copy(isLandscape = isLandscape, isExpanded = false, isInlineReplyOpen = false)
        publish()
    }

    fun setScreenLocked(isLocked: Boolean) {
        if (internal.isScreenLocked == isLocked) return
        internal = internal.copy(isScreenLocked = isLocked, isExpanded = false, isInlineReplyOpen = false)
        publish()
    }

    fun upsertCall(call: IslandActivity.CallActivity) {
        val updated = internal.calls.toMutableMap().apply { put(call.id, call) }
        internal = internal.copy(calls = updated)
        if (call.isIncoming && internal.settings.enableCallsAndTimers) {
            showTransientPopup(call, durationMs = 8000L)
        } else {
            publish()
        }
    }

    fun removeCall(id: String) {
        if (!internal.calls.containsKey(id)) return
        val updated = internal.calls.toMutableMap().apply { remove(id) }
        internal = internal.copy(
            calls = updated,
            transientPopup = if (internal.transientPopup?.id == id) null else internal.transientPopup
        )
        publish()
    }

    fun updateMedia(media: IslandActivity.MediaActivity?) {
        pausedMediaTimeoutJob?.cancel()
        internal = internal.copy(media = media)
        if (media == null && internal.transientPopup is IslandActivity.MediaActivity) {
            internal = internal.copy(transientPopup = null, isExpanded = false)
        } else if (media != null && !media.isPlaying && media.id != "preview_media") {
            // Clear stale paused sessions so Hide When Idle works consistently when playback stops
            pausedMediaTimeoutJob = scope.launch {
                delay(12_000L)
                val current = internal.media
                if (current != null && !current.isPlaying && current.id == media.id && !internal.isExpanded) {
                    internal = internal.copy(media = null)
                    publish()
                }
            }
        }
        publish()
    }

    fun upsertTimer(timer: IslandActivity.TimerActivity) {
        val updated = internal.timers.toMutableMap().apply { put(timer.id, timer) }
        internal = internal.copy(timers = updated)
        publish()
    }

    fun removeTimer(id: String) {
        if (!internal.timers.containsKey(id)) return
        val updated = internal.timers.toMutableMap().apply { remove(id) }
        internal = internal.copy(timers = updated)
        publish()
    }

    fun onNotificationPosted(notification: IslandActivity.NotificationActivity) {
        internal = internal.copy(recentNotification = notification)
        if (internal.settings.enableNotifications) {
            showTransientPopup(notification, internal.settings.popupDurationMs)
        } else {
            publish()
        }
    }

    fun onNotificationRemoved(id: String) {
        val clearRecent = internal.recentNotification?.id == id
        val clearPopup = internal.transientPopup?.id == id
        if (!clearRecent && !clearPopup) return
        internal = internal.copy(
            recentNotification = if (clearRecent) null else internal.recentNotification,
            transientPopup = if (clearPopup) null else internal.transientPopup,
            isExpanded = if (clearPopup) false else internal.isExpanded,
            isInlineReplyOpen = if (clearPopup) false else internal.isInlineReplyOpen
        )
        publish()
    }

    fun showSystemPopup(popup: IslandActivity) {
        if (!internal.settings.enableSystemPopups) return
        showTransientPopup(popup, internal.settings.popupDurationMs)
    }

    fun toggleExpanded() {
        val current = _uiState.value
        when (current.mode) {
            IslandVisualMode.EXPANDED -> collapse()
            IslandVisualMode.COMPACT, IslandVisualMode.POPUP -> {
                popupDismissJob?.cancel()
                internal = internal.copy(isExpanded = true)
                publish()
            }
            IslandVisualMode.IDLE -> {
                val peek = buildOngoingActivities().firstOrNull() ?: internal.recentNotification
                if (peek != null) {
                    internal = internal.copy(transientPopup = peek, isExpanded = true)
                    publish()
                }
            }
            IslandVisualMode.HIDDEN -> Unit
        }
    }

    fun collapse() {
        if (!internal.isExpanded && internal.transientPopup == null && !internal.isInlineReplyOpen) return
        val wasExpanded = internal.isExpanded
        internal = internal.copy(isExpanded = false, isInlineReplyOpen = false)
        if (internal.transientPopup != null) {
            val keepPinnedPreview = wasExpanded && internal.isPreviewPinned && internal.transientPopupIsPreview
            if (!keepPinnedPreview) {
                popupDismissJob?.cancel()
                internal = internal.copy(
                    transientPopup = null,
                    transientPopupIsPreview = false,
                    activePreviewKind = if (internal.transientPopupIsPreview) null else internal.activePreviewKind
                )
            }
        }
        publish()
    }

    fun setPreviewPinned(pinned: Boolean) {
        if (internal.isPreviewPinned == pinned) return
        internal = internal.copy(isPreviewPinned = pinned)
        if (!pinned && internal.transientPopupIsPreview && internal.transientPopup != null && !internal.isExpanded) {
            val popup = internal.transientPopup!!
            schedulePopupDismiss(popup.id, internal.settings.popupDurationMs)
        } else if (pinned && internal.transientPopupIsPreview) {
            popupDismissJob?.cancel()
        }
        publish()
    }

    fun isShowingPinnedPreview(): Boolean =
        internal.isPreviewPinned && internal.activePreviewKind != null

    fun previewFeature(kind: IslandFeatureKind) {
        val previewKind = when (kind) {
            IslandFeatureKind.IDLE -> PreviewKind.CLEAR_PREVIEWS
            IslandFeatureKind.NOTIFICATION -> PreviewKind.NOTIFICATION
            IslandFeatureKind.MEDIA -> PreviewKind.MEDIA
            IslandFeatureKind.CHARGING -> PreviewKind.CHARGING
            IslandFeatureKind.BLUETOOTH -> PreviewKind.BLUETOOTH
            IslandFeatureKind.TIMER -> PreviewKind.TIMER
            IslandFeatureKind.CALL -> PreviewKind.CALL
            IslandFeatureKind.RINGER -> PreviewKind.RINGER
        }
        triggerPreview(previewKind)
    }

    fun setInlineReplyOpen(open: Boolean) {
        if (internal.isInlineReplyOpen == open) return
        internal = internal.copy(isInlineReplyOpen = open)
        publish()
    }

    fun toggleMediaPlayPause() {
        val media = internal.media
        if (media?.id == "preview_media") {
            val now = elapsedRealtimeProvider()
            val elapsed = if (media.isPlaying) (now - media.lastPositionUpdateMs) else 0L
            val nextPos = (media.positionMs + elapsed).coerceIn(0L, media.durationMs)
            updateMedia(
                media.copy(
                    isPlaying = !media.isPlaying,
                    positionMs = nextPos,
                    lastPositionUpdateMs = now
                )
            )
        } else {
            onMediaPlayPause?.invoke()
        }
    }

    fun seekMediaTo(positionMs: Long) {
        val media = internal.media
        if (media?.id == "preview_media") {
            updateMedia(
                media.copy(
                    positionMs = positionMs.coerceIn(0L, media.durationMs),
                    lastPositionUpdateMs = elapsedRealtimeProvider()
                )
            )
        } else {
            onMediaSeekTo?.invoke(positionMs)
        }
    }

    fun dismissCurrentPopupOrCycle(direction: Int) {
        if (internal.transientPopup != null && !internal.isExpanded) {
            popupDismissJob?.cancel()
            internal = internal.copy(
                transientPopup = null,
                transientPopupIsPreview = false,
                activePreviewKind = if (internal.transientPopupIsPreview) null else internal.activePreviewKind
            )
            publish()
            return
        }
        val ongoing = buildOngoingActivities()
        if (ongoing.size > 1) {
            val next = (internal.rotationOffset + direction).mod(ongoing.size)
            internal = internal.copy(rotationOffset = next)
            publish()
        }
    }

    private fun clearPreviewState() {
        popupDismissJob?.cancel()
        val calls = internal.calls.toMutableMap().apply { remove("preview_call") }
        val timers = internal.timers.toMutableMap().apply { remove("preview_timer") }
        val media = if (internal.media?.id == "preview_media") null else internal.media
        val recentNotif = if (internal.recentNotification?.id == "preview_notif") null else internal.recentNotification
        val popup = if (internal.transientPopupIsPreview) null else internal.transientPopup
        internal = internal.copy(
            calls = calls,
            timers = timers,
            media = media,
            recentNotification = recentNotif,
            transientPopup = popup,
            transientPopupIsPreview = false,
            activePreviewKind = null,
            isExpanded = false,
            isInlineReplyOpen = false
        )
    }

    fun triggerPreview(kind: PreviewKind) {
        clearPreviewState()
        if (kind == PreviewKind.CLEAR_PREVIEWS) {
            publish()
            return
        }

        internal = internal.copy(activePreviewKind = kind)
        val now = elapsedRealtimeProvider()
        val autoDismiss = !internal.isPreviewPinned
        when (kind) {
            PreviewKind.NOTIFICATION -> {
                val notification = IslandActivity.NotificationActivity(
                    id = "preview_notif",
                    packageName = "com.dynamicisland.android",
                    appName = "Messages",
                    title = "Alex",
                    body = "Hey! Are we still on for coffee at 5?",
                    timestampMs = System.currentTimeMillis()
                )
                internal = internal.copy(recentNotification = notification)
                showTransientPopup(
                    activity = notification,
                    durationMs = internal.settings.popupDurationMs,
                    isPreview = true,
                    autoDismiss = autoDismiss
                )
            }
            PreviewKind.MEDIA -> updateMedia(
                IslandActivity.MediaActivity(
                    id = "preview_media",
                    packageName = "com.dynamicisland.android",
                    appName = "Music",
                    trackTitle = "Midnight City",
                    artist = "M83",
                    isPlaying = true,
                    positionMs = 74_000L,
                    durationMs = 243_000L,
                    lastPositionUpdateMs = now
                )
            )
            PreviewKind.CHARGING -> showTransientPopup(
                activity = IslandActivity.ChargingPopup(
                    batteryPercent = 84,
                    isCharging = true,
                    chargeSource = ChargeSource.AC,
                    isFastCharging = true
                ),
                durationMs = internal.settings.popupDurationMs,
                isPreview = true,
                autoDismiss = autoDismiss
            )
            PreviewKind.BLUETOOTH -> showTransientPopup(
                activity = IslandActivity.BluetoothPopup(
                    deviceName = "Pixel Buds Pro",
                    batteryPercent = 92,
                    isConnected = true,
                    deviceType = BluetoothDeviceType.HEADPHONES
                ),
                durationMs = internal.settings.popupDurationMs,
                isPreview = true,
                autoDismiss = autoDismiss
            )
            PreviewKind.TIMER -> upsertTimer(
                IslandActivity.TimerActivity(
                    id = "preview_timer",
                    packageName = "com.dynamicisland.android",
                    label = "Pasta Timer",
                    baseElapsedRealtimeMs = now + 5 * 60_000L,
                    isCountDown = true
                )
            )
            PreviewKind.CALL -> upsertCall(
                IslandActivity.CallActivity(
                    id = "preview_call",
                    packageName = "com.dynamicisland.android",
                    callerName = "Samira Patel",
                    statusText = "Ongoing call",
                    isIncoming = false,
                    startTimeMs = now - 42_000L
                )
            )
            PreviewKind.RINGER -> showTransientPopup(
                activity = IslandActivity.RingerPopup(mode = RingerMode.SILENT),
                durationMs = internal.settings.popupDurationMs,
                isPreview = true,
                autoDismiss = autoDismiss
            )
            PreviewKind.CLEAR_PREVIEWS -> Unit
        }
    }

    private fun showTransientPopup(
        activity: IslandActivity,
        durationMs: Long,
        isPreview: Boolean = false,
        autoDismiss: Boolean = true
    ) {
        popupDismissJob?.cancel()
        internal = internal.copy(
            transientPopup = activity,
            transientPopupIsPreview = isPreview
        )
        publish()
        if (autoDismiss) {
            schedulePopupDismiss(activity.id, durationMs)
        }
    }

    private fun schedulePopupDismiss(activityId: String, durationMs: Long) {
        popupDismissJob?.cancel()
        popupDismissJob = scope.launch {
            delay(durationMs.coerceAtLeast(1500L))
            if (!internal.isExpanded && internal.transientPopup?.id == activityId) {
                internal = internal.copy(
                    transientPopup = null,
                    transientPopupIsPreview = false,
                    activePreviewKind = if (internal.transientPopupIsPreview) null else internal.activePreviewKind
                )
                publish()
            }
        }
    }

    private fun buildOngoingActivities(): List<IslandActivity> {
        val list = mutableListOf<IslandActivity>()
        val cfg = internal.settings
        if (cfg.enableCallsAndTimers) {
            list.addAll(internal.calls.values)
        }
        if (cfg.enableMediaPlayer) {
            internal.media?.let { list.add(it) }
        }
        if (cfg.enableCallsAndTimers) {
            list.addAll(internal.timers.values)
        }
        return list
    }

    private fun publish() {
        val cfg = internal.settings
        val hiddenByContext = !cfg.isEnabled ||
            (internal.isLandscape && cfg.hideInLandscape) ||
            (internal.isScreenLocked && !cfg.showOnLockscreen)

        if (hiddenByContext) {
            _uiState.value = IslandUiState(
                mode = IslandVisualMode.HIDDEN,
                isLandscape = internal.isLandscape,
                isScreenLocked = internal.isScreenLocked,
                isPreviewPinned = internal.isPreviewPinned,
                settings = cfg
            )
            return
        }

        val ongoing = buildOngoingActivities()
        val popup = internal.transientPopup

        val nextState = when {
            internal.isExpanded -> {
                val primary = popup ?: ongoing.getOrNull(
                    if (ongoing.isEmpty()) 0 else internal.rotationOffset.mod(ongoing.size)
                ) ?: internal.recentNotification

                if (primary != null) {
                    val secondary = ongoing.firstOrNull { it.id != primary.id }
                    IslandUiState(
                        mode = IslandVisualMode.EXPANDED,
                        primary = primary,
                        secondary = secondary,
                        isLandscape = internal.isLandscape,
                        isScreenLocked = internal.isScreenLocked,
                        isInlineReplyOpen = internal.isInlineReplyOpen,
                        isPreviewPinned = internal.isPreviewPinned,
                        settings = cfg
                    )
                } else {
                    idleOrHiddenState(cfg)
                }
            }
            popup != null -> IslandUiState(
                mode = IslandVisualMode.POPUP,
                primary = popup,
                secondary = null,
                isLandscape = internal.isLandscape,
                isScreenLocked = internal.isScreenLocked,
                isPreviewPinned = internal.isPreviewPinned,
                settings = cfg
            )
            ongoing.isNotEmpty() -> {
                val idx = internal.rotationOffset.mod(ongoing.size)
                val primary = ongoing[idx]
                val secondary = if (ongoing.size > 1) ongoing[(idx + 1).mod(ongoing.size)] else null
                IslandUiState(
                    mode = IslandVisualMode.COMPACT,
                    primary = primary,
                    secondary = secondary,
                    isLandscape = internal.isLandscape,
                    isScreenLocked = internal.isScreenLocked,
                    isPreviewPinned = internal.isPreviewPinned,
                    settings = cfg
                )
            }
            else -> idleOrHiddenState(cfg)
        }

        _uiState.value = nextState
    }

    private fun idleOrHiddenState(cfg: IslandSettings): IslandUiState = IslandUiState(
        mode = if (cfg.hideWhenIdle) IslandVisualMode.HIDDEN else IslandVisualMode.IDLE,
        isLandscape = internal.isLandscape,
        isScreenLocked = internal.isScreenLocked,
        isPreviewPinned = internal.isPreviewPinned,
        settings = cfg
    )

    enum class PreviewKind {
        NOTIFICATION,
        MEDIA,
        CHARGING,
        BLUETOOTH,
        TIMER,
        CALL,
        RINGER,
        CLEAR_PREVIEWS
    }
}

