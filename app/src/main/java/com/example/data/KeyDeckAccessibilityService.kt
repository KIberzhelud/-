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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
 * 3. Intercepts System Notification Shade -> floating Vista Aero Shade Overlay (toggles, brightness, music, live notifs).
 * 4. Intercepts Screen-Off & Screen-On -> KeyDeck LockScreen immediately before unlock.
 * 5. Provides IME-aware invisible bottom gesture handle & full-height side-edge Back strips.
 * 6. Hosts a collapsible Docked Workspace Top Bar with Music/Search/Ctrl+V Paste/Workspace switcher.
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

        fun showVistaShade() {
            getInstance()?.showVistaShadeOverlay()
        }

        fun performGlobalPaste(): Boolean {
            return getInstance()?.performAccessibilityPaste() ?: false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var shizukuBridge: ShizukuBridge? = null
    private var mediaRepo: MediaControlRepository? = null

    private var edgeGestureViewBottom: View? = null
    private var edgeGestureViewLeft: View? = null
    private var edgeGestureViewRight: View? = null

    private var vistaShadeOverlayView: View? = null
    private var workspaceBarView: View? = null
    private var workspaceBarCollapsed = false
    private var currentWorkspaceLabel: String = "Рабочий стол"
    private var isTorchOn = false

    private var currentForegroundPkg: String? = null
    private var pendingScreenshotRunnable: Runnable? = null
    private var screenReceiverRegistered = false
    private var lastShadeInterceptAtMs = 0L

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

        if (pkg == "com.android.systemui" && ShizukuRuntimeFlags.interceptShadeEnabled.get()) {
            maybeInterceptSystemShade(currentWindows)
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

    private fun maybeInterceptSystemShade(currentWindows: List<AccessibilityWindowInfo>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShadeInterceptAtMs < 650L) return

        val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val hasExpandedSysWindow = currentWindows.any { win ->
            if (win.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            val rootPkg = runCatching { win.root?.packageName?.toString() }.getOrNull()
            if (rootPkg != "com.android.systemui") return@any false
            val bounds = android.graphics.Rect()
            win.getBoundsInScreen(bounds)
            bounds.height() > (screenH * 0.25f)
        }

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val hasShadeNode = if (root != null && root.packageName?.toString() == "com.android.systemui") {
            containsShadeMarker(root, depth = 0)
        } else {
            false
        }

        if (!hasExpandedSysWindow && !hasShadeNode) return
        lastShadeInterceptAtMs = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        serviceScope.launch {
            shizukuBridge?.collapseSystemShadeViaShizuku()
        }

        mainHandler.postDelayed({
            if (ShizukuBridge.hasOverlayPermission(this) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                showVistaShadeOverlay()
            } else {
                openLauncherHub()
            }
        }, 80L)
    }

    private fun openLauncherHub() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(EXTRA_OPEN_HUB, true)
            }
            startActivity(intent)
        }
    }

    private fun containsShadeMarker(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 4) return false
        val id = node.viewIdResourceName.orEmpty().lowercase()
        val desc = node.contentDescription?.toString().orEmpty().lowercase()
        val text = node.text?.toString().orEmpty().lowercase()
        if (id.contains("notification_stack_scroller") ||
            id.contains("qs_frame") ||
            id.contains("quick_settings") ||
            id.contains("notification_panel") ||
            id.contains("qs_container") ||
            id.contains("control_center") ||
            desc.contains("notificationshade") ||
            (text.contains("уведомления") && id.contains("systemui"))
        ) {
            return true
        }
        val count = node.childCount.coerceAtMost(10)
        for (i in 0 until count) {
            if (containsShadeMarker(node.getChild(i), depth + 1)) return true
        }
        return false
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
    fun showVistaShadeOverlay() {
        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@post
            dismissVistaShadeOverlay()

            val density = resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density).toInt()

            val rootOverlay = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.argb(185, 4, 8, 14))
                setPadding(dp(12), getStatusBarHeightPx() + dp(8), dp(12), dp(16))
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(245, 22, 34, 52), Color.argb(245, 11, 18, 28))
                ).apply {
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.argb(200, 96, 174, 240))
                }
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }

            // Header bar
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.rgb(42, 62, 88), Color.rgb(22, 33, 49))
                ).apply { cornerRadius = dp(4).toFloat() }
                setPadding(dp(10), dp(7), dp(8), dp(7))
            }
            val titleTv = TextView(this).apply {
                text = "KEYDECK • ШТОРКА"
                setTextColor(Color.rgb(168, 220, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val hubBtn = createShadePillButton("Хаб", Color.rgb(36, 98, 168)) {
                dismissVistaShadeOverlay()
                openLauncherHub()
            }
            val closeBtn = createShadePillButton("✕", Color.rgb(92, 42, 52)) {
                dismissVistaShadeOverlay()
            }
            header.addView(titleTv)
            header.addView(hubBtn)
            header.addView(spaceView(dp(6)))
            header.addView(closeBtn)
            card.addView(header)
            card.addView(spaceView(dp(8)))

            // Quick toggles row 1: Wi-Fi, Bluetooth, Torch
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row1.addView(createShadeTileButton("Wi-Fi", true) {
                dismissVistaShadeOverlay()
                runCatching {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            })
            row1.addView(spaceView(dp(6)))
            row1.addView(createShadeTileButton("Bluetooth", true) {
                dismissVistaShadeOverlay()
                runCatching {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            })
            row1.addView(spaceView(dp(6)))
            var torchBtn: TextView? = null
            torchBtn = createShadeTileButton(if (isTorchOn) "Фонарь: ВКЛ" else "Фонарик", isTorchOn) {
                val next = !isTorchOn
                if (setFlashlightEnabled(next)) {
                    isTorchOn = next
                    torchBtn?.text = if (next) "Фонарь: ВКЛ" else "Фонарик"
                    torchBtn?.background = shadeButtonDrawable(next)
                }
            }
            row1.addView(torchBtn)
            card.addView(row1)
            card.addView(spaceView(dp(6)))

            // Quick toggles row 2: Sound, Auto-Rotate, DND
            val am = getSystemService(AUDIO_SERVICE) as? AudioManager
            val ringerMuted = am?.ringerMode != AudioManager.RINGER_MODE_NORMAL
            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            var soundBtn: TextView? = null
            soundBtn = createShadeTileButton(if (ringerMuted) "Звук: Тихо" else "Звук: ВКЛ", !ringerMuted) {
                am?.let { audio ->
                    val nextMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        AudioManager.RINGER_MODE_VIBRATE
                    } else {
                        AudioManager.RINGER_MODE_NORMAL
                    }
                    runCatching { audio.ringerMode = nextMode }
                    val nowNormal = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
                    soundBtn?.text = if (nowNormal) "Звук: ВКЛ" else "Звук: Тихо"
                    soundBtn?.background = shadeButtonDrawable(nowNormal)
                }
            }
            row2.addView(soundBtn)
            row2.addView(spaceView(dp(6)))

            val rotOn = runCatching {
                Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            }.getOrDefault(false)
            var rotBtn: TextView? = null
            rotBtn = createShadeTileButton(if (rotOn) "Поворот: ВКЛ" else "Поворот: ВЫКЛ", rotOn) {
                if (Settings.System.canWrite(this)) {
                    val cur = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                    val next = !cur
                    Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (next) 1 else 0)
                    rotBtn?.text = if (next) "Поворот: ВКЛ" else "Поворот: ВЫКЛ"
                    rotBtn?.background = shadeButtonDrawable(next)
                } else {
                    dismissVistaShadeOverlay()
                    runCatching {
                        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            row2.addView(rotBtn)
            row2.addView(spaceView(dp(6)))

            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            val dndActive = nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            var dndBtn: TextView? = null
            dndBtn = createShadeTileButton(if (dndActive) "Не бесп.: ВКЛ" else "Не бесп.", dndActive) {
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    val curDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    val nextFilter = if (curDnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    nm.setInterruptionFilter(nextFilter)
                    val nowDnd = !curDnd
                    dndBtn?.text = if (nowDnd) "Не бесп.: ВКЛ" else "Не бесп."
                    dndBtn?.background = shadeButtonDrawable(nowDnd)
                } else {
                    dismissVistaShadeOverlay()
                    runCatching {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            row2.addView(dndBtn)
            card.addView(row2)
            card.addView(spaceView(dp(8)))

            // Brightness bar
            val curBright = runCatching {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 140)
            }.getOrDefault(140).coerceIn(10, 255)
            val brightRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shadeInsetDrawable()
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }
            val brightLbl = TextView(this).apply {
                text = "Яркость"
                setTextColor(Color.rgb(185, 206, 232))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
            val brightSeek = SeekBar(this).apply {
                max = 245
                progress = (curBright - 10).coerceIn(0, 245)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        if (Settings.System.canWrite(this@KeyDeckAccessibilityService)) {
                            runCatching {
                                Settings.System.putInt(
                                    contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                                )
                                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress + 10)
                            }
                        }
                    }

                    override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        if (!Settings.System.canWrite(this@KeyDeckAccessibilityService)) {
                            Toast.makeText(
                                this@KeyDeckAccessibilityService,
                                "Разрешите изменение системных настроек для яркости",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            }
            brightRow.addView(brightLbl)
            brightRow.addView(brightSeek)
            card.addView(brightRow)
            card.addView(spaceView(dp(8)))

            // Live music transport strip
            val activeSession = mediaRepo?.mediaState?.value?.activeSession
            val musicBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shadeInsetDrawable()
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            val trackTv = TextView(this).apply {
                text = if (activeSession != null) {
                    "♫ ${activeSession.title} — ${activeSession.artist.ifBlank { activeSession.appLabel }}"
                } else {
                    "♫ Плеер не активен"
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            musicBox.addView(trackTv)
            musicBox.addView(createShadePillButton("⏮", Color.rgb(38, 52, 72)) {
                mediaRepo?.skipPrevious() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            })
            musicBox.addView(spaceView(dp(4)))
            musicBox.addView(createShadePillButton(if (activeSession?.isPlaying == true) "⏸" else "▶", Color.rgb(36, 102, 174)) {
                mediaRepo?.togglePlayPause() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            })
            musicBox.addView(spaceView(dp(4)))
            musicBox.addView(createShadePillButton("⏭", Color.rgb(38, 52, 72)) {
                mediaRepo?.skipNext() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            })
            card.addView(musicBox)
            card.addView(spaceView(dp(8)))

            // Notifications list + Clear All
            val notifHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val notifs = NotificationHubRepository.notifications.value
            val notifCountTv = TextView(this).apply {
                text = "УВЕДОМЛЕНИЯ (${notifs.size})"
                setTextColor(Color.rgb(142, 198, 250))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val clearAllBtn = createShadePillButton("Очистить всё", Color.rgb(66, 42, 48)) {
                NotificationHubRepository.clearAllNotifications()
                dismissVistaShadeOverlay()
            }
            notifHeader.addView(notifCountTv)
            if (notifs.isNotEmpty()) notifHeader.addView(clearAllBtn)
            card.addView(notifHeader)
            card.addView(spaceView(dp(4)))

            val scroll = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.36f).toInt()
                )
            }
            val listCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            if (notifs.isEmpty()) {
                listCol.addView(
                    TextView(this).apply {
                        text = "Нет активных уведомлений"
                        setTextColor(Color.rgb(150, 170, 194))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setPadding(dp(8), dp(14), dp(8), dp(14))
                    }
                )
            } else {
                notifs.take(15).forEach { item ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = shadeInsetDrawable()
                        setPadding(dp(8), dp(7), dp(8), dp(7))
                    }
                    val textCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    textCol.addView(TextView(this).apply {
                        text = "${item.appName} • ${item.title} (${item.timeFormatted})"
                        setTextColor(Color.rgb(122, 200, 255))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        paint.isFakeBoldText = true
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    textCol.addView(TextView(this).apply {
                        text = item.bigText.ifBlank { item.text }
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    row.addView(textCol)
                    row.addView(spaceView(dp(6)))
                    row.addView(createShadePillButton("Откр", Color.rgb(34, 88, 152)) {
                        dismissVistaShadeOverlay()
                        NotificationHubRepository.openNotification(this@KeyDeckAccessibilityService, item.key)
                    })
                    row.addView(spaceView(dp(4)))
                    row.addView(createShadePillButton("✕", Color.rgb(74, 40, 44)) {
                        NotificationHubRepository.dismissNotification(item.key)
                        listCol.removeView(row)
                    })
                    listCol.addView(row)
                    listCol.addView(spaceView(dp(5)))
                }
            }
            scroll.addView(listCol)
            card.addView(scroll)

            rootOverlay.addView(card)
            rootOverlay.setOnClickListener { dismissVistaShadeOverlay() }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }
            runCatching {
                wm.addView(rootOverlay, params)
                vistaShadeOverlayView = rootOverlay
            }
        }
    }

    fun dismissVistaShadeOverlay() {
        val v = vistaShadeOverlayView ?: return
        vistaShadeOverlayView = null
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeViewImmediate(v) }
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
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(236, 30, 46, 68), Color.argb(236, 15, 24, 38))
                ).apply {
                    cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.argb(200, 102, 182, 246))
                }
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }

            if (workspaceBarCollapsed) {
                barContainer.addView(createShadePillButton("▼ $currentWorkspaceLabel", Color.rgb(36, 98, 168)) {
                    workspaceBarCollapsed = false
                    showOrUpdateWorkspaceBar(currentWorkspaceLabel)
                })
                barContainer.addView(spaceView(dp(4)))
                barContainer.addView(createShadePillButton("✕", Color.rgb(88, 38, 44)) {
                    dismissWorkspaceBar()
                })
            } else {
                val activeTrack = mediaRepo?.mediaState?.value?.activeSession?.title ?: "Музыка"
                val trackBtn = createShadePillButton(
                    text = "♫ ${activeTrack.take(12)}",
                    bgColor = Color.rgb(32, 54, 82)
                ) {
                    mediaRepo?.togglePlayPause() ?: dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                val searchBtn = createShadePillButton("🔍", Color.rgb(38, 66, 102)) {
                    openMainWithAction(EXTRA_OPEN_SEARCH, true)
                }
                val pasteBtn = createShadePillButton("Ctrl+V", Color.rgb(42, 84, 118)) {
                    if (!performAccessibilityPaste()) {
                        Toast.makeText(this@KeyDeckAccessibilityService, "Поле ввода не выбрано", Toast.LENGTH_SHORT).show()
                    }
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

                listOf(trackBtn, searchBtn, pasteBtn, prevWsBtn, homeWsBtn, nextWsBtn, collapseBtn, closeBtn).forEachIndexed { idx, btn ->
                    if (idx > 0) barContainer.addView(spaceView(dp(4)))
                    barContainer.addView(btn)
                }
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

    private fun setFlashlightEnabled(enabled: Boolean): Boolean {
        val cm = getSystemService(CAMERA_SERVICE) as? CameraManager ?: return false
        return runCatching {
            val camId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return@runCatching false
            cm.setTorchMode(camId, enabled)
            true
        }.getOrDefault(false)
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

    private fun shadeButtonDrawable(active: Boolean): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (active) intArrayOf(Color.rgb(48, 128, 214), Color.rgb(24, 72, 138))
            else intArrayOf(Color.rgb(42, 56, 76), Color.rgb(24, 34, 48))
        ).apply {
            cornerRadius = 5f * resources.displayMetrics.density
            setStroke((1f * resources.displayMetrics.density).toInt(), Color.argb(170, 120, 185, 240))
        }
    }

    private fun shadeInsetDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.argb(215, 10, 16, 25))
            cornerRadius = 5f * resources.displayMetrics.density
            setStroke((1f * resources.displayMetrics.density).toInt(), Color.argb(120, 74, 118, 164))
        }
    }

    private fun createShadeTileButton(label: String, active: Boolean, onClick: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = shadeButtonDrawable(active)
            setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
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
        dismissVistaShadeOverlay()
        dismissWorkspaceBar()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceRef?.get() === this) {
            serviceRef = null
        }
        pendingScreenshotRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissVistaShadeOverlay()
        dismissWorkspaceBar()
        unregisterScreenReceiver()
        shizukuBridge?.stopObserving()
        mediaRepo?.stopObserving()
        serviceScope.cancel()
        (getSystemService(WINDOW_SERVICE) as? WindowManager)?.let { removeEdgeStrips(it) }
    }
}
