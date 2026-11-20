package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.example.model.LinuxPackageEntry
import com.example.model.LinuxPackageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Discovers, inspects (.deb AR archive / script headers), installs, and runs Linux packages
 * (.deb, .AppImage, .sh) via Termux (RUN_COMMAND / launcher) or Shizuku / proot-distro / chroot.
 */
class LinuxPackagesRepository(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {

    fun isTermuxInstalled(): Boolean {
        val pkgs = listOf("com.termux", "com.termux.x11")
        return pkgs.any { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }
    }

    suspend fun scanLinuxPackages(query: String = ""): List<LinuxPackageEntry> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, LinuxPackageEntry>()
        val installedNames = queryInstalledTermuxOrDebPackages()

        val roots = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { sd ->
            roots.add(File(sd, "Download"))
            roots.add(File(sd, "Downloads"))
            roots.add(File(sd, "Linux"))
            roots.add(File(sd, "Packages"))
            roots.add(File(sd, "Documents"))
        }
        roots.add(File(context.filesDir, "linux_packages"))

        val visitedDirs = HashSet<String>()
        val queue = ArrayDeque<File>(roots.filter { it.exists() && it.isDirectory })
        var dirsChecked = 0

        while (queue.isNotEmpty() && dirsChecked < 80 && found.size < 60) {
            val dir = queue.removeFirst()
            if (!visitedDirs.add(dir.absolutePath)) continue
            dirsChecked++

            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(".")) return@forEach
                if (file.isDirectory) {
                    queue.add(file)
                } else if (file.isFile) {
                    val lower = file.name.lowercase()
                    val kind = when {
                        lower.endsWith(".deb") -> LinuxPackageKind.DEB
                        lower.endsWith(".appimage") -> LinuxPackageKind.APPIMAGE
                        lower.endsWith(".sh") -> LinuxPackageKind.SHELL
                        else -> null
                    } ?: return@forEach

                    val entry = parsePackageFile(file, kind, installedNames)
                    if (query.isBlank() ||
                        KeyboardSearchMatcher.matches(entry.name, query) ||
                        KeyboardSearchMatcher.matches(entry.packageName, query) ||
                        KeyboardSearchMatcher.matches(file.name, query) ||
                        query.trim().lowercase().let { it == "*deb" || it == "*.deb" || it == ".deb" } && kind == LinuxPackageKind.DEB
                    ) {
                        found.putIfAbsent(file.absolutePath, entry)
                    }
                }
            }
        }

        // Also surface installed proot/dpkg packages when catalog is browsed
        if (query.isBlank()) {
            installedNames.take(15).forEach { pkgName ->
                if (found.values.none { it.packageName.equals(pkgName, ignoreCase = true) }) {
                    val key = "installed:$pkgName"
                    found[key] = LinuxPackageEntry(
                        id = key,
                        filePath = "",
                        name = pkgName,
                        packageName = pkgName,
                        version = "установлен",
                        architecture = "arm64",
                        description = "Установленный пакет Linux / Termux",
                        sizeBytes = 0L,
                        kind = LinuxPackageKind.DEB,
                        isInstalled = true
                    )
                }
            }
        }

        found.values.sortedWith(
            compareByDescending<LinuxPackageEntry> { it.isInstalled }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    internal fun parsePackageFile(
        file: File,
        kind: LinuxPackageKind,
        installedSet: Set<String> = emptySet()
    ): LinuxPackageEntry {
        val fallbackName = file.nameWithoutExtension
        var pkgName = fallbackName.substringBefore("_").ifBlank { fallbackName }
        var version = fallbackName.substringAfter("_", "").substringBefore("_").ifBlank { "1.0" }
        var arch = when {
            file.name.contains("arm64", true) || file.name.contains("aarch64", true) -> "arm64"
            file.name.contains("amd64", true) || file.name.contains("x86_64", true) -> "amd64"
            else -> "all"
        }
        var desc = when (kind) {
            LinuxPackageKind.DEB -> "Пакет Debian (.deb) • запуск через Termux / proot"
            LinuxPackageKind.APPIMAGE -> "Портативный образ Linux AppImage"
            LinuxPackageKind.SHELL -> "Скрипт Linux Shell (.sh)"
        }

        if (kind == LinuxPackageKind.DEB) {
            val debMeta = inspectDebArchiveHeader(file)
            if (debMeta != null) {
                pkgName = debMeta.packageName.ifBlank { pkgName }
                version = debMeta.version.ifBlank { version }
                arch = debMeta.arch.ifBlank { arch }
                if (debMeta.description.isNotBlank()) desc = debMeta.description
            }
        }

        return LinuxPackageEntry(
            id = "linux:${file.absolutePath}",
            filePath = file.absolutePath,
            name = pkgName,
            packageName = pkgName,
            version = version,
            architecture = arch,
            description = desc,
            sizeBytes = runCatching { file.length() }.getOrDefault(0L),
            kind = kind,
            isInstalled = installedSet.contains(pkgName.lowercase())
        )
    }

    internal data class DebHeaderInfo(
        val validArArchive: Boolean,
        val packageName: String = "",
        val version: String = "",
        val arch: String = "",
        val description: String = ""
    )

    internal fun inspectDebArchiveHeader(file: File): DebHeaderInfo? {
        if (!file.exists() || !file.canRead() || file.length() < 16) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(8)
                raf.readFully(magic)
                val magicStr = String(magic, Charsets.US_ASCII)
                val isAr = magicStr == "!<arch>\n"
                if (!isAr) return@runCatching null

                // Read first 8KB scanning for textual control fields if uncompressed or simple metadata
                val sampleSize = minOf(file.length().toInt(), 8192)
                val buf = ByteArray(sampleSize)
                raf.seek(0)
                raf.readFully(buf)
                val ascii = String(buf, Charsets.ISO_8859_1)

                fun extractField(key: String): String {
                    val m = Regex("""(?m)^$key:\s*([^\r\n]+)$""").find(ascii)
                    return m?.groupValues?.getOrNull(1)?.trim().orEmpty()
                }
                DebHeaderInfo(
                    validArArchive = true,
                    packageName = extractField("Package"),
                    version = extractField("Version"),
                    arch = extractField("Architecture"),
                    description = extractField("Description")
                )
            }
        }.getOrNull()
    }

    private suspend fun queryInstalledTermuxOrDebPackages(): Set<String> {
        val shizuku = shizukuBridge.statusState.value
        val priv = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable
        if (!priv) return emptySet()
        val cmd = "ls /data/data/com.termux/files/usr/var/lib/dpkg/info/*.list 2>/dev/null | xargs -n1 basename 2>/dev/null | sed 's/\\.list$//' | head -n 40"
        val res = shizukuBridge.runPrivilegedOrRootShell(cmd).getOrNull().orEmpty()
        return res.lineSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && !it.startsWith("lib") }
            .toSet()
    }

    suspend fun installOrRunPackage(entry: LinuxPackageEntry): Result<String> = withContext(Dispatchers.IO) {
        val command = when {
            entry.isInstalled && entry.filePath.isBlank() -> entry.packageName
            entry.kind == LinuxPackageKind.DEB -> {
                val safePath = entry.filePath.replace("\"", "\\\"")
                "proot-distro login ubuntu -- bash -lc 'dpkg -i \"$safePath\" || apt-get install -f -y' || dpkg -i \"$safePath\""
            }
            entry.kind == LinuxPackageKind.APPIMAGE -> {
                val safePath = entry.filePath.replace("\"", "\\\"")
                "chmod +x \"$safePath\" && \"$safePath\""
            }
            else -> {
                val safePath = entry.filePath.replace("\"", "\\\"")
                "chmod +x \"$safePath\" && sh \"$safePath\""
            }
        }

        // 1. Try Termux RUN_COMMAND intent if Termux is installed
        if (isTermuxInstalled()) {
            val termuxIntent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            val startedSvc = runCatching {
                context.startService(termuxIntent)
                true
            }.getOrDefault(false)
            if (startedSvc) {
                return@withContext Result.success("Отправлено в Termux: ${entry.name}")
            }
            val launchTermux = context.packageManager.getLaunchIntentForPackage("com.termux")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchTermux != null) {
                runCatching { context.startActivity(launchTermux) }
                return@withContext Result.success("Открыт Termux для установки ${entry.name}")
            }
        }

        // 2. Try Shizuku / Root privileged shell execution (works on proot/chroot environments)
        val shizuku = shizukuBridge.statusState.value
        val priv = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable
        if (priv && entry.filePath.isNotBlank()) {
            val shRes = shizukuBridge.runPrivilegedOrRootShell(command)
            if (shRes.isSuccess) {
                return@withContext Result.success("Выполнено через Shizuku: ${entry.name}")
            }
        }

        // 3. Fallback: open Termux install page / notify user cleanly
        Result.failure(
            IllegalStateException("Установите Termux или подключите Shizuku для запуска ${entry.kind.title}")
        )
    }

    fun openTermuxDownloadPage(): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
