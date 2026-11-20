package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.model.AlphabetMode
import com.example.model.AppEntry
import com.example.model.HubCategory
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.MainTab
import com.example.model.MediaPlayerState
import com.example.model.PlayerCoverStyle
import com.example.model.RecentAppFrame
import com.example.model.ShizukuState
import com.example.model.UiThemeColor
import com.example.ui.SymbianLockScreen
import com.example.ui.components.AppContextDialog
import com.example.ui.components.AppTileGrid
import com.example.ui.components.AppearanceSettingsDialog
import com.example.ui.components.Bb10RecentsOverlay
import com.example.ui.components.FavoritesAndAlphabetRail
import com.example.ui.theme.KeyDeckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FavoritesAndNiagaraRailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `favorites panel is hidden when empty and shown when favorite apps exist`() {
        composeTestRule.setContent {
            KeyDeckTheme {
                FavoritesAndAlphabetRail(
                    activeTab = MainTab.HOME,
                    alphabetMode = AlphabetMode.LATIN,
                    favoriteApps = emptyList(),
                    starredContacts = emptyList(),
                    presentLetters = setOf('A', 'T'),
                    activeScrubLetter = null,
                    highlightedLetter = null,
                    onToggleAlphabetMode = {},
                    onScrubLetter = {},
                    onTapLetter = {},
                    onLaunchApp = {},
                    onLongPressApp = {},
                    onCallContact = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_panel_column").assertDoesNotExist()
        composeTestRule.onNodeWithTag("niagara_alphabet_rail").assertIsDisplayed()
    }

    @Test
    fun `favorites panel appears when at least one favorite app is present`() {
        val sampleFavorite = AppEntry(
            id = "org.telegram.messenger/Main",
            label = "Telegram",
            packageName = "org.telegram.messenger",
            activityName = "Main",
            icon = null,
            initialChar = 'T',
            isPinned = true,
            hubCategory = HubCategory.CHAT,
            colorBucket = 0
        )

        composeTestRule.setContent {
            KeyDeckTheme {
                FavoritesAndAlphabetRail(
                    activeTab = MainTab.HOME,
                    alphabetMode = AlphabetMode.LATIN,
                    favoriteApps = listOf(sampleFavorite),
                    starredContacts = emptyList(),
                    presentLetters = setOf('T'),
                    activeScrubLetter = null,
                    highlightedLetter = null,
                    onToggleAlphabetMode = {},
                    onScrubLetter = {},
                    onTapLetter = {},
                    onLaunchApp = {},
                    onLongPressApp = {},
                    onCallContact = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_panel_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorites_rail_list").assertIsDisplayed()
    }

    @Test
    fun `appearance settings dialog uses retro vista trackbar and stepper buttons for scale`() {
        var latestScale = 1.0f
        composeTestRule.setContent {
            var uiScale by remember { mutableFloatStateOf(1.0f) }
            KeyDeckTheme {
                AppearanceSettingsDialog(
                    uiThemeColor = UiThemeColor.BLUE,
                    hasWallpaper = false,
                    wallpaperBitmap = null,
                    wallpaperPalette = null,
                    uiScale = uiScale,
                    onSelectThemeColor = {},
                    onPickWallpaper = {},
                    onClearWallpaper = {},
                    onChangeUiScale = {
                        uiScale = it
                        latestScale = it
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ui_scale_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ui_scale_plus_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.05f, latestScale, 0.001f)
        composeTestRule.onNodeWithTag("ui_scale_minus_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.00f, latestScale, 0.001f)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h915dp")
    fun `bb10 recents overlay renders preview image when previewBitmap is available`() {
        val rawPreview = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(AndroidColor.rgb(40, 90, 180))
        }
        val preview = rawPreview.asImageBitmap()
        val frame = RecentAppFrame(
            packageName = "org.telegram.messenger",
            activityName = "Main",
            appLabel = "Telegram",
            previewBitmap = preview,
            lastActiveMs = 1000L,
            previewTitle = "Dev Chat",
            previewSubtitle = "3 новых сообщения"
        )
        composeTestRule.setContent {
            KeyDeckTheme {
                Bb10RecentsOverlay(
                    frames = listOf(frame),
                    focusedIndex = 0,
                    isLandscape = false,
                    onOpenFrame = {},
                    onCloseFrame = {},
                    onTogglePinFrame = {},
                    onOpenFrameInfo = {},
                    onCloseAllExceptPinned = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bb10_recents_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bb10_preview_image_org.telegram.messenger", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h480dp-land")
    fun `lock screen renders side by side columns in landscape and adapts to wallpaper`() {
        val rawWallpaper = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(AndroidColor.rgb(20, 120, 80))
        }
        val wallpaper = rawWallpaper.asImageBitmap()
        val notif = HubNotificationItem(
            key = "n1",
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            title = "Alex",
            text = "Привет!",
            postTime = 1_000_000L,
            timeFormatted = "14:20",
            subTab = HubSubTab.CHATS
        )
        composeTestRule.setContent {
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 800.dp, height = 420.dp)) {
                    SymbianLockScreen(
                        mediaState = MediaPlayerState(),
                        notifications = listOf(notif),
                        shizukuState = ShizukuState(),
                        wallpaperBitmap = wallpaper,
                        coverStyle = PlayerCoverStyle.COVER,
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onUnlock = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_wallpaper_image").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_clock_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_widget_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_music_tile").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_landscape_notifications_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_unlock_button").assertIsDisplayed()
    }

    @Test
    fun `universal search always shows web search card at bottom even with local matches`() {
        val sampleApp = AppEntry("t1/1", "Telegram", "org.telegram.messenger", "1", null, 'T', false, HubCategory.CHAT, 0)
        composeTestRule.setContent {
            KeyDeckTheme {
                val gridState = rememberLazyGridState()
                val focusRequester = remember { FocusRequester() }
                AppTileGrid(
                    apps = listOf(sampleApp),
                    searchImages = emptyList(),
                    searchFiles = emptyList(),
                    musicSearchResults = emptyList(),
                    isSearchingDevice = false,
                    indexedFolderCount = 0,
                    hasAllFilesAccess = true,
                    columns = 3,
                    searchQuery = "te",
                    focusedIndex = 0,
                    highlightedLetter = null,
                    isLoading = false,
                    gridState = gridState,
                    searchFocusRequester = focusRequester,
                    isSearchFocused = false,
                    onSearchFocusChange = {},
                    onRequestSearchFocus = {},
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onLaunchApp = {},
                    onLongPressApp = {},
                    onOpenSearchFile = {},
                    onLongPressSearchFile = {},
                    onLaunchMusicMatch = {},
                    onConnectSearchFolder = {},
                    onRequestAllFilesAccess = {},
                    onSearchWeb = { _, _ -> },
                    onOpenShortcuts = {},
                    onOpenWriter = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("web_search_fallback_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `app context dialog exposes create workspace action`() {
        val sampleApp = AppEntry("t1/1", "Telegram", "org.telegram.messenger", "1", null, 'T', false, HubCategory.CHAT, 0)
        composeTestRule.setContent {
            KeyDeckTheme {
                AppContextDialog(
                    app = sampleApp,
                    isAutoKill = false,
                    isBackgroundSpoofed = false,
                    onDismiss = {},
                    onLaunch = {},
                    onCreateWorkspace = {},
                    onTogglePin = {},
                    onToggleAutoKill = {},
                    onToggleBackgroundSpoof = {},
                    onRename = {},
                    onChooseIcon = {},
                    onShareApk = {},
                    onSaveApk = {},
                    onOpenInfo = {},
                    onUninstall = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("context_create_workspace").performScrollTo().assertIsDisplayed()
    }
}
