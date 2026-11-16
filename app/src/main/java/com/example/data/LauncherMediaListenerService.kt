package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ui.LockScreenActivity

class LauncherMediaListenerService : NotificationListenerService() {

    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                if (ShizukuRuntimeFlags.interceptLockScreenEnabled.get()) {
                    val lockIntent = Intent(this@LauncherMediaListenerService, LockScreenActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    }
                    runCatching { startActivity(lockIntent) }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHubRepository.attachService(this)
        MediaControlBus.notifyListenerChanged()
        registerScreenReceiverIfNeeded()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationHubRepository.detachService()
        MediaControlBus.notifyListenerChanged()
        unregisterScreenReceiverIfNeeded()
    }

    override fun onDestroy() {
        unregisterScreenReceiverIfNeeded()
        NotificationHubRepository.detachService()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        NotificationHubRepository.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotificationHubRepository.onRemoved(this, sbn)
    }

    private fun registerScreenReceiverIfNeeded() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }
    }

    private fun unregisterScreenReceiverIfNeeded() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }
}
