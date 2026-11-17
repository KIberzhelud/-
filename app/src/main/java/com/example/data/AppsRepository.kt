package com.example.data

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.HubCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class AppsRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val symbianDrawableCache = ConcurrentHashMap<Int, ImageBitmap>()
    private val customIconCache = ConcurrentHashMap<String, ImageBitmap>()

    fun observePackageChanges(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                clearCacheForPackage(packageName)
                trySend(Unit)
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                clearCacheForPackage(packageName)
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                clearCacheForPackage(packageName)
                trySend(Unit)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) {
                trySend(Unit)
            }
        }

        runCatching { launcherApps?.registerCallback(callback) }
        awaitClose {
            runCatching { launcherApps?.unregisterCallback(callback) }
        }
    }

    private fun clearCacheForPackage(packageName: String?) {
        if (packageName != null) {
            iconCache.keys.filter { it.startsWith("$packageName/") }.forEach { iconCache.remove(it) }
        }
    }

    suspend fun loadInstalledApps(pinnedIds: Set<String>): List<AppEntry> = withContext(Dispatchers.IO) {
        val selfPackage = context.packageName
        val musicCapablePackages = discoverMusicCapablePackages()

        val launcherEntries = runCatching {
            launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
        }.getOrDefault(emptyList())

        if (launcherEntries.isNotEmpty()) {
            return@withContext launcherEntries
                .asSequence()
                .filter { it.applicationInfo.packageName != selfPackage }
                .map { info ->
                    val pkg = info.applicationInfo.packageName
                    val act = info.componentName.className
                    val id = "$pkg/$act"
                    val label = info.label?.toString()?.trim().orEmpty().ifEmpty { pkg }
                    val androidIcon = iconCache.getOrPut(id) {
                        runCatching { drawableToImageBitmap(info.getBadgedIcon(0)) }.getOrNull()
                            ?: createFallbackBitmap()
                    }
                    val symbianRes = SymbianIconMapper.resolveSymbianIconRes(pkg, label)
                    val symbianBitmap = symbianRes?.let { resolveSymbianBitmap(it) }
                    val icon = symbianBitmap ?: androidIcon
                    AppEntry(
                        id = id,
                        label = label,
                        packageName = pkg,
                        activityName = act,
                        icon = icon,
                        initialChar = computeInitialChar(label),
                        isPinned = pinnedIds.contains(id),
                        hubCategory = classifyHubCategory(info.applicationInfo, pkg, label, musicCapablePackages),
                        colorBucket = abs(id.hashCode()) % 4,
                        isSymbianIcon = symbianBitmap != null,
                        originalLabel = label,
                        isRenamed = false,
                        iconMode = AppIconMode.AUTO,
                        androidIcon = androidIcon
                    )
                }
                .distinctBy { it.id }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
        }

        // Fallback to PackageManager if LauncherApps returns empty
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        resolveInfos
            .asSequence()
            .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != selfPackage }
            .map { info ->
                val activityInfo = info.activityInfo
                val pkg = activityInfo.packageName
                val act = activityInfo.name
                val id = "$pkg/$act"
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val androidIcon = iconCache.getOrPut(id) {
                    runCatching { drawableToImageBitmap(info.loadIcon(pm)) }.getOrNull()
                        ?: createFallbackBitmap()
                }
                val symbianRes = SymbianIconMapper.resolveSymbianIconRes(pkg, label)
                val symbianBitmap = symbianRes?.let { resolveSymbianBitmap(it) }
                val icon = symbianBitmap ?: androidIcon
                AppEntry(
                    id = id,
                    label = label,
                    packageName = pkg,
                    activityName = act,
                    icon = icon,
                    initialChar = computeInitialChar(label),
                    isPinned = pinnedIds.contains(id),
                    hubCategory = classifyHubCategory(activityInfo.applicationInfo, pkg, label, musicCapablePackages),
                    colorBucket = abs(id.hashCode()) % 4,
                    isSymbianIcon = symbianBitmap != null,
                    originalLabel = label,
                    isRenamed = false,
                    iconMode = AppIconMode.AUTO,
                    androidIcon = androidIcon
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun discoverMusicCapablePackages(): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf<String>()

        // 1. Services exposing MediaBrowserService (standard for music/audio players)
        val serviceIntent = Intent("android.media.browse.MediaBrowserService")
        val services = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(serviceIntent, 0)
            }
        }.getOrDefault(emptyList())
        services.mapNotNullTo(result) { it.serviceInfo?.packageName }

        // 2. Activities registered as MUSIC_PLAYER, CATEGORY_APP_MUSIC, or audio/* viewers
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) },
            Intent("android.intent.action.MUSIC_PLAYER"),
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file:///test.mp3"), "audio/*") }
        )
        intents.forEach { intent ->
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
        return result
    }

    fun applyCustomization(
        app: AppEntry,
        customLabel: String?,
        override: AppIconOverride?,
        isPinned: Boolean
    ): AppEntry {
        val effectiveLabel = customLabel?.takeIf { it.isNotBlank() } ?: app.originalLabel
        val mode = override?.mode ?: AppIconMode.AUTO
        val resolvedAndroid = app.androidIcon ?: app.icon

        val (resolvedIcon, isSymbian) = when (mode) {
            AppIconMode.ORIGINAL -> resolvedAndroid to false
            AppIconMode.SYMBIAN -> {
                val resId = override?.symbianKey?.let { SymbianIconMapper.resolveByKey(it) }
                    ?: SymbianIconMapper.resolveSymbianIconRes(app.packageName, app.originalLabel)
                val bmp = resId?.let { resolveSymbianBitmap(it) }
                if (bmp != null) bmp to true else resolvedAndroid to false
            }
            AppIconMode.CUSTOM -> {
                val bmp = override?.customFilePath?.let { resolveCustomBitmap(it) }
                if (bmp != null) bmp to false else resolvedAndroid to false
            }
            AppIconMode.AUTO -> {
                val resId = SymbianIconMapper.resolveSymbianIconRes(app.packageName, app.originalLabel)
                val bmp = resId?.let { resolveSymbianBitmap(it) }
                if (bmp != null) bmp to true else resolvedAndroid to false
            }
        }

        return app.copy(
            label = effectiveLabel,
            initialChar = computeInitialChar(effectiveLabel),
            isPinned = isPinned,
            icon = resolvedIcon,
            isSymbianIcon = isSymbian,
            isRenamed = effectiveLabel != app.originalLabel,
            iconMode = mode,
            androidIcon = resolvedAndroid,
            iconOverride = override
        )
    }

    fun getSymbianBitmap(resId: Int): ImageBitmap? = resolveSymbianBitmap(resId)

    suspend fun importCustomAppIcon(appId: String, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Не удалось прочитать изображение")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sample = (maxSide / 192).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: throw IllegalStateException("Формат изображения не поддерживается")
            val scaled = if (decoded.width > 192 || decoded.height > 192) {
                val ratio = 192f / maxOf(decoded.width, decoded.height)
                val w = (decoded.width * ratio).toInt().coerceAtLeast(32)
                val h = (decoded.height * ratio).toInt().coerceAtLeast(32)
                Bitmap.createScaledBitmap(decoded, w, h, true)
            } else {
                decoded
            }

            val dir = File(context.filesDir, "custom_icons").apply { mkdirs() }
            val fileName = "icon_${abs(appId.hashCode())}_${System.currentTimeMillis()}.png"
            val outFile = File(dir, fileName)
            outFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            customIconCache[outFile.absolutePath] = scaled.asImageBitmap()
            outFile.absolutePath
        }
    }

    suspend fun shareAppAsApk(app: AppEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (destFile, fileName) = prepareExportedApkFile(app)
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            val clipData = ClipData.newUri(context.contentResolver, fileName, apkUri)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, "${app.label} (${app.packageName})")
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Поделиться APK: ${app.label}").apply {
                this.clipData = clipData
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }
    }

    suspend fun saveAppApkToUri(app: AppEntry, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (sourceApk, _) = prepareExportedApkFile(app)
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                sourceApk.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Не удалось записать APK в выбранное место")
            Unit
        }
    }

    fun buildSuggestedApkName(app: AppEntry): String {
        val safeLabel = app.originalLabel.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").ifBlank { app.packageName }.take(40)
        return "$safeLabel.apk"
    }

    private fun prepareExportedApkFile(app: AppEntry): Pair<File, String> {
        val pm = context.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(app.packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(app.packageName, 0)
        }
        val sourcePath = appInfo.publicSourceDir ?: appInfo.sourceDir
            ?: throw IllegalStateException("APK-файл недоступен")
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            throw IllegalStateException("Нет доступа к системному APK: ${app.label}")
        }

        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, 0)
            }
        }.getOrNull()

        val ver = pkgInfo?.versionName?.replace(Regex("""[^\w.-]+"""), "_")?.take(16).orEmpty()
        val safeLabel = app.originalLabel.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").ifBlank { app.packageName }.take(40)
        val fileName = if (ver.isNotBlank()) "${safeLabel}_$ver.apk" else "$safeLabel.apk"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        exportDir.listFiles { f -> f.name.endsWith(".apk") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(4)
            ?.forEach { runCatching { it.delete() } }

        val destFile = File(exportDir, fileName)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile to fileName
    }

    fun launchApp(app: AppEntry): Boolean {
        val launchedViaService = runCatching {
            val component = ComponentName(app.packageName, app.activityName)
            launcherApps?.startMainActivity(component, Process.myUserHandle(), null, null)
            launcherApps != null
        }.getOrDefault(false)
        if (launchedViaService) return true

        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            launchPackage(app.packageName)
        }
    }

    fun launchPackage(packageName: String): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun openAppInfo(packageName: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun uninstallApp(packageName: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchCamera(): Boolean {
        val actions = listOf(
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            MediaStore.ACTION_IMAGE_CAPTURE
        )
        for (action in actions) {
            val started = runCatching {
                val intent = Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return false
    }

    fun openUrl(url: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openSystemSettings(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotificationShade() {
        runCatching {
            val sbService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            statusBarManager.getMethod("expandNotificationsPanel").invoke(sbService)
        }
    }

    private fun classifyHubCategory(
        appInfo: ApplicationInfo?,
        packageName: String,
        label: String,
        musicCapablePackages: Set<String>
    ): HubCategory {
        val lowerPkg = packageName.lowercase()
        val lowerLabel = label.lowercase()

        val chatKeywords = listOf(
            "telegram", "exteragram", "nekogram", "whatsapp", "viber", "signal",
            "discord", "slack", "messenger", "skype", "teams", "vk.im",
            "chat", "messaging", "sms", "mms", "сообщ", "чат"
        )
        if (chatKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) }) {
            return HubCategory.CHAT
        }

        val musicKeywords = listOf(
            "music", "spotify", "yandex.music", "звук", "музык", "soundcloud",
            "deezer", "tidal", "apple.android.music", "aimp", "poweramp", "vlc",
            "foobar", "neutron", "pleer", "плеер", "radio", "podcast", "подкаст", "vk.music"
        )
        if (musicCapablePackages.contains(packageName) ||
            musicKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) } ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo?.category == ApplicationInfo.CATEGORY_AUDIO)
        ) {
            return HubCategory.MUSIC
        }

        val newsKeywords = listOf(
            "news", "новост", "feed", "rss", "reddit", "habr", "dzen", "zen",
            "pikabu", "medium", "flipboard", "chrome", "browser", "firefox",
            "opera", "edge", "adblock", "duckduckgo", "read", "reader", "4pda"
        )
        if (newsKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) } ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo?.category == ApplicationInfo.CATEGORY_NEWS)
        ) {
            return HubCategory.NEWS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo?.category == ApplicationInfo.CATEGORY_SOCIAL) {
            return HubCategory.CHAT
        }

        return HubCategory.UTILITY
    }

    fun resolveCustomBitmap(path: String): ImageBitmap? {
        customIconCache[path]?.let { return it }
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() ?: return null
        customIconCache[path] = bmp
        return bmp
    }

    private fun resolveSymbianBitmap(resId: Int): ImageBitmap? {
        symbianDrawableCache[resId]?.let { return it }
        val drawable = runCatching { ContextCompat.getDrawable(context, resId) }.getOrNull() ?: return null
        val bmp = drawableToImageBitmap(drawable)
        symbianDrawableCache[resId] = bmp
        return bmp
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width <= 128 && bmp.height <= 128) {
                return bmp.asImageBitmap()
            }
            return Bitmap.createScaledBitmap(bmp, 96, 96, true).asImageBitmap()
        }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private fun createFallbackBitmap(): ImageBitmap {
        return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    companion object {
        fun computeInitialChar(label: String): Char {
            val first = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
            return when {
                first == 'Ё' -> 'Е'
                first in 'A'..'Z' -> first
                first in 'А'..'Я' -> first
                else -> '#'
            }
        }
    }
}
