package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandUiState
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.featureKind
import com.dynamicisland.android.ui.theme.IslandTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DynamicIslandOverlay(
    state: IslandUiState,
    coordinator: IslandStateCoordinator,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cfg = state.settings
    val palette = IslandTheme.palette
    val isVisible = state.mode != IslandVisualMode.HIDDEN

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + scaleIn(
            initialScale = 0.80f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 560f)
        ),
        exit = fadeOut(tween(150)) + scaleOut(
            targetScale = 0.80f,
            animationSpec = tween(150)
        )
    ) {
        val activeKind = if (state.mode == IslandVisualMode.IDLE || state.mode == IslandVisualMode.HIDDEN) {
            IslandFeatureKind.IDLE
        } else {
            state.primary?.featureKind() ?: IslandFeatureKind.IDLE
        }
        val spec = cfg.sizeFor(activeKind)

        val compactWidth: Dp = when (state.mode) {
            IslandVisualMode.COMPACT -> if (state.secondary != null) {
                (spec.widthDp + 42f).coerceAtMost(368f).dp
            } else {
                spec.widthDp.dp
            }
            else -> spec.widthDp.dp
        }

        val compactHeight: Dp = when (state.mode) {
            IslandVisualMode.IDLE, IslandVisualMode.HIDDEN -> spec.heightDp.coerceAtLeast(12f).dp
            IslandVisualMode.COMPACT -> spec.heightDp.coerceAtLeast(30f).dp
            IslandVisualMode.POPUP, IslandVisualMode.EXPANDED -> spec.heightDp.coerceAtLeast(32f).dp
        }

        val targetCorner: Dp = (cfg.cornerRadiusDp * palette.islandCornerScale).coerceAtLeast(10f).dp
        val animatedCorner by animateDpAsState(
            targetValue = targetCorner,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f, visibilityThreshold = 0.25.dp),
            label = "islandCorner"
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 680f),
            label = "pressScale"
        )

        val accentColor by animateColorAsState(
            targetValue = if (state.mode == IslandVisualMode.IDLE) {
                if (palette.showTelemetryPip) palette.primaryAccent else Color.Transparent
            } else {
                activityAccentColor(state.primary, palette)
            },
            animationSpec = tween(200),
            label = "accentAura"
        )

        val currentMode by rememberUpdatedState(state.mode)
        val currentPrimary by rememberUpdatedState(state.primary)

        val shape = RoundedCornerShape(animatedCorner)
        var horizontalDragTotal by remember { mutableFloatStateOf(0f) }
        var verticalDragTotal by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = accentColor.copy(alpha = 0.28f),
                    spotColor = palette.islandShadowSpot,
                    clip = false
                )
                .clip(shape)
                .background(palette.islandSurfaceBrush)
                .drawWithCache {
                    val auraBrush = if (accentColor != Color.Transparent) {
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = palette.islandAuraAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.25f, 0f),
                            radius = (size.width * 0.85f).coerceAtLeast(1f)
                        )
                    } else {
                        null
                    }
                    onDrawBehind {
                        if (auraBrush != null) {
                            drawRect(brush = auraBrush)
                        }
                    }
                }
                .border(
                    width = palette.islandBorderWidth,
                    brush = palette.islandRimBrush,
                    shape = shape
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> horizontalDragTotal += dragAmount },
                        onDragEnd = {
                            if (horizontalDragTotal > 38f) {
                                coordinator.dismissCurrentPopupOrCycle(1)
                            } else if (horizontalDragTotal < -38f) {
                                coordinator.dismissCurrentPopupOrCycle(-1)
                            }
                            horizontalDragTotal = 0f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragTotal = 0f },
                        onVerticalDrag = { _, dragAmount -> verticalDragTotal += dragAmount },
                        onDragEnd = {
                            if (currentMode == IslandVisualMode.EXPANDED && verticalDragTotal < -34f) {
                                coordinator.collapse()
                            } else if (currentMode != IslandVisualMode.EXPANDED && verticalDragTotal > 34f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coordinator.toggleExpanded()
                            }
                            verticalDragTotal = 0f
                        }
                    )
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coordinator.toggleExpanded()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = primaryPendingIntent(currentPrimary)
                        if (intent != null) {
                            launchPendingIntent(context, intent)
                            coordinator.collapse()
                        } else {
                            coordinator.toggleExpanded()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = Triple(state.mode, state.primary?.id, state.secondary?.id),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    (fadeIn(tween(115, delayMillis = 15)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(140, easing = FastOutSlowInEasing)
                        ))
                        .togetherWith(fadeOut(tween(55)))
                        .using(
                            SizeTransform(
                                clip = true,
                                sizeAnimationSpec = { _, _ ->
                                    spring<IntSize>(
                                        dampingRatio = 0.82f,
                                        stiffness = 620f
                                    )
                                }
                            )
                        )
                },
                label = "islandModeContent"
            ) { (mode, _, _) ->
                if (mode == IslandVisualMode.EXPANDED) {
                    ExpandedIslandContent(
                        state = state,
                        coordinator = coordinator,
                        modifier = Modifier
                            .width(spec.expandedWidthDp.dp)
                            .heightIn(min = 96.dp, max = 380.dp)
                    )
                } else {
                    CompactIslandContent(
                        state = state,
                        modifier = Modifier
                            .width(compactWidth)
                            .height(compactHeight)
                    )
                }
            }
        }
    }
}
