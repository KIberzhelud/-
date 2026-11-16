package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.model.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuRuntimeFlags {
    val interceptShadeEnabled = AtomicBoolean(false)
    val interceptLockScreenEnabled = AtomicBoolean(false)
}

class ShizukuBridge(private val context: Context) {

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.manager"
        private const val REQUEST_CODE_SHIZUKU = 9104
    }

    private val _statusState = MutableStateFlow(ShizukuState())
    val statusState: StateFlow<ShizukuState> = _statusState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatus("Связь с Shizuku потеряна")
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            refreshStatus(
                if (granted) "Разрешение Shizuku получено" else "Пользователь отклонил доступ Shizuku"
            )
        }
    }

    fun startObserving() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refreshStatus()
    }

    fun stopObserving() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
    }

    fun refreshStatus(message: String? = _statusState.value.lastCommandStatus) {
        val installed = isPackageInstalled(SHIZUKU_PACKAGE)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permissionGranted = if (binderAlive) {
            runCatching {
                !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }
        val listenerGranted = NotificationHubRepository.isNotificationAccessGranted(context)
        _statusState.value = _statusState.value.copy(
            isInstalled = installed,
            isBinderAlive = binderAlive,
            isPermissionGranted = permissionGranted,
            isNotificationListenerGranted = listenerGranted,
            interceptShadeEnabled = ShizukuRuntimeFlags.interceptShadeEnabled.get(),
            interceptLockScreenEnabled = ShizukuRuntimeFlags.interceptLockScreenEnabled.get(),
            lastCommandStatus = message
        )
    }

    fun updateInterceptFlags(shadeEnabled: Boolean, lockEnabled: Boolean) {
        ShizukuRuntimeFlags.interceptShadeEnabled.set(shadeEnabled)
        ShizukuRuntimeFlags.interceptLockScreenEnabled.set(lockEnabled)
        refreshStatus()
    }

    fun requestShizukuPermission(): Boolean {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            openShizukuManagerApp()
            refreshStatus("Запустите сервис Shizuku в приложении Shizuku")
            return false
        }
        return runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus("Доступ Shizuku уже активен")
                true
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                true
            }
        }.getOrDefault(false)
    }

    fun openShizukuManagerApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent != null) {
            runCatching { context.startActivity(intent) }
        }
    }

    suspend fun grantNotificationAccessViaShizuku(): Result<String> {
        val component = ComponentName(context, LauncherMediaListenerService::class.java).flattenToString()
        val result = runPrivilegedShell("cmd notification allow_listener $component")
        refreshStatus(
            if (result.isSuccess) "Доступ к уведомлениям выдан через Shizuku"
            else "Ошибка Shizuku: ${result.exceptionOrNull()?.message}"
        )
        return result
    }

    suspend fun collapseSystemShadeViaShizuku(): Result<String> {
        val result = runPrivilegedShell("cmd statusbar collapse")
        refreshStatus(
            if (result.isSuccess) "Системная шторка свернута (перехвачена Хабом)"
            else result.exceptionOrNull()?.message
        )
        return result
    }

    suspend fun dismissSystemKeyguardViaShizuku(): Result<String> {
        return runPrivilegedShell("wm dismiss-keyguard")
    }

    suspend fun runPrivilegedShell(command: String): Result<String> = withContext(Dispatchers.IO) {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return@withContext Result.failure(IllegalStateException("Сервис Shizuku не активен"))
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return@withContext Result.failure(SecurityException("Нет разрешения Shizuku"))
        }

        runCatching {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                stdout.ifBlank { "OK" }
            } else {
                throw IllegalStateException(stderr.ifBlank { "Код выхода: $exitCode" })
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }
}
