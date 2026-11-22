package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.AlbumExpectedTrack
import com.example.model.AppEntry
import com.example.model.LocalAudioTrack
import com.example.model.MediaPlayerState
import com.example.model.MusicSourceKind
import com.example.model.SmartAlbum
import com.example.model.SyncedLyricLine
import com.example.model.TrackLyricsData
import com.example.model.TrackSourceCandidate
import com.example.model.UnifiedMusicSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

sealed class DownloadOutcome {
    data class DownloadedFile(val track: LocalAudioTrack, val message: String) : DownloadOutcome()
    data class NeedsStreamingCapture(
        val title: String,
        val artist: String,
        val packageName: String,
        val appLabel: String
    ) : DownloadOutcome()

    data class Failed(val reason: String) : DownloadOutcome()
}

data class AlbumBlueprintResult(
    val releaseId: String,
    val title: String,
    val artist: String,
    val expectedTrackCount: Int,
    val expectedTracks: List<AlbumExpectedTrack>,
    val coverUrl: String?,
    val alternateCoverUrls: List<String>
)

object MusicCatalogAndLyricsRepository {

    private const val USER_AGENT = "KeyDeckLauncher/1.0 (https://keydeck.app)"
    private const val CONNECT_TIMEOUT_MS = 6500
    private const val READ_TIMEOUT_MS = 8500
    private val LRC_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private val artworkBitmapCache = object : LruCache<String, ImageBitmap>(48) {}

    fun normalizeKeyText(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    fun buildTrackKey(title: String, artist: String): String {
        val t = normalizeKeyText(title)
        val a = normalizeKeyText(artist)
        return if (a.isNotEmpty()) "$a|$t" else t
    }

    // ------------------------------------------------------------------------
    // Simultaneous Multi-Source Music Search + Auto / Manual Candidate Grouping
    // ------------------------------------------------------------------------

    suspend fun searchAllSources(
        query: String,
        localTracks: List<LocalAudioTrack>,
        mediaState: MediaPlayerState,
        installedMusicApps: List<AppEntry>
    ): List<UnifiedMusicSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val allCandidates = mutableListOf<TrackSourceCandidate>()

        // 1. Local tracks + Telegram Audio
        val localMatches = localTracks.filter {
            KeyboardSearchMatcher.matches(it.title, trimmed) ||
                KeyboardSearchMatcher.matches(it.artist, trimmed) ||
                KeyboardSearchMatcher.matches(it.album, trimmed)
        }.take(25)

        localMatches.forEach { lt ->
            val ext = lt.filePath?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
                ?: lt.formatLabel.lowercase(Locale.ROOT)
            val isLossless = ext == "flac" || ext == "wav" || lt.formatLabel.equals("FLAC", true)
            val kind = when {
                isLossless -> MusicSourceKind.LOCAL_FLAC
                lt.isTelegramAudio -> MusicSourceKind.TELEGRAM_AUDIO
                else -> MusicSourceKind.LOCAL_FILE
            }
            allCandidates.add(
                TrackSourceCandidate(
                    id = "loc:${lt.id}",
                    sourceKind = kind,
                    sourceLabel = when (kind) {
                        MusicSourceKind.LOCAL_FLAC -> "FLAC Локально"
                        MusicSourceKind.TELEGRAM_AUDIO -> "Telegram Audio"
                        else -> "Файлы устройства"
                    },
                    title = lt.title,
                    artist = lt.artist,
                    album = lt.album,
                    durationMs = lt.durationMs,
                    fileExtension = ext.ifBlank { "mp3" },
                    bitrateKbps = lt.bitrateKbps,
                    sampleRateHz = lt.sampleRateHz,
                    localTrack = lt
                )
            )
        }

        // 2. Active Streaming Sessions Queues + Connected MediaBrowser + Recent Stream History
        mediaState.sessions.forEach { sess ->
            sess.queue.filter {
                KeyboardSearchMatcher.matches(it.title, trimmed) ||
                    KeyboardSearchMatcher.matches(it.subtitle, trimmed)
            }.take(10).forEach { qi ->
                allCandidates.add(
                    TrackSourceCandidate(
                        id = "sq:${sess.packageName}:${qi.queueId}",
                        sourceKind = MusicSourceKind.STREAMING_PAID,
                        sourceLabel = sess.appLabel,
                        title = qi.title,
                        artist = qi.subtitle,
                        album = sess.album,
                        packageName = sess.packageName,
                        fileExtension = "stream",
                        queueItemId = qi.queueId
                    )
                )
            }
        }

        mediaState.browsableTracks.filter {
            it.isPlayable && (KeyboardSearchMatcher.matches(it.title, trimmed) || KeyboardSearchMatcher.matches(it.subtitle, trimmed))
        }.take(10).forEach { bt ->
            allCandidates.add(
                TrackSourceCandidate(
                    id = "mb:${bt.packageName}:${bt.mediaId}",
                    sourceKind = MusicSourceKind.STREAMING_PAID,
                    sourceLabel = bt.appLabel,
                    title = bt.title,
                    artist = bt.subtitle,
                    packageName = bt.packageName,
                    fileExtension = "stream",
                    mediaBrowserTrack = bt
                )
            )
        }

        mediaState.recentTracks.filter {
            KeyboardSearchMatcher.matches(it.title, trimmed) ||
                KeyboardSearchMatcher.matches(it.subtitle, trimmed)
        }.take(8).forEach { rt ->
            allCandidates.add(
                TrackSourceCandidate(
                    id = "rec:${rt.packageName}:${rt.title}",
                    sourceKind = MusicSourceKind.STREAMING_PAID,
                    sourceLabel = rt.appLabel,
                    title = rt.title,
                    artist = rt.subtitle,
                    packageName = rt.packageName,
                    fileExtension = "stream"
                )
            )
        }

        // 3. Online Sources in Parallel: Archive.org (FLAC + MP3), iTunes Catalog, & Wikimedia Commons Audio
        coroutineScope {
            val archiveDeferred = async { searchArchiveOrgAudio(trimmed) }
            val itunesDeferred = async { searchItunesTracks(trimmed) }
            val wikimediaDeferred = async { searchWikimediaCommonsAudio(trimmed) }
            allCandidates.addAll(archiveDeferred.await())
            allCandidates.addAll(itunesDeferred.await())
            allCandidates.addAll(wikimediaDeferred.await())
        }

        // 4. Installed paid streaming bridges for top matches or direct search
        val streamingApps = installedMusicApps
            .filter { MediaControlRepository.isStreamingPackage(it.packageName) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<AppEntry> { MediaControlRepository.sortPriorityScore(it.packageName) })
            .take(4)

        val rawGrouped = groupAndSortCandidates(allCandidates, streamingApps, trimmed)
        rawGrouped
    }

