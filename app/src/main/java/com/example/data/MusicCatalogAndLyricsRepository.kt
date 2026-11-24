package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
import kotlinx.coroutines.awaitAll
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
import kotlin.math.abs

sealed class DownloadOutcome {
    data class DownloadedFile(
        val track: LocalAudioTrack,
        val message: String,
        val coverPath: String? = null
    ) : DownloadOutcome()

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

data class InternetAudioSourceInfo(
    val id: String,
    val name: String,
    val domain: String,
    val priorityTag: String,
    val supportsFlac: Boolean
)

object MusicCatalogAndLyricsRepository {

    private const val USER_AGENT = "KeyDeckLauncher/1.0 (+https://keydeck.app)"
    private const val CONNECT_TIMEOUT_MS = 6500
    private const val READ_TIMEOUT_MS = 8500
    private val LRC_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    val internetAudioSources: List<InternetAudioSourceInfo> = listOf(
        InternetAudioSourceInfo("discogs", "Discogs.com", "discogs.com", "Discogs + FLAC/Vinyl/CD", supportsFlac = true),
        InternetAudioSourceInfo("archive_flac", "Archive.org FLAC", "archive.org", "FLAC Lossless", supportsFlac = true),
        InternetAudioSourceInfo("archive_etree", "Archive.org Live Concerts", "archive.org", "Live FLAC/SHN", supportsFlac = true),
        InternetAudioSourceInfo("archive_netlabels", "Archive.org Netlabels", "archive.org", "Netlabel FLAC/MP3", supportsFlac = true),
        InternetAudioSourceInfo("archive_opensource", "Archive.org Open Audio", "archive.org", "Open FLAC/MP3", supportsFlac = true),
        InternetAudioSourceInfo("archive_vinyl", "Archive.org Vinyl & 78rpm", "archive.org", "Vinyl Rip FLAC/MP3", supportsFlac = true),
        InternetAudioSourceInfo("archive_classical", "Archive.org Musopen / Classical", "archive.org", "Classical FLAC/OGG", supportsFlac = true),
        InternetAudioSourceInfo("archive_general", "Archive.org Audio", "archive.org", "MP3/M4A/OGG", supportsFlac = true),
        InternetAudioSourceInfo("wikimedia", "Wikimedia Commons Audio", "commons.wikimedia.org", "FLAC/WAV/OGG/OPUS", supportsFlac = true),
        InternetAudioSourceInfo("openverse", "Openverse Audio", "api.openverse.org", "FLAC/WAV/MP3/OGG", supportsFlac = true),
        InternetAudioSourceInfo("audius", "Audius Discovery", "discoveryprovider.audius.co", "MP3 Stream", supportsFlac = false),
        InternetAudioSourceInfo("hearthis", "Hearthis.at", "api-v2.hearthis.at", "Direct Stream / MP3", supportsFlac = false),
        InternetAudioSourceInfo("ccmixter", "ccMixter", "ccmixter.org", "Direct MP3/FLAC", supportsFlac = true),
        InternetAudioSourceInfo("jamendo", "Jamendo Music", "api.jamendo.com", "MP3 / FLAC", supportsFlac = true),
        InternetAudioSourceInfo("musicbrainz", "MusicBrainz + CoverArt", "musicbrainz.org", "Catalog + Covers", supportsFlac = false),
        InternetAudioSourceInfo("deezer", "Deezer Catalog", "api.deezer.com", "Catalog + Covers", supportsFlac = false),
        InternetAudioSourceInfo("itunes", "iTunes Catalog", "itunes.apple.com", "Catalog + Covers", supportsFlac = false)
    )

    private val artworkBitmapCache = object : LruCache<String, ImageBitmap>(48) {}

    private val ALLOWED_VERSION_SUFFIX_TOKENS = setOf(
        "live", "remaster", "remastered", "mix", "remix", "version", "ver",
        "edit", "radio", "acoustic", "demo", "instrumental", "bonus", "track",
        "pt", "part", "feat", "ft", "hq", "flac", "mp3", "stereo", "mono",
        "deluxe", "edition", "expanded", "anniversary", "lp", "cd", "vinyl",
        "vol", "volume", "disc", "лайв", "концерт", "ремастер", "акустика",
        "демо", "бонус", "версия", "инструментал", "микс"
    )

    private val SYNTHETIC_ALBUM_LABELS = setOf(
        "ccmixter", "wikimedia", "wikimedia commons", "archive", "archive org",
        "single", "telegram", "hearthis", "jamendo", "audius", "openverse",
        "various artists", "unknown", "unknown album"
    )

