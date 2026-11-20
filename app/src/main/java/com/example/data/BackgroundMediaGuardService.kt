package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground MediaPlayback Service that acts as:
 * 1. Anti-pause background audio guard for external video/music apps (YouTube, VK Video, Rutube, browsers, etc.)
 * 2. Reliable screen-off monitor that holds PARTIAL_WAKE_LOCK, keeps persistent media audio focus,
 *    resumes spoofed players on screen-off, and triggers KeyDeck LockScreen immediately before unlock.
 */
class BackgroundMediaGuardService : Service() {

    companion object {
        private const val CHANNEL_ID = "keydeck_bg_media_guard"
        private const val NOTIFICATION_ID = 4702
        const val ACTION_START = "com.example.action.START_BG_GUARD"
        const val ACTION_STOP = "com.example.action.STOP_BG_GUARD"
        const val ACTION_RESUME_PKG = "com.example.action.RESUME_BG_PKG"
        const val EXTRA_PACKAGE_NAME = "extra_pkg"

        fun syncServiceState(context: Context, enabled: Boolean) {
            val intent = Intent(context, BackgroundMediaGuardService::class.java).apply {
                action = if (enabled) ACTION_START else ACTION_STOP
            }
            if (enabled) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var shizukuBridge: ShizukuBridge? = null
    private var mediaRepo: MediaControlRepository? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var retryJob: Job? = null
    private var lastSpoofBurstAtMs: Long = 0L
    private var activeFocusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ShizukuRuntimeFlags.loadPersistedFlags(applicationContext)
        shizukuBridge = ShizukuBridge(applicationContext).also { it.startObserving() }
        mediaRepo = MediaControlRepository(applicationContext).also { it.startObserving() }

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeyDeck:BgMediaGuard")?.apply {
            setReferenceCounted(false)
            runCatching { acquire(6 * 60 * 60 * 1000L) }
        }

        MediaGuardRuntime.onUnexpectedPauseDetected = { pkg ->
            mainHandler.post {
                triggerSpoofResumeBurst(pkg, reason = "Свернуто / пауза")
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        MediaGuardRuntime.isScreenOff = true
                        if (MediaGuardRuntime.isGuardEnabled) {
                            val guardedActive = mediaRepo?.mediaState?.value?.sessions
                                ?.firstOrNull { MediaGuardRuntime.guardedPackages.contains(it.packageName) }
                            if (guardedActive != null) {
                                triggerSpoofResumeBurst(guardedActive.packageName, reason = "Экран выключен")
                            }
                        }
                        if (ShizukuRuntimeFlags.interceptLockScreenEnabled.get()) {
                            val lockIntent = Intent(this@BackgroundMediaGuardService, LockScreenActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                                )
                            }
                            runCatching { startActivity(lockIntent) }
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        MediaGuardRuntime.isScreenOff = false
                        if (ShizukuRuntimeFlags.interceptLockScreenEnabled.get()) {
                            val lockIntent = Intent(this@BackgroundMediaGuardService, LockScreenActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                                )
                            }
                            runCatching { startActivity(lockIntent) }
                        }
                    }
                }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val titleText = if (MediaGuardRuntime.isGuardEnabled) {
            "Защита фонового звука активна"
        } else {
            "Монитор экрана блокировки активен"
        }
        startForeground(NOTIFICATION_ID, buildNotification(titleText))
        if (intent?.action == ACTION_RESUME_PKG) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            if (!pkg.isNullOrBlank()) {
                triggerSpoofResumeBurst(pkg, reason = "Ручной обход")
            }
        }
        return START_STICKY
    }

    private fun triggerSpoofResumeBurst(packageName: String, reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSpoofBurstAtMs < 600L) return
        lastSpoofBurstAtMs = now

        retryJob?.cancel()
        retryJob = serviceScope.launch {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.notify(NOTIFICATION_ID, buildNotification("Фоновый обход: $packageName ($reason)"))

            val shizuku = shizukuBridge
            if (shizuku != null &&
                shizuku.statusState.value.isBinderAlive &&
                shizuku.statusState.value.isPermissionGranted
            ) {
                launch(Dispatchers.IO) {
                    shizuku.grantBackgroundAudioFreedomViaShizuku(listOf(packageName))
                }
            }

            acquirePersistentPlaybackAudioFocus()

            val delays = listOf(80L, 320L, 780L, 1450L)
            for (d in delays) {
                delay(d)
                if (!MediaGuardRuntime.isGuardEnabled) break
                val elapsedPause = SystemClock.elapsedRealtime() - MediaGuardRuntime.lastUserPausedAtMs
                if (MediaGuardRuntime.lastUserPausedPackage == packageName && elapsedPause < 4500L) {
                    break
                }
                if (!MediaGuardRuntime.isScreenOff && MediaGuardRuntime.visibleWindowPackages.contains(packageName)) {
                    break
                }
                mediaRepo?.forceResumePackagePlayback(packageName)
                if (shizuku != null &&
                    shizuku.statusState.value.isBinderAlive &&
                    shizuku.statusState.value.isPermissionGranted
                ) {
                    launch(Dispatchers.IO) {
                        shizuku.dispatchMediaPlayViaShizuku()
                    }
                }
            }
        }
    }

    private fun acquirePersistentPlaybackAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (activeFocusRequest == null) {
                    activeFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setWillPauseWhenDucked(false)
                        .build()
                }
                activeFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
        val ts = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(ts, ts, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        am.dispatchMediaKeyEvent(KeyEvent(ts, ts, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
    }

    private fun abandonPersistentAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { req ->
                runCatching { am.abandonAudioFocusRequest(req) }
            }
        }
        activeFocusRequest = null
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaGuardRuntime.onUnexpectedPauseDetected = null
        retryJob?.cancel()
        serviceScope.cancel()
        abandonPersistentAudioFocus()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        mediaRepo?.stopObserving()
        shizukuBridge?.stopObserving()
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_guard_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundMediaGuardService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_symbian_music)
            .setContentTitle("KeyDeck • Фоновый обход плееров")
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopIntent)
            .build()
    }
}