    internal fun groupAndSortCandidates(
        candidates: List<TrackSourceCandidate>,
        streamingApps: List<AppEntry> = emptyList(),
        query: String = ""
    ): List<UnifiedMusicSearchResult> {
        val groups = LinkedHashMap<String, MutableList<TrackSourceCandidate>>()

        candidates.forEach { c ->
            val normTitle = normalizeKeyText(c.title).ifBlank { c.title.lowercase(Locale.ROOT) }
            val normArtist = normalizeKeyText(c.artist)
            val existingKey = groups.keys.firstOrNull { key ->
                val parts = key.split('|', limit = 2)
                val kArtist = parts.getOrElse(0) { "" }
                val kTitle = parts.getOrElse(1) { "" }
                kTitle == normTitle && (normArtist.isEmpty() || kArtist.isEmpty() || kArtist == normArtist ||
                    kArtist.contains(normArtist) || normArtist.contains(kArtist))
            }
            val key = existingKey ?: "$normArtist|$normTitle"
            groups.getOrPut(key) { mutableListOf() }.add(c)
        }

        val results = groups.entries.map { (key, list) ->
            // Attach paid streaming service search/capture options if none present yet for online-discovered songs
            val hasStreamOption = list.any { it.sourceKind == MusicSourceKind.STREAMING_PAID }
            val augmented = if (!hasStreamOption && streamingApps.isNotEmpty()) {
                val rep = list.first()
                list + streamingApps.take(2).map { app ->
                    TrackSourceCandidate(
                        id = "stream_bridge:${app.packageName}:${rep.title}:${rep.artist}",
                        sourceKind = MusicSourceKind.STREAMING_PAID,
                        sourceLabel = app.label,
                        title = rep.title,
                        artist = rep.artist,
                        album = rep.album,
                        durationMs = rep.durationMs,
                        packageName = app.packageName,
                        fileExtension = "stream",
                        artworkUrl = rep.artworkUrl
                    )
                }
            } else {
                list
            }

            val sorted = augmented.distinctBy { "${it.sourceKind}:${it.sourceLabel}:${it.downloadUrl ?: it.id}" }
                .sortedWith(
                    compareBy<TrackSourceCandidate> { it.sourceKind.priorityOrder }
                        .thenByDescending { it.isLossless }
                        .thenByDescending { it.bitrateKbps }
                )

            val bestTitle = sorted.firstOrNull { it.title.isNotBlank() }?.title ?: query
            val bestArtist = sorted.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty()
            val bestAlbum = sorted.firstOrNull { it.album.isNotBlank() }?.album.orEmpty()
            val bestDuration = sorted.firstOrNull { it.durationMs > 0L }?.durationMs ?: 0L
            val bestArt = sorted.firstNotNullOfOrNull { it.artworkUrl }

            UnifiedMusicSearchResult(
                key = key,
                title = bestTitle,
                artist = bestArtist,
                album = bestAlbum,
                durationMs = bestDuration,
                artworkUrl = bestArt,
                candidates = sorted
            )
        }.sortedWith(
            compareBy<UnifiedMusicSearchResult> { it.primaryCandidate?.sourceKind?.priorityOrder ?: 99 }
                .thenByDescending { it.hasMultipleSources }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )

        // If online queries returned nothing (offline device) still provide direct search triggers on installed streaming apps
        if (results.isEmpty() && streamingApps.isNotEmpty() && query.isNotBlank()) {
            return listOf(
                UnifiedMusicSearchResult(
                    key = "direct|$query",
                    title = query,
                    artist = "Прямой поиск по стримингам",
                    candidates = streamingApps.map { app ->
                        TrackSourceCandidate(
                            id = "stream_direct:${app.packageName}:$query",
                            sourceKind = MusicSourceKind.STREAMING_PAID,
                            sourceLabel = app.label,
                            title = query,
                            artist = app.label,
                            packageName = app.packageName,
                            fileExtension = "stream"
                        )
                    }
                )
            )
        }
        return results
    }

