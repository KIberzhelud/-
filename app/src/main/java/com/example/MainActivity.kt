package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppAdaptationRuntime
import com.example.data.KeyDeckAccessibilityService
import com.example.ui.HardwareKeyHandler
import com.example.ui.LauncherScreen
import com.example.ui.LocalAppLang
import com.example.ui.theme.KeyDeckTheme
import com.example.ui.theme.buildVistaThemeTokens
import com.example.viewmodel.LauncherViewModel
import com.example.viewmodel.WriterViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val writerViewModel: WriterViewModel by viewModels()
    private val appSearchFocusRequester = FocusRequester()
    private var clearComposeFocusAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleExtraIntent(intent)
        setContent {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            clearComposeFocusAction = { focusManager.clearFocus() }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val vistaTokens = buildVistaThemeTokens(
                themeColor = state.uiThemeColor,
                wallpaperPalette = state.wallpaperPalette,
                hasWallpaper = state.hasWallpaper
            )
            LaunchedEffect(vistaTokens) {
                AppAdaptationRuntime.currentThemeTokens = vistaTokens
            }
            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = (baseDensity.density * state.uiScale).coerceIn(0.6f, 6.0f),
                fontScale = (baseDensity.fontScale * state.uiScale).coerceIn(0.6f, 4.0f)
            )
            CompositionLocalProvider(
                LocalAppLang provides state.appLanguage,
                LocalDensity provides scaledDensity
            ) {
                KeyDeckTheme(vistaTokens = vistaTokens) {
                    LauncherScreen(
                        viewModel = viewModel,
                        appSearchFocusRequester = appSearchFocusRequester,
                        writerViewModel = writerViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExtraIntent(intent)
    }

    private fun handleExtraIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(KeyDeckAccessibilityService.EXTRA_OPEN_RECENTS, false) == true) {
            viewModel.openRecentsOverlay()
            intent.removeExtra(KeyDeckAccessibilityService.EXTRA_OPEN_RECENTS)
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
            onFocusAppSearch = { runCatching { appSearchFocusRequester.requestFocus() } },
            writerViewModel = writerViewModel
        )
        return if (handled) true else super.dispatchKeyEvent(event)
    }
}
