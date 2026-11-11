package com.dynamicisland.android.core

import com.dynamicisland.android.model.FeatureSizeSpec
import com.dynamicisland.android.model.IslandActivity
import com.dynamicisland.android.model.IslandFeatureKind
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandThemeId
import com.dynamicisland.android.model.IslandVisualMode
import com.dynamicisland.android.model.RingerMode
import com.dynamicisland.android.ui.theme.paletteFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandStateCoordinatorTest {

    private fun createCoordinator(testScope: TestScope): IslandStateCoordinator {
        return IslandStateCoordinator(testScope.backgroundScope) { 1000L }
    }

    @Test
    fun `defaults to IDLE and respects hideWhenIdle`() = runTest {
        val coordinator = createCoordinator(this)
        assertEquals(IslandVisualMode.IDLE, coordinator.uiState.value.mode)

        coordinator.updateSettings(IslandSettings(hideWhenIdle = true))
        assertEquals(IslandVisualMode.HIDDEN, coordinator.uiState.value.mode)
    }

    @Test
    fun `hides in landscape when hideInLandscape is enabled`() = runTest {
        val coordinator = createCoordinator(this)
        coordinator.updateMedia(sampleMedia())
        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)

        coordinator.setLandscape(true)
        assertEquals(IslandVisualMode.HIDDEN, coordinator.uiState.value.mode)

        coordinator.setLandscape(false)
        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)
    }

    @Test
    fun `transient popup overrides compact activity and auto dismisses`() = runTest(StandardTestDispatcher()) {
        val coordinator = createCoordinator(this)
        coordinator.updateSettings(IslandSettings(popupDurationMs = 3000L))
        coordinator.updateMedia(sampleMedia())

        coordinator.onNotificationPosted(sampleNotification("n1"))
        runCurrent()

        assertEquals(IslandVisualMode.POPUP, coordinator.uiState.value.mode)
        assertTrue(coordinator.uiState.value.primary is IslandActivity.NotificationActivity)

        advanceTimeBy(3100L)
        runCurrent()

        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)
        assertTrue(coordinator.uiState.value.primary is IslandActivity.MediaActivity)
    }

    @Test
    fun `split pill pairs primary and secondary when two live activities exist`() = runTest {
        val coordinator = createCoordinator(this)
        coordinator.updateMedia(sampleMedia())
        coordinator.upsertTimer(sampleTimer("t1"))

        val state = coordinator.uiState.value
        assertEquals(IslandVisualMode.COMPACT, state.mode)
        assertTrue(state.primary is IslandActivity.MediaActivity)
        assertTrue(state.secondary is IslandActivity.TimerActivity)

        coordinator.dismissCurrentPopupOrCycle(1)
        val rotated = coordinator.uiState.value
        assertTrue(rotated.primary is IslandActivity.TimerActivity)
        assertTrue(rotated.secondary is IslandActivity.MediaActivity)
    }

    @Test
    fun `expand and collapse transitions cleanly`() = runTest {
        val coordinator = createCoordinator(this)
        coordinator.updateMedia(sampleMedia())

        coordinator.toggleExpanded()
        assertEquals(IslandVisualMode.EXPANDED, coordinator.uiState.value.mode)

        coordinator.collapse()
        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)
    }

    @Test
    fun `system popup ignored when enableSystemPopups is false`() = runTest {
        val coordinator = createCoordinator(this)
        coordinator.updateSettings(IslandSettings(enableSystemPopups = false))

        coordinator.showSystemPopup(IslandActivity.RingerPopup(mode = RingerMode.SILENT))
        assertEquals(IslandVisualMode.IDLE, coordinator.uiState.value.mode)
        assertNull(coordinator.uiState.value.primary)
    }

    @Test
    fun `paused media auto clears so hideWhenIdle hides consistently`() = runTest(StandardTestDispatcher()) {
        val coordinator = createCoordinator(this)
        coordinator.updateSettings(IslandSettings(hideWhenIdle = true))
        coordinator.updateMedia(sampleMedia().copy(isPlaying = false))
        runCurrent()

        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)

        advanceTimeBy(12_100L)
        runCurrent()

        assertEquals(IslandVisualMode.HIDDEN, coordinator.uiState.value.mode)
        assertNull(coordinator.uiState.value.primary)
    }

    @Test
    fun `pinned preview stays visible until unpinned or cleared`() = runTest(StandardTestDispatcher()) {
        val coordinator = createCoordinator(this)
        coordinator.updateSettings(IslandSettings(popupDurationMs = 2500L))

        assertTrue(coordinator.uiState.value.isPreviewPinned)
        coordinator.triggerPreview(IslandStateCoordinator.PreviewKind.CHARGING)
        runCurrent()

        assertEquals(IslandVisualMode.POPUP, coordinator.uiState.value.mode)
        assertTrue(coordinator.isShowingPinnedPreview())

        advanceTimeBy(10_000L)
        runCurrent()

        // Remains pinned on screen well past popupDurationMs
        assertEquals(IslandVisualMode.POPUP, coordinator.uiState.value.mode)
        assertTrue(coordinator.uiState.value.primary is IslandActivity.ChargingPopup)

        // Expanding and collapsing returns to POPUP instead of vanishing
        coordinator.toggleExpanded()
        assertEquals(IslandVisualMode.EXPANDED, coordinator.uiState.value.mode)
        coordinator.collapse()
        assertEquals(IslandVisualMode.POPUP, coordinator.uiState.value.mode)

        // Unpinning resumes auto-dismiss
        coordinator.setPreviewPinned(false)
        assertFalse(coordinator.uiState.value.isPreviewPinned)
        advanceTimeBy(2600L)
        runCurrent()
        assertEquals(IslandVisualMode.IDLE, coordinator.uiState.value.mode)

        // Clearing removes pinned previews cleanly
        coordinator.setPreviewPinned(true)
        coordinator.previewFeature(IslandFeatureKind.MEDIA)
        runCurrent()
        assertEquals(IslandVisualMode.COMPACT, coordinator.uiState.value.mode)
        coordinator.triggerPreview(IslandStateCoordinator.PreviewKind.CLEAR_PREVIEWS)
        assertEquals(IslandVisualMode.IDLE, coordinator.uiState.value.mode)
    }

    @Test
    fun `per feature size settings update independently`() {
        val customMedia = FeatureSizeSpec(widthDp = 240f, heightDp = 42f, expandedWidthDp = 370f)
        val settings = IslandSettings().withFeatureSize(IslandFeatureKind.MEDIA, customMedia)

        assertEquals(customMedia, settings.sizeFor(IslandFeatureKind.MEDIA))
        assertEquals(
            IslandSettings().sizeFor(IslandFeatureKind.NOTIFICATION),
            settings.sizeFor(IslandFeatureKind.NOTIFICATION)
        )
    }

    @Test
    fun `theme selection propagates through coordinator and resolves distinct palettes`() = runTest {
        val coordinator = createCoordinator(this)
        assertEquals(IslandThemeId.OBSIDIAN_GLASS, coordinator.uiState.value.settings.theme)

        coordinator.updateSettings(IslandSettings(theme = IslandThemeId.ALABASTER_GOLD))
        assertEquals(IslandThemeId.ALABASTER_GOLD, coordinator.uiState.value.settings.theme)

        coordinator.updateSettings(IslandSettings(theme = IslandThemeId.MONOLITH_CARBON))
        assertEquals(IslandThemeId.MONOLITH_CARBON, coordinator.uiState.value.settings.theme)

        val obsidian = paletteFor(IslandThemeId.OBSIDIAN_GLASS)
        val alabaster = paletteFor(IslandThemeId.ALABASTER_GOLD)
        val monolith = paletteFor(IslandThemeId.MONOLITH_CARBON)

        assertFalse(obsidian.isLightTheme)
        assertTrue(alabaster.isLightTheme)
        assertFalse(monolith.isLightTheme)
        assertTrue(monolith.showTelemetryPip)
        assertFalse(obsidian.showTelemetryPip)
        assertTrue(monolith.islandCornerScale < obsidian.islandCornerScale)
    }

    private fun sampleMedia() = IslandActivity.MediaActivity(
        packageName = "com.example.music",
        appName = "Music",
        trackTitle = "Track 1",
        artist = "Artist",
        isPlaying = true,
        positionMs = 10_000L,
        durationMs = 120_000L,
        lastPositionUpdateMs = 1000L
    )

    private fun sampleNotification(id: String) = IslandActivity.NotificationActivity(
        id = id,
        packageName = "com.example.chat",
        appName = "Chat",
        title = "Alice",
        body = "Hello!",
        timestampMs = 1000L
    )

    private fun sampleTimer(id: String) = IslandActivity.TimerActivity(
        id = id,
        packageName = "com.example.clock",
        label = "Timer",
        baseElapsedRealtimeMs = 60_000L,
        isCountDown = true
    )
}