    fun normalizeKeyText(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    internal fun normalizeComparisonTitle(raw: String): String {
        return normalizeKeyText(raw)
            .replace('ё', 'е')
            .replace(Regex("""^\d{1,3}\s+"""), "")
            .trim()
    }

    internal fun normalizeArtistComparison(raw: String): String {
        return normalizeKeyText(raw)
            .replace('ё', 'е')
            .replace('ы', 'и')
            .replace('й', 'и')
            .trim()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    internal fun titlesMatchStrict(expectedTitle: String, candidateTitle: String): Boolean {
        val exp = normalizeComparisonTitle(expectedTitle)
        val cand = normalizeComparisonTitle(candidateTitle)
        if (exp.isEmpty() || cand.isEmpty()) return false
        if (exp == cand) return true

        val expTokens = exp.split(' ').filter { it.isNotBlank() }
        val candTokens = cand.split(' ').filter { it.isNotBlank() }
        if (expTokens.isEmpty() || candTokens.isEmpty()) return false

        val (shorter, longer) = if (expTokens.size <= candTokens.size) {
            expTokens to candTokens
        } else {
            candTokens to expTokens
        }

        // Shorter must match a contiguous slice (or prefix/suffix) of longer, and every extra token
        // must ONLY be a harmless release/version tag (never a distinct word like "Хрустальные" before "осколки")
        val longerJoined = longer.joinToString(" ")
        val shorterJoined = shorter.joinToString(" ")
        val containsContiguous = longerJoined == shorterJoined ||
            longerJoined.startsWith("$shorterJoined ") ||
            longerJoined.endsWith(" $shorterJoined") ||
            longerJoined.contains(" $shorterJoined ")
        if (!containsContiguous) return false

        val remaining = longer.toMutableList()
        for (tok in shorter) {
            val idx = remaining.indexOf(tok)
            if (idx < 0) return false
            remaining.removeAt(idx)
        }
        return remaining.all { extra ->
            extra in ALLOWED_VERSION_SUFFIX_TOKENS || extra.all { it.isDigit() }
        }
    }

    internal fun artistsMatch(expectedArtist: String, candidateArtist: String): Boolean {
        val expFull = normalizeArtistComparison(expectedArtist)
        val candFull = normalizeArtistComparison(candidateArtist)
        if (expFull.isEmpty() || candFull.isEmpty()) return false
        if (expFull == candFull) return true

        val splitRegex = Regex("""(?i)\s*(?:,|&|/|\+|feat\.?|ft\.?|при уч\.?|x|\u2014)\s*""")
        val expParts = expectedArtist.split(splitRegex).map(::normalizeArtistComparison).filter { it.isNotBlank() }
        val candParts = candidateArtist.split(splitRegex).map(::normalizeArtistComparison).filter { it.isNotBlank() }

        for (ep in expParts) {
            for (cp in candParts) {
                if (ep == cp) return true
                if (ep.length >= 5 && cp.length >= 5 && levenshteinDistance(ep, cp) <= 1) return true
            }
        }
        if (expFull.length >= 5 && candFull.length >= 5 && levenshteinDistance(expFull, candFull) <= 1) return true
        return false
    }

    internal fun albumsMatch(expectedAlbum: String, candidateAlbum: String): Boolean {
        val exp = normalizeComparisonTitle(expectedAlbum)
        val cand = normalizeComparisonTitle(candidateAlbum)
        if (exp.isEmpty() || cand.isEmpty()) return false
        if (exp == cand) return true
        val expTokens = exp.split(' ').filter { it.isNotBlank() }
        val candTokens = cand.split(' ').filter { it.isNotBlank() }
        val (shorter, longer) = if (expTokens.size <= candTokens.size) expTokens to candTokens else candTokens to expTokens
        val shorterStr = shorter.joinToString(" ")
        val longerStr = longer.joinToString(" ")
        if (!(longerStr.startsWith("$shorterStr ") || longerStr.endsWith(" $shorterStr") || longerStr.contains(" $shorterStr "))) {
            return false
        }
        val remaining = longer.toMutableList()
        for (t in shorter) {
            val idx = remaining.indexOf(t)
            if (idx < 0) return false
            remaining.removeAt(idx)
        }
        return remaining.all { it in ALLOWED_VERSION_SUFFIX_TOKENS || it.all { ch -> ch.isDigit() } }
    }

    private fun isSyntheticAlbumLabel(album: String): Boolean {
        val norm = normalizeKeyText(album)
        return norm.isEmpty() || norm in SYNTHETIC_ALBUM_LABELS
    }

    fun buildTrackKey(title: String, artist: String): String {
        val t = normalizeKeyText(title)
        val a = normalizeKeyText(artist)
        return if (a.isNotEmpty()) "$a|$t" else t
    }

    fun buildTrackCoverLookupKeys(trackId: String?, title: String, artist: String): List<String> {
        val out = mutableListOf<String>()
        trackId?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add("id:$it") }
        val fullKey = buildTrackKey(title, artist)
        if (fullKey.isNotEmpty()) out.add(fullKey)
        val titleOnly = normalizeKeyText(title)
        if (titleOnly.isNotEmpty() && titleOnly != fullKey) out.add(titleOnly)
        return out.distinct()
    }

    fun resolveTrackCoverPath(
        overrides: Map<String, String>,
        trackId: String?,
        title: String,
        artist: String
    ): String? {
        if (overrides.isEmpty()) return null
        val keys = buildTrackCoverLookupKeys(trackId, title, artist)
        for (k in keys) {
            val path = overrides[k]
            if (!path.isNullOrBlank()) return path
        }
        return null
    }

    internal fun computeQueryRelevanceScore(title: String, artist: String, query: String): Int {
        val normQuery = normalizeComparisonTitle(query)
        if (normQuery.isBlank()) return 0
        val normTitle = normalizeComparisonTitle(title)
        val normArtist = normalizeArtistComparison(artist)
        val fuzzyQuery = normalizeArtistComparison(query)
        val combined = listOf(normArtist, normTitle).filter { it.isNotBlank() }.joinToString(" ")
        val reverseCombined = listOf(normTitle, normArtist).filter { it.isNotBlank() }.joinToString(" ")

        var score = 0
        if (normTitle == normQuery) score += 280
        else if (titlesMatchStrict(query, title)) score += 235
        else if (combined == fuzzyQuery || reverseCombined == fuzzyQuery) score += 245
        else if (normTitle.startsWith("$normQuery ")) score += 135
        else if (normTitle.contains(normQuery)) score += 90
        else if (combined.contains(fuzzyQuery) || reverseCombined.contains(fuzzyQuery)) score += 110

        val queryTokens = normQuery.split(' ').filter { it.length >= 2 }
        val fuzzyQueryTokens = fuzzyQuery.split(' ').filter { it.length >= 2 }
        if (queryTokens.isNotEmpty()) {
            val titleTokens = normTitle.split(' ').filter { it.isNotBlank() }.toSet()
            val artistTokens = normArtist.split(' ').filter { it.isNotBlank() }.toSet()
            val allTokens = titleTokens + artistTokens
            var titleHits = 0
            var artistHits = 0
            var totalHits = 0
            queryTokens.forEachIndexed { qIdx, qt ->
                val fqt = fuzzyQueryTokens.getOrElse(qIdx) { qt }
                val exactInTitle = titleTokens.any { it == qt }
                val prefixInTitle = titleTokens.any { it.startsWith(qt) || (qt.length >= 3 && it.contains(qt)) }
                val inArtist = artistTokens.any { it == fqt || it.startsWith(fqt) || (fqt.length >= 5 && levenshteinDistance(it, fqt) <= 1) }
                when {
                    exactInTitle -> {
                        score += 50
                        titleHits++
                        totalHits++
                    }
                    prefixInTitle -> {
                        score += 24
                        titleHits++
                        totalHits++
                    }
                    inArtist -> {
                        score += 36
                        artistHits++
                        totalHits++
                    }
                }
            }
            if (totalHits == queryTokens.size) score += 85
            if (titleHits == queryTokens.size && queryTokens.size == titleTokens.size) score += 75
            // Penalize extra unrelated words in title so exact match "Осколки" strongly outranks "Хрустальные осколки"
            val extraTitleTokens = titleTokens.count { tt ->
                tt !in ALLOWED_VERSION_SUFFIX_TOKENS &&
                    queryTokens.none { qt -> tt == qt || tt.startsWith(qt) }
            }
            if (extraTitleTokens > 0 && normTitle != normQuery) {
                score -= extraTitleTokens * 55
            }
            if (artist.isNotBlank() && artistHits == 0 && queryTokens.size >= 2 && titleHits < queryTokens.size) {
                score -= 70
            }
            if (allTokens.isNotEmpty() && totalHits == 0) score -= 60
        }
        return score
    }

    internal fun isDurationCompatible(expectedMs: Long, candidateMs: Long): Boolean {
        if (candidateMs <= 0L) return true
        if (expectedMs <= 0L) return candidateMs !in 1L..45_000L
        if (expectedMs >= 75_000L && candidateMs in 1L..45_000L) return false
        val tolerance = maxOf(30_000L, expectedMs * 28L / 100L)
        return abs(expectedMs - candidateMs) <= tolerance
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

        // 3. 17 Internet Sources in Parallel (Priority: FLAC + discogs.com)
        allCandidates.addAll(queryAllInternetAudioSources(trimmed))

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
            val normTitle = normalizeComparisonTitle(c.title).ifBlank { c.title.lowercase(Locale.ROOT) }
            val normArtist = normalizeArtistComparison(c.artist)
            val existingKey = groups.keys.firstOrNull { key ->
                val parts = key.split('|', limit = 2)
                val kArtist = parts.getOrElse(0) { "" }
                val kTitle = parts.getOrElse(1) { "" }
                kTitle == normTitle && (normArtist.isEmpty() || kArtist.isEmpty() || artistsMatch(kArtist, normArtist))
            }
            val key = existingKey ?: "$normArtist|$normTitle"
            groups.getOrPut(key) { mutableListOf() }.add(c)
        }

        val scoredResults = groups.entries.map { (key, list) ->
            val hasStreamOption = list.any { it.sourceKind == MusicSourceKind.STREAMING_PAID }
            val canonicalRep = list.firstOrNull {
                it.sourceKind == MusicSourceKind.DISCOGS_CATALOG ||
                    it.sourceKind == MusicSourceKind.LOCAL_FLAC ||
                    it.sourceKind == MusicSourceKind.LOCAL_FILE
            } ?: list.first()
            val augmented = if (!hasStreamOption && streamingApps.isNotEmpty()) {
                list + streamingApps.take(2).map { app ->
                    TrackSourceCandidate(
                        id = "stream_bridge:${app.packageName}:${canonicalRep.title}:${canonicalRep.artist}",
                        sourceKind = MusicSourceKind.STREAMING_PAID,
                        sourceLabel = app.label,
                        title = canonicalRep.title,
                        artist = canonicalRep.artist,
                        album = canonicalRep.album,
                        durationMs = canonicalRep.durationMs,
                        packageName = app.packageName,
                        fileExtension = "stream",
                        artworkUrl = canonicalRep.artworkUrl
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

            val bestTitle = canonicalRep.title.ifBlank { sorted.firstOrNull { it.title.isNotBlank() }?.title ?: query }
            val bestArtist = canonicalRep.artist.ifBlank { sorted.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty() }
            val bestAlbum = canonicalRep.album.ifBlank { sorted.firstOrNull { it.album.isNotBlank() }?.album.orEmpty() }
            val bestDuration = sorted.firstOrNull { it.durationMs > 0L }?.durationMs ?: 0L
            val bestArt = sorted.firstNotNullOfOrNull { it.artworkUrl }

            val res = UnifiedMusicSearchResult(
                key = key,
                title = bestTitle,
                artist = bestArtist,
                album = bestAlbum,
                durationMs = bestDuration,
                artworkUrl = bestArt,
                candidates = sorted
            )
            val relevance = computeQueryRelevanceScore(bestTitle, bestArtist, query)
            res to relevance
        }.sortedWith(
            compareByDescending<Pair<UnifiedMusicSearchResult, Int>> { it.second }
                .thenBy { it.first.primaryCandidate?.sourceKind?.priorityOrder ?: 99 }
                .thenByDescending { it.first.hasMultipleSources }
                .thenBy { it.first.title.lowercase(Locale.ROOT) }
        )

        val results = scoredResults.map { it.first }

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

    internal suspend fun queryAllInternetAudioSources(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val archiveMetaCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        return coroutineScope {
            val tasks = listOf(
                async { searchDiscogsTracks(clean) },
                async { searchArchiveOrgCollection(clean, "format:(Flac)", "Archive.org FLAC", archiveMetaCache, maxItems = 4) },
                async { searchArchiveOrgCollection(clean, "collection:(etree)", "Archive.org Live FLAC", archiveMetaCache, maxItems = 3) },
                async { searchArchiveOrgCollection(clean, "collection:(netlabels)", "Archive.org Netlabels", archiveMetaCache, maxItems = 2) },
                async { searchArchiveOrgCollection(clean, "collection:(opensource_audio)", "Archive.org Open Audio", archiveMetaCache, maxItems = 2) },
                async { searchArchiveOrgCollection(clean, "collection:(78rpm OR georgeblood)", "Archive.org Vinyl/78", archiveMetaCache, maxItems = 2) },
                async { searchArchiveOrgCollection(clean, "collection:(musopen OR classicalmusic)", "Archive.org Classical", archiveMetaCache, maxItems = 2) },
                async { searchArchiveOrgCollection(clean, "mediatype:(audio OR etree)", "Archive.org Audio", archiveMetaCache, maxItems = 3) },
                async { searchWikimediaCommonsAudio(clean) },
                async { searchOpenverseAudio(clean) },
                async { searchAudiusTracks(clean) },
                async { searchHearthisTracks(clean) },
                async { searchCcMixterTracks(clean) },
                async { searchJamendoTracks(clean) },
                async { searchMusicBrainzRecordings(clean) },
                async { searchDeezerTracks(clean) },
                async { searchItunesTracks(clean) }
            )
            tasks.awaitAll()
                .flatten()
                .distinctBy { "${it.sourceKind}:${it.sourceLabel}:${it.downloadUrl ?: it.id}" }
        }
    }

    private fun searchArchiveOrgAudio(query: String): List<TrackSourceCandidate> {
        val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val flacHits = searchArchiveOrgCollection(query, "format:(Flac)", "Archive.org FLAC", cache, maxItems = 3)
        val generalHits = searchArchiveOrgCollection(query, "mediatype:(audio OR etree)", "Archive.org Audio", cache, maxItems = 4)
        return (flacHits + generalHits).distinctBy { "${it.sourceKind}:${it.downloadUrl ?: it.id}" }
    }

    private fun searchArchiveOrgCollection(
        query: String,
        collectionFilter: String,
        collectionLabel: String,
        metaCache: java.util.concurrent.ConcurrentHashMap<String, String>,
        maxItems: Int = 3
    ): List<TrackSourceCandidate> {
        return runCatching {
            val encodedQ = URLEncoder.encode("($query) AND $collectionFilter", "UTF-8")
            val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,creator&rows=$maxItems&output=json"
            val raw = httpGetText(searchUrl) ?: return emptyList()
            val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            for (i in 0 until minOf(docs.length(), maxItems)) {
                val doc = docs.optJSONObject(i) ?: continue
                val id = doc.optString("identifier").trim()
                if (id.isEmpty()) continue
                val itemTitle = doc.optString("title").trim().ifBlank { id }
                val itemArtist = doc.optString("creator").trim()
                val metaRaw = metaCache[id] ?: httpGetText("https://archive.org/metadata/$id")?.also {
                    metaCache[id] = it
                } ?: continue
                out.addAll(
                    parseArchiveMetadataFiles(
                        id = id,
                        itemTitle = itemTitle,
                        itemArtist = itemArtist,
                        metaRaw = metaRaw,
                        maxResults = (24 - out.size).coerceAtMost(14),
                        sourceCollectionHint = collectionLabel
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
        maxResults: Int = 24,
        sourceCollectionHint: String? = null
    ): List<TrackSourceCandidate> {
        if (maxResults <= 0) return emptyList()
        return runCatching {
            val files = JSONObject(metaRaw).optJSONArray("files") ?: return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            val seenInItem = HashSet<String>()
            val colTag = sourceCollectionHint?.takeIf { it.isNotBlank() }
            for (fIdx in 0 until files.length()) {
                val f = files.optJSONObject(fIdx) ?: continue
                val name = f.optString("name")
                val lower = name.lowercase(Locale.ROOT)
                val isFlac = lower.endsWith(".flac") || lower.endsWith(".wav")
                val isLossy = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".opus")
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
                val label = when {
                    isFlac && colTag != null && colTag != "Archive.org FLAC" -> "$colTag FLAC"
                    isFlac -> "Archive.org FLAC"
                    colTag != null -> "$colTag ${ext.uppercase(Locale.ROOT)}"
                    else -> "Archive.org ${ext.uppercase(Locale.ROOT)}"
                }
                out.add(
                    TrackSourceCandidate(
                        id = "arch:$id:$name",
                        sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                        sourceLabel = label,
                        title = trackTitle,
                        artist = trackArtist,
                        album = itemTitle,
                        durationMs = (durSec * 1000.0).toLong().coerceAtLeast(0L),
                        downloadUrl = safeFileUrl,
                        previewUrl = safeFileUrl,
                        fileExtension = ext,
                        bitrateKbps = if (isFlac) 1000 else 320,
                        sampleRateHz = 44100,
                        sizeBytes = size,
                        artworkUrl = "https://archive.org/services/img/$id"
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

    // ------------------------------------------------------------------------
    // Discogs.com Search + Release Tracklist & Cover Resolution
    // ------------------------------------------------------------------------

    internal fun parseDiscogsDurationMs(raw: String?): Long {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return 0L
        val parts = clean.split(':')
        return when (parts.size) {
            2 -> {
                val min = parts[0].toLongOrNull() ?: 0L
                val sec = parts[1].toLongOrNull() ?: 0L
                ((min * 60L) + sec).coerceAtLeast(0L) * 1000L
            }
            3 -> {
                val hrs = parts[0].toLongOrNull() ?: 0L
                val min = parts[1].toLongOrNull() ?: 0L
                val sec = parts[2].toLongOrNull() ?: 0L
                ((hrs * 3600L) + (min * 60L) + sec).coerceAtLeast(0L) * 1000L
            }
            else -> 0L
        }
    }

    internal fun parseDiscogsSearchJson(rawJson: String): List<Pair<Long, TrackSourceCandidate>> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 8)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0L) continue
                    val rawTitle = o.optString("title").trim()
                    if (rawTitle.isEmpty()) continue
                    val dashSplit = rawTitle.split(" - ", limit = 2)
                    val artist = if (dashSplit.size == 2) dashSplit[0].trim() else ""
                    val title = if (dashSplit.size == 2) dashSplit[1].trim() else rawTitle
                    val cover = o.optString("cover_image").trim().takeIf { it.startsWith("http") && !it.endsWith("spacer.gif") }
                        ?: o.optString("thumb").trim().takeIf { it.startsWith("http") && !it.endsWith("spacer.gif") }
                    val fmtArr = o.optJSONArray("format")
                    val fmtList = buildList {
                        if (fmtArr != null) {
                            for (f in 0 until fmtArr.length()) add(fmtArr.optString(f).trim())
                        }
                    }
                    val hasFlacTag = fmtList.any {
                        it.contains("FLAC", ignoreCase = true) ||
                            it.contains("Lossless", ignoreCase = true) ||
                            it.contains("WAV", ignoreCase = true)
                    }
                    val fmtHint = fmtList.take(2).joinToString("/").ifBlank { "Release" }
                    add(
                        id to TrackSourceCandidate(
                            id = "discogs_srch:$id",
                            sourceKind = MusicSourceKind.DISCOGS_CATALOG,
                            sourceLabel = "Discogs.com ($fmtHint)",
                            title = title,
                            artist = artist,
                            album = title,
                            durationMs = 0L,
                            downloadUrl = null,
                            previewUrl = null,
                            fileExtension = if (hasFlacTag) "flac" else "mp3",
                            bitrateKbps = if (hasFlacTag) 1000 else 320,
                            sampleRateHz = 44100,
                            artworkUrl = cover
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseDiscogsReleaseTracksJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val o = JSONObject(rawJson)
            val id = o.optLong("id", 0L)
            val albumTitle = o.optString("title").trim()
            val artistFromArr = o.optJSONArray("artists")?.optJSONObject(0)?.optString("name")?.trim()
                ?.replace(Regex("""\s*\(\d+\)$"""), "").orEmpty()
            val images = o.optJSONArray("images")
            var cover: String? = null
            if (images != null) {
                for (i in 0 until images.length()) {
                    val img = images.optJSONObject(i) ?: continue
                    val uri = img.optString("uri").trim().ifBlank { img.optString("uri150").trim() }
                    if (uri.startsWith("http")) {
                        cover = uri
                        break
                    }
                }
            }
            val formats = o.optJSONArray("formats")
            var hasFlacRelease = false
            if (formats != null) {
                for (f in 0 until formats.length()) {
                    val fo = formats.optJSONObject(f) ?: continue
                    val descs = fo.optJSONArray("descriptions")
                    val text = fo.optString("text") + " " + fo.optString("name")
                    if (text.contains("FLAC", true) || text.contains("Lossless", true)) {
                        hasFlacRelease = true
                    }
                    if (descs != null) {
                        for (d in 0 until descs.length()) {
                            val ds = descs.optString(d)
                            if (ds.contains("FLAC", true) || ds.contains("Lossless", true) || ds.contains("WAV", true)) {
                                hasFlacRelease = true
                            }
                        }
                    }
                }
            }
            val tracklist = o.optJSONArray("tracklist") ?: JSONArray()
            val out = mutableListOf<TrackSourceCandidate>()
            for (i in 0 until tracklist.length()) {
                val t = tracklist.optJSONObject(i) ?: continue
                if (t.optString("type_").equals("heading", ignoreCase = true)) continue
                val tTitle = t.optString("title").trim()
                if (tTitle.isBlank()) continue
                val tArtist = t.optJSONArray("artists")?.optJSONObject(0)?.optString("name")?.trim()
                    ?.replace(Regex("""\s*\(\d+\)$"""), "").orEmpty().ifBlank { artistFromArr }
                val durMs = parseDiscogsDurationMs(t.optString("duration"))
                out.add(
                    TrackSourceCandidate(
                        id = "discogs:$id:${i}_${t.optString("position")}",
                        sourceKind = MusicSourceKind.DISCOGS_CATALOG,
                        sourceLabel = if (hasFlacRelease) "Discogs.com (FLAC Release)" else "Discogs.com",
                        title = tTitle,
                        artist = tArtist,
                        album = albumTitle,
                        durationMs = durMs,
                        downloadUrl = null,
                        previewUrl = null,
                        fileExtension = if (hasFlacRelease) "flac" else "mp3",
                        bitrateKbps = if (hasFlacRelease) 1000 else 320,
                        sampleRateHz = 44100,
                        artworkUrl = cover
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    internal fun searchDiscogsTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val searchJson = httpGetText("https://api.discogs.com/database/search?q=$encoded&type=release&per_page=4")
                ?: return emptyList()
            val releases = parseDiscogsSearchJson(searchJson)
            if (releases.isEmpty()) return emptyList()
            val out = mutableListOf<TrackSourceCandidate>()
            out.addAll(releases.map { it.second })
            for ((releaseId, _) in releases.take(2)) {
                val detailJson = httpGetText("https://api.discogs.com/releases/$releaseId") ?: continue
                out.addAll(parseDiscogsReleaseTracksJson(detailJson).take(12))
            }
            out
        }.getOrDefault(emptyList())
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

    internal fun searchAudiusTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://discoveryprovider.audius.co/v1/tracks/search?query=$encoded&app_name=KeyDeck")
                ?: return emptyList()
            parseAudiusSearchJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseAudiusSearchJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val title = o.optString("title").trim()
                    if (id.isEmpty() || title.isEmpty()) continue
                    if (o.optBoolean("is_streamable", true).not()) continue
                    val artist = o.optJSONObject("user")?.optString("name")?.trim().orEmpty()
                    val durationMs = o.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
                    val artObj = o.optJSONObject("artwork")
                    val art = artObj?.optString("480x480")?.takeIf { it.startsWith("http") }
                        ?: artObj?.optString("1000x1000")?.takeIf { it.startsWith("http") }
                        ?: artObj?.optString("150x150")?.takeIf { it.startsWith("http") }
                    val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=KeyDeck"
                    add(
                        TrackSourceCandidate(
                            id = "audius:$id",
                            sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
                            sourceLabel = "Audius MP3",
                            title = title,
                            artist = artist,
                            album = o.optString("genre", "").trim(),
                            durationMs = durationMs,
                            downloadUrl = streamUrl,
                            previewUrl = streamUrl,
                            fileExtension = "mp3",
                            bitrateKbps = 320,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
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

    internal fun searchOpenverseAudio(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://api.openverse.org/v1/audio/?q=$encoded&page_size=8") ?: return emptyList()
            parseOpenverseAudioJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseOpenverseAudioJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim().ifBlank { "$i" }
                    val title = o.optString("title").trim()
                    val url = o.optString("url").trim().takeIf { it.startsWith("http") } ?: continue
                    if (title.isEmpty()) continue
                    val creator = o.optString("creator").trim()
                    val ext = o.optString("filetype").trim().lowercase(Locale.ROOT)
                        .ifBlank { url.substringBefore('?').substringAfterLast('.', "mp3").lowercase(Locale.ROOT) }
                        .takeIf { it.length in 2..5 } ?: "mp3"
                    val durMs = o.optLong("duration", 0L).coerceAtLeast(0L)
                    val thumb = o.optString("thumbnail").trim().takeIf { it.startsWith("http") }
                    val isFlac = ext == "flac" || ext == "wav"
                    add(
                        TrackSourceCandidate(
                            id = "openverse:$id",
                            sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                            sourceLabel = "Openverse ${ext.uppercase(Locale.ROOT)}",
                            title = title,
                            artist = creator,
                            album = o.optJSONObject("audio_set")?.optString("title")?.trim().orEmpty(),
                            durationMs = durMs,
                            downloadUrl = url,
                            previewUrl = url,
                            fileExtension = ext,
                            bitrateKbps = if (isFlac) 1000 else 256,
                            sampleRateHz = 44100,
                            artworkUrl = thumb
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun searchHearthisTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://api-v2.hearthis.at/search/?t=$encoded&page=1&count=8") ?: return emptyList()
            parseHearthisSearchJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseHearthisSearchJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val title = o.optString("title").trim()
                    if (id.isEmpty() || title.isEmpty()) continue
                    val dlUrl = o.optString("download_url").trim().takeIf { it.startsWith("http") }
                    val streamUrl = o.optString("stream_url").trim().takeIf { it.startsWith("http") }
                    val effectiveUrl = dlUrl ?: streamUrl ?: continue
                    val artist = o.optJSONObject("user")?.optString("username")?.trim().orEmpty()
                    val durMs = (o.optString("duration").toLongOrNull() ?: o.optLong("duration", 0L)).coerceAtLeast(0L) * 1000L
                    val art = o.optString("artwork_url").trim().takeIf { it.startsWith("http") }
                        ?: o.optString("thumb").trim().takeIf { it.startsWith("http") }
                    val fmtRaw = o.optString("download_filename").substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val isFlac = fmtRaw == "flac" || fmtRaw == "wav"
                    val ext = if (isFlac) fmtRaw else "mp3"
                    add(
                        TrackSourceCandidate(
                            id = "hearthis:$id",
                            sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                            sourceLabel = if (isFlac) "Hearthis.at FLAC" else "Hearthis.at MP3",
                            title = title,
                            artist = artist,
                            album = o.optString("genre", "").trim(),
                            durationMs = durMs,
                            downloadUrl = effectiveUrl,
                            previewUrl = streamUrl ?: effectiveUrl,
                            fileExtension = ext,
                            bitrateKbps = if (isFlac) 1000 else 320,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun searchCcMixterTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://ccmixter.org/api/query?f=json&searchp=$encoded&limit=8") ?: return emptyList()
            parseCcMixterJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseCcMixterJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("upload_id").trim().ifBlank { "$i" }
                    val title = o.optString("upload_name").trim()
                    if (title.isEmpty()) continue
                    val artist = o.optString("user_real_name").trim().ifBlank { o.optString("user_name").trim() }
                    val files = o.optJSONArray("files") ?: continue
                    for (fIdx in 0 until files.length()) {
                        val f = files.optJSONObject(fIdx) ?: continue
                        val url = f.optString("download_url").trim().takeIf { it.startsWith("http") } ?: continue
                        val fmtName = f.optJSONObject("file_format_info")?.optString("format-name")
                            ?.lowercase(Locale.ROOT).orEmpty()
                        val extFromUrl = url.substringBefore('?').substringAfterLast('.', "mp3").lowercase(Locale.ROOT)
                        val isFlac = fmtName.contains("flac") || fmtName.contains("wav") || extFromUrl == "flac" || extFromUrl == "wav"
                        val ext = if (isFlac) (if (extFromUrl == "wav") "wav" else "flac") else "mp3"
                        add(
                            TrackSourceCandidate(
                                id = "ccmixter:$id:$fIdx",
                                sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                                sourceLabel = if (isFlac) "ccMixter FLAC" else "ccMixter MP3",
                                title = title,
                                artist = artist,
                                album = "ccMixter",
                                downloadUrl = url,
                                previewUrl = url,
                                fileExtension = ext,
                                bitrateKbps = if (isFlac) 1000 else 192,
                                sampleRateHz = 44100
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun searchJamendoTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&limit=8&search=$encoded")
                ?: return emptyList()
            parseJamendoSearchJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseJamendoSearchJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val title = o.optString("name").trim()
                    if (id.isEmpty() || title.isEmpty()) continue
                    val artist = o.optString("artist_name").trim()
                    val album = o.optString("album_name").trim()
                    val durMs = o.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
                    val dlUrl = o.optString("audiodownload").trim().takeIf { it.startsWith("http") }
                        ?: o.optString("audio").trim().takeIf { it.startsWith("http") }
                        ?: continue
                    val preview = o.optString("audio").trim().takeIf { it.startsWith("http") } ?: dlUrl
                    val art = o.optString("image").trim().takeIf { it.startsWith("http") }
                        ?: o.optString("album_image").trim().takeIf { it.startsWith("http") }
                    val isFlac = dlUrl.contains("flac", ignoreCase = true)
                    add(
                        TrackSourceCandidate(
                            id = "jamendo:$id",
                            sourceKind = if (isFlac) MusicSourceKind.ARCHIVE_FLAC else MusicSourceKind.ARCHIVE_LOSSY,
                            sourceLabel = if (isFlac) "Jamendo FLAC" else "Jamendo MP3",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durMs,
                            downloadUrl = dlUrl,
                            previewUrl = preview,
                            fileExtension = if (isFlac) "flac" else "mp3",
                            bitrateKbps = if (isFlac) 1000 else 320,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun searchMusicBrainzRecordings(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://musicbrainz.org/ws/2/recording/?query=$encoded&limit=6&fmt=json")
                ?: return emptyList()
            parseMusicBrainzRecordingsJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseMusicBrainzRecordingsJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("recordings") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 8)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val title = o.optString("title").trim()
                    if (id.isEmpty() || title.isEmpty()) continue
                    val artist = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name")?.trim().orEmpty()
                    val durMs = o.optLong("length", 0L).coerceAtLeast(0L)
                    val firstRel = o.optJSONArray("releases")?.optJSONObject(0)
                    val relId = firstRel?.optString("id")?.trim().orEmpty()
                    val album = firstRel?.optString("title")?.trim().orEmpty()
                    val art = if (relId.isNotEmpty()) "https://coverartarchive.org/release/$relId/front-500" else null
                    add(
                        TrackSourceCandidate(
                            id = "mb_rec:$id",
                            sourceKind = MusicSourceKind.ITUNES_CATALOG,
                            sourceLabel = "MusicBrainz",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durMs,
                            downloadUrl = null,
                            previewUrl = null,
                            fileExtension = "flac",
                            bitrateKbps = 0,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    internal fun searchDeezerTracks(query: String): List<TrackSourceCandidate> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val raw = httpGetText("https://api.deezer.com/search?q=$encoded&limit=8") ?: return emptyList()
            parseDeezerSearchTracksJson(raw)
        }.getOrDefault(emptyList())
    }

    internal fun parseDeezerSearchTracksJson(rawJson: String): List<TrackSourceCandidate> {
        return runCatching {
            val arr = JSONObject(rawJson).optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 8)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    val title = o.optString("title").trim()
                    if (id <= 0L || title.isEmpty()) continue
                    val artist = o.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                    val albumObj = o.optJSONObject("album")
                    val album = albumObj?.optString("title")?.trim().orEmpty()
                    val art = albumObj?.optString("cover_xl")?.ifBlank { albumObj.optString("cover_big") }?.trim()
                        ?.takeIf { it.startsWith("http") }
                    val durMs = o.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
                    val preview = o.optString("preview").trim().takeIf { it.startsWith("http") }
                    add(
                        TrackSourceCandidate(
                            id = "deezer_trk:$id",
                            sourceKind = MusicSourceKind.ITUNES_CATALOG,
                            sourceLabel = "Deezer Каталог",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durMs,
                            downloadUrl = null,
                            previewUrl = preview,
                            fileExtension = "mp3",
                            bitrateKbps = 320,
                            sampleRateHz = 44100,
                            artworkUrl = art
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------------
    // Priority Download Logic: FLAC -> Paid Streaming Capture Bridge -> MP3/M4A
    // ------------------------------------------------------------------------

    internal fun isDownloadCandidateAccurate(
        candidate: TrackSourceCandidate,
        expectedTitle: String,
        expectedArtist: String,
        expectedAlbum: String = "",
        expectedDurationMs: Long = 0L
    ): Boolean {
        if (expectedTitle.isNotBlank() && !titlesMatchStrict(expectedTitle, candidate.title)) {
            return false
        }
        if (expectedArtist.isNotBlank()) {
            if (candidate.artist.isNotBlank()) {
                if (!artistsMatch(expectedArtist, candidate.artist)) return false
            } else {
                val expArtistNorm = normalizeArtistComparison(expectedArtist)
                val titleMentionsArtist = normalizeArtistComparison(candidate.title).contains(expArtistNorm)
                val albumMentionsArtist = normalizeArtistComparison(candidate.album).contains(expArtistNorm)
                val albumMatchesStrict = expectedAlbum.isNotBlank() &&
                    !isSyntheticAlbumLabel(candidate.album) &&
                    albumsMatch(expectedAlbum, candidate.album)
                if (!titleMentionsArtist && !albumMentionsArtist && !albumMatchesStrict) {
                    return false
                }
            }
        }
        if (expectedAlbum.isNotBlank() &&
            candidate.album.isNotBlank() &&
            !isSyntheticAlbumLabel(candidate.album) &&
            !albumsMatch(expectedAlbum, candidate.album)
        ) {
            val tightDurationConfirmed = expectedDurationMs > 0L &&
                candidate.durationMs > 0L &&
                abs(expectedDurationMs - candidate.durationMs) <= 10_000L &&
                expectedArtist.isNotBlank() &&
                candidate.artist.isNotBlank() &&
                artistsMatch(expectedArtist, candidate.artist)
            if (!tightDurationConfirmed) return false
        }
        return true
    }

    suspend fun downloadTrackByPriority(
        context: Context,
        title: String,
        artist: String,
        candidates: List<TrackSourceCandidate>,
        installedMusicApps: List<AppEntry>,
        allowStreamingCapture: Boolean = true,
        extraCandidates: List<TrackSourceCandidate> = emptyList(),
        expectedDurationMs: Long = 0L,
        albumCoverUrl: String? = null,
        preferDirectOnlineOverStreaming: Boolean = false,
        expectedAlbum: String = ""
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val merged = (candidates + extraCandidates)
            .distinctBy { "${it.sourceKind}:${it.downloadUrl ?: it.id}" }
        val sorted = merged.sortedBy { it.sourceKind.priorityOrder }

        val existingLocalFlac = sorted.firstOrNull {
            it.sourceKind == MusicSourceKind.LOCAL_FLAC &&
                it.localTrack != null &&
                isDownloadCandidateAccurate(it, title, artist, expectedAlbum, expectedDurationMs)
        }?.localTrack
        if (existingLocalFlac != null) {
            return@withContext DownloadOutcome.DownloadedFile(existingLocalFlac, "FLAC уже на устройстве: ${existingLocalFlac.title}")
        }

        val discogsMatch = sorted.firstOrNull {
            it.sourceKind == MusicSourceKind.DISCOGS_CATALOG &&
                (title.isBlank() || titlesMatchStrict(title, it.title)) &&
                (artist.isBlank() || it.artist.isBlank() || artistsMatch(artist, it.artist))
        }
        val effectiveTitle = title.ifBlank { discogsMatch?.title.orEmpty() }
        val effectiveArtist = artist.ifBlank { discogsMatch?.artist.orEmpty() }
        val effectiveAlbum = expectedAlbum.ifBlank { discogsMatch?.album.orEmpty() }
        val effectiveExpectedDurationMs = if (expectedDurationMs > 0L) expectedDurationMs else (discogsMatch?.durationMs ?: 0L)
        val effectiveCoverUrl = albumCoverUrl ?: discogsMatch?.artworkUrl ?: sorted.firstNotNullOfOrNull { it.artworkUrl }

        val targetQuery = listOf(effectiveArtist.trim(), effectiveTitle.trim()).filter { it.isNotBlank() }.joinToString(" ")
        fun scoreRemoteCandidate(cand: TrackSourceCandidate): Int {
            if (cand.downloadUrl.isNullOrBlank()) return Int.MIN_VALUE
            if (!isDurationCompatible(effectiveExpectedDurationMs, cand.durationMs)) return Int.MIN_VALUE / 2
            if (!isDownloadCandidateAccurate(cand, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)) {
                return Int.MIN_VALUE / 2
            }
            var s = computeQueryRelevanceScore(cand.title, cand.artist, targetQuery)
            if (effectiveAlbum.isNotBlank() && cand.album.isNotBlank() && albumsMatch(effectiveAlbum, cand.album)) {
                s += 110
            }
            if (cand.isLossless) s += 55
            if (discogsMatch != null && normalizeComparisonTitle(cand.title) == normalizeComparisonTitle(discogsMatch.title)) s += 30
            if (effectiveExpectedDurationMs > 0L && cand.durationMs > 0L) {
                val diff = abs(effectiveExpectedDurationMs - cand.durationMs)
                if (diff <= 10_000L) s += 70 else if (diff <= 25_000L) s += 40
            } else if (cand.durationMs >= 60_000L) {
                s += 25
            }
            return s
        }

        suspend fun finalizeDownloadedOutcome(
            saved: LocalAudioTrack,
            coverUrlOrPath: String?,
            badge: String,
            sourceLabel: String
        ): DownloadOutcome.DownloadedFile {
            val resolvedLocalCover = if (!coverUrlOrPath.isNullOrBlank() && File(coverUrlOrPath).exists()) {
                coverUrlOrPath
            } else {
                cacheRemoteCoverFile(context, coverUrlOrPath, "${saved.artist}_${saved.title}")
            }
            val coverBytes = resolvedLocalCover?.let { path ->
                runCatching { File(path).takeIf { it.exists() && it.canRead() }?.readBytes() }.getOrNull()
            }
            val stamped = AudioFileMetadataRepository.writeTrackMetadata(
                context = context,
                track = saved,
                update = AudioFileMetadataUpdate(
                    title = effectiveTitle.ifBlank { saved.title },
                    artist = effectiveArtist.ifBlank { saved.artist },
                    album = effectiveAlbum.ifBlank { saved.album },
                    newCoverBytes = coverBytes
                )
            ).getOrDefault(saved)
            return DownloadOutcome.DownloadedFile(
                stamped,
                "Скачан $badge ($sourceLabel): ${stamped.title}",
                resolvedLocalCover
            )
        }

        // Priority A: Direct FLAC / WAV Lossless URL download from existing candidates (strictly validated)
        val flacRemote = sorted
            .filter {
                (it.sourceKind == MusicSourceKind.ARCHIVE_FLAC || it.isLossless) &&
                    !it.downloadUrl.isNullOrBlank() &&
                    isDurationCompatible(effectiveExpectedDurationMs, it.durationMs) &&
                    isDownloadCandidateAccurate(it, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)
            }
            .maxByOrNull(::scoreRemoteCandidate)
        if (flacRemote != null && !flacRemote.downloadUrl.isNullOrBlank()) {
            val saved = downloadDirectFile(
                context = context,
                urlString = flacRemote.downloadUrl,
                title = effectiveTitle.ifBlank { flacRemote.title },
                artist = effectiveArtist.ifBlank { flacRemote.artist },
                album = effectiveAlbum.ifBlank { flacRemote.album },
                extension = flacRemote.fileExtension.ifBlank { "flac" },
                expectedDurationMs = effectiveExpectedDurationMs
            )
            if (saved != null) {
                return@withContext finalizeDownloadedOutcome(
                    saved = saved,
                    coverUrlOrPath = effectiveCoverUrl ?: flacRemote.artworkUrl,
                    badge = flacRemote.formatBadge,
                    sourceLabel = flacRemote.sourceLabel
                )
            }
        }

        // Priority B: Query all 17 internet sources in parallel (with FLAC + discogs.com canonical fallback)
        var discoveredRemote: List<TrackSourceCandidate> = emptyList()
        suspend fun ensureInternetDiscovery() {
            if (discoveredRemote.isNotEmpty()) return
            val primaryQuery = targetQuery.ifBlank { effectiveTitle.trim() }
            val albumArtistQuery = listOf(effectiveArtist.trim(), effectiveAlbum.trim(), effectiveTitle.trim())
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")
            val primaryHits = queryAllInternetAudioSources(primaryQuery)
            val secondaryHits = if (albumArtistQuery.isNotBlank() && albumArtistQuery != primaryQuery) {
                queryAllInternetAudioSources(albumArtistQuery)
            } else {
                emptyList()
            }
            discoveredRemote = (primaryHits + secondaryHits)
                .distinctBy { "${it.sourceKind}:${it.sourceLabel}:${it.downloadUrl ?: it.id}" }
        }

        ensureInternetDiscovery()

        val discoveredCover = effectiveCoverUrl ?: discoveredRemote
            .firstOrNull {
                it.sourceKind == MusicSourceKind.DISCOGS_CATALOG &&
                    !it.artworkUrl.isNullOrBlank() &&
                    isDownloadCandidateAccurate(it, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)
            }?.artworkUrl
            ?: discoveredRemote.firstOrNull {
                !it.artworkUrl.isNullOrBlank() &&
                    isDownloadCandidateAccurate(it, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)
            }?.artworkUrl

        val discoveredFlac = discoveredRemote
            .filter {
                (it.sourceKind == MusicSourceKind.ARCHIVE_FLAC || it.isLossless) &&
                    !it.downloadUrl.isNullOrBlank() &&
                    isDurationCompatible(effectiveExpectedDurationMs, it.durationMs) &&
                    isDownloadCandidateAccurate(it, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)
            }
            .filter { computeQueryRelevanceScore(it.title, it.artist, targetQuery) >= 20 }
            .maxByOrNull(::scoreRemoteCandidate)
        if (discoveredFlac != null && !discoveredFlac.downloadUrl.isNullOrBlank()) {
            val saved = downloadDirectFile(
                context = context,
                urlString = discoveredFlac.downloadUrl,
                title = effectiveTitle.ifBlank { discoveredFlac.title },
                artist = effectiveArtist.ifBlank { discoveredFlac.artist },
                album = effectiveAlbum.ifBlank { discoveredFlac.album },
                extension = discoveredFlac.fileExtension.ifBlank { "flac" },
                expectedDurationMs = effectiveExpectedDurationMs
            )
            if (saved != null) {
                return@withContext finalizeDownloadedOutcome(
                    saved = saved,
                    coverUrlOrPath = discoveredCover ?: discoveredFlac.artworkUrl,
                    badge = discoveredFlac.formatBadge,
                    sourceLabel = discoveredFlac.sourceLabel
                )
            }
        }

        val streamCandidate = sorted.firstOrNull { it.sourceKind == MusicSourceKind.STREAMING_PAID && !it.packageName.isNullOrBlank() }
        val fallbackStreamingApp = installedMusicApps
            .filter { MediaControlRepository.isStreamingPackage(it.packageName) }
            .minByOrNull { MediaControlRepository.sortPriorityScore(it.packageName) }
        val targetPkg = streamCandidate?.packageName ?: fallbackStreamingApp?.packageName
        val targetLabel = streamCandidate?.sourceLabel ?: fallbackStreamingApp?.label ?: "Стриминг"

        if (allowStreamingCapture && !preferDirectOnlineOverStreaming && !targetPkg.isNullOrBlank()) {
            return@withContext DownloadOutcome.NeedsStreamingCapture(
                title = effectiveTitle,
                artist = effectiveArtist,
                packageName = targetPkg,
                appLabel = targetLabel
            )
        }

        // Priority C: Direct Lossy (MP3 / OGG / M4A) with strict title+artist+album & duration matching
        val combinedLossyPool = (sorted + discoveredRemote)
            .filter {
                !it.downloadUrl.isNullOrBlank() &&
                    it.sourceKind != MusicSourceKind.ITUNES_CATALOG &&
                    isDurationCompatible(effectiveExpectedDurationMs, it.durationMs) &&
                    isDownloadCandidateAccurate(it, effectiveTitle, effectiveArtist, effectiveAlbum, effectiveExpectedDurationMs)
            }
            .sortedByDescending(::scoreRemoteCandidate)

        for (lossyRemote in combinedLossyPool.take(4)) {
            val url = lossyRemote.downloadUrl ?: continue
            val rel = computeQueryRelevanceScore(lossyRemote.title, lossyRemote.artist, targetQuery)
            if (rel < 20 && sorted.none { it.downloadUrl == url }) continue
            val ext = lossyRemote.fileExtension.ifBlank { "mp3" }
            val saved = downloadDirectFile(
                context = context,
                urlString = url,
                title = effectiveTitle.ifBlank { lossyRemote.title },
                artist = effectiveArtist.ifBlank { lossyRemote.artist },
                album = effectiveAlbum.ifBlank { lossyRemote.album },
                extension = ext,
                expectedDurationMs = effectiveExpectedDurationMs
            )
            if (saved != null) {
                return@withContext finalizeDownloadedOutcome(
                    saved = saved,
                    coverUrlOrPath = discoveredCover ?: lossyRemote.artworkUrl,
                    badge = ext.uppercase(Locale.ROOT),
                    sourceLabel = lossyRemote.sourceLabel
                )
            }
        }

        if (allowStreamingCapture && !targetPkg.isNullOrBlank()) {
            return@withContext DownloadOutcome.NeedsStreamingCapture(
                title = effectiveTitle,
                artist = effectiveArtist,
                packageName = targetPkg,
                appLabel = targetLabel
            )
        }

        DownloadOutcome.Failed("Не найден точный аудиофайл «$effectiveTitle» (${effectiveArtist.ifBlank { "артист" }})")
    }

    suspend fun downloadDirectCandidate(
        context: Context,
        candidate: TrackSourceCandidate,
        albumCoverUrl: String? = null
    ): DownloadOutcome.DownloadedFile? = withContext(Dispatchers.IO) {
        val url = candidate.downloadUrl ?: return@withContext null
        val ext = candidate.fileExtension.ifBlank { "mp3" }
        val saved = downloadDirectFile(
            context = context,
            urlString = url,
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            extension = ext,
            expectedDurationMs = candidate.durationMs
        ) ?: return@withContext null
        val coverPath = cacheRemoteCoverFile(
            context = context,
            coverUrl = albumCoverUrl ?: candidate.artworkUrl,
            keyHint = "${saved.artist}_${saved.title}"
        )
        DownloadOutcome.DownloadedFile(saved, "Скачан ${ext.uppercase(Locale.ROOT)}: ${saved.title}", coverPath)
    }

    private fun downloadDirectFile(
        context: Context,
        urlString: String,
        title: String,
        artist: String,
        album: String,
        extension: String,
        expectedDurationMs: Long = 0L
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
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return null
            val contentType = conn.contentType.orEmpty().lowercase(Locale.ROOT)
            if (contentType.startsWith("text/") || contentType.contains("html")) return null

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

            var actualDurationMs = 0L
            var actualBitrateKbps = if (extension.equals("flac", true)) 1000 else 320
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(outFile.absolutePath)
                actualDurationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val bps = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                if (bps > 0) actualBitrateKbps = bps / 1000
            } catch (_: Throwable) {
            } finally {
                runCatching { mmr.release() }
            }

            if (expectedDurationMs >= 75_000L && actualDurationMs in 1L..45_000L) {
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
                durationMs = actualDurationMs.coerceAtLeast(0L),
                uriString = Uri.fromFile(outFile).toString(),
                filePath = outFile.absolutePath,
                displayPath = outFile.absolutePath,
                isTelegramAudio = false,
                formatLabel = extension.uppercase(Locale.ROOT),
                bitrateKbps = actualBitrateKbps,
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
    // Smart Album Builder (Discogs + Deezer + MusicBrainz + CAA + iTunes + Archive.org)
    // ------------------------------------------------------------------------

    internal fun deduplicateExpectedTracks(tracks: List<AlbumExpectedTrack>): List<AlbumExpectedTrack> {
        if (tracks.size <= 1) return tracks
        val byNormKey = LinkedHashMap<String, AlbumExpectedTrack>()
        tracks.forEach { slot ->
            val key = normalizeKeyText(slot.title).ifBlank { slot.title.trim().lowercase(Locale.ROOT) }
            if (key.isEmpty()) return@forEach
            val prev = byNormKey[key]
            if (prev == null) {
                byNormKey[key] = slot
            } else {
                val preferNew = (prev.durationMs <= 0L && slot.durationMs > 0L) ||
                    (prev.matchedLocalTrackId == null && slot.matchedLocalTrackId != null)
                if (preferNew) {
                    byNormKey[key] = slot.copy(trackNumber = minOf(prev.trackNumber, slot.trackNumber).coerceAtLeast(1))
                }
            }
        }
        return byNormKey.values
            .sortedBy { it.trackNumber }
            .mapIndexed { index, item ->
                if (item.trackNumber <= 0) item.copy(trackNumber = index + 1) else item
            }
    }

    suspend fun fetchAlbumBlueprint(
        albumTitle: String,
        artistHint: String = ""
    ): AlbumBlueprintResult? = withContext(Dispatchers.IO) {
        val cleanAlbum = albumTitle.trim()
        if (cleanAlbum.isEmpty()) return@withContext null

        coroutineScope {
            val discogsDeferred = async { fetchAlbumFromDiscogs(cleanAlbum, artistHint) }
            val deezerDeferred = async { fetchAlbumFromDeezer(cleanAlbum, artistHint) }
            val itunesDeferred = async { fetchAlbumFromItunes(cleanAlbum, artistHint) }
            val mbDeferred = async { fetchAlbumFromMusicBrainz(cleanAlbum, artistHint) }
            val archiveDeferred = async { fetchAlbumFromArchiveOrg(cleanAlbum, artistHint) }

            val discogs = discogsDeferred.await()
            val deezer = deezerDeferred.await()
            val itunes = itunesDeferred.await()
            val mb = mbDeferred.await()
            val archive = archiveDeferred.await()

            val allResults = listOfNotNull(discogs, deezer, itunes, mb, archive)
            if (allResults.isEmpty()) return@coroutineScope null

            fun scoreBlueprint(bp: AlbumBlueprintResult): Int {
                var s = computeQueryRelevanceScore(bp.title, bp.artist, "$artistHint $cleanAlbum")
                if (bp.expectedTracks.isNotEmpty()) s += 140 + minOf(bp.expectedTracks.size, 30) * 3
                if (!bp.coverUrl.isNullOrBlank()) s += 45
                if (bp.expectedTracks.any { it.durationMs > 0L }) s += 25
                if (bp.releaseId.startsWith("discogs:")) s += 30
                return s
            }

            val best = allResults.maxByOrNull(::scoreBlueprint) ?: return@coroutineScope null
            val allCandidateCovers = (allResults.flatMap { listOfNotNull(it.coverUrl) + it.alternateCoverUrls })
                .distinct()
                .take(12)

            val verifiedPrimaryCover = allCandidateCovers.firstOrNull { url ->
                !url.contains("coverartarchive.org") || verifyHttpImageUrl(url)
            } ?: best.coverUrl ?: allCandidateCovers.firstOrNull()

            val uniqueExpected = deduplicateExpectedTracks(best.expectedTracks)
            best.copy(
                expectedTracks = uniqueExpected,
                expectedTrackCount = maxOf(best.expectedTrackCount, uniqueExpected.size),
                coverUrl = verifiedPrimaryCover,
                alternateCoverUrls = allCandidateCovers,
                artist = best.artist.ifBlank {
                    allResults.firstNotNullOfOrNull { r -> r.artist.takeIf { it.isNotBlank() } }.orEmpty()
                }
            )
        }
    }

    internal fun fetchAlbumFromDiscogs(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val q = listOf(artistHint.trim(), albumTitle.trim()).filter { it.isNotBlank() }.joinToString(" ")
            val encoded = URLEncoder.encode(q, "UTF-8")
            val searchJson = httpGetText("https://api.discogs.com/database/search?q=$encoded&type=release&per_page=5")
                ?: return null
            val candidates = parseDiscogsSearchJson(searchJson)
            if (candidates.isEmpty()) return null
            val altCovers = candidates.mapNotNull { it.second.artworkUrl }.distinct()
            val ranked = candidates.sortedByDescending { (_, c) ->
                computeQueryRelevanceScore(c.title, c.artist, q) +
                    if (c.fileExtension.equals("flac", ignoreCase = true)) 25 else 0
            }
            for ((relId, _) in ranked.take(2)) {
                val detailJson = httpGetText("https://api.discogs.com/releases/$relId") ?: continue
                val parsed = parseDiscogsReleaseBlueprintJson(detailJson, altCovers)
                if (parsed != null && parsed.expectedTracks.isNotEmpty()) return parsed
            }
            null
        }.getOrNull()
    }

    internal fun parseDiscogsReleaseBlueprintJson(
        rawJson: String,
        alternateCovers: List<String> = emptyList()
    ): AlbumBlueprintResult? {
        return runCatching {
            val o = JSONObject(rawJson)
            val id = o.optLong("id", 0L)
            val title = o.optString("title").trim()
            if (title.isBlank()) return null
            val artist = o.optJSONArray("artists")?.optJSONObject(0)?.optString("name")?.trim()
                ?.replace(Regex("""\s*\(\d+\)$"""), "").orEmpty()
            val images = o.optJSONArray("images")
            val releaseCovers = buildList {
                if (images != null) {
                    for (i in 0 until images.length()) {
                        val img = images.optJSONObject(i) ?: continue
                        val u = img.optString("uri").trim().ifBlank { img.optString("uri150").trim() }
                        if (u.startsWith("http")) add(u)
                    }
                }
            }
            val tracklist = o.optJSONArray("tracklist") ?: JSONArray()
            val rawTracks = mutableListOf<AlbumExpectedTrack>()
            for (i in 0 until tracklist.length()) {
                val t = tracklist.optJSONObject(i) ?: continue
                if (t.optString("type_").equals("heading", ignoreCase = true)) continue
                val tTitle = t.optString("title").trim()
                if (tTitle.isBlank()) continue
                val tArtist = t.optJSONArray("artists")?.optJSONObject(0)?.optString("name")?.trim()
                    ?.replace(Regex("""\s*\(\d+\)$"""), "").orEmpty().ifBlank { artist }
                rawTracks.add(
                    AlbumExpectedTrack(
                        trackNumber = rawTracks.size + 1,
                        title = tTitle,
                        artist = tArtist,
                        durationMs = parseDiscogsDurationMs(t.optString("duration"))
                    )
                )
            }
            val tracks = deduplicateExpectedTracks(rawTracks)
            if (tracks.isEmpty()) return null
            val allCovers = (releaseCovers + alternateCovers).distinct()
            AlbumBlueprintResult(
                releaseId = "discogs:$id",
                title = title,
                artist = artist,
                expectedTrackCount = tracks.size,
                expectedTracks = tracks,
                coverUrl = allCovers.firstOrNull(),
                alternateCoverUrls = allCovers
            )
        }.getOrNull()
    }

    internal fun fetchAlbumFromDeezer(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val q = listOf(artistHint.trim(), albumTitle.trim()).filter { it.isNotBlank() }.joinToString(" ")
            val encoded = URLEncoder.encode(q, "UTF-8")
            val searchJson = httpGetText("https://api.deezer.com/search/album?q=$encoded&limit=6") ?: return null
            val arr = JSONObject(searchJson).optJSONArray("data") ?: return null
            if (arr.length() == 0) return null

            var bestId = 0L
            var bestScore = Int.MIN_VALUE
            val altCovers = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                if (id == 0L) continue
                val title = o.optString("title").trim()
                val artName = o.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                val cov = o.optString("cover_xl").ifBlank { o.optString("cover_big") }.trim()
                if (cov.startsWith("http")) altCovers.add(cov)
                val sc = computeQueryRelevanceScore(title, artName, "$artistHint $albumTitle") +
                    minOf(o.optInt("nb_tracks", 0), 25)
                if (sc > bestScore) {
                    bestScore = sc
                    bestId = id
                }
            }
            if (bestId == 0L) return null
            val detailJson = httpGetText("https://api.deezer.com/album/$bestId") ?: return null
            parseDeezerAlbumJson(detailJson, altCovers)
        }.getOrNull()
    }

    internal fun parseDeezerAlbumJson(
        rawJson: String,
        alternateCovers: List<String> = emptyList()
    ): AlbumBlueprintResult? {
        return runCatching {
            val o = JSONObject(rawJson)
            if (o.has("error")) return null
            val id = o.optLong("id", 0L)
            val title = o.optString("title").trim()
            if (title.isBlank()) return null
            val artist = o.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            val cover = o.optString("cover_xl").ifBlank {
                o.optString("cover_big").ifBlank { o.optString("cover_medium") }
            }.trim().takeIf { it.startsWith("http") }

            val tracksData = o.optJSONObject("tracks")?.optJSONArray("data") ?: JSONArray()
            val tracks = mutableListOf<AlbumExpectedTrack>()
            for (i in 0 until tracksData.length()) {
                val t = tracksData.optJSONObject(i) ?: continue
                val tTitle = t.optString("title").trim()
                if (tTitle.isBlank()) continue
                val tArtist = t.optJSONObject("artist")?.optString("name")?.trim().orEmpty().ifBlank { artist }
                val durMs = t.optLong("duration", 0L).coerceAtLeast(0L) * 1000L
                tracks.add(
                    AlbumExpectedTrack(
                        trackNumber = t.optInt("track_position", tracks.size + 1).coerceAtLeast(1),
                        title = tTitle,
                        artist = tArtist,
                        durationMs = durMs
                    )
                )
            }
            if (tracks.isEmpty()) return null
            AlbumBlueprintResult(
                releaseId = "deezer:$id",
                title = title,
                artist = artist,
                expectedTrackCount = maxOf(o.optInt("nb_tracks", 0), tracks.size),
                expectedTracks = tracks,
                coverUrl = cover,
                alternateCoverUrls = (listOfNotNull(cover) + alternateCovers).distinct()
            )
        }.getOrNull()
    }

    internal fun fetchAlbumFromItunes(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val query = URLEncoder.encode("$artistHint $albumTitle".trim(), "UTF-8")
            val searchJson = httpGetText("https://itunes.apple.com/search?term=$query&entity=album&limit=10")
                ?: return null
            val arr = JSONObject(searchJson).optJSONArray("results") ?: return null
            if (arr.length() == 0) return null

            var bestAlbumObj: JSONObject? = null
            var bestScore = Int.MIN_VALUE
            val altCovers = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val art = o.optString("artworkUrl100").replace("100x100bb", "1200x1200bb").trim()
                if (art.isNotBlank()) altCovers.add(art)
                val cName = o.optString("collectionName").trim()
                val aName = o.optString("artistName").trim()
                val tCnt = o.optInt("trackCount", 0)
                val sc = computeQueryRelevanceScore(cName, aName, "$artistHint $albumTitle") + minOf(tCnt, 25) * 2
                if (sc > bestScore) {
                    bestScore = sc
                    bestAlbumObj = o
                }
            }
            val chosenObj = bestAlbumObj ?: arr.optJSONObject(0) ?: return null
            val collectionId = chosenObj.optLong("collectionId", 0L)
            if (collectionId == 0L) return null
            val primaryArt = chosenObj.optString("artworkUrl100").replace("100x100bb", "1200x1200bb").trim()
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
                val searchJson = httpGetText("https://musicbrainz.org/ws/2/release/?query=$encoded&limit=6&fmt=json")
                val arr = searchJson?.let { JSONObject(it).optJSONArray("releases") }
                if (arr != null && arr.length() > 0) {
                    releases = arr
                    break
                }
            }
            val finalReleases = releases ?: return@runCatching null
            if (finalReleases.length() == 0) return@runCatching null

            val rankedReleases = (0 until finalReleases.length())
                .mapNotNull { finalReleases.optJSONObject(it) }
                .sortedByDescending { rel ->
                    val rTitle = rel.optString("title")
                    val rArtist = rel.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
                    computeQueryRelevanceScore(rTitle, rArtist, "$cleanArtist $cleanTitle") +
                        minOf(rel.optInt("track-count", 0), 25)
                }

            val altCovers = mutableListOf<String>()
            rankedReleases.forEach { rel ->
                val rid = rel.optString("id").trim()
                val rgid = rel.optJSONObject("release-group")?.optString("id")?.trim().orEmpty()
                if (rid.isNotEmpty()) altCovers.add("https://coverartarchive.org/release/$rid/front-500")
                if (rgid.isNotEmpty()) altCovers.add("https://coverartarchive.org/release-group/$rgid/front-500")
            }

            var bestMbResult: AlbumBlueprintResult? = null
            for (chosen in rankedReleases.take(3)) {
                val mbid = chosen.optString("id").trim()
                if (mbid.isEmpty()) continue
                val detailJson = httpGetText("https://musicbrainz.org/ws/2/release/$mbid?inc=recordings+artists+release-groups&fmt=json")
                    ?: continue
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
                if (tracks.isEmpty()) continue
                val rgid = detail.optJSONObject("release-group")?.optString("id")?.trim().orEmpty()
                val covCandidates = buildList {
                    add("https://coverartarchive.org/release/$mbid/front-500")
                    if (rgid.isNotEmpty()) add("https://coverartarchive.org/release-group/$rgid/front-500")
                }
                val validCover = covCandidates.firstOrNull { verifyHttpImageUrl(it) } ?: covCandidates.firstOrNull()
                bestMbResult = AlbumBlueprintResult(
                    releaseId = "mb:$mbid",
                    title = title,
                    artist = artistCredit,
                    expectedTrackCount = tracks.size.coerceAtLeast(chosen.optInt("track-count", 0)),
                    expectedTracks = tracks,
                    coverUrl = validCover,
                    alternateCoverUrls = (covCandidates + altCovers).distinct()
                )
                break
            }
            bestMbResult
        }.getOrNull()
    }

    private fun fetchAlbumFromArchiveOrg(albumTitle: String, artistHint: String): AlbumBlueprintResult? {
        return runCatching {
            val q = listOf(artistHint.trim(), albumTitle.trim()).filter { it.isNotBlank() }.joinToString(" ")
            val encodedQ = URLEncoder.encode("($q) AND mediatype:(audio OR etree)", "UTF-8")
            val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,creator&rows=3&output=json"
            val raw = httpGetText(searchUrl) ?: return null
            val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs") ?: return null
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val id = doc.optString("identifier").trim()
                if (id.isEmpty()) continue
                val itemTitle = doc.optString("title").trim().ifBlank { albumTitle }
                val itemArtist = doc.optString("creator").trim().ifBlank { artistHint }
                val metaRaw = httpGetText("https://archive.org/metadata/$id") ?: continue
                val files = parseArchiveMetadataFiles(id, itemTitle, itemArtist, metaRaw, maxResults = 32)
                    .distinctBy { normalizeKeyText(it.title) }
                if (files.size >= 2) {
                    val covUrl = "https://archive.org/services/img/$id"
                    val expected = files.mapIndexed { idx, c ->
                        AlbumExpectedTrack(
                            trackNumber = idx + 1,
                            title = c.title,
                            artist = c.artist.ifBlank { itemArtist },
                            durationMs = c.durationMs
                        )
                    }
                    return AlbumBlueprintResult(
                        releaseId = "arch:$id",
                        title = itemTitle,
                        artist = itemArtist,
                        expectedTrackCount = expected.size,
                        expectedTracks = expected,
                        coverUrl = covUrl,
                        alternateCoverUrls = listOf(covUrl)
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun canonicalLocalFileKey(track: LocalAudioTrack): String {
        val path = track.filePath?.trim()?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
        if (path != null) return "path:$path"
        val normTitle = normalizeKeyText(track.title).ifBlank { track.title.trim().lowercase(Locale.ROOT) }
        return "meta:$normTitle|${track.durationMs / 2000L}"
    }

    private fun trackContentIdentityKey(track: LocalAudioTrack): String {
        val normTitle = normalizeKeyText(track.title).ifBlank { track.title.trim().lowercase(Locale.ROOT) }
        val durBucket = if (track.durationMs > 0L) track.durationMs / 3000L else -1L
        return "$normTitle|$durBucket"
    }

    fun reconcileAlbumTracks(
        album: SmartAlbum,
        allLocalTracks: List<LocalAudioTrack>
    ): SmartAlbum {
        // Deduplicate physical/alias tracks (e.g. "ms_audio:..." and "fs:/.../song.flac" for the same song)
        val dedupedLocalTracks = allLocalTracks
            .sortedWith(
                compareByDescending<LocalAudioTrack> { it.isLossless }
                    .thenByDescending { it.bitrateKbps }
                    .thenBy { if (it.id.startsWith("ms_audio:")) 0 else 1 }
            )
            .distinctBy(::canonicalLocalFileKey)
            .distinctBy { tr ->
                val normT = normalizeKeyText(tr.title)
                if (normT.isNotBlank() && tr.durationMs > 0L) trackContentIdentityKey(tr) else tr.id
            }

        val tracksById = dedupedLocalTracks.associateBy { it.id }
        val tracksByPath = dedupedLocalTracks
            .mapNotNull { lt -> lt.filePath?.trim()?.lowercase(Locale.ROOT)?.let { it to lt } }
            .toMap()

        fun resolveCanonicalLocal(id: String): LocalAudioTrack? {
            tracksById[id]?.let { return it }
            if (id.startsWith("fs:")) {
                val p = id.removePrefix("fs:").trim().lowercase(Locale.ROOT)
                tracksByPath[p]?.let { return it }
            }
            allLocalTracks.firstOrNull { it.id == id }?.let { raw ->
                val ck = canonicalLocalFileKey(raw)
                dedupedLocalTracks.firstOrNull { canonicalLocalFileKey(it) == ck }?.let { return it }
            }
            return null
        }

        val attached = album.attachedLocalTrackIds
            .mapNotNull(::resolveCanonicalLocal)
            .distinctBy(::canonicalLocalFileKey)
            .distinctBy(::trackContentIdentityKey)

        val autoMatchedByAlbumName = if (album.title.isNotBlank()) {
            dedupedLocalTracks.filter { lt ->
                lt.album.isNotBlank() &&
                    albumsMatch(album.title, lt.album) &&
                    (album.artist.isBlank() || lt.artist.isBlank() || artistsMatch(album.artist, lt.artist))
            }
        } else {
            emptyList()
        }

        val attachedIdsSet = attached.map { it.id }.toSet()
        val pool = (attached + autoMatchedByAlbumName)
            .distinctBy(::canonicalLocalFileKey)
            .distinctBy(::trackContentIdentityKey)

        val uniqueExpected = deduplicateExpectedTracks(album.expectedTracks)
        val usedIds = HashSet<String>()
        val usedContentKeys = HashSet<String>()

        fun scoreLocalTrackForSlot(lt: LocalAudioTrack, slot: AlbumExpectedTrack): Int? {
            if (!titlesMatchStrict(slot.title, lt.title)) return null
            val targetArtist = slot.artist.ifBlank { album.artist }
            val isManuallyAttached = lt.id in attachedIdsSet
            val artistOk = targetArtist.isBlank() || lt.artist.isBlank() || artistsMatch(targetArtist, lt.artist)
            val albumOk = album.title.isBlank() || lt.album.isBlank() || albumsMatch(album.title, lt.album)

            // Reject mismatched artists on auto-matched tracks; on manually attached tracks only reject if artist differs
            if (targetArtist.isNotBlank() && lt.artist.isNotBlank() && !artistOk) return null
            if (!isManuallyAttached && album.title.isNotBlank() && lt.album.isNotBlank() && !albumOk) return null

            var score = 10
            if (targetArtist.isNotBlank() && lt.artist.isNotBlank() && artistOk) score += 20
            if (album.title.isNotBlank() && lt.album.isNotBlank() && albumOk) score += 15
            if (lt.isLossless) score += 5
            if (isManuallyAttached) score += 3
            return score
        }

        val reconciled = uniqueExpected.map { slot ->
            val hit = pool
                .filter { lt ->
                    val ck = trackContentIdentityKey(lt)
                    !usedIds.contains(lt.id) && !usedContentKeys.contains(ck)
                }
                .mapNotNull { lt -> scoreLocalTrackForSlot(lt, slot)?.let { sc -> lt to sc } }
                .maxByOrNull { it.second }
                ?.first

            if (hit != null) {
                usedIds.add(hit.id)
                usedContentKeys.add(trackContentIdentityKey(hit))
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

        val seenFinalCanonical = HashSet<String>()
        val seenFinalContent = HashSet<String>()
        val finalAttachedIds = buildList {
            (attached.map { it.id } + usedIds).forEach { candidateId ->
                val canonicalTrack = resolveCanonicalLocal(candidateId)
                if (canonicalTrack != null) {
                    val ck = canonicalLocalFileKey(canonicalTrack)
                    val contentKey = trackContentIdentityKey(canonicalTrack)
                    if (seenFinalCanonical.add(ck) && seenFinalContent.add(contentKey)) {
                        add(canonicalTrack.id)
                    }
                } else if (seenFinalCanonical.add("id:$candidateId")) {
                    add(candidateId)
                }
            }
        }

        return album.copy(
            attachedLocalTrackIds = finalAttachedIds,
            expectedTracks = reconciled
        )
    }

    private fun titlesMatch(expectedNorm: String, localNorm: String): Boolean {
        return titlesMatchStrict(expectedNorm, localNorm)
    }

    suspend fun importCustomCoverFromUri(
        context: Context,
        albumId: String,
        sourceUri: Uri
    ): Result<String> = importRichMediaCoverFromUri(
        context = context,
        subDir = "album_covers",
        keyPrefix = "cover_${albumId.replace(Regex("[^a-zA-Z0-9_]"), "_")}",
        sourceUri = sourceUri
    )

    suspend fun importRichMediaCoverFromUri(
        context: Context,
        subDir: String,
        keyPrefix: String,
        sourceUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val coversDir = File(context.filesDir, subDir).apply { mkdirs() }
            val mime = context.contentResolver.getType(sourceUri)?.lowercase(Locale.ROOT).orEmpty()
            val uriExt = sourceUri.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
            val isGif = mime.contains("gif") || uriExt == "gif"
            val isVideo = mime.startsWith("video/") || uriExt in setOf("mp4", "webm", "mkv", "mov")

            val safePrefix = keyPrefix.replace(Regex("[^a-zA-Z0-9_]"), "_").take(32).ifBlank { "media" }
            val stamp = System.currentTimeMillis() % 100000

            if (isGif || isVideo) {
                val ext = when {
                    isGif -> "gif"
                    mime.contains("webm") || uriExt == "webm" -> "webm"
                    else -> "mp4"
                }
                val outFile = File(coversDir, "${safePrefix}_$stamp.$ext")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                } ?: error("Не удалось открыть выбранный медиафайл")
                if (outFile.length() == 0L) error("Выбранный файл пуст")
                return@runCatching outFile.absolutePath
            }

            val outFile = File(coversDir, "${safePrefix}_$stamp.jpg")
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

    suspend fun cacheRemoteCoverFile(
        context: Context,
        coverUrl: String?,
        keyHint: String
    ): String? = withContext(Dispatchers.IO) {
        val url = coverUrl?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            val coversDir = File(context.filesDir, "album_covers").apply { mkdirs() }
            val safeHint = keyHint.replace(Regex("[^a-zA-Z0-9_]"), "_").take(32).ifBlank { "cov" }
            val hash = (url.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
            val outFile = File(coversDir, "net_${safeHint}_$hash.jpg")
            if (outFile.exists() && outFile.length() > 1024L) {
                return@withContext outFile.absolutePath
            }
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val scaled = scaleCoverBitmap(bmp, 720)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            if (scaled !== bmp) bmp.recycle()
            outFile.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun verifyHttpImageUrl(urlString: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            val cType = conn.contentType.orEmpty().lowercase(Locale.ROOT)
            code in 200..299 && (cType.isBlank() || cType.startsWith("image/"))
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
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
