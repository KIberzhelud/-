package com.example.ui

import android.view.KeyEvent as AndroidKeyEvent
import com.example.model.BookSectionTab
import com.example.model.CallsSubTab
import com.example.model.HubSubTab
import com.example.model.MainTab
import com.example.model.WriterSubTab
import com.example.viewmodel.LauncherUiState
import com.example.viewmodel.LauncherViewModel
import com.example.viewmodel.WriterViewModel

object HardwareKeyHandler {

    private const val TAB_LONG_PRESS_THRESHOLD_MS = 450L

    private var tabDownTime: Long = -1L
    private var tabLongPressHandled: Boolean = false

    fun resetKeyTracking() {
        tabDownTime = -1L
        tabLongPressHandled = false
    }

    fun handleAndroidKeyEvent(
        event: AndroidKeyEvent,
        viewModel: LauncherViewModel,
        onClearTextFocus: () -> Unit,
        onFocusAppSearch: () -> Unit,
        writerViewModel: WriterViewModel? = null
    ): Boolean {
        val state: LauncherUiState = viewModel.uiState.value

        // Dedicated handling for Tab (short press = sub-tab switch; hold = toggle Book/Notes Writer Studio)
        if (event.keyCode == AndroidKeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed) {
            return handleTabKeyEvent(event, state, viewModel)
        }

        if (event.action != AndroidKeyEvent.ACTION_DOWN) return false

        val hasAnyModalOpen = state.showMusicSheet ||
            state.showShortcutsDialog ||
            state.showAddLinkDialog ||
            state.showAppearanceDialog ||
            state.showAutoKillDialog ||
            state.showOptimizationDialog ||
            state.showAdaptationDialog ||
            state.showBackgroundWebAudioDialog ||
            state.contextMenuApp != null ||
            state.contextMenuFile != null ||
            state.renameTargetApp != null ||
            state.iconPickerTargetApp != null

        if (hasAnyModalOpen) {
            if (event.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE ||
                (event.keyCode == AndroidKeyEvent.KEYCODE_F5 && state.showMusicSheet)
            ) {
                viewModel.setShowMusicSheet(false)
                viewModel.setShowShortcutsDialog(false)
                viewModel.setShowAddLinkDialog(false)
                viewModel.setShowAppearanceDialog(false)
                viewModel.setShowAutoKillDialog(false)
                viewModel.setShowOptimizationDialog(false)
                viewModel.setShowAdaptationDialog(false)
                viewModel.setShowBackgroundWebAudioDialog(false)
                viewModel.openContextMenu(null)
                viewModel.openFileContextMenu(null)
                viewModel.openRenameAppDialog(null)
                viewModel.openIconPickerDialog(null)
                return true
            }
            return false
        }

        if (state.showRecentsOverlay) {
            val recCols = if (viewModel.isLandscapeLayout) 3 else 2
            when {
                event.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE -> {
                    viewModel.closeRecentsOverlay()
                    return true
                }
                event.isCtrlPressed && event.keyCode == AndroidKeyEvent.KEYCODE_R -> {
                    viewModel.closeRecentsOverlay()
                    return true
                }
                event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.moveRecentsFocus(-1, 0, recCols)
                    return true
                }
                event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.moveRecentsFocus(1, 0, recCols)
                    return true
                }
                event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.moveRecentsFocus(0, -1, recCols)
                    return true
                }
                event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.moveRecentsFocus(0, 1, recCols)
                    return true
                }
                event.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    event.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    event.keyCode == AndroidKeyEvent.KEYCODE_SPACE -> {
                    viewModel.activateFocusedRecentFrame()
                    return true
                }
            }
            return false
        }

        // Global media, F1-F6 tab keys & help keys
        when (event.keyCode) {
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                viewModel.togglePlayPause()
                return true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                viewModel.skipNext()
                return true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                viewModel.skipPrevious()
                return true
            }
            AndroidKeyEvent.KEYCODE_F12 -> {
                viewModel.setShowShortcutsDialog(true)
                return true
            }
            AndroidKeyEvent.KEYCODE_F1 -> {
                if (event.isShiftPressed) {
                    viewModel.setShowShortcutsDialog(true)
                    return true
                }
                if (state.isWriterModeOpen && writerViewModel != null) {
                    writerViewModel.selectSubTab(WriterSubTab.BOOKS)
                } else {
                    viewModel.selectTab(MainTab.HOME)
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_F2 -> {
                if (state.isWriterModeOpen && writerViewModel != null) {
                    writerViewModel.selectSubTab(WriterSubTab.NOTES)
                } else if (state.activeTab == MainTab.CALLS && !state.isWriterModeOpen) {
                    val next = if (state.callsSubTab == CallsSubTab.LOG) {
                        CallsSubTab.CONTACTS
                    } else {
                        CallsSubTab.LOG
                    }
                    viewModel.selectCallsSubTab(next)
                } else {
                    viewModel.selectTab(MainTab.CALLS)
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_F3 -> {
                if (state.isWriterModeOpen && writerViewModel != null) {
                    val curSection = writerViewModel.uiState.value.bookSectionTab
                    val nextSection = when (curSection) {
                        BookSectionTab.PLAN -> BookSectionTab.CHAPTERS
                        BookSectionTab.CHAPTERS -> BookSectionTab.EDITOR
                        BookSectionTab.EDITOR -> BookSectionTab.PLAN
                    }
                    writerViewModel.selectSubTab(WriterSubTab.BOOKS)
                    writerViewModel.selectBookSection(nextSection)
                } else if (state.activeTab == MainTab.HUB && !state.isWriterModeOpen) {
                    val next = when (state.hubSubTab) {
                        HubSubTab.CHATS -> HubSubTab.NEWS
                        HubSubTab.NEWS -> HubSubTab.OTHER
                        HubSubTab.OTHER -> HubSubTab.SETTINGS
                        HubSubTab.SETTINGS -> HubSubTab.CHATS
                    }
                    viewModel.selectHubSubTab(next)
                } else {
                    viewModel.selectTab(MainTab.HUB)
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_F4 -> {
                if (state.isWriterModeOpen) {
                    viewModel.closeWriterMode()
                } else {
                    viewModel.openWriterMode()
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_F5 -> {
                viewModel.toggleMusicDrawer()
                return true
            }
            AndroidKeyEvent.KEYCODE_F6 -> {
                viewModel.launchCamera()
                return true
            }
            AndroidKeyEvent.KEYCODE_MENU -> {
                if (!state.isWriterModeOpen) {
                    val focused = state.filteredApps.getOrNull(state.focusedAppIndex)
                    if (focused != null && state.activeTab == MainTab.HOME) {
                        viewModel.openContextMenu(focused)
                        return true
                    }
                }
            }
        }

        if (event.isCtrlPressed) {
            when (event.keyCode) {
                AndroidKeyEvent.KEYCODE_1 -> { viewModel.selectTab(MainTab.HOME); return true }
                AndroidKeyEvent.KEYCODE_2 -> { viewModel.selectTab(MainTab.CALLS); return true }
                AndroidKeyEvent.KEYCODE_3 -> { viewModel.selectTab(MainTab.HUB); return true }
                AndroidKeyEvent.KEYCODE_4 -> { viewModel.launchCamera(); return true }
                AndroidKeyEvent.KEYCODE_E -> { viewModel.toggleWriterMode(); return true }
                AndroidKeyEvent.KEYCODE_SPACE -> { viewModel.togglePlayPause(); return true }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.skipNext(); return true }
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { viewModel.skipPrevious(); return true }
                AndroidKeyEvent.KEYCODE_DPAD_UP -> { viewModel.adjustVolume(+1); return true }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { viewModel.adjustVolume(-1); return true }
                AndroidKeyEvent.KEYCODE_M -> { viewModel.setShowMusicSheet(true); return true }
                AndroidKeyEvent.KEYCODE_TAB -> { viewModel.cycleMediaSession(); return true }
                AndroidKeyEvent.KEYCODE_D -> {
                    if (!state.isWriterModeOpen) {
                        viewModel.togglePinFocusedApp()
                        return true
                    }
                }
                AndroidKeyEvent.KEYCODE_N -> { viewModel.expandNotificationShade(); return true }
                AndroidKeyEvent.KEYCODE_R -> { viewModel.toggleRecentsOverlay(); return true }
                AndroidKeyEvent.KEYCODE_F -> {
                    if (!state.isWriterModeOpen && state.activeTab == MainTab.HOME) {
                        onFocusAppSearch()
                        return true
                    }
                }
                AndroidKeyEvent.KEYCODE_SLASH -> {
                    viewModel.setShowShortcutsDialog(true)
                    return true
                }
            }
        }

        // Inside Writer Studio Mode, let Esc exit cleanly and pass other keys to text editors
        if (state.isWriterModeOpen) {
            if (event.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                viewModel.closeWriterMode()
                return true
            }
            return false
        }

        if (event.isAltPressed) {
            if (event.keyCode == AndroidKeyEvent.KEYCODE_L) {
                viewModel.toggleAlphabetMode()
                return true
            }
            val codePoint = event.unicodeChar
            if (codePoint > 0 && !Character.isISOControl(codePoint)) {
                val upper = codePoint.toChar().uppercaseChar()
                if (upper in 'A'..'Z' || upper in 'А'..'Я' || upper == '#') {
                    viewModel.jumpToLetter(if (upper == 'Ё') 'Е' else upper)
                    return true
                }
            }
        }

        val columns = if (viewModel.isLandscapeLayout) 3 else 2

        // 2D Arrow / D-Pad Tile Navigation across ALL tabs
        // Note: In HUB CHATS inline reply input, let BasicTextField receive text/Enter if focused,
        // otherwise D-Pad navigates cards.
        when (event.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.moveGridFocus(dx = -1, dy = 0, columns = columns)
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.moveGridFocus(dx = 1, dy = 0, columns = columns)
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.moveGridFocus(dx = 0, dy = -1, columns = columns)
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.moveGridFocus(dx = 0, dy = 1, columns = columns)
                return true
            }
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                if (state.activeTab != MainTab.HUB || state.hubSubTab != HubSubTab.CHATS) {
                    viewModel.activateFocusedItem()
                    return true
                }
            }
            AndroidKeyEvent.KEYCODE_SPACE -> {
                if (!event.isCtrlPressed &&
                    !event.isAltPressed &&
                    !viewModel.isAppSearchInputFocused &&
                    !viewModel.isCallsSearchInputFocused &&
                    state.appSearchQuery.isEmpty() &&
                    state.callsSearchQuery.isEmpty() &&
                    (state.activeTab != MainTab.HUB || state.hubSubTab != HubSubTab.CHATS)
                ) {
                    viewModel.activateFocusedItem()
                    return true
                }
            }
            AndroidKeyEvent.KEYCODE_ESCAPE -> {
                if (state.appSearchQuery.isNotEmpty() || state.callsSearchQuery.isNotEmpty() || state.highlightedLetter != null) {
                    viewModel.clearAllSearchOrHighlights()
                    onClearTextFocus()
                    return true
                } else if (state.activeTab != MainTab.HOME) {
                    viewModel.selectTab(MainTab.HOME)
                    return true
                }
            }
        }

        // Tab-specific auto-search
        when (state.activeTab) {
            MainTab.HOME -> {
                if (event.keyCode == AndroidKeyEvent.KEYCODE_DEL) {
                    if (!viewModel.isAppSearchInputFocused && state.appSearchQuery.isNotEmpty()) {
                        viewModel.backspaceAppSearch()
                        return true
                    }
                    return false
                }
                if (!viewModel.isAppSearchInputFocused && !event.isCtrlPressed && !event.isAltPressed) {
                    val codePoint = event.unicodeChar
                    if (codePoint > 0 && !Character.isISOControl(codePoint)) {
                        val ch = codePoint.toChar()
                        if (ch == '/') {
                            onFocusAppSearch()
                            return true
                        }
                        if (ch.isLetterOrDigit() || ch == ' ' || ch == '*' || ch == '.') {
                            viewModel.appendAppSearchChar(ch)
                            return true
                        }
                    }
                }
            }

            MainTab.CALLS -> {
                if (event.keyCode == AndroidKeyEvent.KEYCODE_DEL) {
                    if (!viewModel.isCallsSearchInputFocused && state.callsSearchQuery.isNotEmpty()) {
                        viewModel.backspaceCallsSearch()
                        return true
                    }
                    return false
                }
                if (!viewModel.isCallsSearchInputFocused && !event.isCtrlPressed && !event.isAltPressed) {
                    val codePoint = event.unicodeChar
                    if (codePoint > 0 && !Character.isISOControl(codePoint)) {
                        val ch = codePoint.toChar()
                        if (ch.isLetterOrDigit() || ch == '+' || ch == ' ') {
                            viewModel.appendCallsSearchChar(ch)
                            return true
                        }
                    }
                }
            }

            MainTab.HUB -> Unit
        }

        return false
    }

    private fun handleTabKeyEvent(
        event: AndroidKeyEvent,
        state: LauncherUiState,
        viewModel: LauncherViewModel
    ): Boolean {
        when (event.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    tabDownTime = if (event.downTime > 0L) event.downTime else event.eventTime
                    tabLongPressHandled = false
                }
                val elapsed = if (tabDownTime >= 0L) event.eventTime - tabDownTime else 0L
                val isHold = event.isLongPress ||
                    event.repeatCount >= 4 ||
                    elapsed >= TAB_LONG_PRESS_THRESHOLD_MS

                if (isHold && !tabLongPressHandled) {
                    tabLongPressHandled = true
                    viewModel.toggleWriterMode()
                }
                return true
            }

            AndroidKeyEvent.ACTION_UP -> {
                val downRef = if (tabDownTime >= 0L) tabDownTime else event.downTime
                val elapsed = if (downRef > 0L) event.eventTime - downRef else 0L
                val alreadyHandled = tabLongPressHandled
                tabDownTime = -1L
                tabLongPressHandled = false

                if (alreadyHandled) return true

                if (elapsed >= TAB_LONG_PRESS_THRESHOLD_MS) {
                    viewModel.toggleWriterMode()
                    return true
                }

                // Short Tab press behavior
                if (!state.isWriterModeOpen) {
                    when (state.activeTab) {
                        MainTab.CALLS -> {
                            val next = if (state.callsSubTab == CallsSubTab.LOG) {
                                CallsSubTab.CONTACTS
                            } else {
                                CallsSubTab.LOG
                            }
                            viewModel.selectCallsSubTab(next)
                        }
                        MainTab.HUB -> {
                            val next = when (state.hubSubTab) {
                                HubSubTab.CHATS -> HubSubTab.NEWS
                                HubSubTab.NEWS -> HubSubTab.OTHER
                                HubSubTab.OTHER -> HubSubTab.SETTINGS
                                HubSubTab.SETTINGS -> HubSubTab.CHATS
                            }
                            viewModel.selectHubSubTab(next)
                        }
                        MainTab.HOME -> Unit
                    }
                }
                return true
            }

            else -> {
                tabDownTime = -1L
                tabLongPressHandled = false
                return false
            }
        }
    }
}