    private fun searchArchiveOrgAudio(query: String): List<TrackSourceCandidate> {
        return runCatching {
            val encodedQ = URLEncoder.encode("($query) AND mediatype:(audio OR etree)", "UTF-8")
            val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,creator&rows=6&output=json"
            val raw = httpGetText(searchUrl) ?: return emptyList()
            val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            for (i in 0 until minOf(docs.length(), 4)) {
                val doc = docs.optJSONObject(i) ?: continue
                val id = doc.optString("identifier").trim()
                if (id.isEmpty()) continue
                val itemTitle = doc.optString("title").trim().ifBlank { id }
                val itemArtist = doc.optString("creator").trim()
                val metaRaw = httpGetText("https://archive.org/metadata/$id") ?: continue
                out.addAll(
                    parseArchiveMetadataFiles(
                        id = id,
                        itemTitle = itemTitle,
                        itemArtist = itemArtist,
                        metaRaw = metaRaw,
                        maxResults = (24 - out.size).coerceAtMost(16)
                    )
                )
                if (out.size >= 24) break
            }
            out
        }.getOrDefault(emptyList())
    }

    internal fun parseArchiveMetadataFiles(
        id: String,
        itemTitle: String,
        itemArtist: String,
        metaRaw: String,
        maxResults: Int = 24
    ): List<TrackSourceCandidate> {
        if (maxResults <= 0) return emptyList()
        return runCatching {
            val files = JSONObject(metaRaw).optJSONArray("files") ?: return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            val seenInItem = HashSet<String>()
            for (fIdx in 0 until files.length()) {
                val f = files.optJSONObject(fIdx) ?: continue
                val name = f.optString("name")
                val lower = name.lowercase(Locale.ROOT)
                val isFlac = lower.endsWith(".flac")
                val isLossy = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg")
                if (!isFlac && !isLossy) continue
                val trackTitle = f.optString("title").trim()
                    .ifBlank { name.substringBeforeLast('.').substringAfterLast('/') }
                    .ifBlank { itemTitle }
                val dedupeKey = "${normalizeKeyText(trackTitle)}|$isFlac"
                if (!seenInItem.add(dedupeKey)) continue
                val trackArtist = f.optString("artist").trim()
                    .ifBlank { f.optString("creator").trim() }
                    .ifBlank { itemArtist }
                val durSec = f.optString("length").toDoubleOrNull() ?: 0.0
                val size = f.optString("size").toLongOrNull() ?: 0L
                val safeFileUrl = "https://archive.org/download/$id/${Uri.encode(name, "/")}"
                val ext = lower.substringAfterLast('.')
                out.add(
                    TrackSourceCandidate(
                        id = "arch:$id:$name",
                        sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                        sourceLabel = if (isFlac) "Archive.org FLAC" else "Archive.org ${ext.uppercase(Locale.ROOT)}",
                        title = trackTitle,
                        artist = trackArtist,
                        album = itemTitle,
                        durationMs = (durSec * 1000.0).toLong().coerceAtLeast(0L),
                        downloadUrl = safeFileUrl,
                        previewUrl = safeFileUrl,
                        fileExtension = ext,
                        bitrateKbps = if (isFlac) 1000 else 320,
                        sampleRateHz = 44100,
                        sizeBytes = size
                    )
                )
                if (out.size >= maxResults) break
            }
            out
        }.getOrDefault(emptyList())
    }

    fun searchArchiveOrgAlbumFiles(albumTitle: String, artist: String): List<TrackSourceCandidate> {
        val query = listOf(artist.trim(), albumTitle.trim()).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return emptyList()
        return searchArchiveOrgAudio(query)
    }

