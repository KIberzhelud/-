package com.example.data

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.graphics.asImageBitmap
import com.example.MainActivity
import com.example.ui.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * KeyDeck AccessibilityService:
 * 1. Tracks foreground window transitions for Auto-Close of one-time apps & BB10 Recents Previews.
 * 2. Captures scaled window thumbnails via API 30+ takeScreenshot() into AppPreviewCache.
 * 3. Intercepts Screen-Off & Screen-On -> KeyDeck LockScreen immediately before unlock.
 * 4. Provides IME-aware invisible bottom gesture handle & full-height side-edge Back strips.
 * 5. Hosts a collapsible Docked Workspace Top Bar with Music/Search/Ctrl+V Paste/Workspace switcher.
 */
class KeyDeckAccessibilityService : AccessibilityService() {

    companion object {
        const val EXTRA_OPEN_RECENTS = "keydeck_open_recents"
        const val EXTRA_OPEN_HUB = "keydeck_open_hub"
        const val EXTRA_OPEN_SEARCH = "keydeck_open_search"
        const val EXTRA_WORKSPACE_ACTION = "keydeck_workspace_action"
        const val WS_ACTION_PREV = "prev"
        const val WS_ACTION_NEXT = "next"
        const val WS_ACTION_HOME = "home"

        @Volatile
        private var serviceRef: WeakReference<KeyDeckAccessibilityService>? = null

        fun getInstance(): KeyDeckAccessibilityService? = serviceRef?.get()

        fun showDockedWorkspaceBar(workspaceLabel: String) {
            getInstance()?.showOrUpdateWorkspaceBar(workspaceLabel)
        }

        fun hideDockedWorkspaceBar() {
            getInstance()?.dismissWorkspaceBar()
        }

        fun performGlobalPaste(): Boolean {
            return getInstance()?.performAccessibilityPaste() ?: false
        }

        fun getRecommendedWorkspaceTopPx(context: Context? = null): Int {
            val inst = getInstance()
            val res = inst?.resources ?: context?.resources
            val density = res?.displayMetrics?.density ?: 3f
            val resId = res?.getIdentifier("status_bar_height", "dimen", "android") ?: 0
            val statusBarPx = if (resId > 0 && res != null) {
                res.getDimensionPixelSize(resId)
            } else {
                (28f * density).toInt()
            }
            return statusBarPx + (116f * density).toInt()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var shizukuBridge: ShizukuBridge? = null
    private var mediaRepo: MediaControlRepository? = null

    private var edgeGestureViewBottom: View? = null
    private var edgeGestureViewLeft: View? = null
    private var edgeGestureViewRight: View? = null

    private var workspaceBarView: View? = null
    private var workspaceBarCollapsed = false
    private var currentWorkspaceLabel: String = "Рабочий стол"
    private var workspaceBarTickRunnable: Runnable? = null

    private var currentForegroundPkg: String? = null
    private var pendingScreenshotRunnable: Runnable? = null
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    MediaGuardRuntime.isScreenOff = true
                    if (ShizukuRuntimeFlags.interceptLockScreenEnabled.get()) {
                        launchLockScreenImmediately(dismissNonSecureKeyguard = false)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    MediaGuardRuntime.isScreenOff = false
                    if (ShizukuRuntimeFlags.interceptLockScreenEnabled.get()) {
                        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                        val locked = km?.isKeyguardLocked == true
                        val secure = km?.isDeviceSecure == true
                        if (locked || !secure) {
                            launchLockScreenImmediately(dismissNonSecureKeyguard = !secure)
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
        ShizukuRuntimeFlags.loadPersistedFlags(this)
        shizukuBridge = ShizukuBridge(applicationContext).also { it.startObserving() }
        mediaRepo = MediaControlRepository(applicationContext).also { it.startObserving() }
        registerScreenReceiver()
        syncOemEdgeGestureStrips()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        syncOemEdgeGestureStrips()

        val currentWindows = runCatching { windows }.getOrNull().orEmpty()
        val visiblePkgs = currentWindows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { win -> win.root?.packageName?.toString() }
            .filter { it.isNotBlank() && it != packageName }
            .toSet()
        MediaGuardRuntime.updateVisibleWindows(visiblePkgs)

        val imeVisible = isImeWindowVisible(currentWindows)
        edgeGestureViewBottom?.let { bottom ->
            val targetVis = if (imeVisible) View.GONE else View.VISIBLE
            if (bottom.visibility != targetVis) {
                bottom.visibility = targetVis
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == packageName) {
                RecentForegroundTracker.clearForegroundIfLauncher(packageName)
                pendingScreenshotRunnable?.let { mainHandler.removeCallbacks(it) }
            } else {
                RecentForegroundTracker.recordForeground(pkg, packageName)
                scheduleScreenshotCapture(pkg)
            }
            if (currentForegroundPkg != pkg) {
                currentForegroundPkg = pkg
            }
        }
    }

    private fun isImeWindowVisible(currentWindows: List<AccessibilityWindowInfo>): Boolean {
        val rect = android.graphics.Rect()
        return currentWindows.any { win ->
            if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@any false
            win.getBoundsInScreen(rect)
            rect.height() > 40
        }
    }

    private fun launchLockScreenImmediately(dismissNonSecureKeyguard: Boolean) {
        if (dismissNonSecureKeyguard) {
            val status = shizukuBridge?.statusState?.value
            if (status != null && ((status.isBinderAlive && status.isPermissionGranted) || status.isRootAvailable)) {
                serviceScope.launch { shizukuBridge?.dismissSystemKeyguardViaShizuku() }
            }
        }
        runCatching {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            startActivity(lockIntent)
        }
    }

    fun performAccessibilityPaste(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (focused != null) {
            val pasted = runCatching {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }.getOrDefault(false)
            if (pasted) return true
        }
        val status = shizukuBridge?.statusState?.value
        if (status != null && ((status.isBinderAlive && status.isPermissionGranted) || status.isRootAvailable)) {
            serviceScope.launch {
                shizukuBridge?.pasteClipboardViaInputKeyEvent()
            }
            return true
        }
        return false
    }

    @SuppressLint("SetTextI18n")
    fun showOrUpdateWorkspaceBar(workspaceLabel: String) {
        currentWorkspaceLabel = workspaceLabel
        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@post
            dismissWorkspaceBar()
            if (!ShizukuBridge.hasOverlayPermission(this) && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return@post
            }

            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()
            val topMarginPx = getStatusBarHeightPx() + dp(2)

            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(242, 22, 34, 52), Color.argb(242, 10, 16, 26))
                ).apply {
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.argb(210, 102, 182, 246))
                }
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }

            if (workspaceBarCollapsed) {
                val collapsedRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                collapsedRow.addView(createShadePillButton("▼ $currentWorkspaceLabel", Color.rgb(36, 98, 168)) {
                    workspaceBarCollapsed = false
                    showOrUpdateWorkspaceBar(currentWorkspaceLabel)
                })
                collapsedRow.addView(spaceView(dp(4)))
                collapsedRow.addView(createShadePillButton("✕", Color.rgb(88, 38, 44)) {
                    dismissWorkspaceBar()
                })
                barContainer.addView(collapsedRow)
            } else {
                val activeSession = mediaRepo?.mediaState?.value?.activeSession

                // Row 1: Track Title + Progress Bar + Prev / Play-Pause / Next
                val musicRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val trackInfoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        mediaRepo?.togglePlayPause() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    }
                }
                val trackTitleTv = TextView(this).apply {
                    text = activeSession?.let {
                        val artistPart = if (it.artist.isNotBlank()) " — ${it.artist}" else ""
                        "♫ ${it.title}$artistPart"
                    } ?: "♫ $currentWorkspaceLabel"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    paint.isFakeBoldText = true
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val trackProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 1000
                    isIndeterminate = false
                    val dur = activeSession?.durationMs ?: 0L
                    val pos = if (activeSession != null) MediaControlRepository.currentEstimatedPositionMs(activeSession) else 0L
                    progress = if (dur > 0L) ((pos * 1000L) / dur).toInt().coerceIn(0, 1000) else 0
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                        topMargin = dp(3)
                    }
                }
                trackInfoCol.addView(trackTitleTv)
                trackInfoCol.addView(trackProgress)

                val prevTrackBtn = createShadePillButton("⏮", Color.rgb(38, 52, 72)) {
                    mediaRepo?.skipPrevious() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                var playPauseBtn: TextView? = null
                playPauseBtn = createShadePillButton(
                    if (activeSession?.isPlaying == true) "⏸" else "▶",
                    Color.rgb(36, 102, 174)
                ) {
                    mediaRepo?.togglePlayPause() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    val nowPlaying = mediaRepo?.mediaState?.value?.activeSession?.isPlaying == true
                    playPauseBtn?.text = if (nowPlaying) "⏸" else "▶"
                }
                val nextTrackBtn = createShadePillButton("⏭", Color.rgb(38, 52, 72)) {
                    mediaRepo?.skipNext() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                musicRow.addView(trackInfoCol)
                musicRow.addView(spaceView(dp(6)))
                musicRow.addView(prevTrackBtn)
                musicRow.addView(spaceView(dp(3)))
                musicRow.addView(playPauseBtn)
                musicRow.addView(spaceView(dp(3)))
                musicRow.addView(nextTrackBtn)
                barContainer.addView(musicRow)
                barContainer.addView(spaceView(dp(5)))

                // Row 2: Clickable Search Pill + Prev WS + Home + Next WS + Collapse + Close
                val controlRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val searchPill = TextView(this).apply {
                    text = "🔍 Поиск: приложения, чаты, *apk, музыка…"
                    setTextColor(Color.rgb(175, 205, 236))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    background = shadeInsetDrawable()
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { openMainWithAction(EXTRA_OPEN_SEARCH, true) }
                }
                val prevWsBtn = createShadePillButton("◀", Color.rgb(38, 52, 72)) {
                    sendWorkspaceAction(WS_ACTION_PREV)
                }
                val homeWsBtn = createShadePillButton("🏠", Color.rgb(36, 102, 174)) {
                    dismissWorkspaceBar()
                    sendWorkspaceAction(WS_ACTION_HOME)
                }
                val nextWsBtn = createShadePillButton("▶", Color.rgb(38, 52, 72)) {
                    sendWorkspaceAction(WS_ACTION_NEXT)
                }
                val collapseBtn = createShadePillButton("▲", Color.rgb(48, 62, 82)) {
                    workspaceBarCollapsed = true
                    showOrUpdateWorkspaceBar(currentWorkspaceLabel)
                }
                val closeBtn = createShadePillButton("✕", Color.rgb(88, 38, 44)) {
                    dismissWorkspaceBar()
                }

                controlRow.addView(searchPill)
                listOf(prevWsBtn, homeWsBtn, nextWsBtn, collapseBtn, closeBtn).forEach { btn ->
                    controlRow.addView(spaceView(dp(4)))
                    controlRow.addView(btn)
                }
                barContainer.addView(controlRow)

                val ticker = object : Runnable {
                    override fun run() {
                        if (workspaceBarView == null || workspaceBarCollapsed) return
                        val sess = mediaRepo?.mediaState?.value?.activeSession
                        trackTitleTv.text = sess?.let {
                            val artistPart = if (it.artist.isNotBlank()) " — ${it.artist}" else ""
                            "♫ ${it.title}$artistPart"
                        } ?: "♫ $currentWorkspaceLabel"
                        val dur = sess?.durationMs ?: 0L
                        val pos = if (sess != null) MediaControlRepository.currentEstimatedPositionMs(sess) else 0L
                        trackProgress.progress = if (dur > 0L) ((pos * 1000L) / dur).toInt().coerceIn(0, 1000) else 0
                        playPauseBtn?.text = if (sess?.isPlaying == true) "⏸" else "▶"
                        mainHandler.postDelayed(this, 1000L)
                    }
                }
                workspaceBarTickRunnable = ticker
                mainHandler.postDelayed(ticker, 1000L)
            }

            val widthPx = if (workspaceBarCollapsed) {
                WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                WindowManager.LayoutParams.MATCH_PARENT
            }
            val params = WindowManager.LayoutParams(
                widthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = if (workspaceBarCollapsed) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = topMarginPx
            }
            runCatching {
                wm.addView(barContainer, params)
                workspaceBarView = barContainer
            }
        }
    }

    fun dismissWorkspaceBar() {
        workspaceBarTickRunnable?.let { mainHandler.removeCallbacks(it) }
        workspaceBarTickRunnable = null
        val v = workspaceBarView ?: return
        workspaceBarView = null
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeViewImmediate(v) }
    }

    private fun openMainWithAction(extraKey: String, value: Boolean) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(extraKey, value)
            }
            startActivity(intent)
        }
    }

    private fun sendWorkspaceAction(action: String) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(EXTRA_WORKSPACE_ACTION, action)
            }
            startActivity(intent)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val ts = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(ts, ts, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(ts, ts, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun getStatusBarHeightPx(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else (28 * resources.displayMetrics.density).toInt()
    }

    private fun spaceView(widthPx: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, widthPx)
        }
    }

    private fun shadeInsetDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.argb(215, 10, 16, 25))
            cornerRadius = 5f * resources.displayMetrics.density
            setStroke((1f * resources.displayMetrics.density).toInt(), Color.argb(120, 74, 118, 164))
        }
    }

    private fun createShadePillButton(text: String, bgColor: Int, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 4f * d
                setStroke((1f * d).toInt(), Color.argb(140, 140, 200, 255))
            }
            setPadding((8 * d).toInt(), (5 * d).toInt(), (8 * d).toInt(), (5 * d).toInt())
            setOnClickListener { onClick() }
        }
    }

    private fun overlayWindowType(): Int {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && !ShizukuBridge.hasOverlayPermission(this) -> {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun scheduleScreenshotCapture(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (pkg == "com.android.systemui" ||
            pkg.startsWith("com.google.android.inputmethod") ||
            pkg.contains("keyboard")
        ) {
            return
        }
        if (!AppPreviewCache.shouldCapture(pkg)) return
        pendingScreenshotRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable { captureWindowPreview(pkg) }
        pendingScreenshotRunnable = task
        mainHandler.postDelayed(task, 360L)
    }

    private fun captureWindowPreview(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (currentForegroundPkg != pkg) return
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        try {
                            val hwBmp = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            if (hwBmp != null) {
                                val softCopy = hwBmp.copy(Bitmap.Config.ARGB_8888, false)
                                hwBmp.recycle()
                                if (softCopy != null) {
                                    val maxDim = 360f
                                    val ratio = minOf(maxDim / softCopy.width, maxDim / softCopy.height).coerceAtMost(1f)
                                    val w = (softCopy.width * ratio).toInt().coerceAtLeast(1)
                                    val h = (softCopy.height * ratio).toInt().coerceAtLeast(1)
                                    val thumb = if (w < softCopy.width || h < softCopy.height) {
                                        Bitmap.createScaledBitmap(softCopy, w, h, true).also {
                                            if (it !== softCopy) softCopy.recycle()
                                        }
                                    } else {
                                        softCopy
                                    }
                                    AppPreviewCache.put(pkg, thumb.asImageBitmap())
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            runCatching { buffer.close() }
                        }
                    }

                    override fun onFailure(errorCode: Int) = Unit
                }
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun syncOemEdgeGestureStrips() {
        ShizukuRuntimeFlags.loadPersistedFlags(this)
        val enabled = ShizukuRuntimeFlags.oemGestureBypassEnabled.get() &&
            (ShizukuBridge.hasOverlayPermission(this) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        if (!enabled) {
            removeEdgeStrips(wm)
            return
        }
        if (edgeGestureViewBottom != null && edgeGestureViewLeft != null && edgeGestureViewRight != null) return

        val windowType = overlayWindowType()
        val density = resources.displayMetrics.density
        val statusBarH = getStatusBarHeightPx()
        val screenH = resources.displayMetrics.heightPixels

        // Completely invisible bottom gesture handle (no contrast box/white rectangle), IME-hidden
        if (edgeGestureViewBottom == null) {
            val bottomView = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                alpha = 0f
                var downY = 0f
                var latestDy = 0f
                var handled = false
                var holdRunnable: Runnable? = null

                setOnTouchListener { _, ev ->
                    val nowWindows = runCatching { windows }.getOrNull().orEmpty()
                    if (isImeWindowVisible(nowWindows)) {
                        visibility = View.GONE
                        return@setOnTouchListener false
                    }
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downY = ev.rawY
                            latestDy = 0f
                            handled = false
                            holdRunnable?.let { mainHandler.removeCallbacks(it) }
                            holdRunnable = null
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            latestDy = downY - ev.rawY
                            if (!handled && latestDy > 34f * density && holdRunnable == null) {
                                val r = Runnable {
                                    if (!handled && latestDy > 28f * density) {
                                        handled = true
                                        openKeyDeckWithRecents()
                                    }
                                }
                                holdRunnable = r
                                mainHandler.postDelayed(r, 230L)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            holdRunnable?.let { mainHandler.removeCallbacks(it) }
                            holdRunnable = null
                            val dy = downY - ev.rawY
                            if (!handled && dy > 24f * density) {
                                handled = true
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            val bottomParams = WindowManager.LayoutParams(
                (200f * density).toInt(),
                (14f * density).toInt(),
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            runCatching {
                wm.addView(bottomView, bottomParams)
                edgeGestureViewBottom = bottomView
            }
        }

        // Full vertical edge strips (statusBar + 24dp .. screenH - 110dp) so Back works everywhere, not just center!
        val topOffset = statusBarH + (24f * density).toInt()
        val bottomExclusion = (110f * density).toInt()
        val sideStripHeight = (screenH - topOffset - bottomExclusion).coerceAtLeast((260f * density).toInt())

        if (edgeGestureViewLeft == null) {
            val leftView = createSideBackStrip(density, fromLeft = true)
            val leftParams = WindowManager.LayoutParams(
                (20f * density).toInt(),
                sideStripHeight,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                y = topOffset
            }
            runCatching {
                wm.addView(leftView, leftParams)
                edgeGestureViewLeft = leftView
            }
        }

        if (edgeGestureViewRight == null) {
            val rightView = createSideBackStrip(density, fromLeft = false)
            val rightParams = WindowManager.LayoutParams(
                (20f * density).toInt(),
                sideStripHeight,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                y = topOffset
            }
            runCatching {
                wm.addView(rightView, rightParams)
                edgeGestureViewRight = rightView
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSideBackStrip(density: Float, fromLeft: Boolean): View {
        return View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
            var downX = 0f
            var downY = 0f
            var triggered = false
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        triggered = false
                        true
                    }
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                        if (!triggered) {
                            val dx = if (fromLeft) (ev.rawX - downX) else (downX - ev.rawX)
                            val dy = abs(ev.rawY - downY)
                            val threshold = if (ev.actionMasked == MotionEvent.ACTION_UP) 14f * density else 16f * density
                            if (dx > threshold && dx > dy * 0.85f) {
                                triggered = true
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
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
        edgeGestureViewLeft?.let { v ->
            runCatching { wm.removeViewImmediate(v) }
            edgeGestureViewLeft = null
        }
        edgeGestureViewRight?.let { v ->
            runCatching { wm.removeViewImmediate(v) }
            edgeGestureViewRight = null
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    override fun onInterrupt() {
        dismissWorkspaceBar()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceRef?.get() === this) {
            serviceRef = null
        }
        pendingScreenshotRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissWorkspaceBar()
        unregisterScreenReceiver()
        shizukuBridge?.stopObserving()
        mediaRepo?.stopObserving()
        serviceScope.cancel()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }
}
