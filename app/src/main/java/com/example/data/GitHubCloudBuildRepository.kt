package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.model.GitHubBuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Syncs an AI Studio / Gradle source ZIP to a user-owned GitHub repository via Git Data REST API,
 * triggers GitHub Actions (`keydeck-cloud-build.yml`), downloads the compiled debug APK artifact,
 * and hands the extracted APK off to KeyDeck's standard APK installer without needing Android Studio.
 */
class GitHubCloudBuildRepository(private val context: Context) {

    suspend fun buildApkFromSourceZip(
        sourceUri: Uri,
        localFilePath: String?,
        config: GitHubBuildConfig,
        onProgress: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSlug = normalizeRepoSlug(config.repoSlug)
            val branch = config.branch.trim().ifBlank { "main" }
            val token = config.token.trim()
            require(cleanSlug.contains("/") && token.isNotBlank()) {
                "Укажите репозиторий (owner/repo) и GitHub Token"
            }

            onProgress("Чтение исходников из ZIP-архива…")
            val files = collectProjectFilesFromZip(sourceUri, localFilePath, branch)
            require(files.isNotEmpty()) {
                "В ZIP-архиве не найдено файлов проекта для сборки"
            }

            val hasGradle = files.keys.any {
                it.endsWith("build.gradle.kts", ignoreCase = true) ||
                    it.endsWith("build.gradle", ignoreCase = true)
            }
            require(hasGradle) {
                "В архиве нет build.gradle(.kts) — облачная сборка поддерживает проекты Android Gradle"
            }

            onProgress("Подключение к github.com/$cleanSlug…")
            verifyRepoAccess(cleanSlug, token)

            val (headCommitSha, baseTreeSha) = ensureBranchHeadAndTree(cleanSlug, branch, token, onProgress)
            val remoteTreeMap = if (baseTreeSha.isNotBlank()) {
                fetchRemoteTreeShas(cleanSlug, baseTreeSha, token)
            } else {
                emptyMap()
            }

            val localShas = files.mapValues { (_, bytes) -> computeGitBlobSha1(bytes) }
            val changedEntries = files.entries.filter { (path, _) ->
                remoteTreeMap[path] != localShas[path]
            }

            val uploadedShas = HashMap<String, String>(files.size + 4)
            listOf(
                "debug.keystore",
                "debug.keystore.base64",
                "app/debug.keystore",
                "app/debug.keystore.base64"
            ).forEach { keystorePath ->
                if (!files.containsKey(keystorePath)) {
                    remoteTreeMap[keystorePath]?.let { uploadedShas[keystorePath] = it }
                }
            }
            files.forEach { (path, _) ->
                val remote = remoteTreeMap[path]
                if (remote != null && remote == localShas[path]) {
                    uploadedShas[path] = remote
                }
            }

            if (changedEntries.isNotEmpty()) {
                onProgress("Отправка изменённых файлов в GitHub: 0/${changedEntries.size}…")
                val doneCount = AtomicInteger(0)
                val sem = Semaphore(4)
                val results = coroutineScope {
                    changedEntries.map { (path, bytes) ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val blobSha = createGitBlob(cleanSlug, token, bytes)
                                val cur = doneCount.incrementAndGet()
                                if (cur % 5 == 0 || cur == changedEntries.size) {
                                    onProgress("Отправка файлов в GitHub: $cur/${changedEntries.size}…")
                                }
                                path to blobSha
                            }
                        }
                    }.awaitAll()
                }
                results.forEach { (path, sha) -> uploadedShas[path] = sha }
            } else {
                onProgress("Файлы не изменились, создание триггерного коммита…")
            }

            onProgress("Создание дерева и коммита в ветке $branch…")
            val newTreeSha = createGitTree(cleanSlug, token, uploadedShas)
            val newCommitSha = createGitCommit(
                repoSlug = cleanSlug,
                token = token,
                message = "KeyDeck ZIP source sync & APK build",
                treeSha = newTreeSha,
                parentCommitSha = headCommitSha
            )
            updateBranchRef(cleanSlug, branch, token, newCommitSha)

            onProgress("Ожидание запуска GitHub Actions для $newCommitSha…")
            val runId = waitForAndCompleteWorkflowRun(
                repoSlug = cleanSlug,
                branch = branch,
                commitSha = newCommitSha,
                token = token,
                onProgress = onProgress
            )

            onProgress("Скачивание собранного APK из артефакта сборки…")
            downloadAndExtractArtifactApk(cleanSlug, runId, token)
        }
    }

    suspend fun fetchLatestApkFromRepo(
        config: GitHubBuildConfig,
        onProgress: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSlug = normalizeRepoSlug(config.repoSlug)
            val branch = config.branch.trim().ifBlank { "main" }
            val token = config.token.trim()
            require(cleanSlug.contains("/") && token.isNotBlank()) {
                "Укажите репозиторий (owner/repo) и GitHub Token"
            }

            onProgress("Поиск последней успешной сборки в $cleanSlug ($branch)…")
            val url = "https://api.github.com/repos/$cleanSlug/actions/runs?branch=${urlEncode(branch)}&status=completed&per_page=10"
            val obj = JSONObject(githubRequest("GET", url, token).second)
            val runs = obj.optJSONArray("workflow_runs") ?: JSONArray()
            var successRunId: Long? = null
            for (i in 0 until runs.length()) {
                val r = runs.optJSONObject(i) ?: continue
                if (r.optString("conclusion") == "success") {
                    successRunId = r.optLong("id", -1L).takeIf { it > 0L }
                    break
                }
            }
            requireNotNull(successRunId) {
                "Успешных сборок GitHub Actions в ветке $branch пока нет"
            }
            onProgress("Скачивание готового APK из запуска #$successRunId…")
            downloadAndExtractArtifactApk(cleanSlug, successRunId, token)
        }
    }

    private fun verifyRepoAccess(repoSlug: String, token: String) {
        val (code, body) = githubRequest("GET", "https://api.github.com/repos/$repoSlug", token, allowError = true)
        if (code == 401 || code == 403) {
            throw IllegalStateException("GitHub отклонил токен (HTTP $code). Проверьте права Contents + Actions")
        }
        if (code == 404) {
            throw IllegalStateException("Репозиторий $repoSlug не найден или недоступен для этого токена")
        }
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            throw IllegalStateException("Ошибка доступа к GitHub ($code): $msg")
        }
    }

    private fun ensureBranchHeadAndTree(
        repoSlug: String,
        branch: String,
        token: String,
        onProgress: (String) -> Unit
    ): Pair<String, String> {
        val refUrl = "https://api.github.com/repos/$repoSlug/git/ref/heads/${urlEncode(branch)}"
        val (code, body) = githubRequest("GET", refUrl, token, allowError = true)
        if (code in 200..299) {
            val commitSha = JSONObject(body).getJSONObject("object").getString("sha")
            val commitObj = JSONObject(
                githubRequest("GET", "https://api.github.com/repos/$repoSlug/git/commits/$commitSha", token).second
            )
            val treeSha = commitObj.getJSONObject("tree").getString("sha")
            return commitSha to treeSha
        }

        if (code == 404 || code == 409) {
            onProgress("Инициализация ветки $branch в репозитории…")
            val initBody = JSONObject().apply {
                put("message", "Initialize KeyDeck repository")
                put("content", Base64.encodeToString("# KeyDeck Cloud Build\n".toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                put("branch", branch)
            }
            val (putCode, putResp) = githubRequest(
                method = "PUT",
                urlStr = "https://api.github.com/repos/$repoSlug/contents/README.md",
                token = token,
                jsonBody = initBody.toString(),
                allowError = true
            )
            if (putCode !in 200..299) {
                val msg = runCatching { JSONObject(putResp).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException("Не удалось инициализировать ветку $branch ($putCode): $msg")
            }
            val createdObj = JSONObject(putResp).optJSONObject("commit")
            val commitSha = createdObj?.optString("sha").orEmpty()
            val treeSha = createdObj?.optJSONObject("tree")?.optString("sha").orEmpty()
            if (commitSha.isNotBlank()) return commitSha to treeSha

            val retryResp = JSONObject(githubRequest("GET", refUrl, token).second)
            val cSha = retryResp.getJSONObject("object").getString("sha")
            val cObj = JSONObject(githubRequest("GET", "https://api.github.com/repos/$repoSlug/git/commits/$cSha", token).second)
            return cSha to cObj.getJSONObject("tree").getString("sha")
        }

        val errMsg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        throw IllegalStateException("Не удалось прочитать ветку $branch ($code): $errMsg")
    }

    private fun fetchRemoteTreeShas(repoSlug: String, treeSha: String, token: String): Map<String, String> {
        val url = "https://api.github.com/repos/$repoSlug/git/trees/$treeSha?recursive=1"
        val (code, body) = githubRequest("GET", url, token, allowError = true)
        if (code !in 200..299) return emptyMap()
        val out = HashMap<String, String>()
        val tree = JSONObject(body).optJSONArray("tree") ?: return out
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") == "blob") {
                val p = item.optString("path")
                val s = item.optString("sha")
                if (p.isNotBlank() && s.isNotBlank()) out[p] = s
            }
        }
        return out
    }

    private fun createGitBlob(repoSlug: String, token: String, bytes: ByteArray): String {
        val payload = JSONObject().apply {
            put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put("encoding", "base64")
        }
        val (_, resp) = githubRequest(
            "POST",
            "https://api.github.com/repos/$repoSlug/git/blobs",
            token,
            payload.toString()
        )
        return JSONObject(resp).getString("sha")
    }

    private fun createGitTree(repoSlug: String, token: String, fileShas: Map<String, String>): String {
        val treeArr = JSONArray()
        fileShas.entries.sortedBy { it.key }.forEach { (path, sha) ->
            val mode = if (path.endsWith("gradlew") || path.endsWith(".sh")) "100755" else "100644"
            treeArr.put(
                JSONObject().apply {
                    put("path", path)
                    put("mode", mode)
                    put("type", "blob")
                    put("sha", sha)
                }
            )
        }
        val payload = JSONObject().apply {
            put("tree", treeArr)
        }
        val (_, resp) = githubRequest(
            "POST",
            "https://api.github.com/repos/$repoSlug/git/trees",
            token,
            payload.toString()
        )
        return JSONObject(resp).getString("sha")
    }

    private fun createGitCommit(
        repoSlug: String,
        token: String,
        message: String,
        treeSha: String,
        parentCommitSha: String
    ): String {
        val payload = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", JSONArray().apply { put(parentCommitSha) })
        }
        val (_, resp) = githubRequest(
            "POST",
            "https://api.github.com/repos/$repoSlug/git/commits",
            token,
            payload.toString()
        )
        return JSONObject(resp).getString("sha")
    }

    private fun updateBranchRef(repoSlug: String, branch: String, token: String, commitSha: String) {
        val payload = JSONObject().apply {
            put("sha", commitSha)
            put("force", true)
        }
        githubRequest(
            "PATCH",
            "https://api.github.com/repos/$repoSlug/git/refs/heads/${urlEncode(branch)}",
            token,
            payload.toString()
        )
    }

    private suspend fun waitForAndCompleteWorkflowRun(
        repoSlug: String,
        branch: String,
        commitSha: String,
        token: String,
        onProgress: (String) -> Unit
    ): Long {
        val startMs = System.currentTimeMillis()
        val timeoutMs = 9 * 60 * 1000L
        var dispatchFallbackSent = false

        while (System.currentTimeMillis() - startMs < timeoutMs) {
            val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
            val runsUrl = "https://api.github.com/repos/$repoSlug/actions/runs?head_sha=$commitSha&per_page=5"
            val (code, body) = githubRequest("GET", runsUrl, token, allowError = true)
            if (code in 200..299) {
                val runs = JSONObject(body).optJSONArray("workflow_runs") ?: JSONArray()
                var matchedRun: JSONObject? = null
                for (i in 0 until runs.length()) {
                    val r = runs.optJSONObject(i) ?: continue
                    if (r.optString("path").contains("keydeck-cloud-build", ignoreCase = true) ||
                        r.optString("name").contains("KeyDeck", ignoreCase = true)
                    ) {
                        matchedRun = r
                        break
                    }
                }
                if (matchedRun == null && runs.length() > 0) {
                    matchedRun = runs.optJSONObject(0)
                }

                if (matchedRun != null) {
                    val runId = matchedRun.getLong("id")
                    val status = matchedRun.optString("status", "queued")
                    val conclusion = matchedRun.optString("conclusion", "")
                    if (status == "completed") {
                        if (conclusion == "success") {
                            return runId
                        }
                        val failDetail = fetchFailedStepHint(repoSlug, runId, token)
                        val htmlUrl = matchedRun.optString("html_url", "")
                        throw IllegalStateException(
                            "Сборка в GitHub Actions завершилась со статусом '$conclusion'" +
                                (if (failDetail.isNotBlank()) " ($failDetail)" else "") +
                                (if (htmlUrl.isNotBlank()) "\nЛог: $htmlUrl" else "")
                        )
                    } else {
                        val rusStatus = when (status) {
                            "queued" -> "в очереди"
                            "in_progress" -> "компиляция Gradle"
                            else -> status
                        }
                        onProgress("GitHub Actions: $rusStatus (${elapsedSec} сек)…")
                    }
                } else {
                    if (!dispatchFallbackSent && elapsedSec >= 12) {
                        dispatchFallbackSent = true
                        runCatching {
                            val dispatchPayload = JSONObject().put("ref", branch).toString()
                            githubRequest(
                                "POST",
                                "https://api.github.com/repos/$repoSlug/actions/workflows/$WORKFLOW_FILE_NAME/dispatches",
                                token,
                                dispatchPayload,
                                allowError = true
                            )
                        }
                    }
                    onProgress("Ожидание старта GitHub Actions (${elapsedSec} сек)…")
                }
            }
            delay(4000L)
        }
        throw IllegalStateException("Превышено время ожидания сборки в GitHub Actions (9 мин)")
    }

    private fun fetchFailedStepHint(repoSlug: String, runId: Long, token: String): String {
        return runCatching {
            val (_, body) = githubRequest(
                "GET",
                "https://api.github.com/repos/$repoSlug/actions/runs/$runId/jobs",
                token
            )
            val jobs = JSONObject(body).optJSONArray("jobs") ?: return ""
            for (j in 0 until jobs.length()) {
                val steps = jobs.optJSONObject(j)?.optJSONArray("steps") ?: continue
                for (s in 0 until steps.length()) {
                    val st = steps.optJSONObject(s) ?: continue
                    if (st.optString("conclusion") == "failure") {
                        return "шаг: ${st.optString("name")}"
                    }
                }
            }
            ""
        }.getOrDefault("")
    }

    private fun downloadAndExtractArtifactApk(repoSlug: String, runId: Long, token: String): File {
        val (_, artBody) = githubRequest(
            "GET",
            "https://api.github.com/repos/$repoSlug/actions/runs/$runId/artifacts",
            token
        )
        val artifacts = JSONObject(artBody).optJSONArray("artifacts") ?: JSONArray()
        var downloadUrl: String? = null
        for (i in 0 until artifacts.length()) {
            val a = artifacts.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name == ARTIFACT_NAME) {
                downloadUrl = a.optString("archive_download_url")
                break
            }
        }
        if (downloadUrl.isNullOrBlank() && artifacts.length() > 0) {
            downloadUrl = artifacts.optJSONObject(0)?.optString("archive_download_url")
        }
        require(!downloadUrl.isNullOrBlank()) {
            "Артефакт со собранным APK не найден в запуске #$runId"
        }

        val outDir = File(context.cacheDir, "exports/updates/cloud_apk").apply {
            runCatching { deleteRecursively() }
            mkdirs()
        }
        val artifactZip = File(outDir, "artifact_$runId.zip")
        downloadBinaryWithGithubRedirect(downloadUrl, token, artifactZip)

        var extractedApk: File? = null
        ZipInputStream(BufferedInputStream(artifactZip.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val baseName = entry.name.replace('\\', '/').substringAfterLast('/')
                if (!entry.isDirectory && baseName.endsWith(".apk", ignoreCase = true)) {
                    val target = File(outDir, baseName.replace(Regex("""[^A-Za-z0-9._-]"""), "_"))
                    target.outputStream().use { zis.copyTo(it, 16 * 1024) }
                    if (target.length() > 0L) {
                        extractedApk = target
                        break
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        runCatching { artifactZip.delete() }
        return extractedApk ?: throw IllegalStateException("Внутри скачанного артефакта не найден .apk файл")
    }

    private fun downloadBinaryWithGithubRedirect(urlStr: String, token: String, destFile: File) {
        var currentUrl = urlStr
        var sendAuth = true
        for (hop in 0 until 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "KeyDeck-CloudUpdater")
                if (sendAuth) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location").orEmpty()
                    require(loc.isNotBlank()) { "Пустой редирект при скачивании артефакта ($code)" }
                    currentUrl = loc
                    sendAuth = loc.startsWith("https://api.github.com/")
                    continue
                }
                if (code !in 200..299) {
                    throw IllegalStateException("Ошибка скачивания артефакта (HTTP $code)")
                }
                conn.inputStream.use { input ->
                    destFile.outputStream().use { out ->
                        input.copyTo(out, 16 * 1024)
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Слишком много редиректов при скачивании APK")
    }

    private fun githubRequest(
        method: String,
        urlStr: String,
        token: String,
        jsonBody: String? = null,
        allowError: Boolean = false
    ): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "KeyDeck-CloudUpdater")
            setRequestProperty("Authorization", "Bearer $token")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (jsonBody != null) {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (!allowError && code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException("GitHub API $method HTTP $code: $msg")
            }
            code to text
        } finally {
            conn.disconnect()
        }
    }

    internal fun collectProjectFilesFromZip(
        sourceUri: Uri,
        localFilePath: String?,
        branch: String
    ): Map<String, ByteArray> {
        val stream: InputStream = when {
            !localFilePath.isNullOrBlank() && File(localFilePath).canRead() -> File(localFilePath).inputStream()
            else -> context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Не удалось открыть исходный ZIP-архив")
        }

        val rawMap = LinkedHashMap<String, ByteArray>()
        ZipInputStream(BufferedInputStream(stream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var count = 0
            while (entry != null && count < 2500) {
                val rawName = entry.name.orEmpty().replace('\\', '/').trimStart('/')
                if (!entry.isDirectory && rawName.isNotBlank() && !rawName.contains("..")) {
                    if (!shouldSkipProjectEntry(rawName)) {
                        val bytes = readBoundedBytes(zis, maxBytes = 15 * 1024 * 1024)
                        if (bytes != null) {
                            rawMap[rawName] = bytes
                            count++
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val normalized = stripWrapperFolderIfPresent(rawMap).toMutableMap()
        normalized[".github/workflows/$WORKFLOW_FILE_NAME"] = buildWorkflowYaml(branch).toByteArray(Charsets.UTF_8)
        return normalized
    }

    internal companion object {
        const val WORKFLOW_FILE_NAME = "keydeck-cloud-build.yml"
        const val ARTIFACT_NAME = "keydeck-debug-apk"

        internal fun normalizeRepoSlug(raw: String): String {
            var s = raw.trim()
            if (s.startsWith("https://github.com/")) s = s.removePrefix("https://github.com/")
            if (s.startsWith("http://github.com/")) s = s.removePrefix("http://github.com/")
            s = s.removeSuffix(".git").trim('/')
            val parts = s.split("/").filter { it.isNotBlank() }
            return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else s
        }

        internal fun computeGitBlobSha1(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-1")
            md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            md.update(bytes)
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun stripWrapperFolderIfPresent(entries: Map<String, ByteArray>): Map<String, ByteArray> {
            if (entries.isEmpty()) return entries
            val keys = entries.keys
            val hasRootGradle = keys.any {
                it == "settings.gradle.kts" || it == "settings.gradle" || it == "app/build.gradle.kts" || it == "app/build.gradle"
            }
            if (hasRootGradle) return entries

            val firstSegments = keys.mapNotNull { it.substringBefore('/', "").takeIf { seg -> seg.isNotBlank() && it.contains('/') } }.toSet()
            if (firstSegments.size == 1 && keys.all { it.contains('/') }) {
                val prefix = firstSegments.first() + "/"
                val stripped = LinkedHashMap<String, ByteArray>(entries.size)
                entries.forEach { (k, v) -> stripped[k.removePrefix(prefix)] = v }
                val strippedHasGradle = stripped.keys.any {
                    it == "settings.gradle.kts" || it == "settings.gradle" || it == "app/build.gradle.kts" || it == "app/build.gradle"
                }
                if (strippedHasGradle) return stripped
            }
            return entries
        }

        internal fun shouldSkipProjectEntry(path: String): Boolean {
            val low = path.lowercase(Locale.ROOT)
            return low.startsWith(".git/") ||
                low.contains("/.git/") ||
                low.startsWith("build/") ||
                low.startsWith("app/build/") ||
                low.startsWith(".gradle/") ||
                low.startsWith(".idea/") ||
                low.startsWith(".aistudio/") ||
                low.startsWith(".build-outputs/") ||
                low == "local.properties" ||
                low.endsWith("/local.properties") ||
                low.endsWith(".apk")
        }

        internal fun buildWorkflowYaml(branch: String): String {
            val safeBranch = branch.replace("\"", "").ifBlank { "main" }
            return """
                name: KeyDeck Cloud APK Build
                on:
                  push:
                    branches: [ "$safeBranch" ]
                  workflow_dispatch:

                jobs:
                  build-apk:
                    runs-on: ubuntu-latest
                    steps:
                      - name: Checkout source
                        uses: actions/checkout@v4
                      - name: Setup JDK 21
                        uses: actions/setup-java@v4
                        with:
                          distribution: temurin
                          java-version: '21'
                      - name: Detect Gradle version
                        id: gradle_ver
                        run: |
                          VER="9.3.1"
                          if [ -f gradle/wrapper/gradle-wrapper.properties ]; then
                            PARSED=${'$'}(grep -oE 'gradle-[0-9]+\.[0-9]+(\.[0-9]+)?' gradle/wrapper/gradle-wrapper.properties | head -n1 | sed 's/gradle-//')
                            if [ -n "${'$'}PARSED" ]; then VER="${'$'}PARSED"; fi
                          fi
                          echo "version=${'$'}VER" >> "${'$'}GITHUB_OUTPUT"
                      - name: Setup Gradle
                        uses: gradle/actions/setup-gradle@v4
                        with:
                          gradle-version: ${'$'}{{ steps.gradle_ver.outputs.version }}
                      - name: Prepare debug keystore and env
                        run: |
                          if [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ]; then
                            base64 -d debug.keystore.base64 > debug.keystore
                          fi
                          if [ ! -f debug.keystore ]; then
                            keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
                          fi
                          if [ ! -f .env ] && [ -f .env.example ]; then
                            cp .env.example .env
                          fi
                          chmod +x gradlew 2>/dev/null || true
                      - name: Build Debug APK
                        run: |
                          if [ -f gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
                            ./gradlew :app:assembleDebug --no-daemon --stacktrace
                          else
                            gradle :app:assembleDebug --no-daemon --stacktrace
                          fi
                      - name: Upload Debug APK Artifact
                        uses: actions/upload-artifact@v4
                        with:
                          name: $ARTIFACT_NAME
                          path: app/build/outputs/apk/debug/*.apk
                          if-no-files-found: error
            """.trimIndent() + "\n"
        }

        private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray? {
            val buf = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBytes) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        private fun urlEncode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
