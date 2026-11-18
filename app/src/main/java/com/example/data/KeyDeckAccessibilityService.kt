package com.example.data

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.model.AppAdaptationMode
import kotlin.math.abs

/**
 * KeyDeck AccessibilityService:
 * 1. Tracks foreground window transitions for reliable Auto-Close of one-time apps.
 * 2. When App Adaptation is enabled for the foreground app, parses live UI nodes (tabs, chat rows, input)
 *    and renders the Symbian/Vista AdaptedAppOverlay (Standard or Messenger mode) docked inside launcher bounds.
 * 3. Intercepts physical hardware keys F1..F6, Arrows, Enter, Escape inside adapted apps.
 * 4. Optionally attaches lightweight bottom/side edge gesture triggers to bypass Xiaomi/OEM gesture locks.
 */
class KeyDeckAccessibilityService : AccessibilityService() {

    companion object {
        const val EXTRA_OPEN_RECENTS = "keydeck_open_recents"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val treeEngine = AppAdaptationTreeEngine()
    private var overlayController: AdaptedAppOverlayController? = null
    private var edgeGestureViewBottom: View? = null
    private var edgeGestureViewSide: View? = null

    private var currentForegroundPkg: String? = null
    private var focusIndex: Int = 0
    private var overlayCollapsed: Boolean = false
    private var pendingRefreshRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = AdaptedAppOverlayController(this)
        AppAdaptationRuntime.onCommandCallback = { cmd ->
            mainHandler.post { handleCommand(cmd) }
        }
        syncOemEdgeGestureStrips()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        syncOemEdgeGestureStrips()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == packageName) {
                RecentForegroundTracker.clearForegroundIfLauncher(packageName)
            } else {
                RecentForegroundTracker.recordForeground(pkg, packageName)
            }
            if (currentForegroundPkg != pkg) {
                currentForegroundPkg = pkg
                focusIndex = 0
                overlayCollapsed = false
            }
        }

        val activePkg = currentForegroundPkg ?: pkg
        val mode = AppAdaptationRuntime.modeFor(activePkg)
        if (!AppAdaptationRuntime.isAdaptationEnabled ||
            activePkg == packageName ||
            mode == AppAdaptationMode.DISABLED
        ) {
            overlayController?.hide()
            AppAdaptationRuntime.updateSnapshot(null)
            return
        }

        // Throttle content re-parsing to keep UI smooth
        pendingRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { refreshOverlayForCurrentWindow(activePkg, mode) }
        pendingRefreshRunnable = r
        mainHandler.postDelayed(r, 90L)
    }

    private fun refreshOverlayForCurrentWindow(pkg: String, mode: AppAdaptationMode) {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val label = AppAdaptationRuntime.labelFor(pkg)
        val snapshot = treeEngine.parseWindow(
            root = root,
            packageName = pkg,
            appLabel = label,
            mode = mode,
            previousFocusIndex = focusIndex,
            collapsed = overlayCollapsed
        ) ?: return

        focusIndex = snapshot.focusedItemIndex
        AppAdaptationRuntime.updateSnapshot(snapshot)
        overlayController?.showOrUpdate(
            snapshot = snapshot,
            notifications = NotificationHubRepository.notifications.value,
            tokens = AppAdaptationRuntime.currentThemeTokens
        )
    }

    private fun handleCommand(cmd: AdaptationCommand) {
        if (cmd is AdaptationCommand.DisablePackage) {
            overlayController?.hide()
            AppAdaptationRuntime.updateSnapshot(null)
            return
        }
        val pkg = currentForegroundPkg ?: AppAdaptationRuntime.embeddedSnapshot.value?.packageName ?: return
        val mode = AppAdaptationRuntime.modeFor(pkg)
        if (mode == AppAdaptationMode.DISABLED) return

        when (cmd) {
            is AdaptationCommand.ClickTab -> {
                treeEngine.clickTab(cmd.index)
                scheduleRefresh(pkg, mode, 180L)
            }
            is AdaptationCommand.MoveFocus -> {
                val count = AppAdaptationRuntime.activeSnapshot.value?.items?.size ?: 0
                if (count > 0) {
                    focusIndex = (focusIndex + cmd.delta).coerceIn(0, count - 1)
                    val cur = AppAdaptationRuntime.activeSnapshot.value ?: return
                    val updated = cur.copy(focusedItemIndex = focusIndex)
                    AppAdaptationRuntime.updateSnapshot(updated)
                    overlayController?.showOrUpdate(
                        snapshot = updated,
                        notifications = NotificationHubRepository.notifications.value,
                        tokens = AppAdaptationRuntime.currentThemeTokens
                    )
                }
            }
            is AdaptationCommand.ActivateFocused -> {
                treeEngine.clickItem(focusIndex)
                scheduleRefresh(pkg, mode, 240L)
            }
            is AdaptationCommand.ScrollContent -> {
                treeEngine.scroll(cmd.forward)
                scheduleRefresh(pkg, mode, 200L)
            }
            is AdaptationCommand.SendText -> {
                treeEngine.sendInputText(cmd.text)
                scheduleRefresh(pkg, mode, 240L)
            }
            is AdaptationCommand.NavigateBack -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                scheduleRefresh(pkg, mode, 220L)
            }
            is AdaptationCommand.ToggleCollapse -> {
                overlayCollapsed = !overlayCollapsed
                refreshOverlayForCurrentWindow(pkg, mode)
            }
            is AdaptationCommand.RefreshTree -> {
                refreshOverlayForCurrentWindow(pkg, mode)
            }
            is AdaptationCommand.DisablePackage -> {
                overlayController?.hide()
                AppAdaptationRuntime.updateSnapshot(null)
            }
        }
    }

    private fun scheduleRefresh(pkg: String, mode: AppAdaptationMode, delayMs: Long) {
        pendingRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { refreshOverlayForCurrentWindow(pkg, mode) }
        pendingRefreshRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun syncOemEdgeGestureStrips() {
        val enabled = ShizukuRuntimeFlags.oemGestureBypassEnabled.get() &&
            ShizukuBridge.hasOverlayPermission(this)
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        if (!enabled) {
            removeEdgeStrips(wm)
            return
        }
        if (edgeGestureViewBottom != null) return

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = resources.displayMetrics.density

        // Bottom gesture handle: swipe UP -> KeyDeck Home; swipe UP + hold -> KeyDeck BB10 Recents
        val bottomView = View(this).apply {
            var downY = 0f
            var downTime = 0L
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        downTime = System.currentTimeMillis()
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = downY - ev.rawY
                        val dt = System.currentTimeMillis() - downTime
                        if (dy > 44f * density) {
                            if (dt > 280L) {
                                openKeyDeckWithRecents()
                            } else {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        val bottomParams = WindowManager.LayoutParams(
            (140f * density).toInt(),
            (14f * density).toInt(),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        // Left-edge trigger: swipe RIGHT -> Global Back
        val sideView = View(this).apply {
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        val dy = abs(ev.rawY - downY)
                        if (dx > 40f * density && dx > dy * 1.3f) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        val sideParams = WindowManager.LayoutParams(
            (12f * density).toInt(),
            (220f * density).toInt(),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        runCatching {
            wm.addView(bottomView, bottomParams)
            edgeGestureViewBottom = bottomView
        }
        runCatching {
            wm.addView(sideView, sideParams)
            edgeGestureViewSide = sideView
        }
    }

    private fun openKeyDeckWithRecents() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_RECENTS, true)
            }
            startActivity(intent)
        }
    }

    private fun removeEdgeStrips(wm: WindowManager) {
        edgeGestureViewBottom?.let { v ->
            runCatching { wm.removeViewImmediate(v) }
            edgeGestureViewBottom = null
        }
        edgeGestureViewSide?.let { v ->
            runCatching { wm.removeViewImmediate(v) }
            edgeGestureViewSide = null
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        val pkg = currentForegroundPkg ?: return false
        val mode = AppAdaptationRuntime.modeFor(pkg)
        if (!AppAdaptationRuntime.isAdaptationEnabled ||
            pkg == packageName ||
            mode == AppAdaptationMode.DISABLED
        ) {
            return false
        }

        // Even when overlay is collapsed, F1..F6 still switch tabs in the adapted app!
        when (event.keyCode) {
            KeyEvent.KEYCODE_F1 -> { handleCommand(AdaptationCommand.ClickTab(0)); return true }
            KeyEvent.KEYCODE_F2 -> { handleCommand(AdaptationCommand.ClickTab(1)); return true }
            KeyEvent.KEYCODE_F3 -> { handleCommand(AdaptationCommand.ClickTab(2)); return true }
            KeyEvent.KEYCODE_F4 -> { handleCommand(AdaptationCommand.ClickTab(3)); return true }
            KeyEvent.KEYCODE_F5 -> { handleCommand(AdaptationCommand.ClickTab(4)); return true }
            KeyEvent.KEYCODE_F6 -> { handleCommand(AdaptationCommand.ClickTab(5)); return true }
        }

        if (overlayCollapsed) {
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { handleCommand(AdaptationCommand.MoveFocus(-1)); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { handleCommand(AdaptationCommand.MoveFocus(+1)); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { handleCommand(AdaptationCommand.ScrollContent(false)); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { handleCommand(AdaptationCommand.ScrollContent(true)); true }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> { handleCommand(AdaptationCommand.ActivateFocused); true }
            KeyEvent.KEYCODE_ESCAPE -> { handleCommand(AdaptationCommand.ToggleCollapse); true }
            else -> false
        }
    }

    override fun onInterrupt() {
        overlayController?.hide()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        AppAdaptationRuntime.onCommandCallback = null
        treeEngine.clearRefs()
        overlayController?.hide()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }
}
