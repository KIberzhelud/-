package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.model.AndroidOptimizationStats
import com.example.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deep Android optimization & system integration helper:
 * - Reads real RAM metrics from ActivityManager.MemoryInfo
 * - Cleans background bloat processes & trims system app caches (with Shizuku boost)
 * - Disables Android 12+ Phantom Process Killer via device_config when Shizuku is active
 * - Configures App Standby Buckets & battery-idle whitelist for important apps
 */
class AndroidOptimizerManager(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    fun readRamStats(
        phantomPatched: Boolean = false,
        lastSummary: String? = null
    ): AndroidOptimizationStats {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val availMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceIn(0, totalMb)
        val usedPct = (((totalMb - availMb).toFloat() / totalMb.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
        return AndroidOptimizationStats(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            usedRamPercent = usedPct,
            phantomKillerPatched = phantomPatched,
            lastCleanupSummary = lastSummary
        )
    }

    suspend fun optimizeMemoryAndProcesses(
        allApps: List<AppEntry>,
        protectedPackages: Set<String>
    ): AndroidOptimizationStats = withContext(Dispatchers.IO) {
        val before = readRamStats()
        val candidatePkgs = allApps
            .map { it.packageName }
            .distinct()
            .filter { pkg ->
                pkg != context.packageName &&
                    !protectedPackages.contains(pkg) &&
                    !pkg.startsWith("com.android.systemui") &&
                    !pkg.contains("shizuku")
            }

        candidatePkgs.forEach { pkg ->
            runCatching { activityManager?.killBackgroundProcesses(pkg) }
        }

        val shizuku = shizukuBridge.statusState.value
        var shizukuNote = "Базовая очистка (${candidatePkgs.size} фоновых пакетов)"
        if (shizuku.isBinderAlive && shizuku.isPermissionGranted) {
            val cmd = buildString {
                append("am kill-all; ")
                append("pm trim-caches 512M >/dev/null 2>&1 || true; ")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    append("device_config set_sync_disabled_for_tests persistent >/dev/null 2>&1 || true; ")
                    append("device_config put activity_manager max_phantom_processes 2147483647 >/dev/null 2>&1 || true; ")
                }
                append("echo OK")
            }
            val res = shizukuBridge.runPrivilegedShell(cmd)
            if (res.isSuccess) {
                shizukuNote = "Shizuku: am kill-all + 512 МБ кэша + защита от Phantom Killer"
            }
        }

        val after = readRamStats(
            phantomPatched = shizuku.isBinderAlive && shizuku.isPermissionGranted,
            lastSummary = null
        )
        val freedMb = (after.availableRamMb - before.availableRamMb).coerceAtLeast(0)
        val summary = if (freedMb > 0) {
            "$shizukuNote • Освобождено ~$freedMb МБ ОЗУ"
        } else {
            "$shizukuNote • Свободно ${after.availableRamMb} МБ"
        }
        after.copy(lastCleanupSummary = summary)
    }

    suspend fun boostAppAndProtectFromDoze(packageName: String): Result<String> {
        return shizukuBridge.grantBackgroundAudioFreedomViaShizuku(listOf(packageName))
    }

    suspend fun disablePhantomProcessKillerViaShizuku(): Result<String> {
        return shizukuBridge.runPrivilegedShell(
            "device_config set_sync_disabled_for_tests persistent >/dev/null 2>&1 || true; " +
                "device_config put activity_manager max_phantom_processes 2147483647"
        )
    }
}
