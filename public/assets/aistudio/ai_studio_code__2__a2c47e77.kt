// core/bridge/SystemBridge.kt
package com.yourname.wplauncher.core.bridge

data class RecentTaskItem(
    val packageName: String,
    val componentName: String
)

interface SystemBridge {
    val isShizukuReady: Boolean
    fun bind()
    fun unbind()
    fun requestPermissionIfNeeded()
    suspend fun expandNotificationShade()
    suspend fun getRecentTasks(): List<RecentTaskItem>
    suspend fun switchToTask(task: RecentTaskItem)
}