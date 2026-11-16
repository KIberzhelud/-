// shizuku/ShizukuUserService.kt
package com.yourname.wplauncher.shizuku

import android.content.Context
import com.yourname.wplauncher.IShizukuShellService
import kotlin.system.exitProcess

class ShizukuUserService(private val context: Context) : IShizukuShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun expandNotifications() {
        // На Samsung One UI и AOSP открывает шторку мгновенно без AccessibilityService
        exec("cmd statusbar expand-notifications")
    }

    override fun expandQuickSettings() {
        exec("cmd statusbar expand-settings")
    }

    override fun collapsePanels() {
        exec("cmd statusbar collapse")
    }

    override fun getRecentPackages(): String {
        // Возвращает список открытых/недавних задач по порядку без поллинга
        val output = exec("dumpsys activity recents")
        val regex = Regex("""Recent #\d+: Task\{.*?type=standard.*?A=\d+:([^ }]+).*?realActivity=\{([^}]+)\}""")
        return regex.findAll(output)
            .map { "${it.groupValues[1]}|${it.groupValues[2]}" }
            .distinct()
            .take(15)
            .joinToString("\n")
    }

    override fun bringPackageToFront(packageName: String, componentName: String) {
        if (componentName.isNotBlank()) {
            exec("am start --activity-reorder-to-front -n $componentName")
        } else {
            exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        }
    }

    override fun grantNotificationListener(component: String) {
        // Задел на будущее: автовыдача прав на чтение уведомлений для Live Tiles / кастомной шторки
        exec("cmd notification allow_listener $component")
    }

    private fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result
        } catch (e: Exception) {
            ""
        }
    }
}