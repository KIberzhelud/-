package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.HardwareKeyHandler
import com.example.ui.LauncherScreen
import com.example.ui.theme.KeyDeckTheme
import com.example.ui.theme.buildVistaThemeTokens
import com.example.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val appSearchFocusRequester = FocusRequester()
    private var clearComposeFocusAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            clearComposeFocusAction = { focusManager.clearFocus() }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val vistaTokens = buildVistaThemeTokens(
                themeColor = state.uiThemeColor,
                wallpaperPalette = state.wallpaperPalette,
                hasWallpaper = state.hasWallpaper
            )
            KeyDeckTheme(vistaTokens = vistaTokens) {
                LauncherScreen(
                    viewModel = viewModel,
                    appSearchFocusRequester = appSearchFocusRequester
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onLauncherPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeRefresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = HardwareKeyHandler.handleAndroidKeyEvent(
            event = event,
            viewModel = viewModel,
            onClearTextFocus = { clearComposeFocusAction?.invoke() },
            onFocusAppSearch = { runCatching { appSearchFocusRequester.requestFocus() } }
        )
        return if (handled) true else super.dispatchKeyEvent(event)
    }
}
