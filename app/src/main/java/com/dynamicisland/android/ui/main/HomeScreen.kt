package com.dynamicisland.android.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.core.IslandStateCoordinator.PreviewKind
import com.dynamicisland.android.core.PermissionHelper
import com.dynamicisland.android.core.PermissionStatus
import com.dynamicisland.android.data.SettingsRepository
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandThemeId
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.defaultFeatureSizes
import com.dynamicisland.android.ui.overlay.DynamicIslandOverlay
import com.dynamicisland.android.ui.overlay.TabularTextStyle
import com.dynamicisland.android.ui.theme.IslandTheme
import com.dynamicisland.android.ui.theme.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    settingsRepository: SettingsRepository,
    coordinator: IslandStateCoordinator
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = IslandSettings()
    )
    val uiState by coordinator.uiState.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(PermissionHelper.getStatus(context)) }
    var selectedFeature by remember { mutableStateOf(IslandFeatureKind.IDLE) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = PermissionHelper.getStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissions = PermissionHelper.getStatus(context)
    }

    val notificationPostLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissions = PermissionHelper.getStatus(context)
    }

    fun updateSettings(transform: (IslandSettings) -> IslandSettings) {
        scope.launch { settingsRepository.update(transform) }
    }

    val palette = IslandTheme.palette

    Scaffold(
        containerColor = palette.appBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header + Live Interactive Stage
            item {
                HeroHeaderCard(
                    settings = settings,
                    uiState = uiState,
                    permissions = permissions,
                    coordinator = coordinator,
                    onToggleEnabled = { enabled ->
                        updateSettings { it.copy(isEnabled = enabled) }
                    }
                )
            }

            // 2. App & Island Theme Studio
            item {
                GlassSectionCard(
                    overline = "VISUAL IDENTITY",
                    title = "App & Island Theme",
                    icon = Icons.Rounded.Palette
                ) {
                    Text(
                        text = "Transforms both the control dashboard and the status-bar Dynamic Island.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = palette.appTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IslandThemeId.entries.forEach { themeId ->
                            ThemeOptionCard(
                                themeId = themeId,
                                selected = settings.theme == themeId,
                                onClick = { updateSettings { it.copy(theme = themeId) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Permissions & Services
            item {
                GlassSectionCard(overline = "SYSTEM ACCESS", title = "Permissions & Services") {
                    PermissionRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        accent = palette.primaryAccent,
                        title = "Accessibility Overlay",
                        subtitle = "Draws the capsule over the status bar & cutout",
                        granted = permissions.accessibilityEnabled,
                        onClick = { context.startActivity(PermissionHelper.accessibilitySettingsIntent()) }
                    )
                    GlassDivider()
                    PermissionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        accent = palette.secondaryAccent,
                        title = "Notification & Media Access",
                        subtitle = "Live notifications, quick reply, music, calls & timers",
                        granted = permissions.notificationListenerEnabled,
                        onClick = { context.startActivity(PermissionHelper.notificationListenerSettingsIntent()) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GlassDivider()
                        PermissionRow(
                            icon = Icons.Rounded.Bluetooth,
                            accent = palette.violetAccent,
                            title = "Bluetooth Devices",
                            subtitle = "Earbuds & watch connection popups with battery",
                            granted = permissions.bluetoothPermissionGranted,
                            onClick = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        GlassDivider()
                        PermissionRow(
                            icon = Icons.Rounded.Notifications,
                            accent = palette.warningAccent,
                            title = "Post Notifications",
                            subtitle = "Optional Android 13+ notification permission",
                            granted = permissions.postNotificationsGranted,
                            onClick = { notificationPostLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
            }

            // 4. Live Preview Lab
            item {
                GlassSectionCard(overline = "INTERACTIVE LAB", title = "Live Preview") {
                    Text(
                        text = "Trigger any event to preview it on both the stage above and your status bar overlay.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = palette.appTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SimpleToggleRow(
                        title = "Pin Preview",
                        subtitle = "Keep previews on screen while adjusting sizes",
                        checked = uiState.isPreviewPinned,
                        onCheckedChange = { coordinator.setPreviewPinned(it) }
                    )
                    GlassDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewTile(
                                label = "Message",
                                icon = Icons.Rounded.Notifications,
                                accent = palette.primaryAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.NOTIFICATION
                                    coordinator.triggerPreview(PreviewKind.NOTIFICATION)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PreviewTile(
                                label = "Music",
                                icon = Icons.Rounded.MusicNote,
                                accent = palette.secondaryAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.MEDIA
                                    coordinator.triggerPreview(PreviewKind.MEDIA)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewTile(
                                label = "Charging",
                                icon = Icons.Rounded.BatteryChargingFull,
                                accent = palette.positiveAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.CHARGING
                                    coordinator.triggerPreview(PreviewKind.CHARGING)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PreviewTile(
                                label = "Bluetooth",
                                icon = Icons.Rounded.Bluetooth,
                                accent = palette.violetAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.BLUETOOTH
                                    coordinator.triggerPreview(PreviewKind.BLUETOOTH)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewTile(
                                label = "Timer",
                                icon = Icons.Rounded.Timer,
                                accent = palette.warningAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.TIMER
                                    coordinator.triggerPreview(PreviewKind.TIMER)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PreviewTile(
                                label = "Call",
                                icon = Icons.Rounded.Call,
                                accent = palette.positiveAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.CALL
                                    coordinator.triggerPreview(PreviewKind.CALL)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewTile(
                                label = "Silent",
                                icon = Icons.Rounded.NotificationsOff,
                                accent = palette.dangerAccent,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.RINGER
                                    coordinator.triggerPreview(PreviewKind.RINGER)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PreviewTile(
                                label = "Clear All",
                                icon = Icons.Rounded.Clear,
                                accent = palette.appTextMuted,
                                onClick = {
                                    selectedFeature = IslandFeatureKind.IDLE
                                    coordinator.triggerPreview(PreviewKind.CLEAR_PREVIEWS)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Feature Modules & Behavior
            item {
                GlassSectionCard(overline = "EXPERIENCE", title = "Island Features") {
                    ModuleToggleRow(
                        icon = Icons.Rounded.Notifications,
                        accent = palette.primaryAccent,
                        title = "Notifications & Quick Reply",
                        subtitle = "Heads-up banners with inline reply actions",
                        checked = settings.enableNotifications,
                        onCheckedChange = { v -> updateSettings { it.copy(enableNotifications = v) } }
                    )
                    GlassDivider()
                    ModuleToggleRow(
                        icon = Icons.Rounded.MusicNote,
                        accent = palette.secondaryAccent,
                        title = "Music & Media Visualizer",
                        subtitle = "Album art, live waveform & seek controls",
                        checked = settings.enableMediaPlayer,
                        onCheckedChange = { v -> updateSettings { it.copy(enableMediaPlayer = v) } }
                    )
                    GlassDivider()
                    ModuleToggleRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        accent = palette.positiveAccent,
                        title = "Battery, Bluetooth & Ringer",
                        subtitle = "Charging status, earbuds battery & silent mode",
                        checked = settings.enableSystemPopups,
                        onCheckedChange = { v -> updateSettings { it.copy(enableSystemPopups = v) } }
                    )
                    GlassDivider()
                    ModuleToggleRow(
                        icon = Icons.Rounded.Call,
                        accent = palette.warningAccent,
                        title = "Calls & Timers",
                        subtitle = "Live chronometers & dual split-pill mode",
                        checked = settings.enableCallsAndTimers,
                        onCheckedChange = { v -> updateSettings { it.copy(enableCallsAndTimers = v) } }
                    )
                    GlassDivider()
                    SimpleToggleRow(
                        title = "Hide When Idle",
                        subtitle = "Hide capsule completely when nothing is active",
                        checked = settings.hideWhenIdle,
                        onCheckedChange = { v -> updateSettings { it.copy(hideWhenIdle = v) } }
                    )
                    SimpleToggleRow(
                        title = "Hide in Landscape",
                        subtitle = "Auto-hide during fullscreen videos & games",
                        checked = settings.hideInLandscape,
                        onCheckedChange = { v -> updateSettings { it.copy(hideInLandscape = v) } }
                    )
                    SimpleToggleRow(
                        title = "Show on Lockscreen",
                        subtitle = "Keep live activities visible while locked",
                        checked = settings.showOnLockscreen,
                        onCheckedChange = { v -> updateSettings { it.copy(showOnLockscreen = v) } }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CalibrationSliderRow(
                        label = "Popup Duration",
                        valueText = "${(settings.popupDurationMs / 1000f).roundToInt()}s",
                        value = settings.popupDurationMs.toFloat(),
                        valueRange = 2000f..8000f,
                        onValueChange = { v -> updateSettings { it.copy(popupDurationMs = v.toLong()) } }
                    )
                }
            }

            // 6. Size & Cutout Calibration
            item {
                val selectedSpec = settings.sizeFor(selectedFeature)
                GlassSectionCard(
                    overline = "PRECISION FIT",
                    title = "Size & Cutout Calibration",
                    icon = Icons.Rounded.Tune
                ) {
                    Text(
                        text = "Select any feature to preview and customize its exact Island dimensions.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = palette.appTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(IslandFeatureKind.entries) { kind ->
                            FeatureChip(
                                label = kind.label,
                                selected = selectedFeature == kind,
                                onClick = {
                                    selectedFeature = kind
                                    coordinator.previewFeature(kind)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    CalibrationSliderRow(
                        label = "${selectedFeature.label} Width",
                        valueText = "${selectedSpec.widthDp.roundToInt()} dp",
                        value = selectedSpec.widthDp,
                        valueRange = 40f..380f,
                        onValueChange = { v ->
                            updateSettings { it.withFeatureSize(selectedFeature, selectedSpec.copy(widthDp = v)) }
                        }
                    )
                    CalibrationSliderRow(
                        label = "${selectedFeature.label} Height",
                        valueText = "${selectedSpec.heightDp.roundToInt()} dp",
                        value = selectedSpec.heightDp,
                        valueRange = 16f..90f,
                        onValueChange = { v ->
                            updateSettings { it.withFeatureSize(selectedFeature, selectedSpec.copy(heightDp = v)) }
                        }
                    )
                    if (selectedFeature != IslandFeatureKind.IDLE) {
                        CalibrationSliderRow(
                            label = "${selectedFeature.label} Expanded Width",
                            valueText = "${selectedSpec.expandedWidthDp.roundToInt()} dp",
                            value = selectedSpec.expandedWidthDp,
                            valueRange = 220f..400f,
                            onValueChange = { v ->
                                updateSettings {
                                    it.withFeatureSize(selectedFeature, selectedSpec.copy(expandedWidthDp = v))
                                }
                            }
                        )
                    }
                    GlassDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    CalibrationSliderRow(
                        label = "Horizontal Offset (X)",
                        valueText = "${settings.offsetXdp.roundToInt()} dp",
                        value = settings.offsetXdp,
                        valueRange = -160f..160f,
                        onValueChange = { v -> updateSettings { it.copy(offsetXdp = v) } }
                    )
                    CalibrationSliderRow(
                        label = "Vertical Offset (Y)",
                        valueText = "${settings.offsetYdp.roundToInt()} dp",
                        value = settings.offsetYdp,
                        valueRange = -20f..80f,
                        onValueChange = { v -> updateSettings { it.copy(offsetYdp = v) } }
                    )
                    CalibrationSliderRow(
                        label = "Corner Radius",
                        valueText = "${settings.cornerRadiusDp.roundToInt()} dp",
                        value = settings.cornerRadiusDp,
                        valueRange = 8f..48f,
                        onValueChange = { v -> updateSettings { it.copy(cornerRadiusDp = v) } }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val defaults = IslandSettings()
                            updateSettings {
                                it.copy(
                                    offsetXdp = defaults.offsetXdp,
                                    offsetYdp = defaults.offsetYdp,
                                    cornerRadiusDp = defaults.cornerRadiusDp,
                                    featureSizes = defaultFeatureSizes()
                                )
                            }
                        },
                        shape = RoundedCornerShape(palette.tileCornerRadius),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Calibration", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeaderCard(
    settings: IslandSettings,
    uiState: IslandUiState,
    permissions: PermissionStatus,
    coordinator: IslandStateCoordinator,
    onToggleEnabled: (Boolean) -> Unit
) {
    val palette = IslandTheme.palette
    val isLive = permissions.coreReady && settings.isEnabled
    val statusColor by animateColorAsState(
        targetValue = when {
            !settings.isEnabled -> palette.appTextMuted
            isLive -> palette.positiveAccent
            else -> palette.warningAccent
        },
        label = "statusDot"
    )
    val statusText = when {
        !settings.isEnabled -> "Overlay Paused"
        isLive -> "Active Over Status Bar"
        !permissions.accessibilityEnabled -> "Accessibility Required"
        else -> "Notification Access Needed"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(palette.cardCornerRadius + 2.dp))
            .background(palette.heroGradientBrush)
            .border(1.dp, palette.cardBorderBrush, RoundedCornerShape(palette.cardCornerRadius + 2.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dynamic Island",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = palette.appTextPrimary
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }
                }

                Switch(
                    checked = settings.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = palette.primaryAccent,
                        uncheckedThumbColor = palette.switchUncheckedThumb,
                        uncheckedTrackColor = palette.switchUncheckedTrack
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // In-app Interactive Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(palette.cardCornerRadius - 4.dp))
                    .background(palette.stageGradientBrush)
                    .border(0.75.dp, palette.hairlineDivider, RoundedCornerShape(palette.cardCornerRadius - 4.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "9:42",
                            color = palette.appTextPrimary.copy(alpha = 0.72f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TabularTextStyle
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SignalCellularAlt,
                                contentDescription = null,
                                tint = palette.appTextPrimary.copy(alpha = 0.72f),
                                modifier = Modifier.size(13.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = palette.appTextPrimary.copy(alpha = 0.72f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.mode == IslandVisualMode.HIDDEN) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.appTextPrimary.copy(alpha = 0.05f))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Hidden while idle",
                                fontSize = 11.5.sp,
                                color = palette.appTextMuted
                            )
                        }
                    } else {
                        DynamicIslandOverlay(
                            state = uiState,
                            coordinator = coordinator
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap capsule to expand · Swipe to cycle or dismiss",
                        fontSize = 11.sp,
                        color = palette.appTextMuted.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    themeId: IslandThemeId,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePalette = IslandTheme.palette
    val previewPalette = paletteFor(themeId)
    val borderColor = if (selected) activePalette.primaryAccent else activePalette.hairlineDivider

    Surface(
        onClick = onClick,
        color = activePalette.cardElevated,
        shape = RoundedCornerShape(activePalette.tileCornerRadius + 2.dp),
        modifier = modifier
            .border(
                width = if (selected) 1.5.dp else 0.75.dp,
                color = borderColor,
                shape = RoundedCornerShape(activePalette.tileCornerRadius + 2.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Miniature live-style mockup of the app canvas + Island pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(previewPalette.tileCornerRadius))
                    .background(previewPalette.appBackground)
                    .border(0.6.dp, previewPalette.cardBorderTop, RoundedCornerShape(previewPalette.tileCornerRadius))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                // Mini Dynamic Island capsule preview
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(46.dp)
                        .height(13.dp)
                        .clip(RoundedCornerShape((13f * previewPalette.islandCornerScale).dp))
                        .background(previewPalette.islandSurfaceBrush)
                        .border(0.7.dp, previewPalette.islandRimBrush, RoundedCornerShape((13f * previewPalette.islandCornerScale).dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(previewPalette.primaryAccent)
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(previewPalette.secondaryAccent)
                        )
                    }
                }

                // Mini card bar at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(previewPalette.cardSurface)
                        .border(0.5.dp, previewPalette.hairlineDivider, RoundedCornerShape(5.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = themeId.displayName,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = activePalette.appTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = themeId.subtitle,
                fontSize = 9.5.sp,
                color = activePalette.appTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassSectionCard(
    overline: String,
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val palette = IslandTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(palette.cardCornerRadius))
            .background(palette.cardSurface)
            .border(1.dp, palette.cardBorderBrush, RoundedCornerShape(palette.cardCornerRadius))
            .padding(18.dp)
    ) {
        Text(
            text = overline,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = palette.primaryAccent
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.appTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = palette.appTextPrimary
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun GlassDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        thickness = 0.6.dp,
        color = IslandTheme.palette.hairlineDivider
    )
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    val palette = IslandTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeColor = if (granted) palette.positiveAccent else accent
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(badgeColor.copy(alpha = 0.14f))
                .border(0.6.dp, badgeColor.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = palette.appTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.appTextMuted,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.positiveAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Active",
                    color = palette.positiveAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = Color.White
                )
            ) {
                Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PreviewTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = IslandTheme.palette
    Surface(
        onClick = onClick,
        color = palette.cardElevated,
        shape = RoundedCornerShape(palette.tileCornerRadius),
        modifier = modifier
            .height(44.dp)
            .border(0.6.dp, palette.hairlineDivider, RoundedCornerShape(palette.tileCornerRadius))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
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
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                color = palette.appTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModuleToggleRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = IslandTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.appTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.appTextMuted
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.primaryAccent,
                uncheckedThumbColor = palette.switchUncheckedThumb,
                uncheckedTrackColor = palette.switchUncheckedTrack
            )
        )
    }
}

@Composable
private fun SimpleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = IslandTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.appTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = palette.appTextMuted
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.primaryAccent,
                uncheckedThumbColor = palette.switchUncheckedThumb,
                uncheckedTrackColor = palette.switchUncheckedTrack
            )
        )
    }
}

@Composable
private fun CalibrationSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = IslandTheme.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.appTextSecondary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.primaryAccent.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = valueText,
                    fontSize = 12.sp,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    style = TabularTextStyle
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = if (palette.isLightTheme) palette.primaryAccent else Color.White,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = palette.sliderInactiveTrack
            )
        )
    }
}

@Composable
private fun FeatureChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = IslandTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(palette.tileCornerRadius - 2.dp),
        color = if (selected) palette.primaryAccent else palette.cardElevated,
        modifier = Modifier.border(
            width = 0.75.dp,
            color = if (selected) palette.primaryAccent else palette.hairlineDivider,
            shape = RoundedCornerShape(palette.tileCornerRadius - 2.dp)
        )
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else palette.appTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
