package com.example.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.CustomColorPalette
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.TrackLyricsData
import com.example.model.ZipArchiveKind
import com.example.model.ZipUpdateInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUpdateRepository(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {
    private fun updatesCacheRoot(): File =
        File(context.cacheDir, "exports/updates").apply { mkdirs() }

    private fun cleanPreviousExtractions() {
        val root = updatesCacheRoot()
        root.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    fun getCurrentVersionInfo(): Pair<String, Long> {
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            (info.versionName ?: "1.0") to code
        }.getOrDefault("1.0" to 1L)
    }

    suspend fun inspectZipArchive(
        uri: Uri,
        localFilePath: String? = null,
        displayNameOverride: String? = null
    ): Result<ZipUpdateInspection> = withContext(Dispatchers.IO) {
        runCatching {
            cleanPreviousExtractions()
            val sessionDir = File(updatesCacheRoot(), "session_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            val (archiveName, archiveSize) = resolveArchiveMeta(uri, localFilePath, displayNameOverride)
            val stream: InputStream = when {
                !localFilePath.isNullOrBlank() && File(localFilePath).canRead() -> File(localFilePath).inputStream()
                else -> context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть ZIP-архив для чтения")
            }

            val apkFiles = mutableListOf<File>()
            val palettes = mutableListOf<CustomColorPalette>()
            val quickLinks = mutableListOf<HubQuickLink>()
            val lyricsList = mutableListOf<TrackLyricsData>()
            var wallpaperPath: String? = null
            var hasConfigJson = false
            var changelog = ""
            var manifestVersionName: String? = null
            var gradleAppId: String? = null
            var gradleVerName: String? = null
            var gradleVerCode: Long? = null
            var hasGradleFiles = false
            var hasSigningKeystoreInZip = false
            var sourceCodeCount = 0
            var totalEntries = 0

            ZipInputStream(BufferedInputStream(stream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null && totalEntries < 2500) {
                    val rawName = entry.name.orEmpty()
                    if (!entry.isDirectory && rawName.isNotBlank()) {
                        totalEntries++
                        val normalized = rawName.replace('\\', '/')
                        val baseName = normalized.substringAfterLast('/')
                        val ext = baseName.substringAfterLast('.', "").lowercase(Locale.ROOT)

                        when {
                            ext == "apk" && apkFiles.size < 32 -> {
                                val outFile = safeResolveEntryFile(sessionDir, normalized)
                                    ?: throw SecurityException("Небезопасный путь в архиве: $rawName")
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    zis.copyTo(out, bufferSize = 16 * 1024)
                                }
                                if (outFile.length() > 0L) {
                                    apkFiles.add(outFile)
                                }
                            }

                            baseName.equals("keydeck-update.json", ignoreCase = true) ||
                                baseName.equals("manifest.json", ignoreCase = true) -> {
                                val text = readBoundedUtf8(zis, maxBytes = 2 * 1024 * 1024)
                                val parsed = parseConfigManifestJson(text)
                                if (parsed != null) {
                                    hasConfigJson = true
                                    changelog = parsed.changelog.ifBlank { changelog }
                                    manifestVersionName = parsed.versionName ?: manifestVersionName
                                    palettes.addAll(parsed.palettes)
                                    quickLinks.addAll(parsed.quickLinks)
                                    lyricsList.addAll(parsed.lyrics)
                                }
                            }

                            baseName.endsWith(".palette.json", ignoreCase = true) -> {
                                val text = readBoundedUtf8(zis, maxBytes = 256 * 1024)
                                parseSinglePaletteJson(text)?.let { palettes.add(it) }
                            }

                            ext == "lrc" -> {
                                val lrcRaw = readBoundedUtf8(zis, maxBytes = 512 * 1024)
                                parseLrcArchiveEntry(baseName, lrcRaw)?.let { lyricsList.add(it) }
                            }

                            baseName.substringBeforeLast('.').equals("wallpaper", ignoreCase = true) &&
                                ext in setOf("jpg", "jpeg", "png", "webp") -> {
                                val outDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                                val outFile = File(outDir, "zip_wallpaper_${System.currentTimeMillis()}.$ext")
                                outFile.outputStream().use { out ->
                                    zis.copyTo(out, bufferSize = 16 * 1024)
                                }
                                if (outFile.length() > 0L) {
                                    wallpaperPath = outFile.absolutePath
                                }
                            }

                            baseName.equals("debug.keystore", ignoreCase = true) ||
                                baseName.equals("debug.keystore.base64", ignoreCase = true) -> {
                                hasSigningKeystoreInZip = true
                            }

                            baseName.equals("build.gradle.kts", ignoreCase = true) ||
                                baseName.equals("build.gradle", ignoreCase = true) -> {
                                sourceCodeCount++
                                hasGradleFiles = true
                                val gradleText = readBoundedUtf8(zis, maxBytes = 512 * 1024)
                                val parsed = parseGradleAppMeta(gradleText)
                                if (parsed.first != null) gradleAppId = parsed.first
                                if (parsed.second != null) gradleVerName = parsed.second
                                if (parsed.third != null) gradleVerCode = parsed.third
                            }

                            ext in setOf("kt", "kts", "java") ||
                                baseName.equals("settings.gradle.kts", ignoreCase = true) ||
                                baseName.equals("settings.gradle", ignoreCase = true) ||
                                baseName.equals("AndroidManifest.xml", ignoreCase = true) -> {
                                sourceCodeCount++
                                if (baseName.startsWith("settings.gradle", ignoreCase = true)) {
                                    hasGradleFiles = true
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val (curVerName, curVerCode) = getCurrentVersionInfo()
            val primaryApk = selectPrimaryApk(apkFiles)
            val pkgInfo = primaryApk?.let { readApkArchiveInfo(it.absolutePath) }
            val targetPkg = pkgInfo?.packageName ?: gradleAppId
            val targetVerName = pkgInfo?.versionName ?: manifestVersionName ?: gradleVerName
            val targetVerCode = pkgInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            } ?: gradleVerCode
            val targetLabel = pkgInfo?.applicationInfo?.let { appInfo ->
                runCatching {
                    appInfo.sourceDir = primaryApk.absolutePath
                    appInfo.publicSourceDir = primaryApk.absolutePath
                    context.packageManager.getApplicationLabel(appInfo).toString()
                }.getOrNull()
            }

            val samePackage = targetPkg != null && targetPkg == context.packageName
            val hasConfigItems = palettes.isNotEmpty() || quickLinks.isNotEmpty() || lyricsList.isNotEmpty() || wallpaperPath != null

            val kind = when {
                apkFiles.size > 1 -> ZipArchiveKind.APK_SPLIT_BUNDLE
                apkFiles.size == 1 && samePackage -> ZipArchiveKind.APK_UPDATE
                apkFiles.size == 1 && !samePackage -> ZipArchiveKind.FOREIGN_APK
                hasConfigItems || hasConfigJson -> ZipArchiveKind.KEYDECK_CONFIG_PACK
                sourceCodeCount > 0 -> ZipArchiveKind.SOURCE_CODE_ARCHIVE
                else -> ZipArchiveKind.UNSUPPORTED
            }

            val summary = buildInspectionSummary(
                kind = kind,
                apkCount = apkFiles.size,
                targetPkg = targetPkg,
                targetVerName = targetVerName,
                targetVerCode = targetVerCode,
                curVerName = curVerName,
                curVerCode = curVerCode,
                palettes = palettes.size,
                quickLinks = quickLinks.size,
                lrc = lyricsList.size,
                hasWallpaper = wallpaperPath != null,
                sourceCodeCount = sourceCodeCount,
                hasGradleFiles = hasGradleFiles,
                hasSigningKeystore = hasSigningKeystoreInZip
            )

            ZipUpdateInspection(
                sourceUriString = uri.toString(),
                localFilePath = localFilePath,
                archiveName = archiveName,
                archiveSizeBytes = archiveSize,
                kind = kind,
                isSamePackageUpdate = samePackage,
                targetPackageName = targetPkg,
                targetAppLabel = targetLabel,
                targetVersionName = targetVerName,
                targetVersionCode = targetVerCode,
                currentVersionName = curVerName,
                currentVersionCode = curVerCode,
                extractedApkPaths = apkFiles.map { it.absolutePath },
                hasConfigJson = hasConfigJson,
                changelog = changelog,
                paletteCount = palettes.size,
                quickLinkCount = quickLinks.size,
                lrcCount = lyricsList.size,
                hasWallpaper = wallpaperPath != null,
                sourceCodeFileCount = sourceCodeCount,
                hasGradleFiles = hasGradleFiles,
                hasSigningKeystoreInZip = hasSigningKeystoreInZip,
                totalEntriesCount = totalEntries,
                summaryMessage = summary,
                pendingPalettes = palettes.distinctBy { it.id },
                pendingQuickLinks = quickLinks.distinctBy { it.url },
                pendingLyrics = lyricsList.distinctBy { it.trackKey },
                extractedWallpaperPath = wallpaperPath
            )
        }
    }

    suspend fun inspectLocalApkFile(apkFile: File): ZipUpdateInspection = withContext(Dispatchers.IO) {
        val (curVerName, curVerCode) = getCurrentVersionInfo()
        val pkgInfo = readApkArchiveInfo(apkFile.absolutePath)
        val targetPkg = pkgInfo?.packageName
        val targetVerName = pkgInfo?.versionName
        val targetVerCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else {
                @Suppress("DEPRECATION")
                it.versionCode.toLong()
            }
        }
        val samePackage = targetPkg != null && targetPkg == context.packageName
        val kind = if (samePackage) ZipArchiveKind.APK_UPDATE else ZipArchiveKind.FOREIGN_APK
        ZipUpdateInspection(
            sourceUriString = Uri.fromFile(apkFile).toString(),
            localFilePath = apkFile.absolutePath,
            archiveName = apkFile.name,
            archiveSizeBytes = apkFile.length(),
            kind = kind,
            isSamePackageUpdate = samePackage,
            targetPackageName = targetPkg,
            targetVersionName = targetVerName,
            targetVersionCode = targetVerCode,
            currentVersionName = curVerName,
            currentVersionCode = curVerCode,
            extractedApkPaths = listOf(apkFile.absolutePath),
            summaryMessage = buildInspectionSummary(
                kind = kind,
                apkCount = 1,
                targetPkg = targetPkg,
                targetVerName = targetVerName,
                targetVerCode = targetVerCode,
                curVerName = curVerName,
                curVerCode = curVerCode,
                palettes = 0,
                quickLinks = 0,
                lrc = 0,
                hasWallpaper = false,
                sourceCodeCount = 0,
                hasGradleFiles = false,
                hasSigningKeystore = false
            )
        )
    }

    suspend fun installExtractedApks(
        inspection: ZipUpdateInspection,
        preferShizukuOrRoot: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val files = inspection.extractedApkPaths.map { File(it) }.filter { it.exists() && it.canRead() }
            if (files.isEmpty()) {
                throw IllegalStateException("APK-файлы внутри архива не найдены")
            }

            val shizukuStatus = shizukuBridge.statusState.value
            val canPrivileged = preferShizukuOrRoot &&
                ((shizukuStatus.isBinderAlive && shizukuStatus.isPermissionGranted) || shizukuStatus.isRootAvailable)

            if (canPrivileged) {
                if (files.size == 1) {
                    val quoted = "'" + files.first().absolutePath.replace("'", "'\\''") + "'"
                    val res = shizukuBridge.runPrivilegedOrRootShell("pm install -r -d $quoted")
                    if (res.isSuccess) {
                        return@runCatching "Установка APK запущена через Shizuku/Root (${res.getOrNull()?.trim().orEmpty()})"
                    }
                } else {
                    val totalSize = files.sumOf { it.length() }
                    val createRes = shizukuBridge.runPrivilegedOrRootShell("pm install-create -r -d -S $totalSize")
                    val sessionId = Regex("""\[(\d+)\]""").find(createRes.getOrNull().orEmpty())?.groupValues?.getOrNull(1)
                    if (sessionId != null) {
                        var allWritten = true
                        files.forEachIndexed { idx, file ->
                            val q = "'" + file.absolutePath.replace("'", "'\\''") + "'"
                            val writeRes = shizukuBridge.runPrivilegedOrRootShell(
                                "pm install-write -S ${file.length()} $sessionId split_$idx.apk $q"
                            )
                            if (writeRes.isFailure) allWritten = false
                        }
                        if (allWritten) {
                            val commitRes = shizukuBridge.runPrivilegedOrRootShell("pm install-commit $sessionId")
                            if (commitRes.isSuccess) {
                                return@runCatching "Пакет из ${files.size} APK установлен через Shizuku/Root"
                            }
                        }
                    }
                }
            }

            if (files.size == 1) {
                val apkFile = files.first()
                val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                val opened = launchSystemApkInstaller(apkUri)
                if (!opened) {
                    throw IllegalStateException("Система не нашла установщик пакетов для запуска APK")
                }
                "Открыт системный установщик для ${apkFile.name}"
            } else {
                commitPackageInstallerSession(files)
                "Сессия установки ${files.size} APK отправлена в систему"
            }
        }
    }

    suspend fun applyConfigPack(
        inspection: ZipUpdateInspection,
        prefsRepo: LauncherPreferencesRepository
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var appliedPalettes = 0
            inspection.pendingPalettes.forEachIndexed { idx, pal ->
                prefsRepo.saveCustomPalette(pal, activate = idx == 0)
                appliedPalettes++
            }
            var appliedLinks = 0
            inspection.pendingQuickLinks.forEach { link ->
                prefsRepo.addHubQuickLink(link.title, link.url, link.category)
                appliedLinks++
            }
            var appliedLyrics = 0
            inspection.pendingLyrics.forEach { lyr ->
                prefsRepo.saveLyricsOverride(lyr)
                appliedLyrics++
            }
            val appliedWall = !inspection.extractedWallpaperPath.isNullOrBlank()
            if (appliedWall) {
                prefsRepo.setWallpaperPath(inspection.extractedWallpaperPath)
            }

            val parts = buildList {
                if (appliedPalettes > 0) add("палитр: $appliedPalettes")
                if (appliedLinks > 0) add("источников: $appliedLinks")
                if (appliedLyrics > 0) add("текстов .lrc: $appliedLyrics")
                if (appliedWall) add("обои")
            }
            if (parts.isEmpty()) {
                "В пакете не найдено применяемых настроек или ресурсов"
            } else {
                "Применено без пересборки: ${parts.joinToString(", ")}"
            }
        }
    }

    suspend fun exportConfigBackupZip(
        destUri: Uri,
        palettes: List<CustomColorPalette>,
        quickLinks: List<HubQuickLink>,
        lyricsMap: Map<String, TrackLyricsData>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val (curVerName, curVerCode) = getCurrentVersionInfo()
            val outStream = context.contentResolver.openOutputStream(destUri)
                ?: throw IllegalStateException("Не удалось открыть файл для сохранения ZIP")

            val manifestJson = buildBackupManifestJson(
                versionName = curVerName,
                versionCode = curVerCode,
                palettes = palettes,
                quickLinks = quickLinks
            )

            ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                zos.putNextEntry(ZipEntry("keydeck-update.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                lyricsMap.values.filter { it.hasAnyLyrics && !it.isDeleted }.forEach { lyr ->
                    val base = listOf(lyr.artist, lyr.title)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                        .ifBlank { lyr.trackKey }
                        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
                        .take(80)
                    val content = lyr.rawLrc.ifBlank { lyr.plainText }
                    if (content.isNotBlank()) {
                        zos.putNextEntry(ZipEntry("lyrics/$base.lrc"))
                        zos.write(content.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
            }
            "Экспортирован ZIP-пакет KeyDeck (палитр: ${palettes.size}, ссылок: ${quickLinks.size}, текстов: ${lyricsMap.size})"
        }
    }

    private fun launchSystemApkInstaller(apkUri: Uri): Boolean {
        val apkMime = "application/vnd.android.package-archive"
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, apkMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    baseIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            res.forEach {
                val pkg = it.activityInfo?.packageName
                if (!pkg.isNullOrBlank()) {
                    context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        return runCatching {
            val chooser = Intent.createChooser(baseIntent, "Установка обновления KeyDeck").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        }.recoverCatching {
            context.startActivity(baseIntent)
            true
        }.getOrDefault(false)
    }

    private fun commitPackageInstallerSession(apkFiles: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apkFiles.forEachIndexed { idx, file ->
                session.openWrite("apk_$idx.apk", 0L, file.length()).use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 16 * 1024)
                    }
                    session.fsync(out)
                }
            }
            val callbackIntent = Intent("com.example.action.ZIP_APK_INSTALL_RESULT").apply {
                setPackage(context.packageName)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
            session.commit(pending.intentSender)
        } finally {
            runCatching { session.close() }
        }
    }

    private fun readApkArchiveInfo(apkPath: String): PackageInfo? {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, 0)
            }
        }.getOrNull()
    }

    private fun selectPrimaryApk(apks: List<File>): File? {
        if (apks.isEmpty()) return null
        return apks.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
            ?: apks.firstOrNull { !it.name.startsWith("split_", ignoreCase = true) && !it.name.contains("config.", ignoreCase = true) }
            ?: apks.first()
    }

    private fun resolveArchiveMeta(uri: Uri, localFilePath: String?, overrideName: String?): Pair<String, Long> {
        if (!localFilePath.isNullOrBlank()) {
            val f = File(localFilePath)
            if (f.exists()) {
                return (overrideName?.takeIf { it.isNotBlank() } ?: f.name) to f.length()
            }
        }
        var name = overrideName?.takeIf { it.isNotBlank() }
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && name.isNullOrBlank()) name = c.getString(nameIdx)
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                }
            }
        }
        val fallbackName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "update.zip"
        return fallbackName to size
    }

    internal companion object {
        internal data class ParsedManifest(
            val versionName: String? = null,
            val changelog: String = "",
            val palettes: List<CustomColorPalette> = emptyList(),
            val quickLinks: List<HubQuickLink> = emptyList(),
            val lyrics: List<TrackLyricsData> = emptyList()
        )

        internal fun safeResolveEntryFile(rootDir: File, entryName: String): File? {
            val clean = entryName.trimStart('/', '\\')
            if (clean.isBlank()) return null
            val candidate = File(rootDir, clean)
            val rootCanonical = rootDir.canonicalPath
            val candCanonical = candidate.canonicalPath
            return if (candCanonical == rootCanonical || candCanonical.startsWith(rootCanonical + File.separator)) {
                candidate
            } else {
                null
            }
        }

        private fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String {
            val buf = ByteArray(4096)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (total < maxBytes) {
                val n = stream.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toString("UTF-8")
        }

        internal fun parseConfigManifestJson(rawJson: String): ParsedManifest? {
            return runCatching {
                val obj = JSONObject(rawJson)
                val versionName = obj.optString("versionName", "").ifBlank { null }
                val changelog = obj.optString("changelog", "").trim()
                val palettes = mutableListOf<CustomColorPalette>()
                val pArr = obj.optJSONArray("palettes")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val p = pArr.optJSONObject(i) ?: continue
                        parsePaletteJsonObj(p)?.let { palettes.add(it) }
                    }
                }
                val links = mutableListOf<HubQuickLink>()
                val lArr = obj.optJSONArray("quickLinks")
                if (lArr != null) {
                    for (i in 0 until lArr.length()) {
                        val l = lArr.optJSONObject(i) ?: continue
                        val title = l.optString("title", "").trim()
                        val url = l.optString("url", "").trim()
                        val cat = runCatching {
                            HubSubTab.valueOf(l.optString("category", "NEWS"))
                        }.getOrDefault(HubSubTab.NEWS)
                        if (title.isNotBlank() && url.isNotBlank()) {
                            links.add(
                                HubQuickLink(
                                    id = l.optString("id").ifBlank { "zip_${System.currentTimeMillis()}_$i" },
                                    title = title,
                                    url = url,
                                    category = cat
                                )
                            )
                        }
                    }
                }
                val lyrics = mutableListOf<TrackLyricsData>()
                val lyrArr = obj.optJSONArray("lyrics")
                if (lyrArr != null) {
                    for (i in 0 until lyrArr.length()) {
                        val o = lyrArr.optJSONObject(i) ?: continue
                        val title = o.optString("title", "").trim()
                        val artist = o.optString("artist", "").trim()
                        val rawLrc = o.optString("rawLrc", "").trim()
                        val plain = o.optString("plainText", "").trim()
                        val body = rawLrc.ifBlank { plain }
                        if (title.isNotBlank() && body.isNotBlank()) {
                            val synced = MusicCatalogAndLyricsRepository.parseLrc(body)
                            val key = MusicCatalogAndLyricsRepository.buildTrackKey(title, artist)
                            lyrics.add(
                                TrackLyricsData(
                                    trackKey = key,
                                    title = title,
                                    artist = artist,
                                    syncedLines = synced,
                                    plainText = if (synced.isNotEmpty()) synced.joinToString("\n") { it.text } else body,
                                    rawLrc = body,
                                    sourceLabel = "ZIP Пакет",
                                    isCustomEdited = true
                                )
                            )
                        }
                    }
                }
                ParsedManifest(
                    versionName = versionName,
                    changelog = changelog,
                    palettes = palettes,
                    quickLinks = links,
                    lyrics = lyrics
                )
            }.getOrNull()
        }

        internal fun parseSinglePaletteJson(rawJson: String): CustomColorPalette? {
            return runCatching { parsePaletteJsonObj(JSONObject(rawJson)) }.getOrNull()
        }

        private fun parsePaletteJsonObj(o: JSONObject): CustomColorPalette? {
            val name = o.optString("name", "").trim()
            if (name.isBlank()) return null
            val id = o.optString("id").ifBlank { "zip_pal_${name.hashCode()}" }
            val dark = o.optLong("dominantDarkArgb", o.optLong("dark", 0xFF101926L))
            val accent = o.optLong("accentArgb", o.optLong("accent", 0xFF42A5F5L))
            val highlight = o.optLong("highlightArgb", o.optLong("highlight", 0xFF81D4FAL))
            val fromWall = o.optBoolean("isFromWallpaper", o.optBoolean("wall", false))
            return CustomColorPalette(
                id = id,
                name = name,
                dominantDarkArgb = dark,
                accentArgb = accent,
                highlightArgb = highlight,
                isFromWallpaper = fromWall
            )
        }

        internal fun parseLrcArchiveEntry(fileName: String, rawLrc: String): TrackLyricsData? {
            val cleanBody = rawLrc.trim()
            if (cleanBody.isBlank()) return null
            val base = fileName.substringBeforeLast('.').trim()
            val (artist, title) = if (base.contains(" - ")) {
                base.substringBefore(" - ").trim() to base.substringAfter(" - ").trim()
            } else {
                "" to base
            }
            if (title.isBlank()) return null
            val synced = MusicCatalogAndLyricsRepository.parseLrc(cleanBody)
            return TrackLyricsData(
                trackKey = MusicCatalogAndLyricsRepository.buildTrackKey(title, artist),
                title = title,
                artist = artist,
                syncedLines = synced,
                plainText = if (synced.isNotEmpty()) synced.joinToString("\n") { it.text } else cleanBody,
                rawLrc = cleanBody,
                sourceLabel = "ZIP (.lrc)",
                isCustomEdited = true
            )
        }

        internal fun buildBackupManifestJson(
            versionName: String,
            versionCode: Long,
            palettes: List<CustomColorPalette>,
            quickLinks: List<HubQuickLink>
        ): String {
            val root = JSONObject().apply {
                put("format", "keydeck-update-v1")
                put("versionName", versionName)
                put("versionCode", versionCode)
                put("changelog", "Резервная копия настроек и тем KeyDeck")
                put(
                    "palettes",
                    JSONArray().apply {
                        palettes.forEach { p ->
                            put(
                                JSONObject().apply {
                                    put("id", p.id)
                                    put("name", p.name)
                                    put("dominantDarkArgb", p.dominantDarkArgb)
                                    put("accentArgb", p.accentArgb)
                                    put("highlightArgb", p.highlightArgb)
                                    put("isFromWallpaper", p.isFromWallpaper)
                                }
                            )
                        }
                    }
                )
                put(
                    "quickLinks",
                    JSONArray().apply {
                        quickLinks.forEach { l ->
                            put(
                                JSONObject().apply {
                                    put("id", l.id)
                                    put("title", l.title)
                                    put("url", l.url)
                                    put("category", l.category.name)
                                }
                            )
                        }
                    }
                )
            }
            return root.toString(2)
        }

        private fun selectPreferredApkInstaller(candidates: List<String>): String? {
            val blocked = listOf("termux", "editor", "browser", "chrome", "firefox", "opera", "viewer")
            val filtered = candidates.filter { pkg ->
                val low = pkg.lowercase(Locale.ROOT)
                blocked.none { low.contains(it) }
            }
            val preferredOrder = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.miui.packageinstaller",
                "com.samsung.android.packageinstaller",
                "com.oplus.appdetail",
                "com.bbk.appstore"
            )
            preferredOrder.forEach { pref ->
                filtered.firstOrNull { it.equals(pref, ignoreCase = true) }?.let { return it }
            }
            return filtered.firstOrNull { it.contains("packageinstaller", ignoreCase = true) }
                ?: filtered.firstOrNull()
        }

        internal fun parseGradleAppMeta(gradleText: String): Triple<String?, String?, Long?> {
            val appId = Regex("""applicationId\s*=?\s*["']([^"']+)["']""")
                .find(gradleText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            val verName = Regex("""versionName\s*=?\s*["']([^"']+)["']""")
                .find(gradleText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            val verCode = Regex("""versionCode\s*=?\s*(\d+)""")
                .find(gradleText)?.groupValues?.getOrNull(1)?.toLongOrNull()
            return Triple(appId, verName, verCode)
        }

        private fun buildInspectionSummary(
            kind: ZipArchiveKind,
            apkCount: Int,
            targetPkg: String?,
            targetVerName: String?,
            targetVerCode: Long?,
            curVerName: String,
            curVerCode: Long,
            palettes: Int,
            quickLinks: Int,
            lrc: Int,
            hasWallpaper: Boolean,
            sourceCodeCount: Int,
            hasGradleFiles: Boolean,
            hasSigningKeystore: Boolean
        ): String = when (kind) {
            ZipArchiveKind.APK_UPDATE -> {
                "Найден APK обновления KeyDeck (${targetVerName ?: "?"} #${targetVerCode ?: "?"}). Текущая: $curVerName #$curVerCode."
            }
            ZipArchiveKind.APK_SPLIT_BUNDLE -> {
                "Найден пакет из $apkCount APK (${targetPkg ?: "split-bundle"}, v${targetVerName ?: "?"})."
            }
            ZipArchiveKind.FOREIGN_APK -> {
                "В архиве найден APK другого пакета (${targetPkg ?: "неизвестно"}), можно установить через системный установщик."
            }
            ZipArchiveKind.KEYDECK_CONFIG_PACK -> {
                val items = buildList {
                    if (palettes > 0) add("палитр: $palettes")
                    if (quickLinks > 0) add("источников: $quickLinks")
                    if (lrc > 0) add("текстов .lrc: $lrc")
                    if (hasWallpaper) add("обои")
                }
                if (items.isEmpty()) "Пакет конфигурации KeyDeck готов к применению."
                else "Готово к применению без пересборки: ${items.joinToString(", ")}."
            }
            ZipArchiveKind.SOURCE_CODE_ARCHIVE -> {
                val meta = buildList {
                    targetPkg?.let { add(it) }
                    if (targetVerName != null || targetVerCode != null) {
                        add("v${targetVerName ?: "?"} #${targetVerCode ?: "?"}")
                    }
                    if (hasGradleFiles) add("Gradle")
                    if (hasSigningKeystore) add("ключ подписи найден")
                }.joinToString(" • ")
                val metaPrefix = if (meta.isNotBlank()) " [$meta]" else ""
                "Архив исходников$metaPrefix ($sourceCodeCount файлов кода). Android не компилирует .kt локально внутри приложения, но KeyDeck может сам отправить этот ZIP в GitHub Actions и сразу скачать/установить собранный APK без Android Studio."
            }
            ZipArchiveKind.UNSUPPORTED -> {
                "В архиве не найдено .apk, keydeck-update.json, .palette.json или .lrc файлов."
            }
        }
    }
}
