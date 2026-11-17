package com.example.data

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.model.DeviceSearchFile
import com.example.model.SearchResultKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UniversalSearchRepository(
    private val context: Context,
    private val shizukuBridge: ShizukuBridge
) {

    suspend fun searchDevice(
        query: String,
        treeUris: Set<String>
    ): List<DeviceSearchFile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = LinkedHashMap<String, DeviceSearchFile>()

        // 1. KeyDeck local exported/workspace files
        searchLocalDirs(trimmed, results)

        // 2. Full /sdcard filesystem scan when All Files Access (MANAGE_EXTERNAL_STORAGE) is granted
        if (hasAllFilesAccess(context)) {
            searchExternalStorageRecursive(trimmed, results)
        }

        // 3. SAF user-connected folders (0-permission DocumentTree)
        treeUris.forEach { treeUriStr ->
            runCatching {
                searchDocumentTree(Uri.parse(treeUriStr), trimmed, results)
            }
        }

        // 4. MediaStore scoped queries (works safely without crashing on scoped storage)
        queryMediaStoreImages(trimmed, results)
        queryMediaStoreFiles(trimmed, results)

        // 5. Privileged Shizuku /sdcard search fallback when Shizuku is granted
        val shizukuStatus = shizukuBridge.statusState.value
        if (shizukuStatus.isBinderAlive && shizukuStatus.isPermissionGranted && results.size < 45) {
            searchViaShizuku(trimmed, results)
        }

        results.values
            .sortedWith(
                compareBy<DeviceSearchFile> { !it.name.startsWith(trimmed, ignoreCase = true) }
                    .thenBy { it.kind }
                    .thenByDescending { it.modifiedMs }
            )
            .take(70)
    }

    private fun searchExternalStorageRecursive(
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull() ?: return
        if (!root.exists() || !root.canRead()) return

        val queue = ArrayDeque<File>()
        queue.add(root)

        var visitedDirs = 0
        val maxDirs = 260
        val maxMatches = 50

        while (queue.isNotEmpty() && visitedDirs < maxDirs && out.size < maxMatches) {
            val dir = queue.removeFirst()
            visitedDirs++

            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (out.size >= maxMatches) break
                val name = child.name
                if (name.startsWith(".")) continue

                if (child.isDirectory) {
                    val lower = name.lowercase()
                    val parentLower = child.parentFile?.name?.lowercase()
                    if (parentLower == "android" && (lower == "data" || lower == "obb")) {
                        continue
                    }
                    queue.add(child)
                } else if (child.isFile && matchesQuery(name, query)) {
                    val path = child.absolutePath
                    if (out.containsKey(path)) continue
                    val mime = guessMimeType(name)
                    val kind = classifyKind(mime, name)
                    val shareUri = runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", child)
                    }.getOrNull() ?: Uri.fromFile(child)
                    val thumb = if (kind == SearchResultKind.IMAGE && child.canRead()) {
                        decodeFileThumbnail(child)
                    } else null

                    out.putIfAbsent(
                        path,
                        DeviceSearchFile(
                            id = "fs:$path",
                            name = name,
                            displayPath = path,
                            parentPath = child.parent ?: root.absolutePath,
                            uriString = shareUri.toString(),
                            localFilePath = path,
                            mimeType = mime,
                            sizeBytes = child.length(),
                            modifiedMs = child.lastModified(),
                            kind = kind,
                            thumbnail = thumb
                        )
                    )
                }
            }
        }
    }

    private fun searchLocalDirs(
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val dirs = listOfNotNull(
            File(context.cacheDir, "exports"),
            File(context.filesDir, "exports")
        )
        dirs.filter { it.exists() && it.isDirectory }.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && matchesQuery(file.name, query)) {
                    val mime = guessMimeType(file.name)
                    val kind = classifyKind(mime, file.name)
                    val contentUri = runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }.getOrNull()
                    val thumb = if (kind == SearchResultKind.IMAGE) {
                        decodeFileThumbnail(file)
                    } else null

                    out.putIfAbsent(
                        file.absolutePath,
                        DeviceSearchFile(
                            id = "local:${file.absolutePath}",
                            name = file.name,
                            displayPath = file.absolutePath,
                            parentPath = file.parent ?: "",
                            uriString = contentUri?.toString() ?: Uri.fromFile(file).toString(),
                            localFilePath = file.absolutePath,
                            mimeType = mime,
                            sizeBytes = file.length(),
                            modifiedMs = file.lastModified(),
                            kind = kind,
                            thumbnail = thumb
                        )
                    )
                }
            }
        }
    }

    private fun searchDocumentTree(
        treeUri: Uri,
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to (rootDocId.substringAfter(':', rootDocId)))

        var visitedDirs = 0
        val maxDirs = 60

        while (queue.isNotEmpty() && visitedDirs < maxDirs && out.size < 50) {
            val (docId, folderLabel) = queue.removeFirst()
            visitedDirs++

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && out.size < 50) {
                        val childId = cursor.getString(idIdx) ?: continue
                        val name = cursor.getString(nameIdx) ?: continue
                        val rawMime = cursor.getString(mimeIdx) ?: "*/*"
                        val size = cursor.getLong(sizeIdx)
                        val modified = cursor.getLong(modIdx)

                        if (rawMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) {
                                queue.add(childId to "$folderLabel/$name")
                            }
                            continue
                        }

                        if (matchesQuery(name, query)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val mime = if (rawMime == "*/*") guessMimeType(name) else rawMime
                            val kind = classifyKind(mime, name)
                            val thumb = if (kind == SearchResultKind.IMAGE) {
                                decodeUriThumbnail(docUri)
                            } else null

                            val key = docUri.toString()
                            out.putIfAbsent(
                                key,
                                DeviceSearchFile(
                                    id = "saf:$key",
                                    name = name,
                                    displayPath = "$folderLabel/$name",
                                    parentPath = folderLabel,
                                    uriString = key,
                                    parentUriString = parentUri.toString(),
                                    mimeType = mime,
                                    sizeBytes = size,
                                    modifiedMs = modified,
                                    kind = kind,
                                    thumbnail = thumb
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun queryMediaStoreImages(
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Images.Media.DATA)
        }

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        runCatching {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }

                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/*" else "image/*"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mod = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L
                    val rel = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "Pictures/"

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val thumb = decodeUriThumbnail(contentUri)
                    val displayPath = if (rel.endsWith(name)) rel else "${rel.trimEnd('/')}/$name"
                    val parent = rel.trimEnd('/')

                    out.putIfAbsent(
                        contentUri.toString(),
                        DeviceSearchFile(
                            id = "ms_img:$id",
                            name = name,
                            displayPath = displayPath,
                            parentPath = parent,
                            uriString = contentUri.toString(),
                            mimeType = mime,
                            sizeBytes = size,
                            modifiedMs = mod,
                            kind = SearchResultKind.IMAGE,
                            thumbnail = thumb
                        )
                    )
                    count++
                }
            }
        }
    }

    private fun queryMediaStoreFiles(
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }

        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        runCatching {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else -1

                var count = 0
                while (cursor.moveToNext() && count < 25) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null) ?: guessMimeType(name)
                    if (mime == "vnd.android.document/directory") continue
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mod = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L
                    val rel = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "Files"

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val kind = classifyKind(mime, name)
                    out.putIfAbsent(
                        contentUri.toString(),
                        DeviceSearchFile(
                            id = "ms_file:$id",
                            name = name,
                            displayPath = "${rel.trimEnd('/')}/$name",
                            parentPath = rel.trimEnd('/'),
                            uriString = contentUri.toString(),
                            mimeType = mime,
                            sizeBytes = size,
                            modifiedMs = mod,
                            kind = kind,
                            thumbnail = if (kind == SearchResultKind.IMAGE) decodeUriThumbnail(contentUri) else null
                        )
                    )
                    count++
                }
            }
        }
    }

    private suspend fun searchViaShizuku(
        query: String,
        out: MutableMap<String, DeviceSearchFile>
    ) {
        val safePattern = query.replace(Regex("""["'`$\\]"""), "")
        if (safePattern.length < 2) return
        val cmd = "find /sdcard -maxdepth 4 -type f -iname \"*$safePattern*\" 2>/dev/null | head -n 30"
        val res = shizukuBridge.runPrivilegedShell(cmd)
        val lines = res.getOrNull()?.lines()?.filter { it.isNotBlank() }.orEmpty()
        for (path in lines) {
            if (out.containsKey(path)) continue
            val file = File(path)
            val name = file.name.ifBlank { path.substringAfterLast('/') }
            val mime = guessMimeType(name)
            val kind = classifyKind(mime, name)
            val shareUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull() ?: Uri.fromFile(file)
            val thumb = if (kind == SearchResultKind.IMAGE && file.canRead()) {
                decodeFileThumbnail(file)
            } else null

            out.putIfAbsent(
                path,
                DeviceSearchFile(
                    id = "shizuku:$path",
                    name = name,
                    displayPath = path,
                    parentPath = file.parent ?: "/sdcard",
                    uriString = shareUri.toString(),
                    localFilePath = path,
                    mimeType = mime,
                    sizeBytes = if (file.exists()) file.length() else 0L,
                    modifiedMs = if (file.exists()) file.lastModified() else System.currentTimeMillis(),
                    kind = kind,
                    thumbnail = thumb
                )
            )
        }
    }

    fun openFile(item: DeviceSearchFile): Boolean {
        val uri = resolvePlayableUri(item)
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun shareFile(item: DeviceSearchFile): Boolean {
        val uri = resolvePlayableUri(item)
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Поделиться: ${item.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun openContainingFolder(item: DeviceSearchFile): Boolean {
        if (item.parentUriString != null) {
            val openedSaf = runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(item.parentUriString),
                        DocumentsContract.Document.MIME_TYPE_DIR
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (openedSaf) return true
        }

        if (!item.localFilePath.isNullOrBlank()) {
            val parentDir = File(item.localFilePath).parentFile
            if (parentDir != null) {
                val openedLocal = runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", parentDir)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "resource/folder")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (openedLocal) return true
            }
        }

        // Copy path & open system file manager as universal fallback
        copyPath(item)
        return runCatching {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Prefer launching device Files app if present
            val filesIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.documentsui")
            if (filesIntent != null) {
                filesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(filesIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun copyPath(item: DeviceSearchFile) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cb?.setPrimaryClip(ClipData.newPlainText("File Path", item.displayPath))
    }

    suspend fun deleteFile(item: DeviceSearchFile): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uriString)
        val deletedDoc = runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                false
            }
        }.getOrDefault(false)
        if (deletedDoc) return@withContext true

        val deletedLocal = item.localFilePath?.let { path ->
            runCatching { File(path).delete() }.getOrDefault(false)
        } ?: false
        if (deletedLocal) return@withContext true

        val deletedResolver = runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
        if (deletedResolver) return@withContext true

        if (!item.localFilePath.isNullOrBlank()) {
            val escaped = item.localFilePath.replace("\"", "\\\"")
            val res = shizukuBridge.runPrivilegedShell("rm -f \"$escaped\"")
            return@withContext res.isSuccess
        }
        false
    }

    private fun resolvePlayableUri(item: DeviceSearchFile): Uri {
        val parsed = Uri.parse(item.uriString)
        if (parsed.scheme == "file" && item.localFilePath != null) {
            return runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.localFilePath))
            }.getOrDefault(parsed)
        }
        return parsed
    }

    private fun matchesQuery(name: String, query: String): Boolean {
        return name.contains(query, ignoreCase = true) ||
            KeyboardSearchMatcher.matches(name, query)
    }

    private fun classifyKind(mime: String, name: String): SearchResultKind {
        val lower = name.lowercase()
        val isImg = mime.startsWith("image/", ignoreCase = true) ||
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic")
        return if (isImg) SearchResultKind.IMAGE else SearchResultKind.FILE
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "apk" -> "application/vnd.android.package-archive"
            "txt", "md", "log" -> "text/plain"
            else -> "*/*"
        }
    }

    private fun decodeUriThumbnail(uri: Uri): ImageBitmap? {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxSide = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
            val sample = (maxSide / 140).coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)?.asImageBitmap()
            }
        }.getOrNull()
    }

    private fun decodeFileThumbnail(file: File): ImageBitmap? {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }

    companion object {
        fun hasAllFilesAccess(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun hasAudioReadAccess(context: Context): Boolean {
            if (hasAllFilesAccess(context)) return true
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun openAllFilesAccessSettings(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appIntent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launchedApp = runCatching {
                    context.startActivity(appIntent)
                    true
                }.getOrDefault(false)
                if (launchedApp) return true

                val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return runCatching {
                    context.startActivity(generalIntent)
                    true
                }.getOrDefault(false)
            }
            return false
        }

        fun formatSize(bytes: Long): String {
            if (bytes <= 0L) return ""
            return when {
                bytes < 1024L -> "$bytes Б"
                bytes < 1024L * 1024L -> "${bytes / 1024L} КБ"
                else -> String.format("%.1f МБ", bytes / (1024f * 1024f))
            }
        }
    }
}