    internal fun parseItunesTracksJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("trackName").trim()
                    if (title.isEmpty()) continue
                    val artist = o.optString("artistName").trim()
                    val album = o.optString("collectionName").trim()
                    val durationMs = o.optLong("trackTimeMillis", 0L)
                    val art = o.optString("artworkUrl100").trim()
                        .replace("100x100bb", "1200x1200bb")
                        .takeIf { it.isNotBlank() }
                    val previewUrl = o.optString("previewUrl").trim().takeIf { it.isNotBlank() }
                    val trackId = o.optLong("trackId", i.toLong())
                    add(
                        TrackSourceCandidate(
                            id = "itunes:$trackId",
                            sourceKind = MusicSourceKind.ITUNES_CATALOG,
                            sourceLabel = "iTunes Каталог",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            downloadUrl = null,
                            previewUrl = previewUrl,
                            fileExtension = "m4a",
                            bitrateKbps = 256,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun searchItunesTracks(query: String): List<TrackSourceCandidate> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = httpGetText("https://itunes.apple.com/search?term=$encoded&entity=song&limit=10") ?: return emptyList()
        return parseItunesTracksJson(body)
    }

    internal fun searchWikimediaCommonsAudio(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode("$clean filetype:audio", "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=$encoded&gsrlimit=6&prop=imageinfo&iiprop=url|mime|size&format=json"
            val raw = httpGetText(url) ?: return emptyList()
            parseWikimediaCommonsJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseWikimediaCommonsJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val pages = JSONObject(rawJson).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val page = pages.optJSONObject(key) ?: continue
                val titleRaw = page.optString("title").removePrefix("File:").trim()
                val ext = titleRaw.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext !in setOf("flac", "ogg", "oga", "opus", "mp3", "wav", "m4a")) continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val fileUrl = info.optString("url").trim().takeIf { it.startsWith("http") } ?: continue
                val sizeBytes = info.optLong("size", 0L)
                val cleanName = titleRaw.substringBeforeLast('.').replace('_', ' ').trim()
                val isFlac = ext == "flac" || ext == "wav"
                out.add(
                    TrackSourceCandidate(
                        id = "wiki:$key:$titleRaw",
                        sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                        sourceLabel = "Wikimedia ${ext.uppercase(Locale.ROOT)}",
                        title = cleanName,
                        artist = "",
                        album = "Wikimedia Commons",
                        downloadUrl = fileUrl,
                        previewUrl = fileUrl,
                        fileExtension = ext,
                        bitrateKbps = if (isFlac) 1000 else 192,
                        sampleRateHz = 44100,
                        sizeBytes = sizeBytes
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------------
    // Priority Download Logic: FLAC -> Paid Streaming Capture Bridge -> MP3/M4A
    // ------------------------------------------------------------------------

    suspend fun downloadTrackByPriority(
        context: Context,
        title: String,
        artist: String,
        candidates: List<TrackSourceCandidate>,
        installedMusicApps: List<AppEntry>,
        allowStreamingCapture: Boolean = true,
        extraCandidates: List<TrackSourceCandidate> = emptyList()
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val merged = (candidates + extraCandidates)
            .distinctBy { "${it.sourceKind}:${it.downloadUrl ?: it.id}" }
        val sorted = merged.sortedBy { it.sourceKind.priorityOrder }

        // Check if already present as local lossless
        val existingLocalFlac = sorted.firstOrNull { it.sourceKind == MusicSourceKind.LOCAL_FLAC }?.localTrack
        if (existingLocalFlac != null) {
            return@withContext DownloadOutcome.DownloadedFile(existingLocalFlac, "FLAC уже на устройстве: ${existingLocalFlac.title}")
        }

        // Priority A: Direct FLAC URL download
        val flacRemote = sorted.firstOrNull { it.sourceKind == MusicSourceKind.ARCHIVE_FLAC && !it.downloadUrl.isNullOrBlank() }
        if (flacRemote != null && !flacRemote.downloadUrl.isNullOrBlank()) {
            val saved = downloadDirectFile(context, flacRemote.downloadUrl, flacRemote.title, flacRemote.artist, flacRemote.album, "flac")
            if (saved != null) {
                return@withContext DownloadOutcome.DownloadedFile(saved, "Скачан FLAC: ${saved.title}")
            }
        }

        // Search Archive.org specifically for FLAC/Lossy if candidates list didn't include remote results
        var discoveredArchive: List<TrackSourceCandidate> = emptyList()
        if (flacRemote == null) {
            val probeQuery = "$artist $title".trim()
            discoveredArchive = searchArchiveOrgAudio(probeQuery)
            val foundFlac = discoveredArchive.firstOrNull { it.sourceKind == MusicSourceKind.ARCHIVE_FLAC && !it.downloadUrl.isNullOrBlank() }
            if (foundFlac != null && !foundFlac.downloadUrl.isNullOrBlank()) {
                val saved = downloadDirectFile(context, foundFlac.downloadUrl, title, artist, foundFlac.album, "flac")
                if (saved != null) {
                    return@withContext DownloadOutcome.DownloadedFile(saved, "Скачан FLAC: ${saved.title}")
                }
            }
        }

        val streamCandidate = sorted.firstOrNull { it.sourceKind == MusicSourceKind.STREAMING_PAID && !it.packageName.isNullOrBlank() }
        val fallbackStreamingApp = installedMusicApps
            .filter { MediaControlRepository.isStreamingPackage(it.packageName) }
            .minByOrNull { MediaControlRepository.sortPriorityScore(it.packageName) }
        val targetPkg = streamCandidate?.packageName ?: fallbackStreamingApp?.packageName
        val targetLabel = streamCandidate?.sourceLabel ?: fallbackStreamingApp?.label ?: "Стриминг"

        // Priority B (when interactive): Paid / Installed Streaming App Audio Capture Bridge
        if (allowStreamingCapture && !targetPkg.isNullOrBlank()) {
            return@withContext DownloadOutcome.NeedsStreamingCapture(
                title = title,
                artist = artist,
                packageName = targetPkg,
                appLabel = targetLabel
            )
        }

        // Priority C: Direct Lossy (MP3 / OGG / M4A) fallback (from existing candidates, archive discovery, or iTunes)
        val combinedLossyPool = sorted + discoveredArchive
        val lossyRemote = combinedLossyPool.firstOrNull {
            (it.sourceKind == MusicSourceKind.ARCHIVE_LOSSY || it.sourceKind == MusicSourceKind.ITUNES_CATALOG) &&
                !it.downloadUrl.isNullOrBlank()
        } ?: searchItunesTracks("$artist $title".trim()).firstOrNull { !it.downloadUrl.isNullOrBlank() }

        if (lossyRemote != null && !lossyRemote.downloadUrl.isNullOrBlank()) {
            val ext = lossyRemote.fileExtension.ifBlank { "mp3" }
            val saved = downloadDirectFile(context, lossyRemote.downloadUrl, title, artist, lossyRemote.album, ext)
            if (saved != null) {
                return@withContext DownloadOutcome.DownloadedFile(saved, "Скачан ${ext.uppercase(Locale.ROOT)}: ${saved.title}")
            }
        }

        if (!allowStreamingCapture && !targetPkg.isNullOrBlank()) {
            return@withContext DownloadOutcome.NeedsStreamingCapture(
                title = title,
                artist = artist,
                packageName = targetPkg,
                appLabel = targetLabel
            )
        }

        DownloadOutcome.Failed("Не найден прямой FLAC/MP3 источник для «$title»")
    }

    suspend fun downloadDirectCandidate(
        context: Context,
        candidate: TrackSourceCandidate
    ): LocalAudioTrack? = withContext(Dispatchers.IO) {
        val url = candidate.downloadUrl ?: return@withContext null
        val ext = candidate.fileExtension.ifBlank { "mp3" }
        downloadDirectFile(context, url, candidate.title, candidate.artist, candidate.album, ext)
    }

    private fun downloadDirectFile(
        context: Context,
        urlString: String,
        title: String,
        artist: String,
        album: String,
        extension: String
    ): LocalAudioTrack? {
        var conn: HttpURLConnection? = null
        return try {
            val targetDir = runCatching {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                File(musicDir, "KeyDeck").apply { mkdirs() }
            }.getOrNull()?.takeIf { it.exists() && it.canWrite() }
                ?: File(context.filesDir, "music").apply { mkdirs() }

            val rawName = if (artist.isNotBlank()) "$artist - $title" else title
            val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(72).ifBlank { "track_${System.currentTimeMillis()}" }
            var outFile = File(targetDir, "$safeName.$extension")
            if (outFile.exists()) {
                outFile = File(targetDir, "${safeName}_${System.currentTimeMillis() % 10000}.$extension")
            }

            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                    }
                }
            }
            if (outFile.length() < 1024L) {
                outFile.delete()
                return null
            }

            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("audio/$extension"),
                    null
                )
            }

            LocalAudioTrack(
                id = "fs:${outFile.absolutePath}",
                title = title.ifBlank { outFile.nameWithoutExtension },
                artist = artist,
                album = album,
                durationMs = 0L,
                uriString = Uri.fromFile(outFile).toString(),
                filePath = outFile.absolutePath,
                displayPath = outFile.absolutePath,
                isTelegramAudio = false,
                formatLabel = extension.uppercase(Locale.ROOT),
                bitrateKbps = if (extension.equals("flac", true)) 1000 else 320,
                sampleRateHz = 44100
            )
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------------
    // Synced Lyrics (LRCLIB + Sidecar .lrc Parser / Serializer)
    // ------------------------------------------------------------------------

    fun parseLrc(rawLrc: String): List<SyncedLyricLine> {
        if (rawLrc.isBlank()) return emptyList()
        val parsed = mutableListOf<SyncedLyricLine>()
        rawLrc.lines().forEach { line ->
            val matches = LRC_TAG_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.replace(LRC_TAG_REGEX, "").trim()
            if (text.isBlank()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues.getOrNull(3).orEmpty()
                val ms = when (fracRaw.length) {
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fracRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = min * 60_000L + sec * 1000L + ms
                parsed.add(SyncedLyricLine(totalMs, text))
            }
        }
        return parsed.sortedBy { it.timeMs }
    }

    fun formatLrcTimestamp(timeMs: Long): String {
        val clamped = timeMs.coerceAtLeast(0L)
        val totalSec = clamped / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val centi = (clamped % 1000L) / 10L
        return "[%02d:%02d.%02d]".format(Locale.US, min, sec, centi)
    }

    fun serializeLrc(lines: List<SyncedLyricLine>): String {
        return lines.sortedBy { it.timeMs }
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { "${formatLrcTimestamp(it.timeMs)} ${it.text.trim()}" }
    }

    suspend fun fetchSyncedLyrics(
        title: String,
        artist: String,
        durationMs: Long = 0L,
        localFilePath: String? = null,
        attemptIndex: Int = 0,
        excludeRawLrc: String? = null
    ): TrackLyricsData? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("""\([^)]*remaster[^)]*\)""", RegexOption.IGNORE_CASE), "").trim()
        val trackKey = buildTrackKey(title, artist)
        val forceAlternate = attemptIndex > 0 || !excludeRawLrc.isNullOrBlank()

        // 1. Check local sidecar .lrc next to audio file on initial lookup only
        if (!forceAlternate && !localFilePath.isNullOrBlank()) {
            val lrcFile = sidecarLrcFile(localFilePath)
            if (lrcFile != null && lrcFile.exists() && lrcFile.canRead()) {
                val raw = runCatching { lrcFile.readText() }.getOrNull().orEmpty()
                if (raw.isNotBlank()) {
                    val synced = parseLrc(raw)
                    return@withContext TrackLyricsData(
                        trackKey = trackKey,
                        title = title,
                        artist = artist,
                        syncedLines = synced,
                        plainText = if (synced.isEmpty()) raw else synced.joinToString("\n") { it.text },
                        rawLrc = raw,
                        sourceLabel = "Файл .lrc"
                    )
                }
            }
        }

        val candidates = mutableListOf<TrackLyricsData>()
        val seenRaw = HashSet<String>()
        val normExcluded = excludeRawLrc?.trim().orEmpty()

        fun addCandidateIfNovel(item: TrackLyricsData?) {
            if (item == null || item.rawLrc.isBlank()) return
            val sig = item.rawLrc.trim()
            if (sig == normExcluded) return
            if (seenRaw.add(sig)) candidates.add(item)
        }

        // 2. Query LRCLIB /api/get
        val encodedTitle = URLEncoder.encode(cleanTitle.ifBlank { title }, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")
        if (artist.isNotBlank() && attemptIndex == 0) {
            val durParam = if (durationMs > 1000L) "&duration=${durationMs / 1000L}" else ""
            val directJson = httpGetText("https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle$durParam")
            addCandidateIfNovel(parseLrclibSingleJson(directJson, trackKey, title, artist))
        }

        // 3. LRCLIB /api/search?q= (collect all distinct matches so retry cycles sources)
        val searchQ = URLEncoder.encode("$artist $cleanTitle".trim(), "UTF-8")
        val searchJson = httpGetText("https://lrclib.net/api/search?q=$searchQ")
        parseLrclibSearchCandidates(searchJson, trackKey, title, artist).forEach(::addCandidateIfNovel)

        // 4. Fallback secondary source: lyrics.ovh (when retrying or when LRCLIB had no match)
        if (artist.isNotBlank() && (candidates.isEmpty() || attemptIndex >= candidates.size)) {
            val pathArtist = Uri.encode(artist.trim())
            val pathTitle = Uri.encode(cleanTitle.ifBlank { title })
            val ovhJson = httpGetText("https://api.lyrics.ovh/v1/$pathArtist/$pathTitle")
            addCandidateIfNovel(parseLyricsOvhJson(ovhJson, trackKey, title, artist))
        }

        if (candidates.isEmpty()) return@withContext null
        val pickIdx = if (attemptIndex <= 0) 0 else (attemptIndex % candidates.size)
        candidates[pickIdx]
    }

    internal fun parseLrclibSingleJson(
        rawJson: String?,
        trackKey: String,
        title: String,
        artist: String
    ): TrackLyricsData? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(rawJson)
            val syncedRaw = o.optString("syncedLyrics", "").trim()
            val plainRaw = o.optString("plainLyrics", "").trim()
            if (syncedRaw.isBlank() && plainRaw.isBlank()) return null
            val synced = parseLrc(syncedRaw)
            val effectiveRaw = syncedRaw.ifBlank { plainRaw }
            TrackLyricsData(
                trackKey = trackKey,
                title = o.optString("trackName", title).ifBlank { title },
                artist = o.optString("artistName", artist).ifBlank { artist },
                syncedLines = synced,
                plainText = plainRaw.ifBlank { synced.joinToString("\n") { it.text } },
                rawLrc = effectiveRaw,
                sourceLabel = "LRCLIB"
            )
        }.getOrNull()
    }

    internal fun parseLrclibSearchJson(
        rawJson: String?,
        trackKey: String,
        title: String,
        artist: String
    ): TrackLyricsData? {
        return parseLrclibSearchCandidates(rawJson, trackKey, title, artist).firstOrNull()
    }

    internal fun parseLrclibSearchCandidates(
        rawJson: String?,
        trackKey: String,
        title: String,
        artist: String
    ): List<TrackLyricsData> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            val syncedList = mutableListOf<TrackLyricsData>()
            val plainList = mutableListOf<TrackLyricsData>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val syncedRaw = o.optString("syncedLyrics", "").trim()
                val plainRaw = o.optString("plainLyrics", "").trim()
                val tName = o.optString("trackName", title).ifBlank { title }
                val aName = o.optString("artistName", artist).ifBlank { artist }
                val synced = if (syncedRaw.isNotBlank()) parseLrc(syncedRaw) else emptyList()
                if (synced.isNotEmpty()) {
                    syncedList.add(
                        TrackLyricsData(
                            trackKey = trackKey,
                            title = tName,
                            artist = aName,
                            syncedLines = synced,
                            plainText = plainRaw.ifBlank { synced.joinToString("\n") { it.text } },
                            rawLrc = syncedRaw,
                            sourceLabel = "LRCLIB #${syncedList.size + 1}"
                        )
                    )
                } else if (plainRaw.isNotBlank()) {
                    plainList.add(
                        TrackLyricsData(
                            trackKey = trackKey,
                            title = tName,
                            artist = aName,
                            syncedLines = emptyList(),
                            plainText = plainRaw,
                            rawLrc = plainRaw,
                            sourceLabel = "LRCLIB (Текст)"
                        )
                    )
                }
            }
            syncedList + plainList
        }.getOrDefault(emptyList())
    }

    internal fun parseLyricsOvhJson(
        rawJson: String?,
        trackKey: String,
        title: String,
        artist: String
    ): TrackLyricsData? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val lyrics = JSONObject(rawJson).optString("lyrics", "").trim()
            if (lyrics.isBlank()) return null
            val synced = parseLrc(lyrics)
            TrackLyricsData(
                trackKey = trackKey,
                title = title,
                artist = artist,
                syncedLines = synced,
                plainText = if (synced.isNotEmpty()) synced.joinToString("\n") { it.text } else lyrics,
                rawLrc = lyrics,
                sourceLabel = "Lyrics.ovh"
            )
        }.getOrNull()
    }

    fun saveSidecarLrc(localFilePath: String?, rawLrc: String): Boolean {
        val file = sidecarLrcFile(localFilePath) ?: return false
        return runCatching {
            file.writeText(rawLrc)
            true
        }.getOrDefault(false)
    }

    fun deleteSidecarLrc(localFilePath: String?) {
        val file = sidecarLrcFile(localFilePath) ?: return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun sidecarLrcFile(audioPath: String?): File? {
        if (audioPath.isNullOrBlank()) return null
        val f = File(audioPath)
        val parent = f.parentFile ?: return null
        if (!parent.exists()) return null
        return File(parent, "${f.nameWithoutExtension}.lrc")
    }

    // ------------------------------------------------------------------------
    // Smart Album Builder (MusicBrainz + Cover Art Archive + iTunes Search)
    // ------------------------------------------------------------------------

    suspend fun fetchAlbumBlueprint(
        albumTitle: String,
        artistHint: String = ""
    ): AlbumBlueprintResult? = withContext(Dispatchers.IO) {
        val cleanAlbum = albumTitle.trim()
        if (cleanAlbum.isEmpty()) return@withContext null

        coroutineScope {
            val itunesDeferred = async { fetchAlbumFromItunes(cleanAlbum, artistHint) }
            val mbDeferred = async { fetchAlbumFromMusicBrainz(cleanAlbum, artistHint) }

            val itunes = itunesDeferred.await()
            val mb = mbDeferred.await()

            when {
                mb != null && mb.expectedTracks.isNotEmpty() -> {
                    val altCovers = (mb.alternateCoverUrls + listOfNotNull(itunes?.coverUrl) + (itunes?.alternateCoverUrls.orEmpty()))
                        .distinct()
                        .take(8)
                    val primaryCover = mb.coverUrl ?: itunes?.coverUrl ?: altCovers.firstOrNull()
                    mb.copy(
                        coverUrl = primaryCover,
                        alternateCoverUrls = altCovers,
                        artist = mb.artist.ifBlank { itunes?.artist.orEmpty() }
                    )
                }
                itunes != null -> itunes
                else -> mb
            }
        }
    }

    internal fun fetchAlbumFromItunes(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val query = URLEncoder.encode("$artistHint $albumTitle".trim(), "UTF-8")
            val searchJson = httpGetText("https://itunes.apple.com/search?term=$query&entity=album&limit=8")
                ?: return null
            val arr = JSONObject(searchJson).optJSONArray("results") ?: return null
            if (arr.length() == 0) return null

            val normQuery = normalizeKeyText(albumTitle)
            var bestAlbumObj = arr.optJSONObject(0) ?: return null
            val altCovers = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val art = o.optString("artworkUrl100").replace("100x100bb", "1200x1200bb").trim()
                if (art.isNotBlank()) altCovers.add(art)
                if (normalizeKeyText(o.optString("collectionName")) == normQuery) {
                    bestAlbumObj = o
                }
            }

            val collectionId = bestAlbumObj.optLong("collectionId", 0L)
            if (collectionId == 0L) return null
            val primaryArt = bestAlbumObj.optString("artworkUrl100").replace("100x100bb", "1200x1200bb").trim()
                .takeIf { it.isNotBlank() }

            val lookupJson = httpGetText("https://itunes.apple.com/lookup?id=$collectionId&entity=song")
                ?: return null
            parseItunesAlbumLookupJson(lookupJson, altCovers, primaryArt)
        }.getOrNull()
    }

    internal fun parseItunesAlbumLookupJson(
        rawJson: String,
        alternateCovers: List<String> = emptyList(),
        fallbackArt: String? = null
    ): AlbumBlueprintResult? {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("results") ?: return null
            var colTitle = ""
            var colArtist = ""
            var trackCount = 0
            var colId = ""
            var colArt = fallbackArt
            val tracks = mutableListOf<AlbumExpectedTrack>()

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val wrapper = o.optString("wrapperType")
                if (wrapper == "collection") {
                    colId = "itunes:${o.optLong("collectionId")}"
                    colTitle = o.optString("collectionName").trim()
                    colArtist = o.optString("artistName").trim()
                    trackCount = o.optInt("trackCount", 0)
                    colArt = o.optString("artworkUrl100").replace("100x100bb", "1200x1200bb").trim()
                        .takeIf { it.isNotBlank() } ?: colArt
                } else if (wrapper == "track") {
                    val tName = o.optString("trackName").trim()
                    if (tName.isNotEmpty()) {
                        tracks.add(
                            AlbumExpectedTrack(
                                trackNumber = o.optInt("trackNumber", tracks.size + 1),
                                title = tName,
                                artist = o.optString("artistName", colArtist).trim(),
                                durationMs = o.optLong("trackTimeMillis", 0L)
                            )
                        )
                    }
                }
            }
            if (colTitle.isBlank() && tracks.isEmpty()) return null
            AlbumBlueprintResult(
                releaseId = colId.ifBlank { "itunes" },
                title = colTitle,
                artist = colArtist,
                expectedTrackCount = maxOf(trackCount, tracks.size),
                expectedTracks = tracks.sortedBy { it.trackNumber },
                coverUrl = colArt,
                alternateCoverUrls = (listOfNotNull(colArt) + alternateCovers).distinct()
            )
        }.getOrNull()
    }

    private fun fetchAlbumFromMusicBrainz(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val cleanTitle = albumTitle.replace("\"", "").trim()
            val cleanArtist = artistHint.replace("\"", "").trim()

            val queries = buildList {
                add("release:\"$cleanTitle\"" + if (cleanArtist.isNotBlank()) " AND artist:\"$cleanArtist\"" else "")
                add("release:$cleanTitle" + if (cleanArtist.isNotBlank()) " AND artist:$cleanArtist" else "")
                add("$cleanTitle $cleanArtist".trim())
            }

            var releases: JSONArray? = null
            for (q in queries) {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val searchJson = httpGetText("https://musicbrainz.org/ws/2/release/?query=$encoded&limit=5&fmt=json")
                val arr = searchJson?.let { JSONObject(it).optJSONArray("releases") }
                if (arr != null && arr.length() > 0) {
                    releases = arr
                    break
                }
            }
            val finalReleases = releases ?: return@runCatching null
            if (finalReleases.length() == 0) return@runCatching null

            val chosen = finalReleases.optJSONObject(0) ?: return@runCatching null
            val mbid = chosen.optString("id").trim()
            if (mbid.isEmpty()) return@runCatching null

            val detailJson = httpGetText("https://musicbrainz.org/ws/2/release/$mbid?inc=recordings+artists&fmt=json")
                ?: return@runCatching null
            val detail = JSONObject(detailJson)
            val title = detail.optString("title", albumTitle).trim()
            val artistCredit = detail.optJSONArray("artist-credit")
                ?.optJSONObject(0)
                ?.optString("name")
                ?.trim()
                .orEmpty()
                .ifBlank { artistHint }

            val mediaArr = detail.optJSONArray("media") ?: JSONArray()
            val tracks = mutableListOf<AlbumExpectedTrack>()
            for (mIdx in 0 until mediaArr.length()) {
                val m = mediaArr.optJSONObject(mIdx) ?: continue
                val tArr = m.optJSONArray("tracks") ?: continue
                for (tIdx in 0 until tArr.length()) {
                    val t = tArr.optJSONObject(tIdx) ?: continue
                    val tTitle = t.optString("title").trim()
                    if (tTitle.isNotEmpty()) {
                        tracks.add(
                            AlbumExpectedTrack(
                                trackNumber = tracks.size + 1,
                                title = tTitle,
                                artist = artistCredit,
                                durationMs = t.optLong("length", 0L)
                            )
                        )
                    }
                }
            }
            val coverUrl = "https://coverartarchive.org/release/$mbid/front-500"
            val altCovers = mutableListOf<String>()
            for (i in 0 until finalReleases.length()) {
                val rid = finalReleases.optJSONObject(i)?.optString("id")?.trim().orEmpty()
                if (rid.isNotEmpty()) {
                    altCovers.add("https://coverartarchive.org/release/$rid/front-500")
                }
            }

            AlbumBlueprintResult(
                releaseId = "mb:$mbid",
                title = title,
                artist = artistCredit,
                expectedTrackCount = tracks.size.coerceAtLeast(chosen.optInt("track-count", 0)),
                expectedTracks = tracks,
                coverUrl = coverUrl,
                alternateCoverUrls = altCovers.distinct()
            )
        }.getOrNull()
    }

    fun reconcileAlbumTracks(
        album: SmartAlbum,
        allLocalTracks: List<LocalAudioTrack>
    ): SmartAlbum {
        val tracksById = allLocalTracks.associateBy { it.id }
        val attached = album.attachedLocalTrackIds.mapNotNull { tracksById[it] }
        val autoMatchedByAlbumName = if (album.title.isNotBlank()) {
            val normAlbum = normalizeKeyText(album.title)
            allLocalTracks.filter {
                val trackAlbum = normalizeKeyText(it.album)
                trackAlbum.isNotEmpty() && (trackAlbum == normAlbum || trackAlbum.contains(normAlbum))
            }
        } else {
            emptyList()
        }
        val pool = (attached + autoMatchedByAlbumName).distinctBy { it.id }
        val usedIds = HashSet<String>()

        val reconciled = album.expectedTracks.map { slot ->
            val slotNorm = normalizeKeyText(slot.title)
            val hit = pool.firstOrNull { lt ->
                !usedIds.contains(lt.id) && titlesMatch(slotNorm, normalizeKeyText(lt.title))
            } ?: attached.firstOrNull { lt ->
                !usedIds.contains(lt.id) && titlesMatch(slotNorm, normalizeKeyText(lt.title))
            }
            if (hit != null) {
                usedIds.add(hit.id)
                val ext = hit.filePath?.substringAfterLast('.', "")?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() } ?: hit.formatLabel.ifBlank { "AUDIO" }
                slot.copy(
                    matchedLocalTrackId = hit.id,
                    matchedLocalTitle = hit.title,
                    matchedFormat = ext
                )
            } else {
                slot.copy(matchedLocalTrackId = null, matchedLocalTitle = null, matchedFormat = null)
            }
        }

        val combinedAttachedIds = (album.attachedLocalTrackIds + usedIds).distinct()
        return album.copy(
            attachedLocalTrackIds = combinedAttachedIds,
            expectedTracks = reconciled
        )
    }

    private fun titlesMatch(expectedNorm: String, localNorm: String): Boolean {
        if (expectedNorm.isEmpty() || localNorm.isEmpty()) return false
        if (expectedNorm == localNorm) return true
        // Strip leading track numbers like "01 " or "1. "
        val strippedLocal = localNorm.replace(Regex("""^\d{1,3}\s+"""), "").trim()
        if (strippedLocal == expectedNorm) return true
        if (expectedNorm.length >= 4 && (strippedLocal.contains(expectedNorm) || expectedNorm.contains(strippedLocal))) {
            return true
        }
        return false
    }

    suspend fun importCustomCoverFromUri(
        context: Context,
        albumId: String,
        sourceUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val coversDir = File(context.filesDir, "album_covers").apply { mkdirs() }
            val outFile = File(coversDir, "cover_${albumId.replace(Regex("[^a-zA-Z0-9_]"), "_")}_${System.currentTimeMillis() % 10000}.jpg")
            val bmp = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: error("Не удалось открыть изображение обложки")
            val scaled = scaleCoverBitmap(bmp, 720)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            if (scaled !== bmp) bmp.recycle()
            artworkBitmapCache.remove(outFile.absolutePath)
            outFile.absolutePath
        }
    }

    suspend fun loadArtworkBitmap(pathOrUrl: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = pathOrUrl?.trim().orEmpty()
        if (key.isEmpty()) return@withContext null
        artworkBitmapCache.get(key)?.let { return@withContext it }

        val bmp: Bitmap? = if (key.startsWith("http://") || key.startsWith("https://")) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(key).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            } finally {
                conn?.disconnect()
            }
        } else {
            val f = File(key)
            if (f.exists() && f.canRead()) {
                runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            } else {
                null
            }
        }

        val img = bmp?.let { scaleCoverBitmap(it, 640).asImageBitmap() }
        if (img != null) {
            artworkBitmapCache.put(key, img)
        }
        img
    }

    private fun scaleCoverBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    private fun httpGetText(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
