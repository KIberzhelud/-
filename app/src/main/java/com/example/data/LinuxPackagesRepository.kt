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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class LinuxInstallOutcome(
    val tileId: String,
    val name: String,
    val packageName: String,
    val kind: LinuxPackageKind,
    val installedDirPath: String,
    val entryCommand: String,
    val log: String
)

/**
 * Discovers, inspects (.deb AR archive / script headers), installs locally inside KeyDeck,
 * and executes Linux packages (.deb, .AppImage, .sh) using an embedded shell runner without requiring Termux.
 */
class LinuxPackagesRepository(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {

    fun isTermuxInstalled(): Boolean = false

    suspend fun scanLinuxPackages(query: String = ""): List<LinuxPackageEntry> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, LinuxPackageEntry>()

        val roots = mutableListOf<File>()
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { sd ->
            roots.add(File(sd, "Download"))
            roots.add(File(sd, "Downloads"))
            roots.add(File(sd, "Linux"))
            roots.add(File(sd, "Packages"))
            roots.add(File(sd, "Documents"))
        }
        roots.add(File(context.filesDir, "linux_packages"))

        val localInstalledDir = File(context.filesDir, "linux_apps")
        val installedSlugs = localInstalledDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.lowercase() }
            ?.toSet()
            ?: emptySet()

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

                    val entry = parsePackageFile(file, kind, installedSlugs)
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
            LinuxPackageKind.DEB -> "Пакет Debian (.deb) • локальная установка в KeyDeck"
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

        val slug = sanitizeSlug(pkgName)
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
            isInstalled = installedSet.contains(slug) || installedSet.contains(pkgName.lowercase())
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
                if (String(magic, Charsets.US_ASCII) != "!<arch>\n") return@runCatching null

                val sampleSize = minOf(file.length().toInt(), 16384)
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

    internal fun parseArMemberHeaders(file: File): List<Pair<String, Long>> {
        if (!file.exists() || !file.canRead() || file.length() < 68) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(8)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "!<arch>\n") return@runCatching emptyList()

                val members = mutableListOf<Pair<String, Long>>()
                val header = ByteArray(60)
                val fileLen = file.length()
                while (raf.filePointer + 60 <= fileLen) {
                    raf.readFully(header)
                    val endMagic = String(header, 58, 2, Charsets.US_ASCII)
                    if (endMagic != "`\n") break
                    val rawName = String(header, 0, 16, Charsets.US_ASCII).trim().trimEnd('/')
                    val size = String(header, 48, 10, Charsets.US_ASCII).trim().toLongOrNull() ?: break
                    if (size < 0L || raf.filePointer + size > fileLen) break
                    members.add(rawName to size)
                    val nextPos = raf.filePointer + size + (size % 2L)
                    if (nextPos <= raf.filePointer) break
                    raf.seek(nextPos)
                }
                members
            }
        }.getOrDefault(emptyList())
    }

    internal fun extractArMembers(file: File, destDir: File): List<String> {
        if (!file.exists() || !file.canRead() || file.length() < 68) return emptyList()
        destDir.mkdirs()
        return runCatching {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(8)
                if (fis.read(magic) != 8 || String(magic, Charsets.US_ASCII) != "!<arch>\n") {
                    return@runCatching emptyList()
                }
                val extracted = mutableListOf<String>()
                val header = ByteArray(60)
                val buf = ByteArray(8192)
                while (true) {
                    val hRead = readFully(fis, header)
                    if (hRead < 60) break
                    if (String(header, 58, 2, Charsets.US_ASCII) != "`\n") break
                    val rawName = String(header, 0, 16, Charsets.US_ASCII).trim().trimEnd('/')
                    val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "member_${extracted.size}" }
                    val size = String(header, 48, 10, Charsets.US_ASCII).trim().toLongOrNull() ?: break
                    if (size < 0L) break

                    val outFile = File(destDir, safeName)
                    var remaining = size
                    FileOutputStream(outFile).use { fos ->
                        while (remaining > 0L) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = fis.read(buf, 0, toRead)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    extracted.add(safeName)
                    if (size % 2L == 1L) {
                        fis.read()
                    }
                }
                extracted
            }
        }.getOrDefault(emptyList())
    }

    private fun readFully(stream: FileInputStream, dst: ByteArray): Int {
        var offset = 0
        while (offset < dst.size) {
            val n = stream.read(dst, offset, dst.size - offset)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    suspend fun installLocally(entry: LinuxPackageEntry): Result<LinuxInstallOutcome> = withContext(Dispatchers.IO) {
        val slug = sanitizeSlug(entry.packageName.ifBlank { entry.name })
        val tileId = "linux_$slug"
        val installDir = File(context.filesDir, "linux_apps/$slug").apply { mkdirs() }
        val membersDir = File(installDir, "members").apply { mkdirs() }
        val log = StringBuilder()

        val sourceFile = entry.filePath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (sourceFile == null || !sourceFile.exists()) {
            // Virtual/already-registered CLI command
            val cmd = entry.entryCommand.ifBlank { entry.packageName }
            log.appendLine("[KeyDeck Linux] Команда готова: $cmd")
            return@withContext Result.success(
                LinuxInstallOutcome(
                    tileId = tileId,
                    name = entry.name,
                    packageName = entry.packageName,
                    kind = entry.kind,
                    installedDirPath = installDir.absolutePath,
                    entryCommand = cmd,
                    log = log.toString()
                )
            )
        }

        when (entry.kind) {
            LinuxPackageKind.DEB -> {
                val headers = parseArMemberHeaders(sourceFile)
                if (headers.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Некорректный .deb AR-архив: ${sourceFile.name}"))
                }
                val extracted = extractArMembers(sourceFile, membersDir)
                log.appendLine("[KeyDeck DEB] Распакован AR-архив: ${sourceFile.name}")
                log.appendLine("Компоненты: ${extracted.joinToString(", ")}")
                val debInfo = inspectDebArchiveHeader(sourceFile)
                if (debInfo != null && debInfo.packageName.isNotBlank()) {
                    log.appendLine("Package: ${debInfo.packageName} (${debInfo.version} / ${debInfo.arch})")
                }
                // Unpack data.tar.* locally into rootfs via toybox/tar with gzip/xz/bzip2 fallback (no Termux)
                val dataMember = extracted.firstOrNull { it.startsWith("data.tar") }
                val rootfsDir = File(installDir, "rootfs").apply { mkdirs() }
                if (dataMember != null) {
                    val dataFile = File(membersDir, dataMember)
                    val tarFlags = when {
                        dataMember.endsWith(".gz") -> "-xzf"
                        dataMember.endsWith(".xz") -> "-xJf"
                        dataMember.endsWith(".bz2") -> "-xjf"
                        else -> "-xf"
                    }
                    val unpackRes = executeLocalShellCommand(
                        command = "(toybox tar $tarFlags \"${dataFile.absolutePath}\" -C \"${rootfsDir.absolutePath}\" 2>&1 || tar $tarFlags \"${dataFile.absolutePath}\" -C \"${rootfsDir.absolutePath}\" 2>&1 || tar -xf \"${dataFile.absolutePath}\" -C \"${rootfsDir.absolutePath}\" 2>&1 || ls -lh \"${membersDir.absolutePath}\")",
                        workingDir = installDir
                    )
                    unpackRes.getOrNull()?.let { if (it.isNotBlank()) log.appendLine(it.trim()) }
                }
                val discoveredBin = File(rootfsDir, "usr/bin").listFiles()?.firstOrNull()?.absolutePath
                    ?: File(rootfsDir, "bin").listFiles()?.firstOrNull()?.absolutePath
                val wrapper = File(installDir, "run.sh")
                wrapper.writeText(
                    """
                    |#!/system/bin/sh
                    |echo "KeyDeck Linux Package: ${entry.packageName} (${entry.version})"
                    |echo "Root dir: ${rootfsDir.absolutePath}"
                    |export PATH="${rootfsDir.absolutePath}/usr/bin:${rootfsDir.absolutePath}/bin:${'$'}PATH"
                    |export LD_LIBRARY_PATH="${rootfsDir.absolutePath}/usr/lib:${rootfsDir.absolutePath}/lib:${'$'}LD_LIBRARY_PATH"
                    |${if (!discoveredBin.isNullOrBlank()) "chmod +x \"$discoveredBin\" 2>/dev/null; \"$discoveredBin\" --version 2>&1 || \"$discoveredBin\" --help 2>&1 || ls -la \"${rootfsDir.absolutePath}\"" else "find \"${rootfsDir.absolutePath}\" -maxdepth 4 2>/dev/null | head -n 25"}
                    """.trimMargin() + "\n"
                )
                wrapper.setExecutable(true, false)
                val cmd = "sh \"${wrapper.absolutePath}\""
                Result.success(
                    LinuxInstallOutcome(
                        tileId = tileId,
                        name = entry.name,
                        packageName = entry.packageName,
                        kind = entry.kind,
                        installedDirPath = installDir.absolutePath,
                        entryCommand = cmd,
                        log = log.toString().trim()
                    )
                )
            }

            LinuxPackageKind.SHELL -> {
                val targetScript = File(installDir, sourceFile.name)
                sourceFile.copyTo(targetScript, overwrite = true)
                targetScript.setExecutable(true, false)
                val cmd = "sh \"${targetScript.absolutePath}\""
                log.appendLine("[KeyDeck SH] Скрипт установлен локально: ${targetScript.name}")
                val execOut = executeLocalShellCommand(cmd, installDir).getOrElse { it.message.orEmpty() }
                if (execOut.isNotBlank()) log.appendLine(execOut.trim())
                Result.success(
                    LinuxInstallOutcome(
                        tileId = tileId,
                        name = entry.name,
                        packageName = entry.packageName,
                        kind = entry.kind,
                        installedDirPath = installDir.absolutePath,
                        entryCommand = cmd,
                        log = log.toString().trim()
                    )
                )
            }

            LinuxPackageKind.APPIMAGE -> {
                val targetImg = File(installDir, sourceFile.name)
                sourceFile.copyTo(targetImg, overwrite = true)
                targetImg.setExecutable(true, false)
                val wrapper = File(installDir, "run.sh")
                wrapper.writeText(
                    """
                    |#!/system/bin/sh
                    |echo "KeyDeck AppImage: ${entry.name}"
                    |file "${targetImg.absolutePath}" 2>/dev/null || ls -lh "${targetImg.absolutePath}"
                    |"${targetImg.absolutePath}" --appimage-version 2>&1 || true
                    """.trimMargin() + "\n"
                )
                wrapper.setExecutable(true, false)
                val cmd = "sh \"${wrapper.absolutePath}\""
                log.appendLine("[KeyDeck AppImage] Установлен локально: ${targetImg.name}")
                val execOut = executeLocalShellCommand(cmd, installDir).getOrElse { it.message.orEmpty() }
                if (execOut.isNotBlank()) log.appendLine(execOut.trim())
                Result.success(
                    LinuxInstallOutcome(
                        tileId = tileId,
                        name = entry.name,
                        packageName = entry.packageName,
                        kind = entry.kind,
                        installedDirPath = installDir.absolutePath,
                        entryCommand = cmd,
                        log = log.toString().trim()
                    )
                )
            }
        }
    }

    suspend fun executeLocalShellCommand(
        command: String,
        workingDir: File? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        if (trimmed.startsWith("shizuku:")) {
            return@withContext shizukuBridge.runPrivilegedOrRootShell(trimmed.removePrefix("shizuku:").trim())
        }

        val work = (workingDir?.takeIf { it.exists() && it.isDirectory }
            ?: File(context.filesDir, "linux_apps").apply { mkdirs() })

        runCatching {
            val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed).apply {
                directory(work)
                redirectErrorStream(true)
                val env = environment()
                env["HOME"] = work.absolutePath
                env["PREFIX"] = work.absolutePath
                env["TMPDIR"] = context.cacheDir.absolutePath
            }
            val proc = pb.start()
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            val output = proc.inputStream.bufferedReader().readText()
            if (!finished) {
                proc.destroyForcibly()
                "$output\n[Таймаут выполнения 10 сек]"
            } else {
                val exit = proc.exitValue()
                if (exit == 0) output.ifBlank { "(exit 0)" } else "$output\n[exit $exit]"
            }
        }.recoverCatching { err ->
            val shizuku = shizukuBridge.statusState.value
            if ((shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable) {
                shizukuBridge.runPrivilegedOrRootShell(trimmed).getOrThrow()
            } else {
                throw err
            }
        }
    }

    suspend fun installOrRunPackage(entry: LinuxPackageEntry): Result<String> {
        return installLocally(entry).map { "Установлено локально: ${it.name}" }
    }

    fun deleteLocalPackageFiles(installedDirPath: String) {
        if (installedDirPath.isBlank()) return
        runCatching {
            val dir = File(installedDirPath)
            val root = File(context.filesDir, "linux_apps").canonicalFile
            if (dir.exists() && dir.canonicalPath.startsWith(root.canonicalPath)) {
                dir.deleteRecursively()
            }
        }
    }

    fun openTermuxDownloadPage(): Boolean = false

    private fun sanitizeSlug(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "pkg_${System.currentTimeMillis() % 10000}" }
    }
}
