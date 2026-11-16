// core/bridge/ShizukuBridge.kt
package com.yourname.wplauncher.core.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.yourname.wplauncher.BuildConfig
import com.yourname.wplauncher.IShizukuShellService
import com.yourname.wplauncher.shizuku.ShizukuUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ShizukuBridge(private val context: Context) : SystemBridge {

    private var remoteService: IShizukuShellService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShizukuUserService::class.java.name)
    )
        .daemon(false) // Не держим лишний демон, если лаунчер выгружен
        .processNameSuffix("shizuku_service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.isBinderAlive) {
                remoteService = IShizukuShellService.Stub.asInterface(service)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
        }
    }

    override val isShizukuReady: Boolean
        get() = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun bind() {
        if (isShizukuReady && remoteService == null) {
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }

    override fun unbind() {
        if (remoteService != null) {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            } catch (_: Exception) {}
            remoteService = null
        }
    }

    override fun requestPermissionIfNeeded() {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1001)
        }
    }

    override suspend fun expandNotificationShade() = withContext(Dispatchers.IO) {
        if (remoteService == null) bind()
        try {
            remoteService?.expandNotifications()
        } catch (_: Exception) {
            // Fallback на случай, если Shizuku отключён: классический скрытый метод
            fallbackExpandStatusBar()
        }
        Unit
    }

    override suspend fun getRecentTasks(): List<RecentTaskItem> = withContext(Dispatchers.IO) {
        if (!isShizukuReady) return@withContext emptyList()
        if (remoteService == null) bind()
        val raw = remoteService?.getRecentPackages().orEmpty()
        raw.lines()
            .filter { it.contains("|") }
            .mapNotNull { line ->
                val parts = line.split("|")
                val pkg = parts.getOrNull(0) ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                RecentTaskItem(pkg, parts.getOrNull(1).orEmpty())
            }
    }

    override suspend fun switchToTask(task: RecentTaskItem) = withContext(Dispatchers.IO) {
        if (remoteService != null) {
            remoteService?.bringPackageToFront(task.packageName, task.componentName)
        } else {
            val intent = context.packageManager.getLaunchIntentForPackage(task.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            if (intent != null) context.startActivity(intent)
        }
    }

    @Suppress("PrivateApi")
    private fun fallbackExpandStatusBar() {
        try {
            val sbService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            statusBarManager.getMethod("expandNotificationsPanel").invoke(sbService)
        } catch (_: Exception) {}
    }
}