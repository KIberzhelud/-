package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.AlphabetMode
import com.example.model.AppEntry
import com.example.model.HubCategory
import com.example.model.MainTab
import com.example.model.UiThemeColor
import com.example.ui.components.AppearanceSettingsDialog
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
}
