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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Shared tracker of packages visited outside KeyDeck, updated both when KeyDeck launches an app
 * and when KeyDeckAccessibilityService observes foreground window switches (e.g. hardware camera shortcut).
 */
object RecentForegroundTracker {
    private val visitedPackages = Collections.synchronizedSet(LinkedHashSet<String>())
    private val recentHistory = Collections.synchronizedMap(LinkedHashMap<String, Long>())
    private val dismissedAtMs = Collections.synchronizedMap(HashMap<String, Long>())

    @Volatile
    var currentForegroundPackage: String? = null
        private set

    fun recordForeground(packageName: String, launcherPackage: String) {
        if (packageName.isBlank() || packageName == launcherPackage) return
        currentForegroundPackage = packageName
        if (
            packageName == "com.android.systemui" ||
            packageName.startsWith("com.google.android.inputmethod") ||
            packageName.contains("keyboard")
        ) {
            return
        }
        val now = System.currentTimeMillis()
        visitedPackages.add(packageName)
        dismissedAtMs.remove(packageName)
        synchronized(recentHistory) {
            recentHistory.remove(packageName)
            recentHistory[packageName] = now
            while (recentHistory.size > 18) {
                val oldest = recentHistory.keys.firstOrNull() ?: break
                recentHistory.remove(oldest)
            }
        }
    }

    fun drainVisited(): Set<String> {
        synchronized(visitedPackages) {
            val copy = visitedPackages.toSet()
            visitedPackages.clear()
            return copy
        }
    }

    fun snapshotRecentHistory(): List<Pair<String, Long>> {
        synchronized(recentHistory) {
            return recentHistory.entries.map { it.key to it.value }.reversed()
        }
    }

    fun isRecentlyDismissed(packageName: String, recentWindowMs: Long = 120_000L): Boolean {
        val ts = dismissedAtMs[packageName] ?: return false
        return (System.currentTimeMillis() - ts) < recentWindowMs
    }

    fun removeFromHistory(packageName: String) {
        dismissedAtMs[packageName] = System.currentTimeMillis()
        synchronized(recentHistory) {
            recentHistory.remove(packageName)
        }
        visitedPackages.remove(packageName)
        AppPreviewCache.remove(packageName)
    }

    fun clearHistoryExcept(pinned: Set<String>) {
        val now = System.currentTimeMillis()
        synchronized(recentHistory) {
            val it = recentHistory.keys.iterator()
            while (it.hasNext()) {
                val pkg = it.next()
                if (!pinned.contains(pkg)) {
                    dismissedAtMs[pkg] = now
                    it.remove()
                    AppPreviewCache.remove(pkg)
                }
            }
        }
    }

    fun clearForegroundIfLauncher(launcherPackage: String) {
        if (currentForegroundPackage != launcherPackage) {
            currentForegroundPackage = launcherPackage
        }
    }
}

/**
 * Lightweight bounded LRU cache for captured recent app screenshots / thumbnails.
 */
object AppPreviewCache {
    private const val MAX_PREVIEWS = 14
    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?
        ): Boolean = size > MAX_PREVIEWS
    }
    private val lastCaptureMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun put(packageName: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        if (packageName.isBlank()) return
        synchronized(cache) {
            cache[packageName] = bitmap
        }
        lastCaptureMs[packageName] = System.currentTimeMillis()
    }

    fun get(packageName: String): androidx.compose.ui.graphics.ImageBitmap? {
        synchronized(cache) {
            return cache[packageName]
        }
    }

    fun shouldCapture(packageName: String, minIntervalMs: Long = 2200L): Boolean {
        val prev = lastCaptureMs[packageName] ?: 0L
        return (System.currentTimeMillis() - prev) >= minIntervalMs
    }

    fun remove(packageName: String) {
        synchronized(cache) {
            cache.remove(packageName)
        }
        lastCaptureMs.remove(packageName)
    }
}

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

    fun recordLaunchedPackage(packageName: String) {
        RecentForegroundTracker.recordForeground(packageName, context.packageName)
    }

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
        val safeTargets = resolvePackagesToTerminate(configuredPackages, activeMediaPackages)
        if (safeTargets.isEmpty()) {
            RecentForegroundTracker.drainVisited()
            return@withContext 0
        }

        // Brief settle delay so exiting activity finishes moving to background before force-stop / kill
        delay(260L)

        val visited = RecentForegroundTracker.drainVisited()
        val visitedTargets = safeTargets.filter { visited.contains(it) }.toMutableSet()

        val shizuku = shizukuBridge.statusState.value
        val privilegedActive = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable

        if (privilegedActive) {
            // Also detect any configured one-time app opened outside KeyDeck (e.g. power-button Camera)
            val aliveOneTime = shizukuBridge.findRunningPackagesViaShizuku(safeTargets)
            visitedTargets.addAll(aliveOneTime)
        } else {
            // Without Shizuku/Root, Android hides other apps from runningAppProcesses, so kill all safe targets
            // if user had paused KeyDeck or visited any target.
            visitedTargets.addAll(safeTargets)
        }

        val toKill = visitedTargets.toList()
        if (toKill.isEmpty()) return@withContext 0

        toKill.forEach { pkg ->
            runCatching { activityManager?.killBackgroundProcesses(pkg) }
        }

        if (privilegedActive) {
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
