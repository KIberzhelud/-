package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.example.model.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuRuntimeFlags {
    val interceptShadeEnabled = AtomicBoolean(false)
    val interceptLockScreenEnabled = AtomicBoolean(false)
    val oemGestureBypassEnabled = AtomicBoolean(false)
}

class ShizukuBridge(private val context: Context) {

    companion object {
        private val SHIZUKU_PACKAGES = listOf(
            "moe.shizuku.privileged.api",
            "moe.shizuku.manager"
        )
        private const val REQUEST_CODE_SHIZUKU = 9104

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedFlat = ComponentName(context, KeyDeckAccessibilityService::class.java).flattenToString()
            val expectedShort = "${context.packageName}/${KeyDeckAccessibilityService::class.java.name}"
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull().orEmpty()
            return enabled.split(':')
                .any { it.equals(expectedFlat, ignoreCase = true) || it.equals(expectedShort, ignoreCase = true) }
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
            } else {
                true
            }
        }
    }

    private val _statusState = MutableStateFlow(ShizukuState())
    val statusState: StateFlow<ShizukuState> = _statusState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus("Связь с Shizuku установлена")
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

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    fun refreshStatus(message: String? = _statusState.value.lastCommandStatus) {
        val installedPkg = resolveInstalledShizukuPackage()
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val version = if (binderAlive) {
            runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        } else {
            -1
        }
        val permissionGranted = if (binderAlive) {
            runCatching {
                !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }
        val installed = installedPkg != null || binderAlive
        val listenerGranted = NotificationHubRepository.isNotificationAccessGranted(context)
        val a11yGranted = isAccessibilityServiceEnabled(context)
        val overlayGranted = hasOverlayPermission(context)
        val rootAvail = detectRootBinaryFast()

        _statusState.value = _statusState.value.copy(
            isInstalled = installed,
            isBinderAlive = binderAlive,
            isPermissionGranted = permissionGranted,
            isRootAvailable = rootAvail,
            isNotificationListenerGranted = listenerGranted,
            isAccessibilityEnabled = a11yGranted,
            hasOverlayPermission = overlayGranted,
            shizukuVersion = version,
            interceptShadeEnabled = ShizukuRuntimeFlags.interceptShadeEnabled.get(),
            interceptLockScreenEnabled = ShizukuRuntimeFlags.interceptLockScreenEnabled.get(),
            oemGestureBypassEnabled = ShizukuRuntimeFlags.oemGestureBypassEnabled.get(),
            lastCommandStatus = message
        )
    }

    fun updateInterceptFlags(
        shadeEnabled: Boolean,
        lockEnabled: Boolean,
        oemGestureBypass: Boolean = ShizukuRuntimeFlags.oemGestureBypassEnabled.get()
    ) {
        ShizukuRuntimeFlags.interceptShadeEnabled.set(shadeEnabled)
        ShizukuRuntimeFlags.interceptLockScreenEnabled.set(lockEnabled)
        ShizukuRuntimeFlags.oemGestureBypassEnabled.set(oemGestureBypass)
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
            if (Shizuku.isPreV11()) {
                refreshStatus("Требуется Shizuku v11+ (устаревшая версия)")
                return@runCatching false
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus("Доступ Shizuku активен")
                true
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                true
            }
        }.getOrDefault(false)
    }

    fun openShizukuManagerApp(): Boolean {
        for (pkg in SHIZUKU_PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                val started = runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (started) return true
            }
        }
        return false
    }

    suspend fun grantNotificationAccessViaShizuku(): Result<String> {
        val component = ComponentName(context, LauncherMediaListenerService::class.java).flattenToString()
        val result = runPrivilegedOrRootShell("cmd notification allow_listener $component")
        refreshStatus(
            if (result.isSuccess) "Доступ к уведомлениям выдан (Shizuku/Root)"
            else "Ошибка: ${result.exceptionOrNull()?.message}"
        )
        return result
    }

    suspend fun grantOverlayAndAccessibilityViaShizuku(): Result<String> {
        val pkg = context.packageName
        val serviceName = "$pkg/${KeyDeckAccessibilityService::class.java.name}"
        val cmd = """
            cmd appops set $pkg SYSTEM_ALERT_WINDOW allow;
            CUR=$(settings get secure enabled_accessibility_services 2>/dev/null);
            case "${'$'}CUR" in
              ""|"null") NEXT="$serviceName" ;;
              *"$serviceName"*) NEXT="${'$'}CUR" ;;
              *) NEXT="${'$'}CUR:$serviceName" ;;
            esac;
            settings put secure enabled_accessibility_services "${'$'}NEXT";
            settings put secure accessibility_enabled 1
        """.trimIndent().replace("\n", " ")
        val res = runPrivilegedOrRootShell(cmd)
        refreshStatus(
            if (res.isSuccess) "Наложение и Спец. возможности включены"
            else "Ошибка активации: ${res.exceptionOrNull()?.message}"
        )
        return res
    }

    suspend fun applyOemGestureBypass(enabled: Boolean): Result<String> {
        ShizukuRuntimeFlags.oemGestureBypassEnabled.set(enabled)
        val cmd = if (enabled) {
            "settings put global force_fsg_nav_bar 1; settings put secure navigation_mode 2"
        } else {
            "settings put global force_fsg_nav_bar 0"
        }
        val res = runPrivilegedOrRootShell(cmd)
        refreshStatus(
            if (res.isSuccess) {
                if (enabled) "Обход блокировки жестов OEM включён"
                else "Обход блокировки жестов отключён"
            } else {
                if (enabled) "Включён жестовый слой поверх экрана (Edge Trigger)"
                else "Жестовый слой отключён"
            }
        )
        return res
    }

    suspend fun collapseSystemShadeViaShizuku(): Result<String> {
        val result = runPrivilegedOrRootShell("cmd statusbar collapse")
        refreshStatus(
            if (result.isSuccess) "Системная шторка свернута (перехвачена Хабом)"
            else result.exceptionOrNull()?.message
        )
        return result
    }

    suspend fun dismissSystemKeyguardViaShizuku(): Result<String> {
        return runPrivilegedOrRootShell("wm dismiss-keyguard")
    }

    suspend fun forceStopPackagesViaShizuku(packages: Collection<String>): Result<String> {
        val safePkgs = packages
            .map { it.trim() }
            .filter { it.matches(Regex("""^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$""")) }
            .distinct()
        if (safePkgs.isEmpty()) return Result.success("Нет пакетов")
        val cmd = safePkgs.joinToString("; ") { "am force-stop $it" }
        return runPrivilegedOrRootShell(cmd)
    }

    suspend fun findRunningPackagesViaShizuku(candidates: Collection<String>): Set<String> {
        val safePkgs = candidates
            .map { it.trim() }
            .filter { it.matches(Regex("""^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$""")) }
            .distinct()
        if (safePkgs.isEmpty()) return emptySet()
        val probeCmd = "for p in ${safePkgs.joinToString(" ")}; do pidof \"${'$'}p\" >/dev/null 2>&1 && echo \"${'$'}p\"; done"
        val res = runPrivilegedOrRootShell(probeCmd)
        val out = res.getOrNull().orEmpty()
        return out.lineSequence()
            .map { it.trim() }
            .filter { it in safePkgs }
            .toSet()
    }

    suspend fun queryRecentPackagesViaPrivilegedOrRoot(): List<String> {
        val cmd = "dumpsys activity recents 2>/dev/null | grep -E 'Recent #|realActivity=' | head -n 28"
        val res = runPrivilegedOrRootShell(cmd)
        val out = res.getOrNull().orEmpty()
        if (out.isBlank()) return emptyList()
        val pkgRegex = Regex("""realActivity=([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/""")
        val found = LinkedHashSet<String>()
        pkgRegex.findAll(out).forEach { match ->
            val pkg = match.groupValues.getOrNull(1)
            if (!pkg.isNullOrBlank() && pkg != context.packageName) {
                found.add(pkg)
            }
        }
        return found.toList()
    }

    suspend fun dumpUiTreeXmlViaRoot(): String? {
        val dumpCmd = "uiautomator dump /data/local/tmp/keydeck_ui.xml >/dev/null 2>&1 && cat /data/local/tmp/keydeck_ui.xml"
        val res = runPrivilegedOrRootShell(dumpCmd)
        val xml = res.getOrNull() ?: return null
        return xml.takeIf { it.contains("<hierarchy") && it.contains("<node") }
    }

    suspend fun grantBackgroundAudioFreedomViaShizuku(packages: Collection<String>): Result<String> {
        val safePkgs = packages
            .map { it.trim() }
            .filter { it.matches(Regex("""^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$""")) }
            .distinct()
        if (safePkgs.isEmpty()) return Result.success("Нет пакетов")
        val cmd = safePkgs.joinToString("; ") { pkg ->
            "cmd appops set $pkg RUN_IN_BACKGROUND allow; " +
                "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; " +
                "cmd appops set $pkg TAKE_AUDIO_FOCUS allow; " +
                "cmd appops set $pkg PICTURE_IN_PICTURE allow; " +
                "dumpsys deviceidle whitelist +$pkg; " +
                "am set-standby-bucket $pkg active"
        }
        return runPrivilegedOrRootShell(cmd)
    }

    suspend fun dispatchMediaPlayViaShizuku(): Result<String> {
        return runPrivilegedOrRootShell("cmd media_session dispatch play || input keyevent 126")
    }

    suspend fun runPrivilegedOrRootShell(command: String): Result<String> {
        val shizukuRes = runPrivilegedShell(command)
        if (shizukuRes.isSuccess) return shizukuRes
        if (detectRootBinaryFast()) {
            val rootRes = runRootShell(command)
            if (rootRes.isSuccess) return rootRes
        }
        return shizukuRes
    }

    suspend fun runRootShell(command: String, timeoutMs: Long = 2500L): Result<String> = withContext(Dispatchers.IO) {
        if (!detectRootBinaryFast()) {
            return@withContext Result.failure(IllegalStateException("Root (su) не найден"))
        }
        runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("Таймаут Root-команды")
            }
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                stdout.ifBlank { "OK" }
            } else {
                throw IllegalStateException(stderr.ifBlank { "Код выхода su: $exitCode" })
            }
        }
    }

    suspend fun runPrivilegedShell(command: String): Result<String> = withContext(Dispatchers.IO) {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return@withContext Result.failure(IllegalStateException("Сервис Shizuku не активен"))
        }
        val granted = runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
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

    private fun detectRootBinaryFast(): Boolean {
        cachedRootAvailable?.let { return it }
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/magisk/.core/bin/su"
        )
        val hasSuBinary = runCatching {
            suPaths.any { File(it).exists() }
        }.getOrDefault(false)
        cachedRootAvailable = hasSuBinary
        return hasSuBinary
    }

    private fun resolveInstalledShizukuPackage(): String? {
        for (pkg in SHIZUKU_PACKAGES) {
            val exists = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
            if (exists) return pkg
        }
        return null
    }
}
