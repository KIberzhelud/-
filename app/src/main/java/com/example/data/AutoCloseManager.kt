package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages "one-time / transient" apps (Camera, Dialer, Calculator, Scanners, etc.):
 * automatically kills background processes and force-stops them (via Shizuku if active)
 * whenever the user returns to the launcher by any navigation method (Home, Back, Recents, gesture).
 */
class AutoCloseManager(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var launchedOrVisitedPackages = mutableSetOf<String>()

    fun recordLaunchedPackage(packageName: String) {
        if (packageName.isNotBlank() && packageName != context.packageName) {
            launchedOrVisitedPackages.add(packageName)
        }
    }

    /**
     * Discovers default one-time utility apps installed on the device:
     * default Camera, default Dialer, Calculators, QR scanners, and standard OEM camera/phone packages.
     */
    fun discoverDefaultOneTimePackages(installedApps: List<AppEntry>): Set<String> {
        val result = linkedSetOf<String>()
        val pm = context.packageManager

        val probeIntents = listOf(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(MediaStore.ACTION_VIDEO_CAPTURE),
            Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:") },
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        )
        probeIntents.forEach { intent ->
            val resolved = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
            }.getOrDefault(emptyList())
            resolved.mapNotNullTo(result) { it.activityInfo?.packageName }
        }

        installedApps.forEach { app ->
            if (isHeuristicOneTimeApp(app.packageName, app.originalLabel)) {
                result.add(app.packageName)
            }
        }
        result.remove(context.packageName)
        return result
    }

    /**
     * Filters out unsafe-to-kill states (e.g., do NOT kill Dialer while user is actively in a phone call,
     * and do NOT kill a package actively playing media unless excluded).
     */
    fun resolvePackagesToTerminate(
        configuredPackages: Set<String>,
        activeMediaPackages: Set<String>
    ): List<String> {
        if (configuredPackages.isEmpty()) return emptyList()

        val inCallMode = runCatching {
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)

        return configuredPackages.filter { pkg ->
            if (pkg == context.packageName) return@filter false
            if (activeMediaPackages.contains(pkg)) return@filter false
            if (inCallMode && isDialerPackage(pkg)) return@filter false
            true
        }
    }

    suspend fun terminateExitedOneTimeApps(
        configuredPackages: Set<String>,
        activeMediaPackages: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val targets = resolvePackagesToTerminate(configuredPackages, activeMediaPackages)
        if (targets.isEmpty()) return@withContext 0

        // Prioritize packages recently launched/visited plus any configured package still lingering in memory
        val runningPkgs = runCatching {
            @Suppress("DEPRECATION")
            activityManager?.runningAppProcesses?.mapNotNull { it.processName?.substringBefore(':') }?.toSet()
        }.getOrNull().orEmpty()

        val toKill = targets.filter { pkg ->
            launchedOrVisitedPackages.contains(pkg) ||
                runningPkgs.isEmpty() ||
                runningPkgs.contains(pkg)
        }
        launchedOrVisitedPackages.clear()

        if (toKill.isEmpty()) return@withContext 0

        toKill.forEach { pkg ->
            runCatching { activityManager?.killBackgroundProcesses(pkg) }
        }

        val shizuku = shizukuBridge.statusState.value
        if (shizuku.isBinderAlive && shizuku.isPermissionGranted) {
            shizukuBridge.forceStopPackagesViaShizuku(toKill)
        }
        toKill.size
    }

    companion object {
        fun isHeuristicOneTimeApp(packageName: String, label: String): Boolean {
            val pkg = packageName.lowercase()
            val lbl = label.lowercase()
            val cameraTokens = listOf("camera", "opencamera", "gcam", "камера", "fotocamera")
            val dialerTokens = listOf("dialer", "incallui", "telecom", "android.phone", "звон", "телефон", "контакты")
            val utilityTokens = listOf("calculator", "калькулятор", "flashlight", "фонарик", "qr", "scanner", "сканер", "compass", "компас")
            return cameraTokens.any { pkg.contains(it) || lbl.contains(it) } ||
                dialerTokens.any { pkg.contains(it) || lbl == it } ||
                utilityTokens.any { pkg.contains(it) || lbl.contains(it) }
        }

        fun isDialerPackage(packageName: String): Boolean {
            val pkg = packageName.lowercase()
            return pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom") || pkg.contains("incallui")
        }
    }
}
