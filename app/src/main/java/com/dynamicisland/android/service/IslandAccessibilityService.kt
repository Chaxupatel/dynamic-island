package com.dynamicisland.android.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicisland.android.DynamicIslandApp
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.core.PermissionHelper
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.featureKind
import com.dynamicisland.android.ui.overlay.DynamicIslandOverlay
import com.dynamicisland.android.ui.theme.DynamicIslandTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Draws the Dynamic Island overlay window using [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 * Dynamically updates window position, cutout mode, and focusability so the status bar
 * remains responsive and inline quick-reply works smoothly when expanded.
 */
class IslandAccessibilityService : AccessibilityService() {

    private lateinit var coordinator: IslandStateCoordinator
    private lateinit var windowManager: WindowManager
    private lateinit var systemEventsMonitor: SystemEventsMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectJob: Job? = null
    private var hideWindowJob: Job? = null
    private var shrinkWindowJob: Job? = null

    private var lifecycleOwner: IslandLifecycleOwner? = null
    private var overlayView: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator = (application as DynamicIslandApp).coordinator
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        updateOrientation(resources.configuration)

        systemEventsMonitor = SystemEventsMonitor(this, coordinator).apply { start() }
        attachOverlayWindow()

        if (PermissionHelper.isNotificationListenerEnabled(this)) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(this, IslandNotificationListenerService::class.java)
                )
            }
        }

        stateCollectJob = serviceScope.launch {
            coordinator.uiState.collect { state ->
                syncWindowParams(state)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: this service only renders the Dynamic Island overlay.
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    override fun onDestroy() {
        stateCollectJob?.cancel()
        hideWindowJob?.cancel()
        shrinkWindowJob?.cancel()
        serviceScope.cancel()
        if (::systemEventsMonitor.isInitialized) {
            systemEventsMonitor.stop()
        }
        detachOverlayWindow()
        super.onDestroy()
    }

    private fun updateOrientation(config: Configuration) {
        coordinator.setLandscape(config.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun attachOverlayWindow() {
        if (overlayView != null) return

        val owner = IslandLifecycleOwner().apply {
            onCreate()
            onStart()
        }
        lifecycleOwner = owner

        val params = createBaseLayoutParams(coordinator.uiState.value)
        layoutParams = params

        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by coordinator.uiState.collectAsState()
                DynamicIslandTheme(themeId = state.settings.theme) {
                    DynamicIslandOverlay(
                        state = state,
                        coordinator = coordinator
                    )
                }
            }
        }
        composeView = compose

        val container = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val isExpanded = coordinator.uiState.value.mode == IslandVisualMode.EXPANDED
                val canAutoCollapse = isExpanded && !coordinator.isShowingPinnedPreview()
                if (canAutoCollapse && ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    coordinator.collapse()
                    return true
                }
                if (canAutoCollapse && ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    val cv = composeView
                    if (cv != null &&
                        (ev.x < cv.left || ev.x > cv.right || ev.y < cv.top || ev.y > cv.bottom)
                    ) {
                        coordinator.collapse()
                        return true
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            )
        }

        overlayView = container
        windowManager.addView(container, params)
    }

    private fun detachOverlayWindow() {
        val view = overlayView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        lifecycleOwner?.onStop()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        composeView = null
        overlayView = null
        layoutParams = null
    }

    private fun createBaseLayoutParams(state: IslandUiState): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            computeFlags(state),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dpToPx(state.settings.offsetXdp)
            y = dpToPx(state.settings.offsetYdp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun computeFlags(state: IslandUiState): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        if (state.mode == IslandVisualMode.HIDDEN) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        // Keep window non-focusable so back button & keyboard work normally in underlying apps,
        // only requesting focus when the user opens the inline quick-reply field.
        if (!state.isInlineReplyOpen) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return flags
    }

    private fun syncWindowParams(state: IslandUiState) {
        val view = overlayView ?: return
        val params = layoutParams ?: return

        val nextX = dpToPx(state.settings.offsetXdp)
        val nextY = dpToPx(state.settings.offsetYdp)
        val nextFlags = computeFlags(state)

        if (state.mode == IslandVisualMode.HIDDEN) {
            shrinkWindowJob?.cancel()
            shrinkWindowJob = null
            if (hideWindowJob?.isActive != true && view.visibility != android.view.View.GONE) {
                hideWindowJob = serviceScope.launch {
                    // Wait for the Compose scale+fade exit animation before collapsing the window surface
                    delay(190L)
                    if (coordinator.uiState.value.mode == IslandVisualMode.HIDDEN) {
                        view.visibility = android.view.View.GONE
                        params.width = 0
                        params.height = 0
                        params.flags = computeFlags(coordinator.uiState.value)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }
            }
            return
        }

        hideWindowJob?.cancel()
        hideWindowJob = null

        var needsRelayout = false
        if (view.visibility != android.view.View.VISIBLE) {
            view.visibility = android.view.View.VISIBLE
        }

        if (state.mode == IslandVisualMode.EXPANDED) {
            shrinkWindowJob?.cancel()
            shrinkWindowJob = null

            val activeKind = state.primary?.featureKind() ?: IslandFeatureKind.IDLE
            val expandedW = state.settings.sizeFor(activeKind).expandedWidthDp.coerceAtLeast(300f) + 32f
            val targetW = dpToPx(expandedW)
            val targetH = dpToPx(416f)
            if (params.width != targetW || params.height != targetH) {
                params.width = targetW
                params.height = targetH
                needsRelayout = true
            }
        } else {
            val isStillExpandedWindow = params.width > 0 || params.height > 0
            if (isStillExpandedWindow) {
                if (shrinkWindowJob?.isActive != true) {
                    shrinkWindowJob = serviceScope.launch {
                        // Keep fixed window bounds while the collapse spring finishes to avoid per-frame relayout jank
                        delay(260L)
                        val latest = coordinator.uiState.value
                        if (latest.mode != IslandVisualMode.EXPANDED && latest.mode != IslandVisualMode.HIDDEN) {
                            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            params.flags = computeFlags(latest)
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                    }
                }
            } else if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT ||
                params.height != ViewGroup.LayoutParams.WRAP_CONTENT
            ) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                needsRelayout = true
            }
        }

        if (params.x != nextX || params.y != nextY || params.flags != nextFlags) {
            params.x = nextX
            params.y = nextY
            params.flags = nextFlags
            needsRelayout = true
        }
        if (needsRelayout) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
