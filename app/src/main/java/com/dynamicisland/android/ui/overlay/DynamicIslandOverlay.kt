package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.dp
import com.dynamicisland.android.core.IslandStateCoordinator
import com.dynamicisland.android.model.IslandActivity
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
        enter = fadeIn(tween(220)) + scaleIn(
            initialScale = 0.78f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
        ),
        exit = fadeOut(tween(180)) + scaleOut(
            targetScale = 0.78f,
            animationSpec = tween(180)
        )
    ) {
        val activeKind = if (state.mode == IslandVisualMode.IDLE || state.mode == IslandVisualMode.HIDDEN) {
            IslandFeatureKind.IDLE
        } else {
            state.primary?.featureKind() ?: IslandFeatureKind.IDLE
        }
        val spec = cfg.sizeFor(activeKind)

        val targetWidth: Dp = when (state.mode) {
            IslandVisualMode.IDLE, IslandVisualMode.HIDDEN -> spec.widthDp.dp
            IslandVisualMode.COMPACT -> if (state.secondary != null) {
                (spec.widthDp + 42f).coerceAtMost(368f).dp
            } else {
                spec.widthDp.dp
            }
            IslandVisualMode.POPUP -> spec.widthDp.dp
            IslandVisualMode.EXPANDED -> spec.expandedWidthDp.dp
        }

        val targetHeight: Dp = when (state.mode) {
            IslandVisualMode.IDLE, IslandVisualMode.HIDDEN -> spec.heightDp.coerceAtLeast(12f).dp
            IslandVisualMode.COMPACT -> spec.heightDp.coerceAtLeast(30f).dp
            IslandVisualMode.POPUP -> spec.heightDp.coerceAtLeast(32f).dp
            IslandVisualMode.EXPANDED -> 108.dp
        }

        val targetCorner: Dp = (cfg.cornerRadiusDp * palette.islandCornerScale).coerceAtLeast(10f).dp

        val smoothSpring = spring<Dp>(
            dampingRatio = 0.80f,
            stiffness = 360f,
            visibilityThreshold = 0.25.dp
        )

        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = smoothSpring,
            label = "islandWidth"
        )
        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = smoothSpring,
            label = "islandHeight"
        )
        val animatedCorner by animateDpAsState(
            targetValue = targetCorner,
            animationSpec = smoothSpring,
            label = "islandCorner"
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "pressScale"
        )

        val accentColor by animateColorAsState(
            targetValue = if (state.mode == IslandVisualMode.IDLE) {
                if (palette.showTelemetryPip) palette.primaryAccent else Color.Transparent
            } else {
                activityAccentColor(state.primary, palette)
            },
            animationSpec = tween(260),
            label = "accentAura"
        )

        val shape = RoundedCornerShape(animatedCorner)
        var horizontalDragTotal by remember { mutableFloatStateOf(0f) }
        var verticalDragTotal by remember { mutableFloatStateOf(0f) }

        val heightModifier = if (state.mode == IslandVisualMode.EXPANDED) {
            Modifier
                .wrapContentHeight()
                .heightIn(min = 98.dp, max = 380.dp)
        } else {
            Modifier.height(animatedHeight)
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .width(animatedWidth)
                .then(heightModifier)
                .shadow(
                    elevation = if (state.mode == IslandVisualMode.EXPANDED) 24.dp else 10.dp,
                    shape = shape,
                    ambientColor = accentColor.copy(alpha = 0.32f),
                    spotColor = palette.islandShadowSpot,
                    clip = false
                )
                .clip(shape)
                .background(palette.islandSurfaceBrush)
                .drawBehind {
                    if (accentColor != Color.Transparent) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = palette.islandAuraAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.25f, 0f),
                                radius = size.width * 0.85f
                            )
                        )
                    }
                }
                .border(
                    width = palette.islandBorderWidth,
                    brush = palette.islandRimBrush,
                    shape = shape
                )
                .pointerInput(state.mode) {
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
                .pointerInput(state.mode) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragTotal = 0f },
                        onVerticalDrag = { _, dragAmount -> verticalDragTotal += dragAmount },
                        onDragEnd = {
                            if (state.mode == IslandVisualMode.EXPANDED && verticalDragTotal < -34f) {
                                coordinator.collapse()
                            } else if (state.mode != IslandVisualMode.EXPANDED && verticalDragTotal > 34f) {
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
                        val intent = primaryPendingIntent(state.primary)
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
                transitionSpec = {
                    (fadeIn(tween(180)) +
                        scaleIn(initialScale = 0.94f, animationSpec = tween(180)))
                        .togetherWith(fadeOut(tween(90)))
                        .using(
                            SizeTransform(
                                clip = true,
                                sizeAnimationSpec = { _, _ ->
                                    spring(dampingRatio = 0.80f, stiffness = 360f)
                                }
                            )
                        )
                },
                label = "islandModeContent"
            ) { (mode, _, _) ->
                if (mode == IslandVisualMode.EXPANDED) {
                    ExpandedIslandContent(state = state, coordinator = coordinator)
                } else {
                    CompactIslandContent(state = state)
                }
            }
        }
    }
}
