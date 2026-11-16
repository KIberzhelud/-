// data/AppRepository.kt
package com.yourname.wplauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import com.yourname.wplauncher.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppRepository(context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconCache = LruCache<String, Drawable>(120)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) { refreshSync() }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) { refreshSync() }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) { refreshSync() }
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) { refreshSync() }
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) { refreshSync() }
    }

    init {
        launcherApps.registerCallback(callback)
    }

    suspend fun loadApps() = withContext(Dispatchers.IO) {
        refreshSync()
    }

    private fun refreshSync() {
        val list = launcherApps.getActivityList(null, Process.myUserHandle())
            .map { info ->
                AppInfo(
                    label = info.label.toString(),
                    packageName = info.applicationInfo.packageName,
                    componentName = info.componentName,
                    user = info.user
                )
            }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
    }

    fun getIcon(app: AppInfo): Drawable? {
        val key = app.componentName.flattenToShortString()
        iconCache.get(key)?.let { return it }
        val icon = launcherApps.getActivityList(app.packageName, app.user)
            .firstOrNull { it.componentName == app.componentName }
            ?.getBadgedIcon(0)
        if (icon != null) iconCache.put(key, icon)
        return icon
    }

    fun launch(app: AppInfo) {
        launcherApps.startMainActivity(app.componentName, app.user, null, null)
    }

    fun release() {
        launcherApps.unregisterCallback(callback)
        iconCache.evictAll()
    }
}